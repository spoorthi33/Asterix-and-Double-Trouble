package impl;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import dto.InvalidateCacheDto;
import frontend.src.main.java.FrontEndRequestHandler;
import enums.StatusCode;
import metadata.OrderServiceReplicasMetadata;
import model.OrderServerReplica;
import model.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static utils.HttpUtils.*;


/**
 * FrontEndRequestHandler class is used to handle the incoming requests for the front-end service.
 */
public class FrontEndRequestHandlerImpl implements Runnable, FrontEndRequestHandler {
    private final HttpExchange exchange;
    private String CATALOG_SERVICE_URL = "http://localhost:9999";
    private String ORDER_SERVICE_URL = "http://localhost:11111";
    private Map<String, Response> cache;

    private Boolean isCacheEnabled;

    private OrderServiceReplicasMetadata orderServiceReplicasMetadata;
    public FrontEndRequestHandlerImpl(HttpExchange exchange,Boolean isCacheEnabled, Map<String, Response> cache, String catalogServiceUrl, OrderServiceReplicasMetadata orderServiceReplicasMetadata) {
        this.exchange = exchange;
        this.cache = cache;
        this.orderServiceReplicasMetadata = orderServiceReplicasMetadata;
        this.isCacheEnabled = isCacheEnabled;

        OrderServerReplica leaderNode = orderServiceReplicasMetadata.getLeaderNode();
        if(leaderNode!=null){
            ORDER_SERVICE_URL = leaderNode.getUrl();
        }

        if(catalogServiceUrl!=null){
            CATALOG_SERVICE_URL = catalogServiceUrl;
        }
        String catalogHost = System.getenv("CATALOG_HOST");
        String catalogPort = System.getenv("CATALOG_PORT");
        //System.out.println("Env variable's catalogHost" + catalogHost);
        if(catalogHost!=null && catalogPort!=null){
            CATALOG_SERVICE_URL = "http://" + catalogHost + ":" + catalogPort;
        }
    }

    @Override
    public void run() {
        try {
            String requestMethod = exchange.getRequestMethod();
            if (requestMethod.equalsIgnoreCase("GET")) {
                handleGetRequest(exchange);
            } else if (requestMethod.equalsIgnoreCase("POST")) {
                handlePostRequest(exchange);
            } else {
                // Only GET and POST methods are supported
                // Unsupported request method, return 405 Method Not Allowed
                int errorCode = StatusCode.METHOD_NOT_ALLOWED.getCode();
                String message = "Invalid Request Type";
                Response response = new Response(errorCode,prepareErrorResponse(errorCode,message));
                sendResponse(exchange,response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to validate the get request
     * @param uri - should be of the format /products/{product_name}
     * @return
     */
    private Boolean isQueryURI(URI uri) {
        return uri.getPath().matches("/products/\\w+");
    }

    /**
     * Method to validate the order query request
     * @param uri - uri should be of format /orders
     * @throws IOException
     */
    private Boolean isQueryBuyURI(URI uri) {
        return uri.getPath().matches("/orders/\\w+");
    }

    /**
     * Method to handle the GET request.
     * @param exchange 
     * @throws IOException
     */
    private void handleGetRequest(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        if(isQueryURI(uri)){
            query(exchange);
        } else if (isQueryBuyURI(uri)) {
            queryOrder(exchange);
        } else {
            // Invalid URI, return 404 Not Found
            int errorCode = StatusCode.NOT_FOUND.getCode();
            String message = "Invalid URI";
            Response response = new Response(errorCode,prepareErrorResponse(errorCode,message));
            sendResponse(exchange,response);
        }
    }

    /**
     * Method to validate the post request
     * @param uri - uri should be of format /orders
     * @throws IOException
     */
    private Boolean isBuyURI(URI uri) {
        return uri.getPath().matches("/orders");
    }


    /**
     * Method to validate the invalidate cache requests
     * @param uri - uri should be of format /invalidate
     * @throws IOException
     */
    private Boolean isInvalidateCacheURI(URI uri) {
        return uri.getPath().matches("/invalidate");
    }

    /**
     * Method to validate the invalidate cache bulk requests
     * @param uri - uri should be of format /invalidateBulk
     *
     */
    private Boolean isInvalidateCacheBulkURI(URI uri) {
        return uri.getPath().matches("/invalidateBulk");
    }

    /**
     * Method to validate the joining cluster requests
     * @param uri - uri should be of format /joinOrderCluster
     *
     */
    private Boolean isJoiningClusterURI(URI uri){
        return  uri.getPath().matches("/joinOrderCluster");
    }

    /**
     * Method to handle the POST request.
     * @param exchange 
     * @throws IOException
     */
    private void handlePostRequest(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        if(isBuyURI(uri)){
            buy(exchange);
        } else if (isInvalidateCacheBulkURI(uri)) {
            invalidateCacheBulk(exchange);
        } else if (isInvalidateCacheURI(uri)) {
            invalidateCache(exchange);
        } else if (isJoiningClusterURI(uri)) {
            joinCluster(exchange);
        } else {
            // Invalid URI or request body, return 400 Bad Request
            int errorCode = StatusCode.NOT_FOUND.getCode();
            String message = "Invalid URI";
            Response response = new Response(errorCode,prepareErrorResponse(errorCode,message));
            sendResponse(exchange,response);
        }
    }

    @Override
    public Response joinCluster(HttpExchange exchange){
        Response response;
        String requestBody = getRequestBody(exchange);
        if(requestBody!=null){
            try{
                JSONObject reqObj = new JSONObject(requestBody);
                OrderServerReplica newNode = new OrderServerReplica(reqObj.optInt("id",-1),reqObj.optString("url",""));

                if(newNode.getId()!=-1){
                    System.out.println("A new node with Id "+ newNode.getId()+ "is trying to join the cluster ");
                    // node has joined the cluster.
                    Boolean hasJoined = orderServiceReplicasMetadata.addNodeToPool(newNode);
                    if(hasJoined){
                        OrderServerReplica leaderNode = orderServiceReplicasMetadata.getLeaderNode();
                        JSONObject responseObj = new JSONObject();
                        responseObj.put("id",leaderNode.getId());
                        responseObj.put("url",leaderNode.getUrl());
                        response = new Response(StatusCode.OK.getCode(), responseObj.toString());
                    }else {
                        response = new Response(StatusCode.BAD_REQUEST.getCode(),"Failed to join");
                    }

                }else{
                    response = new Response(StatusCode.BAD_REQUEST.getCode(),"Failed to join");
                }
            }catch (Exception e){
                System.out.println(e.toString());
                response = new Response(StatusCode.BAD_REQUEST.getCode(),"Failed to join");
            }
        }else{
            int errorCode = StatusCode.BAD_REQUEST.getCode();
            String message = "Invalid Request Body";
            response = new Response(errorCode,prepareErrorResponse(errorCode,message));
        }
        sendResponse(exchange, response);
        return response;
    }



    @Override
    public Response query(HttpExchange exchange) {
        Response response;
        // Send get request to the catalog service using same uri
        URI uri = exchange.getRequestURI();
        System.out.println("Received query request by front-end service for item: "+uri);
        String toyName = uri.getPath().substring("/products/".length());

        // checking if toy is available in cache
        if (isCacheEnabled && cache.containsKey(toyName)) {
            System.out.println("Fetching item from cache "+toyName);
            Response cachedResponse = cache.get(toyName);
            sendResponse(exchange, cachedResponse);
            return cachedResponse;
        }
        System.out.println("Cache missed, calling catalog service");
        try{
            String url = CATALOG_SERVICE_URL + uri;
            response = makeGetRequest(url);
            // Cache the whole response object, because we don't want to construct it.
            // Caching even NOT_FOUND requests because, we are not making any changes to our product catalog in this lab, if real-time additions can be done to file then this is not possible or have to do invalidation
            if(isCacheEnabled){
                cache.put(toyName,response);
            }
        }catch (Exception exception){
            int errorCode = StatusCode.INTERNAL_SERVER_ERROR.getCode();
            String message = "INTERNAL_SERVER_ERROR";
            response = new Response(errorCode,prepareErrorResponse(errorCode,message));
        }
        sendResponse(exchange,response);
        System.out.println("Response message by front-end service for query request: "+ response.getMessage());
        return response;
    }

    @Override
    public Response queryOrder(HttpExchange exchange) {
        Response response;
        // Send get request to the catalog service using same uri
        URI uri = exchange.getRequestURI();
        System.out.println("Front-End received query order details request "+uri);
        String orderId = uri.getPath().substring("/orders/".length());
        try{
            String url = ORDER_SERVICE_URL + uri;
            response = makeGetRequest(url);
        }catch (ConnectException e){
            // handle connection exception
            handleConnectionFailure();
            // retry request after leader re-election
            return queryOrder(exchange);
        }catch (Exception exception){
            int errorCode = StatusCode.INTERNAL_SERVER_ERROR.getCode();
            String message = "INTERNAL_SERVER_ERROR";
            response = new Response(errorCode,prepareErrorResponse(errorCode,message));
        }
        sendResponse(exchange,response);
        System.out.println("Response message by front-end service for query request: "+ response.getMessage());
        return response;
    }

    @Override
    public Response buy(HttpExchange exchange) {
        Response response;
        String requestBody = getRequestBody(exchange);
        // Send create order request to order service.
        if(requestBody!= null){
            try{
                URI uri = exchange.getRequestURI();
                System.out.println("Received buy request by front-end service");
                String url = ORDER_SERVICE_URL + uri;
                response = makePostRequest(url,requestBody);
            } catch (ConnectException e){
                // handle connection exception
                handleConnectionFailure();
                // retry request after leader re-election
                return buy(exchange);
            }
            catch (Exception e){
                int errorCode = StatusCode.BAD_REQUEST.getCode();
                String message = "Bad Request";
                response = new Response(errorCode,prepareErrorResponse(errorCode,message));
            }
        }else{
            int errorCode = StatusCode.BAD_REQUEST.getCode();
            String message = "Invalid Request Body";
            response = new Response(errorCode,prepareErrorResponse(errorCode,message));
        }
        sendResponse(exchange, response);
        System.out.println("Response message by front-end service for buy request: "+ response.getMessage());
        return response;
    }

    @Override
    public Response invalidateCache(HttpExchange exchange){
        System.out.println("Invalidating cache request");
        Response response;
        String requestBody = getRequestBody(exchange);
        // Send create order request to order service.
        if(requestBody!= null){
            try{
                InvalidateCacheDto invalidateCacheDto = InvalidateCacheDto.fromJsonString(requestBody);
                String itemName = invalidateCacheDto.getName();
                // Removing item from cache
                System.out.println("Invalidating cache for item"+itemName);
                cache.remove(itemName);
                response = new Response(StatusCode.OK.getCode(), "Success");
            }catch (Exception e){
                System.out.println(e);
                int errorCode = StatusCode.BAD_REQUEST.getCode();
                String message = "Bad Request";
                response = new Response(errorCode,prepareErrorResponse(errorCode,message));
            }
        }else{
            int errorCode = StatusCode.BAD_REQUEST.getCode();
            String message = "Invalid Request Body";
            response = new Response(errorCode,prepareErrorResponse(errorCode,message));
        }
        sendResponse(exchange, response);
        System.out.println("Response message by front-end service for buy request: "+ response.getMessage());
        return response;
    }

    @Override
    public Response invalidateCacheBulk(HttpExchange exchange){
        System.out.println("Invalidating cache request with bulk payload");
        Response response;
        String requestBody = getRequestBody(exchange);
        // Send create order request to order service.
        if(requestBody!= null){
            try{
                JSONArray itemsList = new JSONArray(requestBody);
                for(Object itemName:itemsList){
                    // Removing item from cache
                    cache.remove(itemName.toString());
                }

                response = new Response(StatusCode.OK.getCode(), "Success");
            }catch (Exception e){
                int errorCode = StatusCode.BAD_REQUEST.getCode();
                String message = "Bad Request";
                response = new Response(errorCode,prepareErrorResponse(errorCode,message));
            }
        }else{
            int errorCode = StatusCode.BAD_REQUEST.getCode();
            String message = "Invalid Request Body";
            response = new Response(errorCode,prepareErrorResponse(errorCode,message));
        }
        sendResponse(exchange, response);
        System.out.println("Response message by front-end service for buy request: "+ response.getMessage());
        return response;
    }

    /**
     * This is only called in middle of request, which means leader node crashed. Re-elect.
     */
    private void handleConnectionFailure(){
        // remove leader
        orderServiceReplicasMetadata.removeNodeFromPool(orderServiceReplicasMetadata.getLeaderNode());
        // re-elect the leader
        orderServiceReplicasMetadata.findLeaderNode();
    }

    /**
     * Method to prepare the error response
     * @param errorCode - Error code
     * @param errorMessage - Error message
     * @return - JSON response of format {"error": {"code": 404,"message": "Item not found"}}
     */
    private String prepareErrorResponse(int errorCode,String errorMessage){
        System.out.println("Preparing error response");
        try {
            // Create a map for the error details
            Map<String, Object> errorObject = new HashMap<>();
            errorObject.put("code", errorCode);
            errorObject.put("message", errorMessage);

            // Create a map for the error response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("error", errorObject);

            // Convert the map to JSON string
            String jsonResponse = mapToJson(responseBody);

            // Print the JSON response
            System.out.println("Response string: " + jsonResponse);

            return jsonResponse;
        } catch (Exception e) {
            System.out.println("Error while preparing response body: " + e);
            return null;
        }
    }

    // Helper method to convert Map to JSON string
    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (json.length() > 1) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof Map) {
                json.append(mapToJson((Map<String, Object>) entry.getValue()));
            } else {
                json.append("\"").append(entry.getValue()).append("\"");
            }
        }
        json.append("}");
        return json.toString();
    }
}

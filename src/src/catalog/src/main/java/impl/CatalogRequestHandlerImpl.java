package impl;

import catalog.src.main.java.CatalogRequestHandler;
import com.sun.net.httpserver.HttpExchange;
import db.DB;
import db.ProductCatalog;
import dto.OrderDto;
import enums.StatusCode;
import handlers.CacheInvalidationHandler;
import model.Item;
import model.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static utils.HttpUtils.*;


/**
 * CatalogRequestHandlerImpl class is used to handle the incoming requests for the catalog service.
 */
public class CatalogRequestHandlerImpl implements Runnable, CatalogRequestHandler {
    private final HttpExchange exchange;
    private ProductCatalog productCatalog;

    private String frontendUrl;

    private CacheInvalidationHandler cacheInvalidationHandler;
    public CatalogRequestHandlerImpl(HttpExchange exchange, String frontendUrl, DB productCatalog, CacheInvalidationHandler cacheInvalidationHandler) {
        this.exchange = exchange;
        this.frontendUrl = frontendUrl;
        this.productCatalog = (ProductCatalog) productCatalog;
        this.cacheInvalidationHandler = cacheInvalidationHandler;
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
                exchange.sendResponseHeaders(StatusCode.METHOD_NOT_ALLOWED.getCode(), 0); // Method Not Allowed
                exchange.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to validate the get request
     * @param uri - should be of the format /products/{product_name}
     * @return - true if format is correct,else false
     */
    private Boolean isQueryURI(URI uri) {
        return uri.getPath().matches("/products/\\w+");
    }

    /**
     * Method to handle the GET request and route them to the appropriate controller function.
     * @param exchange
     * @throws IOException
     */
    private void handleGetRequest(HttpExchange exchange) throws IOException{
        URI uri = exchange.getRequestURI();
        // checking if request is for query
        if(isQueryURI(uri)){
            queryItem(exchange);
        } else {
            // Invalid URI, return 404 Not Found
            sendResponse(exchange, new Response(StatusCode.NOT_FOUND.getCode(),"{\"message\":\"Invalid URL\"}"));
        }
    }

    /**
     * Method to validate the post request
     * @param uri - uri should be of format /updateItem
     * @throws IOException
     */
    private Boolean isUpdateUri(URI uri) {
        Boolean isValidUri = uri.getPath().matches("/updateItem");
        Boolean isValidBody = true; // TO-DO: Add proper validation here.
        return isValidUri && isValidBody;
    }

    /**
     * Method to handle the POST request and route them to the appropriate controller function.
     * @param exchange
     * @throws IOException
     */
    private void handlePostRequest(HttpExchange exchange) throws IOException{
        URI uri = exchange.getRequestURI();
        if(isUpdateUri(uri)){
            updateItem(exchange);
        }
    }

    @Override
    public Response queryItem(HttpExchange exchange) {
        Response response ;
        try{
            String itemName = exchange.getRequestURI().getPath().split("/")[2];
            System.out.println("Received query request by catalog service for item: "+itemName);
            Item item = productCatalog.queryItem(itemName);
            if(item==null){
                int errorCode = StatusCode.NOT_FOUND.getCode();
                String message = "Item not found";
                String errorResponse = prepareErrorResponse(errorCode,message);
                response = new Response(errorCode,errorResponse);
            }else{
                int successCode = StatusCode.OK.getCode();
                response = new Response(successCode,prepareSuccessResponse(item));
            }
        }catch (Exception e){
            System.out.println("Error sending response!!" + e);
            int errorCode = StatusCode.BAD_REQUEST.getCode();
            String message = "Bad Request";
            response = new Response(errorCode,prepareErrorResponse(errorCode,message));
        }
        System.out.println("Response message by catalog service for query request: "+ response.getMessage());
        sendResponse(exchange,response);
        return response;
    }

    @Override
    public Response updateItem(HttpExchange exchange) {
        Response response = null;
        String requestBody = getRequestBody(exchange);
        System.out.println(requestBody);
        if(requestBody!= null){
            try{
                System.out.println("Received update item request by catalog service");
                JSONObject requestObj = new JSONObject(requestBody);
                String itemName = requestObj.getString("name");
                int quantity = requestObj.getInt("quantity");
                String operation = requestObj.optString("operation","remove"); // add or remove
                int isUpdateSuccess = productCatalog.buyItem(itemName,quantity,operation);
                if(isUpdateSuccess==1){
                    response = new Response(StatusCode.OK.getCode(),"Success" );
                    // Send invalidate cache request to front-end service in case of successful update.
                    cacheInvalidationHandler.makeInvalidateCacheRequest(itemName);
                }else if(isUpdateSuccess==0){
                    response = new Response(StatusCode.NOT_FOUND.getCode(),"Requested quantity is not available");
                }else{
                    response = new Response(StatusCode.NOT_FOUND.getCode(),"Item not found");
                }
            }catch (Exception e){
               System.out.println("Error while parsing request body"+e);
                response = new Response(StatusCode.BAD_REQUEST.getCode(),"Invalid Request");
            }
        }else{
            response = new Response(StatusCode.BAD_REQUEST.getCode(),"Invalid Request");
        }
        sendResponse(exchange,response);
        System.out.println("Response message by catalog service for create_order request: "+ response.getMessage());
        return response;
    }



    /**
     * Method to prepare the success response
     * @param item - Item object
     * @return - JSON response of format {"data": {"name": "Tux","price": 100,"quantity": 10}}
     */
    private String prepareSuccessResponse(Item item) {
        // Create a map for the success response
        Map<String, Object> successObject = new HashMap<>();
        successObject.put("name", item.getName());
        successObject.put("price", item.getPrice());
        successObject.put("quantity", item.getQuantity());

        // Create a map for the response body
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", successObject);

        // Convert the map to JSON string
        return mapToJson(responseBody);
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

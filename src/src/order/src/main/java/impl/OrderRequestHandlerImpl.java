package impl;

import com.sun.net.httpserver.HttpExchange;
import dto.LostOrdersDto;
import metadata.OrderServiceReplicaMetaData;
import model.OrderServerReplica;
import order.src.main.java.OrderRequestHandler;
import db.DB;
import db.OrderDB;
import dto.OrderDto;
import enums.StatusCode;
import model.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import raft.LogEntry;
import raft.RaftNode;

import java.io.IOException;
import java.net.URI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static utils.HttpUtils.*;


public class OrderRequestHandlerImpl implements Runnable, OrderRequestHandler {
    private final HttpExchange exchange;
    private String CATALOG_SERVICE_URL = "http://localhost:9999";
    private String FRONTEND_SERVICE_URL = "http://localhost:8888";
    private final OrderDB orderDB;
    private OrderServiceReplicaMetaData orderServiceReplicaMetaData;

    private Boolean useRaft;
    private RaftNode raftNode;

    public OrderRequestHandlerImpl(HttpExchange exchange, OrderServiceReplicaMetaData orderServiceReplicaMetaData, DB db, String catalogServiceUrl, String frontEndServiceUrl, Boolean useRaft, RaftNode raftNode) {
        this.exchange = exchange;
        this.orderDB = (OrderDB) db;
        this.orderServiceReplicaMetaData = orderServiceReplicaMetaData;
        this.useRaft = useRaft;
        this.raftNode = raftNode;
        if(catalogServiceUrl!=null){
            CATALOG_SERVICE_URL = catalogServiceUrl;
        }
        if(frontEndServiceUrl!= null){
            FRONTEND_SERVICE_URL = frontEndServiceUrl;
        }
//        String catalogHost = System.getenv("CATALOG_HOST");
//        String catalogPort = System.getenv("CATALOG_PORT");
//        //System.out.println("Env variable's catalogHost" + catalogHost);
//        if(catalogPort!=null && catalogHost!=null){
//            CATALOG_SERVICE_URL = "http://" + catalogHost + ":" + catalogPort;
//        }
    }

    @Override
    public void run() {
        try {
            String requestMethod = exchange.getRequestMethod();
            if (requestMethod.equalsIgnoreCase("POST")) {
                handlePostRequest(exchange);
            } else if (requestMethod.equalsIgnoreCase("GET")) {
                handleGetRequest(exchange);
            } else {
                // Only POST method is supported
                // Unsupported request method, return 405 Method Not Allowed
                exchange.sendResponseHeaders(StatusCode.METHOD_NOT_ALLOWED.getCode(), 0); // Method Not Allowed
                exchange.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to validate the post request
     * @param uri - uri should be of format /orders
     * @throws IOException
     */
    private Boolean isOrderUri(URI uri) {
        return uri.getPath().matches("/orders");
    }

    /**
     * Method to validate the order query request
     * @param uri - uri should be of format /orders
     * @throws IOException
     */
    private Boolean isOrderQueryURI(URI uri) {
        return uri.getPath().matches("/orders/\\w+");
    }
    /**
     * Method to validate the heartbeat request
     * @param uri - uri should be of format /heartbeat
     * @throws IOException
     */
    private Boolean isHeartBeatUri(URI uri){return uri.getPath().matches("/heartbeat");}

    /**
     * Method to validate the heartbeat request
     * @param uri - uri should be of format /updateLeaderNode
     * @throws IOException
     */
    private Boolean isUpdateLeaderNodeUri(URI uri){return uri.getPath().matches("/updateLeaderNode");}

    /**
     * Method to validate the propagation request
     * @param uri - uri should be of format /propagateOrder
     * @throws IOException
     */
    private Boolean isPropagationUri(URI uri){return uri.getPath().matches("/propagateOrder");}

    /**
     * Method to validate the synchronization of data request
     * @param uri - uri should be of format /syncData
     * @throws IOException
     */
    private Boolean isSyncDataUri(URI uri){return uri.getPath().matches("/syncData");}

    private Boolean isUpdateFollowerNodeUri(URI uri){return uri.getPath().matches("/updateFollowerNodes");}

    private Boolean replicateLogEntryRaftUri(URI uri){
        return uri.getPath().matches("/replicateLogEntryRaft");
    }

    private Boolean ackLogCommittedRaftUri(URI uri){
        return uri.getPath().matches("/ackLogCommittedRaft");
    }

    private Boolean syncLostDataRaftUri(URI uri){
        return uri.getPath().matches("/syncLostDataRaft");
    }

    private Boolean isUpdateRaftTxnStatus(URI uri){
        return uri.getPath().matches("/updateTxnStatusRaft");
    }

    /**
     * Method to handle the POST request and route them to the appropriate controller function.
     * @param exchange
     * @throws IOException
     */
    private void handlePostRequest(HttpExchange exchange) throws IOException{
        URI uri = exchange.getRequestURI();
        if(isOrderUri(uri)){
            createOrder(exchange);
        } else if (isUpdateLeaderNodeUri(uri)) {
            updateLeaderNode(exchange);
        } else if (isPropagationUri(uri)) {
            updatePropagatedOrder(exchange);
        } else if (isSyncDataUri(uri)) {
            syncData(exchange);
        } else if (isUpdateFollowerNodeUri(uri)) {
            updateFollowerNodes(exchange);
        } else if(replicateLogEntryRaftUri(uri)){
            replicateLogEntryRaft(exchange);
        } else if (ackLogCommittedRaftUri(uri)) {
            ackLogCommittedRaft(exchange);
        } else if (syncLostDataRaftUri(uri)) {
            syncLostDataRaft(exchange);
        } else if(isUpdateRaftTxnStatus(uri)){
            updateRaftTxnStatus(exchange);
        }
    }

    /**
     * Method to handle the Get request and route them to the appropriate controller function.
     * @param exchange
     * @throws IOException
     */
    private void handleGetRequest(HttpExchange exchange) throws IOException{
        URI uri = exchange.getRequestURI();
        if(isHeartBeatUri(uri)){
            heartBeat(exchange);
        } else if (isOrderQueryURI(uri)) {
            queryOrder(exchange);
        }
    }

    @Override
    public Response updateRaftTxnStatus(HttpExchange exchange){
        Response response = new Response(StatusCode.BAD_REQUEST.getCode(), "Failure");
        try {
            String requestBody = getRequestBody(exchange);
            JSONObject requestJson = new JSONObject(requestBody);
            int logId = requestJson.getInt("logId");
            int term = requestJson.getInt("term");
            String status = requestJson.getString("status");
            raftNode.updateTxnStatus(logId,term,status);
            response = new Response(StatusCode.OK.getCode(),"Success");
        }catch (Exception e){
            System.out.println(e);
        }
        sendResponse(exchange,response);
        return response;
    }

    @Override
    public Response syncLostDataRaft(HttpExchange exchange){
        Response response = new Response(StatusCode.BAD_REQUEST.getCode(), "Failure");
        try {
            String requestBody = getRequestBody(exchange);
            JSONObject requestJson = new JSONObject(requestBody);
            int lastCommittedId = requestJson.getInt("lastCommittedId");
            JSONArray responseArray = raftNode.getLostData(lastCommittedId);
            response = new Response(StatusCode.OK.getCode(), responseArray.toString());
        }catch (Exception e){
            System.out.println(e);
        }
        sendResponse(exchange,response);
        return response;
    }

    @Override
    public Response ackLogCommittedRaft(HttpExchange exchange){
        Response response = new Response(StatusCode.BAD_REQUEST.getCode(), "Failure");
        try {
            String requestBody = getRequestBody(exchange);
            JSONObject requestJson = new JSONObject(requestBody);
            raftNode.ackCommittedLog(requestJson.getInt("logId"),requestJson.getInt("term"),requestJson.getInt("orderId"));
            response = new Response(StatusCode.OK.getCode(), "Success");
        }catch (Exception e){
            System.out.println(e);
        }
        sendResponse(exchange,response);
        return response;
    }

    @Override
    public Response replicateLogEntryRaft(HttpExchange exchange){
        Response response = new Response(StatusCode.BAD_REQUEST.getCode(), "Failure");
        try {
            String requestBody = getRequestBody(exchange);
            JSONObject requestJson = new JSONObject(requestBody);
            LogEntry logEntry = new LogEntry(requestJson.getInt("logId"),requestJson.getInt("term"),requestJson.getString("orderDetails"));
            System.out.println("Received replicated log with Id: "+logEntry.getLogId()+" for order :"+logEntry.getOrderDetails());
            Boolean isAppended = raftNode.appendReplicatedLog(logEntry);
            if(isAppended){
                response = new Response(StatusCode.OK.getCode(), "Success");
            }
        }catch (Exception e){
            System.out.println(e);
        }
        sendResponse(exchange,response);
        return response;
    }

    @Override
    public Response updateFollowerNodes(HttpExchange exchange){
        Response response ;
        try{
            String requestBody = getRequestBody(exchange);
            JSONObject requestJson = new JSONObject(requestBody);
            String update = requestJson.getString("update");
            JSONArray followerNodes = requestJson.getJSONArray("followerNodes");
            System.out.println("Update has been made to follower nodes "+update);
            if(update.equalsIgnoreCase("add")){
                orderServiceReplicaMetaData.addFollowerNodes(followerNodes);
                response = new Response(StatusCode.OK.getCode(),"Success" );
            }else if(update.equalsIgnoreCase("delete")){
                orderServiceReplicaMetaData.removeFollowerNodes(followerNodes);
                response = new Response(StatusCode.OK.getCode(),"Success" );
            }else{
                response = new Response(StatusCode.BAD_REQUEST.getCode(), "Update not supported.");
            }
        }catch (Exception e) {
            System.out.println(e.toString());
            response = new Response(StatusCode.BAD_REQUEST.getCode(), "Failed to update");
        }
        sendResponse(exchange,response);
        return response;
    }

    @Override
    public Response updateLeaderNode(HttpExchange exchange){
        System.out.println("Trying to update leader node");
        String requestBody = getRequestBody(exchange);
        JSONObject requestJson = new JSONObject(requestBody);
        int leaderId = requestJson.optInt("id",-1);
        String leaderUrl = requestJson.optString("url","");
        OrderServerReplica leaderNode = new OrderServerReplica(leaderId,leaderUrl);
        int replicaId = orderServiceReplicaMetaData.getReplicaId();
        System.out.println("Leader Id: "+leaderId);
        if(leaderId==replicaId){
            System.out.println("I am leader node");
            orderServiceReplicaMetaData.setIsLeaderNode();
        }
        orderServiceReplicaMetaData.setLeaderNode(leaderNode);
        Response response = new Response(StatusCode.OK.getCode()," ");
        sendResponse(exchange,response);
        return response;
    }

    @Override
    public Response updatePropagatedOrder(HttpExchange exchange){
        System.out.println("Received an propagated order ");
        Response response;
        try{
            String requestBody = getRequestBody(exchange);
            JSONObject requestJson = new JSONObject(requestBody);
            int orderId = requestJson.getInt("orderId");
            String itemName = requestJson.getString("itemName");
            int quantity = requestJson.getInt("quantity");
            orderDB.writePropagatedOrder(orderId,itemName,quantity);
            response = new Response(StatusCode.OK.getCode(), "Success");
        }catch (Exception e){
            System.out.println("Error while writing propagated order"+e);
            response = new Response(StatusCode.BAD_REQUEST.getCode(), "UPDATE_FAILED");
        }
        sendResponse(exchange,response);
        return response;
    }

    @Override
    public Response syncData(HttpExchange exchange){
        System.out.println("Trying to get lost data to sync data.");
        Response response;
        try{
            String requestBody = getRequestBody(exchange);
            JSONObject requestJson = new JSONObject(requestBody);
            int lastOrderId = requestJson.getInt("orderId"); // get last order Id of the replica.
            List<LostOrdersDto> lostOrdersDtos = orderDB.getLostData(lastOrderId);
            JSONArray jsonArray = new JSONArray();
            for (LostOrdersDto lostOrder : lostOrdersDtos) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("orderId", lostOrder.getOrderId());
                jsonObject.put("itemName", lostOrder.getName());
                jsonObject.put("quantity", lostOrder.getQuantity());
                jsonArray.put(jsonObject);
            }
            response = new Response(StatusCode.OK.getCode(), jsonArray.toString());
        }catch (Exception e){
            System.out.println("Error while writing propagated order"+e);
            response = new Response(StatusCode.BAD_REQUEST.getCode(), "UPDATE_FAILED");
        }
        sendResponse(exchange,response);
        return response;
    }


    @Override
    public Response heartBeat(HttpExchange exchange){
        Response response = new Response(StatusCode.OK.getCode()," ");
        sendResponse(exchange,response);
        return response;
    }

    @Override
    public Response queryOrder(HttpExchange exchange){
        System.out.println("Order Service received query order request");
        JSONObject errorObject = new JSONObject();
        errorObject.put("code",StatusCode.NOT_FOUND.getCode());
        errorObject.put("message","Order not found");
        JSONObject errRespObj = new JSONObject();
        errRespObj.put("error",errorObject);
        Response response = new Response(StatusCode.NOT_FOUND.getCode(), errRespObj.toString());
        URI uri = exchange.getRequestURI();
        try{
            int orderId = Integer.parseInt(uri.getPath().substring("/orders/".length()));
            if(orderId!=-1){
                System.out.println("Order found");
                OrderDto orderDto = orderDB.getOrderInfo(orderId);
                JSONObject responseBody = new JSONObject();
                JSONObject dataObj = new JSONObject();
                dataObj.put("itemName",orderDto.getName());
                dataObj.put("quantity",orderDto.getQuantity());
                dataObj.put("orderId",orderId);
                responseBody.put("data",dataObj);
                response = new Response(StatusCode.OK.getCode(), responseBody.toString());
            }
        }catch (Exception e){
            System.out.println(e);
        }
        sendResponse(exchange,response);
        return response;
    }

    @Override
    public Response createOrder(HttpExchange exchange) {
        Response response ;
        String requestBody = getRequestBody(exchange);
        if(requestBody!=null){
            JSONObject requestObj = new JSONObject(requestBody);
            String name = requestObj.getString("name");
            int requestedQuantity = requestObj.getInt("quantity");
            if(isQuantityAvailable(name,requestedQuantity)){
                try{
                    String url = CATALOG_SERVICE_URL + "/updateItem";
                    // System.out.println("Received buy request by order service,calling catalog service to update order DB");
                    Response catalogResponse = makePostRequest(url,requestBody);
                    String message = catalogResponse.getMessage();
                    // System.out.println("Message from catalog service: "+message);
                    int statusCode = catalogResponse.getStatusCode();
                    if(statusCode == StatusCode.OK.getCode() && message.equals("Success")){
                        OrderDto orderDto = OrderDto.fromJsonString(requestBody);
                        response = placeOrder(orderDto);
                    }else{
                        response = new Response(statusCode,prepareErrorResponse(statusCode,message));
                    }
                }catch (Exception e){
                    // System.out.println("Error while making request to catalog service"+e);
                    int errorCode = StatusCode.BAD_REQUEST.getCode();
                    String message = "Error while making request to catalog service";
                    response =  new Response(errorCode,prepareErrorResponse(errorCode,message));
                }
            }else {
                int errorCode = StatusCode.BAD_REQUEST.getCode();
                String message = "Quantity not available";
                response =  new Response(errorCode,prepareErrorResponse(errorCode,message));
            }

        }else{
            System.out.println("Invalid Request Body");
            int errorCode = StatusCode.BAD_REQUEST.getCode();
            String message = "Invalid Request Body";
            response =  new Response(errorCode,prepareErrorResponse(errorCode,message));
        }
        sendResponse(exchange,response);
        System.out.println("Response message by order service for create order request: "+ response.getMessage());
        return response;
    }

    private Response placeOrder(OrderDto orderDto){
        Response response;
        if(this.useRaft){
            int orderId = raftNode.appendAndReplicateLog(orderDto);
            if(orderId!=-1){
                response = new Response(StatusCode.OK.getCode(),prepareSuccessResponse(orderId) );
            }else {
                int errorCode = StatusCode.INTERNAL_SERVER_ERROR.getCode();
                response = new Response(errorCode, prepareErrorResponse(errorCode,"DB Replication Failure"));
            }
        }else{

            String itemName = orderDto.getName();
            int quantity = orderDto.getQuantity();
            int orderId = orderDB.placeOrder(itemName,quantity);
            response = new Response(StatusCode.OK.getCode(),prepareSuccessResponse(orderId));
            // propagate details to other replica's
            propagateToFollowerNodes(orderId,itemName,quantity);
        }
       return response;
    }

    private Boolean isQuantityAvailable(String name,int requestedQuantity){
        String queryUrl = CATALOG_SERVICE_URL+"/products/"+name;
        try{
            Response response = makeGetRequest(queryUrl);
            JSONObject respObj = new JSONObject(response.getMessage());
            JSONObject dataObj = respObj.getJSONObject("data");
            int availableQuantity = dataObj.getInt("quantity");
            if(availableQuantity>=requestedQuantity){
                return true;
            }
        }catch (Exception e){
            System.out.println("Error getting item details");
        }
        return false;
    }

    /**
     * Method to propagate order to follower nodes
     * @param orderId
     * @param itemName
     * @param quantity
     */
    private void propagateToFollowerNodes(int orderId, String itemName, int quantity){
        System.out.println("Propagating order to follower nodes");
        JSONObject requestBody = new JSONObject();
        requestBody.put("orderId",orderId);
        requestBody.put("itemName",itemName);
        requestBody.put("quantity",quantity);
        String requestString = requestBody.toString();
        Map<Integer,OrderServerReplica> followerNodes = orderServiceReplicaMetaData.getFollowerNodes();
        System.out.println("Follower Nodes size:"+followerNodes.size());
        for (OrderServerReplica followerNode : followerNodes.values()) {
            // get uri
            int replicaId = followerNode.getId();
            String replicaUrl = followerNode.getUrl();
            String replicaEndpoint = replicaUrl + "/propagateOrder";
            System.out.println("Propagating order to follower node: "+replicaId);
            try{
                makePostRequest(replicaEndpoint,requestString);
            }catch (Exception e){
                System.out.println("Error while propagating request for Id "+ replicaId);
            }
        }
    }

    /**
     * Method to prepare the success response
     * @param orderId
     * @return - JSON response of format {"data": {"order_number": 1}}
     */
    private String prepareSuccessResponse(int orderId){
        // Create a map for the success object
        Map<String, Object> successObject = new HashMap<>();
        successObject.put("order_number", orderId);

        // Create a map for the response body
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("data", successObject);

        // Convert the map to JSON string
        return mapToJson(responseBody);
    }

    /**
     * Method to prepare the error response
     * @param errorCode
     * @param errorMessage
     * @return - JSON response of format {"error": {"code": 400,"message": "Invalid Request Body"}}
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
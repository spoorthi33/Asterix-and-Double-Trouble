package metadata;

import db.OrderDB;
import dto.LostOrdersDto;
import enums.StatusCode;
import model.OrderServerReplica;
import model.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static utils.HttpUtils.makeGetRequest;
import static utils.HttpUtils.makePostRequest;

public class OrderServiceReplicaMetaData {

    public Boolean isLeaderNode;
    public OrderServerReplica replica;
    public OrderServerReplica leaderNode;

    private Map<Integer,OrderServerReplica> followerNodesMap;
    public OrderDB orderDB;

    public OrderServiceReplicaMetaData(OrderDB orderDB){
        this.isLeaderNode = false;
        this.leaderNode = new OrderServerReplica(-1,"");
        this.followerNodesMap = new HashMap<>();
        this.orderDB = orderDB;
    }

    public void setIsLeaderNode(){
        this.isLeaderNode = true;
    }

    public void setLeaderNode(OrderServerReplica leaderNode){
        this.leaderNode = leaderNode;
    }

    public OrderServerReplica getLeaderNode(){
        return this.leaderNode;
    }

    public int getReplicaId() {
        return this.replica.getId();
    }

    public void setReplicaData(Integer replicaId,String url) {
        this.replica = new OrderServerReplica(replicaId,url);
    }

    /**
     * Add follower Nodes
     * @param followerNodes - JSONArray of follower nodes
     */
    public void addFollowerNodes(JSONArray followerNodes){
        for (int i = 0; i < followerNodes.length(); i++) {
            JSONObject nodeObj = followerNodes.getJSONObject(i);
            int nodeId = nodeObj.getInt("id");
            String nodeUrl = nodeObj.getString("url");
            System.out.println("Adding node with Id "+nodeId);
            followerNodesMap.put(nodeId,new OrderServerReplica(nodeId,nodeUrl));
        }
    }


    /**
     * Remove follower Nodes
     * @param followerNodes - JSONArray of follower nodes
     */
    public void removeFollowerNodes(JSONArray followerNodes){
        for (int i = 0; i < followerNodes.length(); i++) {
            JSONObject nodeObj = followerNodes.getJSONObject(i);
            int nodeId = nodeObj.getInt("id");
            if(followerNodesMap.get(nodeId)!=null){
                followerNodesMap.remove(nodeId);
            }
        }
    }

    /**
     * Method to join cluster if possible
     */
    public Boolean joinCluster(String frontEndServiceUrl,Boolean useRaft){
        System.out.println("Trying to join cluster...");
        // call front-end service to check if this replica can join cluster of existing order service nodes.
        String serverUrl = frontEndServiceUrl+"/joinOrderCluster";
        JSONObject requestBody = new JSONObject();
        requestBody.put("id",replica.getId());
        requestBody.put("url",replica.getUrl());
        String requestBodyStr = requestBody.toString();
        try{
            Response response = makePostRequest(serverUrl,requestBodyStr);
            // fetch leader node details
            if(response.getStatusCode()== StatusCode.OK.getCode()){
                JSONObject leaderJson = new JSONObject(response.getMessage());
                OrderServerReplica leaderNode = new OrderServerReplica(leaderJson.optInt("id",-1),leaderJson.optString("url",""));
                System.out.println("Successfully joined cluster with leaderId"+leaderNode.getId());
                setLeaderNode(leaderNode);
                if(!useRaft){
                    synchronizeData(leaderNode); // if we are using raft, it will take care.
                }

                return true;
                // ackFrontEndSyncCompleted(frontEndServiceUrl);
            }
        }catch (Exception e){
            System.out.println(e);
            System.out.println("Failed to join cluster, may be front-end service is still not up...");
        }
        return false;
    }

    /**
     * Method asks leaderNode for lost data,synchronizes data.
     * @param leaderNode - leaderNode details
     */
    public void synchronizeData(OrderServerReplica leaderNode){
        // check max order Id of this replica
        System.out.println("Trying to get lost data and synchronize");
        int currentOrderId = orderDB.getCurrentOrderId();

        // call leader node to return remaining data
        String serverUrl = leaderNode.getUrl()+"/syncData";

        JSONObject requestBody = new JSONObject();
        requestBody.put("orderId",currentOrderId);

        try{
            Response response = makePostRequest(serverUrl,requestBody.toString());
            System.out.println("Got lost data from leaderNode");
            // write details to order DB
            JSONArray jsonArray = new JSONArray(response.getMessage());
            List<LostOrdersDto> lostOrdersDtos = new ArrayList<>();
            for(int i=0;i<jsonArray.length();i++){
                JSONObject jsonObject = jsonArray.optJSONObject(i);
                int orderId = jsonObject.getInt("orderId");
                String name = jsonObject.getString("itemName");
                int quantity = jsonObject.getInt("quantity");
                lostOrdersDtos.add(new LostOrdersDto(name,quantity,orderId));
            }
            orderDB.syncLostData(lostOrdersDtos);
        }catch (Exception e){
            System.out.println("Error while synchronizing data between leader and current replica"+e);
        }
    }


    public Boolean isLeaderNode() {
        return isLeaderNode;
    }

    public int getLeaderId() {
        return leaderNode.getId();
    }

    // returns total number of nodes.
    public int getTotalNode(){
        return 3; // hard coding for now,can fetch from controller.
    }

    public Map<Integer,OrderServerReplica> getFollowerNodes() {
        return followerNodesMap;
    }
}
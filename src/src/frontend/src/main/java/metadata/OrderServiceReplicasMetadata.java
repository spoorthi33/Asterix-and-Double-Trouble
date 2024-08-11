package metadata;


import enums.StatusCode;
import model.OrderServerReplica;
import model.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static utils.HttpUtils.makeGetRequest;
import static utils.HttpUtils.makePostRequest;

public class OrderServiceReplicasMetadata {

    private Map<Integer,OrderServerReplica> orderServiceReplicas;

    private OrderServerReplica leaderNode;

    private Map<Integer,List<OrderServerReplica>> followerNodesMap;
    private ScheduledExecutorService executorService;

    public OrderServiceReplicasMetadata(){
        this.orderServiceReplicas = new HashMap<>();
        this.executorService = Executors.newScheduledThreadPool(1);
    }

    public void setOrderServiceReplicas(List<OrderServerReplica> orderServiceReplicaList) {
        for(OrderServerReplica orderServerReplica:orderServiceReplicaList){
            this.orderServiceReplicas.put(orderServerReplica.getId(),orderServerReplica);
        }
        startPolling();
    }

    /**
     * elects the leader, update leader and follower nodes.
     * @return - leader node details
     */
    public OrderServerReplica findLeaderNode(){

        System.out.println("Trying to find leader node");
        // init dummy replica
        OrderServerReplica leaderReplica = new OrderServerReplica(-1,"");
        for(OrderServerReplica replica:orderServiceReplicas.values()){
            if(replica.getId()>leaderReplica.getId()){
                if(checkOrderServiceReplicaStatus(replica)){
                    leaderReplica = replica;
                }
            }
        }
        System.out.println("Elected leader with Id: "+leaderReplica.getId());
        setLeaderNode(leaderReplica);
        // get follower Nodes
        List<OrderServerReplica> followerNodes = new ArrayList<>();
        for(OrderServerReplica replica:orderServiceReplicas.values()){
            if(replica.getId()!=leaderReplica.getId()){
                followerNodes.add(replica);
            }
        }
        notifyFollowers(leaderReplica); // notify followers about leader
        notifyLeaderNode("add",followerNodes); // notify leader node about followers
        return leaderReplica;
    }


    public OrderServerReplica getLeaderNode() {
        return leaderNode;
    }

    public void setLeaderNode(OrderServerReplica leaderNode) {
        this.leaderNode = leaderNode;
    }

    public Boolean checkOrderServiceReplicaStatus(OrderServerReplica replica){
        String serverUrl = replica.getUrl()+"/heartbeat";
        try {
            Response response = makeGetRequest(serverUrl);
            return response.getStatusCode() == StatusCode.OK.getCode();
        } catch (IOException e) {
            System.out.println("Error checking health check for replica "+replica.getId());
            return false;
        }
    }

    private void pollReplicas() {
        for (OrderServerReplica replica : orderServiceReplicas.values()) {
            if (!checkOrderServiceReplicaStatus(replica)) {
                System.out.println("Health check failed for replica with Id "+replica.getId());
                removeNodeFromPool(replica);
            }
        }
    }

    public void startPolling() {
        executorService.scheduleAtFixedRate(this::pollReplicas, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Remove crashed node from pool of cluster
     * @param faultyReplica - crashed replica
     */
    public void removeNodeFromPool(OrderServerReplica faultyReplica){
        System.out.println("Removing crashed node from pool of cluster");
        this.orderServiceReplicas.remove(faultyReplica.getId());

        // if leader node is crashed , re-elect the leader
        if(faultyReplica.getId() == leaderNode.getId()){
            System.out.println("Crashed replica is leader, so re-electing leader");
            findLeaderNode();
        }else{
            // update leader about faulty node,so that it can stop propagating to faulty/crashed node
            System.out.println("update leader about faulty node,so that it can stop propagating to faulty/crashed node.");
            List<OrderServerReplica> orderServerReplicasList = new ArrayList<>();
            orderServerReplicasList.add(faultyReplica);
            notifyLeaderNode("delete",orderServerReplicasList);
        }
    }

    /**
     * Add newly joined replica into cluster
     * @param orderServerReplica - newly joined replica
     * @return - true(if update is success)
     */
    public Boolean addNodeToPool(OrderServerReplica orderServerReplica){
        // update leaderNode about new node. If update succeeds, add node.
        List<OrderServerReplica> orderServerReplicasList = new ArrayList<>();
        orderServerReplicasList.add(orderServerReplica);
        System.out.println("Notifying leader about newly joined node..");
        if(notifyLeaderNode("add",orderServerReplicasList)){
            System.out.println("Leader has accepted the new node with Id "+orderServerReplica.getId());
            this.orderServiceReplicas.put(orderServerReplica.getId(),orderServerReplica);
            return true;
        }
        return false;

    }

    /**
     * Notify leader node about updates of cluster
     * @param update - add/delete (add when new node is added, delete when node has crashed)
     * @param followerNodes - follower node details
     */
    public Boolean notifyLeaderNode(String update, List<OrderServerReplica> followerNodes){
      System.out.println("Notifying leader node about followers...");
      OrderServerReplica leaderReplica = getLeaderNode();
      String serverUrl = leaderReplica.getUrl()+"/updateFollowerNodes";
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("update",update);
      JSONArray followersArray = new JSONArray();
      for (OrderServerReplica followerNode:followerNodes){
          JSONObject nodeObj = new JSONObject();
          nodeObj.put("id",followerNode.getId());
          nodeObj.put("url",followerNode.getUrl());
          followersArray.put(nodeObj);
      }
      jsonObject.put("followerNodes",followersArray);
      String jsonStr = jsonObject.toString();
      try{
          Response response = makePostRequest(serverUrl,jsonStr);
          if(response.getStatusCode()==StatusCode.OK.getCode()){
              return true;
          }
      }catch (Exception e){
          System.out.println("Error while updating leader node about faulty node"+e);
      }
      return false;
    }

    /**
     * Update follower nodes about leader replica
     * @param leaderReplica - current leader node
     */
    public void notifyFollowers(OrderServerReplica leaderReplica){
        for(OrderServerReplica replica:orderServiceReplicas.values()){
            System.out.println("Notifying node "+replica.getId()+"about leader...");
            String serverUrl = replica.getUrl()+"/updateLeaderNode";
            JSONObject requestBody = new JSONObject();
            requestBody.put("id",leaderReplica.getId());
            requestBody.put("url",leaderReplica.getUrl());
            try{
                makePostRequest(serverUrl,requestBody.toString());
            }catch (Exception e){
                System.out.println("Error updating follower node about the leader "+ replica.getId());
            }
        }
    }

}

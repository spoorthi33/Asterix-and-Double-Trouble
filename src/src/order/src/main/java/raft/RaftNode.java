package raft;

import db.OrderDB;
import dto.OrderDto;
import enums.StatusCode;
import metadata.OrderServiceReplicaMetaData;
import model.OrderServerReplica;
import model.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static utils.HttpUtils.makePostRequest;

public class RaftNode {

    private OrderServiceReplicaMetaData orderServiceReplicaMetaData;

    private int lastCommittedId =0;

    private final DiskLog diskLog;

    private final OrderDB orderDB;
    private Boolean inCluster;

    private String catalogServiceUrl;

    private int termId = 1;

    public RaftNode(OrderServiceReplicaMetaData orderServiceReplicaMetaData,String catalogServiceUrl, OrderDB orderDB,String raftFilePath, Boolean inCluster){
        this.orderServiceReplicaMetaData = orderServiceReplicaMetaData;
        this.catalogServiceUrl = catalogServiceUrl;
        this.inCluster = inCluster;
        this.orderDB = orderDB;
        diskLog = new DiskLog(raftFilePath);
        // get last committed log
        diskLog.loadNextCommitId();
        // ask leader node to sync if in cluster.
        if(inCluster){
            setInCluster(true);
            syncLostData();
        }
    }

    public void setInCluster(Boolean inCluster) {
        this.inCluster = inCluster;
    }

    public Boolean appendReplicatedLog(LogEntry logEntry){
        int nextLogId = diskLog.getNextLogId();
        if(logEntry.getLogId()==nextLogId){
            System.out.println("Appending log entry to log file: "+logEntry.getLogId()+logEntry.getOrderDetails());
            diskLog.appendLogEntry(logEntry);
            return true;
        }
        return false;
    }

    public int appendAndReplicateLog(OrderDto orderDto){
        int logId = diskLog.getNextLogId();
        String orderDetails = orderDto.getTabSeparatedString();
        LogEntry logEntry = new LogEntry(logId,termId,orderDetails);
        System.out.println("Appending log entry :" +logEntry.getLogId()+ " "+logEntry.getOrderDetails());
        diskLog.appendLogEntry(logEntry);
        Map<Integer,OrderServerReplica> followerNodes = orderServiceReplicaMetaData.getFollowerNodes();
        int successRate =1; // inclusive of leader.
        int errorRate =0;
        List<OrderServerReplica> successReplicas = new ArrayList<>();
        for(OrderServerReplica followerNode:followerNodes.values()){
            String serverUrl = followerNode.getUrl()+"/replicateLogEntryRaft";
            JSONObject requestBody = new JSONObject();
            requestBody.put("logId",logEntry.getLogId());
            requestBody.put("term",logEntry.getTerm());
            requestBody.put("orderDetails",logEntry.getOrderDetails());
            System.out.println("Replicating log to follower nodes for order :"+logEntry.getOrderDetails());
            try{
                Response response = makePostRequest(serverUrl,requestBody.toString());
                if(response.getStatusCode()==StatusCode.OK.getCode()){
                    successRate++;
                    successReplicas.add(followerNode);
                }
            }catch (Exception e){
                System.out.println("Error while replicating log for nodeId"+e);
            }
        }

        errorRate = orderServiceReplicaMetaData.getTotalNode()-successRate;
        System.out.println("Positive Votes: "+successRate);
        System.out.println("Negative Votes: "+errorRate);
        // majority voted
        if(successRate>errorRate){
            System.out.println("Got majority of the votes so committing the transaction");

            // update log file that txn is success...
            diskLog.updateTxnStatus(logId,termId,"S");

            int orderId = orderDB.placeOrder(orderDto.getName(),orderDto.getQuantity());
            // send committed ack to followers.
            sendLogCommittedAck(logId,termId,orderId);
            return orderId;
        }else{
            System.out.println("Rolling back since we didn't receive majority voting");
            // update log file that txn has failed.Instead of removing log and waiting for raft to do log compaction I am updating as F, so that in future if replay is needed we can refer to this file
            diskLog.updateTxnStatus(logId,termId,"F");
            // update replicas that transaction has failed.
            updateTransactionStatusToOtherNodes(successReplicas,logId,termId);
            // Update catalog to re-increment the stock,since txn has failed.
            updateCatalogAboutFailedTransaction(orderDto);
            return -1;
        }
    }

    private void updateTransactionStatusToOtherNodes(List<OrderServerReplica> successReplicas,int logId,int termId){
        for (OrderServerReplica replica : successReplicas) {
            try{
                String serverUrl = replica.getUrl()+"/updateTxnStatusRaft";
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("logId",logId);
                jsonObject.put("term",termId);
                jsonObject.put("status","F");
                makePostRequest(serverUrl,jsonObject.toString());
            }catch (Exception e){
                System.out.println("Error updating txn status for replica"+replica.getId());
            }
        }
    }

    private void updateCatalogAboutFailedTransaction(OrderDto orderDto){
        String serverUrl = catalogServiceUrl+"/updateItem";
        JSONObject requestBody = new JSONObject();
        requestBody.put("itemName",orderDto.getName());
        requestBody.put("quantity",orderDto.getQuantity());
        requestBody.put("operation","add");
        try{
            makePostRequest(serverUrl,requestBody.toString());
        }catch (Exception e){
            System.out.println("Error updating catalog about failed transaction");
        }
    }

    public void updateTxnStatus(int logId,int term,String status){
        diskLog.updateTxnStatus(logId,term,status);
    }

    public void ackCommittedLog(int logId,int termId,int orderId){
        System.out.println("Received Ack about committed log: "+logId);
        try{
            // update txn status to S
            diskLog.updateTxnStatus(logId,termId,"S");
            LogEntry logEntry = diskLog.getLogEntry(logId,termId);
            String orderDetails = logEntry.getOrderDetails();
            OrderDto orderDto = OrderDto.fromTSVString(orderDetails);
            if(orderDto!=null){
                orderDB.writePropagatedOrder(orderId,orderDto.getName(),orderDto.getQuantity());
                System.out.println("Committed log for Id: "+logId);
            }
        }catch (Exception e){
            System.out.println(e.toString());
        }

    }

    public void sendLogCommittedAck(int logId,int termId,int orderId){
        Map<Integer,OrderServerReplica> followerNodes = orderServiceReplicaMetaData.getFollowerNodes();
        for(OrderServerReplica followerNode:followerNodes.values()){
            String serverUrl = followerNode.getUrl()+"/ackLogCommittedRaft";
            JSONObject requestBody = new JSONObject();
            requestBody.put("logId",logId);
            requestBody.put("term",termId);
            requestBody.put("orderId",orderId);
            try{
                makePostRequest(serverUrl,requestBody.toString());
            }catch (Exception e){
                System.out.println("Error while sending committed log ack for nodeId"+e);
            }
        }
    }

    public JSONArray getLostData(int lastCommittedId){
        List<LogEntry> uncommittedEntries = diskLog.getLostData(lastCommittedId);
        JSONArray jsonArray = new JSONArray();
        for(LogEntry logEntry:uncommittedEntries){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("logId",logEntry.getLogId());
            jsonObject.put("term",logEntry.getTerm());
            jsonObject.put("orderDetails",logEntry.getOrderDetails());
            jsonArray.put(jsonObject);
        }
        return jsonArray;
    }

    public void syncLostData(){
        System.out.println("Syncing lost data....");
        int lastCommittedId = diskLog.getNextLogId()-1;
        OrderServerReplica leaderNode = orderServiceReplicaMetaData.getLeaderNode();
        String serverUrl = leaderNode.getUrl()+"/syncLostDataRaft";
        JSONObject requestBody = new JSONObject();
        requestBody.put("lastCommittedId",lastCommittedId);
        try{
            Response response = makePostRequest(serverUrl,requestBody.toString());
            if(response.getStatusCode()== StatusCode.OK.getCode()){
                JSONArray lostData = new JSONArray(response.getMessage());
                List<LogEntry> lostEntries = new ArrayList<>();
                List<OrderDto> lostOrderDtos = new ArrayList<>();
                for(int i=0;i<lostData.length();i++){
                    JSONObject logObj = lostData.getJSONObject(i);
                    String orderDetails = logObj.getString("orderDetails");
                    OrderDto orderDto = OrderDto.fromTSVString(orderDetails);
                    LogEntry logEntry = new LogEntry(logObj.getInt("logId"),logObj.getInt("term"),logObj.getString("orderDetails"));
                    logEntry.setTxnStatus("S"); // because it's already committed.
                    lostEntries.add(logEntry);
                    lostOrderDtos.add(orderDto);
                }
                int lastCommitId = diskLog.appendAndCommitLogEntries(lostEntries);
                for(OrderDto orderDto:lostOrderDtos){
                    orderDB.placeOrder(orderDto.getName(),orderDto.getQuantity());
                }
            }
        }catch (Exception e){
            System.out.println("Error while syncing lost data..."+e);
        }
    }

}

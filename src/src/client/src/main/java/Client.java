
import enums.StatusCode;
import model.PlacedOrder;
import model.Response;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;


import static utils.HttpUtils.makeGetRequest;
import static utils.HttpUtils.makePostRequest;

public class Client {

    private static String FRONT_END_SERVICE_URL = "http://localhost:8889";

    private static String[] itemsList = {"Lego","Barbie","Tinkertoy","Marbles","Bicycle","Crayolacrayon","Rollerskates","Frisbee","Monopoly","LincolnLogs","Whale", "Tux", "Fox", "Python"};
    private static final double ORDER_PROBABILITY = 0.5;

    private static final List<PlacedOrder> placedOrderIds = new ArrayList<>();

    public static void main(String[] args) {
        Random random = new Random();
        if(args!=null){
            if(args.length>=1){

                    FRONT_END_SERVICE_URL = args[0]; // reading service url from args.
            }
        }
        System.out.println(FRONT_END_SERVICE_URL);
        List<Double> orderProbabilities = getProbabilityList();
        Map<Double,Double> pQueryLatencyMap = new HashMap<>();
        Map<Double,Double> pOrderLatencyMap = new HashMap<>();
        for(Double p:orderProbabilities){
            List<Long> orderLatencies = new ArrayList<>();
            List<Long> queryLatencies = new ArrayList<>();
            for (int i=0;i<100;i++) {
                String itemName = itemsList[random.nextInt(itemsList.length)];
                Long startTime = System.currentTimeMillis();
                int quantity = query(itemName);
                Long endTime = System.currentTimeMillis();
                queryLatencies.add(endTime-startTime);
                double randomNumber = random.nextDouble();
                if (quantity > 0 && randomNumber < p) {
                    Long startTime1 =System.currentTimeMillis();
                    buy(itemName,1);
                    Long endTime1 = System.currentTimeMillis();
                    orderLatencies.add(endTime1-startTime1);
                }
            }
            Double avgOrderLatency = avgLatency(orderLatencies);
            Double avgQueryLatency = avgLatency(queryLatencies);
            pQueryLatencyMap.put(p,avgQueryLatency);
            pOrderLatencyMap.put(p,avgOrderLatency);
        }
        System.out.println("Query Latencies: ");
        printPLatencyMap(pQueryLatencyMap);

        System.out.println("Order Latencies: ");
        printPLatencyMap(pOrderLatencyMap);

       verifyDataIntegrity();
    }

    private static void TestCase2(){
        for (int i = 0; i < 10; i++) {
            buy(itemsList[i],1);
        }
    }

    private static void verifyDataIntegrity(){
        for(PlacedOrder order:placedOrderIds){
            if(order.getOrderId()!=-1){
                if(verifyOrderDetails(order)){
                    System.out.println("Order details match file for orderId: "+order.getOrderId());
                }else{
                    System.out.println("Order details doesn't match file for orderId: "+order.getOrderId());
                }
            }
        }
    }

    /**
     * Method to fetch order details from order query and verify if it matches with locally stored details
     * @param order - locally stored placed order
     * @return
     */
    private static Boolean verifyOrderDetails(PlacedOrder order){
        int orderId = order.getOrderId();
        String itemName = order.getItemName();
        int quantity = order.getQuantity();
        String getUrl = FRONT_END_SERVICE_URL+"/orders/"+orderId;
        try{
            Response response = makeGetRequest(getUrl);
            String responseString = response.getMessage();
            JSONObject respObj = new JSONObject(responseString);
            JSONObject dataObj = respObj.optJSONObject("data");
            if(dataObj.getString("itemName").equals(itemName) && dataObj.getInt("quantity")==quantity){
                return true;
            }
        }catch (Exception e){
            System.out.println(e);
        }

        return false;
    }
    private static void printPLatencyMap(Map<Double,Double> pLatencyMap){
        for(Double p: pLatencyMap.keySet()){
            Double latency = pLatencyMap.get(p);
            System.out.println("Probability :"+p+" Latency: "+latency);
        }
    }

    private static Double avgLatency(List<Long> latencies){
        Double avgLatency = 0.0;
        for(Long latency:latencies){
            avgLatency+=latency;
        }
        if(!latencies.isEmpty()){
            avgLatency = avgLatency/(latencies.size());
        }
        return  avgLatency;
    }

    private static List<Double> getProbabilityList(){
        List<Double> orderProbabilities = new ArrayList<>();
        orderProbabilities.add((double) 0);
        orderProbabilities.add(0.2);
        orderProbabilities.add(0.4);
        orderProbabilities.add(0.6);
        orderProbabilities.add(0.8);
        return orderProbabilities;
    }


    /**
     * Method to query item.
     * @param itemName - Name of the item to query.
     */
    private static int query(String itemName) {
        // Make a sample GET request
        int quantity = -1;
        try {
            String getUrl = FRONT_END_SERVICE_URL+"/products/"+itemName;
            Response response = makeGetRequest(getUrl);
            String responseString = response.getMessage();
            System.out.println("GET Response: "+responseString);
            // Extracting the quantity from the response string
            int dataIndex = responseString.indexOf("\"data\":{");
            if (dataIndex != -1) {
                int quantityIndex = responseString.indexOf("\"quantity\":\"", dataIndex);
                if (quantityIndex != -1) {
                    int endIndex = responseString.indexOf("\"", quantityIndex + "\"quantity\":\"".length());
                    String quantityString = responseString.substring(quantityIndex + "\"quantity\":\"".length(), endIndex);
                    quantity = Integer.parseInt(quantityString);
                   // System.out.println("Quantity: " + quantity);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return quantity;
    }

    /**
     * Method to buy item.
     * @param itemName - Name of the item to buy.
     * @param quantity - Quantity of the item to buy.
     */
    private static void buy(String itemName,int quantity){
        // Make a sample POST request
        try {
            String postUrl = FRONT_END_SERVICE_URL+"/orders";
            String postData = "{\"name\": \""+itemName+"\",\"quantity\":"+quantity+"}"; // JSON data
            Response response = makePostRequest(postUrl, postData);
            if (response.getStatusCode()== StatusCode.OK.getCode()){
                JSONObject respObj = new JSONObject(response.getMessage());
                JSONObject dataObj = respObj.optJSONObject("data");
                if(!dataObj.isEmpty()){
                    int orderId = dataObj.optInt("order_number",-1);
                    placedOrderIds.add(new PlacedOrder(orderId,itemName,quantity));
                }
            }
            System.out.println("POST Response: " + response.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

package tests;

import model.Response;

import java.io.IOException;

import static utils.HttpUtils.makeGetRequest;
import static utils.HttpUtils.makePostRequest;

public class TestUtils {
    private static String FRONT_END_SERVICE_URL = "http://localhost:8889";

    public static String[] itemsList = {"Lego","Barbie","Tinkertoy","Marbles","Bicycle","Crayolacrayon","Rollerskates","Frisbee","Monopoly","LincolnLogs","Whale", "Tux", "Fox", "Python"};
    /**
     * Method to query item.
     * @param itemName - Name of the item to query.
     */
    static int query(String itemName) {
        // Make a sample GET request
        int quantity = -1;
        try {
            String getUrl = FRONT_END_SERVICE_URL+"/products/"+itemName;
            Response response = makeGetRequest(getUrl);
            String responseString = response.getMessage();

            // Extracting the quantity from the response string
            int dataIndex = responseString.indexOf("\"data\":{");
            if (dataIndex != -1) {
                int quantityIndex = responseString.indexOf("\"quantity\":\"", dataIndex);
                if (quantityIndex != -1) {
                    int endIndex = responseString.indexOf("\"", quantityIndex + "\"quantity\":\"".length());
                    String quantityString = responseString.substring(quantityIndex + "\"quantity\":\"".length(), endIndex);
                    quantity = Integer.parseInt(quantityString);
                    System.out.println("Quantity: " + quantity);
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
    static void buy(String itemName, int quantity){
        // Make a sample POST request
        try {
            String postUrl = FRONT_END_SERVICE_URL+"/orders";
            String postData = "{\"name\": \""+itemName+"\",\"quantity\":"+quantity+"}"; // JSON data
            Response response = makePostRequest(postUrl, postData);
            System.out.println("POST Response: " + response.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

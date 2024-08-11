package dto;

import java.util.HashMap;
import java.util.Map;

/**
 * OrderDto class is used to map the incoming order request.
 */
public class OrderDto {
    private String name;
    private int quantity;

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderDto(String name, int quantity) {
        this.name = name;
        this.quantity = quantity;
    }

    /**
     * Method to parse the incoming JSON request and map it to OrderDto object.
     * @param jsonString - Incoming JSON request.
     * @return - OrderDto object.
     */
    public static OrderDto fromJsonString(String jsonString)  {
        try{
            jsonString = jsonString.replaceAll("\\s", "").replaceAll("\\{", "").replaceAll("\\}", "");

            // Split the key-value pairs
            String[] keyValuePairs = jsonString.split(",");

            // Create a map to store key-value pairs
            Map<String, String> keyValueMap = new HashMap<>();
            for (String pair : keyValuePairs) {
                String[] entry = pair.split(":");
                keyValueMap.put(entry[0], entry[1]);
            }

            // Extract values from the map
            String name = keyValueMap.getOrDefault("\"name\"", "").replaceAll("\"", "");
            int quantity = Integer.parseInt(keyValueMap.getOrDefault("\"quantity\"", "0"));

            // Check if the name is not empty and quantity is not zero
            if (!name.isEmpty() && quantity != 0) {
                return new OrderDto(name, quantity);
            }

            System.out.println("Invalid request received: " + jsonString);
            return null;
        }catch (Exception e){
            System.out.println("Error parsing request"+e);
            return null;
        }
    }

}

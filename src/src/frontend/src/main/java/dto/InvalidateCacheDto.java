package dto;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * OrderDto class is used to map the incoming order request.
 */
public class InvalidateCacheDto {
    private String name;

    public String getName() {
        return name;
    }


    public InvalidateCacheDto(String name) {
        this.name = name;
    }

    /**
     * Method to parse the incoming JSON request and map it to OrderDto object.
     * @param jsonString - Incoming JSON request.
     * @return - OrderDto object.
     */
    public static InvalidateCacheDto fromJsonString(String jsonString)  {
        try{
            JSONObject jsonObject = new JSONObject(jsonString);
            return new InvalidateCacheDto(jsonObject.getString("itemName"));
        }catch (Exception e){
            System.out.println("Error parsing request"+e);
            return null;
        }
    }

}

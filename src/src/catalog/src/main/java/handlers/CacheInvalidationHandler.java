package handlers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import static utils.HttpUtils.makePostRequest;

public class CacheInvalidationHandler {

    private String frontendUrl;
    private Boolean isCacheEnabled;
    public CacheInvalidationHandler(Boolean isCacheEnabled ,String frontendUrl){
        this.frontendUrl = frontendUrl;
        this.isCacheEnabled = isCacheEnabled;
    }

    /**
     * Calls front-end service to invalidate cache.
     */
    public void makeInvalidateCacheRequest(String itemName){
        // make invalidate calls only if cache is enabled.
        if(isCacheEnabled){
            String frontEndURI = frontendUrl+"/invalidate";

            // Create invalidate cache body
            JSONObject requestBody = new JSONObject();
            requestBody.put("itemName",itemName);
            String requestBodyJson = requestBody.toString();

            // make post call to front-end
            try{
                makePostRequest(frontEndURI,requestBodyJson);
            }catch (Exception e){
                System.out.println("Error while making invalidate cache request");
            }
        }

    }

    /**
     * Calls front-end service to invalidate cache with bulk payload.
     */
    public void makeBulkInvalidateCacheRequest(JSONArray items){
        // make invalidate calls only if cache is enabled.
        if(isCacheEnabled){
            String frontEndURI = frontendUrl+"/invalidateBulk";

            // Create invalidate cache body
            String requestBodyJson = items.toString();

            // make post call to front-end
            try{
                makePostRequest(frontEndURI,requestBodyJson);
            }catch (Exception e){

                System.out.println("Error while making invalidate cache request");
            }
        }

    }
}

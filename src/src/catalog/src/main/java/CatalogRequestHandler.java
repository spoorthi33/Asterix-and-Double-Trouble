package catalog.src.main.java;

import model.Response;
import com.sun.net.httpserver.HttpExchange;


/**
 * CatalogRequestHandler interface is used to define the methods to handle the incoming requests for the catalog service.
 */
public interface CatalogRequestHandler {
    /**
     * Method to handle the query request. This method should return the item details for the given item name.
     * @param httpExchange - Incoming request
     * @return - Response object with the item details.
     */
    public Response queryItem(HttpExchange httpExchange);

    /**
     * Method to handle the update request. This method should update the item details for the given item name.
     * @param httpExchange - Incoming request
     * @return - Response object with the updated item details.
     */
    public Response updateItem(HttpExchange httpExchange);

}

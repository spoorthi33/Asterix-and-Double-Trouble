package frontend.src.main.java;

import com.sun.net.httpserver.HttpExchange;
import model.Response;



public interface FrontEndRequestHandler {
    /**
     * Method to handle the query request. This method will call catalog service and return the item details for the given item name.
     * @param httpExchange - Incoming request
     * @return - Response object with the item details.
     */
    public Response query(HttpExchange httpExchange);

    /**
     * Method to handle the buy request. This method will call order service and returns order id.
     * @param httpExchange - Incoming request
     * @return - Response object with the updated item details.
     */
    public Response buy(HttpExchange httpExchange);

    /**
     * Method to handle the invalidateCache request. This method will update the cache.
     * @param httpExchange - Incoming request
     * @return - Response object
     */
    public Response invalidateCache(HttpExchange httpExchange);

    /**
     * Method to handle the bulk invalidateCache request. This method will update the cache.
     * @param httpExchange - Incoming request
     * @return - Response object
     */
    public Response invalidateCacheBulk(HttpExchange httpExchange);

    /**
     * Method to handle the join cluster request. This method will add a new node to existing cluster of order service replicas.
     * @param httpExchange - Incoming request
     * @return - Response object
     */
    public Response joinCluster(HttpExchange httpExchange);

    /**
     * Method to handle query buy request
     * @param httpExchange - Incoming request
     * @return - Response object
     */
    public Response queryOrder(HttpExchange httpExchange);
}

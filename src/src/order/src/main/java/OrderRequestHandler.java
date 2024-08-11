package order.src.main.java;

import com.sun.net.httpserver.HttpExchange;
import model.Response;


public interface OrderRequestHandler {
    /**
     * Method to handle the create order request. This method will accept requests from front-end and query catalog.
     * @param httpExchange - Incoming request
     * @return - Response object with the updated item details.
     */
    public Response createOrder(HttpExchange httpExchange);

    /**
     * Method to handle query buy request
     * @param httpExchange - Incoming request
     * @return - Response object
     */
    public Response queryOrder(HttpExchange httpExchange);

    /**
     * Method to check heart beat of service.
     * @param httpExchange - Incoming request
     * @return - Response object(200 if service is alive)
     */
    public Response heartBeat(HttpExchange httpExchange);

    /**
     * Method to set leader .
     * @param httpExchange - Incoming request(leader node details)
     * @return - Response object
     */
    public Response updateLeaderNode(HttpExchange httpExchange);

    /**
     * Method to update follower nodes .
     * @param httpExchange - Incoming request(update:add/delete, list of nodes)
     * @return - Response object - 200 if success
     */
    public Response updateFollowerNodes(HttpExchange httpExchange);

    /**
     * Method to handle propagation requests .
     * @param httpExchange - Incoming request with orderDetails
     * @return - Response object - 200 if update is success
     */
    public Response updatePropagatedOrder(HttpExchange httpExchange);

    /**
     * Method to handle sync data requests .
     * @param httpExchange - Incoming request with replica details
     * @return - Response object to write data
     */
    public Response syncData(HttpExchange httpExchange);
    /**
     * Method to Inform log has been committed .
     * @param httpExchange - Incoming request with log Id
     * @return - Response object to write data
     */
    public Response ackLogCommittedRaft(HttpExchange httpExchange);

    /**
     * Method to replicate log entries when using raft .
     * @param httpExchange - Incoming request with log replica
     * @return - Response object
     */
    public Response replicateLogEntryRaft(HttpExchange httpExchange);

    /**
     * Method to handle sync data  when using raft.
     * @param httpExchange - Incoming request with last commit Id
     * @return - Response object
     */
    public Response syncLostDataRaft(HttpExchange httpExchange);


    /**
     * Method to update txn status in case of failure, while using raft.
     * @param httpExchange - Incoming request with logId,term,status
     * @return - Response object(200 if update is success)
     */
    public Response updateRaftTxnStatus(HttpExchange httpExchange);
}

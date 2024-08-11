package handlers;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import db.DB;
import impl.OrderRequestHandlerImpl;
import metadata.OrderServiceReplicaMetaData;
import raft.RaftNode;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class CustomHttpHandler implements HttpHandler {
    private ExecutorService executorService;
    private DB db;
    private String catalogServiceUrl;
    private String frontEndServiceUrl;
    private OrderServiceReplicaMetaData orderServiceReplicaMetaData;
    private Boolean useRaft;
    private RaftNode raftNode;
    /**
     * Constructor to initialize the executor service and service name.
     * @param executorService - Thread pool to handle the incoming requests.
     * @param db - DB(product catalog in case of catalog service, order db in case of order service)
     */
    public CustomHttpHandler(ExecutorService executorService,OrderServiceReplicaMetaData orderServiceReplicaMetaData, DB db,String catalogServiceUrl,String frontEndServiceUrl,Boolean useRaft,RaftNode raftNode){
        this.executorService = executorService;
        this.orderServiceReplicaMetaData = orderServiceReplicaMetaData;
        this.db = db;
        this.catalogServiceUrl = catalogServiceUrl;
        this.frontEndServiceUrl = frontEndServiceUrl;
        this.useRaft = useRaft;
        this.raftNode = raftNode;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        executorService.submit(new OrderRequestHandlerImpl(exchange,orderServiceReplicaMetaData,db,catalogServiceUrl,frontEndServiceUrl,useRaft,raftNode));
    }
}
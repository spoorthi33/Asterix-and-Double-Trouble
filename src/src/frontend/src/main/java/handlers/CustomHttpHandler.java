package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import impl.FrontEndRequestHandlerImpl;
import metadata.OrderServiceReplicasMetadata;
import model.Response;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class CustomHttpHandler implements HttpHandler {
    private ExecutorService executorService;
    private String catalogServiceUrl;
    private OrderServiceReplicasMetadata orderServiceReplicasMetadata;
    private Map<String, Response> cache;

    private Boolean isCacheEnabled;

    /**
     * Constructor to initialize the executor service and service name.
     * @param executorService - Thread pool to handle the incoming requests.
     * @param catalogServiceUrl - Catalog service URL.
     * @param orderServiceReplicasMetadata - orderServiceReplicasMetadata
     */
    public CustomHttpHandler(ExecutorService executorService,Boolean isCacheEnabled, Map<String, Response> cache, String catalogServiceUrl, OrderServiceReplicasMetadata orderServiceReplicasMetadata){
        this.executorService = executorService;
        this.catalogServiceUrl = catalogServiceUrl;
        this.orderServiceReplicasMetadata = orderServiceReplicasMetadata;
        this.cache = cache;
        this.isCacheEnabled = isCacheEnabled;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        executorService.submit(new FrontEndRequestHandlerImpl(exchange,isCacheEnabled,cache,catalogServiceUrl,orderServiceReplicasMetadata));
    }
}

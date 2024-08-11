package handlers;

import db.DB;
import impl.CatalogRequestHandlerImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class CustomHttpHandler implements HttpHandler {
    private ExecutorService executorService;
    private String frontendUrl;
    private DB db;

    private CacheInvalidationHandler cacheInvalidationHandler;

    /**
     * Constructor to initialize the executor service and service name.
     * @param executorService - Thread pool to handle the incoming requests.
     * @param frontendUrl - Name of the service to which the request should be routed.
     * @param db - DB(product catalog in case of catalog service)
     * @param cacheInvalidationHandler - Cache Invalidation Handler
     */
    public CustomHttpHandler(ExecutorService executorService, String frontendUrl, DB db,CacheInvalidationHandler cacheInvalidationHandler){
        this.executorService = executorService;
        this.frontendUrl = frontendUrl;
        this.db = db;
        this.cacheInvalidationHandler = cacheInvalidationHandler;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        executorService.submit(new CatalogRequestHandlerImpl(exchange,frontendUrl,db,cacheInvalidationHandler));
    }
}

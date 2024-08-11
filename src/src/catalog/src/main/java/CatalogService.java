import db.ProductCatalog;
import handlers.CacheInvalidationHandler;
import handlers.CustomHttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CatalogService {
    private static final int THREAD_POOL_SIZE = 10;
    private static ProductCatalog productCatalog;

    private static String catalogFilePath = "/Users/ajithkrishnakanduri/Desktop/productCatalog.csv";
    private static String frontServiceURL = "http://localhost:8889";

    private static Boolean isCacheEnabled = false;
    public static void main(String[] args) throws IOException {
        int port = 9999;

        if(args!=null){
            if(args.length>=1){
                port = Integer.parseInt(args[0]);
                System.out.println("Reading port from args "+port);
            }
            if(args.length>=2){
                catalogFilePath = args[1];
                System.out.println("Reading order file path from args "+catalogFilePath);
            }
            if(args.length>=3){
                frontServiceURL = args[2];
                System.out.println("Front-end service URL "+frontServiceURL);
            }
            if(args.length>=4 && args[3]!=null){
                if(args[3].equals("true")){
                    isCacheEnabled = true;
                }
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        CacheInvalidationHandler cacheInvalidationHandler = new CacheInvalidationHandler(isCacheEnabled,frontServiceURL);

        // load catalog from csv to memory
        initDb(catalogFilePath,cacheInvalidationHandler);
        server.createContext("/", new CustomHttpHandler(executor,"catalog",productCatalog,cacheInvalidationHandler));
        server.setExecutor(executor);

        server.start();
        System.out.println("Catalog Service started on port " + port);
        // Shutdown the database when the service exits
        Runtime.getRuntime().addShutdownHook(new Thread(CatalogService::shutdownDb));
    }

    private static void initDb(String catalogFilePath,CacheInvalidationHandler cacheInvalidationHandler){
        productCatalog = new ProductCatalog(catalogFilePath,cacheInvalidationHandler);
    }

    /**
     * Method to shut down the database.
     */
    private static void shutdownDb() {
        if (productCatalog != null) {
            productCatalog.shutdown();
            System.out.println("Database shutdown successfully.");
        }
    }
}

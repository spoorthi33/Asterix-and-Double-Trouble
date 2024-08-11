import com.sun.net.httpserver.HttpServer;
import db.OrderDB;
import handlers.CustomHttpHandler;
import metadata.OrderServiceReplicaMetaData;
import raft.RaftNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OrderService class is used to start the Order service.
 */
public class OrderService {
    private static final int THREAD_POOL_SIZE = 10;

    private static OrderDB orderDB;

    private static String orderFilePath = "/Users/ajithkrishnakanduri/Desktop/order1.csv";

    private static String CATALOG_SERVICE_URL = "http://localhost:9999";
    private static String FRONTEND_SERVICE_URL = "http://localhost:8889";

    private static String CURRENT_REPLICA_URL = "http://localhost:11111";

    private static Boolean useRaft = false;
    private static String raftLogFilePath = "/Users/ajithkrishnakanduri/Desktop/orderLog1.csv";

    public static void main(String[] args) throws IOException {
        int port = 11111;
        int ID = 1;
        if(args!=null){
            if(args.length>=1){
                port = Integer.parseInt(args[0]);
                System.out.println("Reading port from args "+port);
            }
            if(args.length>=2){
                ID = Integer.parseInt(args[1]);
                System.out.println("Id of the replica is "+ID);
            }
            if(args.length>=3){
                orderFilePath = args[2];
                System.out.println("Reading order file path from args "+orderFilePath);
            }
            if(args.length>=4){
                CATALOG_SERVICE_URL = args[3];
                System.out.println("Reading catalog url from args "+CATALOG_SERVICE_URL);
            }
            if(args.length>=5){
                FRONTEND_SERVICE_URL = args[4];
                System.out.println("Reading frontend url from args "+FRONTEND_SERVICE_URL);
            }
            if(args.length>=6){
                CURRENT_REPLICA_URL = args[5];
                System.out.println("Reading current replica url from args "+CURRENT_REPLICA_URL);
            }
            if(args.length>=7 && args[6]!=null){
                useRaft = args[6].equals("True");
            }
            if(args.length>=8){
                raftLogFilePath = args[7];
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        initDb(orderFilePath);

        OrderServiceReplicaMetaData orderServiceReplicaMetaData = new OrderServiceReplicaMetaData(orderDB);
        orderServiceReplicaMetaData.setReplicaData(ID,CURRENT_REPLICA_URL);
        Boolean hasJoinedCluster = orderServiceReplicaMetaData.joinCluster(FRONTEND_SERVICE_URL,useRaft);

        RaftNode raftNode = new RaftNode(orderServiceReplicaMetaData,CATALOG_SERVICE_URL,orderDB,raftLogFilePath,hasJoinedCluster);
        server.createContext("/", new CustomHttpHandler(executor,orderServiceReplicaMetaData,orderDB,CATALOG_SERVICE_URL,FRONTEND_SERVICE_URL,useRaft,raftNode));
        server.setExecutor(executor);

        server.start();
        System.out.println("Order Service started on port " + port);

        // Shutdown the database when the service exits
        Runtime.getRuntime().addShutdownHook(new Thread(OrderService::shutdownDb));
    }



    /**
     * Method to open DB file.
     * @param orderFilePath
     */
    private static void initDb(String orderFilePath){
        orderDB = new OrderDB(orderFilePath);
    }

    /**
     * Method to shut down the database.
     */
    private static void shutdownDb() {
        if (orderDB != null) {
            orderDB.shutdown();
            System.out.println("Database shutdown successfully.");
        }
    }
}
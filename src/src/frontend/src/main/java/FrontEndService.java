package frontend.src.main.java;

import com.sun.net.httpserver.HttpServer;
import handlers.CustomHttpHandler;
import metadata.OrderServiceReplicasMetadata;
import model.OrderServerReplica;
import model.Response;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FrontEndService {
    private static final int THREAD_POOL_SIZE = 10;
    private static String catalogServiceURL = "http://localhost:9999";
    private static String orderServiceURL = "http://localhost:11111";
    private static Map<String, Response> cache; // ToyName -> Response
    private static int CACHE_SIZE = 10;

    private static Boolean isCacheEnabled = false;

    public static void main(String[] args) throws IOException {
        int port = 8888;
        int orderServiceReplica1Id;
        String orderServiceReplica1URL;
        int orderServiceReplica2Id;
        String orderServiceReplica2URL;
        int orderServiceReplica3Id;
        String orderServiceReplica3URL;

        List<OrderServerReplica> orderServerReplicas = new ArrayList<>();
        // Load configuration from properties file
        Properties props = new Properties();


        try {
            InputStream input;
            if(args!=null && args[0]!=null){
                input = new FileInputStream(args[0]);
            }else{
                input = FrontEndService.class.getClassLoader().getResourceAsStream("config.properties");
            }
            if (input != null) {
                props.load(input);
                port = Integer.parseInt(props.getProperty("port"));
                catalogServiceURL = props.getProperty("catalogServiceURL");

                orderServiceReplica1Id = Integer.parseInt(props.getProperty("orderServiceReplica1Id"));
                orderServiceReplica1URL = props.getProperty("orderServiceReplica1URL");
                OrderServerReplica orderServerReplica1 = new OrderServerReplica(orderServiceReplica1Id,orderServiceReplica1URL);
                orderServerReplicas.add(orderServerReplica1);

                orderServiceReplica2Id = Integer.parseInt(props.getProperty("orderServiceReplica2Id"));
                orderServiceReplica2URL = props.getProperty("orderServiceReplica2URL");
                OrderServerReplica orderServerReplica2 = new OrderServerReplica(orderServiceReplica2Id,orderServiceReplica2URL);
                orderServerReplicas.add(orderServerReplica2);

                orderServiceReplica3Id = Integer.parseInt(props.getProperty("orderServiceReplica3Id"));
                orderServiceReplica3URL = props.getProperty("orderServiceReplica3URL");
                OrderServerReplica orderServerReplica3 = new OrderServerReplica(orderServiceReplica3Id,orderServiceReplica3URL);
                orderServerReplicas.add(orderServerReplica3);

                if(props.getProperty("isCacheEnabled").equals("true")){
                    isCacheEnabled = true;
                }

                System.out.println("Read props from config file"+port+" "+catalogServiceURL);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // init cache if its enabled.
        if(isCacheEnabled){
            cache = new LinkedHashMap<String, Response>(CACHE_SIZE, 0.75F, true) {
                protected boolean removeEldestEntry(Map.Entry<String, Response> eldest) {
                    boolean isEvicting = size() > CACHE_SIZE;
                    if (isEvicting) {
                        System.out.println("Evicted item from cache: " + eldest.getKey());
                    }
                    return isEvicting;
                }
            };
        }


        OrderServiceReplicasMetadata orderServiceReplicasMetadata = new OrderServiceReplicasMetadata();
        orderServiceReplicasMetadata.setOrderServiceReplicas(orderServerReplicas);
        OrderServerReplica leaderReplica = orderServiceReplicasMetadata.findLeaderNode();
        if(leaderReplica.getId()==-1){
            System.out.println("None of the order service replicas are alive, start any of order service replicas");
            System.exit(-1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        server.createContext("/", new CustomHttpHandler(executor,isCacheEnabled,cache,catalogServiceURL,orderServiceReplicasMetadata));
        server.setExecutor(executor);

        server.start();
        System.out.println("Front-End Service started on port " + port);
    }


}
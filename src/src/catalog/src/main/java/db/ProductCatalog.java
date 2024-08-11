package db;

import handlers.CacheInvalidationHandler;
import model.Item;
import org.json.JSONArray;


import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ProductCatalog class is used to manage the product catalog data.
 * In real-scenario, this class should be only accessible from catalog service. But for simplicity purpose we have kept it here.
 */
public class ProductCatalog implements db.DB {
    private ConcurrentHashMap<String, Item> productCatalog;
    private static String CSV_FILE_PATH = "/Users/ajithkrishnakanduri/Desktop/CS677/labs/lab2/spring24-lab2-spring24-lab2-ajithkanduri0-spoorthi33/src/part1/src/main/java/catalog/src/main/resources/productcatalog.csv";
    private final HashMap<String, Object> locks;
    private final Object loadLock; // Lock for loading data from CSV
    private boolean isDataLoaded = false; // Flag to track if data is loaded
    private final ScheduledExecutorService executorService;

    private final ScheduledExecutorService restockExecutorService;

    private CacheInvalidationHandler cacheInvalidationHandler;

    /**
     * Here we are using 2 locks to synchronize the access to the product catalog data.
     * 1. loadLock - Lock to synchronize the loading of data from CSV.
     * 2. locks - Locks to synchronize the access to the individual items in the product catalog as we need not lock entire product catalog, we can just take lock on the item which is being accessed. 
     * We are using ConcurrentHashMap to store the product catalog data as it provides thread-safe operations.
     * Constructor to initialize the product catalog and load the data from CSV and schedule task to write data to CSV.
     */
    public ProductCatalog(String catalogFilePath, CacheInvalidationHandler cacheInvalidationHandler){
        this.locks = new HashMap<>();
        this.loadLock = new Object();
        this.executorService = Executors.newScheduledThreadPool(1);
        this.restockExecutorService = Executors.newScheduledThreadPool(1);
        this.cacheInvalidationHandler = cacheInvalidationHandler;

        if(catalogFilePath!=null){
            CSV_FILE_PATH = catalogFilePath;
        }

        if(System.getenv("isContainer")!=null){
            CSV_FILE_PATH = "/app/src/main/resources/productcatalog.csv";
        }
        loadDataFromCSV(); // Load data from CSV
        scheduleCsvUpdateTask(); // Schedule task to write data to CSV
        scheduleRestock(); // Schedule restocking of items
    }

    /**
     * Method to query an item from product catalog.
     * @param name - Name of the item to query.
     */
    public Item queryItem(String name){
        // check if data is loaded, if not wait until data is loaded
        if(!isDataLoaded){
            waitUntilDataIsLoaded();
        }
        // Acquire lock before accessing the item
        synchronized (getLock(name)) {
            return this.productCatalog.get(name);
        }
    }

    /**
     * Method to buy an item from product catalog.
     * @param name - Name of the item to buy.
     * @param quantity - Quantity change
     * @param operation - remove
     * @return
     */
    public int buyItem(String name,int quantity,String operation){
        // check if data is loaded, if not wait until data is loaded
        System.out.println("Requested item: "+name+" quantity: "+quantity);
        if(!isDataLoaded){
            waitUntilDataIsLoaded();
        }

        try{
            // Acquire lock before accessing the item
            synchronized (getLock(name)) {
                Item itemDetails = this.productCatalog.get(name);
                if(itemDetails!=null){
                    int availableQuantity = itemDetails.getQuantity();
                    if(operation.equals("remove")){
                        System.out.println("Available"+availableQuantity);
                        if(availableQuantity>=quantity){
                            itemDetails.setQuantity(availableQuantity-quantity);
                            System.out.println("Update Successful!!");
                            return 1; // Return 1 if item is successfully bought
                        }else{
                            return 0; // Return 0 if item is out of stock
                        }
                    }else if(operation.equals("add")){
                        itemDetails.setQuantity(availableQuantity+quantity);
                        return 1;
                    }else{
                        return -1;
                    }
                }else{
                    return -1;
                }
            }
        }catch (Exception e) {
            return -1; // Return -1 for item not found
        }
    }

    /**
     * Method to load data from csv file.
     */
    private void loadDataFromCSV(){
        // Taking lock to load data from CSV
        synchronized (loadLock) {
            productCatalog = new ConcurrentHashMap<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(CSV_FILE_PATH))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 3) {
                        try {
                            String itemName = parts[0];
                            int quantity = Integer.parseInt(parts[1]);
                            Double price = Double.parseDouble(parts[2]);
                            Item item = new Item(itemName, quantity, price);
                            productCatalog.put(itemName, item);
                        } catch (Exception e) {
                            System.out.println("Some error while reading line from file: " + line);
                        }
                    } else {
                        System.out.println("Line is not in expected format: " + line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                isDataLoaded = true; // Set flag to indicate data is loaded
                loadLock.notifyAll(); // Notify waiting threads that data loading is complete
            }
        }
    }

    /**
     * Method to write data to csv file.
     */
    private void writeDataToCSV(){
        isDataLoaded = false;
        synchronized (loadLock) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE_PATH))) {
                for (Map.Entry<String, Item> entry : productCatalog.entrySet()) {
                    Item item = entry.getValue();
                    writer.println(item.getName() + "," + item.getQuantity() + "," + item.getPrice());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                isDataLoaded = true; // Set flag to indicate data is written
                loadLock.notifyAll(); // Notify waiting threads that data writing is complete.
            }
        }
    }

    /**
     * Method to restock items in catalog.
     */
    private void restockCatalog(){
        isDataLoaded = false;
        JSONArray emptyItems = new JSONArray();
        synchronized (loadLock){
            try {
                for(Item item:productCatalog.values()){
                    if(item.getQuantity()<=0){
                        System.out.println("Restocking for item "+item);
                        item.setQuantity(100);
                        emptyItems.put(item.getName());
                    }
                }
            } catch (Exception e){ System.out.println("Error while restocking items");}
            finally {
                isDataLoaded = true; // Set flag to indicate data is updated
                loadLock.notifyAll(); // Notify waiting threads that data updates is complete.
            }
        }

        // now call invalidate cache if there are any emptyItems.
        if(!emptyItems.isEmpty()){
            System.out.println("Invalidating cache for re-stocked items");
            cacheInvalidationHandler.makeBulkInvalidateCacheRequest(emptyItems);
        }
    }

    private void waitUntilDataIsLoaded(){
        synchronized (loadLock) {
            while (!isDataLoaded) {
                try {
                    loadLock.wait(); // Wait until data is loaded
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Get lock for individual item
    private Object getLock(String itemName) {
        locks.putIfAbsent(itemName, new Object());
        return locks.get(itemName);
    }

    /**
     * Method to schedule task to restock items every 10 seconds.
     */
    private void scheduleRestock() {
        System.out.println("Initiating task to restock items every 10 seconds.");
        executorService.scheduleAtFixedRate(this::restockCatalog, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * Method to schedule task to write data to CSV every minute.
     */
    private void scheduleCsvUpdateTask() {
        System.out.println("Initiating task to write data to CSV every minute.");
        executorService.scheduleAtFixedRate(this::writeDataToCSV, 0, 1, TimeUnit.MINUTES);
    }

    /**
     * Method to gracefully shut down the executor service.
     */
    @Override
    public void shutdown() {
        writeDataToCSV(); // Write data to CSV before shutting down to avoid data loss
        executorService.shutdown();
        restockExecutorService.shutdown();
    }

}

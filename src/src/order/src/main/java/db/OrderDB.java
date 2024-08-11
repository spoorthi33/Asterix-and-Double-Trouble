package db;



import dto.LostOrdersDto;
import dto.OrderDto;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderDB implements DB {
    private static String ORDER_FILE_PATH;
    private AtomicInteger orderNumber;
    private ExecutorService executor;
    private final Object fileLock = new Object();

    public OrderDB(String orderFilePath){

        if(orderFilePath!=null){
            ORDER_FILE_PATH = orderFilePath;
        }
        if(System.getenv("isContainer")!=null){
            ORDER_FILE_PATH = "/app/src/main/resources/ordercatalog.csv";
        }
        this.orderNumber = new AtomicInteger(loadMaxOrderNumber());
        this.executor = Executors.newSingleThreadExecutor(); // Creating a single-threaded executor
    }

    /**
     * Method to load the maximum order number from the CSV file.
     * @return
     */
    private int loadMaxOrderNumber() {
        int maxOrderNumber = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(ORDER_FILE_PATH))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                int orderId = Integer.parseInt(parts[0]);
                maxOrderNumber = Math.max(maxOrderNumber, orderId);
            }
        } catch (IOException e) {
            e.printStackTrace(); // Handle or log the exception properly
        }
        return maxOrderNumber;
    }

    /**
     * Method to return current orderId of this replica
     * @return - current orderId of this replica
     */
    public int getCurrentOrderId(){
        return orderNumber.get();
    }

    /**
     * Method to place an order for an item. Synchronized to avoid race conditions for order number generation.
     * @param itemName - Name of the item to order.
     * @param quantity - Quantity of the item to order.
     * @return - Order number for the placed order.
     */
    public synchronized int placeOrder(String itemName, int quantity) {
        // Generate order number
        int newOrderNumber = orderNumber.incrementAndGet();

        // Schedule order log writing task asynchronously
        executor.submit(() -> writeOrderLog(newOrderNumber, itemName, quantity));

        return newOrderNumber;
    }

    /**
     * Method to write propagated order details
     * @param orderId - Id of the order
     * @param itemName - Name of the item to order.
     * @param quantity - Quantity of the item ordered.
     */
    public void writePropagatedOrder(int orderId, String itemName, int quantity){
        // update orderNumber pointer to propagated orderId to maintain consistency
        this.orderNumber = new AtomicInteger(orderId);
        writeOrderLog(orderId,itemName,quantity);
    }

    public OrderDto getOrderInfo(int orderId){
        synchronized (fileLock) {
            OrderDto orderDto = null; //dummy
            try (BufferedReader reader = new BufferedReader(new FileReader(ORDER_FILE_PATH))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    int csvOrderId = Integer.parseInt(parts[0]);
                    if (csvOrderId==orderId) {
                        String itemName = parts[1];
                        int quantity = Integer.parseInt(parts[2]);
                        orderDto = new OrderDto(itemName,quantity);
                    }
                }
                return orderDto;
            } catch (IOException e) {
                System.out.println("Error while syncing data"+e);
                return null;
            }
        }
    }

    public List<LostOrdersDto> getLostData(int lastOrderId){
        synchronized (fileLock) {
            List<LostOrdersDto> lostOrdersDtos = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(ORDER_FILE_PATH))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    int orderId = Integer.parseInt(parts[0]);
                    if (orderId > lastOrderId) {
                        String itemName = parts[1];
                        int quantity = Integer.parseInt(parts[2]);
                        lostOrdersDtos.add(new LostOrdersDto(itemName,quantity,orderId));
                    }
                }
                return lostOrdersDtos;
            } catch (IOException e) {
                System.out.println("Error while syncing data"+e);
                return null;
            }
        }
    }

    public void syncLostData(List<LostOrdersDto> lostOrdersDtos){
        synchronized (fileLock){
            // Write order log to CSV file
            try (FileWriter writer = new FileWriter(ORDER_FILE_PATH, true)) {
                // Append order details to CSV file
//                writer.append(orderNumber + "," + itemName + "," + quantity + "\n");
//                writer.flush();
                for(LostOrdersDto lostOrdersDto:lostOrdersDtos){
                    writer.append(lostOrdersDto.getOrderId() + "," + lostOrdersDto.getName() + "," + lostOrdersDto.getQuantity() + "\n");
                }
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace(); // Handle or log the exception properly
            }
        }
    }

    /**
     * Method to write the order log to a CSV file.
     * @param orderNumber - Order number for the placed order.
     * @param itemName - Name of the item ordered.
     * @param quantity - Quantity of the item ordered.
     */
    private void writeOrderLog(int orderNumber, String itemName, int quantity) {
        synchronized (fileLock){
            // Write order log to CSV file
            try (FileWriter writer = new FileWriter(ORDER_FILE_PATH, true)) {
                // Append order details to CSV file
                writer.append(orderNumber + "," + itemName + "," + quantity + "\n");
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace(); // Handle or log the exception properly
            }
        }
    }

    // Shutdown the executor service when no longer needed
    @Override
    public void shutdown() {
        executor.shutdown();
    }
}

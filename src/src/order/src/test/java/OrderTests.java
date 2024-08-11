import db.OrderDB;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class OrderTests {

    // Update absolute path of ordercatalog file here.
    private static String orderFilePath = "/Users/spoorthisiri/Desktop/order1.csv";
    @Test
    public void testPlaceOrder() {
        OrderDB orderDB = new OrderDB(orderFilePath);
        int orderNumber = orderDB.placeOrder("Item1", 5);
        
        // Verify that the order number is greater than 0
        assertTrue(orderNumber > 0);
    }

    @Test
    public void testPlaceOrder2() {
        OrderDB orderDB = new OrderDB(orderFilePath);
        int orderNumber1 = orderDB.placeOrder("Item1", 5);
        int orderNumber2 = orderDB.placeOrder("Item1", 5);

        // Verify that the order_number2 is greater than order_number1 to verify synchronization
        assertTrue(orderNumber2 > orderNumber1);
    }

}

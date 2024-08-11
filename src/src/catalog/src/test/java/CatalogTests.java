import db.ProductCatalog;
import model.Item;
import org.junit.Test;

import static org.junit.Assert.*;

public class CatalogTests {

    // Update absolute path of ordercatalog file here.
    private static String catalogFilePath = "/Users/spoorthisiri/Desktop/productCatalog.csv";
    ProductCatalog productCatalog = new ProductCatalog(catalogFilePath,null);
    @Test
    public void testNegQuery() {
        Item item = productCatalog.queryItem("Item1");

        // Verify that the item is not present
        assertNull(item);
    }

    @Test
    public void testPosQuery() {
        Item item = productCatalog.queryItem("Whale");
        // Verify that the item is present
        assertNotNull(item);
    }

    @Test
    public void testPosUpdate(){
        int status = productCatalog.buyItem("Whale",1,"remove");
        System.out.println(status);
        assertEquals(1 , status);
    }

    @Test
    public void testNegUpdate(){

        int status = productCatalog.buyItem("Item1",1,"remove");
        System.out.println(status);
        assertEquals(-1 , status);
    }
}

package tests;

import static tests.TestUtils.*;

public class CacheTests {
    private static void TestCase1(){
        query("Whale");
        query("Whale");
    }

    private static void TestCase2(){
        // cache size is set to 10
        for (int i = 0; i < 11; i++) {
            query(itemsList[i]);
        }
        query("Lego");
    }

    private static void TestCase3(){
        query("Lego");
        query("Lego");
        buy("Lego",2);
        query("Lego");
    }

    private static void TestCase4(){
        for(int i=0;i<110;i++){
            buy("Tux",1);
        }
    }
}

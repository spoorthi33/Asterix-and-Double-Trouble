package tests;

import static tests.TestUtils.buy;
import static tests.TestUtils.itemsList;

public class ReplicationTests {

    private static void TestCase1(){
        for (int i = 0; i < itemsList.length; i++) {
            buy(itemsList[i],1);
        }
    }

    private static void TestCase2(){
        for (int i = 0; i < 5; i++) {
            buy(itemsList[i],1);
        }
    }

}

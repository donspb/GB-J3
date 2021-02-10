package practice07;


public class TestForTest {

    @BeforeSuite
    public static void beforeTests() {
        System.out.println("First. Before suit");
    }

    @AfterSuite
    public static  void afterTests() {
        System.out.println("Last. After suit");
    }

    @Test(priority = 5)
    public static  void testOne() {
        System.out.println("Test with 5 priority");
    }

    @Test(priority = 3)
    public static  void testTwo() {
        System.out.println("Test with 3 priority");
    }

    @Test(priority = 3)
    public static  void testThree() {
        System.out.println("Test with 3 priority");
    }

    @Test(priority = 10)
    public static  void testFour() {
        System.out.println("Test with 10 priority");
    }

    @Test(priority = 1)
    public static  void testFive() {
        System.out.println("Test with 1 priority");
    }

}

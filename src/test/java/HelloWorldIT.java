import org.jsoup.Jsoup;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class HelloWorldIT {

    @Test
    public void testHelloWorld() throws Exception {
        assertTrue(Jsoup.connect("http://192.168.56.2:8080/maven-vbox-plugin-example").get().html().contains("Hello World"));
    }
}

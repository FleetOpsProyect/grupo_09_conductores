package co.edu.univalle.conductores;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MsConductoresApplicationTests {

    @Test
    void contextLoads() {
    }

}

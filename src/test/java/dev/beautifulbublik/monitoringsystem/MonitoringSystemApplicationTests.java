package dev.beautifulbublik.monitoringsystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MonitoringSystemApplicationTests {

    @Test
    @DisplayName("The context starts up: all beans wire together, configuration parses")
    void contextLoads() {
    }
}

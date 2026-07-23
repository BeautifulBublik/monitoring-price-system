package dev.beautifulbublik.monitoringsystem;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PriceMonitorProperties.class)
public class MonitoringSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringSystemApplication.class, args);
    }

}

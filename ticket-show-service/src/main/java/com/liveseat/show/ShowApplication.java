package com.liveseat.show;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@MapperScan("com.liveseat.show.mapper")
@SpringBootApplication(scanBasePackages = "com.liveseat")
public class ShowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShowApplication.class, args);
    }
}

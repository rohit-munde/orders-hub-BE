package com.indiedev.orders_hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@ConfigurationPropertiesScan
public class OrdersHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrdersHubApplication.class, args);
	}

}

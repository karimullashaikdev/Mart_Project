package com.karim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SuperMarketProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(SuperMarketProjectApplication.class, args);
	}

}

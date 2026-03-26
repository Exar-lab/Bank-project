package com.banco.co;

import com.banco.co.fraud.config.FraudDetectionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(FraudDetectionProperties.class)
public class BancoServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BancoServiceApplication.class, args);
	}

}

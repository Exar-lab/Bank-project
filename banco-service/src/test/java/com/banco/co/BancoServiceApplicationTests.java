package com.banco.co;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = BancoServiceApplicationTests.MinimalContext.class,
		properties = "spring.task.scheduling.enabled=false")
class BancoServiceApplicationTests {

	@SpringBootConfiguration
	static class MinimalContext {
	}

	@Test
	void contextLoads() {
	}

}

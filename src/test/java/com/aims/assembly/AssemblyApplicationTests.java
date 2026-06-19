package com.aims.assembly;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.kafka.bootstrap-servers=localhost:9092",
		"app.kafka.security-protocol=PLAINTEXT",
		"app.kafka.listeners-enabled=false"
})
class AssemblyApplicationTests {

	@Test
	void contextLoads() {
	}

}

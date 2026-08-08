package com.mac.alert;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
class AlertApplicationTests {

	@Test
	void applicationDeclaresSpringBootConfiguration() {
		assertNotNull(AlertApplication.class.getAnnotation(
				org.springframework.boot.autoconfigure.SpringBootApplication.class));
	}

}

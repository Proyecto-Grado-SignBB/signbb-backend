package org.signbb.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de humo: comprueba que el contexto de Spring ({@code @SpringBootTest})
 * carga sin errores. Requiere PostgreSQL y Redis arriba (como el CI); ver
 * \ref pruebas.
 */
@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}

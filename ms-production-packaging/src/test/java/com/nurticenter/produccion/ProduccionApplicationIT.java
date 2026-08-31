package com.nurticenter.produccion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TEST DE INTEGRACION (no unitario).
 *
 * Levanta el contexto completo de Spring, por lo que necesita infraestructura
 * real (datasource). Queda EXCLUIDO de `mvn test` por la configuracion de
 * maven-surefire-plugin (patron *IT.java): en esta entrega solo se ejecutan
 * pruebas unitarias. Se habilitara en la fase de tests de integracion.
 */
@SpringBootTest
class ProduccionApplicationIT {

    @Test
    void contextLoads() {
    }
}

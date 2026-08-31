package com.nurticenter.produccion.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de OpenAPI 3 / Swagger UI.
 * UI disponible en: http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI produccionOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("BC4 - Produccion y Envasado API")
                .description("""
                        API REST del bounded context de Produccion (NurtiCenter).
                        Gestiona ordenes de produccion y paquetes etiquetados
                        siguiendo DDD + Clean Architecture.

                        Flujo tipico:
                        1. POST /api/v1/ordenes            -> generar orden
                        2. POST .../iniciar-preparacion    -> PENDIENTE a EN_PREPARACION
                        3. POST .../items/{recetaId}/preparar
                        4. POST /api/v1/paquetes           -> crear paquete del paciente
                        5. POST .../envasar, .../etiquetar, .../marcar-listo
                        6. POST .../ordenes/{id}/completar -> COMPLETADA
                        """)
                .version("1.0.0")
                .contact(new Contact().name("NurtiCenter").email("soporte@nurticenter.com"))
                .license(new License().name("MIT")));
    }
}

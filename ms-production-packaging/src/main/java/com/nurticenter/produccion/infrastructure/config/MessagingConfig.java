package com.nurticenter.produccion.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.infrastructure.messaging.LectorCalendarioConsolidado;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumo de eventos de otros bounded contexts. El lector (ACL de entrada) existe
 * siempre; el listener de Kafka solo con app.kafka.enabled=true.
 */
@Configuration
public class MessagingConfig {

    @Bean
    LectorCalendarioConsolidado lectorCalendarioConsolidado(ObjectMapper objectMapper) {
        return new LectorCalendarioConsolidado(objectMapper);
    }
}

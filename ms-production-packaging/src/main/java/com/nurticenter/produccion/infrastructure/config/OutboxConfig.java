package com.nurticenter.produccion.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.infrastructure.outbox.MapeadorEventoIntegracion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Transactional outbox: el escritor guarda siempre los eventos en la tabla
 * outbox; el relay (que necesita @Scheduled) solo existe con app.kafka.enabled=true.
 */
@Configuration
@EnableScheduling
public class OutboxConfig {

    @Bean
    Clock reloj() {
        return Clock.system(ZoneId.of("America/La_Paz"));
    }

    @Bean
    MapeadorEventoIntegracion mapeadorEventoIntegracion(ObjectMapper objectMapper) {
        // El dominio registra occurredAt como LocalDateTime en la zona de la JVM
        return new MapeadorEventoIntegracion(objectMapper, ZoneId.systemDefault());
    }
}

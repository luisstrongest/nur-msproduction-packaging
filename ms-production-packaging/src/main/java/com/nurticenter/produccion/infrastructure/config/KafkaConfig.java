package com.nurticenter.produccion.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * BC4 es el dueno del topic de sus eventos de integracion: lo crea al arrancar
 * (KafkaAdmin) si todavia no existe.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    NewTopic topicEventosProduccion(@Value("${app.kafka.topic}") String topic,
                                    @Value("${app.kafka.partitions:3}") int particiones) {
        return TopicBuilder.name(topic)
                .partitions(particiones)
                .replicas(1)
                .build();
    }
}

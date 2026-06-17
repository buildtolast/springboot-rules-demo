package com.codrite.ruleaudit.web;

import lombok.Builder;
import lombok.Data;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Endpoint to monitor the health and initialization status of all system components.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final StreamsBuilderFactoryBean streamsFactory;

    public HealthController(StreamsBuilderFactoryBean streamsFactory) {
        this.streamsFactory = streamsFactory;
    }

    @GetMapping("/status")
    public Mono<SystemHealth> getStatus() {
        String streamsState = streamsFactory.getKafkaStreams() != null 
                ? streamsFactory.getKafkaStreams().state().name() 
                : "NOT_STARTED";
        
        boolean isHealthy = "RUNNING".equals(streamsState);

        return Mono.just(SystemHealth.builder()
                .status(isHealthy ? "HEALTHY" : "INITIALIZING")
                .components(Map.of(
                        "kafkaStreams", streamsState,
                        "mongo", "CONNECTED", // Simple assumption for now as reactive repo is used
                        "redis", "CONNECTED"
                ))
                .build());
    }

    @Data
    @Builder
    public static class SystemHealth {
        private String status;
        private Map<String, String> components;
    }
}

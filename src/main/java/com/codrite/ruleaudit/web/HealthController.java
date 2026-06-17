package com.codrite.ruleaudit.web;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint to monitor the health and initialization status of all system components.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final StreamsBuilderFactoryBean streamsFactory;
    private final ReactiveMongoTemplate mongoTemplate;
    private final ReactiveRedisConnectionFactory redisConnectionFactory;

    public HealthController(StreamsBuilderFactoryBean streamsFactory,
                            ReactiveMongoTemplate mongoTemplate,
                            ReactiveRedisConnectionFactory redisConnectionFactory) {
        this.streamsFactory = streamsFactory;
        this.mongoTemplate = mongoTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping("/status")
    public Mono<SystemHealth> getStatus() {
        return Mono.zip(
                getStreamsState(),
                getMongoStatus(),
                getRedisStatus()
        ).map(tuple -> {
            String streamsState = tuple.getT1();
            String mongoStatus = tuple.getT2();
            String redisStatus = tuple.getT3();

            boolean isHealthy = "RUNNING".equals(streamsState) 
                    && "CONNECTED".equals(mongoStatus) 
                    && "CONNECTED".equals(redisStatus);

            Map<String, String> components = new HashMap<>();
            components.put("kafkaStreams", streamsState);
            components.put("mongo", mongoStatus);
            components.put("redis", redisStatus);

            return SystemHealth.builder()
                    .status(isHealthy ? "HEALTHY" : "INITIALIZING")
                    .components(components)
                    .build();
        });
    }

    private Mono<String> getStreamsState() {
        return Mono.fromCallable(() -> 
            streamsFactory.getKafkaStreams() != null 
                ? streamsFactory.getKafkaStreams().state().name() 
                : "NOT_STARTED"
        ).onErrorReturn("ERROR");
    }

    private Mono<String> getMongoStatus() {
        return mongoTemplate.executeCommand("{\"ping\":1}")
                .map(res -> "CONNECTED")
                .onErrorReturn("DISCONNECTED");
    }

    private Mono<String> getRedisStatus() {
        return Mono.fromDirect(redisConnectionFactory.getReactiveConnection().ping())
                .map(res -> "CONNECTED")
                .onErrorReturn("DISCONNECTED");
    }

    @Data
    @Builder
    public static class SystemHealth {
        private String status;
        private Map<String, String> components;
    }
}

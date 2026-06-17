package com.codrite.ruleaudit.config;

import com.codrite.ruleaudit.eval.RuleEvaluator;
import com.codrite.ruleaudit.json.JsonContextFactory;
import com.codrite.ruleaudit.rules.RuleCache;
import com.codrite.ruleaudit.topology.RoutingProcessor;
import com.codrite.ruleaudit.topology.RoutingResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * StreamsBuilder DSL topology:
 *   source -> process(RoutingProcessor) -> RoutingResult
 *     |-> mapValues(auditJson).to(audit)               (every event)
 *     |-> filter(matched).mapValues(routedValue).to(target)  (matched only)
 *
 * The single process() node (new Processor API) still sees record metadata for
 * the deterministic auditId; the DSL splits its one RoutingResult into the two
 * sink topics. No repartition / intermediate Serde is introduced. Streams
 * properties (EOS, serdes, RF) come from spring.kafka.streams.* in application.yml;
 * the lifecycle is managed by Spring (started by PipelineStarter after rules load).
 */
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaStreamsConfig.class);

    @Bean
    public KStream<String, String> ruleAuditStream(
            StreamsBuilder builder,
            RuleEvaluator evaluator, RuleCache cache,
            JsonContextFactory jsonFactory, ObjectMapper mapper,
            @Value("${app.topics.source}") String sourceTopic,
            @Value("${app.topics.target}") String targetTopic,
            @Value("${app.topics.audit}") String auditTopic) {

        log.info("Creating Kafka Streams topology: source={} -> target={}, audit={}", 
                sourceTopic, targetTopic, auditTopic);

        Produced<String, String> asString = Produced.with(Serdes.String(), Serdes.String());
        Serde<RoutingResult> resultSerde = new JsonSerde<>(RoutingResult.class, mapper);

        KStream<String, String> source =
                builder.stream(sourceTopic, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, RoutingResult> evaluated = source.process(
                () -> new RoutingProcessor(evaluator, cache, jsonFactory, mapper),
                Named.as("evaluate"));

        // Explicitly set Serde for evaluated stream to avoid issues with missing default serdes
        // although process() doesn't strictly require it unless followed by repartition/state store
        // it's good practice. 

        evaluated.mapValues(RoutingResult::auditJson, Named.as("audit-value"))
                 .to(auditTopic, asString);

        evaluated.filter((k, v) -> v.matched(), Named.as("matched-filter"))
                 .mapValues(RoutingResult::routedValue, Named.as("routed-value"))
                 .to(targetTopic, asString);

        return source;
    }
}

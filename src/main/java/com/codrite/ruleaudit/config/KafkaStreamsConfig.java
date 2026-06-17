package com.codrite.ruleaudit.config;

import com.codrite.ruleaudit.eval.RuleEvaluator;
import com.codrite.ruleaudit.json.JsonContextFactory;
import com.codrite.ruleaudit.rules.RuleCache;
import com.codrite.ruleaudit.topology.RoutingProcessor;
import com.codrite.ruleaudit.topology.RoutingResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    /**
     * Defines the Kafka Streams topology using the DSL and Processor API.
     * <p>
     * The topology consumes from a source topic, evaluates rules using a {@link RoutingProcessor},
     * and branches the results into an audit topic (for all records) and a target topic (for matched records).
     *
     * @param builder      The StreamsBuilder provided by Spring.
     * @param evaluator    The rule evaluator for SpEL expressions.
     * @param cache        The local cache of rules.
     * @param jsonFactory  Utility to convert JSON strings to evaluation contexts.
     * @param mapper       Jackson ObjectMapper for serialization.
     * @param sourceTopic  Name of the input topic.
     * @param targetTopic  Name of the topic for matched events.
     * @param auditTopic   Name of the topic for evaluation audit logs.
     * @return The source KStream.
     */
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

        // Define serialization for sink topics
        Produced<String, String> asString = Produced.with(Serdes.String(), Serdes.String());
        
        // Define source stream consuming raw JSON strings
        KStream<String, String> source =
                builder.stream(sourceTopic, Consumed.with(Serdes.String(), Serdes.String()));

        // Use the Processor API for rule evaluation to maintain access to record metadata (offset, partition)
        // required for deterministic audit IDs.
        KStream<String, RoutingResult> evaluated = source.process(
                () -> new RoutingProcessor(evaluator, cache, jsonFactory, mapper),
                Named.as("evaluate"));

        // Route 1: All evaluations go to the Audit topic for persistence in MongoDB
        evaluated.flatMapValues(RoutingResult::auditJsons, Named.as("audit-values"))
                 .to(auditTopic, asString);

        // Route 2: Only records that matched at least one active rule go to the Target topic
        evaluated.filter((k, v) -> v.matched(), Named.as("matched-filter"))
                 .mapValues(RoutingResult::routedValue, Named.as("routed-value"))
                 .to(targetTopic, asString);

        return source;
    }
}

package com.codrite.ruleaudit.demo;

import com.codrite.ruleaudit.audit.AuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Demo driver component enabled via the "demo" Spring profile.
 * <p>
 * This runner automates an end-to-end test of the system by:
 * <ol>
 *     <li>Generating a batch of synthetic JSON events.</li>
 *     <li>Producing these events to the Kafka source topic.</li>
 *     <li>Waiting for the Kafka Streams pipeline and Audit consumer to process them.</li>
 *     <li>Querying MongoDB to generate a summary report of evaluation outcomes.</li>
 * </ol>
 */
@Slf4j
@Component
@Order(2)
@Profile("demo")
public class DemoRunner implements ApplicationRunner {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MongoTemplate mongoTemplate;
    private final String sourceTopic;
    private final int messageCount;
    private final long settleMillis;
    private final long publishDelayMs;

    public DemoRunner(KafkaTemplate<String, String> kafkaTemplate,
                      MongoTemplate mongoTemplate,
                      @Value("${app.topics.source}") String sourceTopic,
                      @Value("${app.demo.message-count}") int messageCount,
                      @Value("${app.demo.settle-millis}") long settleMillis,
                      @Value("${app.simulation.publish-delay-ms:5}") long publishDelayMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.mongoTemplate = mongoTemplate;
        this.sourceTopic = sourceTopic;
        this.messageCount = messageCount;
        this.settleMillis = settleMillis;
        this.publishDelayMs = publishDelayMs;
    }

    /**
     * Executes the demo workflow.
     * @param args Application arguments.
     * @throws Exception if data generation or Kafka communication fails.
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Step 1: Generate synthetic messages using the helper
        List<String> messages = DemoMessages.generate(messageCount);
        
        // Step 2: Send messages to the source topic, paced so they are not bursted
        for (int i = 0; i < messages.size(); i++) {
            kafkaTemplate.send(sourceTopic, messages.get(i));
            if (publishDelayMs > 0 && i < messages.size() - 1) {
                Thread.sleep(publishDelayMs);
            }
        }
        kafkaTemplate.flush();
        log.info("DEMO: produced {} messages to '{}'", messages.size(), sourceTopic);

        // Step 3: Wait for asynchronous processing to complete
        log.info("DEMO: waiting {}ms for processing to settle...", settleMillis);
        Thread.sleep(settleMillis);

        // Step 4: Aggregate results from MongoDB for reporting
        long total = mongoTemplate.count(new Query(), AuditRecord.class);
        long matched = countByType("MATCHED");
        long unmatched = countByType("UNMATCHED");
        long errored = countByType("ERRORED");

        log.info("==================== DEMO RESULTS ====================");
        log.info("messages produced : {}", messages.size());
        log.info("audit docs in Mongo: {}", total);
        log.info("  MATCHED   : {}", matched);
        log.info("  UNMATCHED : {}", unmatched);
        log.info("  ERRORED   : {}", errored);
        log.info("----------------------------------------------------");
        
        // Print a few samples to the console
        List<AuditRecord> samples = mongoTemplate.find(new Query().limit(5), AuditRecord.class);
        for (AuditRecord r : samples) {
            log.info("sample {} {} rule={} event={}",
                    r.auditType(), r.auditId().substring(0, 8), r.ruleId(), r.sourceEvent());
        }
        log.info("=====================================================");
    }

    /**
     * Helper to count audit records by their verdict type.
     * @param type The audit verdict type.
     * @return The count of documents matching the type.
     */
    private long countByType(String type) {
        return mongoTemplate.count(new Query(Criteria.where("auditType").is(type)), AuditRecord.class);
    }
}

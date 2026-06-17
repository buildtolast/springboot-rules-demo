package com.codrite.ruleaudit.demo;

import com.codrite.ruleaudit.audit.AuditRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Demo driver (profile "demo"): produces N messages to the source topic, lets the
 * pipeline settle, then reports the audit outcomes recorded in Mongo.
 */
@Component
@Order(2)
@Profile("demo")
public class DemoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MongoTemplate mongoTemplate;
    private final String sourceTopic;
    private final int messageCount;
    private final long settleMillis;

    public DemoRunner(KafkaTemplate<String, String> kafkaTemplate,
                      MongoTemplate mongoTemplate,
                      @Value("${app.topics.source}") String sourceTopic,
                      @Value("${app.demo.message-count}") int messageCount,
                      @Value("${app.demo.settle-millis}") long settleMillis) {
        this.kafkaTemplate = kafkaTemplate;
        this.mongoTemplate = mongoTemplate;
        this.sourceTopic = sourceTopic;
        this.messageCount = messageCount;
        this.settleMillis = settleMillis;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> messages = DemoMessages.generate(messageCount);
        for (String m : messages) {
            kafkaTemplate.send(sourceTopic, m);
        }
        kafkaTemplate.flush();
        log.info("DEMO: produced {} messages to '{}'", messages.size(), sourceTopic);

        Thread.sleep(settleMillis);

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
        List<AuditRecord> samples = mongoTemplate.find(new Query().limit(5), AuditRecord.class);
        for (AuditRecord r : samples) {
            log.info("sample {} {} matched={} event={}",
                    r.auditType(), r.auditId().substring(0, 8), r.matchedRuleIds(), r.sourceEvent());
        }
        log.info("=====================================================");
    }

    private long countByType(String type) {
        return mongoTemplate.count(new Query(Criteria.where("auditType").is(type)), AuditRecord.class);
    }
}

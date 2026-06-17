package com.codrite.ruleaudit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class RuleAuditApplication {

    public static void main(String[] args) {
        log.info("Starting Rule Audit Application...");
        SpringApplication.run(RuleAuditApplication.class, args);
        log.info("Rule Audit Application is up and running.");
    }
}

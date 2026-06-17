package com.codrite.ruleaudit.config;

import com.mongodb.WriteConcern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

/**
 * MongoDB client configuration.
 * <p>
 * Ensures that all writes are performed with a {@code MAJORITY} write concern
 * to guarantee data durability in clustered environments.
 */
@Configuration
public class MongoConfig {

    /**
     * Configures the {@link MongoTemplate} with custom write concern.
     * 
     * @param factory   Spring-managed Mongo database factory.
     * @param converter Spring-managed Mongo converter.
     * @return A configured MongoTemplate.
     */
    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory, MongoConverter converter) {
        MongoTemplate template = new MongoTemplate(factory, converter);
        template.setWriteConcern(WriteConcern.MAJORITY);
        return template;
    }

    /**
     * Configures the {@link ReactiveMongoTemplate} with custom write concern.
     * 
     * @param factory   Spring-managed Reactive Mongo database factory.
     * @param converter Spring-managed Mongo converter.
     * @return A configured ReactiveMongoTemplate.
     */
    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(ReactiveMongoDatabaseFactory factory, MappingMongoConverter converter) {
        ReactiveMongoTemplate template = new ReactiveMongoTemplate(factory, converter);
        template.setWriteConcern(WriteConcern.MAJORITY);
        return template;
    }
}

package com.codrite.ruleaudit.web;

import com.codrite.ruleaudit.demo.DemoMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Controller for triggering synthetic traffic simulations.
 */
@Slf4j
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.topics.source}")
    private String sourceTopic;

    /**
     * Pushes a specified number of random messages to the source topic.
     * 
     * @param count Number of messages to generate.
     * @return Confirmation of the number of messages pushed.
     */
    @PostMapping("/push")
    public Mono<SimulationResult> pushMessages(@RequestParam(defaultValue = "10") int count) {
        log.info("Simulating push of {} messages to {}", count, sourceTopic);
        
        return Mono.fromCallable(() -> {
            List<String> messages = DemoMessages.generate(count);
            for (String m : messages) {
                kafkaTemplate.send(sourceTopic, m);
            }
            kafkaTemplate.flush();
            return new SimulationResult(count, "Successfully pushed " + count + " messages to " + sourceTopic);
        });
    }

    public record SimulationResult(int count, String message) {}
}

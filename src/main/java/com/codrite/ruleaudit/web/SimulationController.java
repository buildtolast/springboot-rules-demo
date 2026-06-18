package com.codrite.ruleaudit.web;

import com.codrite.ruleaudit.demo.DemoMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    /** Delay between consecutive published messages so they are paced, not bursted. */
    @Value("${app.simulation.publish-delay-ms:5}")
    private long publishDelayMs;

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
            for (int i = 0; i < messages.size(); i++) {
                kafkaTemplate.send(sourceTopic, messages.get(i));
                // Pace the stream: wait between messages instead of bursting them all at once.
                if (publishDelayMs > 0 && i < messages.size() - 1) {
                    Thread.sleep(publishDelayMs);
                }
            }
            kafkaTemplate.flush();
            return new SimulationResult(count, "Successfully pushed " + count + " messages to " + sourceTopic);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public record SimulationResult(int count, String message) {}
}

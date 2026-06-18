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
     * Kicks off publishing {@code count} messages to the source topic and returns
     * immediately. The publishing (paced, see {@link #publishDelayMs}) runs in the
     * background on a worker thread, so the UI never blocks on it — important when
     * a large batch with inter-message delay would otherwise take minutes.
     *
     * @param count Number of messages to generate.
     * @return Acknowledgement that background publishing has started.
     */
    @PostMapping("/push")
    public Mono<SimulationResult> pushMessages(@RequestParam(defaultValue = "10") int count) {
        log.info("Accepted request to publish {} messages to {} (background)", count, sourceTopic);

        // Fire-and-forget: schedule the publish loop on a worker thread and return now.
        Mono.fromRunnable(() -> publishBatch(count))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> log.info("Finished publishing {} messages to {}", count, sourceTopic),
                        error -> log.error("Background publish of {} messages failed", count, error));

        return Mono.just(new SimulationResult(count,
                "Publishing " + count + " messages to " + sourceTopic + " in the background"));
    }

    /** Publishes the batch with a delay between messages so they are paced, not bursted. */
    private void publishBatch(int count) {
        List<String> messages = DemoMessages.generate(count);
        for (int i = 0; i < messages.size(); i++) {
            kafkaTemplate.send(sourceTopic, messages.get(i));
            if (publishDelayMs > 0 && i < messages.size() - 1) {
                try {
                    Thread.sleep(publishDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Background publish interrupted after {} of {} messages", i + 1, count);
                    return;
                }
            }
        }
        kafkaTemplate.flush();
    }

    public record SimulationResult(int count, String message) {}
}

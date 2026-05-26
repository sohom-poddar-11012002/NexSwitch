package com.nexswitch.simulator.rest;

import com.nexswitch.adapters.outbound.kafka.MerchantConfigPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// LEARN: SimulatorPattern — merchant-simulator exercises the full integration path (Kafka publish
//        → cache invalidation) so QA scenarios can verify near-real-time config propagation.
@RestController
@RequestMapping("/simulate")
public class MerchantSimulatorController {

    private static final Logger log = LoggerFactory.getLogger(MerchantSimulatorController.class);

    private final MerchantConfigPublisher configPublisher;

    public MerchantSimulatorController(MerchantConfigPublisher configPublisher) {
        this.configPublisher = configPublisher;
    }

    @PostMapping("/merchant-config-change/{merchantId}")
    public ResponseEntity<String> simulateMerchantConfigChange(@PathVariable String merchantId) {
        log.info("simulator.merchant_config_change merchantId={}", merchantId);
        configPublisher.publish(merchantId);
        return ResponseEntity.ok("merchant.config.updated published for " + merchantId);
    }
}

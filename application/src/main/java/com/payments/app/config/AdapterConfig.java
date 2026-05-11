package com.payments.app.config;

import org.springframework.context.annotation.Configuration;

/**
 * Wires domain ports to their adapter implementations via @ConditionalOnProperty.
 *
 * Key properties:
 *   hsm.provider: mock | softhsm       (default: mock for local dev)
 *   upstream.provider: wiremock | razorpay | npci  (default: wiremock)
 *   storage.provider: local | s3       (default: local)
 *
 * Implementation added in Week 1 Step 2 as adapters are created.
 */
@Configuration
public class AdapterConfig {
    // Populated as adapters are implemented
}

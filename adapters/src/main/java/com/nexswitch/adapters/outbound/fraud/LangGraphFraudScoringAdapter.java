package com.nexswitch.adapters.outbound.fraud;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexswitch.domain.model.FraudScoringContext;
import com.nexswitch.domain.port.outbound.FraudScoringPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;

// LEARN: Strategy pattern via @ConditionalOnProperty — Spring activates this bean only when
//        fraud.scoring.enabled=true. MockFraudScoringAdapter activates when it is false (or absent).
//        The domain port interface never changes; only the active implementation swaps.
//        In production you'd use @ConditionalOnProperty to swap real HSM, real network, etc.
@Component
@ConditionalOnProperty(name = "fraud.scoring.enabled", havingValue = "true")
public class LangGraphFraudScoringAdapter implements FraudScoringPort {

    private static final Logger log = LoggerFactory.getLogger(LangGraphFraudScoringAdapter.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final RestClient restClient;

    // LEARN: RestClient timeout — 300ms connect + 450ms read keeps us well under the 500ms
    //        budget the domain passes in. If the fraud-scoring service is overloaded the call
    //        times out and we return Optional.empty() (fail-open), preserving auth success rate.
    public LangGraphFraudScoringAdapter(
            @Value("${fraud.scoring.url:http://fraud-scoring:8090}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(300));
        factory.setReadTimeout(Duration.ofMillis(450));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        log.info("fraud.scoring.adapter.init url={}", baseUrl);
    }

    @Override
    public Optional<BigDecimal> score(FraudScoringContext context, Duration budget) {
        try {
            var req = new ScoringRequest(
                    context.panHash().value(),
                    context.amount().amount().doubleValue(),
                    context.merchantCategory(),
                    context.network().name(),
                    context.method().name(),
                    context.transactionTime().atZone(IST).getHour()
            );
            ScoringResponse resp = restClient.post()
                    .uri("/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(ScoringResponse.class);
            if (resp != null) {
                BigDecimal score = BigDecimal.valueOf(resp.score()).setScale(4, RoundingMode.HALF_UP);
                log.debug("fraud.scored pan_prefix={} score={} reason={}",
                        context.panHash().value().substring(0, 8), score, resp.reasoning());
                return Optional.of(score);
            }
        } catch (Exception e) {
            log.warn("fraud.scoring.unavailable pan_prefix={} err={}",
                    context.panHash().value().substring(0, 8), e.getMessage());
        }
        return Optional.empty();
    }

    record ScoringRequest(
            @JsonProperty("pan_hash")    String panHash,
            @JsonProperty("amount_inr")  double amountInr,
            String mcc,
            String network,
            String method,
            @JsonProperty("hour_of_day") int hourOfDay) {}

    record ScoringResponse(double score, String reasoning) {}
}

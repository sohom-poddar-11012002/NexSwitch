package com.payments.domain.service;

import com.payments.domain.exception.RoutingException;
import com.payments.domain.model.BinInfo;
import com.payments.domain.model.PaymentNetwork;
import com.payments.domain.model.RoutingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.payments.domain.model.PaymentNetwork.*;
import static org.assertj.core.api.Assertions.*;

class RoutingEngineTest {

    private RoutingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RoutingEngine();
    }

    // ── Route by rules ────────────────────────────────────────────────────────

    @Test
    void singleMatchingRule_returnsItsNetwork() {
        var rules = List.of(new RoutingRule("453914", VISA, 1));
        assertThat(engine.route("4539148803436467", rules)).isEqualTo(VISA);
    }

    @Test
    void mcBinPrefix_routesToMastercard() {
        var rules = List.of(new RoutingRule("550000", MASTERCARD, 1));
        assertThat(engine.route("5500005555555559", rules)).isEqualTo(MASTERCARD);
    }

    @Test
    void rupayBinPrefix_routesToRupay() {
        var rules = List.of(new RoutingRule("606011", RUPAY, 1));
        assertThat(engine.route("6060111111111118", rules)).isEqualTo(RUPAY);
    }

    @Test
    void upiRule_routesToUpi() {
        var rules = List.of(new RoutingRule("999999", UPI, 1));
        assertThat(engine.route("9999991234567890", rules)).isEqualTo(UPI);
    }

    // ── Priority: lower number wins ───────────────────────────────────────────

    @Test
    void multipleMatchingRules_lowestPriorityNumberWins() {
        var rules = List.of(
            new RoutingRule("453914", VISA, 5),
            new RoutingRule("4539",   MASTERCARD, 1)   // same PAN, lower priority number
        );
        assertThat(engine.route("4539148803436467", rules)).isEqualTo(MASTERCARD);
    }

    @Test
    void multipleRules_onlyOneMatches() {
        var rules = List.of(
            new RoutingRule("550000", MASTERCARD, 1),
            new RoutingRule("411111", VISA, 1)
        );
        assertThat(engine.route("5500005555555559", rules)).isEqualTo(MASTERCARD);
    }

    // ── No matching rule ──────────────────────────────────────────────────────

    @Test
    void noMatchingRule_throwsRoutingException() {
        var rules = List.of(new RoutingRule("999999", VISA, 1));
        assertThatThrownBy(() -> engine.route("4111111111111111", rules))
            .isInstanceOf(RoutingException.class)
            .hasMessageContaining("411111");
    }

    @Test
    void emptyRuleList_throwsRoutingException() {
        assertThatThrownBy(() -> engine.route("4111111111111111", List.of()))
            .isInstanceOf(RoutingException.class);
    }

    // ── Route directly from BinInfo ───────────────────────────────────────────

    @Test
    void routeFromBinInfo_returnsNetworkDirectly() {
        var binInfo = new BinInfo("453914", VISA, "HDFC Bank", "DEBIT", "CLASSIC", "IN");
        assertThat(engine.route(binInfo)).isEqualTo(VISA);
    }

    @Test
    void routeFromBinInfo_mastercard() {
        var binInfo = new BinInfo("550000", MASTERCARD, "SBI", "CREDIT", "GOLD", "IN");
        assertThat(engine.route(binInfo)).isEqualTo(MASTERCARD);
    }

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void nullPan_throwsNullPointerException() {
        assertThatThrownBy(() -> engine.route(null, List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullRules_throwsNullPointerException() {
        assertThatThrownBy(() -> engine.route("4111111111111111", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullBinInfo_throwsNullPointerException() {
        assertThatThrownBy(() -> engine.route((BinInfo) null))
            .isInstanceOf(NullPointerException.class);
    }
}

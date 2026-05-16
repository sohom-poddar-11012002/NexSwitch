package com.nexswitch.domain.service;

import com.nexswitch.domain.exception.RoutingException;
import com.nexswitch.domain.model.BinInfo;
import com.nexswitch.domain.model.NetworkRoute;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.RoutingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.nexswitch.domain.model.PaymentNetwork.*;
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
            new RoutingRule("4539",   MASTERCARD, 1)
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

    // ── Simple BinInfo route (no acquirer context) ────────────────────────────

    @Test
    void routeFromBinInfo_returnsNetworkDirectly() {
        var binInfo = new BinInfo("453914", VISA, "HDFC Bank", "DEBIT", "CLASSIC", "IN", "HDFC", false);
        assertThat(engine.route(binInfo)).isEqualTo(VISA);
    }

    @Test
    void routeFromBinInfo_mastercard() {
        var binInfo = new BinInfo("550000", MASTERCARD, "SBI", "CREDIT", "GOLD", "IN", "SBI", false);
        assertThat(engine.route(binInfo)).isEqualTo(MASTERCARD);
    }

    // ── Three-tier routing: Tier 1 — On-us ───────────────────────────────────

    @Test
    void onUsTier_whenIssuerMatchesAcquirer_returnsOnUsRoute() {
        var bin = new BinInfo("453914", VISA, "Canara Bank", "DEBIT", "CLASSIC", "IN", "CANARA", false);
        NetworkRoute route = engine.route(bin, "CANARA");
        assertThat(route).isInstanceOf(NetworkRoute.OnUs.class);
        assertThat(route.network()).isEqualTo(VISA);
        assertThat(((NetworkRoute.OnUs) route).bankCode()).isEqualTo("CANARA");
    }

    @Test
    void onUsTier_caseInsensitiveMatch() {
        var bin = new BinInfo("453914", VISA, "SBI", "DEBIT", "CLASSIC", "IN", "sbi", false);
        assertThat(engine.route(bin, "SBI")).isInstanceOf(NetworkRoute.OnUs.class);
    }

    @Test
    void onUsTier_sbiOnSbi_returnsOnUs() {
        var bin = new BinInfo("601200", RUPAY, "SBI", "DEBIT", "CLASSIC", "IN", "SBI", true);
        assertThat(engine.route(bin, "SBI")).isInstanceOf(NetworkRoute.OnUs.class);
    }

    // ── Three-tier routing: Tier 2 — IBL / NPCI NFS ──────────────────────────

    @Test
    void iblTier_domesticNfsEligible_returnsIblRoute() {
        var bin = new BinInfo("607080", RUPAY, "PNB", "DEBIT", "CLASSIC", "IN", "PNB", true);
        NetworkRoute route = engine.route(bin, "CANARA");
        assertThat(route).isInstanceOf(NetworkRoute.Ibl.class);
        assertThat(route.network()).isEqualTo(RUPAY);
    }

    @Test
    void iblTier_notTakenIfNotNfsEligible() {
        var bin = new BinInfo("453914", VISA, "HDFC Bank", "CREDIT", "SIGNATURE", "IN", "HDFC", false);
        NetworkRoute route = engine.route(bin, "CANARA");
        assertThat(route).isInstanceOf(NetworkRoute.Gateway.class);
    }

    @Test
    void iblTier_notTakenIfForeignCard() {
        var bin = new BinInfo("400000", VISA, "Chase", "CREDIT", "INFINITE", "US", null, false);
        NetworkRoute route = engine.route(bin, "CANARA");
        assertThat(route).isInstanceOf(NetworkRoute.Gateway.class);
    }

    // ── Three-tier routing: Tier 3 — Gateway ─────────────────────────────────

    @Test
    void gatewayTier_visaForeignCard_returnsGateway() {
        var bin = new BinInfo("400000", VISA, "Chase", "CREDIT", "INFINITE", "US", null, false);
        NetworkRoute route = engine.route(bin, "CANARA");
        assertThat(route).isInstanceOf(NetworkRoute.Gateway.class);
        assertThat(route.network()).isEqualTo(VISA);
    }

    @Test
    void gatewayTier_mastercardDomestic_notNfsEligible() {
        var bin = new BinInfo("550000", MASTERCARD, "HDFC", "CREDIT", "WORLD", "IN", "HDFC", false);
        NetworkRoute route = engine.route(bin, "CANARA");
        assertThat(route).isInstanceOf(NetworkRoute.Gateway.class);
    }

    // ── Tier priority: on-us beats IBL even when nfsEligible ─────────────────

    @Test
    void onUsPriorityBeatsIbl_whenBothCouldApply() {
        // RuPay debit issued by SBI, acquirer is also SBI → on-us wins over IBL
        var bin = new BinInfo("607080", RUPAY, "SBI", "DEBIT", "CLASSIC", "IN", "SBI", true);
        NetworkRoute route = engine.route(bin, "SBI");
        assertThat(route).isInstanceOf(NetworkRoute.OnUs.class);
    }

    // ── Null guards — three-tier method ──────────────────────────────────────

    @Test
    void nullBinInfoWithAcquirerBank_throwsNullPointerException() {
        assertThatThrownBy(() -> engine.route(null, "CANARA"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullAcquirerBank_fallsThrough_skipsOnUs() {
        var bin = new BinInfo("453914", VISA, "HDFC Bank", "DEBIT", "CLASSIC", "IN", "HDFC", false);
        // Null acquirerBank means on-us check is skipped — should return IBL or Gateway
        NetworkRoute route = engine.route(bin, null);
        assertThat(route).isInstanceOf(NetworkRoute.Gateway.class);
    }

    @Test
    void nullIssuerBank_skipsOnUs() {
        var bin = new BinInfo("453914", VISA, "Unknown", "CREDIT", "STANDARD", "IN", null, false);
        NetworkRoute route = engine.route(bin, "CANARA");
        assertThat(route).isInstanceOf(NetworkRoute.Gateway.class);
    }

    // ── Null guards — original methods ───────────────────────────────────────

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

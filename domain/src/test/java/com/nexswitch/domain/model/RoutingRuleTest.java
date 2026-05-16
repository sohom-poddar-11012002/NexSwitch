package com.nexswitch.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RoutingRuleTest {

    @Test
    void createsRoutingRule() {
        RoutingRule rule = new RoutingRule("453914", PaymentNetwork.VISA, 1);
        assertThat(rule.binPrefix()).isEqualTo("453914");
        assertThat(rule.network()).isEqualTo(PaymentNetwork.VISA);
        assertThat(rule.priority()).isEqualTo(1);
    }

    @Test
    void matchesWhenPanStartsWithPrefix() {
        RoutingRule rule = new RoutingRule("453914", PaymentNetwork.VISA, 1);
        assertThat(rule.matches("4539148803436467")).isTrue();
        assertThat(rule.matches("5500005555555559")).isFalse();
    }

    @Test
    void throwsWhenBinPrefixIsNull() {
        assertThatThrownBy(() -> new RoutingRule(null, PaymentNetwork.VISA, 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNetworkIsNull() {
        assertThatThrownBy(() -> new RoutingRule("453914", null, 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenPriorityIsNegative() {
        assertThatThrownBy(() -> new RoutingRule("453914", PaymentNetwork.VISA, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

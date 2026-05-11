package com.payments.domain.model.vo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void createsMoneyFromStringAmount() {
        Money money = Money.of("6000.00", INR);
        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("6000.00"));
        assertThat(money.currency()).isEqualTo(INR);
    }

    @Test
    void createsMoneyFromBigDecimal() {
        Money money = Money.of(new BigDecimal("500.50"), INR);
        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("500.50"));
    }

    @Test
    void scaleIsAlwaysTwoDecimalPlaces() {
        Money money = Money.of("6000", INR);
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    @Test
    void addsMoneyWithSameCurrency() {
        Money a = Money.of("1000.00", INR);
        Money b = Money.of("500.50", INR);
        assertThat(a.add(b).amount()).isEqualByComparingTo(new BigDecimal("1500.50"));
    }

    @Test
    void subtractsMoneyWithSameCurrency() {
        Money a = Money.of("1000.00", INR);
        Money b = Money.of("300.00", INR);
        assertThat(a.subtract(b).amount()).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    void throwsWhenAddingDifferentCurrencies() {
        Money inr = Money.of("1000.00", INR);
        Money usd = Money.of("100.00", USD);
        assertThatThrownBy(() -> inr.add(usd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency");
    }

    @Test
    void throwsWhenSubtractingDifferentCurrencies() {
        Money inr = Money.of("1000.00", INR);
        Money usd = Money.of("100.00", USD);
        assertThatThrownBy(() -> inr.subtract(usd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency");
    }

    @Test
    void throwsWhenAmountIsNull() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null, INR))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenCurrencyIsNull() {
        assertThatThrownBy(() -> Money.of("1000.00", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenAmountIsNegative() {
        assertThatThrownBy(() -> Money.of("-1.00", INR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negative");
    }

    @Test
    void zeroAmountIsAllowed() {
        Money money = Money.of("0.00", INR);
        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void isGreaterThanComparison() {
        Money big = Money.of("1000.00", INR);
        Money small = Money.of("500.00", INR);
        assertThat(big.isGreaterThan(small)).isTrue();
        assertThat(small.isGreaterThan(big)).isFalse();
    }

    @Test
    void equalityBasedOnAmountAndCurrency() {
        Money a = Money.of("1000.00", INR);
        Money b = Money.of("1000.00", INR);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void inequalityAcrossCurrencies() {
        Money inr = Money.of("1000.00", INR);
        Money usd = Money.of("1000.00", USD);
        assertThat(inr).isNotEqualTo(usd);
    }

    @Test
    void toStringIncludesAmountAndCurrencyCode() {
        Money money = Money.of("6000.00", INR);
        assertThat(money.toString()).contains("6000.00").contains("INR");
    }
}

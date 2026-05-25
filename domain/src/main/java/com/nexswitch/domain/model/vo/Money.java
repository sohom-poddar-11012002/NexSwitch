package com.nexswitch.domain.model.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

// LEARN: ValueObject — BigDecimal+Currency pair; scale 2 HALF_UP enforced at construction; add() checks currency
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        if (currency == null) throw new IllegalArgumentException("currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, Currency currency) {
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        return new Money(new BigDecimal(amount), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean matches(BigDecimal other) {
        return amount.compareTo(other) == 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency))
            throw new IllegalArgumentException(
                "Cannot operate on different currency: " + currency + " vs " + other.currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}

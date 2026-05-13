package com.payments.domain.service;

import com.payments.domain.model.FeeBreakdown;
import com.payments.domain.model.PaymentNetwork;
import com.payments.domain.model.vo.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

import static com.payments.domain.model.PaymentNetwork.*;

public class FeeWaterfallCalculator {

    private static final Map<PaymentNetwork, Map<String, BigDecimal>> INTERCHANGE = Map.of(
        VISA,       Map.of("CREDIT", new BigDecimal("0.0180"), "DEBIT", new BigDecimal("0.0090")),
        MASTERCARD, Map.of("CREDIT", new BigDecimal("0.0175"), "DEBIT", new BigDecimal("0.0085")),
        RUPAY,      Map.of("CREDIT", new BigDecimal("0.0100"), "DEBIT", new BigDecimal("0.0060")),
        UPI,        Map.of("CREDIT", BigDecimal.ZERO,           "DEBIT", BigDecimal.ZERO)
    );

    private static final Map<PaymentNetwork, BigDecimal> ASSESSMENT = Map.of(
        VISA,       new BigDecimal("0.0014"),
        MASTERCARD, new BigDecimal("0.0013"),
        RUPAY,      new BigDecimal("0.0006"),
        UPI,        BigDecimal.ZERO
    );

    private static final BigDecimal MINIMUM_MDR = new BigDecimal("2.00");

    public FeeBreakdown calculate(Money grossAmount,
                                  PaymentNetwork network,
                                  String cardType,
                                  BigDecimal mdrPercentage,
                                  BigDecimal reservePercentage) {
        Objects.requireNonNull(grossAmount,       "grossAmount must not be null");
        Objects.requireNonNull(network,           "network must not be null");
        Objects.requireNonNull(cardType,          "cardType must not be null");
        Objects.requireNonNull(mdrPercentage,     "mdrPercentage must not be null");
        Objects.requireNonNull(reservePercentage, "reservePercentage must not be null");

        var currency = grossAmount.currency();
        var gross    = grossAmount.amount();

        var interchange  = pct(gross, INTERCHANGE.get(network).get(cardType.toUpperCase()));
        var assessment   = pct(gross, ASSESSMENT.get(network));
        var mdrRaw       = pct(gross, mdrPercentage);
        var mdr          = mdrRaw.max(MINIMUM_MDR);
        var net          = gross.subtract(interchange).subtract(assessment).subtract(mdr);
        var reserve      = pct(net, reservePercentage);
        var payout       = net.subtract(reserve);

        return new FeeBreakdown(
            grossAmount,
            Money.of(interchange, currency),
            Money.of(assessment,  currency),
            Money.of(mdr,         currency),
            Money.of(net,         currency),
            Money.of(reserve,     currency),
            Money.of(payout,      currency)
        );
    }

    private static BigDecimal pct(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}

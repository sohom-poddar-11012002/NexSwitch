package com.nexswitch.domain.service;

import com.nexswitch.domain.model.FeeBreakdown;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import static com.nexswitch.domain.model.PaymentNetwork.*;

// LEARN: FeeWaterfall — MDR → interchange → assessment → acquirer margin; BigDecimal throughout
public class FeeWaterfallCalculator {

    // LEARN: EnumMap — array-indexed by ordinal; faster and lower-memory than HashMap for enum keys.
    private static final Map<PaymentNetwork, Map<String, BigDecimal>> INTERCHANGE;
    private static final Map<PaymentNetwork, BigDecimal> ASSESSMENT;

    static {
        INTERCHANGE = new EnumMap<>(PaymentNetwork.class);
        INTERCHANGE.put(VISA,       Map.of("CREDIT", new BigDecimal("0.0180"), "DEBIT", new BigDecimal("0.0090")));
        INTERCHANGE.put(MASTERCARD, Map.of("CREDIT", new BigDecimal("0.0175"), "DEBIT", new BigDecimal("0.0085")));
        INTERCHANGE.put(RUPAY,      Map.of("CREDIT", new BigDecimal("0.0100"), "DEBIT", new BigDecimal("0.0060")));
        INTERCHANGE.put(UPI,        Map.of("CREDIT", BigDecimal.ZERO,           "DEBIT", BigDecimal.ZERO));

        ASSESSMENT = new EnumMap<>(PaymentNetwork.class);
        ASSESSMENT.put(VISA,       new BigDecimal("0.0014"));
        ASSESSMENT.put(MASTERCARD, new BigDecimal("0.0013"));
        ASSESSMENT.put(RUPAY,      new BigDecimal("0.0006"));
        ASSESSMENT.put(UPI,        BigDecimal.ZERO);
    }

    private static final BigDecimal DEFAULT_MINIMUM_MDR = new BigDecimal("2.00");

    private final BigDecimal minimumMdr;

    public FeeWaterfallCalculator() {
        this.minimumMdr = DEFAULT_MINIMUM_MDR;
    }

    public FeeWaterfallCalculator(BigDecimal minimumMdr) {
        Objects.requireNonNull(minimumMdr, "minimumMdr must not be null");
        this.minimumMdr = minimumMdr;
    }

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
        var mdr          = mdrRaw.max(minimumMdr);
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

    // DCC overload: same waterfall + dccFxMarginRate applied on top as additional Payswiff revenue.
    // Settlement always in INR — Payswiff absorbs zero FX risk. Margin is 2–4% above mid-market.
    public FeeBreakdown calculateWithDcc(Money grossAmount,
                                         PaymentNetwork network,
                                         String cardType,
                                         BigDecimal mdrPercentage,
                                         BigDecimal reservePercentage,
                                         BigDecimal dccFxMarginRate) {
        Objects.requireNonNull(dccFxMarginRate, "dccFxMarginRate must not be null");
        if (dccFxMarginRate.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("dccFxMarginRate must be non-negative");

        FeeBreakdown base = calculate(grossAmount, network, cardType, mdrPercentage, reservePercentage);
        var dccMargin = pct(grossAmount.amount(), dccFxMarginRate);
        return base.withDccMargin(Money.of(dccMargin, grossAmount.currency()));
    }

    private static BigDecimal pct(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}

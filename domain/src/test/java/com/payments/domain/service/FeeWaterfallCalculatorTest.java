package com.payments.domain.service;

import com.payments.domain.model.FeeBreakdown;
import com.payments.domain.model.PaymentNetwork;
import com.payments.domain.model.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;

import static com.payments.domain.model.PaymentNetwork.*;
import static org.assertj.core.api.Assertions.*;

class FeeWaterfallCalculatorTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final BigDecimal DEFAULT_MDR      = new BigDecimal("0.0150"); // 1.5%
    private static final BigDecimal DEFAULT_RESERVE  = new BigDecimal("0.0500"); // 5%

    private FeeWaterfallCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new FeeWaterfallCalculator();
    }

    // ── Gross amount flows through ─────────────────────────────────────────────

    @Test
    void grossAmountIsPreserved() {
        var breakdown = calculator.calculate(
            Money.of("6000.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE);
        assertThat(breakdown.grossAmount()).isEqualTo(Money.of("6000.00", INR));
    }

    // ── Visa debit full waterfall ─────────────────────────────────────────────
    //
    // Gross:             6000.00
    // Interchange 0.90%:   54.00
    // Assessment  0.14%:    8.40
    // MDR         1.50%:   90.00
    // Net to merchant:   5847.60
    // Reserve     5.00%:  292.38
    // Payout:            5555.22

    @Test
    void visaDebit_correctWaterfall() {
        var b = calculator.calculate(
            Money.of("6000.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE);

        assertThat(b.interchangeFee()).isEqualTo(Money.of("54.00", INR));
        assertThat(b.networkAssessmentFee()).isEqualTo(Money.of("8.40", INR));
        assertThat(b.mdrFee()).isEqualTo(Money.of("90.00", INR));
        assertThat(b.netToMerchant()).isEqualTo(Money.of("5847.60", INR));
        assertThat(b.reserveWithholding()).isEqualTo(Money.of("292.38", INR));
        assertThat(b.payoutAmount()).isEqualTo(Money.of("5555.22", INR));
    }

    // ── Visa credit ───────────────────────────────────────────────────────────
    //
    // Gross:             6000.00
    // Interchange 1.80%:  108.00
    // Assessment  0.14%:    8.40
    // MDR         1.50%:   90.00
    // Net to merchant:   5793.60
    // Reserve     5.00%:  289.68
    // Payout:            5503.92

    @Test
    void visaCredit_correctInterchange() {
        var b = calculator.calculate(
            Money.of("6000.00", INR), VISA, "CREDIT", DEFAULT_MDR, DEFAULT_RESERVE);

        assertThat(b.interchangeFee()).isEqualTo(Money.of("108.00", INR));
        assertThat(b.netToMerchant()).isEqualTo(Money.of("5793.60", INR));
        assertThat(b.payoutAmount()).isEqualTo(Money.of("5503.92", INR));
    }

    // ── Mastercard ────────────────────────────────────────────────────────────

    @Test
    void mastercardCredit_correctInterchangeAndAssessment() {
        var b = calculator.calculate(
            Money.of("10000.00", INR), MASTERCARD, "CREDIT", DEFAULT_MDR, DEFAULT_RESERVE);

        assertThat(b.interchangeFee()).isEqualTo(Money.of("175.00", INR));   // 1.75%
        assertThat(b.networkAssessmentFee()).isEqualTo(Money.of("13.00", INR)); // 0.13%
    }

    @Test
    void mastercardDebit_correctInterchange() {
        var b = calculator.calculate(
            Money.of("10000.00", INR), MASTERCARD, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE);

        assertThat(b.interchangeFee()).isEqualTo(Money.of("85.00", INR));    // 0.85%
    }

    // ── RuPay ─────────────────────────────────────────────────────────────────

    @Test
    void rupayCredit_correctRates() {
        var b = calculator.calculate(
            Money.of("10000.00", INR), RUPAY, "CREDIT", DEFAULT_MDR, DEFAULT_RESERVE);

        assertThat(b.interchangeFee()).isEqualTo(Money.of("100.00", INR));   // 1.00%
        assertThat(b.networkAssessmentFee()).isEqualTo(Money.of("6.00", INR)); // 0.06%
    }

    @Test
    void rupayDebit_correctRates() {
        var b = calculator.calculate(
            Money.of("10000.00", INR), RUPAY, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE);

        assertThat(b.interchangeFee()).isEqualTo(Money.of("60.00", INR));    // 0.60%
    }

    // ── UPI — zero fees ───────────────────────────────────────────────────────

    @Test
    void upi_zeroInterchangeAndAssessment() {
        var b = calculator.calculate(
            Money.of("500.00", INR), UPI, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE);

        assertThat(b.interchangeFee()).isEqualTo(Money.of("0.00", INR));
        assertThat(b.networkAssessmentFee()).isEqualTo(Money.of("0.00", INR));
    }

    // ── Minimum MDR ───────────────────────────────────────────────────────────
    //
    // Amount ₹100, MDR 1.5% = ₹1.50 → below ₹2.00 minimum → MDR charged as ₹2.00

    @Test
    void smallAmount_minimumMdrApplied() {
        var b = calculator.calculate(
            Money.of("100.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE);

        assertThat(b.mdrFee()).isEqualTo(Money.of("2.00", INR));
    }

    @Test
    void largeAmount_percentageMdrApplied() {
        // 6000 * 1.5% = 90 > 2.00 → percentage wins
        var b = calculator.calculate(
            Money.of("6000.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE);

        assertThat(b.mdrFee()).isEqualTo(Money.of("90.00", INR));
    }

    // ── Custom MDR per merchant ───────────────────────────────────────────────

    @Test
    void customMdrPercentage_usedInsteadOfDefault() {
        var customMdr = new BigDecimal("0.0200"); // 2%
        var b = calculator.calculate(
            Money.of("10000.00", INR), VISA, "DEBIT", customMdr, DEFAULT_RESERVE);

        assertThat(b.mdrFee()).isEqualTo(Money.of("200.00", INR));
    }

    // ── Custom reserve percentage ─────────────────────────────────────────────

    @Test
    void customReservePercentage_appliedCorrectly() {
        var reserve = new BigDecimal("0.1000"); // 10%
        var b = calculator.calculate(
            Money.of("10000.00", INR), UPI, "DEBIT", DEFAULT_MDR, reserve);

        // UPI: no interchange, no assessment, MDR 1.5% = 150 → net = 9850
        // Reserve 10% of net = 985 → payout = 8865
        assertThat(b.netToMerchant()).isEqualTo(Money.of("9850.00", INR));
        assertThat(b.reserveWithholding()).isEqualTo(Money.of("985.00", INR));
        assertThat(b.payoutAmount()).isEqualTo(Money.of("8865.00", INR));
    }

    // ── Payout = net - reserve ────────────────────────────────────────────────

    @Test
    void payoutIsNetMinusReserve() {
        var b = calculator.calculate(
            Money.of("6000.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE);

        BigDecimal expectedPayout = b.netToMerchant().amount()
            .subtract(b.reserveWithholding().amount());
        assertThat(b.payoutAmount().amount()).isEqualByComparingTo(expectedPayout);
    }

    // ── Parameterized network + type combinations ─────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "VISA,       CREDIT, 0.0180",
        "VISA,       DEBIT,  0.0090",
        "MASTERCARD, CREDIT, 0.0175",
        "MASTERCARD, DEBIT,  0.0085",
        "RUPAY,      CREDIT, 0.0100",
        "RUPAY,      DEBIT,  0.0060",
        "UPI,        DEBIT,  0.0000"
    })
    void interchangeRate_matchesSpec(String networkName, String cardType, String expectedRate) {
        var network  = PaymentNetwork.valueOf(networkName);
        var gross    = new BigDecimal("10000.00");
        var expected = gross.multiply(new BigDecimal(expectedRate))
            .setScale(2, java.math.RoundingMode.HALF_UP);

        var b = calculator.calculate(
            Money.of("10000.00", INR), network, cardType, DEFAULT_MDR, DEFAULT_RESERVE);

        assertThat(b.interchangeFee().amount()).isEqualByComparingTo(expected);
    }

    // ── DCC overload — FX margin added on top of normal waterfall ────────────

    @Test
    void dccCalculation_addsFxMarginAboveNormalFees() {
        // Visa DEBIT ₹6,000, MDR 1.5%, reserve 5%, DCC margin 2%
        var dccMarginRate = new BigDecimal("0.0200");
        var b = calculator.calculateWithDcc(
            Money.of("6000.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE, dccMarginRate);

        assertThat(b.hasDcc()).isTrue();
        assertThat(b.dccFxMargin()).isPresent();
        // DCC margin = 2% of ₹6,000 = ₹120
        assertThat(b.dccFxMargin().get().amount()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    void dccCalculation_baseFeesUnchangedVsNormalPath() {
        var dccMarginRate = new BigDecimal("0.0200");
        var normal = calculator.calculate(
            Money.of("6000.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE);
        var dcc = calculator.calculateWithDcc(
            Money.of("6000.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE, dccMarginRate);

        assertThat(dcc.interchangeFee()).isEqualTo(normal.interchangeFee());
        assertThat(dcc.mdrFee()).isEqualTo(normal.mdrFee());
        assertThat(dcc.payoutAmount()).isEqualTo(normal.payoutAmount());
    }

    @Test
    void noDcc_hasDccFalse() {
        var b = calculator.calculate(
            Money.of("6000.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE);
        assertThat(b.hasDcc()).isFalse();
        assertThat(b.dccFxMargin()).isEmpty();
    }

    @Test
    void dccMarginRate_negativeFails() {
        assertThatThrownBy(() ->
            calculator.calculateWithDcc(
                Money.of("6000.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE,
                new BigDecimal("-0.01")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dccMarginRate_null_throws() {
        assertThatThrownBy(() ->
            calculator.calculateWithDcc(
                Money.of("6000.00", INR), VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE, null))
            .isInstanceOf(NullPointerException.class);
    }

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void nullAmount_throws() {
        assertThatThrownBy(() ->
            calculator.calculate(null, VISA, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullNetwork_throws() {
        assertThatThrownBy(() ->
            calculator.calculate(Money.of("100.00", INR), null, "DEBIT", DEFAULT_MDR, DEFAULT_RESERVE))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullCardType_throws() {
        assertThatThrownBy(() ->
            calculator.calculate(Money.of("100.00", INR), VISA, null, DEFAULT_MDR, DEFAULT_RESERVE))
            .isInstanceOf(NullPointerException.class);
    }
}

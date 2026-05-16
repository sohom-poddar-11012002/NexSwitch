package com.nexswitch.terminal.scenario;

// LEARN: ScenarioEnum — each scenario encodes its own pre-conditions; enables scripted end-to-end testing without a real POS
public enum Scenario {

    /** Normal chip+PIN purchase — ₹6,000; expect RC=00, auth code present. */
    NORMAL_PURCHASE("000000600000"),

    /** Amount above merchant per-transaction limit (₹99,999); expect RC=05 decline. */
    OVERLIMIT("000009999900"),

    /**
     * Send 0100, then wait with a 1-second read timeout.
     * If the switch doesn't respond in 1s, the terminal must initiate a 0400 reversal.
     * LEARN: TimeoutReversal — terminal fires 0400 when 0110 doesn't arrive; prevents double-debit on retry
     */
    TIMEOUT("000000600000"),

    /**
     * Send the same 0100 twice with the same STAN.
     * Switch must detect the duplicate and return the original response (idempotency).
     */
    DUPLICATE("000000600000");

    final String amountPaise;

    Scenario(String amountPaise) {
        this.amountPaise = amountPaise;
    }

    public String amountPaise() {
        return amountPaise;
    }
}

package com.payments.terminal.message;

import java.util.concurrent.atomic.AtomicInteger;

// LEARN: AtomicIncrement — lock-free STAN counter; updateAndGet ensures wrap-around is race-free without synchronized
public final class StanGenerator {

    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Returns the next STAN, zero-padded to 6 digits. Wraps from 999999 back to 000001.
     * ISO 8583 §4.3: STAN must be unique per terminal per calendar day; sequential is simplest strategy.
     */
    public String next() {
        int n = counter.updateAndGet(v -> v >= 999_999 ? 1 : v + 1);
        return String.format("%06d", n);
    }

    public String current() {
        return String.format("%06d", counter.get());
    }
}

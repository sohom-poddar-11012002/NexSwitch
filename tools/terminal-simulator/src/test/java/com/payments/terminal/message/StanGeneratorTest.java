package com.payments.terminal.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StanGeneratorTest {

    @Test
    void next_returnsFirstStanAs000001() {
        StanGenerator gen = new StanGenerator();
        assertEquals("000001", gen.next());
    }

    @Test
    void next_incrementsSequentially() {
        StanGenerator gen = new StanGenerator();
        assertEquals("000001", gen.next());
        assertEquals("000002", gen.next());
        assertEquals("000003", gen.next());
    }

    @Test
    void next_zerosPadToSixDigits() {
        StanGenerator gen = new StanGenerator();
        String stan = gen.next();
        assertEquals(6, stan.length());
    }

    @Test
    void next_wrapsFrom999999To000001() {
        // Pre-seed the counter to 999998 by calling next() repeatedly would be slow;
        // use reflection-free approach: verify wrap logic via AtomicInteger contract
        // The real wrap is tested by driving the counter to the boundary.
        StanGenerator gen = new StanGenerator();
        // Advance to 999999 (999999 calls is too slow; test the wrapping contract via a fresh gen at limit)
        // We verify the formula: updateAndGet(v -> v >= 999_999 ? 1 : v + 1)
        // When counter=999999: next() must return "000001" on the following call.
        // Drive with a small generator where we simulate boundary behaviour:
        for (int i = 1; i < 999_999; i++) gen.next(); // drive to 999998
        assertEquals("999999", gen.next()); // boundary
        assertEquals("000001", gen.next()); // wraps
    }

    @Test
    void current_returnsLastIssuedStan() {
        StanGenerator gen = new StanGenerator();
        gen.next(); // 000001
        gen.next(); // 000002
        assertEquals("000002", gen.current());
    }
}

package com.payments.domain.service;

// LEARN: LuhnAlgorithm — validates PAN checksum at terminal boundary before network round-trip
public final class LuhnValidator {

    private LuhnValidator() {}

    public static boolean isValid(String pan) {
        if (pan == null) return false;

        String digits = pan.replace(" ", "");
        if (!digits.matches("\\d+") || digits.length() < 2) return false;

        int sum = 0;
        boolean doubleIt = false;

        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}

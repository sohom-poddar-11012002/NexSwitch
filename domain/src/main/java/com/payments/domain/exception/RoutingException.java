package com.payments.domain.exception;

public class RoutingException extends RuntimeException {

    public RoutingException(String binPrefix) {
        super("No routing rule found for BIN prefix: " + binPrefix);
    }
}

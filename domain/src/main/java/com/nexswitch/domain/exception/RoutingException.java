package com.nexswitch.domain.exception;

// LEARN: UncheckedException — configuration errors are non-recoverable; unchecked avoids try-catch clutter
public class RoutingException extends RuntimeException {

    public RoutingException(String binPrefix) {
        super("No routing rule found for BIN prefix: " + binPrefix);
    }
}

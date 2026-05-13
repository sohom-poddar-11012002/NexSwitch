package com.payments.domain.service;

import com.payments.domain.exception.RoutingException;
import com.payments.domain.model.BinInfo;
import com.payments.domain.model.PaymentNetwork;
import com.payments.domain.model.RoutingRule;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class RoutingEngine {

    public PaymentNetwork route(String pan, List<RoutingRule> rules) {
        Objects.requireNonNull(pan, "pan must not be null");
        Objects.requireNonNull(rules, "rules must not be null");

        return rules.stream()
            .filter(r -> r.matches(pan))
            .min(Comparator.comparingInt(RoutingRule::priority))
            .map(RoutingRule::network)
            .orElseThrow(() -> new RoutingException(pan.substring(0, Math.min(6, pan.length()))));
    }

    public PaymentNetwork route(BinInfo binInfo) {
        Objects.requireNonNull(binInfo, "binInfo must not be null");
        return binInfo.network();
    }
}

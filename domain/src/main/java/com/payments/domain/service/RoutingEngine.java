package com.payments.domain.service;

import com.payments.domain.exception.RoutingException;
import com.payments.domain.model.BinInfo;
import com.payments.domain.model.NetworkRoute;
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

    // Simple BIN-only lookup — no acquirer context, falls through to network gateway.
    public PaymentNetwork route(BinInfo binInfo) {
        Objects.requireNonNull(binInfo, "binInfo must not be null");
        return binInfo.network();
    }

    // Three-tier routing per §6.12:
    //   Tier 1 — on-us: issuerBank == acquirerBank → direct CBS (no network, no interchange)
    //   Tier 2 — IBL:   domestic IN card + nfsEligible → NPCI NFS (RBI mandate)
    //   Tier 3 — gateway: VisaNet / Banknet / NPCI UPI
    public NetworkRoute route(BinInfo binInfo, String acquirerBank) {
        Objects.requireNonNull(binInfo, "binInfo must not be null");

        String issuerBank = binInfo.issuerBank();
        if (acquirerBank != null && issuerBank != null && acquirerBank.equalsIgnoreCase(issuerBank)) {
            return new NetworkRoute.OnUs(binInfo.network(), acquirerBank);
        }

        if ("IN".equals(binInfo.countryCode()) && binInfo.nfsEligible()) {
            return new NetworkRoute.Ibl(binInfo.network());
        }

        return new NetworkRoute.Gateway(binInfo.network());
    }
}

package com.nexswitch.domain.service;

import com.nexswitch.domain.exception.RoutingException;
import com.nexswitch.domain.model.BinInfo;
import com.nexswitch.domain.model.NetworkRoute;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.RoutingRule;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// LEARN: StrategyPattern — three-tier: NFS → PRIMARY → SECONDARY; BIN drives tier selection
public class RoutingEngine {

    // LEARN: Pre-indexed BIN6 map turns O(n) stream-filter into O(1) HashMap lookup.
    //        Call indexByBin6(rules) once at startup; hot-path route(pan, index) never scans.
    public static Map<String, RoutingRule> indexByBin6(List<RoutingRule> rules) {
        Objects.requireNonNull(rules, "rules must not be null");
        Map<String, RoutingRule> index = new HashMap<>(rules.size() * 2);
        for (RoutingRule rule : rules) {
            index.merge(rule.binPrefix(), rule,
                    (existing, incoming) -> existing.priority() <= incoming.priority() ? existing : incoming);
        }
        return index;
    }

    public PaymentNetwork route(String pan, Map<String, RoutingRule> index) {
        Objects.requireNonNull(pan, "pan must not be null");
        Objects.requireNonNull(index, "index must not be null");
        String bin6 = pan.substring(0, Math.min(6, pan.length()));
        RoutingRule rule = index.get(bin6);
        if (rule == null) throw new RoutingException(bin6);
        return rule.network();
    }

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

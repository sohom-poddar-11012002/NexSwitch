package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.PaymentNetwork;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

// LEARN: CommandObject — Set<PaymentNetwork> allows per-network reconciliation runs
public record ReconciliationCommand(
        LocalDate reconciliationDate,
        Set<PaymentNetwork> networks
) {
    public ReconciliationCommand {
        Objects.requireNonNull(reconciliationDate, "reconciliationDate must not be null");
        Objects.requireNonNull(networks, "networks must not be null");
        if (networks.isEmpty()) {
            throw new IllegalArgumentException("networks must not be empty");
        }
        networks = Set.copyOf(networks);
    }
}

package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.Terminal;
import com.nexswitch.domain.model.vo.TerminalId;

import java.util.Optional;

// LEARN: RepositoryPort — terminal active-status check is a hard pre-condition before network auth.
//        Failing here (terminal inactive) saves a round-trip to Visa/MC and avoids a chargeable decline.
public interface TerminalRepository {
    Optional<Terminal> findById(TerminalId id);
}

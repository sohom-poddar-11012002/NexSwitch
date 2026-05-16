package com.nexswitch.adapters.outbound.persistence;

import com.nexswitch.adapters.outbound.persistence.entity.TerminalEntity;
import com.nexswitch.adapters.outbound.persistence.jpa.JpaTerminalRepository;
import com.nexswitch.domain.model.Terminal;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.TerminalId;
import com.nexswitch.domain.port.outbound.TerminalRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// LEARN: StringPK — TerminalEntity uses a String ID (ISO 8583 Field 41, 8-char terminal ID),
//        so JPA findById() accepts String directly; no numeric surrogate key involved.
@Repository
public class PostgresTerminalRepository implements TerminalRepository {

    private final JpaTerminalRepository jpa;

    public PostgresTerminalRepository(JpaTerminalRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Terminal> findById(TerminalId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    private Terminal toDomain(TerminalEntity e) {
        return new Terminal(
            TerminalId.of(e.getId()),
            MerchantId.of(e.getMerchantId()),
            Terminal.Status.valueOf(e.getStatus())
        );
    }
}

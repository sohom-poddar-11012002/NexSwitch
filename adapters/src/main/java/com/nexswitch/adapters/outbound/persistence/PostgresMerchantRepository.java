package com.nexswitch.adapters.outbound.persistence;

import com.nexswitch.adapters.outbound.persistence.jpa.JpaMerchantRepository;
import com.nexswitch.adapters.outbound.persistence.mapper.MerchantMapper;
import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// LEARN: AdapterPattern — domain calls findById(MerchantId); JPA never leaks into domain layer.
//        MerchantMapper converts entity ↔ domain record; MerchantId.value() unwraps to String PK.
@Repository
public class PostgresMerchantRepository implements MerchantRepository {

    private final JpaMerchantRepository jpa;
    private final MerchantMapper mapper;

    public PostgresMerchantRepository(JpaMerchantRepository jpa, MerchantMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<MerchantProfile> findById(MerchantId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }
}

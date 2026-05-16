package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.vo.MerchantId;

import java.util.Optional;

// LEARN: RepositoryPort — the domain defines the interface it needs; JPA @Entity and Spring Data
//        are invisible here. The adapter (PostgresMerchantRepository) implements this port and
//        uses a mapper to convert JPA entities → domain records. Domain stays pure.
public interface MerchantRepository {
    Optional<MerchantProfile> findById(MerchantId id);
}

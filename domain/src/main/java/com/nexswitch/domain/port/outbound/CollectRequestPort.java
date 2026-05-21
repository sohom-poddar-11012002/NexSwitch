package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.CollectRequest;

import java.util.Optional;

public interface CollectRequestPort {
    void save(CollectRequest request);
    Optional<CollectRequest> findByCollectId(String collectId);
    void update(CollectRequest request);
}

package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.CollectRequest;
import com.nexswitch.domain.model.vo.CollectId;

import java.util.Optional;

public interface CollectRequestPort {
    void save(CollectRequest request);
    Optional<CollectRequest> findByCollectId(CollectId collectId);
    void update(CollectRequest request);
}

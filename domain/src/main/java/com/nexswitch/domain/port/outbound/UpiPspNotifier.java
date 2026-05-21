package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.CollectRequest;

// LEARN: Anti-corruption layer — domain knows nothing about HTTP or NPCI APIs.
//        The adapter translates CollectRequest into the mock-upstream JSON format.
public interface UpiPspNotifier {
    void sendCollectRequest(CollectRequest request);
}

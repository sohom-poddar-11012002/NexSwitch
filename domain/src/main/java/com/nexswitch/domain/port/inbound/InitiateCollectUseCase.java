package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.InitiateCollectResult;

public interface InitiateCollectUseCase {
    InitiateCollectResult execute(InitiateCollectCommand command);
}

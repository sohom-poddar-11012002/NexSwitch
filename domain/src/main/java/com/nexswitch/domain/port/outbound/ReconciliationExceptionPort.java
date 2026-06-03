package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.ReconciliationException;

public interface ReconciliationExceptionPort {
    void save(ReconciliationException exception);
}

package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.RefundResult;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.vo.Money;

// LEARN: AdapterPort — refund request routed to original network; domain doesn't know which
public interface RefundPort {
    RefundResult requestRefund(Transaction transaction, Money refundAmount);
}

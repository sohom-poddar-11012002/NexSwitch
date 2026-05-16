package com.payments.domain.port.outbound;

import com.payments.domain.model.RefundResult;
import com.payments.domain.model.Transaction;
import com.payments.domain.model.vo.Money;

// LEARN: AdapterPort — refund request routed to original network; domain doesn't know which
public interface RefundPort {
    RefundResult requestRefund(Transaction transaction, Money refundAmount);
}

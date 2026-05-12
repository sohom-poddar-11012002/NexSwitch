package com.payments.domain.port.outbound;

import com.payments.domain.model.RefundResult;
import com.payments.domain.model.Transaction;
import com.payments.domain.model.vo.Money;

public interface RefundPort {
    RefundResult requestRefund(Transaction transaction, Money refundAmount);
}

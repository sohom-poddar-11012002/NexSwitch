package com.payments.domain.model;

import com.payments.domain.model.vo.Money;

public record FeeBreakdown(
    Money grossAmount,
    Money interchangeFee,
    Money networkAssessmentFee,
    Money mdrFee,
    Money netToMerchant,
    Money reserveWithholding,
    Money payoutAmount
) {}

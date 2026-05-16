package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.Money;

import java.util.Optional;

// LEARN: ValueObject — fee waterfall result; all components immutable in one record for audit trail
public record FeeBreakdown(
    Money grossAmount,
    Money interchangeFee,
    Money networkAssessmentFee,
    Money mdrFee,
    Money netToMerchant,
    Money reserveWithholding,
    Money payoutAmount,
    Optional<Money> dccFxMargin
) {
    public FeeBreakdown(Money grossAmount, Money interchangeFee, Money networkAssessmentFee,
                        Money mdrFee, Money netToMerchant, Money reserveWithholding,
                        Money payoutAmount) {
        this(grossAmount, interchangeFee, networkAssessmentFee, mdrFee,
             netToMerchant, reserveWithholding, payoutAmount, Optional.empty());
    }

    public FeeBreakdown withDccMargin(Money margin) {
        return new FeeBreakdown(grossAmount, interchangeFee, networkAssessmentFee,
                                mdrFee, netToMerchant, reserveWithholding,
                                payoutAmount, Optional.of(margin));
    }

    public boolean hasDcc() {
        return dccFxMargin.isPresent();
    }
}

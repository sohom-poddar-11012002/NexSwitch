package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.vo.PanHash;
import com.nexswitch.domain.model.vo.TerminalId;

// LEARN: Magstripe fallback counter — EMV mandates that issuers limit "chip failure" fallback
//        to magstripe to at most 2–3 attempts per card per terminal per day. Attackers use
//        intentionally bent chips to force fallback so card data can be skimmed unencrypted.
//        Counting fallbacks per PAN+terminal+date and enforcing a limit is a mandatory Visa/MC rule.
public interface FallbackCounterPort {

    /**
     * Returns the current fallback count for this PAN+terminal combination,
     * then atomically increments it. Counts reset after 24 hours.
     */
    int getAndIncrementFallbackCount(PanHash panHash, TerminalId terminalId);
}

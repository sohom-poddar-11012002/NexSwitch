package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.BinInfo;

import java.util.Optional;

// LEARN: OptionalPort — absent means "BIN not in our database", a valid domain concept.
//        Returning null would force every caller to null-check; Optional makes the
//        absence explicit in the type and forces callers to handle it at compile time.
public interface BinLookupPort {
    Optional<BinInfo> lookup(String bin6);
}

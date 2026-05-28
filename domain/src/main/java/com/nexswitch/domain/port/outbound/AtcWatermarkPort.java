package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.vo.PanHash;

// LEARN: ATC watermark — EMV Book 2 §8.1.1 requires issuers to verify ATC > last_seen_atc.
//        If a cloned card replays an old ATC, the watermark check catches it before network auth.
//        "isAtcFresh" returns false for ATCs ≤ watermark, causing an immediate "62" decline.
public interface AtcWatermarkPort {

    /**
     * Returns true if the given ATC is strictly greater than the stored watermark for this PAN,
     * or if no watermark exists yet (first time the card is seen).
     * Returns false if ATC ≤ stored max — indicates a replay or cloned card.
     */
    boolean isAtcFresh(PanHash panHash, int atc);

    /**
     * Records the ATC as the new high-water mark for this PAN.
     * Uses GREATEST(existing, atc) to handle concurrent updates safely.
     */
    void updateWatermark(PanHash panHash, int atc);
}

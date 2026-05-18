package com.nexswitch.qa.domain.model;

public enum ChannelType {
    ISO8583,
    REST,
    KAFKA_ASSERT,
    CHAOS,
    PLAYWRIGHT,
    MULTI        // scenario spans more than one channel
}

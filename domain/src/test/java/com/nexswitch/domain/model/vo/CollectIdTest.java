package com.nexswitch.domain.model.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CollectIdTest {

    @Test
    void constructsWithValidValue() {
        CollectId id = new CollectId("COL1234567890ABCDEF");
        assertThat(id.value()).isEqualTo("COL1234567890ABCDEF");
    }

    @Test
    void toStringReturnsValue() {
        assertThat(new CollectId("COLABC").toString()).isEqualTo("COLABC");
    }

    @Test
    void ofFactoryMethodWorks() {
        assertThat(CollectId.of("COLXYZ").value()).isEqualTo("COLXYZ");
    }

    @Test
    void throwsOnNull() {
        assertThatThrownBy(() -> new CollectId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnBlank() {
        assertThatThrownBy(() -> new CollectId("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityBasedOnValue() {
        assertThat(new CollectId("COLABC")).isEqualTo(new CollectId("COLABC"));
        assertThat(new CollectId("COLABC")).isNotEqualTo(new CollectId("COLXYZ"));
    }
}

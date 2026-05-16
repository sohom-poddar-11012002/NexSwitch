package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.PaymentNetwork;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ReconciliationCommandTest {

    @Test
    void valid_command_constructs_successfully() {
        assertThatNoException().isThrownBy(() ->
                new ReconciliationCommand(LocalDate.of(2026, 5, 12), Set.of(PaymentNetwork.VISA)));
    }

    @Test
    void null_date_throws() {
        assertThatThrownBy(() -> new ReconciliationCommand(null, Set.of(PaymentNetwork.VISA)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_networks_throws() {
        assertThatThrownBy(() -> new ReconciliationCommand(LocalDate.now(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void empty_networks_throws() {
        assertThatThrownBy(() -> new ReconciliationCommand(LocalDate.now(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("networks must not be empty");
    }

    @Test
    void networks_set_is_immutable() {
        var mutable = new java.util.HashSet<>(Set.of(PaymentNetwork.VISA));
        var command = new ReconciliationCommand(LocalDate.now(), mutable);
        mutable.add(PaymentNetwork.MASTERCARD);
        assertThat(command.networks()).containsExactly(PaymentNetwork.VISA);
    }
}

package com.nexswitch.reconciliation.rest;

import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.ReconciliationResult;
import com.nexswitch.domain.port.inbound.ReconcileUseCase;
import com.nexswitch.domain.port.inbound.ReconciliationCommand;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    private final ReconcileUseCase reconcileUseCase;

    public ReconciliationController(ReconcileUseCase reconcileUseCase) {
        this.reconcileUseCase = reconcileUseCase;
    }

    @PostMapping("/run")
    public ResponseEntity<ReconciliationResult> run(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "VISA,MASTERCARD,RUPAY,UPI") Set<PaymentNetwork> networks) {
        ReconciliationResult result = reconcileUseCase.execute(new ReconciliationCommand(date, networks));
        return ResponseEntity.ok(result);
    }
}

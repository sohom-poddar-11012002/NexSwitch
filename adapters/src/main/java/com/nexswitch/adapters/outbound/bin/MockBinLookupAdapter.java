package com.nexswitch.adapters.outbound.bin;

import com.nexswitch.domain.model.BinInfo;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.port.outbound.BinLookupPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

// LEARN: BINRange — BIN (Bank Identification Number) is the first 6–8 digits of the PAN.
//        In production, Visa/MC publish BIN tables (~50MB CSV); this mock covers the major
//        test ranges used in developer sandboxes across all four Indian schemes.
@Component
public class MockBinLookupAdapter implements BinLookupPort {

    // LEARN: IIN vs BIN — "IIN" (Issuer Identification Number) is the ISO 7812 term;
    //        industry still calls them BINs. The first digit encodes the scheme:
    //        4=Visa, 5=MC (51–55), 6=Discover/RuPay/Amex, 3=Amex/Diners.
    private static final Map<String, BinInfo> TABLE = Map.of(
        "411111", new BinInfo("411111", PaymentNetwork.VISA,   "HDFC Bank",   "CREDIT",  "CLASSIC",   "IN", "HDFC", false),
        "424242", new BinInfo("424242", PaymentNetwork.VISA,   "ICICI Bank",  "CREDIT",  "PLATINUM",  "IN", "ICICI", false),
        "555555", new BinInfo("555555", PaymentNetwork.MASTERCARD, "SBI Card",    "CREDIT",  "WORLD",     "IN", "SBI",  false),
        "510510", new BinInfo("510510", PaymentNetwork.MASTERCARD, "Kotak Bank",  "DEBIT",   "STANDARD",  "IN", "KOTAK", false),
        "508528", new BinInfo("508528", PaymentNetwork.RUPAY,  "Canara Bank", "DEBIT",   "CLASSIC",   "IN", "CANARA", true),
        "606985", new BinInfo("606985", PaymentNetwork.RUPAY,  "SBI",         "CREDIT",  "SELECT",    "IN", "SBI",   false)
    );

    @Override
    public Optional<BinInfo> lookup(String bin6) {
        return Optional.ofNullable(TABLE.get(bin6));
    }
}

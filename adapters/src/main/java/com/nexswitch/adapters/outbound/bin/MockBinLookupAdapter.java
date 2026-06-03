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
    private static final Map<String, BinInfo> TABLE = Map.ofEntries(
        // Generic test BINs used in unit tests and REST demos
        Map.entry("411111", new BinInfo("411111", PaymentNetwork.VISA,       "HDFC Bank",   "CREDIT", "CLASSIC",  "IN", "HDFC",   false)),
        Map.entry("424242", new BinInfo("424242", PaymentNetwork.VISA,       "ICICI Bank",  "CREDIT", "PLATINUM", "IN", "ICICI",  false)),
        Map.entry("555555", new BinInfo("555555", PaymentNetwork.MASTERCARD, "SBI Card",    "CREDIT", "WORLD",    "IN", "SBI",    false)),
        Map.entry("510510", new BinInfo("510510", PaymentNetwork.MASTERCARD, "Kotak Bank",  "DEBIT",  "STANDARD", "IN", "KOTAK",  false)),
        Map.entry("508528", new BinInfo("508528", PaymentNetwork.RUPAY,      "Canara Bank", "DEBIT",  "CLASSIC",  "IN", "CANARA", true)),
        Map.entry("606985", new BinInfo("606985", PaymentNetwork.RUPAY,      "SBI",         "CREDIT", "SELECT",   "IN", "SBI",    false)),
        // QA scenario PANs — must match the PANs in resources/scenarios/**/*.yml
        Map.entry("453914", new BinInfo("453914", PaymentNetwork.VISA,       "QA Test Bank", "CREDIT", "CLASSIC",  "IN", "QA",    false)),
        Map.entry("520082", new BinInfo("520082", PaymentNetwork.MASTERCARD, "QA Test Bank", "CREDIT", "STANDARD", "IN", "QA",    false)),
        Map.entry("607482", new BinInfo("607482", PaymentNetwork.RUPAY,      "QA Test Bank", "DEBIT",  "CLASSIC",  "IN", "QA",    false)),
        Map.entry("378282", new BinInfo("378282", PaymentNetwork.AMEX,       "QA Test Bank", "CREDIT", "GREEN",    "IN", "QA",    false)),
        Map.entry("305693", new BinInfo("305693", PaymentNetwork.DINERS,     "QA Test Bank", "CREDIT", "CLUB",     "IN", "QA",    false))
    );

    @Override
    public Optional<BinInfo> lookup(String bin6) {
        return Optional.ofNullable(TABLE.get(bin6));
    }
}

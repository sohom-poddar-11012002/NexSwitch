package com.nexswitch.adapters.inbound.iso8583;

import com.nexswitch.domain.model.AuthorizationResult;
import com.nexswitch.domain.model.PaymentMethod;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.*;
import com.nexswitch.domain.port.inbound.AuthorizationCommand;
import com.nexswitch.domain.port.inbound.ProcessPaymentUseCase;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

// LEARN: SimpleChannelInboundHandler — auto-releases the ISOMsg ByteBuf ref after channelRead0; avoids manual ReferenceCountUtil.release()
@ChannelHandler.Sharable
public class Iso8583RequestHandler extends SimpleChannelInboundHandler<ISOMsg> {

    private static final Logger log = LoggerFactory.getLogger(Iso8583RequestHandler.class);

    private static final String RC_APPROVED     = "00";
    private static final String RC_INVALID_CARD = "14";
    private static final String RC_ERROR        = "30";
    private static final String RC_FRAUD        = "59";
    private static final String RC_SWITCH_INOP  = "91";
    private static final Currency INR           = Currency.getInstance("INR");

    private final GenericPackager packager;
    private final ProcessPaymentUseCase processPaymentUseCase;

    public Iso8583RequestHandler(GenericPackager packager, ProcessPaymentUseCase processPaymentUseCase) {
        this.packager = packager;
        this.processPaymentUseCase = processPaymentUseCase;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ISOMsg req) throws Exception {
        String mti = req.getMTI();
        log.info("iso8583.received mti={} stan={} terminal={}", mti, req.getString(11), req.getString(41));

        ISOMsg response = buildResponse(req, mti);
        ctx.writeAndFlush(response);
    }

    private ISOMsg buildResponse(ISOMsg req, String mti) throws Exception {
        return switch (mti) {
            case "0100" -> authResponse(req);
            case "0400" -> reversalResponse(req);
            case "0800" -> networkManagementResponse(req);
            default     -> unknownResponse(req);
        };
    }

    // LEARN: Field echo — ISO 8583 response must mirror STAN (F11), terminal (F41), merchant (F42) so the terminal correlates request↔response
    private ISOMsg authResponse(ISOMsg req) throws Exception {
        String pan = req.getString(2);

        // LEARN: Luhn check belongs in the adapter (inbound boundary), not in the domain service.
        //        The domain receives only validated inputs; format validation is an infrastructure concern.
        if (pan == null || !luhnCheck(pan)) {
            log.warn("iso8583.luhn_fail stan={}", req.getString(11));
            return errorResponse(req, RC_INVALID_CARD);
        }

        String bin6 = pan.substring(0, 6);
        PanHash panHash = PanHash.fromRawPan(pan);
        Money amount = parseAmount(req.getString(4), req.getString(49));
        TerminalId terminalId = TerminalId.of(sanitizeTerminalId(req.getString(41)));
        MerchantId merchantId = MerchantId.of(sanitizeMerchantId(req.getString(42)));
        SystemTraceAuditNumber stan = SystemTraceAuditNumber.of(req.getString(11));
        String posEntryMode = req.getString(22) != null ? req.getString(22) : "000";

        AuthorizationCommand command = new AuthorizationCommand(
                UUID.randomUUID(),
                merchantId,
                terminalId,
                bin6,
                panHash,
                amount,
                inferNetwork(bin6),
                parsePaymentMethod(posEntryMode),
                stan,
                req.getBytes(55),
                req.getBytes(52),
                posEntryMode
        );

        AuthorizationResult result = processPaymentUseCase.execute(command);
        log.info("iso8583.auth_result stan={} outcome={}", req.getString(11), result.getClass().getSimpleName());

        ISOMsg res = new ISOMsg();
        res.setPackager(packager);
        res.setMTI("0110");
        echoFields(req, res, 3, 4, 7, 11, 12, 13, 41, 42);

        return switch (result) {
            case AuthorizationResult.Approved a -> {
                res.set(39, RC_APPROVED);
                res.set(38, a.authCode().value());
                yield res;
            }
            case AuthorizationResult.Declined d -> {
                res.set(39, d.responseCode());
                yield res;
            }
            case AuthorizationResult.Unknown ignored -> {
                res.set(39, RC_SWITCH_INOP);
                yield res;
            }
            case AuthorizationResult.Blocked ignored -> {
                res.set(39, RC_FRAUD);
                yield res;
            }
        };
    }

    // LEARN: Luhn algorithm — ISO/IEC 7812; doubles alternating digits from the right, subtracts 9 if > 9;
    //        checksum valid when total mod 10 == 0. Catches ~98% of single-digit transcription errors.
    private boolean luhnCheck(String pan) {
        int sum = 0;
        boolean alternate = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            int n = Character.digit(pan.charAt(i), 10);
            if (n < 0) return false;
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private Money parseAmount(String field4, String field49) {
        Currency currency;
        try {
            int numericCode = Integer.parseInt(field49);
            currency = Currency.getAvailableCurrencies().stream()
                    .filter(c -> c.getNumericCode() == numericCode)
                    .findFirst()
                    .orElse(INR);
        } catch (Exception e) {
            currency = INR;
        }
        BigDecimal raw = new BigDecimal(field4 != null ? field4 : "000000000000");
        return Money.of(raw.movePointLeft(currency.getDefaultFractionDigits()), currency);
    }

    private String sanitizeTerminalId(String raw) {
        if (raw == null) return "00000000";
        String clean = raw.strip().replaceAll("[^A-Za-z0-9]", "");
        if (clean.isEmpty()) return "00000000";
        if (clean.length() > 8) return clean.substring(0, 8);
        return String.format("%-8s", clean).replace(' ', '0');
    }

    private String sanitizeMerchantId(String raw) {
        if (raw == null) return "UNKNOWN";
        String clean = raw.strip().replaceAll("[^A-Za-z0-9]", "");
        if (clean.length() > 15) return clean.substring(0, 15);
        if (clean.length() < 6) return String.format("%-6s", clean).replace(' ', '0');
        return clean;
    }

    // LEARN: BIN routing — MII identifies the network family; '3' needs a second digit to split Amex vs Diners.
    //        Amex: 34xxxx / 37xxxx. Diners: 300–305 / 36xxxx / 38–39xxxx. Both are closed-loop international.
    private PaymentNetwork inferNetwork(String bin6) {
        return switch (bin6.charAt(0)) {
            case '4' -> PaymentNetwork.VISA;
            case '5' -> PaymentNetwork.MASTERCARD;
            case '3' -> {
                String prefix2 = bin6.substring(0, 2);
                yield switch (prefix2) {
                    case "34", "37" -> PaymentNetwork.AMEX;
                    default         -> PaymentNetwork.DINERS; // 300–305, 36, 38, 39
                };
            }
            default  -> PaymentNetwork.RUPAY;
        };
    }

    private PaymentMethod parsePaymentMethod(String posEntryMode) {
        String prefix = posEntryMode.length() >= 3 ? posEntryMode.substring(0, 3) : posEntryMode;
        return switch (prefix) {
            case "071", "072", "073" -> PaymentMethod.CONTACTLESS;
            default -> PaymentMethod.CARD_CHIP;
        };
    }

    private ISOMsg errorResponse(ISOMsg req, String rc) throws Exception {
        ISOMsg res = new ISOMsg();
        res.setPackager(packager);
        res.setMTI("0110");
        echoFields(req, res, 11, 41);
        res.set(39, rc);
        return res;
    }

    private ISOMsg reversalResponse(ISOMsg req) throws Exception {
        ISOMsg res = new ISOMsg();
        res.setPackager(packager);
        res.setMTI("0410");
        echoFields(req, res, 11, 41);
        res.set(39, RC_APPROVED);
        return res;
    }

    private ISOMsg networkManagementResponse(ISOMsg req) throws Exception {
        ISOMsg res = new ISOMsg();
        res.setPackager(packager);
        res.setMTI("0810");
        echoFields(req, res, 11);
        res.set(39, RC_APPROVED);
        return res;
    }

    private ISOMsg unknownResponse(ISOMsg req) throws Exception {
        ISOMsg res = new ISOMsg();
        res.setPackager(packager);
        res.setMTI("0110");
        echoFields(req, res, 11);
        res.set(39, RC_ERROR);
        return res;
    }

    private void echoFields(ISOMsg src, ISOMsg dst, int... fieldIds) throws Exception {
        for (int id : fieldIds) {
            String val = src.getString(id);
            if (val != null) {
                dst.set(id, val);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("iso8583.handler.error channel={}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}

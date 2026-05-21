package com.nexswitch.adapters.inbound.iso8583;

import com.nexswitch.domain.model.AuthorizationResult;
import com.nexswitch.domain.model.PaymentMethod;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.AuthorizationCode;
import com.nexswitch.domain.port.inbound.AuthorizationCommand;
import com.nexswitch.domain.port.inbound.ProcessPaymentUseCase;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.*;
import java.net.Socket;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// LEARN: NoSpringTest — Netty TCP server is pure Java; no Spring context needed in unit tests; constructor-wiring lets us instantiate the full pipeline directly
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Iso8583InboundAdapterTest {

    private static final String TIME_FMT = "HHmmss";
    private static final String DATE_FMT = "MMdd";

    // LEARN: AtomicReference delegate — server is started once (@BeforeAll), but each test can swap
    //        the use-case behaviour by updating useCaseRef; the proxy lambda captures the reference.
    private final AtomicReference<ProcessPaymentUseCase> useCaseRef = new AtomicReference<>(
            cmd -> new AuthorizationResult.Approved(AuthorizationCode.of("AUTH01"), Instant.now(), null)
    );
    private final ProcessPaymentUseCase proxyUseCase = cmd -> useCaseRef.get().execute(cmd);

    private Iso8583TcpServer server;
    private GenericPackager packager;

    @BeforeAll
    void startServer() throws Exception {
        Iso8583PackagerFactory factory = new Iso8583PackagerFactory();
        packager = factory.getPackager();
        Iso8583ChannelInitializer initializer = new Iso8583ChannelInitializer(factory, proxyUseCase);
        server = new Iso8583TcpServer(0, initializer); // port 0 → OS picks free port
        server.start();
    }

    @AfterAll
    void stopServer() {
        if (server != null) server.stop();
    }

    @BeforeEach
    void resetUseCase() {
        useCaseRef.set(cmd -> new AuthorizationResult.Approved(AuthorizationCode.of("AUTH01"), Instant.now(), null));
    }

    @Test
    void auth0100_receives0110_rc00() throws Exception {
        ISOMsg req = buildAuthRequest("000001");
        ISOMsg res = sendReceive(req);

        assertThat(res.getMTI()).isEqualTo("0110");
        assertThat(res.getString(39)).isEqualTo("00");
        assertThat(res.getString(38)).isNotBlank(); // auth code echoed
        assertThat(res.getString(11)).isEqualTo("000001"); // STAN echoed
        assertThat(res.getString(41)).isEqualTo("TERM0001"); // terminal echoed
    }

    @Test
    void auth0100_echoesAmountAndProcessingCode() throws Exception {
        ISOMsg req = buildAuthRequest("000002");
        ISOMsg res = sendReceive(req);

        assertThat(res.getMTI()).isEqualTo("0110");
        assertThat(res.getString(3)).isEqualTo("000000");
        assertThat(res.getString(4)).isEqualTo("000000060000");
    }

    @Test
    void auth0100_declined_returnsDeclineResponseCode() throws Exception {
        useCaseRef.set(cmd -> new AuthorizationResult.Declined("51", "insufficient funds"));

        ISOMsg res = sendReceive(buildAuthRequest("000010"));

        assertThat(res.getMTI()).isEqualTo("0110");
        assertThat(res.getString(39)).isEqualTo("51");
    }

    @Test
    void auth0100_blocked_returnsFraudCode() throws Exception {
        useCaseRef.set(cmd -> new AuthorizationResult.Blocked("RULE01"));

        ISOMsg res = sendReceive(buildAuthRequest("000011"));

        assertThat(res.getMTI()).isEqualTo("0110");
        assertThat(res.getString(39)).isEqualTo("59");
    }

    @Test
    void auth0100_switchInoperative_returns91() throws Exception {
        useCaseRef.set(cmd -> new AuthorizationResult.Unknown("timeout", false));

        ISOMsg res = sendReceive(buildAuthRequest("000012"));

        assertThat(res.getMTI()).isEqualTo("0110");
        assertThat(res.getString(39)).isEqualTo("91");
    }

    @Test
    void auth0100_invalidLuhn_returnsInvalidCardCode() throws Exception {
        ISOMsg req = buildAuthRequest("000013");
        req.set(2, "4111111111111112"); // last digit changed — Luhn fails

        ISOMsg res = sendReceive(req);

        assertThat(res.getMTI()).isEqualTo("0110");
        assertThat(res.getString(39)).isEqualTo("14");
    }

    @Test
    void auth0100_commandBuiltWithCorrectFields() throws Exception {
        AtomicReference<AuthorizationCommand> captured = new AtomicReference<>();
        useCaseRef.set(cmd -> {
            captured.set(cmd);
            return new AuthorizationResult.Approved(AuthorizationCode.of("AUTH01"), Instant.now(), null);
        });

        sendReceive(buildAuthRequest("000020"));

        AuthorizationCommand cmd = captured.get();
        assertThat(cmd).isNotNull();
        assertThat(cmd.bin6()).isEqualTo("411111");
        assertThat(cmd.stan().value()).isEqualTo("000020");
        assertThat(cmd.terminalId().value()).isEqualTo("TERM0001");
        assertThat(cmd.merchantId().value()).isEqualTo("MERCHANT000001");
        assertThat(cmd.amount().amount()).isEqualByComparingTo("600.00");
        assertThat(cmd.network()).isEqualTo(PaymentNetwork.VISA);
        assertThat(cmd.paymentMethod()).isEqualTo(PaymentMethod.CARD_CHIP);
    }

    @Test
    void reversal0400_receives0410_rc00() throws Exception {
        ISOMsg req = buildReversalRequest("000003");
        ISOMsg res = sendReceive(req);

        assertThat(res.getMTI()).isEqualTo("0410");
        assertThat(res.getString(39)).isEqualTo("00");
        assertThat(res.getString(11)).isEqualTo("000003");
    }

    @Test
    void networkManagement0800_receives0810_rc00() throws Exception {
        ISOMsg req = buildNetworkMgmtRequest("000004");
        ISOMsg res = sendReceive(req);

        assertThat(res.getMTI()).isEqualTo("0810");
        assertThat(res.getString(39)).isEqualTo("00");
        assertThat(res.getString(11)).isEqualTo("000004");
    }

    @Test
    void unknownMti_receives0110_rc30() throws Exception {
        ISOMsg req = new ISOMsg();
        req.setPackager(packager);
        req.setMTI("0200"); // unknown to our switch
        req.set(11, "000005");
        ISOMsg res = sendReceive(req);

        assertThat(res.getMTI()).isEqualTo("0110");
        assertThat(res.getString(39)).isEqualTo("30");
    }

    @Test
    void multipleSequentialRequestsOnSameConnection() throws Exception {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in   = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            for (int i = 1; i <= 3; i++) {
                ISOMsg req = buildAuthRequest(String.format("%06d", i));
                byte[] packed = req.pack();
                out.writeInt(packed.length);
                out.write(packed);
                out.flush();

                int len = in.readInt();
                byte[] body = in.readNBytes(len);
                ISOMsg res = new ISOMsg();
                res.setPackager(packager);
                res.unpack(body);

                assertThat(res.getMTI()).isEqualTo("0110");
                assertThat(res.getString(39)).isEqualTo("00");
                assertThat(res.getString(11)).isEqualTo(String.format("%06d", i));
            }
        }
    }

    // ---- helpers ----

    private ISOMsg buildAuthRequest(String stan) throws Exception {
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FMT));
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FMT));

        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.setMTI("0100");
        msg.set(2,  "4111111111111111");
        msg.set(3,  "000000");
        msg.set(4,  "000000060000");
        msg.set(7,  date + now);
        msg.set(11, stan);
        msg.set(12, now);
        msg.set(13, date);
        msg.set(18, "5411");
        msg.set(22, "051");
        msg.set(25, "00");
        msg.set(41, "TERM0001");
        msg.set(42, "MERCHANT000001 ");
        msg.set(49, "356");
        return msg;
    }

    private ISOMsg buildReversalRequest(String stan) throws Exception {
        String now  = LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FMT));
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FMT));

        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.setMTI("0400");
        msg.set(3,  "000000");
        msg.set(4,  "000000060000");
        msg.set(7,  date + now);
        msg.set(11, stan);
        msg.set(41, "TERM0001");
        // Field 90: MTI(4) + STAN(6) + TransDateTime(10) + AcqInstId(11) + FwdInstId(11) = 42 digits
        msg.set(90, "0100" + "000001" + date + now + "00000000001" + "00000000001");
        return msg;
    }

    private ISOMsg buildNetworkMgmtRequest(String stan) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.setMTI("0800");
        msg.set(11, stan);
        // Field 70 not in packager — network management code sent via field 48 (Additional Data Private)
        msg.set(48, "301");
        return msg;
    }

    private ISOMsg sendReceive(ISOMsg request) throws Exception {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream  in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            byte[] packed = request.pack();
            out.writeInt(packed.length);
            out.write(packed);
            out.flush();

            int len = in.readInt();
            byte[] body = in.readNBytes(len);
            ISOMsg res = new ISOMsg();
            res.setPackager(packager);
            res.unpack(body);
            return res;
        }
    }
}

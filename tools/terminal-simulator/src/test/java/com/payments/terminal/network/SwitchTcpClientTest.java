package com.payments.terminal.network;

import com.payments.terminal.message.Iso8583Builder;
import com.payments.terminal.config.TerminalConfig;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// LEARN: InProcessMockServer — ServerSocket(0) binds to any free OS port; eliminates port conflicts in CI
class SwitchTcpClientTest {

    private ServerSocket serverSocket;
    private int          serverPort;
    private Iso8583Builder builder;
    private TerminalConfig config;

    @BeforeEach
    void setUp() throws Exception {
        serverSocket = new ServerSocket(0); // OS picks a free port
        serverPort   = serverSocket.getLocalPort();
        builder      = new Iso8583Builder();
        config       = new TerminalConfig("TERM0042", "MERCH0000999", "localhost", serverPort, 5_000, "NORMAL_PURCHASE");
    }

    @AfterEach
    void tearDown() throws Exception {
        serverSocket.close();
    }

    /**
     * Sends a 0100 to a mock server; verifies framing and that the 0110 response is parsed correctly.
     * The mock server reads the 4-byte length prefix, echoes back a minimal 0110 with RC=00.
     */
    @Test
    void send_sendsLengthPrefixedFrameAndParsesResponse() throws Exception {
        GenericPackager packager = builder.getPackager();

        CompletableFuture<Void> mockServer = CompletableFuture.runAsync(() -> {
            try (Socket client = serverSocket.accept()) {
                DataInputStream  serverIn  = new DataInputStream(client.getInputStream());
                DataOutputStream serverOut = new DataOutputStream(client.getOutputStream());

                // Read length-prefixed request
                int    reqLen   = serverIn.readInt();
                byte[] reqBytes = serverIn.readNBytes(reqLen);

                ISOMsg request = new ISOMsg();
                request.setPackager(packager);
                request.unpack(reqBytes);

                // Validate request fields
                assertEquals("0100", request.getMTI());
                assertNotNull(request.getString(11), "STAN must be present");

                // Send 0110 response
                ISOMsg response = new ISOMsg();
                response.setPackager(packager);
                response.setMTI("0110");
                response.set(3,  "000000");
                response.set(4,  "000000600000");
                response.set(11, request.getString(11)); // echo STAN
                response.set(38, "AUTH42");               // auth code
                response.set(39, "00");                   // approved

                byte[] respBytes = response.pack();
                serverOut.writeInt(respBytes.length);
                serverOut.write(respBytes);
                serverOut.flush();

            } catch (Exception e) {
                throw new RuntimeException("Mock server error", e);
            }
        });

        ISOMsg request = builder.buildAuthRequest(config, "000001", "000000600000");
        try (SwitchTcpClient client = new SwitchTcpClient("localhost", serverPort, 5_000)) {
            ISOMsg response = client.send(request);

            assertEquals("0110",   response.getMTI());
            assertEquals("00",     response.getString(39));
            assertEquals("AUTH42", response.getString(38).trim());
            assertEquals("000001", response.getString(11)); // STAN echoed back
        }

        mockServer.get(5, TimeUnit.SECONDS); // propagate mock server assertion failures
    }

    /**
     * Verifies that the 4-byte length prefix carries the exact packed message length.
     * This tests the wire protocol independently of message content.
     */
    @Test
    void send_writesExactLengthInFourBytePrefix() throws Exception {
        CompletableFuture<Integer> capturedLength = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try (Socket client = serverSocket.accept()) {
                DataInputStream  serverIn  = new DataInputStream(client.getInputStream());
                DataOutputStream serverOut = new DataOutputStream(client.getOutputStream());

                int    prefixedLen = serverIn.readInt();
                byte[] body        = serverIn.readNBytes(prefixedLen);
                capturedLength.complete(prefixedLen);

                // Send back a trivial 0110 so the client doesn't hang
                GenericPackager packager = builder.getPackager();
                ISOMsg response = new ISOMsg();
                response.setPackager(packager);
                response.setMTI("0110");
                response.set(11, "000001");
                response.set(39, "00");
                byte[] respBytes = response.pack();
                serverOut.writeInt(respBytes.length);
                serverOut.write(respBytes);
                serverOut.flush();

                // The body length must match the prefix
                assertEquals(prefixedLen, body.length);

            } catch (Exception e) {
                capturedLength.completeExceptionally(e);
            }
        });

        ISOMsg request = builder.buildAuthRequest(config, "000001", "000000600000");
        byte[] expectedPacked = request.pack();

        try (SwitchTcpClient client = new SwitchTcpClient("localhost", serverPort, 5_000)) {
            client.send(request);
        }

        int receivedLen = capturedLength.get(5, TimeUnit.SECONDS);
        assertEquals(expectedPacked.length, receivedLen,
            "4-byte length prefix must equal the packed message body length");
    }

    /**
     * Verifies that a SocketTimeoutException is thrown when the server doesn't respond.
     * This is the trigger condition for the TIMEOUT scenario's 0400 reversal.
     */
    @Test
    void send_throwsSocketTimeoutExceptionWhenServerSilent() {
        // Server accepts but never writes a response
        CompletableFuture.runAsync(() -> {
            try (Socket ignored = serverSocket.accept()) {
                Thread.sleep(10_000); // hold connection open, never respond
            } catch (Exception ignored) {}
        });

        ISOMsg request = builder.buildAuthRequest(config, "000001", "000000600000");

        assertThrows(SocketTimeoutException.class, () -> {
            try (SwitchTcpClient client = new SwitchTcpClient("localhost", serverPort, 200)) { // 200ms timeout
                client.send(request);
            }
        });
    }

    /**
     * Verifies connection refused when no server is listening.
     */
    @Test
    void constructor_throwsIOExceptionWhenNothingListening() throws Exception {
        serverSocket.close(); // stop the server
        assertThrows(java.io.IOException.class,
            () -> new SwitchTcpClient("localhost", serverPort, 500));
    }
}

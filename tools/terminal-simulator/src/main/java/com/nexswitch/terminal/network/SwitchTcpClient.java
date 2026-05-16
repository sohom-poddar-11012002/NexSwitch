package com.nexswitch.terminal.network;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

// LEARN: LengthPrefixedFraming — 4-byte big-endian int before each message body eliminates TCP stream boundary ambiguity
public final class SwitchTcpClient implements Closeable {

    // LEARN: TCPConnection — a socket is a 4-tuple (src-ip, src-port, dst-ip, dst-port); one server port holds many connections
    private final Socket socket;
    private final DataOutputStream out;
    private final DataInputStream  in;

    /**
     * Opens a TCP connection to the acquiring-service switch.
     *
     * @param host      switch hostname or IP
     * @param port      switch ISO 8583 listener port (default 8000)
     * @param timeoutMs connect timeout AND per-read socket timeout in milliseconds
     */
    public SwitchTcpClient(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    /**
     * Sends an ISO 8583 request and blocks until the response arrives or the socket timeout fires.
     *
     * Wire format (both directions):
     *   [4 bytes big-endian length][N bytes ISO 8583 packed body]
     *
     * @param request ISOMsg with packager already set (use Iso8583Builder)
     * @return parsed ISOMsg response (e.g. 0110 with field 39 = response code)
     */
    public ISOMsg send(ISOMsg request) throws IOException, ISOException {
        byte[] packed = request.pack();
        out.writeInt(packed.length);
        out.write(packed);
        out.flush();

        int    responseLen   = in.readInt();
        byte[] responseBytes = in.readNBytes(responseLen);

        ISOMsg response = new ISOMsg();
        response.setPackager(request.getPackager());
        response.unpack(responseBytes);
        return response;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}

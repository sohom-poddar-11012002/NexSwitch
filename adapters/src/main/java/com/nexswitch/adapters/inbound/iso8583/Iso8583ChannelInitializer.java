package com.nexswitch.adapters.inbound.iso8583;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.springframework.stereotype.Component;

// LEARN: ChannelInitializer — called once per accepted connection; each channel gets its own LengthFieldBasedFrameDecoder instance (stateful, not @Sharable)
@Component
public class Iso8583ChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1 MB
    private static final int LENGTH_FIELD_OFFSET = 0;
    private static final int LENGTH_FIELD_LENGTH = 4;
    private static final int LENGTH_ADJUSTMENT = 0;
    private static final int INITIAL_BYTES_TO_STRIP = 4; // strip the 4-byte length header before passing body to decoder

    private final Iso8583Decoder decoder;
    private final Iso8583Encoder encoder;
    private final Iso8583RequestHandler handler;

    public Iso8583ChannelInitializer(Iso8583PackagerFactory packagerFactory) {
        this.decoder = new Iso8583Decoder(packagerFactory.getPackager());
        this.encoder = new Iso8583Encoder();
        this.handler = new Iso8583RequestHandler(packagerFactory.getPackager());
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                // LEARN: LengthFieldBasedFrameDecoder — reads the 4-byte big-endian length prefix, buffers bytes until full frame arrives, then fires exactly one ByteBuf of the body downstream; eliminates TCP stream reassembly in all downstream handlers
                .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                        MAX_FRAME_LENGTH,
                        LENGTH_FIELD_OFFSET,
                        LENGTH_FIELD_LENGTH,
                        LENGTH_ADJUSTMENT,
                        INITIAL_BYTES_TO_STRIP))
                .addLast("iso8583Decoder", decoder)
                .addLast("iso8583Encoder", encoder)
                .addLast("requestHandler", handler);
    }
}

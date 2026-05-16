package com.nexswitch.adapters.inbound.iso8583;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;

import java.util.List;

// LEARN: FrameDecoder — LengthFieldBasedFrameDecoder upstream strips the 4-byte header; this handler receives exact message body with no stream re-assembly needed
@ChannelHandler.Sharable
public class Iso8583Decoder extends MessageToMessageDecoder<ByteBuf> {

    private final GenericPackager packager;

    public Iso8583Decoder(GenericPackager packager) {
        this.packager = packager;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] body = new byte[in.readableBytes()];
        in.readBytes(body);
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.unpack(body);
        out.add(msg);
    }
}

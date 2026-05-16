package com.payments.adapters.inbound.iso8583;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.jpos.iso.ISOMsg;

// LEARN: NettyEncoder — MessageToByteEncoder allocates a PooledByteBuf per write; writeInt(len) + writeBytes(body) matches SwitchTcpClient's 4-byte length-prefix protocol
@ChannelHandler.Sharable
public class Iso8583Encoder extends MessageToByteEncoder<ISOMsg> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ISOMsg msg, ByteBuf out) throws Exception {
        byte[] body = msg.pack();
        out.writeInt(body.length);
        out.writeBytes(body);
    }
}

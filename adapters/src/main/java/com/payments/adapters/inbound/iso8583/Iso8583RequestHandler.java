package com.payments.adapters.inbound.iso8583;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// LEARN: SimpleChannelInboundHandler — auto-releases the ISOMsg ByteBuf ref after channelRead0; avoids manual ReferenceCountUtil.release()
@ChannelHandler.Sharable
public class Iso8583RequestHandler extends SimpleChannelInboundHandler<ISOMsg> {

    private static final Logger log = LoggerFactory.getLogger(Iso8583RequestHandler.class);

    private static final String RC_APPROVED  = "00";
    private static final String RC_ERROR     = "30";
    private static final String AUTH_CODE    = "AUTH01";

    private final GenericPackager packager;

    public Iso8583RequestHandler(GenericPackager packager) {
        this.packager = packager;
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
        ISOMsg res = new ISOMsg();
        res.setPackager(packager);
        res.setMTI("0110");
        echoFields(req, res, 3, 4, 7, 11, 12, 13, 41, 42);
        res.set(39, RC_APPROVED);
        res.set(38, AUTH_CODE);
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

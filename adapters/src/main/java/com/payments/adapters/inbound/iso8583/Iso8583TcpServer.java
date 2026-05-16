package com.payments.adapters.inbound.iso8583;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

// LEARN: SmartLifecycle — Spring calls start() after context refresh and stop() on shutdown; avoids @PostConstruct thread leak where the context may not be fully wired yet
// LEARN: Boss+Worker groups — one boss thread accepts connections (accept(2) syscall); N worker threads handle all I/O for accepted channels (NIO event loop); OS TCP stack queues SYN packets while boss is busy
@Component
public class Iso8583TcpServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(Iso8583TcpServer.class);

    private final int configuredPort;
    private final Iso8583ChannelInitializer channelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannelFuture;
    private volatile boolean running;

    public Iso8583TcpServer(
            @Value("${iso8583.port:8000}") int configuredPort,
            Iso8583ChannelInitializer channelInitializer) {
        this.configuredPort = configuredPort;
        this.channelInitializer = channelInitializer;
    }

    @Override
    public void start() {
        bossGroup   = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(channelInitializer);

            serverChannelFuture = bootstrap.bind(configuredPort).sync();
            running = true;
            int bound = ((InetSocketAddress) serverChannelFuture.channel().localAddress()).getPort();
            log.info("iso8583.server.started port={}", bound);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ISO 8583 server interrupted during bind", e);
        }
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        try {
            serverChannelFuture.channel().close().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            log.info("iso8583.server.stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Actual bound port — differs from configuredPort when configuredPort=0 (used in tests). */
    public int getPort() {
        if (serverChannelFuture == null) {
            throw new IllegalStateException("Server not started");
        }
        return ((InetSocketAddress) serverChannelFuture.channel().localAddress()).getPort();
    }
}

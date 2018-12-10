package com.xcxcxcxcx.core.connector.channel;

import com.xcxcxcxcx.mini.api.connector.connection.Connection;
import com.xcxcxcxcx.mini.api.connector.connection.ConnectionFactory;
import com.xcxcxcxcx.mini.api.connector.connection.ConnectionManager;
import com.xcxcxcxcx.mini.api.connector.message.Packet;
import com.xcxcxcxcx.mini.api.connector.message.PacketDispatcher;
import com.xcxcxcxcx.mini.tools.log.LogUtils;
import com.xcxcxcxcx.mini.tools.monitor.cost.CostUtils;
import com.xcxcxcxcx.network.connection.NettyConnection;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.traffic.GlobalChannelTrafficShapingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;

/**
 * @author XCXCXCXCX
 * @since 1.0
 */
@ChannelHandler.Sharable
public class ServerChannelHandler extends ChannelInboundHandlerAdapter{

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerChannelHandler.class);

    private ConnectionFactory connectionFactory;

    private final ConnectionManager connectionManager;

    private final PacketDispatcher packetDispatcher;

    public ServerChannelHandler(ConnectionManager connectionManager, PacketDispatcher packetDispatcher) {
        this.connectionManager = connectionManager;
        this.packetDispatcher = packetDispatcher;
        connectionFactory = NettyConnection::new;
    }

    /**
     * 封装channel并注册到connectionManager
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        connectionManager.addConnection(connectionFactory.create(ctx.channel()));
        LogUtils.connection.info("client connected conn={}", ctx.channel());
    }

    /**
     * connectionManager删除并关闭连接
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        connectionManager.removeAndCloseConnection(ctx.channel());
        LogUtils.connection.info("client disconnected conn={}", ctx.channel());
    }

    /**
     * 监听到读事件，将packet转发到消息分发中心
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        Connection connection = connectionManager.getConnection(ctx.channel());

        try {
            CostUtils.begin("packet read and dispatch");
            packetDispatcher.dispatch((Packet) msg, connection);
        }finally {
            CostUtils.end();
        }

    }

    /**
     * 捕获到channel上的异常
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Connection connection = connectionManager.getConnection(ctx.channel());
        LogUtils.connection.error("client caught ex, conn={}", connection);
        LOGGER.error("caught an ex, channel={}, conn={}", ctx.channel(), connection, cause);
        ctx.close();
    }
}
package org.my.proxy;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author 董帅阳
 * @version 1.0
 * @date 2019/9/26
 **/
public class ProxyConnection {

    private final SocketChannel proxySocket;

    private final SocketChannel targetSocket;

    private final BlockingQueue<ByteBuffer> sendQueue;

    private final BlockingQueue<ByteBuffer> recvQueue;

    public ProxyConnection(SocketChannel proxySocket, SocketChannel targetSocket) {
        this(proxySocket, targetSocket, new LinkedBlockingDeque<>(), new LinkedBlockingDeque<>());
    }

    public ProxyConnection(SocketChannel proxySocket, SocketChannel targetSocket, BlockingQueue<ByteBuffer> sendQueue, BlockingQueue<ByteBuffer> recvQueue) {
        this.proxySocket = proxySocket;
        this.targetSocket = targetSocket;
        this.sendQueue = sendQueue;
        this.recvQueue = recvQueue;
    }

    public BlockingQueue<ByteBuffer> getSendQueue() {
        return sendQueue;
    }

    public BlockingQueue<ByteBuffer> getRecvQueue() {
        return recvQueue;
    }

    public SocketChannel getProxySocket() {
        return proxySocket;
    }

    public SocketChannel getTargetSocket() {
        return targetSocket;
    }
}

package org.my.proxy;

import com.codahale.metrics.MetricRegistry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author 董帅阳
 * @version 1.0
 * @date 2019/9/26
 **/
public class ProxyWorker implements Runnable {

    private final MetricRegistry metrics;

    private final Selector selector;

    private final BlockingQueue<ProxyConnection> registerQueue;

    ProxyWorker(MetricRegistry metrics) throws IOException {
        this.metrics = metrics;

        selector = Selector.open();

        registerQueue = new LinkedBlockingQueue<>();
    }

    public void registerProxyConnection(ProxyConnection proxyConnection) {
        registerQueue.add(proxyConnection);
    }


    @Override
    public void run() {

        try {
            doSelect();
        } catch (IOException e){
            return;
        }
    }

    private void registerPendingConn() throws IOException {

        while (true) {

            ProxyConnection con = registerQueue.poll();

            if(con == null) break;

            con.getProxySocket().register(selector, SelectionKey.OP_READ, new ConnWrap(con, ConnType.Proxy));
            con.getTargetSocket().register(selector, SelectionKey.OP_READ, new ConnWrap(con, ConnType.Target));
        }
    }

    private void doSelect() throws IOException {

        while (true) {

            registerPendingConn();

            selector.select(1000);

            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {

                SelectionKey selectionKey = iterator.next();
                iterator.remove();

                ConnWrap connWrap = (ConnWrap) selectionKey.attachment();

                if(selectionKey.isValid() && selectionKey.isReadable()) {

                    switch (connWrap.connType) {
                        case Proxy:
                            processProxyRead(connWrap.con);
                            break;
                        case Target:

                            processTargetRead(connWrap.con);
                            break;
                    }
                }
            }
        }
    }

    private void processProxyRead(ProxyConnection proxyConnection) {

        try {
            SocketChannel socketChannel = proxyConnection.getProxySocket();
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024);
            while (true) {

                int n = socketChannel.read(byteBuffer);

                if (n > 0) {
                    byteBuffer.flip();
                    while (byteBuffer.hasRemaining() && proxyConnection.getTargetSocket().finishConnect()) {
                        proxyConnection.getTargetSocket().write(byteBuffer);
                    }
                }

                if (n == -1) {
                    socketChannel.close();
                    proxyConnection.getTargetSocket().close();
                }

                if (n == 0) {
                    metrics.meter("proxy-read").mark();
                    break;
                }
                byteBuffer.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processTargetRead(ProxyConnection proxyConnection) {

        try {
            SocketChannel socketChannel = proxyConnection.getTargetSocket();
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024);
            while (true) {

                int n = socketChannel.read(byteBuffer);

                if (n > 0) {
                    byteBuffer.flip();
                    while (byteBuffer.hasRemaining()) {
                        proxyConnection.getProxySocket().write(byteBuffer);
                    }
                }

                if (n == -1) {
                    socketChannel.close();
                    proxyConnection.getProxySocket().close();
                }

                if (n == 0) {
                    metrics.meter("target-recv").mark();
                    break;
                }
                byteBuffer.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    enum ConnType {

        Proxy,

        Target
    }

    private class ConnWrap {

        private ProxyConnection con;

        private ConnType connType;

        public ConnWrap(ProxyConnection con, ConnType connType) {
            this.con = con;
            this.connType = connType;
        }
    }
}

package org.my.proxy;

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

    private final Selector selector;

    private final BlockingQueue<ProxyConnection> registerQueue;

    ProxyWorker() throws IOException {

        selector = Selector.open();

        registerQueue = new LinkedBlockingQueue<>();
    }

    public void registerProxyConnection(ProxyConnection proxyConnection) throws IOException {
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

            con.getProxySocket().register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE, new ConnWrap(con, ConnType.Proxy));
            con.getTargetSocket().register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE|SelectionKey.OP_CONNECT, new ConnWrap(con, ConnType.Target));
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

                SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

                if(selectionKey.isValid() && selectionKey.isReadable()) {

                    switch (connWrap.connType) {
                        case Proxy:
                            while (true) {
                                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                                int n = socketChannel.read(byteBuffer);

                                if(n > 0) {
                                    byteBuffer.flip();
                                    connWrap.con.getSendQueue().add(byteBuffer);
                                }

                                if(n <= -1) {
                                    socketChannel.close();
                                    connWrap.con.getTargetSocket().close();
                                }

                                if(n < 1024) {
                                    break;
                                }
                            }
                            break;
                        case Target:

                            while (true) {
                                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                                int n = socketChannel.read(byteBuffer);

                                if(n > 0) {
                                    byteBuffer.flip();
                                    connWrap.con.getRecvQueue().add(byteBuffer);
                                }

                                if(n <= -1) {
                                    socketChannel.close();
                                }

                                if(n < 1024) {
                                    break;
                                }
                            }

                            break;
                    }
                }


                if(selectionKey.isValid() && selectionKey.isWritable()) {

                    switch (connWrap.connType) {
                        case Proxy:

                            BlockingQueue<ByteBuffer> recvQueue = connWrap.con.getRecvQueue();

                            while (true) {
                                ByteBuffer byteBuffer = recvQueue.peek();

                                if(byteBuffer == null) break;

                                socketChannel.write(byteBuffer);

                                if(!byteBuffer.hasRemaining()) recvQueue.poll();
                            }
                            break;

                        case Target:

                            BlockingQueue<ByteBuffer> sendQueue = connWrap.con.getSendQueue();

                            while (true) {
                                ByteBuffer byteBuffer = sendQueue.peek();

                                if(byteBuffer == null) break;

                                socketChannel.write(byteBuffer);

                                if(!byteBuffer.hasRemaining()) sendQueue.remove();

                            }

                            break;
                    }
                }

                if(selectionKey.isValid() && selectionKey.isConnectable()) {
                    socketChannel.finishConnect();
                }
            }
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

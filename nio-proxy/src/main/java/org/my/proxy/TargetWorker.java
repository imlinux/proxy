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
public class TargetWorker implements Runnable {

    private final Selector selector;

    private final BlockingQueue<ProxyConnection> registerQueue;

    TargetWorker() throws IOException {

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

            con.getTargetSocket().register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE|SelectionKey.OP_CONNECT, con);
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

                ProxyConnection proxyConnection = (ProxyConnection) selectionKey.attachment();
                if(selectionKey.isValid() && selectionKey.isReadable()) {

                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

                    while (true) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                        int n = socketChannel.read(byteBuffer);

                        if(n > 0) {
                            byteBuffer.flip();
                            proxyConnection.getRecvQueue().add(byteBuffer);
                        }

                        if(n <= -1) {
                            socketChannel.close();
                        }

                        if(n < 1024) {
                            break;
                        }
                    }
                }


                if(selectionKey.isValid() && selectionKey.isWritable()) {

                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

                    BlockingQueue<ByteBuffer> sendQueue = proxyConnection.getSendQueue();

                    while (true) {
                        ByteBuffer byteBuffer = sendQueue.peek();

                        if(byteBuffer == null) break;

                        socketChannel.write(byteBuffer);

                        if(!byteBuffer.hasRemaining()) sendQueue.remove();

                    }
                }

                if(selectionKey.isValid() && selectionKey.isConnectable()) {
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    socketChannel.finishConnect();
                }
            }
        }
    }
}

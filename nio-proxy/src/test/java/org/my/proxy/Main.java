package org.my.proxy;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * @author 董帅阳
 * @version 1.0
 * @date 2019/9/26
 **/
public class Main {


    public static void main(String[] args) throws Exception {

        ByteBuffer bf = ByteBuffer.wrap("hello".getBytes());

        Selector selector = Selector.open();

        ServerSocketChannel ssc = ServerSocketChannel.open();

        ssc.configureBlocking(false);

        ssc.socket().bind(new InetSocketAddress(9999));

        ssc.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {

            int n = selector.select(1000);

            System.out.println(n);
            if(n == 0) {
                continue;
            }

            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {

                SelectionKey selectionKey = iterator.next();

                if(selectionKey.isAcceptable()) {
                    System.out.println("acc");

//                    ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
//
//                    SocketChannel socketChannel = serverSocketChannel.accept();
                }


                iterator.remove();
            }
        }
    }
}

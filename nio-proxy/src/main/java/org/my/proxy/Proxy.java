package org.my.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author 董帅阳
 * @version 1.0
 * @date 2019/9/26
 **/
public class Proxy implements Runnable {

    private final Selector selector;

    private final ServerSocketChannel serverSocketChannel;

    private final SocketAddress bindAddress;

    private final ProxyWorker proxyWorker;

    private final TargetWorker targetWorker;

    private Proxy(SocketAddress bindAddress) throws IOException {

        this.bindAddress = bindAddress;
        selector = Selector.open();

        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);

        proxyWorker = new ProxyWorker();
        targetWorker = new TargetWorker();
    }


    public void run() {

        try {
            serverSocketChannel.bind(bindAddress);

            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            doSelect();
        } catch (IOException e) {
            return;
        }
    }

    private void doSelect() throws IOException {

        while (true) {

            int n = selector.select(1000);

            if(n > 0) {

                Set<SelectionKey> selectedKey = selector.selectedKeys();

                Iterator<SelectionKey> iterator = selectedKey.iterator();

                while (iterator.hasNext()) {

                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();

                    if(selectionKey.isAcceptable()) {
                        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
                        doAccept(serverSocketChannel);
                    }
                }
            }
        }
    }

    private void doAccept(ServerSocketChannel serverSocketChannel) throws IOException {

        while (true) {
            SocketChannel sc = serverSocketChannel.accept();
            if (sc == null) break;

            sc.configureBlocking(false);

            SocketChannel targetSocketChannel = SocketChannel.open();
            targetSocketChannel.configureBlocking(false);
            targetSocketChannel.connect(new InetSocketAddress("127.0.0.1", 3306));

            ProxyConnection proxyConnection = new ProxyConnection(sc, targetSocketChannel);

            proxyWorker.registerProxyConnection(proxyConnection);
            targetWorker.registerProxyConnection(proxyConnection);
        }
    }

    public static void main(String[] args) throws Exception {

        ExecutorService executorService = Executors.newFixedThreadPool(4);

        Proxy proxy = new Proxy(new InetSocketAddress(8080));


        executorService.execute(proxy);

        executorService.execute(proxy.proxyWorker);
        executorService.execute(proxy.targetWorker);
    }
}

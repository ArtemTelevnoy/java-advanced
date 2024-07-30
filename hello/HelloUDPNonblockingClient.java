package info.kgeorgiy.ja.televnoi.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

import static info.kgeorgiy.ja.televnoi.hello.Methods.*;

/**
 * class for imitation client
 *
 * @author Artem Televnoy
 */
public class HelloUDPNonblockingClient implements HelloClient {

    /**
     * default constructor
     */
    public HelloUDPNonblockingClient() {
    }

    /**
     * run client
     *
     * @param host server host
     * @param port server port
     * @param prefix request prefix
     * @param threads number of request threads
     * @param requests number of requests per thread.
     */
    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        final SocketAddress socketAddr = getSocketAddress(host, port);
        final Selector selector = getSelector();

        for (int i = 1; i <= threads; i++) {
            try {
                final DatagramChannel channel = openChannel();
                channel.connect(socketAddr);

                channel.register(selector, SelectionKey.OP_WRITE, new VisorObj(i));
            } catch (IOException e) {
                throw new RuntimeException("Error creating channel", e);
            }
        }

        reqOfThreads(selector, socketAddr, requests, prefix);
    }

    private static void reqOfThreads(final Selector selector, final SocketAddress socketAddress,
                                     final int requests, final String prefix) {
        while (!(selector.keys().isEmpty() || Thread.currentThread().isInterrupted())) {
            try {
                if (selector.select(WAITING_TIME) == 0) {
                    selector.keys().stream().filter(SelectionKey::isWritable).
                            forEach(o -> requester(o, prefix, socketAddress));
                    continue;
                }
            } catch (IOException e) {
                throw new RuntimeException("Error while processing request", e);
            }

            final Set<SelectionKey> keys = selector.selectedKeys();
            final Iterator<SelectionKey> iterator = keys.iterator();

            while (iterator.hasNext()) {
                final SelectionKey key = iterator.next();

                if (key.isReadable()) {
                    responser(key, prefix, requests);
                } else {
                    requester(key, prefix, socketAddress);
                }

                iterator.remove();
            }
        }
    }

    private static void responser(final SelectionKey key,
                                  final String prefix, final int requests) {
        final DatagramChannel channel = (DatagramChannel) key.channel();
        final VisorObj visorObj = (VisorObj) key.attachment();
        final ByteBuffer buffer = visorObj.buffer;

        buffer.clear();
        try {
            channel.receive(buffer);
        } catch (IOException e) {
            throw new RuntimeException("Bad response", e);
        }
        buffer.flip();

        if (checkMessage(CHARSET.decode(buffer).toString(), prefix, visorObj.thread, visorObj.request)) {
            visorObj.request++;
        }

        if (visorObj.request > requests) {
            try {
                channel.close();
            } catch (IOException e) {
                throw new RuntimeException("Error occurs with channel", e);
            }
        } else {
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    private static void requester(final SelectionKey key,
                                  final String prefix, final SocketAddress socketAddress) {
        final VisorObj visorObj = (VisorObj) key.attachment();
        final DatagramChannel channel = (DatagramChannel) key.channel();
        final ByteBuffer buffer = visorObj.buffer;

        buffer.clear();
        try {
            logs("Request was sent:%n%s%d_%d%n%n", prefix, visorObj.thread, visorObj.request);
            channel.send(ByteBuffer.wrap(String.format(
                    "%s%d_%d", prefix, visorObj.thread, visorObj.request).getBytes(CHARSET)), socketAddress);
        } catch (IOException e) {
            throw new RuntimeException("Bad request", e);
        }
        buffer.flip();

        key.interestOps(SelectionKey.OP_READ);
    }

    private static class VisorObj {
        private final ByteBuffer buffer;
        private final int thread;
        private int request;

        public VisorObj(final int thread) {
            buffer = ByteBuffer.allocate(BUF_SIZE);
            this.thread = thread;
            request = 1;
        }
    }

    /**
     * main method
     *
     * @param args args format: {@code host}, {@code port}, {@code prefix}, {@code threads}, {@code requests}
     */
    public static void main(String[] args) {
        clientMainer(args, new HelloUDPNonblockingClient());
    }
}

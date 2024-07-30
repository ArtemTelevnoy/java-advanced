package info.kgeorgiy.ja.televnoi.hello;

import info.kgeorgiy.java.advanced.hello.NewHelloServer;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.*;

import static info.kgeorgiy.ja.televnoi.hello.Methods.*;

/**
 * Class for imitation server
 *
 * @author Artem Televnoy
 */
public class HelloUDPNonblockingServer implements NewHelloServer {
    private Selector selector;
    private Set<DatagramChannel> channelSet;

    private ExecutorService listener;
    private ExecutorService workers;

    private Queue<Future<Answer>> answers;
    private Queue<ByteBuffer> freeBuffers;

    /**
     * default Constructor
     */
    public HelloUDPNonblockingServer() {
    }

    /**
     * starting server work
     *
     * @param map mapped from ports in prefix
     * @param threads number of working threads.
     */
    @Override
    public void start(int threads, Map<Integer, String> map) {
        answers = new LinkedList<>();
        channelSet = new HashSet<>();

        freeBuffers = new LinkedList<>();
        for (int i = 0; i < threads; i++) {
            freeBuffers.add(ByteBuffer.allocate(BUF_SIZE));
        }

        selector = getSelector();

        for (final int port : map.keySet()) {
            final DatagramChannel datagramChannel = openChannel();
            try {
                datagramChannel.bind(getSocketAddress(null, port));
                datagramChannel.register(selector, SelectionKey.OP_READ);
            } catch (IOException e) {
                throw new RuntimeException("Couldn't initialize datagram channel", e);
            }
            channelSet.add(datagramChannel);
        }

        workers = Executors.newFixedThreadPool(threads);
        listener = Executors.newSingleThreadExecutor();
        listener.submit(() -> process(map));
    }

    /**
     * close working pools, selector and channels
     */
    @Override
    public void close() {
        if (channelSet == null) {
            return;
        }

        try {
            for (final DatagramChannel chan : channelSet) {
                chan.close();
            }
            selector.close();
        } catch (IOException e) {
            error("Bad closing selector or channel: " + e.getMessage());
        }

        channelSet = null;
        listener.close();
        workers.close();
    }

    private void process(final Map<Integer, String> map) {
        while (!Thread.currentThread().isInterrupted() && selector.isOpen()) {
            try {
                if (selector.select(WAITING_TIME) == 0) {
                    selector.keys().stream().filter(SelectionKey::isWritable).
                            forEach(this::isDoneAnswer);
                    continue;
                }
            } catch (IOException e) {
                throw new RuntimeException("Error while selecting channels", e);
            }
            final Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                final SelectionKey key = iterator.next();

                if (key.isReadable()) {
                    receiveRequest(key, map);
                } else {
                    isDoneAnswer(key);
                }

                if (freeBuffers.isEmpty()) {
                    key.interestOps(SelectionKey.OP_WRITE);
                } else if (answers.isEmpty()) {
                    key.interestOps(SelectionKey.OP_READ);
                }

                iterator.remove();
            }
        }
    }

    private void receiveRequest(final SelectionKey key, final Map<Integer, String> map) {
        final ByteBuffer buffer = freeBuffers.remove();
        final DatagramChannel chan = (DatagramChannel) key.channel();

        buffer.clear();
        final SocketAddress address;
        try {
            address = chan.receive(buffer);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't receive datagram via channel: " + e.getMessage(), e);
        }
        buffer.flip();

        final Future<Answer> future = workers.submit(() -> {
            final String message = map.get(chan.socket().getLocalPort()).
                    replace("$", CHARSET.decode(buffer).toString());
            //Thread.sleep(1);

            return new Answer(ByteBuffer.wrap(message.getBytes(CHARSET)), address);
        });

        answers.add(future);

        key.interestOpsOr(SelectionKey.OP_WRITE);
    }

    private void isDoneAnswer(final SelectionKey key) {
        for (final Future<Answer> future : answers) {
            if (future.isDone()) {
                answering(key, future);
                break;
            }
        }
    }

    private void answering(final SelectionKey key, final Future<Answer> future) {
        final Answer answer;
        try {
            answer = future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Bad getting task", e);
        }
        answers.remove(future);

        answer.buffer.clear();
        try {
            ((DatagramChannel) key.channel()).send(answer.buffer, answer.address);
        } catch (IOException e) {
            throw new RuntimeException("Bad sending answer", e);
        }
        answer.buffer.flip();

        freeBuffers.add(answer.buffer);

        key.interestOpsOr(SelectionKey.OP_READ);
    }

    private record Answer(ByteBuffer buffer, SocketAddress address) {
    }

    /**
     * main method for starting server work
     *
     * @param args format: {@code port} {@code threads}
     */
    public static void main(String[] args) {
        serverMainer(args, new HelloUDPNonblockingServer());
    }
}

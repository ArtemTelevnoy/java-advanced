package info.kgeorgiy.ja.televnoi.hello;

import info.kgeorgiy.java.advanced.hello.NewHelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static info.kgeorgiy.ja.televnoi.hello.Methods.*;

/**
 * Class for imitation server
 *
 * @author Artem Televnoy
 */
public class HelloUDPServer implements NewHelloServer {
    private Set<DatagramSocket> sockets;
    private ExecutorService workers;
    private ExecutorService listener;

    /**
     * starting server work
     *
     * @param ports map server port.
     * @param threads number of working threads.
     */
    @Override
    public void start(int threads, Map<Integer, String> ports) {
        workers = Executors.newFixedThreadPool(threads);
        listener = Executors.newFixedThreadPool(Math.max(1, ports.size()));
        sockets = ConcurrentHashMap.newKeySet();

        for (final int key : ports.keySet()) {
            final int len;

            final DatagramSocket el;
            try {
                el = new DatagramSocket(key);
                sockets.add(el);
                len = el.getReceiveBufferSize();
            } catch (SocketException e) {
                error(String.format("Error while creating socket on %d port", key));
                return;
            }

            listener.submit(() -> {
                while (!(el.isClosed() || Thread.currentThread().isInterrupted())) {
                    try {
                        final DatagramPacket msg = new DatagramPacket(new byte[len], len);
                        el.receive(msg);

                        workers.submit(() -> {
                            msg.setData(ports.get(key).replace("$", new String(
                                    msg.getData(), msg.getOffset(), msg.getLength(), CHARSET)).getBytes(CHARSET));

                            try {
                                el.send(msg);
                            } catch (IOException e) {
                                if (!el.isClosed()) {
                                    error("Error sending message because socket was closed: " + e.getMessage());
                                }
                            }
                        });
                    } catch (IOException e) {
                        if (!el.isClosed()) {
                            error("Error receiving message because socket was closed: " + e.getMessage());
                        }
                    }
                }
            });
        }
    }

    /**
     * close socket and threadPools
     */
    @Override
    public void close() {
        if (sockets != null) {
            sockets.forEach(DatagramSocket::close);
        }
        workers.close();
        listener.close();
    }

    /**
     * main method for starting server work
     *
     * @param args format: {@code port} {@code threads}
     */
    public static void main(String[] args) {
        serverMainer(args, new HelloUDPServer());
    }
}

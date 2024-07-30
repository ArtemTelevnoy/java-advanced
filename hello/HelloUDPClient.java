package info.kgeorgiy.ja.televnoi.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static info.kgeorgiy.ja.televnoi.hello.Methods.*;

/**
 * class for imitation client
 *
 * @author Artem Televnoy
 */
public class HelloUDPClient implements HelloClient {

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
        final ExecutorService workPool = Executors.newFixedThreadPool(threads);

        for (int i = 1; i <= threads; i++) {
            final int id = i;
            workPool.submit(() -> reqOfThreads(id, socketAddr, requests, prefix));
        }

        workPool.close();
    }

    private static void reqOfThreads(final int id, final SocketAddress socketAddress, final int requests, final String prefix) {
        try (final DatagramSocket socket = new DatagramSocket()) {
            final DatagramPacket response = new DatagramPacket(
                    new byte[socket.getReceiveBufferSize()], socket.getReceiveBufferSize());
            socket.setSoTimeout(WAITING_TIME);

            for (int j = 1; j <= requests; j++) {
                final String reqMes = String.format("%s%d_%d", prefix, id, j);
                final DatagramPacket msg = new DatagramPacket(reqMes.getBytes(CHARSET), reqMes.length(), socketAddress);

                while (!(socket.isClosed() || Thread.currentThread().isInterrupted())) {
                    try {
                        socket.send(msg);
                        logs("Request was sent:%n%s%n%n", reqMes);

                        socket.receive(response);
                        final String resMes = new String(response.getData(),
                                response.getOffset(), response.getLength(), CHARSET);

                        if (checkMessage(resMes, prefix, id, j)) {
                            break;
                        }
                    } catch (IOException e) {
                        error("Error while processing request: " + e.getMessage());
                    }
                }
            }
        } catch (SocketException e) {
            error("Socket didn't created, thread was skipped");
        }
    }

    /**
     * main method
     *
     * @param args args format: {@code host}, {@code port}, {@code prefix}, {@code threads}, {@code requests}
     */
    public static void main(String[] args) {
        clientMainer(args, new HelloUDPClient());
    }
}

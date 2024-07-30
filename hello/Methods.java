package info.kgeorgiy.ja.televnoi.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Methods {
    protected static final int BUF_SIZE = 1024;
    protected static final int WAITING_TIME = 500;
    protected static final Charset CHARSET = StandardCharsets.UTF_8;

    protected static void error(final String mes) {
        System.err.println(mes);
    }

    protected static void logs(final String mes, final Object... args) {
        System.out.printf(mes, args);
    }

    protected static InetSocketAddress getSocketAddress(final String host, final int port) {
        try {
            return host == null ? new InetSocketAddress(port)
                    : new InetSocketAddress(InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Incorrect Host", e);
        }
    }

    protected static DatagramChannel openChannel() {
        final DatagramChannel channel;
        try {
            channel = DatagramChannel.open();
        } catch (IOException e) {
            throw new RuntimeException("Error opening channel", e);
        }

        try {
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            channel.configureBlocking(false);
            return channel;
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException ex) {
                throw new RuntimeException("Bad closing channel", ex);
            }
            throw new RuntimeException("Bad settings", e);
        }
    }

    protected static Selector getSelector() {
        try {
            return Selector.open();
        } catch (IOException e) {
            throw new RuntimeException("Error creating selector", e);
        }
    }

    protected static boolean checkMessage(final String resMes, final String prefix, final int i, final int j) {
        if (resMes.contains(String.format("%s%d_%d", prefix, i, j))) {
            logs("Success answer for request. Response was received:%n%s%n%n", resMes);
            return true;
        }
        return false;
    }

    protected static void serverMainer(String[] args, final HelloServer server) {
        if (args == null || args.length != 2) {
            error("Invalid count of arguments: must be two args");
            return;
        }

        int port;
        int threads;
        try {
            port = Integer.parseInt(args[0]);
            threads = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            error("Invalid arguments: args must be integer");
            return;
        }

        try (server) {
            server.start(port, threads);
        } catch (Exception e) {
            error("some wrong: " + e.getMessage());
        }
    }

    protected static void clientMainer(String[] args, final HelloClient client) {
        if (args == null || args.length != 5) {
            error("Invalid count of arguments: must be five args");
            return;
        }

        final int port;
        final int threads;
        final int requests;
        try {
            port = Integer.parseInt(args[1]);
            threads = Integer.parseInt(args[3]);
            requests = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            error("Invalid arguments: second, forth and fifth args must be integer");
            return;
        }

        try {
            client.run(args[0], port, args[2], threads, requests);
        } catch (Exception e) {
            error(e.getMessage());
        }
    }
}

package info.kgeorgiy.ja.televnoi.walk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static java.lang.System.err;

public class Walk {

    private static int jenkins(byte [] arr, int l, int hash) {
        for (int i = 0; i < l; i++) {
            hash += (arr[i] & 0xFF);
            hash += (hash << 10);
            hash ^= (hash >>> 6);
        }
        return hash;
    }

    private static void error(String str) {
        err.println(str);
    }

    public static void main(String[] args) {
        if (args == null || args.length != 2 || args[1] == null || args[0] == null) {
            error("Invalid args");
            return;
        }

        Path in;
        Path out;
        try {
            in = Paths.get(args[0]);
            out = Paths.get(args[1]);
            try {
                if (out.getParent() != null) {
                    Files.createDirectories(out.getParent());
                }
            } catch (IOException | SecurityException e) {
                error("Error create directories: " + e.getMessage());
                return;
            }
        } catch (InvalidPathException e) {
            error("Invalid path to file: " + e.getMessage());
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(in, StandardCharsets.UTF_8)) {
            try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                String fileName;
                final int i = 1024;
                final byte [] arr = new byte[i];
                int len;
                try {
                    while ((fileName = reader.readLine()) != null) {
                        int hash = 0;
                        try (InputStream file = Files.newInputStream(Paths.get(fileName))) {
                            while ((len = file.read(arr)) != -1) {
                                hash = jenkins(arr, len, hash);
                            }
                            hash += (hash << 3);
                            hash ^= (hash >>> 11);
                            hash += (hash << 15);
                        } catch (IOException | InvalidPathException | SecurityException e) {
                            hash = 0;
                        }

                        try {
                            writer.write(String.format("%08x", hash) + ' ' + fileName);
                            writer.newLine();
                        } catch (IOException e) {
                            error("Error writing file: " + e.getMessage());
                            return;
                        }
                    }
                } catch (IOException e) {
                    error("Error reading input file: " + e.getMessage());
                }
            } catch (SecurityException e) {
                error("Error access to output file");
            } catch (IOException e) {
                error("Error writing output file: " + e.getMessage());
            }
        } catch (SecurityException e) {
            error("Error access to file. " + e.getMessage());
        } catch (IOException e) {
            error("Error open file: " + e.getMessage());
        }
    }
}

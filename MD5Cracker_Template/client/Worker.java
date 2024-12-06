package client;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class Worker {

    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java Worker workerIPAddress");
            System.exit(0);
        }

        String workerAddress = args[0];
        DatagramSocket socket = new DatagramSocket(6001, InetAddress.getByName(workerAddress));

        System.out.println("Partito");

        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            try {
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength()).trim();

                String[] parts = message.split(",");
                byte[] problemHash = Base64.getDecoder().decode(parts[0]);
                int start = Integer.parseInt(parts[1].trim());
                int end = Integer.parseInt(parts[2].trim());

                ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
                String solution = processTaskInParallel(problemHash, start, end, executor);

                executor.shutdown();
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

                sendTaskResult(solution, packet.getAddress(), socket, packet.getPort());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String processTaskInParallel(byte[] problemHash, int start, int end, ExecutorService executor) {
        AtomicReference<String> solution = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);

        int chunkSize = (end - start + 1) / NUM_THREADS;
        for (int i = 0; i < NUM_THREADS; i++) {
            int chunkStart = start + i * chunkSize;
            int chunkEnd = (i == NUM_THREADS - 1) ? end : chunkStart + chunkSize - 1;

            executor.submit(() -> {
                try {
                    String result = processChunk(problemHash, chunkStart, chunkEnd);
                    if (result != null) {
                        solution.compareAndSet(null, result);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return solution.get();
    }

    private static String processChunk(byte[] problemHash, int start, int end) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");

        for (int i = start; i <= end; i++) {
            byte[] currentHash = md.digest(Integer.toString(i).getBytes("UTF-8"));
            if (Arrays.equals(currentHash, problemHash)) {
                return Integer.toString(i);
            }
        }
        return null;
    }

    private static void sendTaskResult(String solution, InetAddress clientAddress, DatagramSocket socket, int port) {
        try {
            String message = solution != null ? solution : "NO_SOLUTION";
            DatagramPacket responsePacket = new DatagramPacket(message.getBytes(), message.length(), clientAddress, port);
            socket.send(responsePacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

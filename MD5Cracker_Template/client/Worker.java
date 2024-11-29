package client;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class Worker {

    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors(); // Usa i core disponibili

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java Worker workerIPAddress");
            System.exit(0);
        }

        String workerAddress = args[0];
        DatagramSocket socket = new DatagramSocket(5000, InetAddress.getByName(workerAddress));

        System.out.println("Worker listening on " + workerAddress + ":5000");

        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            try {
                // Ricezione del task dal client
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength()).trim();

                String[] parts = message.split(",");
                if (parts.length != 3) {
                    System.err.println("Invalid message format: " + message);
                    continue;
                }

                byte[] problemHash = Base64.getDecoder().decode(parts[0]);
                int start = Integer.parseInt(parts[1].trim());
                int end = Integer.parseInt(parts[2].trim());

                System.out.println("Received task: start=" + start + ", end=" + end);

                // Creare un nuovo executor per ogni task
                ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

                // Elaborazione parallela del task
                String solution = processTaskInParallel(problemHash, start, end, executor);

                if (solution != null) {
                    sendSolutionToClient(solution, packet.getAddress());
                    System.out.println("Solution found: " + solution);
                } else {
                    System.out.println("Task completed with no solution found.");
                }

                // Chiudi correttamente l'executor
                executor.shutdown();
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String processTaskInParallel(byte[] problemHash, int start, int end, ExecutorService executor) {
        AtomicReference<String> solution = new AtomicReference<>(null);

        // Suddividi l'intervallo in sottoblocchi
        int chunkSize = (end - start + 1) / NUM_THREADS;
        for (int i = 0; i < NUM_THREADS; i++) {
            int chunkStart = start + i * chunkSize;
            int chunkEnd = (i == NUM_THREADS - 1) ? end : chunkStart + chunkSize - 1;

            executor.submit(() -> {
                try {
                    String result = processChunk(problemHash, chunkStart, chunkEnd);
                    if (result != null) {
                        solution.compareAndSet(null, result); // Salva la soluzione se non già presente
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Ritorna la soluzione se trovata
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

    private static void sendSolutionToClient(String solution, InetAddress clientAddress) {
        try (Socket clientSocket = new Socket(clientAddress, 5001);
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            writer.println(solution);
        } catch (IOException e) {
            System.err.println("Failed to send solution to client.");
            e.printStackTrace();
        }
    }
}
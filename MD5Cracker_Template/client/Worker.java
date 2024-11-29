package client;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public class Worker {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java Worker workerIPAddress");
            System.exit(0);
        }

        String workerAddress = args[0];
        DatagramSocket socket = new DatagramSocket(5000, InetAddress.getByName(workerAddress));

        System.out.println("Worker listening on " + workerAddress + ":5000");

        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            try {
                // Ricezione del task dal client
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength()).trim();
                System.out.println("Received raw message: " + message); // Debug

                String[] parts = message.split(",");

                // Validazione del formato dei dati
                if (parts.length != 3) {
                    System.err.println("Invalid message format: " + message);
                    continue; // Ignora i messaggi malformati
                }

                try {
                    // Parsing dell'hash
                    byte[] problemHash = Base64.getDecoder().decode(parts[0]);

                    // Parsing dei valori start e end
                    int start = Integer.parseInt(parts[1].trim());
                    int end = Integer.parseInt(parts[2].trim());

                    System.out.println("Parsed values: start=" + start + ", end=" + end);

                    // Elaborazione del task
                    String solution = processTask(problemHash, start, end);

                    if (solution != null) {
                        sendSolutionToClient(solution, packet.getAddress());
                        System.out.println("Solution found and sent to client: " + solution);
                    } else {
                        System.out.println("Task completed with no solution found.");
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing start or end values: " + Arrays.toString(parts));
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    System.err.println("Error parsing hash: " + parts[0]);
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String processTask(byte[] problemHash, int start, int end) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");

        for (int i = start; i <= end; i++) {
            byte[] currentHash = md.digest(Integer.toString(i).getBytes());
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
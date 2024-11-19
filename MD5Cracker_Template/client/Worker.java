package client;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class Worker {

    private static final AtomicBoolean solutionFound = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(5000);
        System.out.println("Worker ready to receive tasks...");

        byte[] buffer = new byte[1024];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String taskData = new String(packet.getData(), 0, packet.getLength());

            String[] task = taskData.split(",");
            byte[] targetHash = parseHash(task[0]);
            int start = Integer.parseInt(task[1].trim());
            int end = Integer.parseInt(task[2].trim());

            System.out.println("Received task: Start=" + start + ", End=" + end);

            String solution = bruteForce(targetHash, start, end);
            if (solution != null) {
                sendSolutionToCoordinator(solution);
                break;
            }

            // Notifica il client che il task è completato
            notifyTaskCompleted(packet.getAddress(), targetHash);
        }
    }

    private static byte[] parseHash(String hashString) {
        String[] byteStrings = hashString.replaceAll("[\\[\\]]", "").trim().split(",");
        byte[] hash = new byte[byteStrings.length];
        for (int i = 0; i < byteStrings.length; i++) {
            hash[i] = Byte.parseByte(byteStrings[i].trim());
        }
        return hash;
    }

    private static String bruteForce(byte[] targetHash, int start, int end) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (int i = start; i <= end && !solutionFound.get(); i++) {
                String candidate = Integer.toString(i);
                byte[] hash = md.digest(candidate.getBytes());
                if (Arrays.equals(hash, targetHash)) {
                    solutionFound.set(true);
                    return candidate;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void sendSolutionToCoordinator(String solution) {
        try (Socket socket = new Socket("coordinator-ip", 5001);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            writer.println(solution);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void notifyTaskCompleted(InetAddress coordinatorAddress, byte[] problem) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = "TASK_COMPLETED";
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, coordinatorAddress, 5000);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

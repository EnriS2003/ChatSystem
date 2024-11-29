package client;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class Worker {

    private static final AtomicBoolean solutionFound = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        // Worker listens for tasks on a specific port (e.g., 5000)
        DatagramSocket socket = new DatagramSocket(5000);
        System.out.println("Worker ready to receive tasks...");

        byte[] buffer = new byte[1024]; // Buffer for receiving data

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet); // Receive the task from the coordinator

            String taskData = new String(packet.getData(), 0, packet.getLength());
 // Handle case where no task is sent
            if ("NO_PROBLEM".equals(taskData)) {
                System.out.println("No problem received. Waiting for a new task...");
                continue;
            }

            // Parse the task data
            String[] taskParts = taskData.split(",");
            byte[] targetHash = parseHash(taskParts[0]);
            int start = Integer.parseInt(taskParts[1].trim());
            int end = Integer.parseInt(taskParts[2].trim());

            System.out.println("Received task: Start=" + start + ", End=" + end);

            // Process the chunk and find a solution if possible
            String solution = bruteForce(targetHash, start, end);
            if (solution != null) {
                sendSolutionToCoordinator(solution, packet.getAddress());
                break; // Stop if a solution is found
            }

            // Notify the coordinator that the task is completed without a solution
            notifyTaskCompleted(packet.getAddress());
        }
    }

/**
     * Parses a hash string into a byte array.
     */
    private static byte[] parseHash(String hashString) {
        String[] byteStrings = hashString.replaceAll("[\\[\\]]", "").trim().split(",");
        byte[] hash = new byte[byteStrings.length];
        for (int i = 0; i < byteStrings.length; i++) {
            hash[i] = Byte.parseByte(byteStrings[i].trim());
        }
        return hash;
    }

    /**
     * Performs a brute force search for the solution in the given range.
     */
    private static String bruteForce(byte[] targetHash, int start, int end) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (int i = start; i <= end && !solutionFound.get(); i++) {
                String candidate = Integer.toString(i);
                byte[] hash = md.digest(candidate.getBytes());
                if (Arrays.equals(hash, targetHash)) {
                    solutionFound.set(true);
                    return candidate; // Solution found
 }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // No solution found
    }

    /**
     * Sends the solution back to the coordinator.
     */
    private static void sendSolutionToCoordinator(String solution, InetAddress coordinatorAddress) {
        try (Socket socket = new Socket(coordinatorAddress.getHostAddress(), 5001);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            writer.println(solution); // Send the solution
            System.out.println("Solution sent to coordinator: " + solution);
    System.out.println("Solution sent to coordinator: " + solution);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Notifies the coordinator that the task was completed without finding a solution.
     */
    private static void notifyTaskCompleted(InetAddress coordinatorAddress) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = "TASK_COMPLETED";
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, coordinatorAddress, 5000);
            socket.send(packet); // Notify the coordinator
            System.out.println("Notified coordinator about task completion without solution.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
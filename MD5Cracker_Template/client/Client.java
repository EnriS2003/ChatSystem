package client;

import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import server.ServerCommInterface;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class Client {

    private static final AtomicBoolean solutionFound = new AtomicBoolean(false);
    private static final Queue<int[]> taskQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger remainingTasks = new AtomicInteger();
    private static ServerSocket serverSocket;
    public static int problemSize;

    public static int getProblemSize() {
        return problemSize;
    }

    public static void setProblemSize(int size) {
        problemSize = size;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: java Client serverAddress yourOwnIPAddress teamName workerIPs");
            System.exit(0);
        }

        String serverAddress = args[0];
        String clientAddress = args[1];
        String teamName = args[2];
        String[] workerIPs = args[3].split(",");

        System.out.println("Coordinator started with server: " + serverAddress);
        System.setProperty("java.rmi.server.hostname", clientAddress);

        ServerCommInterface sci = (ServerCommInterface) Naming.lookup("rmi://" + serverAddress + "/server");
        ClientCommHandler cch = new ClientCommHandler();
        sci.register(teamName, cch);

        serverSocket = new ServerSocket(1099);

        while (true) {
            while (cch.currProblem == null) {
                Thread.sleep(50);
            }

            byte[] problemHash = cch.currProblem;
            setProblemSize(cch.currProblemSize);

            System.out.println("Received problem: " + Arrays.toString(problemHash) + ", size: " + problemSize);
            prepareTasks(getProblemSize());

            ExecutorService executor = Executors.newFixedThreadPool(workerIPs.length);
            for (String workerIP : workerIPs) {
                executor.execute(() -> handleWorkerTasks(workerIP, problemHash));
            }
            
            System.out.println("Prima di waitForSolution");
            String solution = waitForSolution(problemHash, sci, teamName);
            System.out.println("SOLUTION SENT TO SERVER, dopo waitForSolution");

            if (solution != null) {
                System.out.println("Solution submitted to server: " + solution);
                cch.currProblem = null;
                solutionFound.set(false);
            } else {
                System.err.println("No solution found for current problem.");
            }

            executor.shutdownNow();
        }
    }

    private static void prepareTasks(int problemSize) {
        int chunkSize = 10000;
        taskQueue.clear();
        remainingTasks.set(0);

        for (int i = 0; i < problemSize; i += chunkSize) {
            int end = Math.min(i + chunkSize - 1, problemSize - 1);
            taskQueue.add(new int[]{i, end});
            remainingTasks.incrementAndGet();
        }

        System.out.println("Prepared tasks: " + taskQueue.size());
    }

    private static void handleWorkerTasks(String workerIP, byte[] problemHash) {
        int[] task;
        while ((task = taskQueue.poll()) != null && !solutionFound.get()) {
            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress address = InetAddress.getByName(workerIP);
                String encodedHash = Base64.getEncoder().encodeToString(problemHash);
                String message = encodedHash + "," + task[0] + "," + task[1];

                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), address, 6001);
                socket.send(packet);

                byte[] buffer = new byte[1024];
                DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                socket.setSoTimeout(5000);
                socket.receive(responsePacket);

                String response = new String(responsePacket.getData(), 0, responsePacket.getLength()).trim();
                if (response.matches("\\d+")) {
                    System.out.println("Worker found solution: " + response);
                    solutionFound.set(true);
                    System.out.println("Solution found true");
                    return;
                }
            } catch (SocketTimeoutException e) {
                taskQueue.add(task);
                remainingTasks.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
                taskQueue.add(task);
                remainingTasks.incrementAndGet();
            } finally {
                remainingTasks.decrementAndGet();
            }
        }
    }

    private static String waitForSolution(byte[] problemHash, ServerCommInterface sci, String teamName) {
        try {
            while (!solutionFound.get() && remainingTasks.get() > 0) {
                Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = reader.readLine().trim();
                reader.close();
                socket.close();
                System.out.println("QUi");
                if (solutionFound.get()) {
                    System.out.println("QUo");

                    System.out.println("QUa");
                    solutionFound.set(true);
                    sci.submitSolution(teamName, response);
                    return response;
                    
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean isValidSolution(String solution, byte[] problemHash) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(solution.getBytes(StandardCharsets.UTF_8));
            return Arrays.equals(hash, problemHash);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}

package client;

import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.util.Arrays;
import java.util.Base64;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import server.ServerCommInterface;

public class Client {

    private static final AtomicBoolean solutionFound = new AtomicBoolean(false);
    private static final Queue<int[]> taskQueue = new ConcurrentLinkedQueue<>();
    private static ServerSocket serverSocket;
    public static int problemSize;
    
        public static int getProblemSize(){
                return problemSize;
        }
        public static void setProblemSize(int a){
                    problemSize = a;
                return;
    
        }
    
        public static void main(String[] args) throws Exception {
            if (args.length < 4) {
                System.out.println("Usage: java Client serverAddress yourOwnIPAddress teamName workerIPs");
                System.exit(0);
            }
    
            String serverAddress = args[0];
            String clientAddress = args[1];
            String teamName = args[2];
            String[] workerIPs = args[3].split(","); // Lista degli indirizzi IP dei worker
    
            System.setProperty("java.rmi.server.hostname", clientAddress);
    
            // Connessione al server RMI
            ServerCommInterface sci = (ServerCommInterface) Naming.lookup("rmi://" + serverAddress + "/server");
            ClientCommHandler cch = new ClientCommHandler();
            sci.register(teamName, cch);
    
            serverSocket = new ServerSocket(5001);
    
            while (true) {
                // Aspetta che venga pubblicato un nuovo problema
                while (cch.currProblem == null) {
                    Thread.sleep(50);
                }
    
                byte[] problemHash = cch.currProblem;
                setProblemSize(cch.currProblemSize);

            System.out.println("Received problem: " + Arrays.toString(problemHash) + ", size: " + problemSize);

            // Prepara i chunk
            prepareTasks(getProblemSize());

            // Assegna i chunk ai worker
            ExecutorService executor = Executors.newFixedThreadPool(workerIPs.length);
            for (String workerIP : workerIPs) {
                executor.execute(() -> handleWorkerTasks(workerIP, problemHash));
            }

            // Attendi una soluzione dai worker
            String solution = waitForSolution(problemHash, sci, teamName);

            if (solution != null) {
                System.out.println("Solution submitted to server: " + solution);
                cch.currProblem = null; // Reset per il prossimo problema
                solutionFound.set(false); // Resetta lo stato per il prossimo problema
            }

            executor.shutdownNow();
        }
    }

    private static void prepareTasks(int problemSize) {
        int chunkSize = 10000;
        taskQueue.clear();

        for (int i = 0; i <= problemSize; i += chunkSize) {
            int end = Math.min(i + chunkSize - 1, problemSize);
            taskQueue.add(new int[]{i, end});
        }

        System.out.println("Prepared tasks: " + taskQueue.size());
    }

    private static void handleWorkerTasks(String workerIP, byte[] problemHash) {
        while (!taskQueue.isEmpty() && !solutionFound.get()) {
            int[] task = taskQueue.poll();
            if (task == null) return;
    
            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress address = InetAddress.getByName(workerIP);
                
                // Usa Base64 per codificare l'hash del problema
                String encodedHash = Base64.getEncoder().encodeToString(problemHash);
                String message = encodedHash + "," + task[0] + "," + task[1];
                
                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), address, 5000);
                socket.send(packet);
    
                System.out.println("Assigned task " + Arrays.toString(task) + " to worker at " + workerIP);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String waitForSolution(byte[] problemHash, ServerCommInterface sci, String teamName) {
        try {
            while (!solutionFound.get()) {
                Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = reader.readLine().trim();
                reader.close();
                socket.close();

                if (response != null && !"TASK_COMPLETED".equals(response)) {
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
}
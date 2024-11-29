package client;

import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import server.ServerCommInterface;

public class Client {

    private static final String[] WORKER_IPS = {"192.168.1.77"};
    private static final AtomicBoolean solutionFound = new AtomicBoolean(false);
    private static final Queue<int[]> taskQueue = new ConcurrentLinkedQueue<>();
    private static ServerSocket serverSocket;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Proper Usage is: java Client serverAddress yourOwnIPAddress teamname");
            System.exit(0);
        }

        String teamName = args[2];
        System.out.println("Client starting, listens on IP " + args[1] + " for server callback.");
        System.setProperty("java.rmi.server.hostname", args[1]);

        ServerCommInterface sci = (ServerCommInterface) Naming.lookup("rmi://" + args[0] + "/server");
        ClientCommHandler cch = new ClientCommHandler();
        System.out.println("Client registers with the server");
        sci.register(teamName, cch);

        serverSocket = new ServerSocket(5001);

        while (true) {
            // Attendi finché non arriva un nuovo problema dal server
            while (cch.currProblem == null) {
                Thread.sleep(5);
            }

            byte[] problem = cch.currProblem;
            int problemSize = cch.currProblemSize;

            // Divide il lavoro in chunk e li aggiunge alla coda
            prepareTasks(problemSize);

            // Assegna i task ai worker
            for (String ip : WORKER_IPS) {
                assignTaskToWorker(ip, problem);
            }

            // Ascolta per una soluzione
            String solution = listenForSolution(problem);

            if (solution != null) {
                System.out.println("Solution found: " + solution);
                sci.submitSolution(teamName, solution);
                cch.currProblem = null;
                solutionFound.set(true); // Interrompe ulteriori assegnazioni se la soluzione è trovata
            }
        }
    }

    /**
     * Divide il lavoro in chunk e li aggiunge alla coda dei task.
     */
    private static void prepareTasks(int problemSize) {
        int chunkSize = 1000;
        for (int i = 0; i <= problemSize; i += chunkSize) {
            int end = Math.min(i + chunkSize - 1, problemSize);
            taskQueue.add(new int[]{i, end});
        }
        System.out.println("Prepared " + taskQueue.size() + " tasks for workers.");
    }

    /**
     * Assegna un task a un worker specifico.
     */
    private static void assignTaskToWorker(String ip, byte[] problem) {
        if (taskQueue.isEmpty()) {
            System.out.println("No more tasks to assign.");
            return;
        }

        int[] range = taskQueue.poll();
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(ip);
            String message = (problem != null ? Arrays.toString(problem) : "NO_PROBLEM") + "," + range[0] + "," + range[1];
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, 5000);
            socket.send(packet);
            System.out.println("Assigned chunk " + Arrays.toString(range) + " to worker at " + ip);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Ascolta per una soluzione dai worker.
     */
    private static String listenForSolution(byte[] problem) {
        ExecutorService executor = Executors.newCachedThreadPool();

        try {
            while (!solutionFound.get() && !taskQueue.isEmpty()) {
                Callable<String> workerListener = () -> {
                    Socket socket = serverSocket.accept();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String result = reader.readLine();
                    reader.close();
                    socket.close();

                    if ("TASK_COMPLETED".equals(result)) {
                        System.out.println("Worker completed task without finding a solution.");
                        // Riassegna un nuovo task al worker
                        assignTaskToWorker(socket.getInetAddress().getHostAddress(), problem);
                        return null;
                    }

                    return result;
                };

                Future<String> workerFuture = executor.submit(workerListener);

                // Attendi il risultato dal primo worker che risponde
                String solution = workerFuture.get();
                if (solution != null && !"TASK_COMPLETED".equals(solution)) {
                    return solution;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        return null;
    }
}
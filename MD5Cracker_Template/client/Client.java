package client;

import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import server.ServerCommInterface;

public class Client {

	private static final String[] WORKER_IPS = {"192.168.1.2", "192.168.1.3", "192.168.1.4"};
	private static final AtomicBoolean solutionFound = new AtomicBoolean(false);
	private static final Queue<int[]> taskQueue = new ConcurrentLinkedQueue<>(); // Per assegnare chunk dinamici

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

		while (true) {
			while (cch.currProblem == null) {
				Thread.sleep(5);
			}

			byte[] problem = cch.currProblem;
			int problemSize = cch.currProblemSize;

			// Popola la task queue con chunk
			int chunkSize = 1000;
			for (int i = 0; i <= problemSize; i += chunkSize) {
				int end = Math.min(i + chunkSize - 1, problemSize);
				taskQueue.add(new int[]{i, end});
			}

			// Assegna i chunk iniziali ai worker
			for (String ip : WORKER_IPS) {
				assignTaskToWorker(ip, problem);
			}

			// Ascolta per la soluzione
			String solution = listenForSolution();
			if (solution != null) {
				System.out.println("Solution found: " + solution);
				sci.submitSolution(teamName, solution);
				cch.currProblem = null;
			}
		}
	}

	private static void assignTaskToWorker(String ip, byte[] problem) {
		if (taskQueue.isEmpty()) return;

		int[] range = taskQueue.poll();
		try (DatagramSocket socket = new DatagramSocket()) {
			InetAddress address = InetAddress.getByName(ip);
			String message = Arrays.toString(problem) + "," + range[0] + "," + range[1];
			byte[] buffer = message.getBytes();
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, 5000);
			socket.send(packet);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String listenForSolution() {
		try (ServerSocket serverSocket = new ServerSocket(5001)) {
			Socket socket = serverSocket.accept();
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			return reader.readLine(); // Legge la soluzione
		} catch (Exception e) {
			return null;
		}
	}
}

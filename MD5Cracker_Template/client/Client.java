package client;

import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import server.ServerCommInterface;

public class Client {

	private static final String[] WORKER_IPS = {"192.168.1.2", "192.168.1.3", "192.168.1.4"};
	private static final AtomicBoolean solutionFound = new AtomicBoolean(false);
	private static final Queue<int[]> taskQueue = new ConcurrentLinkedQueue<>();

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

			// Suddividi il lavoro in chunk
			int chunkSize = 1000;
			for (int i = 0; i <= problemSize; i += chunkSize) {
				int end = Math.min(i + chunkSize - 1, problemSize);
				taskQueue.add(new int[]{i, end});
			}

			// Il client prende il primo chunk
			int[] clientChunk = taskQueue.poll();
			System.out.println("Client processing chunk: " + Arrays.toString(clientChunk));
			Future<String> clientTask = processClientChunk(problem, clientChunk[0], clientChunk[1]);

			// Assegna i chunk ai worker
			for (String ip : WORKER_IPS) {
				assignTaskToWorker(ip, problem);
			}

			// Ascolta per la soluzione
			String solution = listenForSolution(clientTask);
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

	private static Future<String> processClientChunk(byte[] problem, int start, int end) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		return executor.submit(() -> bruteForce(problem, start, end));
	}

	private static String listenForSolution(Future<String> clientTask) {
		ExecutorService executor = Executors.newSingleThreadExecutor();

		try (ServerSocket serverSocket = new ServerSocket(5001)) {
			Callable<String> workerListener = () -> {
				Socket socket = serverSocket.accept();
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				return reader.readLine();
			};

			Future<String> workerFuture = executor.submit(workerListener);

			// Aspetta il risultato del client o del worker, qualunque arrivi per primo
			return CompletableFuture.anyOf(
					CompletableFuture.supplyAsync(() -> {
						try {
							return clientTask.get();
						} catch (Exception e) {
							return null;
						}
					}),
					CompletableFuture.supplyAsync(() -> {
						try {
							return workerFuture.get();
						} catch (Exception e) {
							return null;
						}
					})
			).join().toString();

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
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
}

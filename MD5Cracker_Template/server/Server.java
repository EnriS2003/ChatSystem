package server;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.util.HashMap;

public class Server {

	// Determines how hard the problems are, the larger this number, the longer cracking will take
	// 5 million makes 1 RPi calculate about 10 seconds per problem
	// 5000000
	public static final int MACHINES = 8;
	public static final int BASESIZE = 5000000;
	public static final int PROBLEMSIZE = MACHINES*BASESIZE;
	public static final int WAITSTART = 30000;


	public static void main(String[] args) throws Exception {	
		
		if (args.length<1)
			System.out.println("Correct use is java Server own-IP");
		
		// How many problems will be generated
		int RUNS = 20;// Integer.parseInt(args[0]);
		System.setProperty("java.rmi.server.hostname", args[0]);
		
		LocateRegistry.createRegistry(1099);
		//System.setSecurityManager(new SecurityManager());
		HashMap<String,Integer> scoreMap = new HashMap<String,Integer>();
		ServerCommHandler sc = new ServerCommHandler(scoreMap);
		Naming.rebind("rmi://" + args[0] + "/server", sc);
		
		// Sleep for 5 seconds to give clients time to register before giving the first task
		// But it is also possible to register later..
		Thread.sleep(WAITSTART);
		System.out.println("Server starts...");
		
		while (true) {
			System.out.println("Starting new round");
		
			// Now create problems and wait until they get solved
			for (int r=1; r<=RUNS; r++) {
				System.out.println("\n  Creating Problem " + r);
				sc.createAndPublishProblem();
				// wait until problem is solved..
				while (!sc.isSolved()) { Thread.sleep(5);}
			}
			sc.currentHash = null;
			System.out.println("\nTasks finished");
			
			// Print the results
			printScores(scoreMap);
			resetScores(scoreMap);
			Thread.sleep(5000);
		}
	}

	// To print the final scores
	private static void printScores(HashMap<String, Integer> scoreMap) {
		System.out.println("\nFinal score:");
		for (String s : scoreMap.keySet())
			System.out.println("  Team: " + s + " - score: " + scoreMap.get(s));
		
	}

	private static void resetScores(HashMap<String, Integer> scoreMap) {
		for (String s : scoreMap.keySet())
			scoreMap.put(s,0);
		
	}
	
}

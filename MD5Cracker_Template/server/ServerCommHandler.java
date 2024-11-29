package server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import client.ClientCommInterface;

public class ServerCommHandler extends UnicastRemoteObject implements ServerCommInterface {

	private static final long serialVersionUID = -753493834709721813L;
	HashMap<String, Integer> myScoreMap;
	private Set<ClientCommInterface> myClients;
	String currentSolution = null;
	byte[] currentHash = null;
	boolean isSolved = false;
	MessageDigest md = null;
	HashSet<String> pastProblems;
	
	// Constructor
	public ServerCommHandler(HashMap<String, Integer> sm) throws RemoteException, NoSuchAlgorithmException {
		myScoreMap = sm;
		myClients = new HashSet<ClientCommInterface>();
		md = MessageDigest.getInstance("MD5");
		pastProblems = new HashSet<String>();
	}

	@Override
	// What do to if a client registers
	public synchronized void register(String name, ClientCommInterface cc) throws Exception {
		System.out.println("Registering team " + name);
		if (!myScoreMap.containsKey(name))
			myScoreMap.put(name,0);
		myClients.add(cc);
		if (currentHash!=null) {
			System.out.println("Trying to give problem to client");
			cc.publishProblem(currentHash, Server.PROBLEMSIZE);
		}
	}

	public synchronized void createAndPublishProblem() throws Exception {
		// Create problem
		isSolved = false;
		currentSolution = generateRandomString(Server.PROBLEMSIZE);
		currentHash = md.digest(currentSolution.getBytes());
		HashSet<ClientCommInterface> toRemove = new HashSet<ClientCommInterface>();
		for (ClientCommInterface cc : myClients) {
			try {cc.publishProblem(currentHash, Server.PROBLEMSIZE);}
			catch (Exception e) {
				System.out.println("Could not print to some client");
				toRemove.add(cc);
			}
		}
		myClients.removeAll(toRemove);
		// System.out.println("  Problem " + currentSolution + " created and published to " + myClients.size() + " teams.");
		System.out.println("Generated solution: "+currentSolution);
		System.out.println("Hash: "+ Base64.getEncoder().encodeToString(currentHash));
	}

	private String generateRandomString(int max) {
		Integer random = (int)Math.round(Math.random()*max);
		return random.toString();
	}

	@Override
	public synchronized void submitSolution(String name, String sol) {
		byte[] solHash = md.digest(sol.getBytes());
		if (Arrays.equals(solHash, currentHash)) {
			isSolved = true;
			currentHash = null; // So that one cannot submit this solution a second time and get points again
			myScoreMap.put(name, myScoreMap.get(name)+1) ;
			System.out.println("  Team " + name + " solved the problem correctly");
			pastProblems.add(sol);
		}
		else {
			if (pastProblems.contains(sol)) {
				System.out.println("  Team " + name + " submitted a correct solution too late");
			}
			else {
				myScoreMap.put(name, myScoreMap.get(name)-1) ;
				System.out.println("  Team " + name + " submitted an incorrect solution");
			}
		}
		
	}

	public boolean isSolved() {
		return isSolved;
	}

}

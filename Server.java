import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;

public class Server extends UnicastRemoteObject implements ChatServer {
    private static final Set<String> availableClients = ConcurrentHashMap.newKeySet();
    private static final Queue<String> waitingClients = new LinkedList<>();
    private static final Map<String, ClientCallback> clientMap = new ConcurrentHashMap<>();
    private static final Map<String, String> partnerMap = new ConcurrentHashMap<>();

    protected Server() throws RemoteException {
        super();
    }

    @Override
    public synchronized void registerClient(String username, ClientCallback client) throws RemoteException {
        availableClients.add(username);
        clientMap.put(username, client);
        System.out.println(username + " registered.");
        assignPartner(username);
    }

    @Override
    public synchronized void sendMessage(String username, String message) throws RemoteException {
        String partner = partnerMap.get(username);
        if (partner != null) {
            clientMap.get(partner).receiveMessage(username + ": " + message);
        } else {
            clientMap.get(username).receiveMessage("Wait for being assigned to a partner.");
        }
    }

    @Override
    public synchronized void disconnect(String username) throws RemoteException {
        availableClients.remove(username);
        waitingClients.remove(username);
        ClientCallback client = clientMap.get(username);

        if (client != null) {
            String partner = partnerMap.remove(username);
            if (partner != null) {
                partnerMap.remove(partner);
                clientMap.get(partner).receiveMessage("Your partner left the chat.");

                // Remove the partner from waitingClients to avoid duplication
                waitingClients.remove(partner);

                // Reassign the partner immediately to ensure they stay connected
                assignPartner(partner);
            }

            clientMap.remove(username);
            System.out.println(username + " disconnected.");
        }
    }


    @Override
    public synchronized void shufflePartner(String username) throws RemoteException {
        String partner = partnerMap.get(username);
        if (partner != null) {
            clientMap.get(partner).receiveMessage("Your partner left the chat to look for a new one.");
            partnerMap.remove(username);
            partnerMap.remove(partner);
            waitingClients.offer(partner);
        }
        assignPartner(username);
    }

    private synchronized void assignPartner(String username) throws RemoteException {
        if (!waitingClients.isEmpty()) {
            String partner = waitingClients.poll();
            if (!partner.equals(username)) {
                partnerMap.put(username, partner);
                partnerMap.put(partner, username);
                clientMap.get(username).receiveMessage("You are now connected with " + partner + ". Enjoy the chat!");
                clientMap.get(partner).receiveMessage("You are now connected with " + username + ". Enjoy the chat!");
            } else {
                waitingClients.offer(username);
                clientMap.get(username).receiveMessage("No other available users, wait for someone else to connect!");
            }
        } else {
            waitingClients.offer(username);
            clientMap.get(username).receiveMessage("No other available users, wait for someone else to connect!");
        }
    }

    public static void main(String[] args) {
        try {
            LocateRegistry.createRegistry(1099); // Create RMI registry on default port 1099
            ChatServer server = new Server();
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind("ChatServer", server);
            System.out.println("Chat Server is running...");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}

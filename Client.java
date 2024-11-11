import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

/**
 * The client class connects to the RMI server and interacts using the RMI methods.
 * It listens for incoming messages and sends user input to the server.
 */
public class Client extends UnicastRemoteObject implements ClientCallback {
    private static final long serialVersionUID = 1L;
    private final String username;

    protected Client(String username) throws RemoteException {
        super();
        this.username = username;
    }

    @Override
    public void receiveMessage(String message) throws RemoteException {
        System.out.println(message);
    }

    public static void main(String[] args) {
        try {
            // Connect to the RMI registry on the server
            Registry registry = LocateRegistry.getRegistry("localhost", 1099); // Replace "localhost" with the server IP
            ChatServer server = (ChatServer) registry.lookup("ChatServer");

            Scanner scanner = new Scanner(System.in);
            System.out.print("Insert your username: ");
            String username = scanner.nextLine();

            // Create and export the client object
            Client client = new Client(username);
            server.registerClient(username, client);

            // Thread to read user input and send to the server
            new Thread(() -> {
                while (true) {
                    try {
                        String userMessage = scanner.nextLine();
                        if (userMessage.equalsIgnoreCase("/quit")) {
                            server.disconnect(username);
                            System.out.println("You have disconnected from the chat.");
                            break;
                        } else if (userMessage.equalsIgnoreCase("/shuffle")) {
                            server.shufflePartner(username);
                        } else {
                            server.sendMessage(username, userMessage);
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                        System.out.println("Error communicating with the server. Please try again.");
                        break;
                    }
                }
            }).start();

        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}

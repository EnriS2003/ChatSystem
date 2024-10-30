import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = 12345;
    private static final Set<ClientHandler> availableClients = ConcurrentHashMap.newKeySet(); // List of all clients available into the Chat System
    protected static Queue<ClientHandler> waitingClients = new LinkedList<>(); // List of all clients pending for a match to chat

    public static void main(String[] args) {
        // Start the UDP broadcasting thread
        new Thread(() -> {
            try (DatagramSocket udpSocket = new DatagramSocket()) {
                udpSocket.setBroadcast(true);  // Enable the broadcast

                while (true) {
                    String broadcastMessage = "The server IP is:" + InetAddress.getLocalHost().getHostAddress();
                    byte[] buffer = broadcastMessage.getBytes();

                    // Broadcasting to port 9876 in a subnet of the UniBZ's network
                    // NOTE: the host value is to be computed again if the network you are connected to changes!
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                            InetAddress.getByName("10.12.159.255"), 9876);

                    udpSocket.send(packet);
                    System.out.println("Broadcasting server address -> " + broadcastMessage);
                    Thread.sleep(5000); // Broadcast every 5 seconds
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();


        // Start listening for TCP connections
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) { // 0.0.0.0 mean all network interfaces, no limitations
            System.out.println("Server running on port: " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                availableClients.add(clientHandler); // Add all the new connections to the list of available clients
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Assign a chat partner to a client
    static synchronized void assignPartner(ClientHandler client) {
        // If two client are waiting to be matched, they are matched immediately
        if (!waitingClients.isEmpty()) {
            ClientHandler partner = waitingClients.poll();  // Get a client which was waiting
            if (partner != client) {
                // Set the partener attribute of each of the two clients -> A chats with B and B with A
                client.setPartner(partner);
                partner.setPartner(client);

                // User notification messages
                client.sendMessage("You are now connected with " + partner.getUsername() + ". Enjoy the chat!");
                partner.sendMessage("You are now connected with " + client.getUsername() + ". Enjoy the chat!");
            } else {
                waitingClients.offer(client);  // If no matchable clients are in waiting list
                client.sendMessage("No other available users, wait for someone else to connect!");
            }
        } else {
            waitingClients.offer(client); // If no matchable clients are in waiting list
            client.sendMessage("No other available users, wait for someone else to connect!");
        }
    }

    // Remove a client from the waiting and available clients list -> disconnection from the entire system
    static synchronized void removeClient(ClientHandler client) {
        availableClients.remove(client);
        waitingClients.remove(client);
        ClientHandler partner = client.getPartner();
        if (partner != null) {
            partner.sendMessage("Your partner left the chat.");
            partner.setPartner(null);
            waitingClients.offer(partner);  // Make the partner available for new matches
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private ClientHandler partner;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void setPartner(ClientHandler partner) {
        this.partner = partner;
    }

    public ClientHandler getPartner() {
        return partner;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        try {
            // Setup I/O streams
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Fetch the username
            username = in.readLine();
            System.out.println(username + " is now connected.");

            // Try to match with a partner
            Server.assignPartner(this);

            // Read client's messages
            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("/quit")) {
                    out.println("Disconnecting... Hope to see you soon!");
                    break;
                } else if (message.equalsIgnoreCase("/shuffle")) {
                    shufflePartner();
                } else if (partner != null) {
                    partner.sendMessage(username + ": " + message);
                } else {
                    out.println("Wait for being assigned to a partner.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            out.println("You have been successfully disconnected, see you soon!");
        } finally {
            disconnect();
        }
    }

    // Send a message to the current client
    public void sendMessage(String message) {
        out.println(message);
    }

    // Method to change partner
    private synchronized void shufflePartner() {
        if (partner != null) {
            partner.sendMessage("Your partner left the chat to look for a new one.");
            partner.setPartner(null);
            Server.waitingClients.offer(partner);
        }
        out.println("Changing partner...");
        Server.assignPartner(this);  // Assign a new partner
    }

    // Method to handle the client's disconnection
    private void disconnect() {
        try {
            if (partner != null) {
                partner.sendMessage("Your partner quitted.");
                partner.setPartner(null);
                Server.waitingClients.offer(partner);
            }
            Server.removeClient(this);  // Remove the client from the list of available ones
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            e.printStackTrace();
            out.println("You have been successfully disconnected, see you soon!");
        }
        System.out.println(username + " left.");
    }

}



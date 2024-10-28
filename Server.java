import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = 12345; // TCP Port for client connections
    private static final Set<ClientHandler> availableClients = ConcurrentHashMap.newKeySet();
    protected static Queue<ClientHandler> waitingClients = new LinkedList<>();

    public static void main(String[] args) {
        // Start the UDP broadcasting thread
        new Thread(() -> {
            try (DatagramSocket udpSocket = new DatagramSocket()) {
                udpSocket.setBroadcast(true);  // Enable broadcasting

                while (true) {
                    String broadcastMessage = "Server IP:" + InetAddress.getLocalHost().getHostAddress();
                    byte[] buffer = broadcastMessage.getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                            InetAddress.getByName("255.255.255.255"), 9876); // Broadcasting to port 12346

                    udpSocket.send(packet);
                    System.out.println("Broadcasting server address: " + broadcastMessage);
                    Thread.sleep(5000); // Broadcast every 5 seconds
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        // Start listening for TCP connections
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            System.out.println("Server started on port: " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                availableClients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    // Metodo per assegnare un partner di conversazione a un client
    static synchronized void assignPartner(ClientHandler client) {
        // Se ci sono altri client in attesa, i due client vanno accoppiati per la conversazione
        if (!waitingClients.isEmpty()) {
            ClientHandler partner = waitingClients.poll();  // Ottiene un client in attesa
            if (partner != client) {
                // Imposta i partner in maniera reciproca -> A parla con B e B con A
                client.setPartner(partner);
                partner.setPartner(client);

                // Messaggi di notifica per gli utenti
                client.sendMessage("Sei stato connesso con " + partner.getUsername() + ". Puoi iniziare a chattare.");
                partner.sendMessage("Sei stato connesso con " + client.getUsername() + ". Puoi iniziare a chattare.");
            } else {
                waitingClients.offer(client);  // Il client viene rimesso in lista se è lo stesso
                client.sendMessage("Nessun altro utente disponibile. Attendi che qualcuno si connetta.");
            }
        } else {
            waitingClients.offer(client);// Se nessun altro è in attesa, aggiungi il client alla lista di attesa
            client.sendMessage("Nessun altro utente disponibile. Attendi che qualcuno si connetta.");
        }
    }

    // Rimuove un client dalla lista dei disponibili e dalla lista di attesa -> disconnessione totale
    static synchronized void removeClient(ClientHandler client) {
        availableClients.remove(client);
        waitingClients.remove(client);
        ClientHandler partner = client.getPartner();
        if (partner != null) {
            partner.sendMessage("Il tuo partner si è disconnesso.");
            partner.setPartner(null);
            waitingClients.offer(partner);  // Rende il partner disponibile
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
            // Setup degli stream di input e output
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Chiedi il nome utente
            username = in.readLine();
            System.out.println(username + " si è connesso.");

            // Aggiungi alla coda di attesa per essere assegnato a un partner
            Server.assignPartner(this);

            // Ciclo di lettura dei messaggi dal client
            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("/quit")) {
                    //out.println("\n");
                    out.println("Disconnessione... Speriamo di rivederti presto!");
                    break;
                } else if (message.equalsIgnoreCase("/shuffle")) {
                    shufflePartner();
                } else if (partner != null) {
                    partner.sendMessage(username + ": " + message);
                } else {
                    out.println("Attendi che venga assegnato un partner.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            out.println("Sei stato disconnesso con successo, a presto!");
        } finally {
            disconnect();
        }
    }

    // Metodo per inviare un messaggio al client corrente
    public void sendMessage(String message) {
        out.println(message);
    }

    // Metodo per cambiare partner di chat
    private synchronized void shufflePartner() {
        if (partner != null) {
            partner.sendMessage("Il tuo partner si è disconnesso per cercare un nuovo partner.");
            partner.setPartner(null);
            Server.waitingClients.offer(partner);
        }
        out.println("Cambio partner in corso...");
        Server.assignPartner(this);  // Riassegna un nuovo partner
    }

    // Metodo per gestire la disconnessione del client
    private void disconnect() {
        try {
            if (partner != null) {
                partner.sendMessage("Il tuo partner si è disconnesso.");
                partner.setPartner(null);
                Server.waitingClients.offer(partner);
            }
            Server.removeClient(this);  // Rimuove il client dalla lista dei disponibili
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            //e.printStackTrace();
            out.println("Sei stato disconnesso con successo, a presto!");
        }
        System.out.println(username + " si è disconnesso.");
    }


}



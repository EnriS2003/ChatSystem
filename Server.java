import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = 12345;
    private static Set<ClientHandler> clientSet = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server avviato sulla porta: " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuovo client connesso");

                // Creazione di un ClientHandler per ogni client connesso
                ClientHandler clientHandler = new ClientHandler(clientSocket, clientSet);
                clientSet.add(clientHandler);

                // Avvia un thread separato per ogni client
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Metodo per fare broadcast dei messaggi a tutti i client
    static void send_broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clientSet) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private static Set<ClientHandler> clientSet;

    public ClientHandler(Socket socket, Set<ClientHandler> clients) {
        this.socket = socket;
        clientSet = clients;
    }

    @Override
    public void run() {
        try {
            // Setup degli stream di input e output
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Chiedi il nome utente
            out.println("Inserisci il tuo nome utente:");
            username = in.readLine();
            System.out.println(username + " si è connesso.");

            // Broadcast di notifica per i nuovi utenti
            Server.send_broadcast(username + " è entrato nella chat.", this);

            // Ciclo di lettura dei messaggi dal client
            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("/quit")) {
                    out.println("Disconnessione...");
                    break;
                } else if (message.equalsIgnoreCase("/shuffle")) {
                    // Logica di shuffle per riassegnare partner di chat, se implementata
                    out.println("Cambio partner di chat...");
                    // Logica di riassegnazione qui
                } else {
                    Server.send_broadcast(username + ": " + message, this);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            disconnect();
        }
    }

    // Metodo per inviare un messaggio al client corrente
    public void sendMessage(String message) {
        out.println(message);
    }

    // Metodo per fare broadcast dei messaggi a tutti i client connessi
    private void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clientSet) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    // Metodo per gestire la disconnessione del client
    private void disconnect() {
        try {
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            clientSet.remove(this);
            broadcast(username + " si è disconnesso.", this);
            System.out.println(username + " si è disconnesso.");
        }
    }
}

import java.io.*;
import java.net.*;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Inserisci il tuo nome utente: ");
            String username = userIn.readLine();
            out.println(username);

            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    //e.printStackTrace();
                }
            }).start();

            String userMessage;
            while ((userMessage = userIn.readLine()) != null) {
                out.println(userMessage);
                if (userMessage.equalsIgnoreCase("/quit")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

import java.io.*;
import java.net.*;


/**
 * The client class takes care about connections to the server.
 * The client is provided wiht the server's IP address to establish a connection.
 **/
public class Client {
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        String serverAddress = listenForServerBroadcast();

        if (serverAddress == null) {
            System.out.println("No server found. Please, make sure the server is running!");
            return;
        }

        try (Socket socket = new Socket(serverAddress, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Insert your username: ");
            String username = userIn.readLine();
            out.println(username);

            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
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

    private static String listenForServerBroadcast() {
        try (DatagramSocket udpSocket = new DatagramSocket(9876)) {
            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            udpSocket.setSoTimeout(5000); // Wait for 5 seconds

            System.out.println("Listening for server broadcast...");
            udpSocket.receive(packet); // This will block until a message is received

            String receivedMessage = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received broadcast: " + receivedMessage);
            return receivedMessage.split(":")[1]; // Extract the IP address
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout: any broadcast message was received.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; // Return null if no message is received
    }

}

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ChatServer extends Remote {
    void registerClient(String username, ClientCallback client) throws RemoteException;
    void sendMessage(String username, String message) throws RemoteException;
    void disconnect(String username) throws RemoteException;
    void shufflePartner(String username) throws RemoteException;
}

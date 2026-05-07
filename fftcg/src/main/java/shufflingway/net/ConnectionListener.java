package shufflingway.net;

public interface ConnectionListener {
    void onActionReceived(GameAction action);
    void onDisconnected(String reason);
}

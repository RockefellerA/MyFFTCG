package shufflingway.menu;

import shufflingway.net.GameAction;
import shufflingway.net.GameConnection;
import shufflingway.net.HostLobbyDialog;
import shufflingway.net.JoinLobbyDialog;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * Multiplayer menu — lets P1 host or join a game over a direct TCP connection.
 * Once connected, the active {@link GameConnection} is stored and actions
 * received from the opponent are logged until full game-sync is implemented.
 */
public class MultiplayerMenu extends JMenu {

    private GameConnection activeConnection;
    private final JMenuItem disconnectItem;

    public MultiplayerMenu(JFrame owner, Runnable onConnected,
                           Runnable onDisconnected, Consumer<GameAction> onActionReceived) {
        super("Multiplayer");

        JMenuItem hostItem = new JMenuItem("Host Game…");
        JMenuItem joinItem = new JMenuItem("Join Game…");
        disconnectItem = new JMenuItem("Disconnect");
        disconnectItem.setEnabled(false);

        hostItem.addActionListener(e -> {
            HostLobbyDialog dlg = new HostLobbyDialog(owner);
            dlg.setVisible(true);
            GameConnection conn = dlg.getConnection();
            if (conn != null) activate(conn, owner, onConnected, onDisconnected, onActionReceived);
        });

        joinItem.addActionListener(e -> {
            JoinLobbyDialog dlg = new JoinLobbyDialog(owner);
            dlg.setVisible(true);
            GameConnection conn = dlg.getConnection();
            if (conn != null) activate(conn, owner, onConnected, onDisconnected, onActionReceived);
        });

        disconnectItem.addActionListener(e -> disconnect(owner, onDisconnected));

        add(hostItem);
        add(joinItem);
        addSeparator();
        add(disconnectItem);
    }

    private void activate(GameConnection conn, JFrame owner,
                          Runnable onConnected, Runnable onDisconnected,
                          Consumer<GameAction> onActionReceived) {
        if (activeConnection != null) activeConnection.close();
        activeConnection = conn;
        disconnectItem.setEnabled(true);

        conn.addListener(new shufflingway.net.ConnectionListener() {
            @Override
            public void onActionReceived(GameAction action) {
                SwingUtilities.invokeLater(() -> onActionReceived.accept(action));
            }
            @Override
            public void onDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> {
                    activeConnection = null;
                    disconnectItem.setEnabled(false);
                    if (onDisconnected != null) onDisconnected.run();
                    JOptionPane.showMessageDialog(owner,
                        "Opponent disconnected: " + reason,
                        "Disconnected", JOptionPane.WARNING_MESSAGE);
                });
            }
        });

        conn.start();
        onConnected.run();
    }

    private void disconnect(JFrame owner, Runnable onDisconnected) {
        if (activeConnection != null) {
            activeConnection.send(GameAction.of(shufflingway.net.ActionType.DISCONNECT,
                    new org.json.JSONObject().put("reason", "Player left")));
            activeConnection.close();
            activeConnection = null;
        }
        disconnectItem.setEnabled(false);
        if (onDisconnected != null) onDisconnected.run();
        JOptionPane.showMessageDialog(owner, "Disconnected.", "Multiplayer",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** Returns the active connection, or {@code null} if not connected. */
    public GameConnection getActiveConnection() { return activeConnection; }
}

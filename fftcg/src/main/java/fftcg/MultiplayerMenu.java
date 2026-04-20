package fftcg;

import fftcg.net.GameAction;
import fftcg.net.GameConnection;
import fftcg.net.HostLobbyDialog;
import fftcg.net.JoinLobbyDialog;

import javax.swing.*;

/**
 * Multiplayer menu — lets P1 host or join a game over a direct TCP connection.
 * Once connected, the active {@link GameConnection} is stored and actions
 * received from the opponent are logged until full game-sync is implemented.
 */
class MultiplayerMenu extends JMenu {

    private GameConnection activeConnection;
    private final JMenuItem disconnectItem;

    MultiplayerMenu(JFrame owner, Runnable onConnectionEstablished) {
        super("Multiplayer");

        JMenuItem hostItem = new JMenuItem("Host Game…");
        JMenuItem joinItem = new JMenuItem("Join Game…");
        disconnectItem = new JMenuItem("Disconnect");
        disconnectItem.setEnabled(false);

        hostItem.addActionListener(e -> {
            HostLobbyDialog dlg = new HostLobbyDialog(owner);
            dlg.setVisible(true);
            GameConnection conn = dlg.getConnection();
            if (conn != null) activate(conn, owner, onConnectionEstablished);
        });

        joinItem.addActionListener(e -> {
            JoinLobbyDialog dlg = new JoinLobbyDialog(owner);
            dlg.setVisible(true);
            GameConnection conn = dlg.getConnection();
            if (conn != null) activate(conn, owner, onConnectionEstablished);
        });

        disconnectItem.addActionListener(e -> disconnect(owner));

        add(hostItem);
        add(joinItem);
        addSeparator();
        add(disconnectItem);
    }

    private void activate(GameConnection conn, JFrame owner, Runnable onConnectionEstablished) {
        if (activeConnection != null) activeConnection.close();
        activeConnection = conn;
        disconnectItem.setEnabled(true);

        conn.addListener(new fftcg.net.ConnectionListener() {
            @Override
            public void onActionReceived(GameAction action) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(owner,
                        "Received: " + action.type() + "\n" + action.payload(),
                        "Opponent Action", JOptionPane.INFORMATION_MESSAGE));
            }
            @Override
            public void onDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> {
                    activeConnection = null;
                    disconnectItem.setEnabled(false);
                    JOptionPane.showMessageDialog(owner,
                        "Opponent disconnected: " + reason,
                        "Disconnected", JOptionPane.WARNING_MESSAGE);
                });
            }
        });

        conn.start();
        onConnectionEstablished.run();
    }

    private void disconnect(JFrame owner) {
        if (activeConnection != null) {
            activeConnection.send(GameAction.of(fftcg.net.ActionType.DISCONNECT,
                    new org.json.JSONObject().put("reason", "Player left")));
            activeConnection.close();
            activeConnection = null;
        }
        disconnectItem.setEnabled(false);
        JOptionPane.showMessageDialog(owner, "Disconnected.", "Multiplayer",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** Returns the active connection, or {@code null} if not connected. */
    public GameConnection getActiveConnection() { return activeConnection; }
}

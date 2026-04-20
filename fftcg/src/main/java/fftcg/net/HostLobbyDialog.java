package fftcg.net;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Modal dialog that opens a {@link ServerSocket} on the default port and waits
 * for an opponent to connect. Share your IP address and port with the opponent
 * out-of-band (chat, voice, etc.).
 *
 * On successful connection the "Start Game" button is enabled; disposing the
 * dialog with that button returns a live {@link GameConnection} from
 * {@link #getConnection()}. Cancelling returns {@code null}.
 */
public class HostLobbyDialog extends JDialog {

    static final int DEFAULT_PORT = 7777;

    private GameConnection connection;
    private ServerSocket serverSocket;

    private final JLabel statusLabel;
    private final JButton cancelBtn;
    private final JButton startBtn;

    public HostLobbyDialog(Frame owner) {
        super(owner, "Host Game", true);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));

        // Show all local IPv4 addresses so the host can tell the opponent which to use
        JPanel ipPanel = new JPanel(new GridLayout(0, 1, 0, 4));
        ipPanel.setBorder(BorderFactory.createTitledBorder("Share one of these with your opponent"));
        for (String ip : getLocalAddresses()) {
            JLabel lbl = new JLabel(ip + "  :  " + DEFAULT_PORT, SwingConstants.CENTER);
            lbl.setFont(new Font("Monospaced", Font.BOLD, 13));
            ipPanel.add(lbl);
        }
        content.add(ipPanel, BorderLayout.NORTH);

        statusLabel = new JLabel("Waiting for opponent…", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
        content.add(statusLabel, BorderLayout.CENTER);

        cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> cancel());

        startBtn = new JButton("Start Game");
        startBtn.setEnabled(false);
        startBtn.addActionListener(e -> dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.add(cancelBtn);
        btnRow.add(startBtn);
        content.add(btnRow, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setMinimumSize(new Dimension(380, 220));
        setLocationRelativeTo(owner);

        openServerSocket();
    }

    private void openServerSocket() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(DEFAULT_PORT);
                Socket client = serverSocket.accept();
                connection = new GameConnection(client);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Connected: " + connection.getRemoteAddress());
                    startBtn.setEnabled(true);
                    cancelBtn.setText("Cancel");
                });
            } catch (IOException e) {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Error: " + e.getMessage()));
                }
            } finally {
                try { if (serverSocket != null) serverSocket.close(); }
                catch (IOException ignored) {}
            }
        }, "HostLobby-accept").start();
    }

    private void cancel() {
        try { if (serverSocket != null) serverSocket.close(); }
        catch (IOException ignored) {}
        if (connection != null) { connection.close(); connection = null; }
        dispose();
    }

    /** Returns the live connection, or {@code null} if the dialog was cancelled. */
    public GameConnection getConnection() { return connection; }

    private static List<String> getLocalAddresses() {
        List<String> addrs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) addrs.add(addr.getHostAddress());
                }
            }
        } catch (SocketException ignored) {}
        if (addrs.isEmpty()) addrs.add("127.0.0.1");
        return addrs;
    }
}

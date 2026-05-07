package shufflingway.net;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.Socket;

/**
 * Modal dialog that connects to a host's IP:port.
 * On success, exposes a live {@link GameConnection} via {@link #getConnection()}.
 * Cancelling or failing returns {@code null}.
 */
public class JoinLobbyDialog extends JDialog {

    private GameConnection connection;

    private final JTextField hostField;
    private final JTextField portField;
    private final JLabel statusLabel;
    private final JButton connectBtn;

    public JoinLobbyDialog(Frame owner) {
        super(owner, "Join Game", true);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0; gc.gridy = 0; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        fields.add(new JLabel("Host IP:"), gc);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        hostField = new JTextField(16);
        fields.add(hostField, gc);

        gc.gridx = 0; gc.gridy = 1; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        fields.add(new JLabel("Port:"), gc);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        portField = new JTextField(String.valueOf(HostLobbyDialog.DEFAULT_PORT), 6);
        fields.add(portField, gc);

        content.add(fields, BorderLayout.NORTH);

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
        content.add(statusLabel, BorderLayout.CENTER);

        connectBtn = new JButton("Connect");
        connectBtn.addActionListener(e -> attemptConnect());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.add(cancelBtn);
        btnRow.add(connectBtn);
        content.add(btnRow, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setMinimumSize(new Dimension(300, 170));
        setLocationRelativeTo(owner);

        getRootPane().setDefaultButton(connectBtn);
    }

    private void attemptConnect() {
        String host = hostField.getText().trim();
        if (host.isEmpty()) { statusLabel.setText("Enter a host address."); return; }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            statusLabel.setText("Invalid port number.");
            return;
        }

        connectBtn.setEnabled(false);
        statusLabel.setText("Connecting…");

        new Thread(() -> {
            try {
                Socket socket = new Socket(host, port);
                connection = new GameConnection(socket);
                SwingUtilities.invokeLater(this::dispose);
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Failed: " + ex.getMessage());
                    connectBtn.setEnabled(true);
                });
            }
        }, "JoinLobby-connect").start();
    }

    /** Returns the live connection, or {@code null} if cancelled or failed. */
    public GameConnection getConnection() { return connection; }
}

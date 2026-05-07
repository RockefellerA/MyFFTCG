package shufflingway.net;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wraps a connected TCP socket and exchanges {@link GameAction}s as
 * newline-delimited JSON. A daemon reader thread fires listener callbacks
 * for each incoming action; all Swing updates must use
 * {@code SwingUtilities.invokeLater} in the listener.
 */
public class GameConnection {

    private final Socket socket;
    private final PrintWriter writer;
    private final BufferedReader reader;
    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();

    public GameConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.writer  = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)),
                true);
        this.reader  = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    public void addListener(ConnectionListener listener) {
        listeners.add(listener);
    }

    /** Serializes and sends an action. Thread-safe. */
    public synchronized void send(GameAction action) {
        writer.println(action.serialize());
    }

    /** Starts the background reader thread. Call once after construction. */
    public void start() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    final GameAction action = GameAction.deserialize(line);
                    for (ConnectionListener l : listeners) l.onActionReceived(action);
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    for (ConnectionListener l : listeners) l.onDisconnected(e.getMessage());
                    return;
                }
            }
            for (ConnectionListener l : listeners) l.onDisconnected("Connection closed");
        }, "GameConnection-reader");
        t.setDaemon(true);
        t.start();
    }

    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    public String getRemoteAddress() {
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }
}

package fftcg;

import scraper.DeckDatabase;
import scraper.DeckDatabase.DeckEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.net.URL;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.*;

public class DeckManager extends JDialog {

    private static final String[] BROWSER_COLUMNS = {
        "Serial", "Name", "Type", "Element", "Cost", "Power", "Rarity", "Job", "Category 1", "Category 2"
    };
    private static final String[] DECK_COLUMNS = {
        "Serial", "Name", "Type", "Element", "Cost", "Power", "Rarity", "Qty"
    };
    private static final int MAX_DECK_SIZE = 50;
    private static final int MAX_COPIES    = 3;

    // Deck list (left panel)
    private final DefaultListModel<DeckEntry> deckListModel = new DefaultListModel<>();
    private final JList<DeckEntry> deckList = new JList<>(deckListModel);

    // Card browser (top-center)
    private final DefaultTableModel browserModel;
    private TableRowSorter<DefaultTableModel> browserSorter;
    private JTable cardTable;

    // Deck contents (bottom-center)
    private final DefaultTableModel deckModel;
    private JTable deckTable;

    private final JLabel countLabel;
    private final JLabel cardImageLabel;

    private DeckDatabase db;
    private int selectedDeckId = -1;

    public DeckManager(JFrame parent) {
        super(parent, "Deck Manager", true);
        setSize(1400, 800);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(4, 4));

        browserModel = new DefaultTableModel(BROWSER_COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        deckModel = new DefaultTableModel(DECK_COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        countLabel = new JLabel("0 / " + MAX_DECK_SIZE + " cards", SwingConstants.RIGHT);
        countLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 8));

        cardImageLabel = new JLabel("Select a card to preview", SwingConstants.CENTER);
        cardImageLabel.setPreferredSize(new Dimension(220, 309));
        cardImageLabel.setBorder(BorderFactory.createEtchedBorder());

        JPanel imagePanel = new JPanel(new GridBagLayout());
        imagePanel.setPreferredSize(new Dimension(240, 0));
        imagePanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));
        imagePanel.add(cardImageLabel);

        add(buildDeckListPanel(), BorderLayout.WEST);
        add(buildCenterSplit(),   BorderLayout.CENTER);
        add(imagePanel,           BorderLayout.EAST);

        // Open DB and load data
        try {
            db = new DeckDatabase();
            loadDeckList();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error opening database:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }

        // Load card browser in the background so the dialog opens immediately
        loadBrowserCards();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (db != null) try { db.close(); } catch (SQLException ignored) {}
            }
        });
    }

    // -------------------------------------------------------------------------
    // Left panel: saved deck list
    // -------------------------------------------------------------------------

    private JPanel buildDeckListPanel() {
        deckList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deckList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DeckEntry entry = deckList.getSelectedValue();
                if (entry != null) {
                    selectedDeckId = entry.id();
                    loadDeckCards(selectedDeckId);
                }
            }
        });

        JButton newBtn    = new JButton("New");
        JButton renameBtn = new JButton("Rename");
        JButton copyBtn   = new JButton("Copy");
        JButton deleteBtn = new JButton("Delete");

        newBtn.addActionListener(e    -> onNewDeck());
        renameBtn.addActionListener(e -> onRenameDeck());
        copyBtn.addActionListener(e   -> onCopyDeck());
        deleteBtn.addActionListener(e -> onDeleteDeck());

        JButton importBtn = new JButton("Import");
        JButton exportBtn = new JButton("Export");
        importBtn.addActionListener(e -> onImportDeck());
        exportBtn.addActionListener(e -> onExportDeck());

        JPanel mgmtPanel = new JPanel(new GridLayout(2, 2, 4, 4));
        mgmtPanel.add(newBtn);
        mgmtPanel.add(renameBtn);
        mgmtPanel.add(copyBtn);
        mgmtPanel.add(deleteBtn);

        JPanel ioPanel = new JPanel(new GridLayout(1, 2, 4, 4));
        ioPanel.add(importBtn);
        ioPanel.add(exportBtn);

        JPanel southPanel = new JPanel(new BorderLayout(0, 4));
        southPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        southPanel.add(mgmtPanel, BorderLayout.NORTH);
        southPanel.add(ioPanel,   BorderLayout.SOUTH);

        JLabel title = new JLabel("My Decks", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(200, 0));
        panel.setBorder(BorderFactory.createEtchedBorder());
        panel.add(title,                        BorderLayout.NORTH);
        panel.add(new JScrollPane(deckList),    BorderLayout.CENTER);
        panel.add(southPanel,                   BorderLayout.SOUTH);
        return panel;
    }

    // -------------------------------------------------------------------------
    // Center: card browser (top) + deck contents (bottom)
    // -------------------------------------------------------------------------

    private JSplitPane buildCenterSplit() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildBrowserPanel(), buildDeckContentPanel());
        split.setResizeWeight(0.55);
        // Defer divider placement until the dialog is shown and sized
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.55));
        return split;
    }

    private JPanel buildBrowserPanel() {
        cardTable = new JTable(browserModel);
        cardTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cardTable.getTableHeader().setReorderingAllowed(false);
        cardTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        browserSorter = new TableRowSorter<>(browserModel);
        cardTable.setRowSorter(browserSorter);
        applyBrowserRenderers(cardTable);
        setNarrowColumns(cardTable);

        cardTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = cardTable.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = cardTable.convertRowIndexToModel(viewRow);
                    loadCardImageAsync((String) browserModel.getValueAt(modelRow, 0));
                }
            }
        });

        // Search controls
        String[] columnChoices = new String[BROWSER_COLUMNS.length + 1];
        columnChoices[0] = "All Columns";
        System.arraycopy(BROWSER_COLUMNS, 0, columnChoices, 1, BROWSER_COLUMNS.length);

        JComboBox<String> columnDropdown = new JComboBox<>(columnChoices);
        JTextField searchField = new JTextField(20);
        JButton searchButton = new JButton("\uD83D\uDD0D"); // 🔍
        JButton clearButton  = new JButton("✕");

        Runnable doSearch = () -> applyFilter(searchField.getText(), columnDropdown.getSelectedIndex());
        searchButton.addActionListener(e -> doSearch.run());
        clearButton.addActionListener(e -> { searchField.setText(""); browserSorter.setRowFilter(null); });
        searchField.addActionListener(e -> doSearch.run());

        JButton addBtn = new JButton("Add to Deck  ▼");
        addBtn.addActionListener(e -> addSelectedCard());
        // Double-click also adds
        cardTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) addSelectedCard();
            }
        });

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(columnDropdown);
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(clearButton);
        searchPanel.add(addBtn);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Card Browser"));
        panel.add(searchPanel,                BorderLayout.NORTH);
        panel.add(new JScrollPane(cardTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDeckContentPanel() {
        deckTable = new JTable(deckModel);
        deckTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deckTable.getTableHeader().setReorderingAllowed(false);
        deckTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        applyDeckRenderers(deckTable);
        setNarrowColumns(deckTable);

        deckTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = deckTable.getSelectedRow();
                if (row >= 0) loadCardImageAsync((String) deckModel.getValueAt(row, 0));
            }
        });

        JButton removeBtn    = new JButton("Remove from Deck  ✕");
        JButton removeAllBtn = new JButton("Remove All From Deck");
        removeAllBtn.setEnabled(false);

        removeBtn.addActionListener(e -> removeSelectedCard());
        removeAllBtn.addActionListener(e -> removeAllOfSelectedCard());

        // Double-click also removes one copy
        deckTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) removeSelectedCard();
            }
        });

        // Enable "Remove All" only when the selected card has Qty > 1
        deckTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = deckTable.getSelectedRow();
                if (row >= 0) {
                    Integer qty = (Integer) deckModel.getValueAt(row, 7);
                    removeAllBtn.setEnabled(qty != null && qty > 1);
                } else {
                    removeAllBtn.setEnabled(false);
                }
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        btnPanel.add(removeBtn);
        btnPanel.add(removeAllBtn);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Deck Contents"));
        panel.add(btnPanel,                   BorderLayout.NORTH);
        panel.add(new JScrollPane(deckTable), BorderLayout.CENTER);
        panel.add(countLabel,                 BorderLayout.SOUTH);
        return panel;
    }

    // -------------------------------------------------------------------------
    // Card browser loading
    // -------------------------------------------------------------------------

    private void loadBrowserCards() {
        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                return db.getAllCards();
            }
            @Override
            protected void done() {
                try {
                    for (Object[] row : get()) browserModel.addRow(row);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(DeckManager.this,
                            "Error loading card browser:\n" + e.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void applyFilter(String text, int columnDropdownIndex) {
        if (text == null || text.isBlank()) {
            browserSorter.setRowFilter(null);
        } else {
            String regex = "(?i)" + Pattern.quote(text.trim());
            if (columnDropdownIndex == 0) {
                browserSorter.setRowFilter(RowFilter.regexFilter(regex));
            } else {
                browserSorter.setRowFilter(RowFilter.regexFilter(regex, columnDropdownIndex - 1));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cell renderers (mirroring CardBrowser style)
    // -------------------------------------------------------------------------

    private void applyBrowserRenderers(JTable table) {
        // Grey out Job (7) and Category 2 (9) when empty
        DefaultTableCellRenderer greyIfEmpty = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                if (!sel && (value == null || value.toString().isBlank()))
                    setBackground(Color.LIGHT_GRAY);
                else
                    setBackground(t.getBackground());
                return this;
            }
        };
        for (int col : new int[]{7, 9})
            table.getColumnModel().getColumn(col).setCellRenderer(greyIfEmpty);

        // Grey out Power (5) when 0 or null
        table.getColumnModel().getColumn(5).setCellRenderer(powerRenderer(table));
    }

    private void applyDeckRenderers(JTable table) {
        // Grey out Power (5) when 0 or null
        table.getColumnModel().getColumn(5).setCellRenderer(powerRenderer(table));
    }

    private DefaultTableCellRenderer powerRenderer(JTable table) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                boolean empty = value == null || Integer.valueOf(0).equals(value);
                super.getTableCellRendererComponent(t, empty ? null : value, sel, focus, row, col);
                if (!sel) setBackground(empty ? Color.LIGHT_GRAY : t.getBackground());
                return this;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Deck list operations
    // -------------------------------------------------------------------------

    private void loadDeckList() throws SQLException {
        int prevId = selectedDeckId;
        deckListModel.clear();
        for (DeckEntry entry : db.getDecks()) deckListModel.addElement(entry);
        // Restore selection if the deck still exists
        for (int i = 0; i < deckListModel.size(); i++) {
            if (deckListModel.get(i).id() == prevId) {
                deckList.setSelectedIndex(i);
                return;
            }
        }
    }

    private void loadDeckCards(int deckId) {
        deckModel.setRowCount(0);
        try {
            int total = 0;
            for (Object[] row : db.getDeckCards(deckId)) {
                deckModel.addRow(row);
                total += (Integer) row[7];
            }
            countLabel.setText(total + " / " + MAX_DECK_SIZE + " cards");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading deck:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onNewDeck() {
        String name = JOptionPane.showInputDialog(this, "Enter deck name:", "New Deck", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        try {
            int id = db.createDeck(name.trim());
            loadDeckList();
            selectDeckById(id);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error creating deck:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onRenameDeck() {
        DeckEntry entry = deckList.getSelectedValue();
        if (entry == null) { JOptionPane.showMessageDialog(this, "Select a deck to rename."); return; }
        String name = (String) JOptionPane.showInputDialog(this, "Enter new name:", "Rename Deck",
                JOptionPane.PLAIN_MESSAGE, null, null, entry.name());
        if (name == null || name.isBlank()) return;
        try {
            db.renameDeck(entry.id(), name.trim());
            loadDeckList();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error renaming deck:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onCopyDeck() {
        DeckEntry entry = deckList.getSelectedValue();
        if (entry == null) { JOptionPane.showMessageDialog(this, "Select a deck to copy."); return; }
        String name = (String) JOptionPane.showInputDialog(this, "Enter name for copy:", "Copy Deck",
                JOptionPane.PLAIN_MESSAGE, null, null, entry.name() + " (Copy)");
        if (name == null || name.isBlank()) return;
        try {
            int newId = db.copyDeck(entry.id(), name.trim());
            loadDeckList();
            selectDeckById(newId);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error copying deck:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDeleteDeck() {
        DeckEntry entry = deckList.getSelectedValue();
        if (entry == null) { JOptionPane.showMessageDialog(this, "Select a deck to delete."); return; }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete \"" + entry.name() + "\"? This cannot be undone.",
                "Delete Deck", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            db.deleteDeck(entry.id());
            selectedDeckId = -1;
            deckModel.setRowCount(0);
            countLabel.setText("0 / " + MAX_DECK_SIZE + " cards");
            loadDeckList();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error deleting deck:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectDeckById(int id) {
        for (int i = 0; i < deckListModel.size(); i++) {
            if (deckListModel.get(i).id() == id) {
                deckList.setSelectedIndex(i);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Add / remove cards in the selected deck
    // -------------------------------------------------------------------------

    private void addSelectedCard() {
        if (selectedDeckId < 0) {
            JOptionPane.showMessageDialog(this, "Select or create a deck first.");
            return;
        }
        int viewRow = cardTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = cardTable.convertRowIndexToModel(viewRow);
        String serial = (String) browserModel.getValueAt(modelRow, 0);

        try {
            int total   = db.getTotalCardCount(selectedDeckId);
            int current = db.getCardCount(selectedDeckId, serial);

            if (total >= MAX_DECK_SIZE) {
                JOptionPane.showMessageDialog(this,
                        "Deck is already at " + MAX_DECK_SIZE + " cards.");
                return;
            }
            if (current >= MAX_COPIES) {
                JOptionPane.showMessageDialog(this,
                        "You already have " + MAX_COPIES + " copies of this card in the deck.");
                return;
            }

            db.setCardCount(selectedDeckId, serial, current + 1);
            loadDeckCards(selectedDeckId);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error adding card:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeSelectedCard() {
        if (selectedDeckId < 0) return;
        int viewRow = deckTable.getSelectedRow();
        if (viewRow < 0) return;
        String serial = (String) deckModel.getValueAt(viewRow, 0);
        int count     = (Integer) deckModel.getValueAt(viewRow, 7);

        try {
            db.setCardCount(selectedDeckId, serial, count - 1);
            loadDeckCards(selectedDeckId);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error removing card:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeAllOfSelectedCard() {
        if (selectedDeckId < 0) return;
        int viewRow = deckTable.getSelectedRow();
        if (viewRow < 0) return;
        String serial = (String) deckModel.getValueAt(viewRow, 0);

        try {
            db.setCardCount(selectedDeckId, serial, 0);
            loadDeckCards(selectedDeckId);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error removing card:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Import / Export
    // -------------------------------------------------------------------------

    private void onExportDeck() {
        DeckEntry entry = deckList.getSelectedValue();
        if (entry == null) { JOptionPane.showMessageDialog(this, "Select a deck to export."); return; }

        try {
            JSONArray cards = new JSONArray();
            for (Object[] row : db.getDeckCards(entry.id())) {
                cards.put(new JSONObject()
                        .put("serial", row[0])
                        .put("qty",    row[7]));
            }
            String json = new JSONObject()
                    .put("name",  entry.name())
                    .put("cards", cards)
                    .toString(2);

            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(json), null);

            JOptionPane.showMessageDialog(this,
                    "Deck JSON copied to clipboard.",
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException | JSONException e) {
            JOptionPane.showMessageDialog(this, "Error exporting deck:\n" + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onImportDeck() {
        JTextArea textArea = new JTextArea(16, 50);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(textArea);

        int result = JOptionPane.showConfirmDialog(this, scroll,
                "Paste Deck JSON", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String text = textArea.getText().trim();
        if (text.isBlank()) return;

        try {
            JSONObject json  = new JSONObject(text);
            String deckName  = json.getString("name");
            JSONArray cards  = json.getJSONArray("cards");

            int newId = db.createDeck(deckName);
            for (int i = 0; i < cards.length(); i++) {
                JSONObject card = cards.getJSONObject(i);
                String serial   = card.getString("serial");
                int qty         = card.getInt("qty");
                if (qty > 0) db.setCardCount(newId, serial, Math.min(qty, MAX_COPIES));
            }

            loadDeckList();
            selectDeckById(newId);
        } catch (JSONException e) {
            JOptionPane.showMessageDialog(this, "Invalid JSON format:\n" + e.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error importing deck:\n" + e.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Card image preview
    // -------------------------------------------------------------------------

    private void loadCardImageAsync(String serial) {
        cardImageLabel.setIcon(null);
        cardImageLabel.setText("Loading…");

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                try (java.sql.Connection conn = DriverManager.getConnection("jdbc:sqlite:fftcg_cards.db");
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT image_url FROM cards WHERE serial = ?")) {
                    ps.setString(1, serial);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String imageUrl = rs.getString("image_url");
                            if (imageUrl != null && !imageUrl.isBlank()) {
                                Image img = ImageIO.read(new URL(imageUrl));
                                if (img != null)
                                    return new ImageIcon(img.getScaledInstance(220, 309, Image.SCALE_SMOOTH));
                            }
                        }
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        cardImageLabel.setIcon(icon);
                        cardImageLabel.setText(null);
                    } else {
                        cardImageLabel.setIcon(null);
                        cardImageLabel.setText("No image available");
                    }
                } catch (InterruptedException | ExecutionException e) {
                    cardImageLabel.setIcon(null);
                    cardImageLabel.setText("Error loading image");
                }
            }
        }.execute();
    }

    // -------------------------------------------------------------------------
    // Column sizing
    // -------------------------------------------------------------------------

    /** Constrains Cost (4), Power (5), and Rarity (6) to compact widths. */
    private void setNarrowColumns(JTable table) {
        int[][] specs = {
            {4, 45, 60},  // Cost
            {5, 55, 70},  // Power
            {6, 55, 70},  // Rarity
        };
        for (int[] s : specs) {
            var col = table.getColumnModel().getColumn(s[0]);
            col.setPreferredWidth(s[1]);
            col.setMaxWidth(s[2]);
        }
    }
}

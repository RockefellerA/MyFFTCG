package fftcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import java.util.concurrent.ExecutionException;

import scraper.CardScraper;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public class CardBrowser extends JDialog {

    private static final String DB_URL = "jdbc:sqlite:fftcg_cards.db";
    private static final String[] COLUMNS = {
        "Serial", "Name", "Type", "Element", "Cost", "Power",
        "Rarity", "Job", "Category 1", "Category 2"
    };

    private final DefaultTableModel tableModel;
    private final JLabel cardImageLabel;
    private final JLabel countLabel;
    private TableRowSorter<DefaultTableModel> sorter;

    public CardBrowser(JFrame parent) {
        super(parent, "Card Browser", true);
        setSize(1200, 700);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // --- Card table ---
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        JTable cardTable = new JTable(tableModel);
        cardTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cardTable.getTableHeader().setReorderingAllowed(false);
        cardTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        sorter = new TableRowSorter<>(tableModel);
        cardTable.setRowSorter(sorter);

        // Grey out empty cells in Job (7), Category 1 (8), Category 2 (9)
        DefaultTableCellRenderer greyIfEmpty = new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected && (value == null || value.toString().isBlank())) {
                    setBackground(Color.LIGHT_GRAY);
                } else {
                    setBackground(table.getBackground());
                }
                return this;
            }
        };
        for (int col : new int[]{7, 9}) {
            cardTable.getColumnModel().getColumn(col).setCellRenderer(greyIfEmpty);
        }

        // Grey out Power (5) when value is 0 or null
        cardTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                boolean empty = value == null || Integer.valueOf(0).equals(value);
                super.getTableCellRendererComponent(table, empty ? null : value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    setBackground(empty ? Color.LIGHT_GRAY : table.getBackground());
                }
                return this;
            }
        });

        // --- Search bar ---
        String[] columnChoices = new String[COLUMNS.length + 1];
        columnChoices[0] = "All Columns";
        System.arraycopy(COLUMNS, 0, columnChoices, 1, COLUMNS.length);

        JComboBox<String> columnDropdown = new JComboBox<>(columnChoices);
        JTextField searchField = new JTextField(20);
        JButton searchButton = new JButton("\uD83D\uDD0D"); // 🔍
        JButton clearButton  = new JButton("✕");

        searchButton.addActionListener(e -> applyFilter(searchField.getText(), columnDropdown.getSelectedIndex()));
        clearButton.addActionListener(e -> {
            searchField.setText("");
            sorter.setRowFilter(null);
            updateCount();
        });
        // Also trigger search on Enter in the text field
        searchField.addActionListener(e -> applyFilter(searchField.getText(), columnDropdown.getSelectedIndex()));

        // --- Update button + spinner ---
        JButton updateButton = new JButton("Update Cards");
        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setVisible(false);
        spinner.setPreferredSize(new Dimension(120, updateButton.getPreferredSize().height));

        updateButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                    CardBrowser.this,
                    "This will re-fetch all cards from the Square Enix API and update the database.\nContinue?",
                    "Update Cards",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
            runScrape(updateButton, spinner);
        });

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(columnDropdown);
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(clearButton);
        searchPanel.add(updateButton);
        searchPanel.add(spinner);

        // --- Card image preview ---
        cardImageLabel = new JLabel("Select a card to preview", SwingConstants.CENTER);
        cardImageLabel.setPreferredSize(new Dimension(220, 309));
        cardImageLabel.setBorder(BorderFactory.createEtchedBorder());

        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setPreferredSize(new Dimension(240, 309));
        imagePanel.add(cardImageLabel, BorderLayout.CENTER);

        // Show card image when a row is selected
        cardTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = cardTable.getSelectedRow();
                if (row >= 0) {
                    int modelRow = cardTable.convertRowIndexToModel(row);
                    loadCardImageAsync((String) tableModel.getValueAt(modelRow, 0));
                }
            }
        });

        // --- Status bar ---
        countLabel = new JLabel("", SwingConstants.RIGHT);
        countLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 8));

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(countLabel, BorderLayout.EAST);

        add(searchPanel, BorderLayout.NORTH);
        add(new JScrollPane(cardTable), BorderLayout.CENTER);
        add(imagePanel, BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);

        if (!new java.io.File("fftcg_cards.db").exists()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "No card database found. Fetch card data from the Square Enix API now?",
                    "No Database Found",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                runScrape(updateButton, spinner);
            }
        } else {
            loadCards();
        }
    }

    private void runScrape(JButton updateButton, JProgressBar spinner) {
        updateButton.setEnabled(false);
        spinner.setVisible(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                CardScraper.main(new String[0]);
                return null;
            }
            @Override
            protected void done() {
                spinner.setVisible(false);
                tableModel.setRowCount(0);
                loadCards();
                updateButton.setEnabled(true);
            }
        }.execute();
    }

    private void applyFilter(String text, int columnDropdownIndex) {
        if (text == null || text.isBlank()) {
            sorter.setRowFilter(null);
        } else {
            String regex = "(?i)" + Pattern.quote(text.trim());
            if (columnDropdownIndex == 0) {
                // All columns
                sorter.setRowFilter(RowFilter.regexFilter(regex));
            } else {
                // Specific column (dropdown index 1 = COLUMNS index 0)
                sorter.setRowFilter(RowFilter.regexFilter(regex, columnDropdownIndex - 1));
            }
        }
        updateCount();
    }

    private void updateCount() {
        int visible = sorter.getViewRowCount();
        int total   = tableModel.getRowCount();
        countLabel.setText(visible == total
                ? "Total Cards: " + total
                : "Showing " + visible + " of " + total + " cards");
    }

    private void loadCards() {
        String sql = "SELECT serial, name_en, type_en, element, cost, power, rarity, job_en, "
                   + "category_1, category_2 FROM cards ORDER BY serial";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getString("serial"),
                    rs.getString("name_en"),
                    rs.getString("type_en"),
                    rs.getString("element"),
                    rs.getObject("cost"),
                    rs.getObject("power"),
                    rs.getString("rarity"),
                    rs.getString("job_en"),
                    rs.getString("category_1"),
                    rs.getString("category_2")
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading cards:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
        updateCount();
    }

    private void loadCardImageAsync(String serial) {
        cardImageLabel.setIcon(null);
        cardImageLabel.setText("Loading…");

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                try (Connection conn = DriverManager.getConnection(DB_URL);
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT image_url FROM cards WHERE serial = ?")) {
                    ps.setString(1, serial);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String imageUrl = rs.getString("image_url");
                            if (imageUrl != null && !imageUrl.isBlank()) {
                                Image img = ImageIO.read(new URL(imageUrl));
                                if (img != null) {
                                    return new ImageIcon(img.getScaledInstance(220, 309, Image.SCALE_SMOOTH));
                                }
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
}

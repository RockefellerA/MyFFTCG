package fftcg;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

public class CardBrowser extends JDialog {

    private static final String DB_URL = "jdbc:sqlite:fftcg_cards_test.db";

    private final DefaultTableModel tableModel;
    private final JLabel cardImageLabel;

    public CardBrowser(JFrame parent) {
        super(parent, "Card Browser", true);
        setSize(1200, 700);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // --- Card table ---
        String[] columns = {"Serial", "Name", "Type", "Element", "Cost", "Power", "Rarity", "Job", "Category 1", "Category 2", "Set"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        JTable cardTable = new JTable(tableModel);
        cardTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cardTable.getTableHeader().setReorderingAllowed(false);
        cardTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        cardTable.setAutoCreateRowSorter(true);

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
                    String serial = (String) tableModel.getValueAt(modelRow, 0);
                    loadCardImageAsync(serial);
                }
            }
        });

        JLabel countLabel = new JLabel("", SwingConstants.RIGHT);
        countLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 8));

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(countLabel, BorderLayout.EAST);

        add(new JScrollPane(cardTable), BorderLayout.CENTER);
        add(imagePanel, BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);

        loadCards(countLabel);
    }

    private void loadCards(JLabel countLabel) {
        String sql = "SELECT serial, name_en, type_en, element, cost, power, rarity, job_en, "
                   + "category_1, category_2, set_number FROM cards ORDER BY serial";
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
                    rs.getString("category_2"),
                    rs.getString("set_number")
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading cards:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
        countLabel.setText("Total Cards: " + tableModel.getRowCount());
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
                } catch (Exception e) {
                    cardImageLabel.setIcon(null);
                    cardImageLabel.setText("Error loading image");
                }
            }
        }.execute();
    }
}

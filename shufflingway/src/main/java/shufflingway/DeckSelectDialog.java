package shufflingway;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.sql.SQLException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

import scraper.DeckDatabase;
import scraper.DeckDatabase.DeckSummary;

public class DeckSelectDialog extends JDialog {

    private int selectedDeckId = -1;

    public DeckSelectDialog(JFrame parent) {
        super(parent, "New Game – Choose a Deck", true);
        setSize(420, 460);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        List<DeckSummary> decks = loadDecks();

        JLabel label = new JLabel("Select a deck with at least 50 main cards:");
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        DefaultListModel<DeckSummary> listModel = new DefaultListModel<>();
        for (DeckSummary d : decks) listModel.addElement(d);

        JList<DeckSummary> deckList = new JList<>(listModel);
        deckList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deckList.setCellRenderer(new DeckListRenderer());
        deckList.setFixedCellHeight(28);

        // Prevent selecting decks without exactly 50 main cards
        deckList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DeckSummary sel = deckList.getSelectedValue();
                if (sel != null && sel.mainCardCount() != 50) deckList.clearSelection();
            }
        });

        JButton startBtn  = new JButton("Start Game");
        JButton cancelBtn = new JButton("Cancel");
        startBtn.setEnabled(false);

        deckList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DeckSummary sel = deckList.getSelectedValue();
                startBtn.setEnabled(sel != null && sel.mainCardCount() == 50);
            }
        });

        startBtn.addActionListener(e -> {
            DeckSummary sel = deckList.getSelectedValue();
            if (sel != null) selectedDeckId = sel.id();
            dispose();
        });
        cancelBtn.addActionListener(e -> dispose());

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(startBtn);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.add(startBtn);
        btnPanel.add(cancelBtn);

        add(label,                     BorderLayout.NORTH);
        add(new JScrollPane(deckList), BorderLayout.CENTER);
        add(btnPanel,                  BorderLayout.SOUTH);
    }

    /** Returns the selected deck ID, or -1 if the dialog was cancelled. */
    public int getSelectedDeckId() { return selectedDeckId; }

    private List<DeckSummary> loadDecks() {
        try (DeckDatabase db = new DeckDatabase()) {
            return db.getDecksSummary();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading decks:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return List.of();
        }
    }

    private static class DeckListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof DeckSummary d) {
                setText(d.name() + "  (" + d.mainCardCount() + " / 50"
                        + (d.lbCardCount() > 0 ? " +" + d.lbCardCount() + " LB" : "") + ")");
                if (d.mainCardCount() != 50) {
                    setForeground(Color.GRAY);
                    setBackground(list.getBackground());
                }
            }
            return this;
        }
    }
}

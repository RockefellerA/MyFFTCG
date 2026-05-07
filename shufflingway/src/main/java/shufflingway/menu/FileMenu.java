package shufflingway.menu;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.IntConsumer;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import shufflingway.CardBrowser;
import shufflingway.DeckManager;
import shufflingway.DeckSelectDialog;
import shufflingway.PreferencesDialog;

/**
 * File menu for the main window.
 * Owns New Game, Deck Manager, Card Browser, Preferences, and Exit items.
 */
public class FileMenu extends JMenu {

    public FileMenu(JFrame owner, IntConsumer startGame, Runnable onLayoutChanged) {
        super("File");

        JMenuItem newGame = new JMenuItem("New Game");
        add(newGame);
        newGame.addActionListener((ActionEvent e) -> {
            DeckSelectDialog dialog = new DeckSelectDialog(owner);
            dialog.setVisible(true);
            int deckId = dialog.getSelectedDeckId();
            if (deckId >= 0) startGame.accept(deckId);
        });

        JMenuItem deckManager = new JMenuItem("Deck Manager");
        add(deckManager);
        deckManager.addActionListener((ActionEvent e) -> {
            DeckManager dm = new DeckManager(owner);
            dm.setVisible(true);
        });

        JMenuItem cardBrowser = new JMenuItem("Card Browser");
        add(cardBrowser);
        cardBrowser.addActionListener((ActionEvent e) -> {
            CardBrowser cb = new CardBrowser(owner);
            cb.setVisible(true);
        });

        JMenuItem preferences = new JMenuItem("Preferences");
        add(preferences);
        preferences.addActionListener((ActionEvent e) -> {
            PreferencesDialog dialog = new PreferencesDialog(owner, onLayoutChanged);
            dialog.setVisible(true);
        });

        addSeparator();

        JMenuItem exit = new JMenuItem("Exit Shufflingway");
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        add(exit);
        exit.addActionListener((ActionEvent e) -> {
            int result = JOptionPane.showConfirmDialog(owner,
                    "Are you sure you want to quit?", exit.getText(),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) System.exit(0);
        });
    }
}

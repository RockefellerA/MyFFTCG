package fftcg.menu;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

/**
 * Help menu for the main window.
 * Owns all guide links and the About item.
 */
public class HelpMenu extends JMenu {

    public HelpMenu(JFrame owner) {
        super("Help");

        addGuideItem("How to Play (Basics)",
                "This will open the FFTCG Starter Guide in your browser. Continue?",
                owner, 0);
        addGuideItem("How to Play (Advanced)",
                "This will open the FFTCG Comprehensive Rules in your browser. Continue?",
                owner, 1);
        addGuideItem("Limit Break Rules Sheet",
                "This will open the FFTCG Limit Break Rules Sheet in your browser. Continue?",
                owner, 2);
        addGuideItem("Priming Rules Explanation",
                "This will open the FFTCG Priming Rules Explanation in your browser. Continue?",
                owner, 3);
        addGuideItem("Priming Rules Supplementary Explanation",
                "This will open the FFTCG Priming Rules Supplementary Explanation in your browser. Continue?",
                owner, 4);

        addSeparator();

        JMenuItem about = new JMenuItem("About Shufflingway");
        add(about);
        about.addActionListener((ActionEvent e) -> {
            About dialog = new About();
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        });
    }

    private void addGuideItem(String label, String prompt, JFrame owner, int guideIndex) {
        JMenuItem item = new JMenuItem(label);
        add(item);
        item.addActionListener((ActionEvent e) -> {
            int result = JOptionPane.showConfirmDialog(owner, prompt, label,
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) openGuidePdf(guideIndex);
        });
    }

    private static void openGuidePdf(int guide) {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) return;
        try {
            switch (guide) {
                case 0 -> Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2024-03/fftcgrulesheet-en.pdf"));
                case 1 -> Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2025-09/fftcg-comprules-v3.2.1.pdf"));
                case 2 -> Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2024-03/lb-rule-explanation-eg.pdf"));
                case 3 -> Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2024-11/priming-rules-explanation-en.pdf"));
                case 4 -> Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2024-11/priming-supplementary-rules-en.pdf"));
            }
        } catch (IOException | URISyntaxException ex) {
            ex.printStackTrace();
        }
    }
}

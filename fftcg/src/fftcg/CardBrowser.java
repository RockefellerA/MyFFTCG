package fftcg;

import javax.swing.JPanel;
import java.awt.Panel;
import java.awt.Color;

@SuppressWarnings("serial")
public class CardBrowser extends JPanel {

	/**
	 * Create the panel.
	 */
	public CardBrowser() {
		setBackground(Color.BLUE);
		this.setBounds(800, 500, 1280, 720);
		setLayout(null);
		
		Panel panel = new Panel();
		panel.setBackground(Color.WHITE);
		panel.setBounds(62, 95, 160, 269);
		add(panel);
	}
}

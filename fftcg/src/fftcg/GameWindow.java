package fftcg;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

@SuppressWarnings("serial")
public class GameWindow extends JPanel {
	
	public int phase = 0; // Track the game phases with this handy button!

	/**
	 * Create the panel.
	 */
	public GameWindow(Component parent) {
		this.setBackground(new Color(0, 0, 139));
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{1280, 0};
		gridBagLayout.rowHeights = new int[]{673, 25, 0};
		gridBagLayout.columnWeights = new double[]{1.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		JButton phaseButton = new JButton("Active Phase");
		phaseButton.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		phaseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				switch (phase) {
				case 0: phaseButton.setText("Draw Phase");
					phase++;
					break;
				case 1: phaseButton.setText("Main Phase 1");
					phase++;
					break;
				case 2: phaseButton.setText("Attack Phase");
					phase++;
					break;
				case 3: phaseButton.setText("Main Phase 2");
					phase++;
					break;
				case 4: phaseButton.setText("End Phase");
					phase++;
					break;
				case 5: phaseButton.setText("Active Phase");
					phase=0;
					break;
				}
			}
		});
		
		GridBagConstraints gbc_phaseButton = new GridBagConstraints();
		gbc_phaseButton.anchor = GridBagConstraints.SOUTH;
		gbc_phaseButton.fill = GridBagConstraints.HORIZONTAL;
		gbc_phaseButton.gridx = 0;
		gbc_phaseButton.gridy = 1;
		this.add(phaseButton, gbc_phaseButton);
		
		Panel Deck1 = new Panel();
		Deck1.setBackground(new Color(255, 255, 255));
		Deck1.setBounds(1135, 329, 119, 134);
		
		JLabel lblNewLabel_2_1 = new JLabel("P1 Deck");
		Deck1.add(lblNewLabel_2_1);
		
		Panel Break1 = new Panel();
		Break1.setBackground(Color.WHITE);
		Break1.setBounds(1135, 487, 119, 134);
		
		JLabel lblNewLabel_1_1 = new JLabel("P1 Break Zone");
		Break1.add(lblNewLabel_1_1);
		
		Panel Damage1 = new Panel();
		Damage1.setBackground(Color.LIGHT_GRAY);
		Damage1.setBounds(10, 329, 127, 295);
		
		JLabel lblNewLabel_3_1 = new JLabel("P1 Damage Zone");
		Damage1.add(lblNewLabel_3_1);
		
		Panel Deck2 = new Panel();
		Deck2.setBackground(Color.WHITE);
		Deck2.setBounds(18, 165, 119, 134);
		
		JLabel lblNewLabel_2 = new JLabel("P2 Deck");
		Deck2.add(lblNewLabel_2);
		
		Panel Break2 = new Panel();
		Break2.setBackground(Color.WHITE);
		Break2.setBounds(18, 20, 119, 134);
		
		JLabel lblNewLabel_1 = new JLabel("P2 Break Zone");
		Break2.add(lblNewLabel_1);
		
		Panel Damage2 = new Panel();
		Damage2.setBackground(Color.LIGHT_GRAY);
		Damage2.setBounds(1127, 16, 127, 295);
		
		JLabel lblNewLabel_3 = new JLabel("P2 Damage Zone");
		Damage2.add(lblNewLabel_3);

	}

}

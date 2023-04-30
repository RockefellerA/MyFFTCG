package fftcg;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import java.awt.Font;

public class MainWindow {

	private JFrame frame;
	
	int i = 0;
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MainWindow window = new MainWindow();
					window.frame.setVisible(true);
					ImageIcon icon40 = new ImageIcon(getClass().getResource("/resources/MyFF40.png"));
					window.frame.setIconImage(icon40.getImage());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public MainWindow() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame("MyFFTCG");
		frame.setBackground(new Color(255, 255, 255));
		frame.getContentPane().setBackground(new Color(0, 0, 160));
		frame.setBounds(800, 500, 1280, 720);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(null);
		
		JButton phaseButton = new JButton("Active Phase");
		phaseButton.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		phaseButton.setBounds(0, 658, 1264, 23);
		phaseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				switch (i) {
				case 0: phaseButton.setText("Draw Phase");
						i++;
						break;
				case 1: phaseButton.setText("Main Phase 1");
						i++;
						break;
				case 2: phaseButton.setText("Attack Phase");
						i++;
						break;
				case 3: phaseButton.setText("Main Phase 2");
						i++;
						break;
				case 4: phaseButton.setText("End Phase");
						i++;
						break;
				case 5: phaseButton.setText("Active Phase");
						i=0;
						break;
				}
			}
		});
		frame.getContentPane().add(phaseButton);
		
		Panel Deck1 = new Panel();
		Deck1.setBackground(new Color(255, 255, 255));
		Deck1.setBounds(1135, 329, 119, 134);
		frame.getContentPane().add(Deck1);
		
		Panel Break1 = new Panel();
		Break1.setBackground(Color.WHITE);
		Break1.setBounds(1135, 487, 119, 134);
		frame.getContentPane().add(Break1);
		
		Panel Damage1 = new Panel();
		Damage1.setBackground(Color.LIGHT_GRAY);
		Damage1.setBounds(10, 329, 127, 295);
		frame.getContentPane().add(Damage1);
		
		Panel Deck2 = new Panel();
		Deck2.setBackground(Color.WHITE);
		Deck2.setBounds(18, 165, 119, 134);
		frame.getContentPane().add(Deck2);
		
		Panel Break2 = new Panel();
		Break2.setBackground(Color.WHITE);
		Break2.setBounds(18, 20, 119, 134);
		frame.getContentPane().add(Break2);
		
		Panel Damage2 = new Panel();
		Damage2.setBackground(Color.LIGHT_GRAY);
		Damage2.setBounds(1127, 16, 127, 295);
		frame.getContentPane().add(Damage2);
	}
}

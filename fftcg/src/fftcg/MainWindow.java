package fftcg;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import javax.swing.SwingConstants;

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
		phaseButton.setBounds(0, 636, 1264, 23);
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
		
		JLabel lblNewLabel_2_1 = new JLabel("P1 Deck");
		Deck1.add(lblNewLabel_2_1);
		
		Panel Break1 = new Panel();
		Break1.setBackground(Color.WHITE);
		Break1.setBounds(1135, 487, 119, 134);
		frame.getContentPane().add(Break1);
		
		JLabel lblNewLabel_1_1 = new JLabel("P1 Break Zone");
		Break1.add(lblNewLabel_1_1);
		
		Panel Damage1 = new Panel();
		Damage1.setBackground(Color.LIGHT_GRAY);
		Damage1.setBounds(10, 329, 127, 295);
		frame.getContentPane().add(Damage1);
		
		JLabel lblNewLabel_3_1 = new JLabel("P1 Damage Zone");
		Damage1.add(lblNewLabel_3_1);
		
		Panel Deck2 = new Panel();
		Deck2.setBackground(Color.WHITE);
		Deck2.setBounds(18, 165, 119, 134);
		frame.getContentPane().add(Deck2);
		
		JLabel lblNewLabel_2 = new JLabel("P2 Deck");
		Deck2.add(lblNewLabel_2);
		
		Panel Break2 = new Panel();
		Break2.setBackground(Color.WHITE);
		Break2.setBounds(18, 20, 119, 134);
		frame.getContentPane().add(Break2);
		
		JLabel lblNewLabel_1 = new JLabel("P2 Break Zone");
		Break2.add(lblNewLabel_1);
		
		Panel Damage2 = new Panel();
		Damage2.setBackground(Color.LIGHT_GRAY);
		Damage2.setBounds(1127, 16, 127, 295);
		frame.getContentPane().add(Damage2);
		
		JLabel lblNewLabel_3 = new JLabel("P2 Damage Zone");
		Damage2.add(lblNewLabel_3);
		
		JInternalFrame aboutFrame = new JInternalFrame("About MyFFTCG");
		aboutFrame.setFrameIcon(new ImageIcon(MainWindow.class.getResource("/resources/MyFF20.png")));
		aboutFrame.setClosable(true);
		aboutFrame.setBounds(460, 229, 272, 166);
		frame.getContentPane().add(aboutFrame);
		
		JLabel lblNewLabel = new JLabel("<html>Author: Andrew Rockefeller © 2023<br/>Pixel NES font by Neale Davidson</html>");
		lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
		aboutFrame.getContentPane().add(lblNewLabel, BorderLayout.CENTER);
		
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);
		
		JMenu mnNewMenu = new JMenu("File");
		menuBar.add(mnNewMenu);
		
		JMenuItem mntmNewMenuItem_3 = new JMenuItem("New Game");
		mnNewMenu.add(mntmNewMenuItem_3);
		
		JMenuItem mntmNewMenuItem_1 = new JMenuItem("Deck Manager");
		mnNewMenu.add(mntmNewMenuItem_1);
		
		JMenuItem mntmNewMenuItem_2 = new JMenuItem("Card Browser");
		mnNewMenu.add(mntmNewMenuItem_2);
		
		JMenuItem mntmNewMenuItem = new JMenuItem("Exit MyFFTCG");
		mntmNewMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
		mnNewMenu.add(mntmNewMenuItem);
		
		JMenu helpMenu = new JMenu("Help");
		menuBar.add(helpMenu);
		
		JMenuItem howToPlayMenuItem = new JMenuItem("How to Play");
		helpMenu.add(howToPlayMenuItem);
		
		JMenuItem menuItemAbout = new JMenuItem("About MyFFTCG");
		helpMenu.add(menuItemAbout);
		menuItemAbout.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aboutFrame.setVisible(true);
			}
		});
	}
}

package fftcg;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

public class MainWindow {

	private JFrame frame;
	
	int phase = 0; // Track the game phases with this handy button!
	
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
//		frame.getContentPane().setLayout(new CardLayout());
		
		JButton phaseButton = new JButton("Active Phase");
		phaseButton.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		phaseButton.setBounds(0, 636, 1264, 23);
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
		
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);
		
		JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);
		
		JMenuItem newGameMenuItem = new JMenuItem("New Game");
		fileMenu.add(newGameMenuItem);
		
		JMenuItem deckManagerMenuItem = new JMenuItem("Deck Manager");
		fileMenu.add(deckManagerMenuItem);
		
		JMenuItem cardBrowserMenuItem = new JMenuItem("Card Browser");
		fileMenu.add(cardBrowserMenuItem);
		cardBrowserMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// Call a JPanel? Frame? How should I design this?
			}
		});
		
		JMenuItem exitMenuItem = new JMenuItem("Exit MyFFTCG");
		exitMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
		fileMenu.add(exitMenuItem);
		exitMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
	            int result = JOptionPane.showConfirmDialog(frame,"Are you sure you want to quit?", exitMenuItem.getText(),
	                    JOptionPane.YES_NO_OPTION,
	                    JOptionPane.WARNING_MESSAGE);
				if (result == JOptionPane.YES_OPTION) {
					System.exit(0);
				}
			}
		});
		
		JMenu helpMenu = new JMenu("Help");
		menuBar.add(helpMenu);
		
		JMenuItem howToPlayMenuItem = new JMenuItem("How to Play (Basics)");
		helpMenu.add(howToPlayMenuItem);
		howToPlayMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
	            int result = JOptionPane.showConfirmDialog(frame,"This will open the FFTCG Starter Guide in your browser. Continue?", howToPlayMenuItem.getText(),
	                    JOptionPane.YES_NO_OPTION,
	                    JOptionPane.QUESTION_MESSAGE);
				if (result == JOptionPane.YES_OPTION) {
					openGuidePdf(0);
				}
			}
		});
		
		JMenuItem howToPlay2MenuItem = new JMenuItem("How to Play (Advanced)");
		helpMenu.add(howToPlay2MenuItem);
		howToPlay2MenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
	            int result = JOptionPane.showConfirmDialog(frame,"This will open the FFTCG Comprehensive Rules in your browser. Continue?", howToPlay2MenuItem.getText(),
	                    JOptionPane.YES_NO_OPTION,
	                    JOptionPane.QUESTION_MESSAGE);
				if (result == JOptionPane.YES_OPTION) {
					openGuidePdf(1);
				}
			}
		});
		
		JMenuItem menuItemAbout = new JMenuItem("About MyFFTCG");
		helpMenu.add(menuItemAbout);
		menuItemAbout.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				About dialog = new About();
				dialog.setLocationRelativeTo(frame);
				dialog.setVisible(true);
			}
		});
	}
	
	/**
	 * Opens the Starter or Advanced Guide in the browser.  These links could go dead.
	 */
	private void openGuidePdf(int guide) {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
		    try {
		    	switch(guide) {
		    		case 0:
		    			Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2020-02/fftcgrulesheet-en.pdf"));
		    			break;
		    		case 1:
						Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2022-11/fftcg-opus-cr-english-20220811.pdf"));
		    			break;
		    	}
			} catch (IOException | URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}
}

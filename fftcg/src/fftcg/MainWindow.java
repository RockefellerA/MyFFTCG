package fftcg;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Image;
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
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Font;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.SoftBevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.JTextField;

public class MainWindow {

	private JFrame frame;
	
	int phase = 0; // Track the game phases with this handy button!
	
	boolean gameInProgress = false; // placeholder to assume a game is in progress
	private JTextField txtP2;
	private JTextField txtP1;
	
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
		frame.getContentPane().setBackground(Color.LIGHT_GRAY);
		frame.setBounds(0, 0, 1920, 1080);
		frame.setLocationRelativeTo(null);
		frame.setResizable(false);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		JButton phaseButton = new JButton("Active Phase");
		phaseButton.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		phaseButton.setBounds(0, 996, 1904, 23);
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
//		
//		Panel Deck1 = new Panel();
//		Deck1.setBackground(new Color(255, 255, 255));
//		Deck1.setBounds(1135, 329, 119, 134);
//		frame.getContentPane().add(Deck1);
//		
//		JLabel lblNewLabel_2_1 = new JLabel("P1 Deck");
//		Deck1.add(lblNewLabel_2_1);
//		
//		Panel Break1 = new Panel();
//		Break1.setBackground(Color.WHITE);
//		Break1.setBounds(1135, 487, 119, 134);
//		frame.getContentPane().add(Break1);
//		
//		JLabel lblNewLabel_1_1 = new JLabel("P1 Break Zone");
//		Break1.add(lblNewLabel_1_1);
//		
//		Panel Damage1 = new Panel();
//		Damage1.setBackground(Color.LIGHT_GRAY);
//		Damage1.setBounds(10, 329, 127, 295);
//		frame.getContentPane().add(Damage1);
//		
//		JLabel lblNewLabel_3_1 = new JLabel("P1 Damage Zone");
//		Damage1.add(lblNewLabel_3_1);
//		
//		Panel Deck2 = new Panel();
//		Deck2.setBackground(Color.WHITE);
//		Deck2.setBounds(18, 165, 119, 134);
//		frame.getContentPane().add(Deck2);
//		
//		JLabel lblNewLabel_2 = new JLabel("P2 Deck");
//		Deck2.add(lblNewLabel_2);
//		
//		Panel Break2 = new Panel();
//		Break2.setBackground(Color.WHITE);
//		Break2.setBounds(18, 20, 119, 134);
//		frame.getContentPane().add(Break2);
//		
//		JLabel lblNewLabel_1 = new JLabel("P2 Break Zone");
//		Break2.add(lblNewLabel_1);
//		
//		Panel Damage2 = new Panel();
//		Damage2.setBackground(Color.LIGHT_GRAY);
//		Damage2.setBounds(1127, 16, 127, 295);
//		frame.getContentPane().add(Damage2);
//		
//		JLabel lblNewLabel_3 = new JLabel("P2 Damage Zone");
//		Damage2.add(lblNewLabel_3);
		
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);
		
		JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);
		
		JMenuItem newGameMenuItem = new JMenuItem("New Game");
		fileMenu.add(newGameMenuItem);
		newGameMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frame.getContentPane().removeAll();
				frame.getContentPane().invalidate();
				frame.getContentPane().add(new GameWindow(frame));
				frame.getContentPane().revalidate();
//				frame.setLocationRelativeTo(null);
			}
		});
		
		JMenuItem deckManagerMenuItem = new JMenuItem("Deck Manager");
		fileMenu.add(deckManagerMenuItem);
		
		JMenuItem cardBrowserMenuItem = new JMenuItem("Card Browser");
		fileMenu.add(cardBrowserMenuItem);
		cardBrowserMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (gameInProgress) {
					int result = JOptionPane.showConfirmDialog(frame,"Game is in progress. Abandon to view card browser?", cardBrowserMenuItem.getText(),
							JOptionPane.YES_NO_OPTION,
							JOptionPane.WARNING_MESSAGE);
					if (result == JOptionPane.YES_OPTION) {
						frame.getContentPane().removeAll();
						frame.getContentPane().invalidate();
						frame.getContentPane().add(new CardBrowser());
						frame.getContentPane().revalidate();
					}
				}
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
		frame.getContentPane().setLayout(null);
		
		JLabel deck = new JLabel();
		deck.setBorder(new BevelBorder(BevelBorder.RAISED, new Color(0, 0, 0), null, null, null));
		deck.setToolTipText("Player 1 Deck");
		deck.setBounds(1676, 605, 132, 185);
		frame.getContentPane().add(deck);
		
		ImageIcon cardBack = new ImageIcon(getClass().getResource("/resources/cardback60p.jpg"));
		deck.setIcon(cardBack);
		
		JLabel break1 = new JLabel();
		break1.setHorizontalAlignment(SwingConstants.CENTER);
		break1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		break1.setText("B R E A K");
		break1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		break1.setBackground(Color.DARK_GRAY);
		break1.setToolTipText("Player 1 Break Zone");
		break1.setBounds(1676, 801, 132, 185);
		frame.getContentPane().add(break1);
		
		JLabel lblDAM = new JLabel();
		lblDAM.setToolTipText("Player 1 Damage Zone");
		lblDAM.setText("DAMAGE");
		lblDAM.setHorizontalAlignment(SwingConstants.CENTER);
		lblDAM.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblDAM.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblDAM.setBackground(Color.DARK_GRAY);
		lblDAM.setBounds(10, 490, 132, 497);
		frame.getContentPane().add(lblDAM);
		
		JLabel lblLimit = new JLabel();
		lblLimit.setToolTipText("Player 1 LB");
		lblLimit.setText("LIMIT");
		lblLimit.setHorizontalAlignment(SwingConstants.CENTER);
		lblLimit.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblLimit.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblLimit.setBackground(Color.DARK_GRAY);
		lblLimit.setBounds(1818, 605, 76, 185);
		frame.getContentPane().add(lblLimit);
		
		JLabel lblRemove = new JLabel();
		lblRemove.setToolTipText("Player 1 Removed From Play");
		lblRemove.setText("REMOVED FROM PLAY");
		lblRemove.setHorizontalAlignment(SwingConstants.CENTER);
		lblRemove.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblRemove.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblRemove.setBackground(Color.DARK_GRAY);
		lblRemove.setBounds(1818, 801, 76, 185);
		frame.getContentPane().add(lblRemove);
		
		JLabel lblDAM_1 = new JLabel();
		lblDAM_1.setToolTipText("Player 2 Damage Zone");
		lblDAM_1.setText("DAMAGE");
		lblDAM_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblDAM_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblDAM_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblDAM_1.setBackground(Color.DARK_GRAY);
		lblDAM_1.setBounds(1762, 11, 132, 497);
		frame.getContentPane().add(lblDAM_1);
		
		txtP2 = new JTextField();
		txtP2.setEditable(false);
		txtP2.setBorder(null);
		txtP2.setHorizontalAlignment(SwingConstants.CENTER);
		txtP2.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		txtP2.setText("P2");
		txtP2.setBackground(Color.LIGHT_GRAY);
		txtP2.setBounds(1785, 227, 86, 20);
		frame.getContentPane().add(txtP2);
		txtP2.setColumns(10);
		
		txtP1 = new JTextField();
		txtP1.setEditable(false);
		txtP1.setBorder(null);
		txtP1.setText("P1");
		txtP1.setHorizontalAlignment(SwingConstants.CENTER);
		txtP1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		txtP1.setColumns(10);
		txtP1.setBackground(Color.LIGHT_GRAY);
		txtP1.setBounds(31, 710, 86, 20);
		frame.getContentPane().add(txtP1);
		
		JLabel deck_1 = new JLabel();
		deck_1.setIcon(new ImageIcon(MainWindow.class.getResource("/resources/cardback60p.jpg")));
		deck_1.setToolTipText("Player 2 Deck");
		deck_1.setBorder(new BevelBorder(BevelBorder.RAISED, new Color(0, 0, 0), null, null, null));
		deck_1.setBounds(96, 207, 132, 185);
		frame.getContentPane().add(deck_1);
		
		JLabel lblRemove_1 = new JLabel();
		lblRemove_1.setToolTipText("Player 2 Removed From Play");
		lblRemove_1.setText("REMOVED FROM PLAY");
		lblRemove_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblRemove_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblRemove_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblRemove_1.setBackground(Color.DARK_GRAY);
		lblRemove_1.setBounds(10, 11, 76, 185);
		frame.getContentPane().add(lblRemove_1);
		
		JLabel break1_1 = new JLabel();
		break1_1.setToolTipText("Player 2 Break Zone");
		break1_1.setText("B R E A K");
		break1_1.setHorizontalAlignment(SwingConstants.CENTER);
		break1_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		break1_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		break1_1.setBackground(Color.DARK_GRAY);
		break1_1.setBounds(96, 11, 132, 185);
		frame.getContentPane().add(break1_1);
		
		JLabel lblLimit_1 = new JLabel();
		lblLimit_1.setToolTipText("Player 2 LB");
		lblLimit_1.setText("LIMIT");
		lblLimit_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblLimit_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblLimit_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblLimit_1.setBackground(Color.DARK_GRAY);
		lblLimit_1.setBounds(10, 207, 76, 185);
		frame.getContentPane().add(lblLimit_1);
		
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
						Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/advanced-rules/fftcg-rules-2.1.10.pdf"));
		    			break;
		    	}
			} catch (IOException | URISyntaxException e1) {
				e1.printStackTrace();
			}
		}
	}
}

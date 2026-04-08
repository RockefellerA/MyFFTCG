package fftcg;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.GridLayout;
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
import java.awt.Cursor;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.SoftBevelBorder;
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
		frame.setResizable(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		frame.getContentPane().setLayout(new BorderLayout());

		// --- Menu Bar ---
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
			}
		});

		JMenuItem deckManagerMenuItem = new JMenuItem("Deck Manager");
		fileMenu.add(deckManagerMenuItem);

		JMenuItem cardBrowserMenuItem = new JMenuItem("Card Browser");
		fileMenu.add(cardBrowserMenuItem);
		cardBrowserMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (gameInProgress) {
					int result = JOptionPane.showConfirmDialog(frame, "Game is in progress. Abandon to view card browser?", cardBrowserMenuItem.getText(),
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
				int result = JOptionPane.showConfirmDialog(frame, "Are you sure you want to quit?", exitMenuItem.getText(),
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
				int result = JOptionPane.showConfirmDialog(frame, "This will open the FFTCG Starter Guide in your browser. Continue?", howToPlayMenuItem.getText(),
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
				int result = JOptionPane.showConfirmDialog(frame, "This will open the FFTCG Comprehensive Rules in your browser. Continue?", howToPlay2MenuItem.getText(),
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

		// --- Phase Button ---
		JButton phaseButton = new JButton("Active Phase");
		phaseButton.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		phaseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				switch (phase) {
					case 0: phaseButton.setText("Draw Phase");   phase++; break;
					case 1: phaseButton.setText("Main Phase 1"); phase++; break;
					case 2: phaseButton.setText("Attack Phase"); phase++; break;
					case 3: phaseButton.setText("Main Phase 2"); phase++; break;
					case 4: phaseButton.setText("End Phase");    phase++; break;
					case 5: phaseButton.setText("Active Phase"); phase = 0; break;
				}
			}
		});

		// --- P2 Zones (top of screen) ---
		// Top-left corner: narrow column (Remove, Limit) beside wide column (Break, Deck)
		JLabel lblRemove_1 = new JLabel("REMOVED FROM PLAY");
		lblRemove_1.setToolTipText("Player 2 Removed From Play");
		lblRemove_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblRemove_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblRemove_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblRemove_1.setBackground(Color.DARK_GRAY);
		lblRemove_1.setOpaque(true);

		JLabel lblLimit_1 = new JLabel("LIMIT");
		lblLimit_1.setToolTipText("Player 2 LB");
		lblLimit_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblLimit_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblLimit_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblLimit_1.setBackground(Color.DARK_GRAY);
		lblLimit_1.setOpaque(true);

		JLabel break1_1 = new JLabel("B R E A K");
		break1_1.setToolTipText("Player 2 Break Zone");
		break1_1.setHorizontalAlignment(SwingConstants.CENTER);
		break1_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		break1_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		break1_1.setBackground(Color.DARK_GRAY);
		break1_1.setOpaque(true);

		JLabel deck_1 = new JLabel();
		deck_1.setIcon(new ImageIcon(MainWindow.class.getResource("/resources/cardback60p.jpg")));
		deck_1.setToolTipText("Player 2 Deck");
		deck_1.setHorizontalAlignment(SwingConstants.CENTER);
		deck_1.setBorder(new BevelBorder(BevelBorder.RAISED, new Color(0, 0, 0), null, null, null));

		JPanel p2CornerNarrow = new JPanel(new GridLayout(2, 1));
		p2CornerNarrow.add(lblRemove_1);
		p2CornerNarrow.add(lblLimit_1);

		JPanel p2CornerWide = new JPanel(new GridLayout(2, 1));
		p2CornerWide.add(break1_1);
		p2CornerWide.add(deck_1);

		JPanel p2CornerPanel = new JPanel(new BorderLayout());
		p2CornerPanel.add(p2CornerNarrow, BorderLayout.WEST);
		p2CornerPanel.add(p2CornerWide, BorderLayout.CENTER);

		// Top-right: damage zone with player label
		JLabel lblDAM_1 = new JLabel("DAMAGE");
		lblDAM_1.setToolTipText("Player 2 Damage Zone");
		lblDAM_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblDAM_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblDAM_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblDAM_1.setBackground(Color.DARK_GRAY);
		lblDAM_1.setOpaque(true);

		txtP2 = new JTextField("P2");
		txtP2.setEditable(false);
		txtP2.setBorder(null);
		txtP2.setHorizontalAlignment(SwingConstants.CENTER);
		txtP2.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		txtP2.setBackground(Color.LIGHT_GRAY);

		JPanel p2DamagePanel = new JPanel(new BorderLayout());
		p2DamagePanel.add(lblDAM_1, BorderLayout.CENTER);
		p2DamagePanel.add(txtP2, BorderLayout.SOUTH);

		JPanel p2ZonesPanel = new JPanel(new BorderLayout());
		p2ZonesPanel.add(p2CornerPanel, BorderLayout.WEST);
		p2ZonesPanel.add(p2DamagePanel, BorderLayout.EAST);

		// --- P1 Zones (bottom of screen, above phase button) ---
		// Bottom-left: damage zone with player label
		JLabel lblDAM = new JLabel("DAMAGE");
		lblDAM.setToolTipText("Player 1 Damage Zone");
		lblDAM.setHorizontalAlignment(SwingConstants.CENTER);
		lblDAM.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblDAM.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblDAM.setBackground(Color.DARK_GRAY);
		lblDAM.setOpaque(true);

		txtP1 = new JTextField("P1");
		txtP1.setEditable(false);
		txtP1.setBorder(null);
		txtP1.setHorizontalAlignment(SwingConstants.CENTER);
		txtP1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		txtP1.setBackground(Color.LIGHT_GRAY);

		JPanel p1DamagePanel = new JPanel(new BorderLayout());
		p1DamagePanel.add(txtP1, BorderLayout.NORTH);
		p1DamagePanel.add(lblDAM, BorderLayout.CENTER);

		// Bottom-right corner: wide column (Deck, Break) beside narrow column (Limit, Remove)
		JLabel deck = new JLabel();
		deck.setIcon(new ImageIcon(getClass().getResource("/resources/cardback60p.jpg")));
		deck.setToolTipText("Player 1 Deck");
		deck.setHorizontalAlignment(SwingConstants.CENTER);
		deck.setBorder(new BevelBorder(BevelBorder.RAISED, new Color(0, 0, 0), null, null, null));

		JLabel break1 = new JLabel("B R E A K");
		break1.setToolTipText("Player 1 Break Zone");
		break1.setHorizontalAlignment(SwingConstants.CENTER);
		break1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		break1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		break1.setBackground(Color.DARK_GRAY);
		break1.setOpaque(true);

		JLabel lblLimit = new JLabel("LIMIT");
		lblLimit.setToolTipText("Player 1 LB");
		lblLimit.setHorizontalAlignment(SwingConstants.CENTER);
		lblLimit.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblLimit.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblLimit.setBackground(Color.DARK_GRAY);
		lblLimit.setOpaque(true);

		JLabel lblRemove = new JLabel("REMOVED FROM PLAY");
		lblRemove.setToolTipText("Player 1 Removed From Play");
		lblRemove.setHorizontalAlignment(SwingConstants.CENTER);
		lblRemove.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblRemove.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblRemove.setBackground(Color.DARK_GRAY);
		lblRemove.setOpaque(true);

		JPanel p1CornerWide = new JPanel(new GridLayout(2, 1));
		p1CornerWide.add(deck);
		p1CornerWide.add(break1);

		JPanel p1CornerNarrow = new JPanel(new GridLayout(2, 1));
		p1CornerNarrow.add(lblLimit);
		p1CornerNarrow.add(lblRemove);

		JPanel p1CornerPanel = new JPanel(new BorderLayout());
		p1CornerPanel.add(p1CornerWide, BorderLayout.CENTER);
		p1CornerPanel.add(p1CornerNarrow, BorderLayout.EAST);

		JPanel p1ZonesPanel = new JPanel(new BorderLayout());
		p1ZonesPanel.add(p1DamagePanel, BorderLayout.WEST);
		p1ZonesPanel.add(p1CornerPanel, BorderLayout.EAST);

		// --- South Panel: P1 zones + phase button ---
		JPanel southPanel = new JPanel(new BorderLayout());
		southPanel.add(p1ZonesPanel, BorderLayout.CENTER);
		southPanel.add(phaseButton, BorderLayout.SOUTH);

		// --- Game Board (center, takes all remaining space) ---
		JPanel gameBoard = new JPanel();
		gameBoard.setBackground(Color.LIGHT_GRAY);

		// --- Assemble ---
		frame.getContentPane().add(p2ZonesPanel, BorderLayout.NORTH);
		frame.getContentPane().add(southPanel, BorderLayout.SOUTH);
		frame.getContentPane().add(gameBoard, BorderLayout.CENTER);
		
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

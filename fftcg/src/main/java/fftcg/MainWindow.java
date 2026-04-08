package fftcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JSeparator;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.SoftBevelBorder;

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

		JMenuItem cardBrowserMenuItem = new JMenuItem("Card Browser");
		fileMenu.add(cardBrowserMenuItem);
		cardBrowserMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				CardBrowser cb = new CardBrowser(frame);
				cb.setVisible(true);
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

		// Card dimensions (cardback.jpg scaled to 110x155, sized to allow full 220x309 preview to fit)
		Dimension cardSize = new Dimension(110, 155);

		// --- Card Preview (shown on deck mouseover, right side of window) ---
		JLabel cardPreview = new JLabel();
		cardPreview.setIcon(new ImageIcon(getClass().getResource("/resources/cardback.jpg")));
		cardPreview.setVisible(false);

		MouseAdapter deckHover = new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) { cardPreview.setVisible(true); }
			@Override public void mouseExited(MouseEvent e)  { cardPreview.setVisible(false); }
		};

		// --- P2 Zones (top of screen) ---
		// Top-left corner: narrow column (Remove, Limit) beside wide column (Break, Deck)
		JLabel lblRemove_1 = new JLabel("<html><div style='text-align:center'>REMOVED<br>FROM<br>PLAY</div></html>");
		lblRemove_1.setToolTipText("Player 2 Removed From Play");
		lblRemove_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblRemove_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblRemove_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblRemove_1.setBackground(Color.DARK_GRAY);
		lblRemove_1.setForeground(Color.WHITE);
		lblRemove_1.setOpaque(true);
		lblRemove_1.setPreferredSize(cardSize);
		lblRemove_1.setMinimumSize(cardSize);

		JLabel lblLimit_1 = new JLabel("LIMIT");
		lblLimit_1.setToolTipText("Player 2 LB");
		lblLimit_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblLimit_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblLimit_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblLimit_1.setBackground(Color.DARK_GRAY);
		lblLimit_1.setForeground(Color.WHITE);
		lblLimit_1.setOpaque(true);
		lblLimit_1.setPreferredSize(cardSize);
		lblLimit_1.setMinimumSize(cardSize);

		JLabel break1_1 = new JLabel("BREAK");
		break1_1.setToolTipText("Player 2 Break Zone");
		break1_1.setHorizontalAlignment(SwingConstants.CENTER);
		break1_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		break1_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		break1_1.setBackground(Color.DARK_GRAY);
		break1_1.setForeground(Color.WHITE);
		break1_1.setOpaque(true);
		break1_1.setPreferredSize(cardSize);
		break1_1.setMinimumSize(cardSize);

		JLabel deck_1 = new JLabel();
		deck_1.setIcon(new ImageIcon(new ImageIcon(MainWindow.class.getResource("/resources/cardback.jpg")).getImage().getScaledInstance(cardSize.width, cardSize.height, Image.SCALE_SMOOTH)));
		deck_1.setToolTipText("Player 2 Deck");
		deck_1.setHorizontalAlignment(SwingConstants.CENTER);
		deck_1.setBorder(new BevelBorder(BevelBorder.RAISED, new Color(0, 0, 0), null, null, null));
		deck_1.addMouseListener(deckHover);

		JPanel p2CornerPanel = new JPanel(new GridLayout(2, 2));
		p2CornerPanel.add(lblRemove_1);
		p2CornerPanel.add(break1_1);
		p2CornerPanel.add(lblLimit_1);
		p2CornerPanel.add(deck_1);

		// Top-right: damage zone with player label
		JLabel lblDAM_1 = new JLabel("<html><div style='text-align:center'>D<br>A<br>M<br>A<br>G<br>E</div></html>");
		lblDAM_1.setToolTipText("Player 2 Damage Zone");
		lblDAM_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblDAM_1.setVerticalAlignment(SwingConstants.CENTER);
		lblDAM_1.setFont(new Font("Pixel NES", Font.PLAIN, 36));
		lblDAM_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblDAM_1.setBackground(Color.DARK_GRAY);
		lblDAM_1.setForeground(Color.WHITE);
		lblDAM_1.setOpaque(true);

		txtP2 = new JTextField("P2");
		txtP2.setEditable(false);
		txtP2.setFocusable(false);
		txtP2.setBorder(null);
		txtP2.setHorizontalAlignment(SwingConstants.CENTER);
		txtP2.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		txtP2.setBackground(Color.LIGHT_GRAY);

		JPanel p2DamagePanel = new JPanel(new BorderLayout());
		p2DamagePanel.setPreferredSize(new Dimension(cardSize.width, cardSize.height * 2));
		p2DamagePanel.add(lblDAM_1, BorderLayout.CENTER);
		p2DamagePanel.add(txtP2, BorderLayout.SOUTH);

		JPanel p2ZonesPanel = new JPanel(new BorderLayout());
		p2ZonesPanel.add(p2CornerPanel, BorderLayout.WEST);
		p2ZonesPanel.add(p2DamagePanel, BorderLayout.EAST);

		// --- P1 Zones (bottom of screen, above phase button) ---
		// Bottom-left: damage zone with player label
		JLabel lblDAM = new JLabel("<html><div style='text-align:center'>D<br>A<br>M<br>A<br>G<br>E</div></html>");
		lblDAM.setToolTipText("Player 1 Damage Zone");
		lblDAM.setHorizontalAlignment(SwingConstants.CENTER);
		lblDAM.setVerticalAlignment(SwingConstants.CENTER);
		lblDAM.setFont(new Font("Pixel NES", Font.PLAIN, 36));
		lblDAM.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblDAM.setBackground(Color.DARK_GRAY);
		lblDAM.setForeground(Color.WHITE);
		lblDAM.setOpaque(true);

		txtP1 = new JTextField("P1");
		txtP1.setEditable(false);
		txtP1.setFocusable(false);
		txtP1.setBorder(null);
		txtP1.setHorizontalAlignment(SwingConstants.CENTER);
		txtP1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		txtP1.setBackground(Color.LIGHT_GRAY);

		JPanel p1DamagePanel = new JPanel(new BorderLayout());
		p1DamagePanel.setPreferredSize(new Dimension(cardSize.width, cardSize.height * 2));
		p1DamagePanel.add(txtP1, BorderLayout.NORTH);
		p1DamagePanel.add(lblDAM, BorderLayout.CENTER);

		// Bottom-right corner: wide column (Deck, Break) beside narrow column (Limit, Remove)
		JLabel deck = new JLabel();
		deck.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/resources/cardback.jpg")).getImage().getScaledInstance(cardSize.width, cardSize.height, Image.SCALE_SMOOTH)));
		deck.setToolTipText("Player 1 Deck");
		deck.setHorizontalAlignment(SwingConstants.CENTER);
		deck.setBorder(new BevelBorder(BevelBorder.RAISED, new Color(0, 0, 0), null, null, null));
		deck.addMouseListener(deckHover);

		JLabel break1 = new JLabel("BREAK");
		break1.setToolTipText("Player 1 Break Zone");
		break1.setHorizontalAlignment(SwingConstants.CENTER);
		break1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		break1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		break1.setBackground(Color.DARK_GRAY);
		break1.setForeground(Color.WHITE);
		break1.setOpaque(true);
		break1.setPreferredSize(cardSize);
		break1.setMinimumSize(cardSize);

		JLabel lblLimit = new JLabel("LIMIT");
		lblLimit.setToolTipText("Player 1 LB");
		lblLimit.setHorizontalAlignment(SwingConstants.CENTER);
		lblLimit.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblLimit.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblLimit.setBackground(Color.DARK_GRAY);
		lblLimit.setForeground(Color.WHITE);
		lblLimit.setOpaque(true);
		lblLimit.setPreferredSize(cardSize);
		lblLimit.setMinimumSize(cardSize);

		JLabel lblRemove = new JLabel("<html><div style='text-align:center'>REMOVED<br>FROM<br>PLAY</div></html>");
		lblRemove.setToolTipText("Player 1 Removed From Play");
		lblRemove.setHorizontalAlignment(SwingConstants.CENTER);
		lblRemove.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblRemove.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblRemove.setBackground(Color.DARK_GRAY);
		lblRemove.setForeground(Color.WHITE);
		lblRemove.setOpaque(true);
		lblRemove.setPreferredSize(cardSize);
		lblRemove.setMinimumSize(cardSize);

		JPanel p1CornerPanel = new JPanel(new GridLayout(2, 2));
		p1CornerPanel.add(deck);
		p1CornerPanel.add(lblLimit);
		p1CornerPanel.add(break1);
		p1CornerPanel.add(lblRemove);

		JPanel p1ZonesPanel = new JPanel(new BorderLayout());
		p1ZonesPanel.add(p1DamagePanel, BorderLayout.WEST);
		p1ZonesPanel.add(p1CornerPanel, BorderLayout.EAST);

		// --- South Panel: P1 zones + phase button ---
		JPanel southPanel = new JPanel(new BorderLayout());
		southPanel.add(p1ZonesPanel, BorderLayout.CENTER);
		southPanel.add(phaseButton, BorderLayout.SOUTH);

		// --- Game Board (center, takes all remaining space) ---
		// --- P2 board half ---
		JPanel p2Board = new JPanel();
		p2Board.setBackground(UIManager.getColor("Panel.background"));

		// --- P1 board half ---
		JPanel p1Board = new JPanel();
		p1Board.setBackground(UIManager.getColor("Panel.background"));

		JSeparator divider = new JSeparator(JSeparator.HORIZONTAL);
		divider.setForeground(Color.LIGHT_GRAY);

		JPanel gameBoard = new JPanel(new GridBagLayout());
		gameBoard.setBackground(UIManager.getColor("Panel.background"));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill    = GridBagConstraints.BOTH;
		gbc.weightx = 1.0;
		gbc.gridx   = 0;

		gbc.weighty = 1.0; gbc.gridy = 0; gameBoard.add(p2Board,  gbc);
		gbc.weighty = 0.0; gbc.gridy = 1; gameBoard.add(divider,  gbc);
		gbc.weighty = 1.0; gbc.gridy = 2; gameBoard.add(p1Board,  gbc);

		// --- Card Preview Panel (right side, vertically centered) ---
		JPanel previewPanel = new JPanel(new GridBagLayout());
		previewPanel.setOpaque(true);
		previewPanel.add(cardPreview);
		previewPanel.setBackground(UIManager.getColor("Panel.background"));

		// --- Assemble ---
		frame.getContentPane().add(p2ZonesPanel, BorderLayout.NORTH);
		frame.getContentPane().add(southPanel, BorderLayout.SOUTH);
		frame.getContentPane().add(gameBoard, BorderLayout.CENTER);
		frame.getContentPane().add(previewPanel, BorderLayout.EAST);
		
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

package fftcg;

import scraper.DeckDatabase;
import scraper.DeckDatabase.DeckCardDetail;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.SoftBevelBorder;

public class MainWindow {

	private JFrame frame;

	int phase = 0;

	// Card size constants
	private static final int CARD_W = 140;
	private static final int CARD_H = 205;

	// P1 zone labels that change during gameplay
	private JLabel p1DeckLabel;
	private JLabel p1LimitLabel;
	private JLabel p1HandLabel;
	private GrayscaleLabel p1RemoveLabel;
	private GrayscaleLabel p2RemoveLabel;
	// Zoom popup for LB card hover
	private JWindow zoomPopup;
	// Opening hand confirmation popup
	private JWindow openingHandPopup;

	// --- P1 game state ---
	private Deque<String> p1MainDeck  = new ArrayDeque<>();  // imageUrls
	private List<String>  p1LbDeck    = new ArrayList<>();   // imageUrls
	private List<String>  p1Hand      = new ArrayList<>();   // imageUrls
	private int           p1LbIndex   = 0;
	private int           p1HandIndex = 0;
	private boolean       p1TopFaceUp = false;               // is top main-deck card revealed?
	private boolean       p1OpeningHandPending = false;

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

	public MainWindow() {
		initialize();
	}

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
				DeckSelectDialog dialog = new DeckSelectDialog(frame);
				dialog.setVisible(true);
				int deckId = dialog.getSelectedDeckId();
				if (deckId >= 0) startGame(deckId);
			}
		});

		JMenuItem deckManagerMenuItem = new JMenuItem("Deck Manager");
		fileMenu.add(deckManagerMenuItem);
		deckManagerMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				DeckManager dm = new DeckManager(frame);
				dm.setVisible(true);
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

		JMenuItem preferencesMenuItem = new JMenuItem("Preferences");
		fileMenu.add(preferencesMenuItem);
		preferencesMenuItem.addActionListener(e -> {
			PreferencesDialog dialog = new PreferencesDialog(frame);
			dialog.setVisible(true);
		});
		fileMenu.addSeparator();

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
				if (result == JOptionPane.YES_OPTION) openGuidePdf(0);
			}
		});

		JMenuItem howToPlay2MenuItem = new JMenuItem("How to Play (Advanced)");
		helpMenu.add(howToPlay2MenuItem);
		howToPlay2MenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int result = JOptionPane.showConfirmDialog(frame, "This will open the FFTCG Comprehensive Rules in your browser. Continue?", howToPlay2MenuItem.getText(),
						JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE);
				if (result == JOptionPane.YES_OPTION) openGuidePdf(1);
			}
		});

		JMenuItem limitBreakRulesMenuItem = new JMenuItem("Limit Break Rules Sheet");
		helpMenu.add(limitBreakRulesMenuItem);
		limitBreakRulesMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int result = JOptionPane.showConfirmDialog(frame, "This will open the FFTCG Limit Break Rules Sheet in your browser. Continue?", limitBreakRulesMenuItem.getText(),
						JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE);
				if (result == JOptionPane.YES_OPTION) openGuidePdf(2);
			}
		});

		JMenuItem primingRulesMenuItem = new JMenuItem("Priming Rules Explanation");
		helpMenu.add(primingRulesMenuItem);
		primingRulesMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int result = JOptionPane.showConfirmDialog(frame, "This will open the FFTCG Priming Rules Explanation in your browser. Continue?", primingRulesMenuItem.getText(),
						JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE);
				if (result == JOptionPane.YES_OPTION) openGuidePdf(3);
			}
		});

		JMenuItem primingSupplementaryRulesMenuItem = new JMenuItem("Priming Rules Supplementary Explanation");
		helpMenu.add(primingSupplementaryRulesMenuItem);
		primingSupplementaryRulesMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int result = JOptionPane.showConfirmDialog(frame, "This will open the FFTCG Priming Rules Supplementary Explanation in your browser. Continue?", primingSupplementaryRulesMenuItem.getText(),
						JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE);
				if (result == JOptionPane.YES_OPTION) openGuidePdf(4);
			}
		});

		helpMenu.addSeparator();

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

		Dimension cardSize = new Dimension(CARD_W, CARD_H);

		// --- P2 Zones (top of screen) ---
		p2RemoveLabel = new GrayscaleLabel("<html><div style='text-align:center'>REMOVED<br>FROM<br>PLAY</div></html>");
		GrayscaleLabel lblRemove_1 = p2RemoveLabel;
		lblRemove_1.setToolTipText("Player 2 Removed From Play");
		lblRemove_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblRemove_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblRemove_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblRemove_1.setBackground(Color.DARK_GRAY);
		lblRemove_1.setForeground(Color.WHITE);
		lblRemove_1.setOpaque(true);
		lblRemove_1.setPreferredSize(cardSize);
		lblRemove_1.setMinimumSize(cardSize);
		lblRemove_1.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) { showGrayscaleZoom(p2RemoveLabel); }
			@Override public void mouseExited(MouseEvent e)  { hideZoom(); }
		});

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
		deck_1.setIcon(scaledCardback(cardSize));
		deck_1.setToolTipText("Player 2 Deck");
		deck_1.setHorizontalAlignment(SwingConstants.CENTER);
		deck_1.setBorder(new BevelBorder(BevelBorder.RAISED, new Color(0, 0, 0), null, null, null));

		JPanel p2CornerPanel = new JPanel(new GridLayout(2, 2));
		p2CornerPanel.add(lblRemove_1);
		p2CornerPanel.add(break1_1);
		p2CornerPanel.add(lblLimit_1);
		p2CornerPanel.add(deck_1);

		JComboBox<String> p2ColorBox = buildColorDropdown();
		JPanel p2DamagePanel = buildDamageZonePanel("P2", p2ColorBox);

		JPanel p2BackupSlots = buildBackupZonePanel();
		JPanel p2BackupWrapper = new JPanel(new GridBagLayout());
		GridBagConstraints p2BackupGbc = new GridBagConstraints();
		p2BackupGbc.anchor = GridBagConstraints.NORTH;
		p2BackupGbc.weighty = 1.0;
		p2BackupGbc.insets = new Insets(0, CARD_W - 8, 0, 0);
		p2BackupWrapper.add(p2BackupSlots, p2BackupGbc);

		JPanel p2HandAligned = new JPanel(new GridBagLayout());
		p2HandAligned.setPreferredSize(new Dimension(2 * CARD_W, CARD_H));
		GridBagConstraints p2HandGbc = new GridBagConstraints();
		p2HandGbc.anchor = GridBagConstraints.NORTHWEST;
		p2HandGbc.weighty = 1.0;
		p2HandAligned.add(buildHandSlot(), p2HandGbc);

		JPanel p2EastPanel = new JPanel(new BorderLayout(0, 0));
		p2EastPanel.add(p2HandAligned, BorderLayout.WEST);
		p2EastPanel.add(p2DamagePanel,  BorderLayout.EAST);

		JPanel p2ZonesPanel = new JPanel(new BorderLayout());
		p2ZonesPanel.add(p2CornerPanel,   BorderLayout.WEST);
		p2ZonesPanel.add(p2BackupWrapper, BorderLayout.CENTER);
		p2ZonesPanel.add(p2EastPanel,     BorderLayout.EAST);

		// --- P1 Zones (bottom of screen) ---
		JComboBox<String> p1ColorBox = buildColorDropdown();
		JPanel p1DamagePanel = buildDamageZonePanel("P1", p1ColorBox);

		// P1 deck label — interactive
		p1DeckLabel = new JLabel();
		p1DeckLabel.setIcon(scaledCardback(cardSize));
		p1DeckLabel.setToolTipText("Player 1 Deck");
		p1DeckLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p1DeckLabel.setBorder(new BevelBorder(BevelBorder.RAISED, new Color(0, 0, 0), null, null, null));
		p1DeckLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				onP1DeckClicked();
			}
			@Override
			public void mouseEntered(MouseEvent e) {
				if (p1TopFaceUp && !p1MainDeck.isEmpty()) showDeckZoom(p1MainDeck.peek());
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hideZoom();
			}
		});

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

		// P1 limit label — interactive
		p1LimitLabel = new JLabel("LIMIT");
		p1LimitLabel.setToolTipText("Player 1 LB Deck");
		p1LimitLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p1LimitLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		p1LimitLabel.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		p1LimitLabel.setBackground(Color.DARK_GRAY);
		p1LimitLabel.setForeground(Color.WHITE);
		p1LimitLabel.setOpaque(true);
		p1LimitLabel.setPreferredSize(cardSize);
		p1LimitLabel.setMinimumSize(cardSize);
		p1LimitLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				onP1LbClicked();
			}
			@Override
			public void mouseEntered(MouseEvent e) {
				showLbZoom();
			}
			@Override
			public void mouseExited(MouseEvent e) {
				hideZoom();
			}
		});

		p1RemoveLabel = new GrayscaleLabel("<html><div style='text-align:center'>REMOVED<br>FROM<br>PLAY</div></html>");
		GrayscaleLabel lblRemove = p1RemoveLabel;
		lblRemove.setToolTipText("Player 1 Removed From Play");
		lblRemove.setHorizontalAlignment(SwingConstants.CENTER);
		lblRemove.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		lblRemove.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblRemove.setBackground(Color.DARK_GRAY);
		lblRemove.setForeground(Color.WHITE);
		lblRemove.setOpaque(true);
		lblRemove.setPreferredSize(cardSize);
		lblRemove.setMinimumSize(cardSize);
		lblRemove.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) { showGrayscaleZoom(p1RemoveLabel); }
			@Override public void mouseExited(MouseEvent e)  { hideZoom(); }
		});

		JPanel p1CornerPanel = new JPanel(new GridLayout(2, 2));
		p1CornerPanel.add(p1DeckLabel);
		p1CornerPanel.add(p1LimitLabel);
		p1CornerPanel.add(break1);
		p1CornerPanel.add(lblRemove);

		JPanel p1BackupSlots = buildBackupZonePanel();
		JPanel p1BackupWrapper = new JPanel(new GridBagLayout());
		GridBagConstraints p1BackupGbc = new GridBagConstraints();
		p1BackupGbc.anchor = GridBagConstraints.SOUTH;
		p1BackupGbc.weighty = 1.0;
		p1BackupGbc.insets = new Insets(0, 0, 0, CARD_W - 8);
		p1BackupWrapper.add(p1BackupSlots, p1BackupGbc);

		p1HandLabel = buildHandSlot();
		p1HandLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				onP1HandClicked();
			}
		});

		JPanel p1HandAligned = new JPanel(new GridBagLayout());
		p1HandAligned.setPreferredSize(new Dimension(2 * CARD_W, CARD_H));
		GridBagConstraints p1HandGbc = new GridBagConstraints();
		p1HandGbc.anchor = GridBagConstraints.SOUTHEAST;
		p1HandGbc.weighty = 1.0;
		p1HandAligned.add(p1HandLabel, p1HandGbc);

		JPanel p1WestPanel = new JPanel(new BorderLayout(0, 0));
		p1WestPanel.add(p1DamagePanel,  BorderLayout.WEST);
		p1WestPanel.add(p1HandAligned,  BorderLayout.EAST);

		JPanel p1ZonesPanel = new JPanel(new BorderLayout());
		p1ZonesPanel.add(p1WestPanel,     BorderLayout.WEST);
		p1ZonesPanel.add(p1BackupWrapper, BorderLayout.CENTER);
		p1ZonesPanel.add(p1CornerPanel,   BorderLayout.EAST);

		JPanel southPanel = new JPanel(new BorderLayout());
		southPanel.add(p1ZonesPanel, BorderLayout.CENTER);
		southPanel.add(phaseButton,  BorderLayout.SOUTH);

		// --- Game Board ---
		GradientPanel p2Board = new GradientPanel(true);
		GradientPanel p1Board = new GradientPanel(false);

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

		MouseAdapter debugRightClick = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e)  { maybeShowDebugMenu(e); }
			@Override
			public void mouseReleased(MouseEvent e) { maybeShowDebugMenu(e); }
			private void maybeShowDebugMenu(MouseEvent e) {
				if (!e.isPopupTrigger()) return;
				boolean isP2Side = e.getSource() == p2Board;
				showDebugMenu(e, isP2Side ? p2RemoveLabel : p1RemoveLabel);
			}
		};
		p2Board.addMouseListener(debugRightClick);
		p1Board.addMouseListener(debugRightClick);

		p2ColorBox.addActionListener(e -> {
			String sel = (String) p2ColorBox.getSelectedItem();
			Color c = "Default".equals(sel) ? null : ElementColor.fromName(sel).color;
			applyElementColor(sel, p2ZonesPanel);
			p2Board.setGradientColor(c);
		});
		p1ColorBox.addActionListener(e -> {
			String sel = (String) p1ColorBox.getSelectedItem();
			Color c = "Default".equals(sel) ? null : ElementColor.fromName(sel).color;
			applyElementColor(sel, p1ZonesPanel);
			p1Board.setGradientColor(c);
		});

		// --- Assemble ---
		frame.getContentPane().add(p2ZonesPanel, BorderLayout.NORTH);
		frame.getContentPane().add(southPanel,   BorderLayout.SOUTH);
		frame.getContentPane().add(gameBoard,    BorderLayout.CENTER);
	}

	// -------------------------------------------------------------------------
	// Game startup
	// -------------------------------------------------------------------------

	private void startGame(int deckId) {
		p1MainDeck.clear();
		p1LbDeck.clear();
		p1Hand.clear();
		p1LbIndex   = 0;
		p1HandIndex = 0;
		p1TopFaceUp = false;
		p1OpeningHandPending = false;
		refreshP1HandLabel();

		new SwingWorker<Void, Void>() {
			List<DeckCardDetail> cards;

			@Override
			protected Void doInBackground() throws Exception {
				try (DeckDatabase db = new DeckDatabase()) {
					cards = db.getDeckCardsDetailed(deckId);
				}
				return null;
			}

			@Override
			protected void done() {
				try {
					get(); // surface any exception
				} catch (InterruptedException | ExecutionException ex) {
					JOptionPane.showMessageDialog(frame, "Error loading deck:\n" + ex.getMessage(),
							"Error", JOptionPane.ERROR_MESSAGE);
					return;
				}

				List<String> main = new ArrayList<>();
				for (DeckCardDetail card : cards) {
					if (card.isLb()) p1LbDeck.add(card.imageUrl());
					else             main.add(card.imageUrl());
				}
				Collections.shuffle(main);
				p1MainDeck.addAll(main);

				p1OpeningHandPending = true;
				refreshP1DeckLabel();
				refreshP1LimitLabel();
			}
		}.execute();
	}

	// -------------------------------------------------------------------------
	// P1 deck interaction
	// -------------------------------------------------------------------------

	private void onP1DeckClicked() {
		if (p1MainDeck.isEmpty()) return;

		if (p1OpeningHandPending) {
			showOpeningHandConfirm();
			return;
		}

		if (!p1TopFaceUp) {
			// Reveal top card
			p1TopFaceUp = true;
			String url = p1MainDeck.peek();
			loadDeckLabelAsync(p1DeckLabel, url);
		} else {
			// Put top card on bottom, show cardback again
			String top = p1MainDeck.poll();
			p1MainDeck.addLast(top);
			p1TopFaceUp = false;
			p1DeckLabel.setIcon(scaledCardback(new Dimension(CARD_W, CARD_H)));
		}
	}

	private void showOpeningHandConfirm() {
		if (openingHandPopup != null) { openingHandPopup.dispose(); }
		openingHandPopup = new JWindow(frame);

		JButton yesBtn = new JButton("Draw opening hand (5 cards)");
		yesBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		yesBtn.addActionListener(e -> {
			openingHandPopup.dispose();
			openingHandPopup = null;
			p1OpeningHandPending = false;
			drawOpeningHand();
		});

		JButton noBtn = new JButton("Cancel");
		noBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		noBtn.addActionListener(e -> {
			openingHandPopup.dispose();
			openingHandPopup = null;
		});

		JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
		panel.setBorder(BorderFactory.createRaisedBevelBorder());
		panel.add(yesBtn);
		panel.add(noBtn);

		openingHandPopup.getContentPane().add(panel);
		openingHandPopup.pack();

		Point loc = p1DeckLabel.getLocationOnScreen();
		openingHandPopup.setLocation(loc.x - openingHandPopup.getWidth() - 6, loc.y);
		openingHandPopup.setVisible(true);
	}

	private void drawOpeningHand() {
		for (int i = 0; i < 5 && !p1MainDeck.isEmpty(); i++) {
			p1Hand.add(p1MainDeck.poll());
		}
		p1HandIndex = 0;
		refreshP1DeckLabel();
		refreshP1HandLabel();
	}

	private void refreshP1HandLabel() {
		if (p1Hand.isEmpty()) {
			p1HandLabel.setIcon(null);
			p1HandLabel.setText("HAND");
			return;
		}
		loadHandLabelAsync(p1Hand.get(p1HandIndex % p1Hand.size()));
	}

	private void onP1HandClicked() {
		if (p1Hand.isEmpty()) return;
		p1HandIndex = (p1HandIndex + 1) % p1Hand.size();
		refreshP1HandLabel();
	}

	private void loadHandLabelAsync(String imageUrl) {
		new SwingWorker<ImageIcon, Void>() {
			@Override
			protected ImageIcon doInBackground() throws Exception {
				Image img = ImageIO.read(new URL(imageUrl));
				return img == null ? null : new ImageIcon(
						img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
			}
			@Override
			protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) { p1HandLabel.setIcon(icon); p1HandLabel.setText(null); }
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	private void refreshP1DeckLabel() {
		p1TopFaceUp = false;
		p1DeckLabel.setIcon(p1MainDeck.isEmpty() ? null : scaledCardback(new Dimension(CARD_W, CARD_H)));
		p1DeckLabel.setText(p1MainDeck.isEmpty() ? "EMPTY" : null);
	}

	// -------------------------------------------------------------------------
	// P1 LB deck interaction
	// -------------------------------------------------------------------------

	private void onP1LbClicked() {
		if (p1LbDeck.isEmpty()) return;
		// Rotate: current card goes to bottom, advance index
		String current = p1LbDeck.remove(p1LbIndex % p1LbDeck.size());
		p1LbDeck.add(current); // add to end
		p1LbIndex = 0;         // new top is index 0 after rotation
		refreshP1LimitLabel();
	}

	private void refreshP1LimitLabel() {
		if (p1LbDeck.isEmpty()) {
			p1LimitLabel.setIcon(null);
			p1LimitLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
			p1LimitLabel.setText("LIMIT");
			return;
		}
		String url = p1LbDeck.get(0);
		loadLbLabelAsync(url);
	}

	// -------------------------------------------------------------------------
	// Async image loading helpers
	// -------------------------------------------------------------------------

	/** Loads a card image into a zone label at card size. */
	private void loadDeckLabelAsync(JLabel target, String imageUrl) {
		new SwingWorker<ImageIcon, Void>() {
			@Override
			protected ImageIcon doInBackground() throws Exception {
				Image img = ImageIO.read(new URL(imageUrl));
				return img == null ? null : new ImageIcon(
						img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
			}
			@Override
			protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) { target.setIcon(icon); target.setText(null); }
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	/** Loads the current LB card into p1LimitLabel at card size. */
	private void loadLbLabelAsync(String imageUrl) {
		new SwingWorker<ImageIcon, Void>() {
			@Override
			protected ImageIcon doInBackground() throws Exception {
				Image img = ImageIO.read(new URL(imageUrl));
				return img == null ? null : new ImageIcon(
						img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
			}
			@Override
			protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) {
						p1LimitLabel.setText(null);
						p1LimitLabel.setIcon(icon);
					}
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	// -------------------------------------------------------------------------
	// LB zoom popup (full-resolution on hover)
	// -------------------------------------------------------------------------

	private void showDeckZoom(String url) {
		showZoomAt(url, p1DeckLabel);
	}

	private void showLbZoom() {
		if (!p1LbDeck.isEmpty()) showZoomAt(p1LbDeck.get(0), p1LimitLabel);
	}

	private void showZoomAt(String url, JLabel anchor) {
		if (url == null) return;

		if (zoomPopup == null) zoomPopup = new JWindow(frame);

		new SwingWorker<ImageIcon, Void>() {
			@Override
			protected ImageIcon doInBackground() throws Exception {
				Image img = ImageIO.read(new URL(url));
				return img == null ? null : new ImageIcon(img);
			}
			@Override
			protected void done() {
				try {
					ImageIcon icon = get();
					if (icon == null) return;

					JLabel zl = new JLabel(icon);
					zl.setBorder(BorderFactory.createRaisedBevelBorder());
					zoomPopup.getContentPane().removeAll();
					zoomPopup.getContentPane().add(zl);
					zoomPopup.pack();

					int w = icon.getIconWidth();
					int h = icon.getIconHeight();
					Point loc = anchor.getLocationOnScreen();
					int x = loc.x - w - 6;
					int y = loc.y + (anchor.getHeight() - h) / 2;
					int screenH = Toolkit.getDefaultToolkit().getScreenSize().height;
					y = Math.max(0, Math.min(y, screenH - h));
					zoomPopup.setLocation(x, y);
					zoomPopup.setVisible(true);
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	private void hideZoom() {
		if (zoomPopup != null) zoomPopup.setVisible(false);
	}

	private void showGrayscaleZoom(GrayscaleLabel label) {
		String url = label.getUrl();
		if (url == null) return;

		if (zoomPopup == null) zoomPopup = new JWindow(frame);

		new SwingWorker<ImageIcon, Void>() {
			@Override
			protected ImageIcon doInBackground() throws Exception {
				Image img = ImageIO.read(new URL(url));
				if (img == null) return null;
				BufferedImage buf = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
				buf.getGraphics().drawImage(img, 0, 0, null);
				BufferedImage gray = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(buf, null);
				return new ImageIcon(gray);
			}
			@Override
			protected void done() {
				try {
					ImageIcon icon = get();
					if (icon == null) return;
					JLabel zl = new JLabel(icon);
					zl.setBorder(BorderFactory.createRaisedBevelBorder());
					zoomPopup.getContentPane().removeAll();
					zoomPopup.getContentPane().add(zl);
					zoomPopup.pack();
					Point loc = label.getLocationOnScreen();
					int x = loc.x - icon.getIconWidth() - 6;
					int y = loc.y + (label.getHeight() - icon.getIconHeight()) / 2;
					int screenH = Toolkit.getDefaultToolkit().getScreenSize().height;
					y = Math.max(0, Math.min(y, screenH - icon.getIconHeight()));
					zoomPopup.setLocation(x, y);
					zoomPopup.setVisible(true);
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	// -------------------------------------------------------------------------
	// Debug menu
	// -------------------------------------------------------------------------

	private void showDebugMenu(MouseEvent e, GrayscaleLabel removeLabel) {
		JPopupMenu menu = new JPopupMenu("Debug");

		JMenuItem addToRemoved = new JMenuItem("Add random card to Removed from Play");
		addToRemoved.addActionListener(ev -> debugAddRandomCardToRemoved(removeLabel));
		menu.add(addToRemoved);

		menu.show(e.getComponent(), e.getX(), e.getY());
	}

	private void debugAddRandomCardToRemoved(GrayscaleLabel removeLabel) {
		List<String> pool = new ArrayList<>(p1MainDeck);
		pool.addAll(p1Hand);
		if (pool.isEmpty()) return;
		String url = pool.get((int) (Math.random() * pool.size()));
		new SwingWorker<ImageIcon, Void>() {
			@Override
			protected ImageIcon doInBackground() throws Exception {
				Image img = ImageIO.read(new URL(url));
				return img == null ? null : new ImageIcon(
						img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
			}
			@Override
			protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) {
						removeLabel.setText(null);
						removeLabel.setIcon(icon); // GrayscaleLabel converts automatically
						removeLabel.setUrl(url);
					}
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Builds a damage zone panel for one player.
	 * Contains 7 slots (D, A, M, A, G, E, Px) stacked vertically,
	 * each sized to hold a sideways card (CARD_H wide × CARD_W tall).
	 * The color dropdown sits below the slots.
	 */
	private JPanel buildBackupZonePanel() {
		JPanel slotsPanel = new JPanel(new GridLayout(1, 5, 2, 0));
		slotsPanel.setBackground(Color.LIGHT_GRAY);
		for (int i = 1; i <= 5; i++) {
			JLabel slot = new JLabel("BACKUP " + i, SwingConstants.CENTER);
			slot.setFont(new Font("Pixel NES", Font.PLAIN, 11));
			slot.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
			slot.setBackground(Color.LIGHT_GRAY);
			slot.setForeground(Color.DARK_GRAY);
			slot.setOpaque(true);
			slot.setPreferredSize(new Dimension(CARD_W, CARD_H));
			slot.setMinimumSize(new Dimension(CARD_W, CARD_H));
			slotsPanel.add(slot);
		}
		return slotsPanel;
	}

	private JLabel buildHandSlot() {
		JLabel slot = new JLabel("HAND", SwingConstants.CENTER);
		slot.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		slot.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		slot.setBackground(Color.LIGHT_GRAY);
		slot.setForeground(Color.DARK_GRAY);
		slot.setOpaque(true);
		slot.setPreferredSize(new Dimension(CARD_W, CARD_H));
		slot.setMinimumSize(new Dimension(CARD_W, CARD_H));
		return slot;
	}

	private JPanel buildDamageZonePanel(String playerLabel, JComboBox<String> colorBox) {
		String[] letters = { "D", "A", "M", "A", "G", "E", playerLabel };

		JPanel slotsPanel = new JPanel(new GridLayout(7, 1, 2, 2));
		slotsPanel.setBackground(Color.DARK_GRAY);
		for (String letter : letters) {
			JLabel slot = new JLabel(letter, SwingConstants.CENTER);
			slot.setFont(new Font("Pixel NES", Font.PLAIN, 14));
			slot.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
			slot.setBackground(Color.DARK_GRAY);
			slot.setForeground(Color.WHITE);
			slot.setOpaque(true);
			slotsPanel.add(slot);
		}

		// Match the original fixed size: one card wide, two cards tall
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setPreferredSize(new Dimension(CARD_W, CARD_H * 2));
		panel.add(slotsPanel, BorderLayout.CENTER);
		panel.add(colorBox,   BorderLayout.SOUTH);
		return panel;
	}

	private ImageIcon scaledCardback(Dimension size) {
		return new ImageIcon(new ImageIcon(getClass().getResource("/resources/cardback.jpg"))
				.getImage().getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH));
	}

	private JComboBox<String> buildColorDropdown() {
		String[] items = new String[ElementColor.values().length + 1];
		items[0] = "Default";
		for (int i = 0; i < ElementColor.values().length; i++)
			items[i + 1] = ElementColor.values()[i].name().charAt(0)
					+ ElementColor.values()[i].name().substring(1).toLowerCase();
		return new JComboBox<>(items);
	}

	private void applyElementColor(String selection, JPanel... panels) {
		Color bg = "Default".equals(selection)
				? UIManager.getColor("Panel.background")
				: ElementColor.fromName(selection).color;
		for (JPanel panel : panels) {
			setPanelBackground(panel, bg);
			panel.repaint();
		}
	}

	private void setPanelBackground(JPanel panel, Color color) {
		panel.setBackground(color);
		for (Component c : panel.getComponents()) {
			if (c instanceof JPanel) {
				setPanelBackground((JPanel) c, color);
			}
		}
	}

	private void openGuidePdf(int guide) {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			try {
				switch (guide) {
					case 0 -> Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2024-03/fftcgrulesheet-en.pdf"));
					case 1 -> Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2025-09/fftcg-comprules-v3.2.1.pdf"));
					case 2 -> Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2024-03/lb-rule-explanation-eg.pdf"));
					case 3 -> Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2024-11/priming-rules-explanation-en.pdf"));
					case 4 -> Desktop.getDesktop().browse(new URI("https://fftcg.cdn.sewest.net/2024-11/priming-supplementary-rules-en.pdf"));
				}
			} catch (IOException | URISyntaxException e1) {
				e1.printStackTrace();
			}
		}
	}

	// -------------------------------------------------------------------------
	// Gradient board panel
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// Grayscale label — auto-converts any icon set on it to grayscale
	// -------------------------------------------------------------------------

	private static class GrayscaleLabel extends JLabel {
		private String url;

		GrayscaleLabel(String text) { super(text); }

		void setUrl(String u) { this.url = u; }
		String getUrl()       { return url; }

		@Override
		public void setIcon(javax.swing.Icon icon) {
			if (icon instanceof ImageIcon) {
				Image src = ((ImageIcon) icon).getImage();
				BufferedImage buf = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
				buf.getGraphics().drawImage(src, 0, 0, null);
				BufferedImage gray = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(buf, null);
				super.setIcon(new ImageIcon(gray));
			} else {
				super.setIcon(icon);
			}
		}
	}

	// -------------------------------------------------------------------------
	// Gradient board panel
	// -------------------------------------------------------------------------

	private class GradientPanel extends JPanel {
		private Color gradientColor;
		private final boolean colorAtTop;

		GradientPanel(boolean colorAtTop) {
			this.colorAtTop = colorAtTop;
		}

		void setGradientColor(Color c) {
			this.gradientColor = c;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Color base = UIManager.getColor("Panel.background");
			Color top    = colorAtTop && gradientColor != null ? gradientColor : base;
			Color bottom = !colorAtTop && gradientColor != null ? gradientColor : base;
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
			g2d.fillRect(0, 0, getWidth(), getHeight());
			g2d.dispose();
		}
	}
}

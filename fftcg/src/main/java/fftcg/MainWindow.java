package fftcg;

import scraper.DeckDatabase;
import scraper.DeckDatabase.DeckCardDetail;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.RescaleOp;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JComboBox;
import javax.swing.JDialog;
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
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

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
	private JLabel p1BreakLabel;
	private GrayscaleLabel p1RemoveLabel;
	private GrayscaleLabel p2RemoveLabel;
	// Zoom popup for LB card hover
	private JWindow zoomPopup;
	// Opening hand confirmation popup
	private JWindow openingHandPopup;
	// Removed from Play confirmation popup
	private JWindow removeConfirmPopup;
	// Hand hover popover (deck zone mouseover)
	private JWindow handPopup;
	private javax.swing.Timer handPopupHideTimer;
	private boolean handCardMenuOpen = false;

	// Backup card state constants
	private static final int BACKUP_NORMAL = 0;
	private static final int BACKUP_DULLED = 1;
	private static final int BACKUP_FROZEN = 2;

	// --- Game state ---
	private final GameState gameState   = new GameState();
	// UI-only state (not owned by GameState)
	private JLabel[]    p1BackupLabels = new JLabel[5];
	private String[]    p1BackupUrls   = new String[5];
	private CardData[]  p1BackupCards  = new CardData[5];
	private int[]       p1BackupStates = new int[5];

	private final List<JLabel>   p1ForwardLabels = new ArrayList<>();
	private final List<String>   p1ForwardUrls;
	private final List<CardData> p1ForwardCards  = new ArrayList<>();
	private final List<Integer>  p1ForwardStates = new ArrayList<>();
	private JPanel p1ForwardPanel;

	private int             p1LbIndex   = 0;

	public static void main(String[] args) {
		Runtime.getRuntime().addShutdownHook(new Thread(ImageCache::shutdown));
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
        this.p1ForwardUrls = new ArrayList<>();
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
		menuBar.add(new FileMenu(frame, this::startGame));
		menuBar.add(new HelpMenu(frame));

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
			@Override public void mousePressed(MouseEvent e)  { addRandomCardToRemoved(p2RemoveLabel); }
			@Override public void mouseEntered(MouseEvent e)  { showGrayscaleZoom(p2RemoveLabel); }
			@Override public void mouseExited(MouseEvent e)   { hideZoom(); }
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

		JPanel p2BackupSlots = buildBackupZonePanel(null);
		JPanel p2BackupWrapper = new JPanel(new GridBagLayout());
		GridBagConstraints p2BackupGbc = new GridBagConstraints();
		p2BackupGbc.anchor = GridBagConstraints.NORTH;
		p2BackupGbc.weighty = 1.0;
		p2BackupGbc.insets = new Insets(0, CARD_W - 8, 0, 0);
		p2BackupWrapper.add(p2BackupSlots, p2BackupGbc);

		JScrollPane p2ForwardZone = buildForwardZonePanel(false);

		JPanel p2HandAligned = new JPanel(new GridBagLayout());
		p2HandAligned.setPreferredSize(new Dimension(2 * CARD_W, CARD_H));
		GridBagConstraints p2HandGbc = new GridBagConstraints();
		p2HandGbc.anchor = GridBagConstraints.NORTHWEST;
		p2HandGbc.weighty = 1.0;
		p2HandAligned.add(buildHandSlot(), p2HandGbc);

		JPanel p2TopRow = new JPanel(new BorderLayout());
		p2TopRow.add(p2BackupWrapper, BorderLayout.CENTER);
		p2TopRow.add(p2HandAligned,   BorderLayout.EAST);

		JPanel p2MainArea = new JPanel(new BorderLayout(0, 4));
		p2MainArea.add(p2TopRow,      BorderLayout.NORTH);
		p2MainArea.add(p2ForwardZone, BorderLayout.SOUTH);

		JPanel p2ZonesPanel = new JPanel(new BorderLayout());
		p2ZonesPanel.add(p2CornerPanel,  BorderLayout.WEST);
		p2ZonesPanel.add(p2MainArea,     BorderLayout.CENTER);
		p2ZonesPanel.add(p2DamagePanel,  BorderLayout.EAST);

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
			public void mousePressed(MouseEvent e) {
				onP1DeckClicked();
			}
		});

		p1BreakLabel = new JLabel("BREAK");
		p1BreakLabel.setToolTipText("Player 1 Break Zone");
		p1BreakLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p1BreakLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		p1BreakLabel.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		p1BreakLabel.setBackground(Color.DARK_GRAY);
		p1BreakLabel.setForeground(Color.WHITE);
		p1BreakLabel.setOpaque(true);
		p1BreakLabel.setPreferredSize(cardSize);
		p1BreakLabel.setMinimumSize(cardSize);

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
			public void mousePressed(MouseEvent e) {
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
			@Override public void mousePressed(MouseEvent e)  { addRandomCardToRemoved(p1RemoveLabel); }
			@Override public void mouseEntered(MouseEvent e)  { showGrayscaleZoom(p1RemoveLabel); }
			@Override public void mouseExited(MouseEvent e)   { hideZoom(); }
		});

		JPanel p1CornerPanel = new JPanel(new GridLayout(2, 2));
		p1CornerPanel.add(p1DeckLabel);
		p1CornerPanel.add(p1LimitLabel);
		p1CornerPanel.add(p1BreakLabel);
		p1CornerPanel.add(lblRemove);

		JPanel p1BackupSlots = buildBackupZonePanel(p1BackupLabels);
		for (int i = 0; i < p1BackupLabels.length; i++) {
			final int backupIdx = i;
			p1BackupLabels[i].addMouseListener(new MouseAdapter() {
				@Override public void mousePressed(MouseEvent e) {
					if (p1BackupLabels[backupIdx].getIcon() != null)
						showBackupContextMenu(backupIdx, p1BackupLabels[backupIdx], e);
				}
			});
		}

		JPanel p1BackupWrapper = new JPanel(new GridBagLayout());
		GridBagConstraints p1BackupGbc = new GridBagConstraints();
		p1BackupGbc.anchor = GridBagConstraints.SOUTH;
		p1BackupGbc.weighty = 1.0;
		p1BackupGbc.insets = new Insets(0, 0, 0, CARD_W - 8);
		p1BackupWrapper.add(p1BackupSlots, p1BackupGbc);

		JScrollPane p1ForwardZone = buildForwardZonePanel(true);

		p1HandLabel = buildHandSlot();
		p1HandLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				if (!gameState.getP1Hand().isEmpty()) showHandPopup();
			}
			@Override
			public void mouseExited(MouseEvent e) {
				scheduleHandPopupHide();
			}
		});

		JPanel p1HandAligned = new JPanel(new GridBagLayout());
		p1HandAligned.setPreferredSize(new Dimension(2 * CARD_W, CARD_H));
		GridBagConstraints p1HandGbc = new GridBagConstraints();
		p1HandGbc.anchor = GridBagConstraints.SOUTHEAST;
		p1HandGbc.weighty = 1.0;
		p1HandAligned.add(p1HandLabel, p1HandGbc);

		JPanel p1BottomRow = new JPanel(new BorderLayout());
		p1BottomRow.add(p1HandAligned,   BorderLayout.WEST);
		p1BottomRow.add(p1BackupWrapper, BorderLayout.CENTER);

		JPanel p1MainArea = new JPanel(new BorderLayout(0, 4));
		p1MainArea.add(p1ForwardZone,  BorderLayout.NORTH);
		p1MainArea.add(p1BottomRow,    BorderLayout.SOUTH);

		JPanel p1ZonesPanel = new JPanel(new BorderLayout());
		p1ZonesPanel.add(p1DamagePanel,  BorderLayout.WEST);
		p1ZonesPanel.add(p1MainArea,     BorderLayout.CENTER);
		p1ZonesPanel.add(p1CornerPanel,  BorderLayout.EAST);

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


		p2ColorBox.addActionListener(e -> {
			String sel = (String) p2ColorBox.getSelectedItem();
			Color c = "Default".equals(sel) ? null : ElementColor.fromName(sel).color;
			applyElementColor(sel, p2ZonesPanel);
			p2Board.setGradientColor(c);
			AppSettings.setP2BoardColor(sel);
			AppSettings.save();
		});
		p1ColorBox.addActionListener(e -> {
			String sel = (String) p1ColorBox.getSelectedItem();
			Color c = "Default".equals(sel) ? null : ElementColor.fromName(sel).color;
			applyElementColor(sel, p1ZonesPanel);
			p1Board.setGradientColor(c);
			AppSettings.setP1BoardColor(sel);
			AppSettings.save();
		});

		p2ColorBox.setSelectedItem(AppSettings.getP2BoardColor());
		p1ColorBox.setSelectedItem(AppSettings.getP1BoardColor());

		// --- Assemble ---
		frame.getContentPane().add(p2ZonesPanel, BorderLayout.NORTH);
		frame.getContentPane().add(southPanel,   BorderLayout.SOUTH);
		frame.getContentPane().add(gameBoard,    BorderLayout.CENTER);
	}

	// -------------------------------------------------------------------------
	// Game startup
	// -------------------------------------------------------------------------

	private void startGame(int deckId) {
		gameState.reset();
		p1LbIndex = 0;
		clearUIZones();
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

				List<CardData> main = new ArrayList<>();
				List<CardData> lb   = new ArrayList<>();
				for (DeckCardDetail card : cards) {
					CardData cd = new CardData(card.imageUrl(), card.name(), card.element(),
							card.cost(), card.type(), card.isLb());
					if (card.isLb()) lb.add(cd);
					else             main.add(cd);
				}
				gameState.initializeDeck(main, lb);
				refreshP1DeckLabel();
				refreshP1LimitLabel();
			}
		}.execute();
	}

	// -------------------------------------------------------------------------
	// P1 deck interaction
	// -------------------------------------------------------------------------

	private void onP1DeckClicked() {
		if (gameState.getP1MainDeck().isEmpty()) return;

		if (gameState.isP1OpeningHandPending()) {
			showOpeningHandConfirm();
			return;
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
		List<CardData> drawn = gameState.drawOpeningHand();
		refreshP1DeckLabel();
		showOpeningHandPopup(drawn, !gameState.isP1MulliganUsed());
	}

	/**
	 * Shows the opening hand popup.
	 *
	 * @param cards             the 5 cards to display
	 * @param mulliganAvailable whether the Mulligan button should be enabled
	 */
	private void showOpeningHandPopup(List<CardData> cards, boolean mulliganAvailable) {
		if (openingHandPopup != null) openingHandPopup.dispose();
		openingHandPopup = new JWindow(frame);

		// Mutable display order — swapped in-place when player reorders
		List<CardData> handOrder = new ArrayList<>(cards);

		// ── Card labels ──────────────────────────────────────────────────────
		@SuppressWarnings("unchecked")
		JLabel[] cardLabels = new JLabel[handOrder.size()];
		int[] selectedIdx = { -1 };  // -1 = nothing selected

		JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));

		for (int i = 0; i < handOrder.size(); i++) {
			final int idx = i;
			JLabel lbl = new JLabel("Loading...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
			lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
			lbl.setOpaque(true);
			lbl.setBackground(Color.DARK_GRAY);
			lbl.setForeground(Color.WHITE);
			lbl.setFont(new Font("Pixel NES", Font.PLAIN, 10));
			lbl.setHorizontalAlignment(SwingConstants.CENTER);

			lbl.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					if (selectedIdx[0] == -1) {
						// Select this card
						selectedIdx[0] = idx;
						cardLabels[idx].setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
					} else if (selectedIdx[0] == idx) {
						// Deselect
						selectedIdx[0] = -1;
						cardLabels[idx].setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
					} else {
						// Swap the two cards
						int other = selectedIdx[0];

						CardData tmpCard = handOrder.get(idx);
						handOrder.set(idx, handOrder.get(other));
						handOrder.set(other, tmpCard);

						javax.swing.Icon tmpIcon = cardLabels[idx].getIcon();
						String tmpText = cardLabels[idx].getText();
						cardLabels[idx].setIcon(cardLabels[other].getIcon());
						cardLabels[idx].setText(cardLabels[other].getText());
						cardLabels[other].setIcon(tmpIcon);
						cardLabels[other].setText(tmpText);

						cardLabels[idx].setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
						cardLabels[other].setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
						selectedIdx[0] = -1;
					}
				}
				@Override
				public void mouseEntered(MouseEvent e) {
					showZoomAt(handOrder.get(idx).imageUrl(), lbl);
				}
				@Override
				public void mouseExited(MouseEvent e) {
					hideZoom();
				}
			});

			cardLabels[i] = lbl;
			cardsPanel.add(lbl);
		}

		// Load card images asynchronously
		for (int i = 0; i < handOrder.size(); i++) {
			final int idx = i;
			final String url = handOrder.get(i).imageUrl();
			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(url);
					return img == null ? null
							: new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
				}
				@Override protected void done() {
					try {
						ImageIcon icon = get();
						if (icon != null) { cardLabels[idx].setIcon(icon); cardLabels[idx].setText(null); }
					} catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();
		}

		// ── Instructions label ───────────────────────────────────────────────
		JLabel instructions = new JLabel(
				"Click a card to select it, then click another to swap positions.",
				SwingConstants.CENTER);
		instructions.setFont(new Font("Pixel NES", Font.PLAIN, 10));

		// ── Buttons ──────────────────────────────────────────────────────────
		JButton keepBtn = new JButton("Keep Hand");
		keepBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		keepBtn.addActionListener(e -> {
			hideZoom();
			openingHandPopup.dispose();
			openingHandPopup = null;
			gameState.keepHand(handOrder);
			// Player 1 goes first: draw 1 card at the start of their first Active phase
			// (subsequent turns draw 2; Player 2 also draws 2 on their first turn)
			gameState.drawToHand(1);
			refreshP1HandLabel();
			refreshP1DeckLabel();
		});

		JButton mulliganBtn = new JButton("Mulligan");
		mulliganBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		mulliganBtn.setEnabled(mulliganAvailable);
		mulliganBtn.setToolTipText(mulliganAvailable
				? "Put these cards on the bottom (in this order) and draw 5 new cards"
				: "Mulligan already used");
		mulliganBtn.addActionListener(e -> {
			hideZoom();
			// handOrder is the player's chosen bottom-of-deck order
			List<CardData> newCards = gameState.mulligan(new ArrayList<>(handOrder));
			refreshP1DeckLabel();
			showOpeningHandPopup(newCards, false);
		});

		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
		buttonsPanel.add(keepBtn);
		buttonsPanel.add(mulliganBtn);

		// ── Assemble ─────────────────────────────────────────────────────────
		JLabel titleLabel = new JLabel("Opening Hand", SwingConstants.CENTER);
		titleLabel.setFont(new Font("Pixel NES", Font.PLAIN, 14));

		JPanel bottomPanel = new JPanel(new BorderLayout(0, 2));
		bottomPanel.add(instructions, BorderLayout.NORTH);
		bottomPanel.add(buttonsPanel,  BorderLayout.SOUTH);

		JPanel mainPanel = new JPanel(new BorderLayout(0, 6));
		mainPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createRaisedBevelBorder(),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		mainPanel.add(titleLabel,  BorderLayout.NORTH);
		mainPanel.add(cardsPanel,  BorderLayout.CENTER);
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);

		openingHandPopup.getContentPane().add(mainPanel);
		openingHandPopup.pack();

		// Centre on screen
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		openingHandPopup.setLocation(
				(screen.width  - openingHandPopup.getWidth())  / 2,
				(screen.height - openingHandPopup.getHeight()) / 2);
		openingHandPopup.setVisible(true);
	}

	private void refreshP1HandLabel() {
		if (gameState.getP1Hand().isEmpty()) {
			p1HandLabel.setIcon(null);
			p1HandLabel.setText("HAND");
			return;
		}
		loadHandLabelAsync(gameState.getP1Hand().get(0).imageUrl());
	}

	private void loadHandLabelAsync(String imageUrl) {
		new SwingWorker<ImageIcon, Void>() {
			@Override
			protected ImageIcon doInBackground() throws Exception {
				Image img = ImageCache.load(imageUrl);
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
		p1DeckLabel.setIcon(gameState.getP1MainDeck().isEmpty() ? null : scaledCardback(new Dimension(CARD_W, CARD_H)));
		p1DeckLabel.setText(gameState.getP1MainDeck().isEmpty() ? "EMPTY" : null);
	}

	/** Resets all interactive UI zones to their empty state for a new game. */
	private void clearUIZones() {
		// Backup slots
		for (int i = 0; i < p1BackupLabels.length; i++) {
			if (p1BackupLabels[i] != null) {
				p1BackupLabels[i].setIcon(null);
				p1BackupLabels[i].setText("BACKUP " + (i + 1));
			}
			p1BackupUrls[i]   = null;
			p1BackupCards[i]  = null;
			p1BackupStates[i] = 0;
		}

		// Forward zone
		if (p1ForwardPanel != null && p1ForwardPanel.getComponentCount() > 1) {
			while (p1ForwardPanel.getComponentCount() > 1) {
				p1ForwardPanel.remove(p1ForwardPanel.getComponentCount() - 1);
			}
			p1ForwardPanel.revalidate();
			p1ForwardPanel.repaint();
		}
		p1ForwardLabels.clear();
		p1ForwardUrls.clear();
		p1ForwardCards.clear();
		p1ForwardStates.clear();

		// Break zone label
		refreshP1BreakLabel();

		// Limit label
		refreshP1LimitLabel();

		// Removed from play labels
		p1RemoveLabel.setIcon(null);
		p1RemoveLabel.setUrl(null);
		p1RemoveLabel.setText("<html><div style='text-align:center'>REMOVED<br>FROM<br>PLAY</div></html>");
		p2RemoveLabel.setIcon(null);
		p2RemoveLabel.setUrl(null);
		p2RemoveLabel.setText("<html><div style='text-align:center'>REMOVED<br>FROM<br>PLAY</div></html>");
	}

	// -------------------------------------------------------------------------
	// P1 LB deck interaction
	// -------------------------------------------------------------------------

	private void onP1LbClicked() {
		if (gameState.getP1LbDeck().isEmpty()) return;
		// Rotate: current card goes to bottom, advance index
		CardData current = gameState.getP1LbDeck().remove(p1LbIndex % gameState.getP1LbDeck().size());
		gameState.getP1LbDeck().add(current); // add to end
		p1LbIndex = 0;                        // new top is index 0 after rotation
		refreshP1LimitLabel();
	}

	private void refreshP1LimitLabel() {
		if (gameState.getP1LbDeck().isEmpty()) {
			p1LimitLabel.setIcon(null);
			p1LimitLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
			p1LimitLabel.setText("LIMIT");
			return;
		}
		String url = gameState.getP1LbDeck().get(0).imageUrl();
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
				Image img = ImageCache.load(imageUrl);
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
				Image img = ImageCache.load(imageUrl);
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

	private void showLbZoom() {
		if (!gameState.getP1LbDeck().isEmpty()) showZoomAt(gameState.getP1LbDeck().get(0).imageUrl(), p1LimitLabel);
	}

	private void showZoomAt(String url, JLabel anchor) {
		if (url == null) return;

		if (zoomPopup == null) zoomPopup = new JWindow(frame);

		new SwingWorker<ImageIcon, Void>() {
			@Override
			protected ImageIcon doInBackground() throws Exception {
				Image img = ImageCache.load(url);
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

	// -------------------------------------------------------------------------
	// Hand hover popover (shown when mousing over the deck zone)
	// -------------------------------------------------------------------------

	private void showHandPopup() {
		cancelHandPopupHide();
		if (handPopup != null && handPopup.isVisible()) return;  // already open

		if (handPopup != null) { handPopup.dispose(); }
		handPopup = new JWindow(frame);

		List<CardData> hand = gameState.getP1Hand();

		JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
		cardsPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createRaisedBevelBorder(),
				BorderFactory.createEmptyBorder(4, 4, 4, 4)));
		cardsPanel.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) { cancelHandPopupHide(); }
			@Override public void mouseExited(MouseEvent e) { scheduleHandPopupHide(); }
		});

		for (int i = 0; i < hand.size(); i++) {
			final int idx = i;
			final CardData card = hand.get(i);
			final String url = card.imageUrl();

			JLabel lbl = new JLabel("Loading...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
			lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true);
			lbl.setBackground(Color.DARK_GRAY);
			lbl.setForeground(Color.WHITE);
			lbl.setFont(new Font("Pixel NES", Font.PLAIN, 10));
			lbl.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
			lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			lbl.addMouseListener(new MouseAdapter() {
				@Override public void mouseEntered(MouseEvent e) {
					cancelHandPopupHide();
					showHandCardZoom(url, lbl);
				}
				@Override public void mouseExited(MouseEvent e) {
					hideZoom();
					scheduleHandPopupHide();
				}
				@Override public void mousePressed(MouseEvent e) {
					onHandPopupCardClicked(idx, card, lbl, e);
				}
			});

			// Load image async
			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(url);
					return img == null ? null
							: new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
				}
				@Override protected void done() {
					try {
						ImageIcon icon = get();
						if (icon != null) { lbl.setIcon(icon); lbl.setText(null); }
					} catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();

			cardsPanel.add(lbl);
		}

		handPopup.getContentPane().add(cardsPanel);
		handPopup.pack();

		// Position above the hand label, left-aligned to its left edge
		Point loc = p1HandLabel.getLocationOnScreen();
		int x = loc.x;
		int y = loc.y - handPopup.getHeight() - 4;
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		x = Math.max(0, Math.min(x, screen.width  - handPopup.getWidth()));
		y = Math.max(0, Math.min(y, screen.height - handPopup.getHeight()));
		handPopup.setLocation(x, y);
		handPopup.setVisible(true);
	}

	/** Dismisses the hand popover after a short delay (cancelled if mouse re-enters). */
	private void scheduleHandPopupHide() {
		if (handCardMenuOpen) return;
		if (handPopupHideTimer != null) handPopupHideTimer.stop();
		handPopupHideTimer = new javax.swing.Timer(120, e -> {
			if (handPopup != null) { handPopup.dispose(); handPopup = null; }
			handPopupHideTimer = null;
		});
		handPopupHideTimer.setRepeats(false);
		handPopupHideTimer.start();
	}

	private void cancelHandPopupHide() {
		if (handPopupHideTimer != null) { handPopupHideTimer.stop(); handPopupHideTimer = null; }
	}

	private void onHandPopupCardClicked(int handIdx, CardData card, JLabel cardLabel, MouseEvent e) {
		cancelHandPopupHide();
		handCardMenuOpen = true;

		JPopupMenu menu = new JPopupMenu();

		JMenuItem playItem = new JMenuItem("Play");
		playItem.setEnabled(canAffordCard(card, handIdx) && (!card.isBackup() || hasAvailableBackupSlot()));
		playItem.addActionListener(ae -> {
			hideZoom();
			if (handPopup != null) { handPopup.dispose(); handPopup = null; }
			showPaymentDialog(card, handIdx);
		});
		menu.add(playItem);

		menu.addPopupMenuListener(new PopupMenuListener() {
			@Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
			@Override public void popupMenuCanceled(PopupMenuEvent e) {
				handCardMenuOpen = false;
			}
			@Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
				handCardMenuOpen = false;
				scheduleHandPopupHide();
			}
		});

		menu.show(cardLabel, e.getX(), e.getY());
	}

	/** Shows a full-resolution preview of a hand card to the left of the hand popover. */
	private void showHandCardZoom(String url, JLabel anchor) {
		if (url == null) return;
		if (zoomPopup == null) zoomPopup = new JWindow(frame);

		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image img = ImageCache.load(url);
				return img == null ? null : new ImageIcon(img);
			}
			@Override protected void done() {
				try {
					ImageIcon icon = get();
					if (icon == null) return;
					JLabel zl = new JLabel(icon);
					zl.setBorder(BorderFactory.createRaisedBevelBorder());
					zoomPopup.getContentPane().removeAll();
					zoomPopup.getContentPane().add(zl);
					zoomPopup.pack();

					// Position to the left of the hand popover window
					Point base = (handPopup != null && handPopup.isVisible())
							? handPopup.getLocationOnScreen()
							: anchor.getLocationOnScreen();
					int x = base.x - icon.getIconWidth() - 6;
					int y = base.y;
					Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
					x = Math.max(0, x);
					y = Math.max(0, Math.min(y, screen.height - icon.getIconHeight()));
					zoomPopup.setLocation(x, y);
					zoomPopup.setVisible(true);
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	private void refreshP1BreakLabel() {
		List<CardData> zone = gameState.getP1BreakZone();
		if (zone.isEmpty()) {
			p1BreakLabel.setIcon(null);
			p1BreakLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
			p1BreakLabel.setText("BREAK");
			return;
		}
		String url = zone.get(zone.size() - 1).imageUrl();
		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image img = ImageCache.load(url);
				return img == null ? null
						: new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
			}
			@Override protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) { p1BreakLabel.setIcon(icon); p1BreakLabel.setText(null); }
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	// -------------------------------------------------------------------------
	// Play / Payment
	// -------------------------------------------------------------------------

	/**
	 * Returns true if the player can theoretically afford to play {@code card}
	 * by combining existing CP with potential discards from hand.
	 * {@code excludeHandIdx} is the index of the card being played (not available
	 * for discard).
	 */
	private boolean canAffordCard(CardData card, int excludeHandIdx) {
		int existing = gameState.getP1CpForElement(card.element());
		int canGenerate = 0;
		List<CardData> hand = gameState.getP1Hand();
		for (int i = 0; i < hand.size(); i++) {
			if (i == excludeHandIdx) continue;
			CardData h = hand.get(i);
			if (!h.isLightOrDark() && card.element().equalsIgnoreCase(h.element()))
				canGenerate += 2;
		}
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] != null && p1BackupStates[i] == BACKUP_NORMAL
					&& card.element().equalsIgnoreCase(p1BackupCards[i].element()))
				canGenerate += 1;
		}
		return existing + canGenerate >= card.cost();
	}

	/** Returns true if at least one P1 backup slot is currently empty. */
	private boolean hasAvailableBackupSlot() {
		if (p1BackupLabels == null) return false;
		for (JLabel slot : p1BackupLabels) {
			if (slot != null && slot.getIcon() == null) return true;
		}
		return false;
	}

	/**
	 * Opens a modal payment dialog where the player selects backups to dull (1 CP each)
	 * and/or hand cards to discard (2 CP each) to cover the cost of {@code card}.
	 *
	 * Constraints enforced:
	 *   - Backups may not cause total CP to exceed the cost (no overpay via backups).
	 *   - Discards may cause total CP to exceed cost by at most 1 (unavoidable with 2-CP increments).
	 *   - Both gates reduce to: an item can only be added when current total is still below cost.
	 */
	private void showPaymentDialog(CardData card, int handIdx) {
		JDialog dlg = new JDialog(frame, "Play " + card.name(), true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		List<CardData> hand   = gameState.getP1Hand();
		String         elem   = card.element();
		int            cost   = card.cost();
		int            bankCp = gameState.getP1CpForElement(elem);

		List<Integer> selectedBackups  = new ArrayList<>();
		List<Integer> selectedDiscards = new ArrayList<>();

		// Collect eligible (undulled, matching-element) backup slot indices
		List<Integer> eligibleBackupSlots = new ArrayList<>();
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] != null && p1BackupStates[i] == BACKUP_NORMAL
					&& elem.equalsIgnoreCase(p1BackupCards[i].element()))
				eligibleBackupSlots.add(i);
		}

		JLabel cpLabel = new JLabel();
		cpLabel.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cpLabel.setHorizontalAlignment(SwingConstants.CENTER);

		JButton confirmBtn = new JButton("Confirm");
		confirmBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));

		// Labels tracked for visual refresh; parallel lists with their slot/hand indices
		List<JLabel>   backupLbls  = new ArrayList<>();
		List<Integer>  backupSlots = new ArrayList<>();
		List<JLabel>   discardLbls  = new ArrayList<>();
		List<Integer>  discardIdxs  = new ArrayList<>();

		// Refreshes CP counter, Confirm state, and the clickable appearance of every card label.
		// An unselected item is active (hand cursor, bright border) only when total < cost,
		// ensuring backups never overpay and discards overpay by at most 1.
		Runnable updateAll = () -> {
			int total      = bankCp + selectedBackups.size() + selectedDiscards.size() * 2;
			boolean canAdd = total < cost;
			cpLabel.setText("CP: " + total + " / " + cost + "  (" + elem + ")");
			confirmBtn.setEnabled(total >= cost);
			for (int i = 0; i < backupLbls.size(); i++) {
				JLabel  lbl      = backupLbls.get(i);
				boolean selected = selectedBackups.contains(backupSlots.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(
						selected ? Color.YELLOW : (canAdd ? Color.GRAY : new Color(80, 80, 80)),
						selected ? 3 : 1));
				lbl.setBackground(selected || canAdd ? Color.DARK_GRAY : new Color(50, 50, 50));
				lbl.setCursor(selected || canAdd
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
			for (int i = 0; i < discardLbls.size(); i++) {
				JLabel  lbl      = discardLbls.get(i);
				boolean selected = selectedDiscards.contains(discardIdxs.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(
						selected ? Color.YELLOW : (canAdd ? Color.GRAY : new Color(80, 80, 80)),
						selected ? 3 : 1));
				lbl.setBackground(selected || canAdd ? Color.DARK_GRAY : new Color(50, 50, 50));
				lbl.setCursor(selected || canAdd
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
		};
		updateAll.run();

		// ── Backup section ───────────────────────────────────────────────────
		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

		if (!eligibleBackupSlots.isEmpty()) {
			JLabel backupHeader = new JLabel("Backups — dull for 1 CP each:");
			backupHeader.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			backupHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
			JPanel backupCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
			backupCardsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

			for (int slot : eligibleBackupSlots) {
				JLabel lbl = new JLabel("...", SwingConstants.CENTER);
				lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
				lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
				lbl.setOpaque(true);
				lbl.setBackground(Color.DARK_GRAY);
				lbl.setForeground(Color.WHITE);
				lbl.setFont(new Font("Pixel NES", Font.PLAIN, 10));
				lbl.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
				lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mousePressed(MouseEvent e) {
						int total = bankCp + selectedBackups.size() + selectedDiscards.size() * 2;
						if (selectedBackups.remove(Integer.valueOf(slot))) {
							// deselect always allowed
						} else if (total < cost) {
							selectedBackups.add(slot);
						}
						updateAll.run();
					}
				});
				final String url = p1BackupUrls[slot];
				new SwingWorker<ImageIcon, Void>() {
					@Override protected ImageIcon doInBackground() throws Exception {
						Image img = ImageCache.load(url);
						return img == null ? null
								: new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
					}
					@Override protected void done() {
						try {
							ImageIcon icon = get();
							if (icon != null) { lbl.setIcon(icon); lbl.setText(null); }
						} catch (InterruptedException | ExecutionException ignored) {}
					}
				}.execute();
				backupLbls.add(lbl);
				backupSlots.add(slot);
				backupCardsPanel.add(lbl);
			}
			centerPanel.add(backupHeader);
			centerPanel.add(backupCardsPanel);
		}

		// ── Hand discard section ─────────────────────────────────────────────
		JLabel discardHeader = new JLabel("Hand — discard for 2 CP each:");
		discardHeader.setFont(new Font("Pixel NES", Font.PLAIN, 9));
		discardHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel discardCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
		discardCardsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		for (int i = 0; i < hand.size(); i++) {
			if (i == handIdx) continue;
			final int hi   = i;
			CardData  hc   = hand.get(i);
			boolean payable = !hc.isLightOrDark() && elem.equalsIgnoreCase(hc.element());

			JLabel lbl = new JLabel("...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
			lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true);
			lbl.setBackground(payable ? Color.DARK_GRAY : new Color(50, 50, 50));
			lbl.setForeground(Color.WHITE);
			lbl.setFont(new Font("Pixel NES", Font.PLAIN, 10));
			lbl.setBorder(BorderFactory.createLineBorder(payable ? Color.GRAY : new Color(80, 80, 80), 1));
			lbl.setCursor(payable
					? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());

			if (payable) {
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mousePressed(MouseEvent e) {
						int total = bankCp + selectedBackups.size() + selectedDiscards.size() * 2;
						if (selectedDiscards.remove(Integer.valueOf(hi))) {
							// deselect always allowed
						} else if (total < cost) {
							selectedDiscards.add(hi);
						}
						updateAll.run();
					}
				});
				discardLbls.add(lbl);
				discardIdxs.add(hi);
			}

			final String  imgUrl = hc.imageUrl();
			final boolean grey   = !payable;
			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(imgUrl);
					if (img == null) return null;
					if (grey) {
						BufferedImage buf = new BufferedImage(img.getWidth(null), img.getHeight(null),
								BufferedImage.TYPE_INT_ARGB);
						buf.getGraphics().drawImage(img, 0, 0, null);
						return new ImageIcon(
								new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null)
										.filter(buf, null));
					}
					return new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
				}
				@Override protected void done() {
					try {
						ImageIcon icon = get();
						if (icon != null) { lbl.setIcon(icon); lbl.setText(null); }
					} catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();
			discardCardsPanel.add(lbl);
		}
		centerPanel.add(discardHeader);
		centerPanel.add(discardCardsPanel);

		// ── Hint ─────────────────────────────────────────────────────────────
		JLabel hint = new JLabel(
				"<html><center>Backups: dull for 1 CP. Hand cards (" + elem
				+ ", non-Light/Dark): discard for 2 CP.</center></html>",
				SwingConstants.CENTER);
		hint.setFont(new Font("Pixel NES", Font.PLAIN, 9));

		// ── Buttons ──────────────────────────────────────────────────────────
		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cancelBtn.addActionListener(e -> dlg.dispose());

		confirmBtn.addActionListener(e -> {
			dlg.dispose();
			executePlay(card, handIdx, new ArrayList<>(selectedDiscards), new ArrayList<>(selectedBackups));
		});

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
		buttonPanel.add(confirmBtn);
		buttonPanel.add(cancelBtn);

		// ── Assemble ─────────────────────────────────────────────────────────
		JLabel titleLabel = new JLabel(
				"Pay for: " + card.name() + "  (Cost " + cost + " " + elem + " CP)",
				SwingConstants.CENTER);
		titleLabel.setFont(new Font("Pixel NES", Font.PLAIN, 11));

		JPanel topPanel = new JPanel(new BorderLayout(0, 4));
		topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
		topPanel.add(titleLabel, BorderLayout.NORTH);
		topPanel.add(cpLabel,    BorderLayout.CENTER);
		topPanel.add(hint,       BorderLayout.SOUTH);

		JPanel mainPanel = new JPanel(new BorderLayout(0, 4));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
		mainPanel.add(new JScrollPane(centerPanel), BorderLayout.CENTER);
		mainPanel.add(buttonPanel,                  BorderLayout.SOUTH);

		dlg.getContentPane().setLayout(new BorderLayout());
		dlg.getContentPane().add(topPanel,  BorderLayout.NORTH);
		dlg.getContentPane().add(mainPanel, BorderLayout.CENTER);

		dlg.pack();
		dlg.setLocationRelativeTo(frame);
		dlg.setVisible(true);
	}

	/**
	 * Executes the play: dulls selected backups, discards payment cards (high-index
	 * first to preserve indices), adds the generated CP to the bank, spends the cost,
	 * removes the played card from hand, and places it in the appropriate zone.
	 */
	private void executePlay(CardData card, int cardHandIdx,
			List<Integer> discardIndices, List<Integer> backupDullIndices) {
		for (int bi : backupDullIndices) {
			p1BackupStates[bi] = BACKUP_DULLED;
			refreshP1BackupSlot(bi);
			gameState.addP1Cp(card.element(), 1);
		}
		discardIndices.sort(Collections.reverseOrder());
		for (int di : discardIndices) {
			gameState.addP1Cp(card.element(), 2);
			gameState.discardFromHand(di);
			if (di < cardHandIdx) cardHandIdx--;
		}
		gameState.spendP1Cp(card.element(), card.cost());
		gameState.clearP1Cp(card.element());
		gameState.removeFromHand(cardHandIdx);

		if (card.isBackup()) {
			placeCardInFirstBackupSlot(card);
		} else if (card.isForward()) {
			placeCardInForwardZone(card);
		}

		refreshP1HandLabel();
		refreshP1BreakLabel();
	}

	/** Places a card into the first empty P1 backup slot and renders it. */
	private void placeCardInFirstBackupSlot(CardData card) {
		if (p1BackupLabels == null) return;
		for (int i = 0; i < p1BackupLabels.length; i++) {
			if (p1BackupLabels[i] == null || p1BackupLabels[i].getIcon() != null) continue;
			p1BackupUrls[i]   = card.imageUrl();
			p1BackupCards[i]  = card;
			p1BackupStates[i] = BACKUP_NORMAL;
			refreshP1BackupSlot(i);
			break;
		}
	}

	/** Reloads and re-renders a single P1 backup slot using its stored URL and state. */
	private void refreshP1BackupSlot(int idx) {
		String url  = p1BackupUrls[idx];
		int    state = p1BackupStates[idx];
		JLabel slot  = p1BackupLabels[idx];
		if (url == null || slot == null) return;
		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				if (raw == null) return null;
				BufferedImage card = toARGB(raw, CARD_W, CARD_H);
				return new ImageIcon(renderBackupCard(card, state));
			}
			@Override protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) { slot.setIcon(icon); slot.setText(null); }
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	/**
	 * Composites a (possibly transformed) card image onto a square
	 * {@code CARD_H × CARD_H} canvas, respecting the slot alignment rules:
	 * <ul>
	 *   <li>Normal / Frozen — upright (or flipped) card pinned to the left edge, top</li>
	 *   <li>Dulled — card rotated 90° CW ({@code CARD_H × CARD_W}), pinned left + bottom</li>
	 * </ul>
	 */
	private static BufferedImage renderBackupCard(BufferedImage card, int state) {
		BufferedImage canvas = new BufferedImage(CARD_H, CARD_H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		switch (state) {
			case BACKUP_DULLED -> {
				BufferedImage rotated = rotateCW90(card);          // now CARD_H × CARD_W
				g.drawImage(rotated, 0, CARD_H - CARD_W, null);   // pinned to bottom-left
			}
			case BACKUP_FROZEN -> {
				BufferedImage flipped = rotate180(card);
				g.drawImage(applyBlueTint(flipped), 0, 0, null);  // pinned to top-left
			}
			default -> g.drawImage(card, 0, 0, null);             // pinned to top-left
		}
		g.dispose();
		return canvas;
	}

	/** Converts any {@link Image} to a scaled {@link BufferedImage} (ARGB). */
	private static BufferedImage toARGB(Image src, int w, int h) {
		BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = buf.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return buf;
	}

	/** Rotates a {@link BufferedImage} 90° clockwise. Result dimensions are {@code h × w}. */
	private static BufferedImage rotateCW90(BufferedImage src) {
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dst.createGraphics();
		g.translate(h, 0);
		g.rotate(Math.PI / 2);
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return dst;
	}

	/** Rotates a {@link BufferedImage} 180°. */
	private static BufferedImage rotate180(BufferedImage src) {
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dst.createGraphics();
		g.translate(w, h);
		g.rotate(Math.PI);
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return dst;
	}

	/** Applies a blue tint to a {@link BufferedImage} (darkens R/G, boosts B). */
	private static BufferedImage applyBlueTint(BufferedImage src) {
		float[] scales  = { 0.4f, 0.4f, 1.0f, 1.0f };
		float[] offsets = { 0f,   0f,   60f,  0f   };
		return new RescaleOp(scales, offsets, null).filter(src, null);
	}

	/**
	 * Shows a debug context menu for a P1 backup slot.
	 * Only visible when Debug Mode is enabled.
	 */
	private void showBackupContextMenu(int idx, JLabel slot, MouseEvent e) {
		if (!AppSettings.isDebugMode()) return;
		JPopupMenu menu = new JPopupMenu();

		JMenuItem dullItem = new JMenuItem("Debug: Dull");
		dullItem.addActionListener(ae -> {
			p1BackupStates[idx] = (p1BackupStates[idx] == BACKUP_DULLED) ? BACKUP_NORMAL : BACKUP_DULLED;
			refreshP1BackupSlot(idx);
		});
		menu.add(dullItem);

		JMenuItem freezeItem = new JMenuItem("Debug: Freeze");
		freezeItem.addActionListener(ae -> {
			p1BackupStates[idx] = (p1BackupStates[idx] == BACKUP_FROZEN) ? BACKUP_NORMAL : BACKUP_FROZEN;
			refreshP1BackupSlot(idx);
		});
		menu.add(freezeItem);

		menu.show(slot, e.getX(), e.getY());
	}

	private void addRandomCardToRemoved(GrayscaleLabel removeLabel) {
		if (!AppSettings.isDebugMode()) return;
		if (removeConfirmPopup != null) { removeConfirmPopup.dispose(); }
		removeConfirmPopup = new JWindow(frame);

		JButton yesBtn = new JButton("Debug: Add Card to Removed from Play");
		yesBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		yesBtn.addActionListener(e -> {
			removeConfirmPopup.dispose();
			removeConfirmPopup = null;
			doAddRandomCardToRemoved(removeLabel);
		});

		JButton noBtn = new JButton("Cancel");
		noBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		noBtn.addActionListener(e -> {
			removeConfirmPopup.dispose();
			removeConfirmPopup = null;
		});

		JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
		panel.setBorder(BorderFactory.createRaisedBevelBorder());
		panel.add(yesBtn);
		panel.add(noBtn);

		removeConfirmPopup.getContentPane().add(panel);
		removeConfirmPopup.pack();

		Point loc = removeLabel.getLocationOnScreen();
		removeConfirmPopup.setLocation(loc.x - removeConfirmPopup.getWidth() - 6, loc.y);
		removeConfirmPopup.setVisible(true);
	}

	private void doAddRandomCardToRemoved(GrayscaleLabel removeLabel) {
		List<CardData> pool = new ArrayList<>(gameState.getP1MainDeck());
		pool.addAll(gameState.getP1Hand());
		if (pool.isEmpty()) return;
		String url = pool.get((int) (Math.random() * pool.size())).imageUrl();
		new SwingWorker<ImageIcon, Void>() {
			@Override
			protected ImageIcon doInBackground() throws Exception {
				Image img = ImageCache.load(url);
				return img == null ? null : new ImageIcon(
						img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
			}
			@Override
			protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) {
						removeLabel.setText(null);
						removeLabel.setIcon(icon);
						removeLabel.setUrl(url);
					}
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	private void showGrayscaleZoom(GrayscaleLabel label) {
		String url = label.getUrl();
		if (url == null) return;

		if (zoomPopup == null) zoomPopup = new JWindow(frame);

		new SwingWorker<ImageIcon, Void>() {
			@Override
			protected ImageIcon doInBackground() throws Exception {
				Image img = ImageCache.load(url);
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
	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Builds a damage zone panel for one player.
	 * Contains 7 slots (D, A, M, A, G, E, Px) stacked vertically,
	 * each sized to hold a sideways card (CARD_H wide × CARD_W tall).
	 * The color dropdown sits below the slots.
	 */
	/**
	 * @param labelStorage if non-null, the 5 created slot labels are stored here (index 0-4)
	 */
	/**
	 * Builds the Forward zone: a horizontally-scrollable row of card slots.
	 * Pass {@code true} for P1 to store a reference for dynamic card placement.
	 */
	private JScrollPane buildForwardZonePanel(boolean isP1) {
		JLabel forwardTag = new JLabel("FORWARDS", SwingConstants.CENTER);
		forwardTag.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		forwardTag.setBorder(BorderFactory.createEmptyBorder());
		forwardTag.setBackground(Color.LIGHT_GRAY);
		forwardTag.setForeground(Color.DARK_GRAY);
		forwardTag.setOpaque(true);

		JPanel inner = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
			@Override
			public Dimension getPreferredSize() {
				int cardSlots = Math.max(getComponentCount() - 1, 0);
				int tagW  = forwardTag.getPreferredSize().width;
				int slotW = CARD_H;
				int gap   = 4;
				int width = gap + tagW + gap + (slotW + gap) * cardSlots;
				return new Dimension(Math.max(width, tagW + gap * 2), CARD_H);
			}
		};
		inner.setBackground(Color.LIGHT_GRAY);
		inner.add(forwardTag);

		if (isP1) {
			p1ForwardPanel = inner;
		}

		JScrollPane scroll = new JScrollPane(inner,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setPreferredSize(new Dimension(0, CARD_H));
		return scroll;
	}

	/** Adds a Forward card to P1's forward zone and wires up the debug context menu. */
	private void placeCardInForwardZone(CardData card) {
		if (p1ForwardPanel == null) return;
		int idx = p1ForwardLabels.size();

		JLabel lbl = new JLabel("", SwingConstants.CENTER);
		lbl.setPreferredSize(new Dimension(CARD_H, CARD_H));
		lbl.setMinimumSize(new Dimension(CARD_H, CARD_H));
		lbl.setOpaque(true);
		lbl.setBackground(Color.LIGHT_GRAY);
		lbl.setForeground(Color.DARK_GRAY);
		lbl.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		lbl.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lbl.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) {
				if (lbl.getIcon() != null) showForwardContextMenu(idx, lbl, e);
			}
		});

		p1ForwardUrls.add(card.imageUrl());
		p1ForwardCards.add(card);
		p1ForwardStates.add(BACKUP_NORMAL);
		p1ForwardLabels.add(lbl);

		p1ForwardPanel.add(lbl);
		p1ForwardPanel.revalidate();
		p1ForwardPanel.repaint();

		refreshP1ForwardSlot(idx);
	}

	/** Reloads and re-renders a single P1 forward slot using its stored URL and state. */
	private void refreshP1ForwardSlot(int idx) {
		String url   = p1ForwardUrls.get(idx);
		int    state = p1ForwardStates.get(idx);
		JLabel slot  = p1ForwardLabels.get(idx);
		if (url == null) return;
		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				if (raw == null) return null;
				BufferedImage card = toARGB(raw, CARD_W, CARD_H);
				return new ImageIcon(renderBackupCard(card, state));
			}
			@Override protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) { slot.setIcon(icon); slot.setText(null); }
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	/** Shows a debug context menu for a P1 forward slot. */
	private void showForwardContextMenu(int idx, JLabel slot, MouseEvent e) {
		if (!AppSettings.isDebugMode()) return;
		JPopupMenu menu = new JPopupMenu();

		JMenuItem dullItem = new JMenuItem("Debug: Dull");
		dullItem.addActionListener(ae -> {
			p1ForwardStates.set(idx,
					p1ForwardStates.get(idx) == BACKUP_DULLED ? BACKUP_NORMAL : BACKUP_DULLED);
			refreshP1ForwardSlot(idx);
		});
		menu.add(dullItem);

		JMenuItem freezeItem = new JMenuItem("Debug: Freeze");
		freezeItem.addActionListener(ae -> {
			p1ForwardStates.set(idx,
					p1ForwardStates.get(idx) == BACKUP_FROZEN ? BACKUP_NORMAL : BACKUP_FROZEN);
			refreshP1ForwardSlot(idx);
		});
		menu.add(freezeItem);

		menu.show(slot, e.getX(), e.getY());
	}

	private JPanel buildBackupZonePanel(JLabel[] labelStorage) {
		JPanel slotsPanel = new JPanel(new GridLayout(1, 5, 2, 0));
		slotsPanel.setBackground(Color.LIGHT_GRAY);
		for (int i = 0; i < 5; i++) {
			JLabel slot = new JLabel("BACKUP " + (i + 1), SwingConstants.CENTER);
			slot.setFont(new Font("Pixel NES", Font.PLAIN, 11));
			slot.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
			slot.setBackground(Color.LIGHT_GRAY);
			slot.setForeground(Color.DARK_GRAY);
			slot.setOpaque(true);
			slot.setPreferredSize(new Dimension(CARD_H, CARD_H));
			slot.setMinimumSize(new Dimension(CARD_H, CARD_H));
			if (labelStorage != null) labelStorage[i] = slot;
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

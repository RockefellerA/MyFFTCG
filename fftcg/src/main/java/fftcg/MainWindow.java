package fftcg;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.color.ColorSpace;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.RescaleOp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.SoftBevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import scraper.DeckDatabase;
import scraper.DeckDatabase.DeckCardDetail;

public class MainWindow {

	private JFrame frame;

	// Card size constants
	private static final int CARD_W = 140;
	private static final int CARD_H = 205;

	// Side info panel dimensions.
	// The panel is sized to the native card-image width on the first hover;
	// these are just the fallback values used before any image loads.
	private static final int SIDE_MARGIN   = 4;                   // px between card and panel edge
	private static final double PREVIEW_SCALE = 0.8;
	private int sidePanelW = (int)(3 * CARD_W * PREVIEW_SCALE);   // updated on first image load
	private int previewH   =
			(int)(sidePanelW * (double) CARD_H / CARD_W);         // updated on first image load
	private boolean previewSized = false;

	// P1 zone labels that change during gameplay
	private JLabel p1DeckLabel;
	private JLabel p1LimitLabel;
	private JPanel handPanel;
	private JLabel p1BreakLabel;
	private GrayscaleLabel p1RemoveLabel;
	private GrayscaleLabel p2RemoveLabel;
	// Game event log
	private JTextArea gameLog;
	// Side info panel (card preview + Next button + game log)
	private JPanel        sidePanel;
	private JPanel        cardPreviewPanel;   // custom-painted card preview
	private BufferedImage previewImage;       // current card to draw (null = empty)
	private float         previewAlpha  = 0f; // 0 = transparent, 1 = fully opaque
	private javax.swing.Timer fadeTimer;      // drives fade-in / fade-out animation
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
	private final Set<Integer> spentLbIndices = new HashSet<>();

	// Damage zone UI
	private JPanel   p1DamageSlotPanel;
	private JPanel[] p1DamageSlots = new JPanel[7];

	// Next-phase button and its glow animation
	private JButton              nextPhaseButton;
	private javax.swing.Timer    glowTimer;
	private final float[]        glowAngle = { 0f };

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
		frame.setResizable(false);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		frame.getContentPane().setLayout(new BorderLayout());

		// --- Menu Bar ---
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);
		menuBar.add(new FileMenu(frame, this::startGame,
				() -> applySidePanelSide(AppSettings.getSidePanelSide())));
		menuBar.add(new HelpMenu(frame));

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

		JLabel deck_1 = new JLabel("DECK");
		deck_1.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		deck_1.setToolTipText("Player 2 Deck");
		deck_1.setHorizontalAlignment(SwingConstants.CENTER);
		deck_1.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		deck_1.setBackground(Color.DARK_GRAY);
		deck_1.setForeground(Color.WHITE);
		deck_1.setOpaque(true);

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
		p2BackupWrapper.add(p2BackupSlots, p2BackupGbc);

		JScrollPane p2ForwardZone = buildForwardZonePanel(false);

		JPanel p2TopRow = new JPanel(new BorderLayout());
		p2TopRow.add(p2BackupWrapper, BorderLayout.CENTER);

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
		p1DeckLabel = new JLabel("DECK");
		p1DeckLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		p1DeckLabel.setToolTipText("Player 1 Deck");
		p1DeckLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p1DeckLabel.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		p1DeckLabel.setBackground(Color.DARK_GRAY);
		p1DeckLabel.setForeground(Color.WHITE);
		p1DeckLabel.setOpaque(true);
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
		p1BreakLabel.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) {
				List<CardData> zone = gameState.getP1BreakZone();
				if (!zone.isEmpty()) showZoomAt(zone.get(zone.size() - 1).imageUrl(), p1BreakLabel);
			}
			@Override public void mouseExited(MouseEvent e) { hideZoom(); }
			@Override public void mousePressed(MouseEvent e) {
				if (!gameState.getP1BreakZone().isEmpty()) { hideZoom(); showBreakZoneDialog(); }
			}
		});

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
				GameState.GamePhase phase = gameState.getCurrentPhase();
				boolean isMainPhase = phase == GameState.GamePhase.MAIN_1
						|| phase == GameState.GamePhase.MAIN_2;
				if (!gameState.getP1LbDeck().isEmpty() && isMainPhase && !gameState.isP1GameOver()) showLbDialog();
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
				@Override public void mouseEntered(MouseEvent e) {
					if (p1BackupLabels[backupIdx].getIcon() != null)
						showZoomAt(p1BackupUrls[backupIdx], p1BackupLabels[backupIdx]);
				}
				@Override public void mouseExited(MouseEvent e) { hideZoom(); }
			});
		}

		JPanel p1BackupWrapper = new JPanel(new GridBagLayout());
		GridBagConstraints p1BackupGbc = new GridBagConstraints();
		p1BackupGbc.anchor = GridBagConstraints.SOUTH;
		p1BackupGbc.weighty = 1.0;
		p1BackupWrapper.add(p1BackupSlots, p1BackupGbc);

		JScrollPane p1ForwardZone = buildForwardZonePanel(true);

		// --- Next Phase Button ---
		nextPhaseButton = new JButton("<html><center>NEXT<br>&#9658;</center></html>");
		nextPhaseButton.setFont(new Font("Pixel NES", Font.PLAIN, 14));
		nextPhaseButton.setEnabled(false);
		nextPhaseButton.setFocusPainted(false);
		nextPhaseButton.addActionListener(e -> onNextPhase());

		// Pulsing glow border — runs continuously, only paints when enabled
		glowTimer = new javax.swing.Timer(40, e -> {
			if (nextPhaseButton == null || !nextPhaseButton.isEnabled()) return;
			glowAngle[0] += 0.09f;
			float t = (float)(0.5 + 0.5 * Math.sin(glowAngle[0]));
			int r = (int)(180 + t * 75);   // 180–255
			int g = (int)(110 + t * 80);   // 110–190
			nextPhaseButton.setBorder(BorderFactory.createLineBorder(
					new Color(Math.min(r, 255), Math.min(g, 255), 20), 3, true));
		});
		glowTimer.start();

		JPanel p1BottomRow = new JPanel(new BorderLayout());
		p1BottomRow.add(p1BackupWrapper, BorderLayout.CENTER);

		JPanel p1MainArea = new JPanel(new BorderLayout(0, 4));
		p1MainArea.add(p1ForwardZone,  BorderLayout.NORTH);
		p1MainArea.add(p1BottomRow,    BorderLayout.SOUTH);

		// Damage panel on the left, hand slot flush against its right edge at the bottom
		JPanel p1LeftGroup = new JPanel(new GridBagLayout());
		GridBagConstraints lgbc = new GridBagConstraints();
		lgbc.gridx = 0; lgbc.gridy = 0;
		lgbc.fill = GridBagConstraints.BOTH;
		lgbc.weighty = 1.0;
		p1LeftGroup.add(p1DamagePanel, lgbc);

		JPanel p1ZonesPanel = new JPanel(new BorderLayout());
		p1ZonesPanel.add(p1LeftGroup,    BorderLayout.WEST);
		p1ZonesPanel.add(p1MainArea,     BorderLayout.CENTER);
		p1ZonesPanel.add(p1CornerPanel,  BorderLayout.EAST);

		JPanel southPanel = new JPanel(new BorderLayout());
		southPanel.add(p1ZonesPanel, BorderLayout.CENTER);

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

		// --- Side Panel (card preview + Next button + Game Log) ---

		// Card preview — custom-painted panel that draws previewImage at native size
		cardPreviewPanel = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (previewImage != null && previewAlpha > 0f) {
					Graphics2D g2 = (Graphics2D) g;
					g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
							RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, previewAlpha));
					int m = SIDE_MARGIN / 2;
					g2.drawImage(previewImage,
							m, m, getWidth() - m, getHeight() - m,
							0, 0, previewImage.getWidth(), previewImage.getHeight(), null);
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
				}
			}
		};
		cardPreviewPanel.setPreferredSize(new Dimension(sidePanelW, previewH));
		cardPreviewPanel.setMinimumSize (new Dimension(sidePanelW, previewH));
		cardPreviewPanel.setMaximumSize (new Dimension(sidePanelW, previewH));
		cardPreviewPanel.setBackground(Color.DARK_GRAY);
		cardPreviewPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));

		// Next-phase button, centred below the preview
		JPanel nextBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
		nextBtnPanel.add(nextPhaseButton);

		JPanel sideNorth = new JPanel();
		sideNorth.setLayout(new BoxLayout(sideNorth, BoxLayout.Y_AXIS));
		sideNorth.add(cardPreviewPanel);
		sideNorth.add(nextBtnPanel);

		// Game log (scrollable, fills the rest of the side panel)
		gameLog = new JTextArea();
		gameLog.setEditable(false);
		gameLog.setLineWrap(true);
		gameLog.setWrapStyleWord(true);
		gameLog.setFont(loadLatinia(12));
		gameLog.setBackground(Color.WHITE);
		gameLog.setForeground(Color.BLACK);
		gameLog.setMargin(new Insets(4, 4, 4, 4));
		gameLog.setCaretColor(Color.WHITE);
		logEntry("Welcome to MyFFTCG!");

		JScrollPane logScrollPane = new JScrollPane(gameLog,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		handPanel = new JPanel(null);
		handPanel.setBackground(Color.DARK_GRAY);
		handPanel.setPreferredSize(new Dimension(sidePanelW, CARD_H));
		handPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));
		handPanel.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) {
				if (!gameState.getP1Hand().isEmpty()) showHandPopup();
			}
			@Override public void mouseExited(MouseEvent e) { scheduleHandPopupHide(); }
		});
		refreshHandPanel();

		sidePanel = new JPanel(new BorderLayout());
		sidePanel.setPreferredSize(new Dimension(sidePanelW, 0));
		sidePanel.add(sideNorth,     BorderLayout.NORTH);
		sidePanel.add(logScrollPane, BorderLayout.CENTER);
		sidePanel.add(handPanel,     BorderLayout.SOUTH);

		// --- Main game area (wraps both player zones + board so the side panel
		//     spans the full frame height rather than just the centre strip) ---
		JPanel mainArea = new JPanel(new BorderLayout());
		mainArea.add(p2ZonesPanel, BorderLayout.NORTH);
		mainArea.add(southPanel,   BorderLayout.SOUTH);
		mainArea.add(gameBoard,    BorderLayout.CENTER);

		// --- Assemble ---
		frame.getContentPane().add(mainArea, BorderLayout.CENTER);
		applySidePanelSide(AppSettings.getSidePanelSide());
	}

	// -------------------------------------------------------------------------
	// Side panel docking
	// -------------------------------------------------------------------------

	/**
	 * Docks the side info panel to the left or right of the frame.
	 * Safe to call at any time after {@code initialize()} — removes the panel
	 * from its current position, flips its separator border, then re-adds it.
	 *
	 * @param side {@code "left"} or {@code "right"}
	 */
	private void applySidePanelSide(String side) {
		if (sidePanel == null) return;
		frame.getContentPane().remove(sidePanel);
		boolean right = "right".equals(side);
		sidePanel.setBorder(BorderFactory.createMatteBorder(
				0, right ? 1 : 0, 0, right ? 0 : 1, Color.LIGHT_GRAY));
		frame.getContentPane().add(sidePanel, right ? BorderLayout.EAST : BorderLayout.WEST);
		frame.revalidate();
		frame.repaint();
	}

	// -------------------------------------------------------------------------
	// Game startup
	// -------------------------------------------------------------------------

	private void startGame(int deckId) {
		gameState.reset();
		p1LbIndex = 0;
		clearUIZones();
		if (nextPhaseButton != null) nextPhaseButton.setEnabled(false);
		if (gameLog != null) gameLog.setText("");
		logEntry("Game Start");
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
							card.cost(), card.type(), card.isLb(), card.lbCost(), card.exBurst());
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
		if (gameState.isP1GameOver()) return;
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
		logEntry("Drew opening hand (" + drawn.size() + " cards)");
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
		JButton keepBtn = new JButton(mulliganAvailable ? "Keep Hand" : "Take Hand");
		keepBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		keepBtn.addActionListener(e -> {
			hideZoom();
			openingHandPopup.dispose();
			openingHandPopup = null;
			if (mulliganAvailable) logEntry("Kept opening hand");
			gameState.keepHand(handOrder);
			gameState.startFirstTurn();
			logEntry("Turn 1 — Active Phase");
			if (nextPhaseButton != null) nextPhaseButton.setEnabled(true);
			refreshP1HandLabel();
		});

		JButton mulliganBtn = new JButton("Mulligan");
		mulliganBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		mulliganBtn.setEnabled(mulliganAvailable);
		mulliganBtn.setToolTipText(mulliganAvailable
				? "Put these cards on the bottom (in this order) and draw 5 new cards"
				: "Mulligan already used");
		mulliganBtn.addActionListener(e -> {
			hideZoom();
			logEntry("Took mulligan");
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
		refreshHandPanel();
	}

	private void refreshHandPanel() {
		if (handPanel == null) return;
		handPanel.removeAll();
		int n = gameState.getP1Hand().size();
		String text = n == 0 ? "HAND" : "HAND -" + n + "-";
		JLabel lbl = new JLabel(text, SwingConstants.CENTER);
		lbl.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		lbl.setForeground(Color.LIGHT_GRAY);
		lbl.setBounds(0, 0, handPanel.getWidth() > 0 ? handPanel.getWidth() : sidePanelW, CARD_H);
		handPanel.add(lbl);
		handPanel.revalidate();
		handPanel.repaint();
	}

	private static Font loadLatinia(float size) {
		try (java.io.InputStream is = MainWindow.class.getResourceAsStream("/resources/Latinia.ttf")) {
			if (is != null) {
				Font font = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(size);
				java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
				return font;
			}
			System.err.println("[Font] Latinia.ttf not found in resources — falling back to Serif");
		} catch (Exception e) {
			System.err.println("[Font] Failed to load Latinia.ttf: " + e.getMessage());
		}
		return new Font("Serif", Font.PLAIN, (int) size);
	}

	private void refreshP1DeckLabel() {
		int count = gameState.getP1MainDeck().size();
		if (count == 0) {
			p1DeckLabel.setIcon(null);
			p1DeckLabel.setText("DECK");
		} else {
			p1DeckLabel.setIcon(scaledCardbackWithCount(new Dimension(CARD_W, CARD_H), count));
			p1DeckLabel.setText(null);
		}
	}

	// -------------------------------------------------------------------------
	// Phase management
	// -------------------------------------------------------------------------

	/**
	 * Called when the player clicks the "Next" button.
	 * Executes any automatic actions for the phase being left, advances the
	 * phase in GameState, and logs the transition to the game log.
	 *
	 * <ul>
	 *   <li>ACTIVE  → DRAW   : activate dull cards, draw 1 (turn 1) or 2 cards</li>
	 *   <li>DRAW    → MAIN_1 : nothing automatic</li>
	 *   <li>MAIN_1  → ATTACK : nothing automatic</li>
	 *   <li>ATTACK  → MAIN_2 : nothing automatic</li>
	 *   <li>MAIN_2  → END    : nothing automatic</li>
	 *   <li>END     → ACTIVE : increment turn, immediately activate cards</li>
	 * </ul>
	 */
	private void onNextPhase() {
		if (gameState.isP1GameOver()) return;
		GameState.GamePhase current = gameState.getCurrentPhase();
		if (current == null) return;

		switch (current) {

			case ACTIVE: {
				// Advance first so getTurnNumber() still reflects the current turn
				gameState.advancePhase();   // ACTIVE → DRAW
				int drawCount = gameState.getTurnNumber() == 1 ? 1 : 2;
				List<CardData> drawn = gameState.drawToHand(drawCount);
				refreshP1HandLabel();
				refreshP1DeckLabel();
				logEntry("Draw Phase — Drew " + drawn.size()
						+ " card" + (drawn.size() != 1 ? "s" : ""));
				if (drawn.size() < drawCount) {
					logEntry("Milled Out - You Lose!");
					nextPhaseButton.setEnabled(false);
					return;
				}
				// No choices to make during Draw phase — advance automatically
				onNextPhase();
				break;
			}

			case DRAW:
				gameState.advancePhase();   // DRAW → MAIN_1
				logEntry("Main Phase 1");
				break;

			case MAIN_1:
				gameState.advancePhase();   // MAIN_1 → ATTACK
				logEntry("Attack Phase");
				break;

			case ATTACK:
				gameState.advancePhase();   // ATTACK → MAIN_2
				logEntry("Main Phase 2");
				break;

			case MAIN_2:
				gameState.advancePhase();   // MAIN_2 → END
				logEntry("End Phase");
				showEndPhaseDiscardDialog();
				break;

			case END: {
				// Advance (turn number increments inside advancePhase)
				gameState.advancePhase();   // END → ACTIVE

				// Execute Active Phase: dull → normal, frozen → dull
				int activated = 0, thawed = 0;
				for (int i = 0; i < p1BackupStates.length; i++) {
					if (p1BackupStates[i] == BACKUP_FROZEN) {
						p1BackupStates[i] = BACKUP_DULLED;
						refreshP1BackupSlot(i);
						thawed++;
					} else if (p1BackupStates[i] == BACKUP_DULLED) {
						p1BackupStates[i] = BACKUP_NORMAL;
						refreshP1BackupSlot(i);
						activated++;
					}
				}
				for (int i = 0; i < p1ForwardStates.size(); i++) {
					if (p1ForwardStates.get(i) == BACKUP_FROZEN) {
						p1ForwardStates.set(i, BACKUP_DULLED);
						refreshP1ForwardSlot(i);
						thawed++;
					} else if (p1ForwardStates.get(i) == BACKUP_DULLED) {
						p1ForwardStates.set(i, BACKUP_NORMAL);
						refreshP1ForwardSlot(i);
						activated++;
					}
				}

				StringBuilder msg = new StringBuilder(
						"Turn " + gameState.getTurnNumber() + " — Active Phase");
				if (activated > 0)
					msg.append(" (").append(activated).append(" activated");
				if (thawed > 0)
					msg.append(activated > 0 ? ", " : " (").append(thawed).append(" thawed");
				if (activated > 0 || thawed > 0) msg.append(")");
				logEntry(msg.toString());
				break;
			}
		}
	}

	/** Appends a timestamped entry to the game log. */
	private void logEntry(String text) {
		if (gameLog == null) return;
		String time = java.time.LocalTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
		gameLog.append(time + "  " + text + "\n");
		gameLog.setCaretPosition(gameLog.getDocument().getLength());
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
		spentLbIndices.clear();

		// Damage zone
		if (p1DamageSlotPanel != null) {
			p1DamageSlotPanel.putClientProperty("exBurst", Boolean.FALSE);
			p1DamageSlotPanel.repaint();
		}
		for (JPanel slot : p1DamageSlots) {
			if (slot != null) {
				slot.putClientProperty("cardImg", null);
				slot.putClientProperty("isExBurst", null);
				slot.repaint();
			}
		}

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

	private void refreshP1LimitLabel() {
		if (gameState.getP1LbDeck().isEmpty()) {
			p1LimitLabel.setIcon(null);
			p1LimitLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
			p1LimitLabel.setText("LIMIT");
			return;
		}
		int playable = gameState.getP1LbDeck().size() - spentLbIndices.size();
		p1LimitLabel.setText(null);
		p1LimitLabel.setIcon(goldenCardback(new Dimension(CARD_W, CARD_H), playable));
	}

	private ImageIcon goldenCardback(Dimension size, int count) {
		Image base = new ImageIcon(getClass().getResource("/resources/cardback.jpg")).getImage();
		BufferedImage buf = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = buf.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.drawImage(base, 0, 0, size.width, size.height, null);
		g.setColor(new Color(255, 200, 0, 90));
		g.fillRect(0, 0, size.width, size.height);
		String text = String.valueOf(count);
		g.setFont(new Font("Pixel NES", Font.PLAIN, 12));
		int textW = g.getFontMetrics().stringWidth(text);
		int textH = g.getFontMetrics().getAscent();
		int x = size.width - textW - 4;
		int y = textH + 4;
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(Color.WHITE);
		g.drawString(text, x, y);
		g.dispose();
		return new ImageIcon(buf);
	}

	private void showBreakZoneDialog() {
		List<CardData> zone = gameState.getP1BreakZone();
		if (zone.isEmpty()) return;

		JDialog dlg = new JDialog(frame, "Break Zone (" + zone.size() + " cards)", true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

		for (CardData cd : zone) {
			JPanel cardWrapper = new JPanel(new BorderLayout(0, 4));
			cardWrapper.setBackground(cardsPanel.getBackground());

			JLabel lbl = new JLabel("...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
			lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true);
			lbl.setBackground(Color.DARK_GRAY);
			lbl.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));

			lbl.addMouseListener(new MouseAdapter() {
				@Override public void mouseEntered(MouseEvent e) {
					if (lbl.getIcon() != null) showZoomAt(cd.imageUrl(), lbl);
				}
				@Override public void mouseExited(MouseEvent e) { hideZoom(); }
			});

			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(cd.imageUrl());
					if (img == null) return null;
					BufferedImage buf = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g2 = buf.createGraphics();
					g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g2.drawImage(img, 0, 0, CARD_W, CARD_H, null);
					g2.dispose();
					return new ImageIcon(buf);
				}
				@Override protected void done() {
					try {
						ImageIcon icon = get();
						if (icon != null) { lbl.setIcon(icon); lbl.setText(null); }
					} catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();

			JLabel nameLabel = new JLabel(cd.name(), SwingConstants.CENTER);
			nameLabel.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			nameLabel.setPreferredSize(new Dimension(CARD_W, 18));

			cardWrapper.add(lbl,       BorderLayout.CENTER);
			cardWrapper.add(nameLabel, BorderLayout.SOUTH);
			cardsPanel.add(cardWrapper);
		}

		JScrollPane scrollPane = new JScrollPane(cardsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setPreferredSize(new Dimension(
				Math.min(zone.size() * (CARD_W + 16) + 16, 900),
				CARD_H + 60));

		JButton closeBtn = new JButton("Close");
		closeBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		closeBtn.addActionListener(ae -> { hideZoom(); dlg.dispose(); });

		JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
		south.add(closeBtn);
		south.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

		dlg.getContentPane().setLayout(new BorderLayout(0, 4));
		dlg.getContentPane().add(scrollPane, BorderLayout.CENTER);
		dlg.getContentPane().add(south,      BorderLayout.SOUTH);
		dlg.pack();
		dlg.setLocationRelativeTo(frame);
		dlg.setVisible(true);
	}

	private void triggerGameOver(String reason) {
		gameState.setP1GameOver(true);
		logEntry(reason);
		if (nextPhaseButton != null) nextPhaseButton.setEnabled(false);
	}

	private void p1TakeDamage() {
		if (gameState.isP1GameOver()) return;
		CardData drawn = gameState.drawToDamageZone();
		if (drawn == null) {
			triggerGameOver("P1 milled out — You Lose!");
			return;
		}
		int idx = gameState.getP1DamageZone().size() - 1;
		boolean isEx = drawn.exBurst();

		refreshP1DeckLabel();
		logEntry("P1 takes 1 damage — " + drawn.name() + (isEx ? " [EX BURST!]" : ""));

		if (gameState.getP1DamageZone().size() >= 7) {
			triggerGameOver("7 Damage Taken - You Lose!");
		}

		if (p1DamageSlotPanel != null) {
			p1DamageSlotPanel.putClientProperty("exBurst", isEx ? Boolean.TRUE : Boolean.FALSE);
			for (JPanel s : p1DamageSlots) { if (s != null) s.repaint(); }
			p1DamageSlotPanel.repaint();
		}

		if (idx < 7 && p1DamageSlots[idx] != null) {
			JPanel slot = p1DamageSlots[idx];
			slot.putClientProperty("isExBurst", isEx ? Boolean.TRUE : Boolean.FALSE);
			slot.repaint();
			String url = drawn.imageUrl();
			new SwingWorker<Image, Void>() {
				@Override protected Image doInBackground() throws Exception {
					return ImageCache.load(url);
				}
				@Override protected void done() {
					try {
						Image img = get();
						if (img != null) { slot.putClientProperty("cardImg", img); slot.repaint(); }
					} catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();
		}
	}

	private void showDamageZoneDialog() {
		List<CardData> zone = gameState.getP1DamageZone();
		if (zone.isEmpty()) return;

		JDialog dlg = new JDialog(frame, "Damage Zone (" + zone.size() + " cards)", true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

		for (CardData cd : zone) {
			JPanel cardWrapper = new JPanel(new BorderLayout(0, 4));
			cardWrapper.setBackground(cardsPanel.getBackground());

			JLabel lbl = new JLabel("...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
			lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true);
			lbl.setBackground(Color.DARK_GRAY);
			lbl.setBorder(BorderFactory.createLineBorder(
					cd.exBurst() ? Color.YELLOW : Color.LIGHT_GRAY, cd.exBurst() ? 2 : 1));

			lbl.addMouseListener(new MouseAdapter() {
				@Override public void mouseEntered(MouseEvent e) {
					if (lbl.getIcon() != null) showZoomAt(cd.imageUrl(), lbl);
				}
				@Override public void mouseExited(MouseEvent e) { hideZoom(); }
			});

			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(cd.imageUrl());
					if (img == null) return null;
					BufferedImage buf = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g2 = buf.createGraphics();
					g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g2.drawImage(img, 0, 0, CARD_W, CARD_H, null);
					g2.dispose();
					return new ImageIcon(buf);
				}
				@Override protected void done() {
					try {
						ImageIcon icon = get();
						if (icon != null) { lbl.setIcon(icon); lbl.setText(null); }
					} catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();

			JLabel nameLabel = new JLabel(cd.name() + (cd.exBurst() ? " ★EX" : ""), SwingConstants.CENTER);
			nameLabel.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			nameLabel.setForeground(cd.exBurst() ? Color.YELLOW : null);
			nameLabel.setPreferredSize(new Dimension(CARD_W, 18));

			cardWrapper.add(lbl,       BorderLayout.CENTER);
			cardWrapper.add(nameLabel, BorderLayout.SOUTH);
			cardsPanel.add(cardWrapper);
		}

		JScrollPane scrollPane = new JScrollPane(cardsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setPreferredSize(new Dimension(
				Math.min(zone.size() * (CARD_W + 16) + 16, 900),
				CARD_H + 60));

		JButton closeBtn = new JButton("Close");
		closeBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		closeBtn.addActionListener(ae -> { hideZoom(); dlg.dispose(); });

		JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
		south.add(closeBtn);
		south.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

		dlg.getContentPane().setLayout(new BorderLayout(0, 4));
		dlg.getContentPane().add(scrollPane, BorderLayout.CENTER);
		dlg.getContentPane().add(south,      BorderLayout.SOUTH);
		dlg.pack();
		dlg.setLocationRelativeTo(frame);
		dlg.setVisible(true);
	}

	private void showLbDialog() {
		List<CardData> lbDeck = gameState.getP1LbDeck();
		if (lbDeck.isEmpty()) return;

		JDialog dlg = new JDialog(frame, "Limit Break", true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		// Track which cards are being cast / selected for payment in this dialog session
		int[] castingIdx       = { -1 };   // index of card being cast
		int[] paymentChosen    = { 0 };    // how many payment cards selected so far
		Set<Integer> paymentSet = new java.util.HashSet<>();

		JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
		statusLabel.setFont(new Font("Pixel NES", Font.PLAIN, 10));

		JButton confirmCastBtn = new JButton("Confirm Cast");
		confirmCastBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		confirmCastBtn.setVisible(false);

		JButton cancelCastBtn = new JButton("Cancel");
		cancelCastBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cancelCastBtn.setVisible(false);

		// One label per LB card
		List<JLabel> cardLabels = new ArrayList<>();
		JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

		Runnable refreshLabels = () -> {
			for (int i = 0; i < cardLabels.size(); i++) {
				JLabel lbl = cardLabels.get(i);
				boolean spent   = spentLbIndices.contains(i);
				boolean casting = (castingIdx[0] == i);
				boolean payment = paymentSet.contains(i);
				boolean inPaymentMode = castingIdx[0] >= 0;

				if (casting) {
					lbl.setBorder(BorderFactory.createLineBorder(new Color(255, 200, 0), 3));
				} else if (payment) {
					lbl.setBorder(BorderFactory.createLineBorder(Color.CYAN, 3));
				} else if (spent) {
					lbl.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1));
				} else {
					lbl.setBorder(BorderFactory.createLineBorder(
							inPaymentMode ? Color.GRAY : Color.LIGHT_GRAY, 1));
				}
				lbl.setCursor((!spent && !casting && (castingIdx[0] < 0 || !paymentSet.contains(i) || payment))
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
			if (castingIdx[0] >= 0) {
				int needed = lbDeck.get(castingIdx[0]).lbCost() - paymentSet.size();
				statusLabel.setText(needed > 0
						? "Choose " + needed + " more LB card(s) as payment"
						: "Ready — click Confirm Cast");
				confirmCastBtn.setEnabled(needed <= 0);
			} else {
				statusLabel.setText(" ");
			}
		};

		confirmCastBtn.addActionListener(ae -> {
			CardData cast = lbDeck.get(castingIdx[0]);
			dlg.dispose();
			if (cast.cost() == 0) {
				// No CP dialog — commit immediately
				spentLbIndices.add(castingIdx[0]);
				spentLbIndices.addAll(paymentSet);
				logEntry("Cast LB \"" + cast.name() + "\"");
				executeLbPlay(cast, Collections.emptyList(), Collections.emptyList());
			} else {
				// Defer committing until CP payment is confirmed
				int pendingCastIdx = castingIdx[0];
				Set<Integer> pendingPayment = new java.util.HashSet<>(paymentSet);
				showLbCpPaymentDialog(cast, pendingCastIdx, pendingPayment);
			}
		});

		cancelCastBtn.addActionListener(ae -> {
			hideZoom();
			castingIdx[0] = -1;
			paymentSet.clear();
			paymentChosen[0] = 0;
			confirmCastBtn.setVisible(false);
			cancelCastBtn.setVisible(false);
			refreshLabels.run();
		});

		for (int i = 0; i < lbDeck.size(); i++) {
			final int idx = i;
			CardData  cd  = lbDeck.get(i);

			JPanel cardWrapper = new JPanel(new BorderLayout(0, 4));
			cardWrapper.setBackground(cardsPanel.getBackground());

			JLabel lbl = new JLabel("...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
			lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true);
			lbl.setBackground(Color.DARK_GRAY);
			lbl.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
			cardLabels.add(lbl);

			lbl.addMouseListener(new MouseAdapter() {
				@Override public void mouseEntered(MouseEvent e) {
					if (lbl.getIcon() != null) showZoomAt(cd.imageUrl(), lbl);
				}
				@Override public void mouseExited(MouseEvent e) { hideZoom(); }
				@Override public void mousePressed(MouseEvent e) {
					boolean spent = spentLbIndices.contains(idx);
					if (spent) return;

					if (castingIdx[0] < 0) {
						// Start casting this card
						castingIdx[0] = idx;
						paymentSet.clear();
						paymentChosen[0] = 0;
						confirmCastBtn.setVisible(true);
						cancelCastBtn.setVisible(true);
						confirmCastBtn.setEnabled(cd.lbCost() == 0);
					} else if (castingIdx[0] == idx) {
						// Click on casting card — cancel
						castingIdx[0] = -1;
						paymentSet.clear();
						confirmCastBtn.setVisible(false);
						cancelCastBtn.setVisible(false);
					} else {
						// Toggle payment selection
						if (paymentSet.contains(idx)) {
							paymentSet.remove(idx);
						} else if (paymentSet.size() < lbDeck.get(castingIdx[0]).lbCost()) {
							paymentSet.add(idx);
						}
					}
					refreshLabels.run();
				}
			});

			// Load full image, greyed if spent
			final boolean spent = spentLbIndices.contains(i);
			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(cd.imageUrl());
					if (img == null) return null;
					BufferedImage buf = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g2 = buf.createGraphics();
					g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g2.drawImage(img, 0, 0, CARD_W, CARD_H, null);
					g2.dispose();
					if (spent) {
						return new ImageIcon(new ColorConvertOp(
								ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(buf, null));
					}
					return new ImageIcon(buf);
				}
				@Override protected void done() {
					try {
						ImageIcon icon = get();
						if (icon != null) { lbl.setIcon(icon); lbl.setText(null); }
					} catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();

			JLabel nameLabel = new JLabel(cd.name() + " - LB " + cd.lbCost() + "",
					SwingConstants.CENTER);
			nameLabel.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			nameLabel.setPreferredSize(new Dimension(CARD_W, 18));

			cardWrapper.add(lbl,       BorderLayout.CENTER);
			cardWrapper.add(nameLabel, BorderLayout.SOUTH);
			cardsPanel.add(cardWrapper);
		}

		refreshLabels.run();

		JScrollPane scrollPane = new JScrollPane(cardsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setPreferredSize(new Dimension(
				Math.min(lbDeck.size() * (CARD_W + 16) + 16, 900),
				CARD_H + 60));

		JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
		statusBar.add(statusLabel);
		statusBar.add(confirmCastBtn);
		statusBar.add(cancelCastBtn);

		JButton closeBtn = new JButton("Close");
		closeBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		closeBtn.addActionListener(ae -> { hideZoom(); dlg.dispose(); });

		JPanel south = new JPanel(new BorderLayout());
		south.add(statusBar,  BorderLayout.CENTER);
		south.add(closeBtn,   BorderLayout.EAST);
		south.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

		dlg.getContentPane().setLayout(new BorderLayout(0, 4));
		dlg.getContentPane().add(scrollPane, BorderLayout.CENTER);
		dlg.getContentPane().add(south,      BorderLayout.SOUTH);
		dlg.pack();
		dlg.setLocationRelativeTo(frame);
		dlg.setVisible(true);
	}

	private void showEndPhaseDiscardDialog() {
		List<CardData> hand = gameState.getP1Hand();
		if (hand.size() <= 5) return;

		JDialog dlg = new JDialog(frame, "End Phase — Discard to 5", true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

		Set<Integer> selected = new java.util.HashSet<>();
		int mustDiscard = hand.size() - 5;

		JLabel statusLabel = new JLabel("Select " + mustDiscard + " card(s) to discard.", SwingConstants.CENTER);
		statusLabel.setFont(new Font("Pixel NES", Font.PLAIN, 10));

		List<JLabel> cardLabels = new ArrayList<>();
		JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

		JButton confirmBtn = new JButton("Confirm");
		confirmBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		confirmBtn.setEnabled(false);

		Runnable refresh = () -> {
			int remaining = mustDiscard - selected.size();
			statusLabel.setText(remaining > 0
					? "Select " + remaining + " more card(s) to discard."
					: "Ready — click Confirm to discard.");
			confirmBtn.setEnabled(selected.size() == mustDiscard);
			for (int i = 0; i < cardLabels.size(); i++) {
				cardLabels.get(i).setBorder(BorderFactory.createLineBorder(
						selected.contains(i) ? Color.RED : Color.LIGHT_GRAY, selected.contains(i) ? 3 : 1));
			}
		};

		for (int i = 0; i < hand.size(); i++) {
			final int idx = i;
			CardData cd = hand.get(i);

			JPanel wrapper = new JPanel(new BorderLayout(0, 4));
			wrapper.setBackground(cardsPanel.getBackground());

			JLabel lbl = new JLabel("...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
			lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true);
			lbl.setBackground(Color.DARK_GRAY);
			lbl.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
			lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			cardLabels.add(lbl);

			lbl.addMouseListener(new MouseAdapter() {
				@Override public void mouseEntered(MouseEvent e) { if (lbl.getIcon() != null) showZoomAt(cd.imageUrl(), lbl); }
				@Override public void mouseExited(MouseEvent e)  { hideZoom(); }
				@Override public void mousePressed(MouseEvent e) {
					if (selected.contains(idx)) selected.remove(idx);
					else if (selected.size() < mustDiscard)  selected.add(idx);
					refresh.run();
				}
			});

			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(cd.imageUrl());
					if (img == null) return null;
					BufferedImage buf = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g2 = buf.createGraphics();
					g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g2.drawImage(img, 0, 0, CARD_W, CARD_H, null);
					g2.dispose();
					return new ImageIcon(buf);
				}
				@Override protected void done() {
					try { ImageIcon icon = get(); if (icon != null) { lbl.setIcon(icon); lbl.setText(null); } }
					catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();

			JLabel nameLabel = new JLabel(cd.name(), SwingConstants.CENTER);
			nameLabel.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			nameLabel.setPreferredSize(new Dimension(CARD_W, 18));

			wrapper.add(lbl,       BorderLayout.CENTER);
			wrapper.add(nameLabel, BorderLayout.SOUTH);
			cardsPanel.add(wrapper);
		}

		confirmBtn.addActionListener(ae -> {
			hideZoom();
			dlg.dispose();
			List<Integer> toDiscard = new ArrayList<>(selected);
			toDiscard.sort(Collections.reverseOrder());
			for (int di : toDiscard) {
				gameState.discardFromHand(di);
			}
			logEntry("Discarded " + toDiscard.size() + " card(s) — hand reduced to 5");
			refreshP1HandLabel();
			refreshP1BreakLabel();
		});

		JScrollPane scrollPane = new JScrollPane(cardsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setPreferredSize(new Dimension(
				Math.min(hand.size() * (CARD_W + 16) + 16, 900),
				CARD_H + 60));

		JPanel south = new JPanel(new BorderLayout());
		south.add(statusLabel,  BorderLayout.CENTER);
		south.add(confirmBtn,   BorderLayout.EAST);
		south.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

		dlg.getContentPane().setLayout(new BorderLayout(0, 4));
		dlg.getContentPane().add(scrollPane, BorderLayout.CENTER);
		dlg.getContentPane().add(south,      BorderLayout.SOUTH);
		dlg.pack();
		dlg.setLocationRelativeTo(frame);
		dlg.setVisible(true);
	}

	// -------------------------------------------------------------------------
	// Async image loading helpers
	// -------------------------------------------------------------------------

	/**
	 * Loads the card image for {@code url} at its native resolution and
	 * displays it in the side-panel preview.  The first time this is called
	 * the side panel is resized to exactly fit the card plus {@link #SIDE_MARGIN}.
	 * The {@code anchor} parameter is kept for call-site compatibility.
	 */
	private void showZoomAt(String url, JLabel anchor) {
		if (url == null || cardPreviewPanel == null) return;
		new SwingWorker<BufferedImage, Void>() {
			@Override
			protected BufferedImage doInBackground() throws Exception {
				Image img = ImageCache.load(url);
				if (img == null) return null;
				int w = img.getWidth(null);
				int h = img.getHeight(null);
				BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				buf.getGraphics().drawImage(img, 0, 0, null);
				return buf;
			}
			@Override
			protected void done() {
				try {
					BufferedImage img = get();
					if (img == null) return;
					sizePreviewPanel(img.getWidth(), img.getHeight());
					previewImage = img;
					startFadeIn();
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	/** Clears the side-panel card preview with a fade-out. */
	private void hideZoom() {
		startFadeOut();
	}

	/** Fades the preview in from transparent to opaque (~120 ms). */
	private void startFadeIn() {
		if (fadeTimer != null) fadeTimer.stop();
		previewAlpha = 0f;
		cardPreviewPanel.repaint();
		fadeTimer = new javax.swing.Timer(16, e -> {
			previewAlpha = Math.min(1f, previewAlpha + 0.15f);
			cardPreviewPanel.repaint();
			if (previewAlpha >= 1f) ((javax.swing.Timer) e.getSource()).stop();
		});
		fadeTimer.start();
	}

	/** Fades the preview out to transparent (~120 ms), then clears the image. */
	private void startFadeOut() {
		if (fadeTimer != null) fadeTimer.stop();
		if (cardPreviewPanel == null) { previewImage = null; return; }
		fadeTimer = new javax.swing.Timer(16, e -> {
			previewAlpha = Math.max(0f, previewAlpha - 0.15f);
			cardPreviewPanel.repaint();
			if (previewAlpha <= 0f) {
				((javax.swing.Timer) e.getSource()).stop();
				previewImage = null;
				cardPreviewPanel.repaint();
			}
		});
		fadeTimer.start();
	}

	/**
	 * On the first call, resizes the side panel and preview panel so the panel
	 * is exactly {@code imgW + SIDE_MARGIN} pixels wide and {@code imgH} pixels tall.
	 * Subsequent calls are no-ops.
	 */
	private void sizePreviewPanel(int imgW, int imgH) {
		if (previewSized) return;
		previewSized = true;
		sidePanelW = (int)(imgW * PREVIEW_SCALE) + SIDE_MARGIN;
		previewH   = (int)(imgH * PREVIEW_SCALE);
		cardPreviewPanel.setPreferredSize(new Dimension(sidePanelW, previewH));
		cardPreviewPanel.setMinimumSize  (new Dimension(sidePanelW, previewH));
		cardPreviewPanel.setMaximumSize  (new Dimension(sidePanelW, previewH));
		sidePanel.setPreferredSize(new Dimension(sidePanelW, 0));
		handPanel.setPreferredSize(new Dimension(sidePanelW, CARD_H));
		refreshHandPanel();
		frame.revalidate();
	}

	// -------------------------------------------------------------------------
	// Hand card zoom / popup helpers
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

		// Position above the hand panel: extend right for left sidebar, left for right sidebar
		Point loc = handPanel.getLocationOnScreen();
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		boolean sidebarOnRight = "right".equals(AppSettings.getSidePanelSide());
		int x = sidebarOnRight
				? loc.x + handPanel.getWidth() - handPopup.getWidth()
				: loc.x;
		int y = loc.y - handPopup.getHeight() - 4;
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
		if (gameState.isP1GameOver()) return;
		cancelHandPopupHide();
		handCardMenuOpen = true;

		JPopupMenu menu = new JPopupMenu();

		JMenuItem playItem = new JMenuItem("Play");
		GameState.GamePhase phase = gameState.getCurrentPhase();
		boolean isMainPhase = phase == GameState.GamePhase.MAIN_1 || phase == GameState.GamePhase.MAIN_2;
		playItem.setEnabled(isMainPhase && canAffordCard(card, handIdx) && (!card.isBackup() || hasAvailableBackupSlot()));
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

	/** Shows a preview of a hand card in the side panel. */
	private void showHandCardZoom(String url, JLabel anchor) {
		showZoomAt(url, anchor);
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
				final String url = p1BackupUrls[slot];
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
					@Override public void mouseEntered(MouseEvent e) {
						if (lbl.getIcon() != null) showZoomAt(url, lbl);
					}
					@Override public void mouseExited(MouseEvent e) { hideZoom(); }
				});
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

			final String  imgUrl = hc.imageUrl();
			final boolean grey   = !payable;
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
					@Override public void mouseEntered(MouseEvent e) {
						if (lbl.getIcon() != null) showZoomAt(imgUrl, lbl);
					}
					@Override public void mouseExited(MouseEvent e) { hideZoom(); }
				});
				discardLbls.add(lbl);
				discardIdxs.add(hi);
			} else {
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mouseEntered(MouseEvent e) {
						if (lbl.getIcon() != null) showZoomAt(imgUrl, lbl);
					}
					@Override public void mouseExited(MouseEvent e) { hideZoom(); }
				});
			}

			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(imgUrl);
					if (img == null) return null;
					BufferedImage buf = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g2 = buf.createGraphics();
					g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g2.drawImage(img, 0, 0, CARD_W, CARD_H, null);
					g2.dispose();
					if (grey) {
						return new ImageIcon(
								new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null)
										.filter(buf, null));
					}
					return new ImageIcon(buf);
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
		logEntry("Played \"" + card.name() + "\"");

		if (card.isBackup()) {
			placeCardInFirstBackupSlot(card);
		} else if (card.isForward()) {
			placeCardInForwardZone(card);
		}

		refreshP1HandLabel();
		refreshP1BreakLabel();
	}

	/**
	 * CP payment dialog for LB casting — mirrors showPaymentDialog but has no
	 * hand-card to exclude and calls executeLbPlay on confirm.
	 */
	private void showLbCpPaymentDialog(CardData card, int lbCastIdx, Set<Integer> pendingLbPayment) {
		JDialog dlg = new JDialog(frame, "Pay CP for LB: " + card.name(), true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		List<CardData> hand   = gameState.getP1Hand();
		String         elem   = card.element();
		int            cost   = card.cost();
		int            bankCp = gameState.getP1CpForElement(elem);

		List<Integer> selectedBackups  = new ArrayList<>();
		List<Integer> selectedDiscards = new ArrayList<>();

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

		List<JLabel>  backupLbls  = new ArrayList<>();
		List<Integer> backupSlots = new ArrayList<>();
		List<JLabel>  discardLbls  = new ArrayList<>();
		List<Integer> discardIdxs  = new ArrayList<>();

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
				final String url = p1BackupUrls[slot];
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
					@Override public void mouseEntered(MouseEvent e) {
						if (lbl.getIcon() != null) showZoomAt(url, lbl);
					}
					@Override public void mouseExited(MouseEvent e) { hideZoom(); }
				});
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

		JLabel discardHeader = new JLabel("Hand — discard for 2 CP each:");
		discardHeader.setFont(new Font("Pixel NES", Font.PLAIN, 9));
		discardHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel discardCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
		discardCardsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		for (int i = 0; i < hand.size(); i++) {
			final int hi  = i;
			CardData  hc  = hand.get(i);
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

			final String  imgUrl = hc.imageUrl();
			final boolean grey   = !payable;
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
					@Override public void mouseEntered(MouseEvent e) {
						if (lbl.getIcon() != null) showZoomAt(imgUrl, lbl);
					}
					@Override public void mouseExited(MouseEvent e) { hideZoom(); }
				});
				discardLbls.add(lbl);
				discardIdxs.add(hi);
			} else {
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mouseEntered(MouseEvent e) {
						if (lbl.getIcon() != null) showZoomAt(imgUrl, lbl);
					}
					@Override public void mouseExited(MouseEvent e) { hideZoom(); }
				});
			}

			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(imgUrl);
					if (img == null) return null;
					BufferedImage buf = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g2 = buf.createGraphics();
					g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g2.drawImage(img, 0, 0, CARD_W, CARD_H, null);
					g2.dispose();
					if (grey) {
						return new ImageIcon(
								new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null)
										.filter(buf, null));
					}
					return new ImageIcon(buf);
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

		JLabel hint = new JLabel(
				"<html><center>Backups: dull for 1 CP. Hand cards (" + elem
				+ ", non-Light/Dark): discard for 2 CP.</center></html>",
				SwingConstants.CENTER);
		hint.setFont(new Font("Pixel NES", Font.PLAIN, 9));

		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cancelBtn.addActionListener(e -> { hideZoom(); dlg.dispose(); });

		confirmBtn.addActionListener(e -> {
			spentLbIndices.add(lbCastIdx);
			spentLbIndices.addAll(pendingLbPayment);
			logEntry("Cast LB \"" + card.name() + "\"");
			dlg.dispose();
			executeLbPlay(card, new ArrayList<>(selectedDiscards), new ArrayList<>(selectedBackups));
		});

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
		buttonPanel.add(confirmBtn);
		buttonPanel.add(cancelBtn);

		JLabel titleLabel = new JLabel(
				"Pay for LB: " + card.name() + "  (Cost " + cost + " " + elem + " CP)",
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
	 * Executes an LB cast: dulls selected backups, discards payment hand cards,
	 * spends CP, and places the card — without removing it from hand.
	 */
	private void executeLbPlay(CardData card, List<Integer> discardIndices,
			List<Integer> backupDullIndices) {
		for (int bi : backupDullIndices) {
			p1BackupStates[bi] = BACKUP_DULLED;
			refreshP1BackupSlot(bi);
			gameState.addP1Cp(card.element(), 1);
		}
		discardIndices.sort(Collections.reverseOrder());
		for (int di : discardIndices) {
			gameState.addP1Cp(card.element(), 2);
			gameState.discardFromHand(di);
		}
		gameState.spendP1Cp(card.element(), card.cost());
		gameState.clearP1Cp(card.element());
		if (card.isBackup()) {
			placeCardInFirstBackupSlot(card);
		} else if (card.isForward()) {
			placeCardInForwardZone(card);
		}
		refreshP1HandLabel();
		refreshP1BreakLabel();
		refreshP1LimitLabel();
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

	private void animateDullBackup(int idx, boolean dulling) {
		String url  = p1BackupUrls[idx];
		JLabel slot = p1BackupLabels[idx];
		if (url == null || slot == null) return;

		new SwingWorker<BufferedImage, Void>() {
			@Override protected BufferedImage doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				return raw == null ? null : toARGB(raw, CARD_W, CARD_H);
			}
			@Override protected void done() {
				try {
					BufferedImage card = get();
					if (card == null) { refreshP1BackupSlot(idx); return; }

					int   totalFrames = 12;
					int[] frame       = { 0 };
					javax.swing.Timer timer = new javax.swing.Timer(16, null);
					timer.addActionListener(ae -> {
						frame[0]++;
						double progress = Math.min(1.0, (double) frame[0] / totalFrames);
						// ease in-out
						double t = progress < 0.5
								? 2 * progress * progress
								: 1 - Math.pow(-2 * progress + 2, 2) / 2;
						double angle = dulling ? (Math.PI / 2 * t) : (Math.PI / 2 * (1 - t));
						slot.setIcon(new ImageIcon(renderBackupCardAtAngle(card, angle)));
						slot.setText(null);
						if (frame[0] >= totalFrames) {
							timer.stop();
							refreshP1BackupSlot(idx);
						}
					});
					timer.start();
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	private void animateFreezeBackup(int idx, boolean freezing, int prevState) {
		String url  = p1BackupUrls[idx];
		JLabel slot = p1BackupLabels[idx];
		if (url == null || slot == null) return;

		new SwingWorker<BufferedImage, Void>() {
			@Override protected BufferedImage doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				return raw == null ? null : toARGB(raw, CARD_W, CARD_H);
			}
			@Override protected void done() {
				try {
					BufferedImage card = get();
					if (card == null) { refreshP1BackupSlot(idx); return; }

					double startAngle = freezing ? (prevState == BACKUP_DULLED ? Math.PI / 2 : 0.0) : Math.PI;
					double endAngle   = freezing ? Math.PI : 0.0;

					int   totalFrames = 12;
					int[] frame       = { 0 };
					javax.swing.Timer timer = new javax.swing.Timer(16, null);
					timer.addActionListener(ae -> {
						frame[0]++;
						double progress = Math.min(1.0, (double) frame[0] / totalFrames);
						double t = progress < 0.5
								? 2 * progress * progress
								: 1 - Math.pow(-2 * progress + 2, 2) / 2;
						double angle = startAngle + (endAngle - startAngle) * t;
						slot.setIcon(new ImageIcon(renderBackupCardAtAngle(card, angle)));
						slot.setText(null);
						if (frame[0] >= totalFrames) {
							timer.stop();
							refreshP1BackupSlot(idx);
						}
					});
					timer.start();
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	private void animateFreezeForward(int idx, boolean freezing, int prevState) {
		String url  = p1ForwardUrls.get(idx);
		JLabel slot = p1ForwardLabels.get(idx);
		if (url == null || slot == null) return;

		new SwingWorker<BufferedImage, Void>() {
			@Override protected BufferedImage doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				return raw == null ? null : toARGB(raw, CARD_W, CARD_H);
			}
			@Override protected void done() {
				try {
					BufferedImage card = get();
					if (card == null) { refreshP1ForwardSlot(idx); return; }

					double startAngle = freezing ? (prevState == BACKUP_DULLED ? Math.PI / 2 : 0.0) : Math.PI;
					double endAngle   = freezing ? Math.PI : 0.0;

					int   totalFrames = 12;
					int[] frame       = { 0 };
					javax.swing.Timer timer = new javax.swing.Timer(16, null);
					timer.addActionListener(ae -> {
						frame[0]++;
						double progress = Math.min(1.0, (double) frame[0] / totalFrames);
						double t = progress < 0.5
								? 2 * progress * progress
								: 1 - Math.pow(-2 * progress + 2, 2) / 2;
						double angle = startAngle + (endAngle - startAngle) * t;
						slot.setIcon(new ImageIcon(renderBackupCardAtAngle(card, angle)));
						slot.setText(null);
						if (frame[0] >= totalFrames) {
							timer.stop();
							refreshP1ForwardSlot(idx);
						}
					});
					timer.start();
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	private static BufferedImage renderBackupCardAtAngle(BufferedImage card, double angle) {
		BufferedImage canvas = new BufferedImage(CARD_H, CARD_H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.translate(CARD_H / 2.0, CARD_H / 2.0);
		g.rotate(angle);
		g.translate(-CARD_W / 2.0, -CARD_H / 2.0);
		g.drawImage(card, 0, 0, null);
		g.dispose();
		return canvas;
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
			boolean dulling = p1BackupStates[idx] != BACKUP_DULLED;
			p1BackupStates[idx] = dulling ? BACKUP_DULLED : BACKUP_NORMAL;
			animateDullBackup(idx, dulling);
		});
		menu.add(dullItem);

		JMenuItem freezeItem = new JMenuItem("Debug: Freeze");
		freezeItem.addActionListener(ae -> {
			boolean freezing = p1BackupStates[idx] != BACKUP_FROZEN;
			int prevState = p1BackupStates[idx];
			p1BackupStates[idx] = freezing ? BACKUP_FROZEN : BACKUP_NORMAL;
			animateFreezeBackup(idx, freezing, prevState);
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

	/** Shows a grayscale preview of a "Removed from Play" card in the side panel. */
	private void showGrayscaleZoom(GrayscaleLabel label) {
		String url = label.getUrl();
		if (url == null || cardPreviewPanel == null) return;
		new SwingWorker<BufferedImage, Void>() {
			@Override
			protected BufferedImage doInBackground() throws Exception {
				Image img = ImageCache.load(url);
				if (img == null) return null;
				int nativeW = img.getWidth(null);
				int nativeH = img.getHeight(null);
				BufferedImage buf = new BufferedImage(nativeW, nativeH, BufferedImage.TYPE_INT_ARGB);
				buf.getGraphics().drawImage(img, 0, 0, null);
				return new ColorConvertOp(
						ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(buf, null);
			}
			@Override
			protected void done() {
				try {
					BufferedImage img = get();
					if (img != null) {
						sizePreviewPanel(img.getWidth(), img.getHeight());
						previewImage = img;
						startFadeIn();
					}
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

		int tagW = forwardTag.getPreferredSize().width;
		JPanel tagWrapper = new JPanel(new BorderLayout());
		tagWrapper.setBackground(Color.LIGHT_GRAY);
		tagWrapper.setPreferredSize(new Dimension(tagW, CARD_H));
		tagWrapper.add(forwardTag, BorderLayout.NORTH);

		JPanel inner = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
			@Override
			public Dimension getPreferredSize() {
				int cardSlots = Math.max(getComponentCount() - 1, 0);
				int gap   = 4;
				int width = gap + tagW + gap + (CARD_H + gap) * cardSlots;
				return new Dimension(Math.max(width, tagW + gap * 2), CARD_H);
			}
		};
		inner.setBackground(Color.LIGHT_GRAY);
		inner.add(tagWrapper);

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
			@Override public void mouseEntered(MouseEvent e) {
				if (lbl.getIcon() != null) showZoomAt(p1ForwardUrls.get(idx), lbl);
			}
			@Override public void mouseExited(MouseEvent e) { hideZoom(); }
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
			boolean freezing = p1ForwardStates.get(idx) != BACKUP_FROZEN;
			int prevState = p1ForwardStates.get(idx);
			p1ForwardStates.set(idx, freezing ? BACKUP_FROZEN : BACKUP_NORMAL);
			animateFreezeForward(idx, freezing, prevState);
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

	private JPanel buildDamageZonePanel(String playerLabel, JComboBox<String> colorBox) {
		boolean isP1 = "P1".equals(playerLabel);

		// Inner panel: 7 mini-card slots stacked vertically.
		// For P1: shows card thumbnails and handles EX burst overlay.
		// For P2: shows plain letters (D-A-M-A-G-E-P2), same as before.
		JPanel slotsPanel;

		if (isP1) {
			slotsPanel = new JPanel(new GridLayout(7, 1, 2, 2)) {
				@Override public void setBackground(Color c) { /* paintComponent owns background */ }
				@Override protected void paintComponent(Graphics g) {
					g.setColor(Color.DARK_GRAY);
					g.fillRect(0, 0, getWidth(), getHeight());
				}
			};
			slotsPanel.setOpaque(true);

			String[] slotLetters = { "D", "A", "M", "A", "G", "E", "P1" };
			for (int i = 0; i < 7; i++) {
				final String letter = slotLetters[i];
				JPanel slot = new JPanel() {
					@Override public void setBackground(Color c) { /* paintComponent owns background */ }
					@Override protected void paintComponent(Graphics g) {
						Image img = (Image) getClientProperty("cardImg");
						Graphics2D g2 = (Graphics2D) g.create();
						g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
						g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
						g2.setColor(img != null ? Color.RED : Color.DARK_GRAY);
						g2.fillRect(0, 0, getWidth(), getHeight());
						if (img != null) {
							int iw = img.getWidth(null), ih = img.getHeight(null);
							if (iw > 0 && ih > 0) {
								int cardAreaW = getWidth() / 2;
								double scale = Math.min((double) cardAreaW / iw, (double) getHeight() / ih);
								int dw = (int)(iw * scale), dh = (int)(ih * scale);
								int dy = (getHeight() - dh) / 2;
								g2.drawImage(img, 0, dy, dw, dy + dh, 0, 0, iw, ih, null);
							}
						}
						g2.setFont(new Font("Pixel NES", Font.PLAIN, 14));
						g2.setColor(Color.WHITE);
						FontMetrics fm = g2.getFontMetrics();
						int tx = (getWidth() - fm.stringWidth(letter)) / 2;
						int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
						g2.drawString(letter, tx, ty);
						if (getClientProperty("isExBurst") == Boolean.TRUE) {
							g2.setFont(new Font("Pixel NES", Font.PLAIN, 9));
							FontMetrics exFm = g2.getFontMetrics();
							int exW = exFm.stringWidth("EX");
							int exX = getWidth() - exW - 3;
							int exY = exFm.getAscent() + 2;
							g2.setColor(Color.BLACK);
							g2.drawString("EX", exX + 1, exY + 1);
							g2.setColor(Color.YELLOW);
							g2.drawString("EX", exX, exY);
						}
						g2.dispose();
					}
				};
				slot.setOpaque(true);
				slot.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80), 1));
				slotsPanel.add(slot);
				p1DamageSlots[i] = slot;
			}

			slotsPanel.addMouseListener(new MouseAdapter() {
				@Override public void mousePressed(MouseEvent e) {
					if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
						JPopupMenu menu = new JPopupMenu();
						boolean gameActive = gameState.getCurrentPhase() != null;
						if (gameActive && !gameState.getP1MainDeck().isEmpty()) {
							JMenuItem dmgItem = new JMenuItem("Take 1 Damage");
							dmgItem.addActionListener(ae -> p1TakeDamage());
							menu.add(dmgItem);
						}
						boolean ex = slotsPanel.getClientProperty("exBurst") == Boolean.TRUE;
						if (ex) {
							JMenuItem clearEx = new JMenuItem("Dismiss EX");
							clearEx.addActionListener(ae -> {
								slotsPanel.putClientProperty("exBurst", Boolean.FALSE);
								for (JPanel s : p1DamageSlots) { if (s != null) s.repaint(); }
								slotsPanel.repaint();
							});
							menu.add(clearEx);
						}
						if (menu.getComponentCount() > 0) menu.show(slotsPanel, e.getX(), e.getY());
					} else {
						if (!gameState.getP1DamageZone().isEmpty()) showDamageZoneDialog();
					}
				}
			});

			p1DamageSlotPanel = slotsPanel;

		} else {
			// P2: plain letter display
			String[] letters = { "D", "A", "M", "A", "G", "E", playerLabel };
			slotsPanel = new JPanel(new GridLayout(7, 1, 2, 2));
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
		}

		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setPreferredSize(new Dimension(CARD_W, CARD_H * 2));
		panel.add(slotsPanel, BorderLayout.CENTER);
		panel.add(colorBox,   BorderLayout.SOUTH);
		return panel;
	}

	private ImageIcon scaledCardbackWithCount(Dimension size, int count) {
		Image base = new ImageIcon(getClass().getResource("/resources/cardback.jpg")).getImage();
		BufferedImage buf = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = buf.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.drawImage(base, 0, 0, size.width, size.height, null);
		String text = String.valueOf(count);
		g.setFont(new Font("Pixel NES", Font.PLAIN, 12));
		int textW = g.getFontMetrics().stringWidth(text);
		int textH = g.getFontMetrics().getAscent();
		int x = size.width - textW - 4;
		int y = textH + 4;
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(Color.WHITE);
		g.drawString(text, x, y);
		g.dispose();
		return new ImageIcon(buf);
	}

	private JComboBox<String> buildColorDropdown() {
		String[] items = new String[ElementColor.values().length + 1];
		items[0] = "Default";
		for (int i = 0; i < ElementColor.values().length; i++)
			items[i + 1] = ElementColor.values()[i].name().charAt(0)
					+ ElementColor.values()[i].name().substring(1).toLowerCase();
		JComboBox<String> box = new JComboBox<>(items);
		box.setFocusable(false);
		return box;
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

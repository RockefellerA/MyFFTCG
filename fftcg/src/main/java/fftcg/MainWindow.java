package fftcg;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.SoftBevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import fftcg.menu.FileMenu;
import fftcg.menu.HelpMenu;
import fftcg.menu.MultiplayerMenu;
import fftcg.net.ActionType;
import fftcg.net.GameConnection;
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
	private static final int    SIDE_MARGIN    = 4;                   // px between card and panel edge
	private static final double PREVIEW_SCALE  = 0.8;
	private static final int    RESIZE_HANDLE_W = 5;                 // draggable sidebar divider width
	private int sidePanelW = (int)(3 * CARD_W * PREVIEW_SCALE);   // updated on first image load
	private int previewH   =
			(int)(sidePanelW * (double) CARD_H / CARD_W);         // updated on first image load
	private boolean previewSized = false;
	private int nativeImgW   = 0;   // native card image dimensions (set on first hover)
	private int nativeImgH   = 0;
	private int minSidePanelW = 0;  // resize clamp bounds (set on first hover)
	private int maxSidePanelW = 0;

	// P1 zone labels that change during gameplay
	private JLabel p1DeckLabel;
	private JLabel p2DeckLabel;
	private CrystalDisplay p1CrystalDisplay;
	private CrystalDisplay p2CrystalDisplay;
	private JButton p1LimitLabel;
	private JPanel handPanel;
	private JLabel p1BreakLabel;
	private JLabel p2BreakLabel;
	private JLabel p2HandCountLabel;
	private GrayscaleLabel p1RemoveLabel;
	private GrayscaleLabel p2RemoveLabel;
	private JButton        p1RemoveButton;
	private JButton        p2RemoveButton;
	// Game event log
	private JTextArea gameLog;
	// Chat bar (enabled only when connected to multiplayer)
	private JTextField chatInput;
	private JButton    chatSendBtn;
	// Multiplayer menu reference (to access active connection)
	private MultiplayerMenu multiplayerMenu;
	// Side info panel (card preview + Next button + game log)
	private JPanel        sidePanel;
	private JPanel        sideWrapper;        // contains resizeHandle + sidePanel
	private JPanel        resizeHandle;       // draggable divider between board and sidebar
	private JPanel        cardPreviewPanel;   // custom-painted card preview
	private BufferedImage previewImage;       // current card to draw (null = empty)
	private float         previewAlpha  = 0f; // 0 = transparent, 1 = fully opaque
	private javax.swing.Timer fadeTimer;      // drives fade-in / fade-out animation
	// Opening hand confirmation popup
	private JWindow openingHandPopup;
	// Hand hover popover (deck zone mouseover)
	private JWindow handPopup;
	private javax.swing.Timer handPopupHideTimer;
	private boolean handCardMenuOpen = false;


	// --- Game state ---
	private final GameState gameState   = new GameState();
	// UI-only state (not owned by GameState)
	private JLabel[]    p1BackupLabels = new JLabel[5];
	private String[]    p1BackupUrls   = new String[5];
	private CardData[]  p1BackupCards  = new CardData[5];
	private CardState[] p1BackupStates = new CardState[5];

	private final List<JLabel>    p1ForwardLabels      = new ArrayList<>();
	private final List<String>    p1ForwardUrls;
	private final List<CardData>  p1ForwardCards       = new ArrayList<>();
	private final List<CardState> p1ForwardStates      = new ArrayList<>();
	private final List<Integer>   p1ForwardPlayedOnTurn = new ArrayList<>();
	private final List<Integer>   p1ForwardDamage       = new ArrayList<>();
	/** Top card of a Primed stack; {@code null} at each index means not primed. */
	private final List<CardData>  p1ForwardPrimedTop   = new ArrayList<>();
	private JPanel p1ForwardPanel;

	/** Turn number on which each backup slot was last filled (0 = empty/unknown). */
	private final int[] p1BackupPlayedOnTurn = new int[5];

	private final List<JLabel>   p1MonsterLabels      = new ArrayList<>();
	private final List<String>   p1MonsterUrls        = new ArrayList<>();
	private final List<CardData> p1MonsterCards       = new ArrayList<>();
	private final List<CardState> p1MonsterStates      = new ArrayList<>();
	private final List<Integer>  p1MonsterPlayedOnTurn = new ArrayList<>();
	private JPanel p1MonsterPanel;

	private int      p2DamageCount = 0;
	private JPanel[] p2DamageSlots = new JPanel[7];

	// P2 field state (managed by ComputerPlayer)
	private final JLabel[]     p2BackupLabels        = new JLabel[5];
	private final String[]     p2BackupUrls          = new String[5];
	private final CardData[]   p2BackupCards         = new CardData[5];
	private final CardState[]  p2BackupStates        = new CardState[5];
	private JPanel             p2ForwardPanel;
	private final List<JLabel>    p2ForwardLabels       = new ArrayList<>();
	private final List<String>    p2ForwardUrls         = new ArrayList<>();
	private final List<CardData>  p2ForwardCards        = new ArrayList<>();
	private final List<CardState> p2ForwardStates       = new ArrayList<>();
	private final List<Integer>   p2ForwardPlayedOnTurn = new ArrayList<>();
	private final List<Integer>   p2ForwardDamage       = new ArrayList<>();
	private ComputerPlayer        computerPlayer;

	private int             p1LbIndex   = 0;
	private final Set<Integer> spentLbIndices   = new HashSet<>();
	private final Set<Integer> p2SpentLbIndices = new HashSet<>();
	private JButton            p2LimitButton;

	// Damage zone UI
	private JPanel   p1DamageSlotPanel;
	private JPanel[] p1DamageSlots = new JPanel[7];
	private JPanel   p2DamageSlotPanel;

	// Next-phase button and its glow animation
	private JButton              nextPhaseButton;
	private javax.swing.Timer    glowTimer;
	private final float[]        glowAngle = { 0f };

	// Attack button and selection state for party attacks
	private JButton              attackButton;
	private final List<Integer>  p1AttackSelection = new ArrayList<>();

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
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override public void windowClosing(java.awt.event.WindowEvent e) {
				AppSettings.setSidePanelWidth(sidePanelW);
				AppSettings.save();
			}
		});
		frame.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		frame.getContentPane().setLayout(new BorderLayout());

		// --- Menu Bar ---
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);
		menuBar.add(new FileMenu(frame, this::startGame,
				() -> applySidePanelSide(AppSettings.getSidePanelSide())));
		multiplayerMenu = new MultiplayerMenu(frame,
				() -> {
					logEntry("Multiplayer connection established");
					SwingUtilities.invokeLater(() -> {
						chatInput.setEnabled(true);
						chatSendBtn.setEnabled(true);
					});
				},
				() -> SwingUtilities.invokeLater(() -> {
					chatInput.setEnabled(false);
					chatSendBtn.setEnabled(false);
				}),
				action -> {
					if (action.type() == ActionType.CHAT) {
						String msg = action.payload().optString("msg", "");
						if (!msg.isEmpty()) logEntry("[Opponent] " + msg);
					}
				});
		menuBar.add(multiplayerMenu);
		menuBar.add(new HelpMenu(frame));

		Dimension cardSize = new Dimension(CARD_W, CARD_H);

		// --- P2 Zones (top of screen) ---
		p2RemoveLabel = new GrayscaleLabel("");

		int CORNER_BAR_H = 28;
		int LIMIT_W      = (CARD_W * 3) / 4;   // 105 px
		int REMOVE_W     = CARD_W - LIMIT_W;    //  35 px

		p2LimitButton = new JButton("LIMIT");
		JButton lblLimit_1 = p2LimitButton;
		lblLimit_1.setToolTipText("Player 2 LB Deck");
		lblLimit_1.setFont(new Font("Pixel NES", Font.PLAIN, 10));
		lblLimit_1.setBackground(new Color(212, 175, 55));
		lblLimit_1.setForeground(Color.BLACK);
		lblLimit_1.setOpaque(true);
		lblLimit_1.setBorderPainted(false);
		lblLimit_1.setFocusPainted(false);
		lblLimit_1.setPreferredSize(new Dimension(LIMIT_W, CORNER_BAR_H));
		lblLimit_1.setMinimumSize(new Dimension(LIMIT_W, CORNER_BAR_H));
		lblLimit_1.setMaximumSize(new Dimension(LIMIT_W, CORNER_BAR_H));
		lblLimit_1.addActionListener(e -> showP2LbViewerDialog());

		p2BreakLabel = new JLabel("BREAK");
		p2BreakLabel.setToolTipText("Player 2 Break Zone");
		p2BreakLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p2BreakLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		p2BreakLabel.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		p2BreakLabel.setBackground(Color.DARK_GRAY);
		p2BreakLabel.setForeground(Color.WHITE);
		p2BreakLabel.setOpaque(true);
		p2BreakLabel.setPreferredSize(cardSize);
		p2BreakLabel.setMinimumSize(cardSize);
		p2BreakLabel.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) {
				List<CardData> zone = gameState.getP2BreakZone();
				if (!zone.isEmpty()) showZoomAt(zone.get(zone.size() - 1).imageUrl(), p2BreakLabel);
			}
			@Override public void mouseExited(MouseEvent e) { hideZoom(); }
			@Override public void mousePressed(MouseEvent e) {
				if (!gameState.getP2BreakZone().isEmpty()) { hideZoom(); showP2BreakZoneDialog(); }
			}
		});

		p2DeckLabel = new JLabel("DECK");
		p2DeckLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		p2DeckLabel.setToolTipText("Player 2 Deck");
		p2DeckLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p2DeckLabel.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		p2DeckLabel.setBackground(Color.DARK_GRAY);
		p2DeckLabel.setForeground(Color.WHITE);
		p2DeckLabel.setOpaque(true);

		p2RemoveButton = new JButton("RFP");
		p2RemoveButton.setToolTipText("Player 2 Removed From Play");
		p2RemoveButton.setFont(new Font("Pixel NES", Font.PLAIN, 7));
		p2RemoveButton.setBackground(new Color(30, 30, 30));
		p2RemoveButton.setForeground(Color.LIGHT_GRAY);
		p2RemoveButton.setOpaque(true);
		p2RemoveButton.setBorderPainted(false);
		p2RemoveButton.setFocusPainted(false);
		p2RemoveButton.setEnabled(false);
		p2RemoveButton.setPreferredSize(new Dimension(REMOVE_W, CORNER_BAR_H));
		p2RemoveButton.setMinimumSize(new Dimension(REMOVE_W, CORNER_BAR_H));
		p2RemoveButton.setMaximumSize(new Dimension(REMOVE_W, CORNER_BAR_H));
		p2RemoveButton.addActionListener(e -> showRemovedFromPlayDialog(p2RemoveLabel, "P2"));

		JPanel p2BottomBar = new JPanel(new GridBagLayout());
		p2BottomBar.setPreferredSize(new Dimension(CARD_W, CORNER_BAR_H));
		p2BottomBar.setMinimumSize(new Dimension(CARD_W, CORNER_BAR_H));
		{
			GridBagConstraints bbc = new GridBagConstraints();
			bbc.fill = GridBagConstraints.BOTH; bbc.weighty = 1.0; bbc.gridy = 0;
			bbc.gridx = 0; bbc.weightx = 0.75; p2BottomBar.add(lblLimit_1, bbc);
			bbc.gridx = 1; bbc.weightx = 0.25; p2BottomBar.add(p2RemoveButton, bbc);
		}

		p2DeckLabel.setPreferredSize(cardSize);
		p2DeckLabel.setMinimumSize(cardSize);

		p2CrystalDisplay = new CrystalDisplay(0);
		p2CrystalDisplay.setPreferredSize(new Dimension(REMOVE_W, CRYSTAL_H));
		p2CrystalDisplay.setMinimumSize(new Dimension(REMOVE_W, CRYSTAL_H));
		p2CrystalDisplay.setMaximumSize(new Dimension(REMOVE_W, CRYSTAL_H));

		JPanel p2CornerPanel = new JPanel(new BorderLayout(0, 0));
		p2CornerPanel.add(p2BreakLabel, BorderLayout.NORTH);
		p2CornerPanel.add(p2DeckLabel,  BorderLayout.CENTER);
		p2CornerPanel.add(p2BottomBar,  BorderLayout.SOUTH);

		p2HandCountLabel = new JLabel("P2 Hand: 0", SwingConstants.CENTER) {
			@Override protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
						RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				FontMetrics fm = g2.getFontMetrics(getFont());
				String text = getText();
				int x = (getWidth()  - fm.stringWidth(text)) / 2;
				int y = fm.getAscent();
				g2.setFont(getFont());
				g2.setColor(new Color(0, 0, 0, 180));
				g2.drawString(text, x + 1, y + 1);
				g2.setColor(getForeground());
				g2.drawString(text, x, y);
				g2.dispose();
			}
		};
		p2HandCountLabel.setFont(new Font("Pixel NES", Font.PLAIN, 10));
		p2HandCountLabel.setForeground(Color.LIGHT_GRAY);
		p2HandCountLabel.setOpaque(false);

		// Crystal display sits to the left of the hand-count label
		JPanel p2HandRow = new JPanel(new BorderLayout(0, 0));
		p2HandRow.setOpaque(false);
		p2HandRow.add(p2CrystalDisplay, BorderLayout.WEST);
		p2HandRow.add(p2HandCountLabel,  BorderLayout.CENTER);

		JPanel p2CornerWrapper = new JPanel(new BorderLayout(0, 2));
		p2CornerWrapper.setOpaque(false);
		p2CornerWrapper.add(p2CornerPanel, BorderLayout.CENTER);
		p2CornerWrapper.add(p2HandRow,     BorderLayout.SOUTH);

		JComboBox<String> p2ColorBox = buildColorDropdown();
		JPanel p2DamagePanel = buildDamageZonePanel("P2", p2ColorBox);

		JPanel p2BackupSlots = buildBackupZonePanel(p2BackupLabels);
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

		JPanel p2ZonesPanel = new JPanel(new GridBagLayout());
		{
			GridBagConstraints z = new GridBagConstraints();
			z.gridy = 0; z.fill = GridBagConstraints.NONE; z.anchor = GridBagConstraints.NORTH; z.weightx = 0;
			z.gridx = 0; p2ZonesPanel.add(p2CornerWrapper, z);
			z.gridx = 2; p2ZonesPanel.add(p2DamagePanel, z);
			z.gridx = 1; z.fill = GridBagConstraints.BOTH; z.weightx = 1.0; z.weighty = 1.0;
			p2ZonesPanel.add(p2MainArea, z);
		}

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

		// P1 limit button — gold, 3/4 of card width
		p1LimitLabel = new JButton("LIMIT");
		p1LimitLabel.setToolTipText("Player 1 LB Deck");
		p1LimitLabel.setFont(new Font("Pixel NES", Font.PLAIN, 10));
		p1LimitLabel.setBackground(new Color(212, 175, 55));
		p1LimitLabel.setForeground(Color.BLACK);
		p1LimitLabel.setOpaque(true);
		p1LimitLabel.setBorderPainted(false);
		p1LimitLabel.setFocusPainted(false);
		p1LimitLabel.setPreferredSize(new Dimension(LIMIT_W, CORNER_BAR_H));
		p1LimitLabel.setMinimumSize(new Dimension(LIMIT_W, CORNER_BAR_H));
		p1LimitLabel.setMaximumSize(new Dimension(LIMIT_W, CORNER_BAR_H));
		p1LimitLabel.addActionListener(e -> {
			GameState.GamePhase phase = gameState.getCurrentPhase();
			boolean isMainPhase = phase == GameState.GamePhase.MAIN_1
					|| phase == GameState.GamePhase.MAIN_2;
			if (!gameState.getP1LbDeck().isEmpty() && isMainPhase && !gameState.isP1GameOver()) showLbDialog();
		});

		p1RemoveLabel = new GrayscaleLabel("");

		p1RemoveButton = new JButton("RFP");
		p1RemoveButton.setToolTipText("Player 1 Removed From Play");
		p1RemoveButton.setFont(new Font("Pixel NES", Font.PLAIN, 7));
		p1RemoveButton.setBackground(new Color(30, 30, 30));
		p1RemoveButton.setForeground(Color.LIGHT_GRAY);
		p1RemoveButton.setOpaque(true);
		p1RemoveButton.setBorderPainted(false);
		p1RemoveButton.setFocusPainted(false);
		p1RemoveButton.setEnabled(false);
		p1RemoveButton.setPreferredSize(new Dimension(REMOVE_W, CORNER_BAR_H));
		p1RemoveButton.setMinimumSize(new Dimension(REMOVE_W, CORNER_BAR_H));
		p1RemoveButton.setMaximumSize(new Dimension(REMOVE_W, CORNER_BAR_H));
		p1RemoveButton.addActionListener(e -> showRemovedFromPlayDialog(p1RemoveLabel, "P1"));

		p1CrystalDisplay = new CrystalDisplay(0);
		p1CrystalDisplay.setPreferredSize(new Dimension(REMOVE_W, CRYSTAL_H));
		p1CrystalDisplay.setMinimumSize(new Dimension(REMOVE_W, CRYSTAL_H));
		p1CrystalDisplay.setMaximumSize(new Dimension(REMOVE_W, CRYSTAL_H));

		// Crystal sits above the full bar, pinned to the right to align with the RFP button
		JPanel p1CrystalRow = new JPanel(new BorderLayout(0, 0));
		p1CrystalRow.add(p1CrystalDisplay, BorderLayout.EAST);

		// Restore the limit button's original height constraint
		p1LimitLabel.setMaximumSize(new Dimension(LIMIT_W, CORNER_BAR_H));

		// Restore the original two-button top bar
		JPanel p1TopBar = new JPanel(new GridBagLayout());
		p1TopBar.setPreferredSize(new Dimension(CARD_W, CORNER_BAR_H));
		p1TopBar.setMinimumSize(new Dimension(CARD_W, CORNER_BAR_H));
		{
			GridBagConstraints tbc = new GridBagConstraints();
			tbc.fill = GridBagConstraints.BOTH; tbc.weighty = 1.0; tbc.gridy = 0;
			tbc.gridx = 0; tbc.weightx = 0.75; p1TopBar.add(p1LimitLabel, tbc);
			tbc.gridx = 1; tbc.weightx = 0.25; p1TopBar.add(p1RemoveButton, tbc);
		}

		// Wrapper: crystal row above, top bar below
		JPanel p1NorthWrapper = new JPanel(new BorderLayout(0, 0));
		p1NorthWrapper.add(p1CrystalRow, BorderLayout.NORTH);
		p1NorthWrapper.add(p1TopBar,     BorderLayout.SOUTH);

		p1DeckLabel.setPreferredSize(cardSize);
		p1DeckLabel.setMinimumSize(cardSize);

		JPanel p1CornerPanel = new JPanel(new BorderLayout(0, 0));
		p1CornerPanel.add(p1NorthWrapper, BorderLayout.NORTH);
		p1CornerPanel.add(p1DeckLabel,    BorderLayout.CENTER);
		p1CornerPanel.add(p1BreakLabel,   BorderLayout.SOUTH);

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

		JPanel p1ZonesPanel = new JPanel(new GridBagLayout());
		{
			GridBagConstraints z = new GridBagConstraints();
			z.gridy = 0; z.fill = GridBagConstraints.NONE; z.anchor = GridBagConstraints.SOUTH; z.weightx = 0;
			z.gridx = 0; p1ZonesPanel.add(p1LeftGroup,   z);
			z.gridx = 2; p1ZonesPanel.add(p1CornerPanel, z);
			z.gridx = 1; z.fill = GridBagConstraints.BOTH; z.weightx = 1.0; z.weighty = 1.0;
			p1ZonesPanel.add(p1MainArea, z);
		}

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

		// Attack button (enabled only during P1's Attack Phase with a selection)
		attackButton = new JButton("Attack");
		attackButton.setFont(new Font("Pixel NES", Font.PLAIN, 12));
		attackButton.setEnabled(false);
		attackButton.setFocusPainted(false);
		attackButton.addActionListener(e -> {
			if (!p1AttackSelection.isEmpty()) {
				List<Integer> sel = new ArrayList<>(p1AttackSelection);
				p1AttackSelection.clear();
				refreshAttackButton();
				executeP1Attack(sel);
			}
		});

		// Next-phase button, centred below the preview
		JPanel nextBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
		nextBtnPanel.add(nextPhaseButton);
		nextBtnPanel.add(attackButton);

		JPanel sideNorth = new JPanel();
		sideNorth.setLayout(new BoxLayout(sideNorth, BoxLayout.Y_AXIS));
		sideNorth.add(cardPreviewPanel);
		sideNorth.add(nextBtnPanel);

		// Game log (scrollable, fills the rest of the side panel)
		gameLog = new JTextArea();
		gameLog.setEditable(false);
		gameLog.setLineWrap(true);
		gameLog.setWrapStyleWord(true);
		gameLog.setFont(new Font("Courier New", Font.PLAIN, 12));
		gameLog.setBackground(Color.WHITE);
		gameLog.setForeground(Color.BLACK);
		gameLog.setMargin(new Insets(4, 4, 4, 4));
		gameLog.setCaretColor(Color.WHITE);
		logEntry("Welcome to MyFFTCG!");

		JScrollPane logScrollPane = new JScrollPane(gameLog,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		// ── Chat bar ─────────────────────────────────────────────────────────
		chatInput = new JTextField();
		chatInput.setFont(new Font("Serif", Font.PLAIN, 11));
		chatInput.setEnabled(false);
		chatInput.setToolTipText("Connect to multiplayer to chat");

		chatSendBtn = new JButton("Send");
		chatSendBtn.setFont(new Font("Serif", Font.PLAIN, 11));
		chatSendBtn.setEnabled(false);

		Runnable sendChat = () -> {
			String text = chatInput.getText().trim();
			if (text.isEmpty()) return;
			GameConnection conn = multiplayerMenu == null ? null : multiplayerMenu.getActiveConnection();
			if (conn == null) return;
			conn.send(fftcg.net.GameAction.of(ActionType.CHAT, new org.json.JSONObject().put("msg", text)));
			logEntry("[You] " + text);
			chatInput.setText("");
		};
		chatInput.addActionListener(e -> sendChat.run());
		chatSendBtn.addActionListener(e -> sendChat.run());

		JPanel chatPanel = new JPanel(new BorderLayout(4, 0));
		chatPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));
		chatPanel.add(chatInput,   BorderLayout.CENTER);
		chatPanel.add(chatSendBtn, BorderLayout.EAST);

		JPanel logWithChat = new JPanel(new BorderLayout());
		logWithChat.add(logScrollPane, BorderLayout.CENTER);
		logWithChat.add(chatPanel,     BorderLayout.SOUTH);

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
		sidePanel.add(sideNorth,    BorderLayout.NORTH);
		sidePanel.add(logWithChat,  BorderLayout.CENTER);
		sidePanel.add(handPanel,    BorderLayout.SOUTH);

		// Draggable divider between game board and side panel
		resizeHandle = new JPanel();
		resizeHandle.setPreferredSize(new Dimension(RESIZE_HANDLE_W, 0));
		resizeHandle.setBackground(Color.LIGHT_GRAY);
		MouseAdapter sideResizer = new MouseAdapter() {
			private int pressScreenX;
			private int pressW;
			@Override public void mousePressed(MouseEvent e) {
				pressScreenX = e.getXOnScreen();
				pressW = sidePanel.getWidth();
			}
			@Override public void mouseDragged(MouseEvent e) {
				if (nativeImgW == 0) return;
				int dx = e.getXOnScreen() - pressScreenX;
				boolean right = "right".equals(AppSettings.getSidePanelSide());
				int newW = right ? pressW - dx : pressW + dx;
				newW = Math.max(minSidePanelW, Math.min(maxSidePanelW, newW));
				setSidePanelWidth(newW);
			}
		};
		resizeHandle.addMouseListener(sideResizer);
		resizeHandle.addMouseMotionListener(sideResizer);

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
		if (sideWrapper != null) frame.getContentPane().remove(sideWrapper);
		boolean right = "right".equals(side);
		sidePanel.setBorder(null);
		resizeHandle.setCursor(Cursor.getPredefinedCursor(
				right ? Cursor.W_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR));
		sideWrapper = new JPanel(new BorderLayout());
		sideWrapper.setPreferredSize(new Dimension(sidePanelW + RESIZE_HANDLE_W, 0));
		if (right) {
			sideWrapper.add(resizeHandle, BorderLayout.WEST);
			sideWrapper.add(sidePanel,    BorderLayout.CENTER);
		} else {
			sideWrapper.add(sidePanel,    BorderLayout.CENTER);
			sideWrapper.add(resizeHandle, BorderLayout.EAST);
		}
		frame.getContentPane().add(sideWrapper, right ? BorderLayout.EAST : BorderLayout.WEST);
		frame.revalidate();
		frame.repaint();
	}

	// -------------------------------------------------------------------------
	// Game startup
	// -------------------------------------------------------------------------

	private void startGame(int deckId) {
		gameState.reset();
		p1LbIndex = 0;
		computerPlayer = new ComputerPlayer();
		clearUIZones();
		if (nextPhaseButton != null) nextPhaseButton.setEnabled(false);
		if (gameLog != null) gameLog.setText("");
		logEntry("Game Start");
		refreshP1HandLabel();

		new SwingWorker<Void, Void>() {
			List<DeckCardDetail> p1Cards;
			List<DeckCardDetail> p2Cards;
			String               p2DeckName;

			@Override
			protected Void doInBackground() throws Exception {
				try (DeckDatabase db = new DeckDatabase()) {
					p1Cards = db.getDeckCardsDetailed(deckId);

					List<scraper.DeckDatabase.DeckSummary> eligible = db.getDecksSummary()
							.stream()
							.filter(d -> d.mainCardCount() == 50 && d.id() != deckId)
							.collect(java.util.stream.Collectors.toList());
					if (!eligible.isEmpty()) {
						scraper.DeckDatabase.DeckSummary chosen =
								eligible.get(new java.util.Random().nextInt(eligible.size()));
						p2DeckName = chosen.name();
						p2Cards    = db.getDeckCardsDetailed(chosen.id());
					}
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
				for (DeckCardDetail card : p1Cards) {
					String tx = card.textEn();
					CardData cd = new CardData(card.imageUrl(), card.name(), card.element(),
							card.cost(), card.power(), card.type(), card.isLb(), card.lbCost(), card.exBurst(),
							card.multicard(), CardData.parseTraits(tx),
							CardData.parseWarpValue(tx), CardData.parseWarpCost(tx),
							CardData.parsePrimingTarget(tx), CardData.parsePrimingCost(tx),
							CardData.parseActionAbilities(tx));
					if (card.isLb()) lb.add(cd);
					else             main.add(cd);
				}
				gameState.initializeDeck(main, lb);
				refreshP1DeckLabel();
				refreshP1LimitLabel();
				drawOpeningHand();

				if (p2Cards != null) {
					List<CardData> p2Main = new ArrayList<>();
					List<CardData> p2Lb   = new ArrayList<>();
					for (DeckCardDetail card : p2Cards) {
						String tx = card.textEn();
						CardData cd = new CardData(card.imageUrl(), card.name(), card.element(),
								card.cost(), card.power(), card.type(), card.isLb(), card.lbCost(), card.exBurst(),
								card.multicard(), CardData.parseTraits(tx),
								CardData.parseWarpValue(tx), CardData.parseWarpCost(tx),
								CardData.parsePrimingTarget(tx), CardData.parsePrimingCost(tx),
								CardData.parseActionAbilities(tx));
						if (card.isLb()) p2Lb.add(cd);
						else             p2Main.add(cd);
					}
					gameState.initializeP2Deck(p2Main);
					gameState.initializeP2LbDeck(p2Lb);
					refreshP2DeckLabel();
					refreshP2HandCountLabel();
					refreshP2LimitButton();
					logEntry("P2 deck: " + p2DeckName);
				}
			}
		}.execute();
	}

	// -------------------------------------------------------------------------
	// P1 deck interaction
	// -------------------------------------------------------------------------

	private void onP1DeckClicked() {
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
					if (!mulliganAvailable) return;
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
				mulliganAvailable ? "Click a card to select it, then click another to swap positions." : " ",
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
			boolean p1GoesFirst = Math.random() < 0.5;
			GameState.Player firstPlayer = p1GoesFirst
					? GameState.Player.P1 : GameState.Player.P2;
			gameState.startFirstTurn(firstPlayer);
			refreshP1HandLabel();
			if (p1GoesFirst) {
				logEntry("Coin flip: You go first!");
				logEntry("Turn 1 — Active Phase");
				if (nextPhaseButton != null) nextPhaseButton.setEnabled(true);
				onNextPhase();
			} else {
				logEntry("Coin flip: Opponent goes first!");
				if (nextPhaseButton != null) nextPhaseButton.setEnabled(false);
				computerPlayer.runTurn();
			}
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

	private void refreshP2DeckLabel() {
		if (p2DeckLabel == null) return;
		int count = gameState.getP2MainDeck().size();
		if (count == 0) {
			p2DeckLabel.setIcon(null);
			p2DeckLabel.setText("DECK");
		} else {
			p2DeckLabel.setIcon(scaledCardbackWithCount(new Dimension(CARD_W, CARD_H), count));
			p2DeckLabel.setText(null);
		}
	}

	private void refreshP2HandCountLabel() {
		if (p2HandCountLabel == null) return;
		p2HandCountLabel.setText("P2 Hand: " + gameState.getP2Hand().size());
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
		if (gameState.getCurrentPlayer() == GameState.Player.P2) return;
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
					triggerGameOver("Milled Out - You Lose!");
					return;
				}
				// No choices to make during Draw phase — advance automatically
				onNextPhase();
				break;
			}

			case DRAW:
				gameState.advancePhase();   // DRAW → MAIN_1
				logEntry("Main Phase 1");
				processWarpCounters();
				break;

			case MAIN_1:
				p1AttackSelection.clear();
				gameState.advancePhase();   // MAIN_1 → ATTACK
				refreshAttackButton();
				logEntry("Attack Phase");
				refreshAllForwardSlots();
				if (!hasAttackableForward() && !hasBackAttackInHand()) {
					logEntry("No attackers available — skipping to Main Phase 2");
					onNextPhase();
				}
				break;

			case ATTACK:
				p1AttackSelection.clear();
				refreshAttackButton();
				gameState.advancePhase();   // ATTACK → MAIN_2
				refreshAllForwardSlots();
				logEntry("Main Phase 2");
				break;

			case MAIN_2:
				gameState.advancePhase();   // MAIN_2 → END
				logEntry("End Phase");
				for (int i = 0; i < p1ForwardDamage.size(); i++) p1ForwardDamage.set(i, 0);
				for (int i = 0; i < p1ForwardCards.size(); i++) refreshP1ForwardSlot(i);
				for (int i = 0; i < p2ForwardDamage.size(); i++) p2ForwardDamage.set(i, 0);
				for (int i = 0; i < p2ForwardCards.size(); i++) refreshP2ForwardSlot(i);
				showEndPhaseDiscardDialog();
				onNextPhase();             // END → ACTIVE (auto-advance)
				break;

			case END: {
				// END → ACTIVE: increments turn number and switches to P2
				gameState.advancePhase();
				nextPhaseButton.setEnabled(false);
				computerPlayer.runTurn();
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
				p1BackupLabels[i].setText(null);
			}
			p1BackupUrls[i]   = null;
			p1BackupCards[i]  = null;
			p1BackupStates[i] = CardState.NORMAL;
		}

		// Forward zone
		if (p1ForwardPanel != null) {
			p1ForwardPanel.removeAll();
			p1ForwardPanel.revalidate();
			p1ForwardPanel.repaint();
		}
		p1ForwardLabels.clear();
		p1ForwardUrls.clear();
		p1ForwardCards.clear();
		p1ForwardStates.clear();
		p1ForwardPlayedOnTurn.clear();
		p1ForwardDamage.clear();
		p1ForwardPrimedTop.clear();
		p1AttackSelection.clear();
		java.util.Arrays.fill(p1BackupPlayedOnTurn, 0);

		// Monster zone
		if (p1MonsterPanel != null) {
			p1MonsterPanel.removeAll();
			p1MonsterPanel.revalidate();
			p1MonsterPanel.repaint();
		}
		p1MonsterLabels.clear();
		p1MonsterUrls.clear();
		p1MonsterCards.clear();
		p1MonsterStates.clear();
		p1MonsterPlayedOnTurn.clear();
		spentLbIndices.clear();
		p2SpentLbIndices.clear();

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

		// Break zone labels
		refreshP1BreakLabel();
		refreshP2BreakLabel();

		// Limit labels
		refreshP1LimitLabel();
		refreshP2LimitButton();

		// Removed from play labels
		p1RemoveLabel.setIcon(null);
		p1RemoveLabel.setUrl(null);
		p2RemoveLabel.setIcon(null);
		p2RemoveLabel.setUrl(null);
		refreshRemoveButtons();

		// Crystal badges
		refreshCrystalDisplays();

		// P2 backup slots
		for (int i = 0; i < p2BackupCards.length; i++) {
			if (p2BackupLabels[i] != null) {
				p2BackupLabels[i].setIcon(null);
				p2BackupLabels[i].setText(null);
			}
			p2BackupUrls[i]   = null;
			p2BackupCards[i]  = null;
			p2BackupStates[i] = CardState.NORMAL;
		}

		// P2 forward zone
		if (p2ForwardPanel != null) {
			p2ForwardPanel.removeAll();
			p2ForwardPanel.revalidate();
			p2ForwardPanel.repaint();
		}
		p2ForwardLabels.clear();
		p2ForwardUrls.clear();
		p2ForwardCards.clear();
		p2ForwardStates.clear();
		p2ForwardPlayedOnTurn.clear();
		p2ForwardDamage.clear();

		// Reset P2 damage zone display
		p2DamageCount = 0;
		for (JPanel slot : p2DamageSlots) {
			if (slot != null) {
				slot.putClientProperty("cardImg", null);
				slot.putClientProperty("isExBurst", null);
				slot.repaint();
			}
		}
		refreshP2DeckLabel();
		refreshP2HandCountLabel();
	}

	// -------------------------------------------------------------------------
	// P1 LB deck interaction
	// -------------------------------------------------------------------------

	private void refreshP1LimitLabel() {
		int total    = gameState.getP1LbDeck().size();
		int playable = total - spentLbIndices.size();
		if (total == 0) {
			p1LimitLabel.setText("LIMIT");
			p1LimitLabel.setForeground(new Color(80, 65, 20));
		} else {
			p1LimitLabel.setText("LIMIT -" + playable + "-");
			p1LimitLabel.setForeground(Color.BLACK);
		}
	}


	private void refreshP2LimitButton() {
		if (p2LimitButton == null) return;
		int total    = gameState.getP2LbDeck().size();
		int playable = total - p2SpentLbIndices.size();
		if (total == 0) {
			p2LimitButton.setText("LIMIT");
			p2LimitButton.setForeground(new Color(80, 65, 20));
		} else {
			p2LimitButton.setText("LIMIT -" + playable + "-");
			p2LimitButton.setForeground(Color.BLACK);
		}
	}

	/** Shows P2's LB deck: cardback for unplayed cards, face-up for spent ones. */
	private void showP2LbViewerDialog() {
		List<CardData> lbDeck = gameState.getP2LbDeck();
		if (lbDeck.isEmpty()) {
			JOptionPane.showMessageDialog(frame, "P2 has no LB cards.",
					"P2 Limit Break Deck", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		JDialog dlg = new JDialog(frame, "P2 Limit Break Deck (" + lbDeck.size() + " cards)", true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

		for (int i = 0; i < lbDeck.size(); i++) {
			final int idx   = i;
			final CardData cd = lbDeck.get(i);
			boolean spent   = p2SpentLbIndices.contains(idx);

			JLabel lbl = new JLabel("...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
			lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true);
			lbl.setBackground(Color.DARK_GRAY);
			if (spent) {
				lbl.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1));
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mouseEntered(MouseEvent e) { showZoomAt(cd.imageUrl(), lbl); }
					@Override public void mouseExited(MouseEvent e)  { hideZoom(); }
				});
			} else {
				lbl.setBorder(BorderFactory.createLineBorder(new Color(212, 175, 55), 2));
			}

			new SwingWorker<ImageIcon, Void>() {
				final boolean loadFace = spent;
				@Override protected ImageIcon doInBackground() throws Exception {
					String url = loadFace ? cd.imageUrl()
							: getClass().getResource("/resources/cardback.jpg").toString();
					Image img = loadFace ? ImageCache.load(url)
							: new ImageIcon(getClass().getResource("/resources/cardback.jpg")).getImage();
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

			JPanel wrapper = new JPanel(new BorderLayout(0, 4));
			wrapper.setBackground(cardsPanel.getBackground());
			JLabel nameLabel = new JLabel(spent ? cd.name() : "???", SwingConstants.CENTER);
			nameLabel.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			nameLabel.setPreferredSize(new Dimension(CARD_W, 18));
			wrapper.add(lbl,       BorderLayout.CENTER);
			wrapper.add(nameLabel, BorderLayout.SOUTH);
			cardsPanel.add(wrapper);
		}

		JScrollPane scrollPane = new JScrollPane(cardsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setPreferredSize(new Dimension(
				Math.min(lbDeck.size() * (CARD_W + 16) + 16, 900), CARD_H + 60));

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

	private void refreshRemoveButtons() {
		if (p1RemoveButton != null)
			p1RemoveButton.setEnabled(!gameState.getP1WarpZone().isEmpty()
					|| !gameState.getP1PermanentRfp().isEmpty());
		if (p2RemoveButton != null)
			p2RemoveButton.setEnabled(p2RemoveLabel != null && p2RemoveLabel.getUrl() != null);
	}

	/** Updates the P1 RFP label to show the most recently added removed card (warp or permanent). */
	private void refreshP1WarpZoneUI() {
		List<GameState.WarpEntry> zone = gameState.getP1WarpZone();
		List<CardData>            perm = gameState.getP1PermanentRfp();
		if (zone.isEmpty() && perm.isEmpty()) {
			p1RemoveLabel.setIcon(null);
			p1RemoveLabel.setUrl(null);
			refreshRemoveButtons();
			return;
		}
		// Prefer the last-added permanent RFP card for the label; fall back to last warp card
		String url = !perm.isEmpty()
				? perm.get(perm.size() - 1).imageUrl()
				: zone.get(zone.size() - 1).card.imageUrl();
		p1RemoveLabel.setUrl(url);
		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image img = ImageCache.load(url);
				return img == null ? null
						: new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
			}
			@Override protected void done() {
				try { ImageIcon ic = get(); if (ic != null) { p1RemoveLabel.setIcon(ic); } }
				catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
		refreshRemoveButtons();
	}

	/**
	 * Decrements Warp counters on every card in P1's warp zone at the start of Main Phase 1.
	 * Cards whose counter hits 0 are pushed onto the Stack as auto-abilities and resolved
	 * to the field.
	 */
	private void processWarpCounters() {
		List<GameState.WarpEntry> zone = gameState.getP1WarpZone();
		if (zone.isEmpty()) return;

		// Log the decrement for every card before we tick (zone is a live view)
		for (GameState.WarpEntry entry : zone) {
			int before = entry.counters;
			int after  = before - 1;
			logEntry("Warp: \"" + entry.card.name() + "\" counter " + before + " → " + after
					+ (after == 0 ? " (resolving!)" : ""));
		}

		List<CardData> resolved = gameState.tickP1WarpCounters();
		for (CardData card : resolved) {
			logEntry("Warp: \"" + card.name() + "\" enters play (auto-ability)");
			gameState.pushStack(card);
			gameState.popStack();
			if (card.isForward()) {
				placeCardInForwardZone(card);
			} else if (card.isBackup()) {
				if (hasAvailableBackupSlot()) placeCardInFirstBackupSlot(card);
				else {
					gameState.getP1BreakZone().add(card);
					logEntry("  No backup slot — \"" + card.name() + "\" → Break Zone");
				}
			} else if (card.isMonster()) {
				placeCardInMonsterZone(card);
			}
		}
		if (!resolved.isEmpty()) refreshP1BreakLabel();
		refreshP1WarpZoneUI();
	}

	private void showRemovedFromPlayDialog(GrayscaleLabel removeLabel, String player) {
		List<GameState.WarpEntry> warpZone = gameState.getP1WarpZone();
		List<CardData>            permZone = gameState.getP1PermanentRfp();
		if (warpZone.isEmpty() && permZone.isEmpty()) return;

		int total = warpZone.size() + permZone.size();
		JDialog dlg = new JDialog(frame, player + " — Removed From Play (" + total
				+ " card" + (total != 1 ? "s" : "") + ")", true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

		// Warp zone cards (show remaining counter)
		for (GameState.WarpEntry entry : warpZone) {
			JPanel wrapper = new JPanel(new BorderLayout(0, 4));
			wrapper.setBackground(cardsPanel.getBackground());
			JLabel lbl = makeRfpCardLabel(entry.card.imageUrl());
			JLabel info = new JLabel(entry.card.name() + "  [" + entry.counters + "]", SwingConstants.CENTER);
			info.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			info.setPreferredSize(new Dimension(CARD_W, 18));
			wrapper.add(lbl, BorderLayout.CENTER);
			wrapper.add(info, BorderLayout.SOUTH);
			cardsPanel.add(wrapper);
		}

		// Permanent RFP cards (Primed top cards, etc.)
		for (CardData card : permZone) {
			JPanel wrapper = new JPanel(new BorderLayout(0, 4));
			wrapper.setBackground(cardsPanel.getBackground());
			JLabel lbl = makeRfpCardLabel(card.imageUrl());
			JLabel info = new JLabel(card.name() + "  [RFG]", SwingConstants.CENTER);
			info.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			info.setPreferredSize(new Dimension(CARD_W, 18));
			wrapper.add(lbl, BorderLayout.CENTER);
			wrapper.add(info, BorderLayout.SOUTH);
			cardsPanel.add(wrapper);
		}

		dlg.getContentPane().add(new JScrollPane(cardsPanel));
		dlg.pack();
		dlg.setLocationRelativeTo(frame);
		dlg.setVisible(true);
	}

	private JLabel makeRfpCardLabel(String imageUrl) {
		JLabel lbl = new JLabel("...", SwingConstants.CENTER);
		lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
		lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
		lbl.setOpaque(true);
		lbl.setBackground(Color.DARK_GRAY);
		lbl.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
		lbl.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) { if (lbl.getIcon() != null) showZoomAt(imageUrl, lbl); }
			@Override public void mouseExited(MouseEvent e)  { hideZoom(); }
		});
		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image img = ImageCache.load(imageUrl);
				return img == null ? null : new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
			}
			@Override protected void done() {
				try { ImageIcon ic = get(); if (ic != null) { lbl.setIcon(ic); lbl.setText(null); } }
				catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
		return lbl;
	}

	private void showBreakZoneDialog() { showBreakZoneDialog(gameState.getP1BreakZone(), "P1 Break Zone"); }
	private void showP2BreakZoneDialog() { showBreakZoneDialog(gameState.getP2BreakZone(), "P2 Break Zone"); }

	private void showBreakZoneDialog(List<CardData> zone, String title) {
		if (zone.isEmpty()) return;

		JDialog dlg = new JDialog(frame, title + " (" + zone.size() + " cards)", true);
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

	private void p2TakeDamage() {
		CardData drawn = gameState.drawToP2DamageZone();
		p2DamageCount++;
		boolean isEx = drawn != null && drawn.exBurst();
		String cardInfo = drawn != null ? " — " + drawn.name() + (isEx ? " [EX BURST!]" : "") : "";
		logEntry("P2 takes 1 damage (" + p2DamageCount + "/7)" + cardInfo);

		int slotIdx = p2DamageCount - 1;
		if (slotIdx >= 0 && slotIdx < p2DamageSlots.length && p2DamageSlots[slotIdx] != null) {
			JPanel slot = p2DamageSlots[slotIdx];
			slot.putClientProperty("isExBurst", isEx ? Boolean.TRUE : Boolean.FALSE);
			slot.repaint();
			if (drawn != null) {
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
		refreshP2DeckLabel();
		if (p2DamageCount >= 7) {
			triggerGameOver("Player 2 Defeated - You Win!");
		}
	}

	// -------------------------------------------------------------------------
	// Combat: breaking forwards
	// -------------------------------------------------------------------------

	/** Removes P1's forward at {@code idx} from the field and sends it to P1's Break Zone. */
	private void breakP1Forward(int idx) {
		if (idx < 0 || idx >= p1ForwardCards.size()) return;
		CardData card    = p1ForwardCards.get(idx);
		CardData topCard = p1ForwardPrimedTop.get(idx);

		if (topCard != null) {
			// Primed: both cards move to break zone, then top card is immediately RFP'd
			gameState.getP1BreakZone().add(card);
			gameState.getP1BreakZone().add(topCard);
			logEntry(card.name() + " + " + topCard.name() + " → Break Zone (Primed)");
			gameState.getP1BreakZone().remove(topCard);
			gameState.addToP1PermanentRfp(topCard);
			logEntry(topCard.name() + " → Removed From Game");
		} else {
			gameState.getP1BreakZone().add(card);
			logEntry(card.name() + " → Break Zone");
		}

		p1ForwardCards.remove(idx);
		p1ForwardUrls.remove(idx);
		p1ForwardStates.remove(idx);
		p1ForwardPlayedOnTurn.remove(idx);
		p1ForwardDamage.remove(idx);
		p1ForwardPrimedTop.remove(idx);
		p1ForwardLabels.remove(idx);

		if (p1ForwardPanel != null) {
			p1ForwardPanel.removeAll();
			p1ForwardLabels.clear();
			for (int i = 0; i < p1ForwardCards.size(); i++) {
				final int fi = i;
				JLabel lbl = new JLabel("", SwingConstants.CENTER);
				lbl.setPreferredSize(new Dimension(CARD_H, CARD_H));
				lbl.setMinimumSize(new Dimension(CARD_H, CARD_H));
				lbl.setOpaque(false);
				lbl.setForeground(Color.DARK_GRAY);
				lbl.setFont(new Font("Pixel NES", Font.PLAIN, 11));
				lbl.setBorder(BorderFactory.createEmptyBorder());
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mousePressed(MouseEvent e) {
						if (lbl.getIcon() == null) return;
						if (SwingUtilities.isLeftMouseButton(e)
								&& gameState.getCurrentPhase() == GameState.GamePhase.ATTACK) {
							toggleAttackSelection(fi);
						} else {
							showForwardContextMenu(fi, lbl, e);
						}
					}
					@Override public void mouseEntered(MouseEvent e) {
						if (lbl.getIcon() == null) return;
						CardData top = p1ForwardPrimedTop.get(fi);
						showZoomAt(top != null ? top.imageUrl() : p1ForwardUrls.get(fi), lbl);
					}
					@Override public void mouseExited(MouseEvent e) { hideZoom(); }
				});
				p1ForwardLabels.add(lbl);
				p1ForwardPanel.add(lbl);
			}
			p1ForwardPanel.revalidate();
			p1ForwardPanel.repaint();
			for (int i = 0; i < p1ForwardCards.size(); i++) refreshP1ForwardSlot(i);
		}
		refreshP1BreakLabel();
	}

	/** Removes P2's forward at {@code idx} from the field and sends it to P2's Break Zone. */
	private void breakP2Forward(int idx) {
		if (idx < 0 || idx >= p2ForwardCards.size()) return;
		CardData card = p2ForwardCards.get(idx);
		gameState.getP2BreakZone().add(card);
		logEntry("[P2] " + card.name() + " → Break Zone");

		p2ForwardCards.remove(idx);
		p2ForwardUrls.remove(idx);
		p2ForwardStates.remove(idx);
		p2ForwardPlayedOnTurn.remove(idx);
		p2ForwardDamage.remove(idx);
		p2ForwardLabels.remove(idx);

		if (p2ForwardPanel != null) {
			p2ForwardPanel.removeAll();
			p2ForwardLabels.clear();
			for (int i = 0; i < p2ForwardCards.size(); i++) {
				final int fi = i;
				JLabel lbl = new JLabel("", SwingConstants.CENTER);
				lbl.setPreferredSize(new Dimension(CARD_H, CARD_H));
				lbl.setMinimumSize(new Dimension(CARD_H, CARD_H));
				lbl.setOpaque(false);
				lbl.setFont(new Font("Pixel NES", Font.PLAIN, 11));
				lbl.setBorder(BorderFactory.createEmptyBorder());
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mouseEntered(MouseEvent e) {
						if (lbl.getIcon() != null) showZoomAt(p2ForwardUrls.get(fi), lbl);
					}
					@Override public void mouseExited(MouseEvent e) { hideZoom(); }
				});
				p2ForwardLabels.add(lbl);
				p2ForwardPanel.add(lbl);
			}
			p2ForwardPanel.revalidate();
			p2ForwardPanel.repaint();
			for (int i = 0; i < p2ForwardCards.size(); i++) refreshP2ForwardSlot(i);
		}
		refreshP2BreakLabel();
	}

	/**
	 * Resolves combat between an attacker and a blocker.
	 * A forward breaks when the opponent's power equals or exceeds its own power.
	 * First Strike: if one side has it and the other doesn't, that side strikes first;
	 * if the strike kills the opponent, the survivor takes no damage.
	 */
	private void resolveCombat(CardData attacker, boolean attackerIsP1, int attackerIdx,
			CardData blocker, boolean blockerIsP1, int blockerIdx) {
		boolean attackerFirst = attacker.hasTrait(CardData.Trait.FIRST_STRIKE)
				&& !blocker.hasTrait(CardData.Trait.FIRST_STRIKE);
		boolean blockerFirst = blocker.hasTrait(CardData.Trait.FIRST_STRIKE)
				&& !attacker.hasTrait(CardData.Trait.FIRST_STRIKE);

		logEntry((attackerIsP1 ? "" : "[P2] ") + attacker.name() + " (" + attacker.power() + ")"
				+ " vs " + (blockerIsP1 ? "" : "[P2] ") + blocker.name() + " (" + blocker.power() + ")");

		boolean attackerBroken = blocker.power() >= attacker.power();
		boolean blockerBroken  = attacker.power() >= blocker.power();

		if (attackerFirst && blockerBroken) {
			attackerBroken = false;
		} else if (blockerFirst && attackerBroken) {
			blockerBroken = false;
		}

		if (attackerBroken) {
			if (attackerIsP1) breakP1Forward(attackerIdx);
			else              breakP2Forward(attackerIdx);
		} else {
			// Attacker survives — accumulate damage it received from the blocker
			int received = blockerFirst ? 0 : blocker.power();
			if (received > 0) {
				if (attackerIsP1) {
					p1ForwardDamage.set(attackerIdx, p1ForwardDamage.get(attackerIdx) + received);
					refreshP1ForwardSlot(attackerIdx);
				} else {
					p2ForwardDamage.set(attackerIdx, p2ForwardDamage.get(attackerIdx) + received);
					refreshP2ForwardSlot(attackerIdx);
				}
			}
		}
		if (blockerBroken) {
			if (blockerIsP1) breakP1Forward(blockerIdx);
			else             breakP2Forward(blockerIdx);
		} else {
			// Blocker survives — accumulate damage it received from the attacker
			int received = attackerFirst ? 0 : attacker.power();
			if (received > 0) {
				if (blockerIsP1) {
					p1ForwardDamage.set(blockerIdx, p1ForwardDamage.get(blockerIdx) + received);
					refreshP1ForwardSlot(blockerIdx);
				} else {
					p2ForwardDamage.set(blockerIdx, p2ForwardDamage.get(blockerIdx) + received);
					refreshP2ForwardSlot(blockerIdx);
				}
			}
		}
		if (!attackerBroken && !blockerBroken) {
			logEntry("Both forwards survive combat");
		}
	}

	/**
	 * P2 AI: returns the index of the best P2 blocker against {@code attacker},
	 * or -1 if P2 declines to block.
	 * Strategy: block with the highest-power active forward that can survive (power >= attacker) or trade evenly.
	 */
	private int p2ChooseBlocker(CardData attacker) {
		int bestIdx = -1, bestPower = -1;
		for (int i = 0; i < p2ForwardStates.size(); i++) {
			if (p2ForwardStates.get(i) != CardState.NORMAL) continue;
			CardData c = p2ForwardCards.get(i);
			if (c.power() >= attacker.power() && c.power() > bestPower) {
				bestPower = c.power();
				bestIdx = i;
			}
		}
		return bestIdx;
	}

	/** Called after P1 attacks: gives P2 AI a chance to declare a blocker. */
	private void p2OfferBlock(CardData attacker, int attackerIdx) {
		int blockerIdx = p2ChooseBlocker(attacker);
		if (blockerIdx >= 0) {
			CardData blocker = p2ForwardCards.get(blockerIdx);
			logEntry("[P2] " + blocker.name() + " blocks!");
			resolveCombat(attacker, true, attackerIdx, blocker, false, blockerIdx);
		} else {
			p2TakeDamage();
		}
	}

	/**
	 * Called when P2 attacks: shows a modal dialog for P1 to optionally declare a blocker.
	 * {@code onDone} is called synchronously after combat (or taking damage) resolves.
	 */
	private void p1ChooseBlockerDialog(CardData attacker, int attackerIdx, Runnable onDone) {
		List<Integer> eligible = new ArrayList<>();
		for (int i = 0; i < p1ForwardStates.size(); i++) {
			CardState s = p1ForwardStates.get(i);
			// NORMAL forwards can always block; BRAVE_ATTACKED forwards can block because
			// Brave allows acting again even after attacking
			if (s == CardState.NORMAL || s == CardState.BRAVE_ATTACKED) eligible.add(i);
		}
		if (eligible.isEmpty()) {
			p1TakeDamage();
			onDone.run();
			return;
		}

		JDialog dlg = new JDialog(frame, "Declare Blocker", true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JLabel header = new JLabel("[P2] " + attacker.name() + " (" + attacker.power() + ") attacks!");
		header.setFont(new Font("Pixel NES", Font.PLAIN, 12));
		header.setHorizontalAlignment(SwingConstants.CENTER);
		panel.add(header, BorderLayout.NORTH);

		JPanel forwardPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
		int[] chosen = { -1 };
		for (int bi : eligible) {
			// Use the top card's name/power for display when the forward is primed
			CardData top = p1ForwardPrimedTop.get(bi);
			CardData display = top != null ? top : p1ForwardCards.get(bi);
			JButton btn = new JButton("<html><center>" + display.name() + "<br>(" + display.power() + ")</center></html>");
			btn.setFont(new Font("Pixel NES", Font.PLAIN, 10));
			btn.setPreferredSize(new Dimension(130, 60));
			final int blockerIdx = bi;
			btn.addActionListener(ae -> { chosen[0] = blockerIdx; dlg.dispose(); });
			forwardPanel.add(btn);
		}
		panel.add(forwardPanel, BorderLayout.CENTER);

		JButton noBlockBtn = new JButton("No Block (Take Damage)");
		noBlockBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		noBlockBtn.addActionListener(ae -> dlg.dispose());
		panel.add(noBlockBtn, BorderLayout.SOUTH);

		dlg.setContentPane(panel);
		dlg.pack();
		dlg.setLocationRelativeTo(frame);
		dlg.setVisible(true);

		if (chosen[0] >= 0) {
			// Use the top card's stats for combat when the blocker is primed
			CardData top = p1ForwardPrimedTop.get(chosen[0]);
			CardData blocker = top != null ? top : p1ForwardCards.get(chosen[0]);
			resolveCombat(attacker, false, attackerIdx, blocker, true, chosen[0]);
		} else {
			p1TakeDamage();
		}
		onDone.run();
	}

	private void showP2DamageZoneDialog() {
		List<CardData> zone = gameState.getP2DamageZone();
		if (zone.isEmpty()) return;

		JDialog dlg = new JDialog(frame, "P2 Damage Zone (" + zone.size() + " cards)", true);
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

			String labelText = cd.name() + (cd.exBurst() ? " EX" : "");
			JLabel nameLabel = cd.exBurst() ? new JLabel(labelText, SwingConstants.CENTER) {
				@Override protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setFont(getFont());
					FontMetrics fm = g2.getFontMetrics();
					int x = (getWidth() - fm.stringWidth(labelText)) / 2;
					int y = fm.getAscent() + (getHeight() - fm.getHeight()) / 2;
					g2.setColor(new Color(0, 0, 0, 180));
					g2.drawString(labelText, x + 1, y + 1);
					g2.setColor(Color.YELLOW);
					g2.drawString(labelText, x, y);
					g2.dispose();
				}
			} : new JLabel(labelText, SwingConstants.CENTER);
			nameLabel.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			if (!cd.exBurst()) nameLabel.setForeground(null);
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

			String labelText = cd.name() + (cd.exBurst() ? " EX" : "");
			JLabel nameLabel = cd.exBurst() ? new JLabel(labelText, SwingConstants.CENTER) {
				@Override protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setFont(getFont());
					FontMetrics fm = g2.getFontMetrics();
					int x = (getWidth() - fm.stringWidth(labelText)) / 2;
					int y = fm.getAscent() + (getHeight() - fm.getHeight()) / 2;
					g2.setColor(new Color(0, 0, 0, 180));
					g2.drawString(labelText, x + 1, y + 1);
					g2.setColor(Color.YELLOW);
					g2.drawString(labelText, x, y);
					g2.dispose();
				}
			} : new JLabel(labelText, SwingConstants.CENTER);
			nameLabel.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			if (!cd.exBurst()) nameLabel.setForeground(null);
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
				CardData lcd = lbDeck.get(i);
				boolean spent       = spentLbIndices.contains(i);
				boolean casting     = (castingIdx[0] == i);
				boolean payment     = paymentSet.contains(i);
				boolean inPaymentMode = castingIdx[0] >= 0;
				boolean nameBlocked = !inPaymentMode && !spent
						&& (lcd.isForward() || lcd.isBackup() || lcd.isMonster())
						&& ((!lcd.multicard() && hasCharacterNameOnField(lcd.name()))
							|| (lcd.isLightOrDark() && hasLightOrDarkOnField(true)));

				if (casting) {
					lbl.setBorder(BorderFactory.createLineBorder(new Color(255, 200, 0), 3));
				} else if (payment) {
					lbl.setBorder(BorderFactory.createLineBorder(Color.CYAN, 3));
				} else if (spent) {
					lbl.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1));
				} else if (nameBlocked) {
					lbl.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
				} else {
					lbl.setBorder(BorderFactory.createLineBorder(
							inPaymentMode ? Color.GRAY : Color.LIGHT_GRAY, 1));
				}
				boolean canInteract = !spent && !nameBlocked && !casting
						&& (castingIdx[0] < 0 || !paymentSet.contains(i) || payment);
				lbl.setCursor(canInteract
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
					boolean nameBlocked = castingIdx[0] < 0
							&& (cd.isForward() || cd.isBackup() || cd.isMonster())
							&& ((!cd.multicard() && hasCharacterNameOnField(cd.name()))
								|| (cd.isLightOrDark() && hasLightOrDarkOnField(true)));
					if (nameBlocked) return;

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
				gameState.breakFromHand(di);
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
	 * On the first call, resizes the side panel and preview panel to the card's
	 * native image dimensions scaled by PREVIEW_SCALE, and establishes the min/max
	 * bounds for user-driven sidebar resizing. Subsequent calls are no-ops.
	 */
	private void sizePreviewPanel(int imgW, int imgH) {
		if (previewSized) return;
		previewSized  = true;
		nativeImgW    = imgW;
		nativeImgH    = imgH;
		minSidePanelW = (int)(imgW * 0.75) + SIDE_MARGIN;
		maxSidePanelW = imgW + SIDE_MARGIN;
		int defaultW  = (int)(imgW * PREVIEW_SCALE) + SIDE_MARGIN;
		int savedW    = AppSettings.getSidePanelWidth(defaultW);
		// Clamp to valid range; fall back to default if saved value is out of bounds
		int initialW  = (savedW >= minSidePanelW && savedW <= maxSidePanelW) ? savedW : defaultW;
		setSidePanelWidth(initialW);
	}

	private void setSidePanelWidth(int w) {
		sidePanelW = w;
		previewH = nativeImgH > 0
				? (int)((w - SIDE_MARGIN) * (double) nativeImgH / nativeImgW)
				: (int)(w * (double) CARD_H / CARD_W);
		cardPreviewPanel.setPreferredSize(new Dimension(w, previewH));
		cardPreviewPanel.setMinimumSize  (new Dimension(w, previewH));
		cardPreviewPanel.setMaximumSize  (new Dimension(w, previewH));
		sidePanel.setPreferredSize(new Dimension(w, 0));
		handPanel.setPreferredSize(new Dimension(w, CARD_H));
		if (sideWrapper != null)
			sideWrapper.setPreferredSize(new Dimension(w + RESIZE_HANDLE_W, 0));
		refreshHandPanel();
		frame.revalidate();
		frame.repaint();
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
		boolean isCharacter = card.isForward() || card.isBackup() || card.isMonster();
		boolean nameConflict = isCharacter && !card.multicard() && hasCharacterNameOnField(card.name());
		boolean lightDarkConflict = isCharacter && card.isLightOrDark() && hasLightOrDarkOnField(true);
		playItem.setEnabled(isMainPhase && !nameConflict && !lightDarkConflict && canAffordCard(card, handIdx)
				&& (!card.isBackup() || hasAvailableBackupSlot()));
		playItem.addActionListener(ae -> {
			hideZoom();
			if (handPopup != null) { handPopup.dispose(); handPopup = null; }
			showPaymentDialog(card, handIdx);
		});
		menu.add(playItem);

		if (card.hasWarp()) {
			JMenuItem warpItem = new JMenuItem("Play (Warp " + card.warpValue() + ")");
			warpItem.setEnabled(isMainPhase && canAffordWarpCost(card, handIdx));
			warpItem.addActionListener(ae -> {
				hideZoom();
				if (handPopup != null) { handPopup.dispose(); handPopup = null; }
				showWarpPaymentDialog(card, handIdx);
			});
			menu.add(warpItem);
		}

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

	private void refreshP2BreakLabel() {
		List<CardData> zone = gameState.getP2BreakZone();
		if (zone.isEmpty()) {
			p2BreakLabel.setIcon(null);
			p2BreakLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
			p2BreakLabel.setText("BREAK");
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
					if (icon != null) { p2BreakLabel.setIcon(icon); p2BreakLabel.setText(null); }
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
	/** Returns the first element of {@code source} that matches one of {@code playedElems}. */
	private String contributingElement(CardData source, String[] playedElems) {
		for (String pe : playedElems)
			if (source.containsElement(pe)) return pe;
		return playedElems[0];
	}

	/**
	 * Returns the element of {@code source} that has the largest remaining deficit
	 * ({@code required - alreadyPaid}), so multi-element payment cards fill whichever
	 * requirement is still most needed rather than always defaulting to the first match.
	 */
	private String contributingElement(CardData source, String[] playedElems,
			Map<String, Integer> cpByElem, Map<String, Integer> costByElem) {
		String best = null;
		int maxDeficit = Integer.MIN_VALUE;
		for (String pe : playedElems) {
			if (source.containsElement(pe)) {
				int deficit = costByElem.getOrDefault(pe, 0) - cpByElem.getOrDefault(pe, 0);
				if (deficit > maxDeficit) {
					maxDeficit = deficit;
					best = pe;
				}
			}
		}
		return best != null ? best : playedElems[0];
	}

	/** Returns true if {@code source} contains any element from {@code playedElems}. */
	private boolean matchesAnyElement(CardData source, String[] playedElems) {
		for (String pe : playedElems)
			if (source.containsElement(pe)) return true;
		return false;
	}

	private boolean canAffordCard(CardData card, int excludeHandIdx) {
		String[]       elems = card.elements();
		List<CardData> hand  = gameState.getP1Hand();
		int totalGenerate = 0;

		if (card.isLightOrDark()) {
			// L/D cards accept any element — sum all banked CP and all available sources
			int totalExisting = gameState.getP1CpByElement().values().stream().mapToInt(Integer::intValue).sum();
			for (int i = 0; i < hand.size(); i++) {
				if (i == excludeHandIdx) continue;
				if (!hand.get(i).isLightOrDark()) totalGenerate += 2;
			}
			for (int i = 0; i < p1BackupCards.length; i++) {
				if (p1BackupCards[i] != null && p1BackupStates[i] == CardState.NORMAL)
					totalGenerate += 1;
			}
			return totalExisting + totalGenerate >= card.cost();
		}

		boolean[] hasElemSource = new boolean[elems.length];
		int totalExisting = 0;
		for (int ei = 0; ei < elems.length; ei++) {
			int ex = gameState.getP1CpForElement(elems[ei]);
			totalExisting += ex;
			if (ex > 0) hasElemSource[ei] = true;
		}
		for (int i = 0; i < hand.size(); i++) {
			if (i == excludeHandIdx) continue;
			CardData h = hand.get(i);
			if (h.isLightOrDark()) continue;
			totalGenerate += 2;
			for (int ei = 0; ei < elems.length; ei++) {
				if (h.containsElement(elems[ei])) hasElemSource[ei] = true;
			}
		}
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] != null && p1BackupStates[i] == CardState.NORMAL) {
				for (int ei = 0; ei < elems.length; ei++) {
					if (p1BackupCards[i].containsElement(elems[ei])) {
						totalGenerate += 1;
						hasElemSource[ei] = true;
						break;
					}
				}
			}
		}
		for (boolean hs : hasElemSource) if (!hs) return false;
		return totalExisting + totalGenerate >= card.cost();
	}

	/**
	 * Returns true if the player can afford the Warp alternate cost of {@code card}.
	 * Warp cost is a list of element CP requirements (e.g. ["Lightning"] = 1 Lightning CP).
	 */
	private boolean canAffordWarpCost(CardData card, int handIdx) {
		List<String> warpCost = card.warpCost();
		if (warpCost.isEmpty()) return true;

		// Separate element-specific requirements from generic CP (empty-string entries)
		boolean hasGeneric = warpCost.contains("");
		LinkedHashMap<String, Integer> needed = new LinkedHashMap<>();
		for (String e : warpCost) if (!e.isEmpty()) needed.merge(e, 1, Integer::sum);
		String[] elems = needed.keySet().toArray(String[]::new);
		int total = warpCost.size();

		boolean[] hasSrc = new boolean[elems.length];
		int available = 0;

		// Banked CP (element-specific)
		for (int ei = 0; ei < elems.length; ei++) {
			int b = gameState.getP1CpForElement(elems[ei]);
			available += b;
			if (b > 0) hasSrc[ei] = true;
		}
		// Banked CP of any element counts toward generic
		if (hasGeneric) {
			available += gameState.getP1CpByElement().values().stream().mapToInt(Integer::intValue).sum();
			for (int ei = 0; ei < elems.length; ei++)
				available -= gameState.getP1CpForElement(elems[ei]); // avoid double-counting
		}

		// Undulled backups: matching backups satisfy element requirements;
		// any backup can cover generic CP
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] == null || p1BackupStates[i] != CardState.NORMAL) continue;
			boolean matched = false;
			for (int ei = 0; ei < elems.length; ei++) {
				if (p1BackupCards[i].containsElement(elems[ei])) {
					available++;
					hasSrc[ei] = true;
					matched = true;
					break;
				}
			}
			if (!matched && hasGeneric) available++;
		}

		// Non-L/D hand cards always contribute 2 CP toward total
		List<CardData> hand = gameState.getP1Hand();
		for (int i = 0; i < hand.size(); i++) {
			if (i == handIdx) continue;
			CardData h = hand.get(i);
			if (h.isLightOrDark()) continue;
			available += 2;
			for (int ei = 0; ei < elems.length; ei++) {
				if (h.containsElement(elems[ei])) hasSrc[ei] = true;
			}
		}

		for (boolean s : hasSrc) if (!s) return false;
		return available >= total;
	}

	/**
	 * Opens a payment dialog for the Warp alternate cost and, on confirm,
	 * moves the card from hand to the Removed-From-Play zone with Warp counters.
	 */
	private void showWarpPaymentDialog(CardData card, int handIdx) {
		List<String> rawCost = card.warpCost();
		// Generic CP (empty-string entries) don't go into the per-element cost map
		long genericNeeded = rawCost.stream().filter(String::isEmpty).count();
		LinkedHashMap<String, Integer> costByElem = new LinkedHashMap<>();
		for (String e : rawCost) if (!e.isEmpty()) costByElem.merge(e, 1, Integer::sum);
		String[] elems = costByElem.keySet().toArray(String[]::new);
		int totalCost  = rawCost.size();  // includes generic entries

		JDialog dlg = new JDialog(frame, "Warp: " + card.name(), true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		List<CardData> hand = gameState.getP1Hand();

		Map<String, Integer> bankCpByElem = new LinkedHashMap<>(costByElem);
		for (String k : bankCpByElem.keySet()) bankCpByElem.put(k, 0);

		List<Integer> selectedBackups  = new ArrayList<>();
		List<Integer> selectedDiscards = new ArrayList<>();

		List<Integer> eligibleBackupSlots = new ArrayList<>();
		for (int i = 0; i < p1BackupCards.length; i++) {
			// When there is generic CP in the cost, any undulled backup is eligible
			if (p1BackupCards[i] != null && p1BackupStates[i] == CardState.NORMAL
					&& (genericNeeded > 0 || matchesAnyElement(p1BackupCards[i], elems)))
				eligibleBackupSlots.add(i);
		}

		JLabel cpLabel = new JLabel();
		cpLabel.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cpLabel.setHorizontalAlignment(SwingConstants.CENTER);

		JButton confirmBtn = new JButton("Confirm Warp");
		confirmBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));

		List<JLabel>   backupLbls   = new ArrayList<>();
		List<Integer>  backupSlots  = new ArrayList<>();
		List<JLabel>   discardLbls  = new ArrayList<>();
		List<Integer>  discardIdxs  = new ArrayList<>();

		boolean[] canAddDiscard = {false};
		Runnable updateAll = () -> {
			Map<String, Integer> cpByElem = new LinkedHashMap<>(bankCpByElem);
			int extraCp = 0;
			for (int slot : selectedBackups) {
				if (matchesAnyElement(p1BackupCards[slot], elems))
					cpByElem.merge(contributingElement(p1BackupCards[slot], elems, cpByElem, costByElem), 1, Integer::sum);
				else
					extraCp++;
			}
			for (int idx : selectedDiscards) {
				if (matchesAnyElement(hand.get(idx), elems))
					cpByElem.merge(contributingElement(hand.get(idx), elems, cpByElem, costByElem), 2, Integer::sum);
				else
					extraCp += 2;
			}
			int total = cpByElem.values().stream().mapToInt(Integer::intValue).sum() + extraCp;
			int unsatisfied = (int) java.util.stream.IntStream.range(0, elems.length)
					.filter(ei -> cpByElem.getOrDefault(elems[ei], 0) < costByElem.get(elems[ei]))
					.count();
			// Max allowed total CP = cost + one overpay slot per distinct specific element +
			// one extra slot when the cost is odd (unavoidable when paying via 2-CP discards).
			int maxAllowed = totalCost + elems.length + (totalCost % 2);
			boolean canAddBackup = total < totalCost;
			// A discard may only be added if it keeps us within the overpay budget
			// AND we still need more total CP or have at least one unsatisfied element.
			canAddDiscard[0] = (total + 2 <= maxAllowed) && (total < totalCost || unsatisfied > 0);
			boolean satisfied    = cpByElem.entrySet().stream()
					.allMatch(e -> e.getValue() >= costByElem.getOrDefault(e.getKey(), 0));
			confirmBtn.setEnabled(total >= totalCost && satisfied);

			StringBuilder sb = new StringBuilder("Warp CP: " + total + " / " + totalCost + "  (");
			boolean first = true;
			for (String e : elems) {
				if (!first) sb.append(", ");
				sb.append(e).append(": ").append(cpByElem.getOrDefault(e, 0))
				  .append("/").append(costByElem.get(e));
				first = false;
			}
			if (genericNeeded > 0) {
				if (!first) sb.append(", ");
				sb.append("any: ").append(Math.min(extraCp, (int) genericNeeded))
				  .append("/").append((int) genericNeeded);
				first = false;
			}
			if (first) sb.append("free");  // pure 0-cost warp
			sb.append(")");
			cpLabel.setText(sb.toString());

			for (int i = 0; i < backupLbls.size(); i++) {
				JLabel lbl = backupLbls.get(i);
				boolean sel = selectedBackups.contains(backupSlots.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(
						sel ? Color.YELLOW : (canAddBackup ? Color.GRAY : new Color(80, 80, 80)), sel ? 3 : 1));
				lbl.setBackground(sel || canAddBackup ? Color.DARK_GRAY : new Color(50, 50, 50));
				lbl.setCursor(sel || canAddBackup
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
			for (int i = 0; i < discardLbls.size(); i++) {
				JLabel lbl = discardLbls.get(i);
				boolean sel = selectedDiscards.contains(discardIdxs.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(
						sel ? Color.YELLOW : (canAddDiscard[0] ? Color.GRAY : new Color(80, 80, 80)), sel ? 3 : 1));
				lbl.setBackground(sel || canAddDiscard[0] ? Color.DARK_GRAY : new Color(50, 50, 50));
				lbl.setCursor(sel || canAddDiscard[0]
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
		};
		updateAll.run();

		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

		if (!eligibleBackupSlots.isEmpty()) {
			JLabel hdr = new JLabel("Backups — dull for 1 CP each:");
			hdr.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
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
						int tot = bankCpByElem.values().stream().mapToInt(Integer::intValue).sum()
								+ selectedBackups.size() + selectedDiscards.size() * 2;
						if (selectedBackups.remove(Integer.valueOf(slot))) { /* deselect ok */ }
						else if (tot < totalCost) selectedBackups.add(slot);
						updateAll.run();
					}
					@Override public void mouseEntered(MouseEvent e) { if (lbl.getIcon() != null) showZoomAt(url, lbl); }
					@Override public void mouseExited(MouseEvent e)  { hideZoom(); }
				});
				new SwingWorker<ImageIcon, Void>() {
					@Override protected ImageIcon doInBackground() throws Exception {
						Image img = ImageCache.load(url);
						return img == null ? null
								: new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
					}
					@Override protected void done() {
						try { ImageIcon ic = get(); if (ic != null) { lbl.setIcon(ic); lbl.setText(null); } }
						catch (InterruptedException | ExecutionException ignored) {}
					}
				}.execute();
				backupLbls.add(lbl);
				backupSlots.add(slot);
				backupCardsPanel.add(lbl);
			}
			centerPanel.add(hdr);
			centerPanel.add(backupCardsPanel);
		}

		JLabel discardHdr = new JLabel("Hand — discard for 2 CP each:");
		discardHdr.setFont(new Font("Pixel NES", Font.PLAIN, 9));
		discardHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel discardCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
		discardCardsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < hand.size(); i++) {
			if (i == handIdx) continue;
			final int hi = i;
			CardData hc  = hand.get(i);
			boolean payable = !hc.isLightOrDark();
			JLabel lbl = new JLabel("...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
			lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true);
			lbl.setBackground(payable ? Color.DARK_GRAY : new Color(50, 50, 50));
			lbl.setForeground(Color.WHITE);
			lbl.setFont(new Font("Pixel NES", Font.PLAIN, 10));
			lbl.setBorder(BorderFactory.createLineBorder(payable ? Color.GRAY : new Color(80, 80, 80), 1));
			lbl.setCursor(payable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			final String imgUrl = hc.imageUrl();
			if (payable) {
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mousePressed(MouseEvent e) {
						if (!selectedDiscards.remove(Integer.valueOf(hi)) && canAddDiscard[0])
							selectedDiscards.add(hi);
						updateAll.run();
					}
					@Override public void mouseEntered(MouseEvent e) { if (lbl.getIcon() != null) showZoomAt(imgUrl, lbl); }
					@Override public void mouseExited(MouseEvent e)  { hideZoom(); }
				});
				discardLbls.add(lbl);
				discardIdxs.add(hi);
			} else {
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mouseEntered(MouseEvent e) { if (lbl.getIcon() != null) showZoomAt(imgUrl, lbl); }
					@Override public void mouseExited(MouseEvent e)  { hideZoom(); }
				});
			}
			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(imgUrl);
					return img == null ? null
							: new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
				}
				@Override protected void done() {
					try { ImageIcon ic = get(); if (ic != null) { lbl.setIcon(ic); lbl.setText(null); } }
					catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();
			discardCardsPanel.add(lbl);
		}
		centerPanel.add(discardHdr);
		centerPanel.add(discardCardsPanel);

		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cancelBtn.addActionListener(e -> dlg.dispose());
		confirmBtn.addActionListener(e -> {
			dlg.dispose();
			executeWarpPlay(card, handIdx, new ArrayList<>(selectedDiscards), new ArrayList<>(selectedBackups));
		});

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
		buttonPanel.add(confirmBtn);
		buttonPanel.add(cancelBtn);

		StringBuilder costDesc = new StringBuilder();
		boolean f = true;
		for (Map.Entry<String, Integer> en : costByElem.entrySet()) {
			if (!f) costDesc.append(" + ");
			costDesc.append(en.getValue()).append(" ").append(en.getKey()).append(" CP");
			f = false;
		}
		if (genericNeeded > 0) {
			if (!f) costDesc.append(" + ");
			costDesc.append((int) genericNeeded).append(" any CP");
		}
		JLabel titleLabel = new JLabel(
				"Warp cost for: " + card.name() + "  (" + (costDesc.length() > 0 ? costDesc : "free") + ")",
				SwingConstants.CENTER);
		titleLabel.setFont(new Font("Pixel NES", Font.PLAIN, 11));

		JPanel topPanel = new JPanel(new BorderLayout(0, 4));
		topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
		topPanel.add(titleLabel, BorderLayout.NORTH);
		topPanel.add(cpLabel,   BorderLayout.CENTER);

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
	 * Pays the Warp alternate cost (dulls backups, discards hand cards), removes the card
	 * from hand, and places it in the Removed-From-Play zone with Warp counters.
	 */
	private void executeWarpPlay(CardData card, int cardHandIdx,
			List<Integer> discardIndices, List<Integer> backupDullIndices) {
		List<String> rawCost = card.warpCost();
		LinkedHashMap<String, Integer> costByElem = new LinkedHashMap<>();
		for (String e : rawCost) costByElem.merge(e, 1, Integer::sum);
		String[] elems = costByElem.keySet().toArray(String[]::new);

		for (int bi : backupDullIndices) {
			p1BackupStates[bi] = CardState.DULLED;
			animateDullBackup(bi, true);
			String cpElem = matchesAnyElement(p1BackupCards[bi], elems)
					? contributingElement(p1BackupCards[bi], elems) : elems[0];
			gameState.addP1Cp(cpElem, 1);
		}
		discardIndices.sort(Collections.reverseOrder());
		for (int di : discardIndices) {
			CardData discarded = gameState.getP1Hand().get(di);
			String cpElem = matchesAnyElement(discarded, elems)
					? contributingElement(discarded, elems) : elems[0];
			gameState.addP1Cp(cpElem, 2);
			gameState.breakFromHand(di);
			if (di < cardHandIdx) cardHandIdx--;
		}
		for (String e : elems) {
			gameState.spendP1Cp(e, gameState.getP1CpForElement(e));
			gameState.clearP1Cp(e);
		}
		gameState.removeFromHand(cardHandIdx);

		gameState.addToP1WarpZone(card, card.warpValue());
		logEntry("Played \"" + card.name() + "\" via Warp — " + card.warpValue()
				+ " counter" + (card.warpValue() != 1 ? "s" : "") + " → Removed From Play");
		refreshP1HandLabel();
		refreshP1BreakLabel();
		refreshP1WarpZoneUI();
	}

	/** Returns true if at least one P1 backup slot is currently empty. */
	private boolean hasCharacterNameOnField(String name) {
		for (CardData c : p1ForwardCards)
			if (name.equalsIgnoreCase(c.name())) return true;
		for (CardData c : p1MonsterCards)
			if (name.equalsIgnoreCase(c.name())) return true;
		for (CardData c : p1BackupCards)
			if (c != null && name.equalsIgnoreCase(c.name())) return true;
		return false;
	}

	private boolean p2HasCharacterNameOnField(String name) {
		for (CardData c : p2ForwardCards)
			if (name.equalsIgnoreCase(c.name())) return true;
		for (CardData c : p2BackupCards)
			if (c != null && name.equalsIgnoreCase(c.name())) return true;
		return false;
	}

	/** Returns true if any Light or Dark character is on the given player's field. */
	private boolean hasLightOrDarkOnField(boolean isP1) {
		if (isP1) {
			for (CardData c : p1ForwardCards) if (c.isLightOrDark()) return true;
			for (CardData c : p1MonsterCards) if (c.isLightOrDark()) return true;
			for (CardData c : p1BackupCards)  if (c != null && c.isLightOrDark()) return true;
		} else {
			for (CardData c : p2ForwardCards) if (c.isLightOrDark()) return true;
			for (CardData c : p2BackupCards)  if (c != null && c.isLightOrDark()) return true;
		}
		return false;
	}

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
	 *   - Discards may overpay by 1 per element (total <= cost + elems.length - 1 after adding).
	 */
	private void showPaymentDialog(CardData card, int handIdx) {
		JDialog dlg = new JDialog(frame, "Play " + card.name(), true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		List<CardData> hand   = gameState.getP1Hand();
		String         elem   = card.element();
		String[]       elems  = card.elements();
		int            cost   = card.cost();
		boolean        isLD   = card.isLightOrDark();

		// Always start from 0 — CP is generated and spent within a single payment action
		// and must never carry over from a previous play.
		Map<String, Integer> bankCpByElem = new LinkedHashMap<>();
		if (isLD) {
			bankCpByElem.put(elem, 0);
		} else {
			for (String e : elems) bankCpByElem.put(e, 0);
		}

		List<Integer> selectedBackups  = new ArrayList<>();
		List<Integer> selectedDiscards = new ArrayList<>();

		// L/D cards: any undulled backup is eligible. Others: must match an element.
		List<Integer> eligibleBackupSlots = new ArrayList<>();
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] != null && p1BackupStates[i] == CardState.NORMAL
					&& (isLD || matchesAnyElement(p1BackupCards[i], elems)))
				eligibleBackupSlots.add(i);
		}

		JLabel cpLabel = new JLabel();
		cpLabel.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cpLabel.setHorizontalAlignment(SwingConstants.CENTER);

		JButton confirmBtn = new JButton("Confirm");
		confirmBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));

		List<JLabel>   backupLbls  = new ArrayList<>();
		List<Integer>  backupSlots = new ArrayList<>();
		List<JLabel>   discardLbls  = new ArrayList<>();
		List<Integer>  discardIdxs  = new ArrayList<>();

		boolean[] canAddDiscard = {false};
		Runnable updateAll = () -> {
			Map<String, Integer> cpByElem = new LinkedHashMap<>(bankCpByElem);
			int extraCp = 0;
			for (int slot : selectedBackups) {
				if (isLD)
					cpByElem.merge(elem, 1, Integer::sum);
				else if (matchesAnyElement(p1BackupCards[slot], elems))
					cpByElem.merge(contributingElement(p1BackupCards[slot], elems), 1, Integer::sum);
				else
					extraCp += 1;
			}
			for (int idx : selectedDiscards) {
				if (isLD)
					cpByElem.merge(elem, 2, Integer::sum);
				else if (matchesAnyElement(hand.get(idx), elems))
					cpByElem.merge(contributingElement(hand.get(idx), elems), 2, Integer::sum);
				else
					extraCp += 2;
			}
			int total = cpByElem.values().stream().mapToInt(Integer::intValue).sum() + extraCp;
			int unsatisfiedElems = isLD ? 0 : (int) cpByElem.values().stream().filter(v -> v < 1).count();
			boolean canAddBackup  = total < cost;
			canAddDiscard[0] = total < cost + unsatisfiedElems;
			boolean allElemsPresent = isLD || cpByElem.values().stream().allMatch(v -> v >= 1);
			confirmBtn.setEnabled(total >= cost && allElemsPresent);
			if (elems.length == 1) {
				cpLabel.setText("CP: " + total + " / " + cost + "  (" + elem + ")");
			} else {
				StringBuilder sb = new StringBuilder("CP: " + total + " / " + cost + "  (");
				boolean first = true;
				for (Map.Entry<String, Integer> e : cpByElem.entrySet()) {
					if (!first) sb.append(", ");
					sb.append(e.getKey()).append(": ").append(e.getValue());
					first = false;
				}
				sb.append(")");
				cpLabel.setText(sb.toString());
			}
			for (int i = 0; i < backupLbls.size(); i++) {
				JLabel  lbl      = backupLbls.get(i);
				boolean selected = selectedBackups.contains(backupSlots.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(
						selected ? Color.YELLOW : (canAddBackup ? Color.GRAY : new Color(80, 80, 80)),
						selected ? 3 : 1));
				lbl.setBackground(selected || canAddBackup ? Color.DARK_GRAY : new Color(50, 50, 50));
				lbl.setCursor(selected || canAddBackup
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
			for (int i = 0; i < discardLbls.size(); i++) {
				JLabel  lbl      = discardLbls.get(i);
				boolean selected = selectedDiscards.contains(discardIdxs.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(
						selected ? Color.YELLOW : (canAddDiscard[0] ? Color.GRAY : new Color(80, 80, 80)),
						selected ? 3 : 1));
				lbl.setBackground(selected || canAddDiscard[0] ? Color.DARK_GRAY : new Color(50, 50, 50));
				lbl.setCursor(selected || canAddDiscard[0]
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
						int total = bankCpByElem.values().stream().mapToInt(Integer::intValue).sum() + selectedBackups.size() + selectedDiscards.size() * 2;
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
			boolean payable = !hc.isLightOrDark();

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
						if (!selectedDiscards.remove(Integer.valueOf(hi)) && canAddDiscard[0]) {
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
		String[] elems = card.elements();
		boolean  isLD  = card.isLightOrDark();
		for (int bi : backupDullIndices) {
			p1BackupStates[bi] = CardState.DULLED;
			animateDullBackup(bi, true);
			String cpElem = isLD ? p1BackupCards[bi].elements()[0] : contributingElement(p1BackupCards[bi], elems);
			gameState.addP1Cp(cpElem, 1);
		}
		discardIndices.sort(Collections.reverseOrder());
		for (int di : discardIndices) {
			CardData discarded = gameState.getP1Hand().get(di);
			String cpElem = isLD ? discarded.elements()[0] : contributingElement(discarded, elems);
			gameState.addP1Cp(cpElem, 2);
			gameState.breakFromHand(di);
			if (di < cardHandIdx) cardHandIdx--;
		}
		for (String e : elems) {
			gameState.spendP1Cp(e, gameState.getP1CpForElement(e));
			gameState.clearP1Cp(e);
		}
		gameState.removeFromHand(cardHandIdx);
		logEntry("Played \"" + card.name() + "\"");

		if (card.isBackup()) {
			placeCardInFirstBackupSlot(card);
		} else if (card.isForward()) {
			placeCardInForwardZone(card);
		} else if (card.isMonster()) {
			placeCardInMonsterZone(card);
		} else if (card.isSummon()) {
			gameState.pushStack(card);
			CardData resolved = gameState.popStack();
			gameState.getP1BreakZone().add(resolved);
			logEntry("\"" + resolved.name() + "\" resolves → Break Zone");
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
		String[]       elems  = card.elements();
		int            cost   = card.cost();
		boolean        isLD   = card.isLightOrDark();

		// Always start from 0 — CP is generated and spent within a single payment action.
		Map<String, Integer> bankCpByElem = new LinkedHashMap<>();
		if (isLD) {
			bankCpByElem.put(elem, 0);
		} else {
			for (String e : elems) bankCpByElem.put(e, 0);
		}

		List<Integer> selectedBackups  = new ArrayList<>();
		List<Integer> selectedDiscards = new ArrayList<>();

		List<Integer> eligibleBackupSlots = new ArrayList<>();
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] != null && p1BackupStates[i] == CardState.NORMAL
					&& (isLD || matchesAnyElement(p1BackupCards[i], elems)))
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

		boolean[] canAddDiscard = {false};
		Runnable updateAll = () -> {
			Map<String, Integer> cpByElem = new LinkedHashMap<>(bankCpByElem);
			int extraCp = 0;
			for (int slot : selectedBackups) {
				if (isLD)
					cpByElem.merge(elem, 1, Integer::sum);
				else if (matchesAnyElement(p1BackupCards[slot], elems))
					cpByElem.merge(contributingElement(p1BackupCards[slot], elems), 1, Integer::sum);
				else
					extraCp += 1;
			}
			for (int idx : selectedDiscards) {
				if (isLD)
					cpByElem.merge(elem, 2, Integer::sum);
				else if (matchesAnyElement(hand.get(idx), elems))
					cpByElem.merge(contributingElement(hand.get(idx), elems), 2, Integer::sum);
				else
					extraCp += 2;
			}
			int total = cpByElem.values().stream().mapToInt(Integer::intValue).sum() + extraCp;
			int unsatisfiedElems = isLD ? 0 : (int) cpByElem.values().stream().filter(v -> v < 1).count();
			boolean canAddBackup  = total < cost;
			canAddDiscard[0] = total < cost + unsatisfiedElems;
			boolean allElemsPresent = isLD || cpByElem.values().stream().allMatch(v -> v >= 1);
			confirmBtn.setEnabled(total >= cost && allElemsPresent);
			if (elems.length == 1) {
				cpLabel.setText("CP: " + total + " / " + cost + "  (" + elem + ")");
			} else {
				StringBuilder sb = new StringBuilder("CP: " + total + " / " + cost + "  (");
				boolean first = true;
				for (Map.Entry<String, Integer> e : cpByElem.entrySet()) {
					if (!first) sb.append(", ");
					sb.append(e.getKey()).append(": ").append(e.getValue());
					first = false;
				}
				sb.append(")");
				cpLabel.setText(sb.toString());
			}
			for (int i = 0; i < backupLbls.size(); i++) {
				JLabel  lbl      = backupLbls.get(i);
				boolean selected = selectedBackups.contains(backupSlots.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(
						selected ? Color.YELLOW : (canAddBackup ? Color.GRAY : new Color(80, 80, 80)),
						selected ? 3 : 1));
				lbl.setBackground(selected || canAddBackup ? Color.DARK_GRAY : new Color(50, 50, 50));
				lbl.setCursor(selected || canAddBackup
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
			for (int i = 0; i < discardLbls.size(); i++) {
				JLabel  lbl      = discardLbls.get(i);
				boolean selected = selectedDiscards.contains(discardIdxs.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(
						selected ? Color.YELLOW : (canAddDiscard[0] ? Color.GRAY : new Color(80, 80, 80)),
						selected ? 3 : 1));
				lbl.setBackground(selected || canAddDiscard[0] ? Color.DARK_GRAY : new Color(50, 50, 50));
				lbl.setCursor(selected || canAddDiscard[0]
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
						int total = bankCpByElem.values().stream().mapToInt(Integer::intValue).sum() + selectedBackups.size() + selectedDiscards.size() * 2;
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
			boolean payable = !hc.isLightOrDark();

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
						if (!selectedDiscards.remove(Integer.valueOf(hi)) && canAddDiscard[0]) {
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
		String[] elems = card.elements();
		boolean  isLD  = card.isLightOrDark();
		for (int bi : backupDullIndices) {
			p1BackupStates[bi] = CardState.DULLED;
			animateDullBackup(bi, true);
			String cpElem = isLD ? p1BackupCards[bi].elements()[0] : contributingElement(p1BackupCards[bi], elems);
			gameState.addP1Cp(cpElem, 1);
		}
		discardIndices.sort(Collections.reverseOrder());
		for (int di : discardIndices) {
			CardData discarded = gameState.getP1Hand().get(di);
			String cpElem = isLD ? discarded.elements()[0] : contributingElement(discarded, elems);
			gameState.addP1Cp(cpElem, 2);
			gameState.breakFromHand(di);
		}
		for (String e : elems) {
			gameState.spendP1Cp(e, gameState.getP1CpForElement(e));
			gameState.clearP1Cp(e);
		}
		if (card.isBackup()) {
			placeCardInFirstBackupSlot(card);
		} else if (card.isForward()) {
			placeCardInForwardZone(card);
		} else if (card.isMonster()) {
			placeCardInMonsterZone(card);
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
			p1BackupUrls[i]          = card.imageUrl();
			p1BackupCards[i]         = card;
			p1BackupStates[i]        = CardState.DULLED;
			p1BackupPlayedOnTurn[i]  = gameState.getTurnNumber();
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

	private void animateFreezeBackup(int idx, boolean freezing, CardState prevState) {
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

					double startAngle = freezing ? (prevState == CardState.DULLED ? Math.PI / 2 : 0.0) : Math.PI;
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

	private void animateFreezeForward(int idx, boolean freezing, CardState prevState) {
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

					double startAngle = freezing ? (prevState == CardState.DULLED ? Math.PI / 2 : 0.0) : Math.PI;
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

	private void animateDullForward(int idx, Runnable onComplete) {
		String url  = p1ForwardUrls.get(idx);
		JLabel slot = p1ForwardLabels.get(idx);
		if (url == null || slot == null) { refreshP1ForwardSlot(idx); if (onComplete != null) onComplete.run(); return; }

		new SwingWorker<BufferedImage, Void>() {
			@Override protected BufferedImage doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				return raw == null ? null : toARGB(raw, CARD_W, CARD_H);
			}
			@Override protected void done() {
				try {
					BufferedImage card = get();
					if (card == null) { refreshP1ForwardSlot(idx); if (onComplete != null) onComplete.run(); return; }

					int   totalFrames = 12;
					int[] frame       = { 0 };
					javax.swing.Timer timer = new javax.swing.Timer(16, null);
					timer.addActionListener(ae -> {
						frame[0]++;
						double progress = Math.min(1.0, (double) frame[0] / totalFrames);
						double t = progress < 0.5
								? 2 * progress * progress
								: 1 - Math.pow(-2 * progress + 2, 2) / 2;
						double angle = Math.PI / 2 * t;
						slot.setIcon(new ImageIcon(renderBackupCardAtAngle(card, angle)));
						slot.setText(null);
						if (frame[0] >= totalFrames) {
							timer.stop();
							refreshP1ForwardSlot(idx);
							if (onComplete != null) onComplete.run();
						}
					});
					timer.start();
				} catch (InterruptedException | ExecutionException ignored) {
					refreshP1ForwardSlot(idx);
					if (onComplete != null) onComplete.run();
				}
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
		CardState state = p1BackupStates[idx];
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
	private static BufferedImage renderBackupCard(BufferedImage card, CardState state) {
		return renderBackupCard(card, state, false);
	}

	private static BufferedImage renderBackupCard(BufferedImage card, CardState state, boolean highlight) {
		return renderBackupCard(card, state, highlight, false);
	}

	private static BufferedImage renderBackupCard(BufferedImage card, CardState state, boolean highlight, boolean selected) {
		BufferedImage canvas = new BufferedImage(CARD_H, CARD_H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		switch (state) {
			case CardState.DULLED -> {
				BufferedImage rotated = rotateCW90(card);          // now CARD_H × CARD_W
				g.drawImage(rotated, 0, CARD_H - CARD_W, null);   // pinned to bottom-left
			}
			case CardState.FROZEN -> {
				BufferedImage flipped = rotate180(card);
				g.drawImage(applyBlueTint(flipped), 0, 0, null);  // pinned to top-left
			}
			default -> g.drawImage(card, 0, 0, null);             // pinned to top-left
		}
		if (selected) {
			g.setColor(new Color(255, 165, 0));
			g.setStroke(new BasicStroke(4f));
			g.drawRect(2, 2, CARD_W - 5, CARD_H - 5);
		} else if (highlight) {
			g.setColor(new Color(0, 220, 0));
			g.setStroke(new BasicStroke(3f));
			g.drawRect(1, 1, CARD_W - 3, CARD_H - 3);
		}
		g.dispose();
		return canvas;
	}

	/**
	 * Draws remaining-HP text in the bottom-center of {@code canvas} when a forward
	 * has taken damage but not broken.  The dark pill behind the number keeps it
	 * readable over any card art.
	 */
	private static void renderDamageOverlay(BufferedImage canvas, int remainingHp) {
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		String text = String.valueOf(remainingHp);
		Font font = new Font("Pixel NES", Font.BOLD, 13);
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int tw = fm.stringWidth(text);
		int tx = 4;
		int ty = canvas.getHeight() - 5;
		g.setColor(new Color(0, 0, 0, 180));
		g.fillRoundRect(tx - 4, ty - fm.getAscent() - 1, tw + 8, fm.getAscent() + fm.getDescent() + 2, 5, 5);
		g.setColor(new Color(255, 50, 50));
		g.drawString(text, tx, ty);
		g.dispose();
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
	// -------------------------------------------------------------------------
	// Action Ability helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns a display label for an action ability menu item, e.g.
	 * {@code "[Mug] Wind, Dull, S → ...effect..."} (truncated to 60 chars).
	 */
	private String buildAbilityMenuLabel(ActionAbility ability) {
		StringBuilder sb = new StringBuilder();
		if (ability.isSpecial() && !ability.abilityName().isEmpty())
			sb.append("[").append(ability.abilityName()).append("] ");
		sb.append("[");
		boolean first = true;
		if (ability.requiresDull())  { sb.append("Dull");      first = false; }
		if (ability.isSpecial())     { if (!first) sb.append(", "); sb.append("S"); first = false; }
		for (String e : ability.cpCost()) {
			if (!first) sb.append(", ");
			sb.append(e.isEmpty() ? "any" : e);
			first = false;
		}
		sb.append("] → ");
		String fx = ability.effectText();
		sb.append(fx.length() > 55 ? fx.substring(0, 52) + "..." : fx);
		return sb.toString();
	}

	/**
	 * Returns {@code true} if the player can afford the CP portion of an action
	 * ability's cost (element and generic CP only; Dull/S requirements are checked
	 * separately in the context-menu enable logic).
	 */
	private boolean canAffordAbilityCost(ActionAbility ability) {
		List<String> cost = ability.cpCost();
		if (cost.isEmpty()) return true;

		boolean hasGeneric = cost.contains("");
		LinkedHashMap<String, Integer> needed = new LinkedHashMap<>();
		for (String e : cost) if (!e.isEmpty()) needed.merge(e, 1, Integer::sum);
		String[] elems = needed.keySet().toArray(String[]::new);
		int total = cost.size();

		boolean[] hasSrc = new boolean[elems.length];
		int available = 0;

		for (int ei = 0; ei < elems.length; ei++) {
			int b = gameState.getP1CpForElement(elems[ei]);
			available += b;
			if (b > 0) hasSrc[ei] = true;
		}
		if (hasGeneric) {
			available += gameState.getP1CpByElement().values().stream().mapToInt(Integer::intValue).sum();
			for (int ei = 0; ei < elems.length; ei++) available -= gameState.getP1CpForElement(elems[ei]);
		}
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] == null || p1BackupStates[i] != CardState.NORMAL) continue;
			boolean matched = false;
			for (int ei = 0; ei < elems.length; ei++) {
				if (p1BackupCards[i].containsElement(elems[ei])) { available++; hasSrc[ei] = true; matched = true; break; }
			}
			if (!matched && hasGeneric) available++;
		}
		List<CardData> hand = gameState.getP1Hand();
		for (CardData h : hand) {
			if (h.isLightOrDark()) continue;
			available += 2;
			for (int ei = 0; ei < elems.length; ei++) if (h.containsElement(elems[ei])) hasSrc[ei] = true;
		}
		for (boolean s : hasSrc) if (!s) return false;
		return available >= total;
	}

	/**
	 * Returns {@code true} if the player has at least one card named {@code name}
	 * in hand (needed for Special Ability payment).
	 */
	private boolean hasSameNameInHand(String name) {
		for (CardData c : gameState.getP1Hand())
			if (name.equalsIgnoreCase(c.name())) return true;
		return false;
	}

	/**
	 * Returns {@code true} if {@code ability} can currently be activated by the
	 * card at the given slot.
	 *
	 * @param state       current card state (NORMAL / DULLED / FROZEN / BRAVE_ATTACKED)
	 * @param playedTurn  turn the card entered the field (0 = unknown)
	 * @param sourceName  card name, needed for special-ability hand check
	 */
	private boolean canActivateAbility(ActionAbility ability, CardState state,
			int playedTurn, String sourceName) {
		// Frozen cards cannot activate any ability
		if (state == CardState.FROZEN) return false;
		// Abilities with Dull cost require the card to be in NORMAL state
		// and not played this turn (summoning restriction)
		if (ability.requiresDull()) {
			if (state != CardState.NORMAL) return false;
			if (playedTurn == gameState.getTurnNumber()) return false;
		}
		// Special abilities require a same-name card in hand
		if (ability.isSpecial() && !hasSameNameInHand(sourceName)) return false;
		return canAffordAbilityCost(ability);
	}

	/**
	 * Adds an action-ability section to {@code menu} for all abilities on {@code card}.
	 * Each item is enabled only when the ability is currently activatable.
	 *
	 * @param card        the card whose abilities to list
	 * @param state       current field state of the card
	 * @param playedTurn  turn the card entered the field
	 * @param applyDull   called on confirm if the ability has a Dull cost (dulls the card)
	 */
	private void addAbilityMenuItems(JPopupMenu menu, CardData card, CardState state,
			int playedTurn, Runnable applyDull) {
		List<ActionAbility> abilities = card.actionAbilities();
		if (abilities.isEmpty()) return;

		GameState.GamePhase phase = gameState.getCurrentPhase();
		boolean isMainPhase = phase == GameState.GamePhase.MAIN_1 || phase == GameState.GamePhase.MAIN_2;

		for (ActionAbility ability : abilities) {
			JMenuItem item = new JMenuItem(buildAbilityMenuLabel(ability));
			item.setEnabled(isMainPhase && canActivateAbility(ability, state, playedTurn, card.name()));
			item.addActionListener(ae ->
					showActionAbilityPaymentDialog(ability, card, applyDull));
			menu.add(item);
		}
	}

	/**
	 * Payment dialog for an action ability.  Mirrors the Priming payment dialog
	 * but also handles Dull cost (dulls the source card) and Special cost (discards
	 * a same-name card from hand).  On successful payment calls
	 * {@link ActionResolver#resolve}.
	 */
	private void showActionAbilityPaymentDialog(ActionAbility ability, CardData source,
			Runnable applyDull) {
		List<String> rawCost = ability.cpCost();
		long genericNeeded   = rawCost.stream().filter(String::isEmpty).count();
		LinkedHashMap<String, Integer> costByElem = new LinkedHashMap<>();
		for (String e : rawCost) if (!e.isEmpty()) costByElem.merge(e, 1, Integer::sum);
		String[] elems  = costByElem.keySet().toArray(String[]::new);
		int      totalCost = rawCost.size();

		// If zero CP cost and only Dull/Special, confirm immediately
		if (totalCost == 0) {
			executeAbilityPayment(ability, source, applyDull, new ArrayList<>(), new ArrayList<>());
			return;
		}

		JDialog dlg = new JDialog(frame, "Activate: " + source.name(), true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		List<CardData> hand = gameState.getP1Hand();
		Map<String, Integer> bankCpByElem = new LinkedHashMap<>(costByElem);
		for (String k : bankCpByElem.keySet()) bankCpByElem.put(k, 0);

		List<Integer> selectedBackups  = new ArrayList<>();
		List<Integer> selectedDiscards = new ArrayList<>();

		List<Integer> eligibleBackupSlots = new ArrayList<>();
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] != null && p1BackupStates[i] == CardState.NORMAL
					&& (genericNeeded > 0 || matchesAnyElement(p1BackupCards[i], elems)))
				eligibleBackupSlots.add(i);
		}

		JLabel cpLabel     = new JLabel();
		cpLabel.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cpLabel.setHorizontalAlignment(SwingConstants.CENTER);

		JButton confirmBtn = new JButton("Confirm");
		confirmBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));

		List<JLabel>  backupLbls  = new ArrayList<>();
		List<Integer> backupSlots = new ArrayList<>();
		List<JLabel>  discardLbls = new ArrayList<>();
		List<Integer> discardIdxs = new ArrayList<>();

		boolean[] canAddDiscard = {false};
		Runnable updateAll = () -> {
			Map<String, Integer> cpByElem = new LinkedHashMap<>(bankCpByElem);
			int extraCp = 0;
			for (int slot : selectedBackups) {
				if (matchesAnyElement(p1BackupCards[slot], elems))
					cpByElem.merge(contributingElement(p1BackupCards[slot], elems, cpByElem, costByElem), 1, Integer::sum);
				else extraCp++;
			}
			for (int idx : selectedDiscards) {
				if (matchesAnyElement(hand.get(idx), elems))
					cpByElem.merge(contributingElement(hand.get(idx), elems, cpByElem, costByElem), 2, Integer::sum);
				else extraCp += 2;
			}
			int total       = cpByElem.values().stream().mapToInt(Integer::intValue).sum() + extraCp;
			int unsatisfied = (int) java.util.stream.IntStream.range(0, elems.length)
					.filter(ei -> cpByElem.getOrDefault(elems[ei], 0) < costByElem.get(elems[ei])).count();
			int maxAllowed  = totalCost + elems.length + (totalCost % 2);
			boolean canAddBackup = total < totalCost;
			canAddDiscard[0]     = (total + 2 <= maxAllowed) && (total < totalCost || unsatisfied > 0);
			boolean satisfied    = cpByElem.entrySet().stream()
					.allMatch(en -> en.getValue() >= costByElem.getOrDefault(en.getKey(), 0));
			confirmBtn.setEnabled(total >= totalCost && satisfied);

			StringBuilder sb = new StringBuilder("CP: " + total + " / " + totalCost + "  (");
			boolean first = true;
			for (String en : elems) {
				if (!first) sb.append(", ");
				sb.append(en).append(": ").append(cpByElem.getOrDefault(en, 0)).append("/").append(costByElem.get(en));
				first = false;
			}
			if (genericNeeded > 0) {
				if (!first) sb.append(", ");
				sb.append("any: ").append(Math.min(extraCp, (int) genericNeeded)).append("/").append((int) genericNeeded);
			}
			if (first) sb.append("free");
			sb.append(")");
			cpLabel.setText(sb.toString());

			for (int i = 0; i < backupLbls.size(); i++) {
				JLabel lbl = backupLbls.get(i); boolean sel = selectedBackups.contains(backupSlots.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(sel ? Color.YELLOW : (canAddBackup ? Color.GRAY : new Color(80,80,80)), sel?3:1));
				lbl.setBackground(sel || canAddBackup ? Color.DARK_GRAY : new Color(50,50,50));
				lbl.setCursor(sel || canAddBackup ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
			for (int i = 0; i < discardLbls.size(); i++) {
				JLabel lbl = discardLbls.get(i); boolean sel = selectedDiscards.contains(discardIdxs.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(sel ? Color.YELLOW : (canAddDiscard[0] ? Color.GRAY : new Color(80,80,80)), sel?3:1));
				lbl.setBackground(sel || canAddDiscard[0] ? Color.DARK_GRAY : new Color(50,50,50));
				lbl.setCursor(sel || canAddDiscard[0] ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
		};
		updateAll.run();

		JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

		if (!eligibleBackupSlots.isEmpty()) {
			JLabel hdr = new JLabel("Backups — dull for 1 CP each:");
			hdr.setFont(new Font("Pixel NES", Font.PLAIN, 9)); hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
			JPanel bp = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6)); bp.setAlignmentX(Component.LEFT_ALIGNMENT);
			for (int slot : eligibleBackupSlots) {
				JLabel lbl = new JLabel("...", SwingConstants.CENTER);
				lbl.setPreferredSize(new Dimension(CARD_W, CARD_H)); lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
				lbl.setOpaque(true); lbl.setBackground(Color.DARK_GRAY); lbl.setForeground(Color.WHITE);
				lbl.setFont(new Font("Pixel NES", Font.PLAIN, 10)); lbl.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
				lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				final String url = p1BackupUrls[slot];
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mousePressed(MouseEvent ev) {
						int tot = bankCpByElem.values().stream().mapToInt(Integer::intValue).sum() + selectedBackups.size() + selectedDiscards.size() * 2;
						if (selectedBackups.remove(Integer.valueOf(slot))) { /* deselect */ } else if (tot < totalCost) selectedBackups.add(slot);
						updateAll.run();
					}
					@Override public void mouseEntered(MouseEvent ev) { if (lbl.getIcon() != null) showZoomAt(url, lbl); }
					@Override public void mouseExited(MouseEvent ev)  { hideZoom(); }
				});
				new SwingWorker<ImageIcon, Void>() {
					@Override protected ImageIcon doInBackground() throws Exception {
						Image img = ImageCache.load(url);
						return img == null ? null : new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
					}
					@Override protected void done() {
						try { ImageIcon ic = get(); if (ic != null) { lbl.setIcon(ic); lbl.setText(null); } }
						catch (InterruptedException | ExecutionException ignored) {}
					}
				}.execute();
				backupLbls.add(lbl); backupSlots.add(slot); bp.add(lbl);
			}
			center.add(hdr); center.add(bp);
		}

		JLabel discHdr = new JLabel("Hand — discard for 2 CP each:");
		discHdr.setFont(new Font("Pixel NES", Font.PLAIN, 9)); discHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel dp = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6)); dp.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < hand.size(); i++) {
			final int hi = i; CardData hc = hand.get(i); boolean payable = !hc.isLightOrDark();
			JLabel lbl = new JLabel("...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H)); lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true); lbl.setBackground(payable ? Color.DARK_GRAY : new Color(50,50,50));
			lbl.setForeground(Color.WHITE); lbl.setFont(new Font("Pixel NES", Font.PLAIN, 10));
			lbl.setBorder(BorderFactory.createLineBorder(payable ? Color.GRAY : new Color(80,80,80), 1));
			lbl.setCursor(payable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			final String imgUrl = hc.imageUrl();
			if (payable) {
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mousePressed(MouseEvent ev) {
						if (!selectedDiscards.remove(Integer.valueOf(hi)) && canAddDiscard[0]) selectedDiscards.add(hi);
						updateAll.run();
					}
					@Override public void mouseEntered(MouseEvent ev) { if (lbl.getIcon() != null) showZoomAt(imgUrl, lbl); }
					@Override public void mouseExited(MouseEvent ev)  { hideZoom(); }
				});
				discardLbls.add(lbl); discardIdxs.add(hi);
			} else {
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mouseEntered(MouseEvent ev) { if (lbl.getIcon() != null) showZoomAt(imgUrl, lbl); }
					@Override public void mouseExited(MouseEvent ev)  { hideZoom(); }
				});
			}
			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(imgUrl);
					return img == null ? null : new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
				}
				@Override protected void done() {
					try { ImageIcon ic = get(); if (ic != null) { lbl.setIcon(ic); lbl.setText(null); } }
					catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();
			dp.add(lbl);
		}
		center.add(discHdr); center.add(dp);

		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cancelBtn.addActionListener(ev -> dlg.dispose());
		confirmBtn.addActionListener(ev -> {
			dlg.dispose();
			executeAbilityPayment(ability, source, applyDull,
					new ArrayList<>(selectedDiscards), new ArrayList<>(selectedBackups));
		});

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
		buttonPanel.add(confirmBtn); buttonPanel.add(cancelBtn);

		// Build cost summary for title
		StringBuilder costDesc = new StringBuilder();
		boolean cf = true;
		if (ability.requiresDull()) { costDesc.append("Dull"); cf = false; }
		if (ability.isSpecial())    { if (!cf) costDesc.append(" + "); costDesc.append("S (discard ").append(source.name()).append(")"); cf = false; }
		for (Map.Entry<String, Integer> en : costByElem.entrySet()) {
			if (!cf) costDesc.append(" + ");
			costDesc.append(en.getValue()).append(" ").append(en.getKey()).append(" CP"); cf = false;
		}
		if (genericNeeded > 0) { if (!cf) costDesc.append(" + "); costDesc.append((int) genericNeeded).append(" any CP"); }

		JLabel titleLabel = new JLabel(
				"<html><center>" + source.name() + " — " + (costDesc.length() > 0 ? costDesc : "free") + "</center></html>",
				SwingConstants.CENTER);
		titleLabel.setFont(new Font("Pixel NES", Font.PLAIN, 11));

		JPanel topPanel = new JPanel(new BorderLayout(0, 4));
		topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
		topPanel.add(titleLabel, BorderLayout.NORTH);
		topPanel.add(cpLabel,   BorderLayout.CENTER);

		JPanel mainPanel = new JPanel(new BorderLayout(0, 4));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
		mainPanel.add(new JScrollPane(center), BorderLayout.CENTER);
		mainPanel.add(buttonPanel,             BorderLayout.SOUTH);

		dlg.getContentPane().setLayout(new BorderLayout());
		dlg.getContentPane().add(topPanel,  BorderLayout.NORTH);
		dlg.getContentPane().add(mainPanel, BorderLayout.CENTER);
		dlg.pack(); dlg.setLocationRelativeTo(frame); dlg.setVisible(true);
	}

	/**
	 * Executes the full payment for an action ability: dulls selected backups,
	 * discards hand cards for CP, optionally dulls the source card, optionally
	 * discards a same-name card (Special), then calls {@link ActionResolver#resolve}.
	 */
	private void executeAbilityPayment(ActionAbility ability, CardData source,
			Runnable applyDull, List<Integer> discardIndices, List<Integer> backupDullIndices) {
		List<String> rawCost = ability.cpCost();
		LinkedHashMap<String, Integer> costByElem = new LinkedHashMap<>();
		for (String e : rawCost) if (!e.isEmpty()) costByElem.merge(e, 1, Integer::sum);
		String[] elems = costByElem.keySet().toArray(String[]::new);

		for (int bi : backupDullIndices) {
			p1BackupStates[bi] = CardState.DULLED;
			animateDullBackup(bi, true);
			String cpElem = matchesAnyElement(p1BackupCards[bi], elems)
					? contributingElement(p1BackupCards[bi], elems) : (elems.length > 0 ? elems[0] : "");
			if (!cpElem.isEmpty()) gameState.addP1Cp(cpElem, 1);
		}
		discardIndices.sort(Collections.reverseOrder());
		for (int di : discardIndices) {
			CardData discarded = gameState.getP1Hand().get(di);
			String cpElem = matchesAnyElement(discarded, elems)
					? contributingElement(discarded, elems) : (elems.length > 0 ? elems[0] : "");
			if (!cpElem.isEmpty()) gameState.addP1Cp(cpElem, 2);
			gameState.breakFromHand(di);
		}
		for (String e : elems) { gameState.spendP1Cp(e, gameState.getP1CpForElement(e)); gameState.clearP1Cp(e); }

		// Dull source card
		if (ability.requiresDull()) applyDull.run();

		// Special: discard first same-name card from hand
		if (ability.isSpecial()) {
			List<CardData> hand = gameState.getP1Hand();
			for (int i = 0; i < hand.size(); i++) {
				if (source.name().equalsIgnoreCase(hand.get(i).name())) {
					gameState.breakFromHand(i);
					logEntry("Special: discarded \"" + source.name() + "\" from hand");
					break;
				}
			}
		}

		logEntry("\"" + source.name() + "\" activated ability");

		// Build a GameContext so ActionResolver can apply field effects without
		// direct access to MainWindow's private fields.
		GameContext ctx = new GameContext() {
			@Override public void logEntry(String msg) { MainWindow.this.logEntry(msg); }

			@Override public int p1ForwardCount()                    { return p1ForwardCards.size(); }
			@Override public CardData p1Forward(int idx) {
				CardData top = p1ForwardPrimedTop.get(idx);
				return top != null ? top : p1ForwardCards.get(idx);
			}
			@Override public int       p1ForwardCurrentDamage(int idx) { return p1ForwardDamage.get(idx); }
			@Override public CardState p1ForwardState(int idx)          { return p1ForwardStates.get(idx); }
			@Override public void damageP1Forward(int idx, int amount) {
				if (idx >= p1ForwardCards.size()) return;
				int dmg = p1ForwardDamage.get(idx) + amount;
				p1ForwardDamage.set(idx, dmg);
				CardData eff = p1Forward(idx);
				logEntry(eff.name() + " takes " + amount + " damage"
						+ (eff.power() > 0 ? " (" + (eff.power() - dmg) + " remaining)" : ""));
				if (eff.power() > 0 && dmg >= eff.power()) breakP1Forward(idx);
				else refreshP1ForwardSlot(idx);
			}

			@Override public int p2ForwardCount()                    { return p2ForwardCards.size(); }
			@Override public CardData p2Forward(int idx)             { return p2ForwardCards.get(idx); }
			@Override public int       p2ForwardCurrentDamage(int idx) { return p2ForwardDamage.get(idx); }
			@Override public CardState p2ForwardState(int idx)          { return p2ForwardStates.get(idx); }
			@Override public void damageP2Forward(int idx, int amount) {
				if (idx >= p2ForwardCards.size()) return;
				int dmg = p2ForwardDamage.get(idx) + amount;
				p2ForwardDamage.set(idx, dmg);
				CardData card = p2ForwardCards.get(idx);
				logEntry("[P2] " + card.name() + " takes " + amount + " damage"
						+ (card.power() > 0 ? " (" + (card.power() - dmg) + " remaining)" : ""));
				if (card.power() > 0 && dmg >= card.power()) breakP2Forward(idx);
				else refreshP2ForwardSlot(idx);
			}

			@Override
			public java.util.List<ForwardTarget> selectForwards(
					int maxCount, boolean upTo, boolean opponentOnly, String condition) {
				java.util.List<ForwardTarget> eligible = new ArrayList<>();
				if (!opponentOnly) {
					for (int i = 0; i < p1ForwardCards.size(); i++) {
						if (meetsTargetCondition(p1ForwardStates.get(i), p1ForwardDamage.get(i), condition))
							eligible.add(new ForwardTarget(true, i));
					}
				}
				for (int i = 0; i < p2ForwardCards.size(); i++) {
					if (meetsTargetCondition(p2ForwardStates.get(i), p2ForwardDamage.get(i), condition))
						eligible.add(new ForwardTarget(false, i));
				}
				String title = "Choose " + (upTo ? "up to " : "") + maxCount
						+ (condition != null ? " " + condition : "")
						+ " Forward" + (maxCount != 1 ? "s" : "")
						+ (opponentOnly ? " (opponent)" : "");
				return showForwardSelectDialog(eligible, maxCount, upTo, title);
			}

			@Override public void dullP1Forward(int idx) {
				if (idx >= p1ForwardStates.size()) return;
				p1ForwardStates.set(idx, CardState.DULLED);
				logEntry(p1Forward(idx).name() + " is dulled");
				refreshP1ForwardSlot(idx);
			}

			@Override public void dullP2Forward(int idx) {
				if (idx >= p2ForwardStates.size()) return;
				p2ForwardStates.set(idx, CardState.DULLED);
				logEntry("[P2] " + p2ForwardCards.get(idx).name() + " is dulled");
				refreshP2ForwardSlot(idx);
			}
		};

		ActionResolver.resolve(ability, source, gameState, ctx);
		refreshP1HandLabel();
		refreshP1BreakLabel();
	}

	// -------------------------------------------------------------------------
	// Forward target selection helpers (used by GameContext.selectForwards)
	// -------------------------------------------------------------------------

	private static boolean meetsTargetCondition(CardState state, int damage, String condition) {
		if (condition == null) return true;
		return switch (condition.toLowerCase()) {
			case "dull", "dulled" -> state == CardState.DULLED;
			case "damaged"        -> damage > 0;
			default               -> true;
		};
	}

	/**
	 * Shows a modal dialog for P1 to pick targeted forwards from {@code eligible}.
	 * Auto-selects all when the eligible count does not exceed {@code maxCount} and
	 * {@code upTo} is false.  Returns immediately with an empty list when there are
	 * no eligible targets.
	 */
	private java.util.List<ForwardTarget> showForwardSelectDialog(
			java.util.List<ForwardTarget> eligible, int maxCount, boolean upTo, String title) {
		if (eligible.isEmpty()) { logEntry("Choose: no eligible targets"); return java.util.List.of(); }
		if (!upTo && eligible.size() <= maxCount) return java.util.List.copyOf(eligible);

		JDialog dlg = new JDialog(frame, title, true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		java.util.List<ForwardTarget> chosen = new ArrayList<>();
		java.util.Set<Integer> sel = new java.util.LinkedHashSet<>();

		JPanel btnsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
		JButton[] btns = new JButton[eligible.size()];

		JButton confirmBtn = new JButton("Confirm");
		confirmBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		confirmBtn.setEnabled(upTo); // enabled immediately for "up to", otherwise waits for maxCount

		for (int i = 0; i < eligible.size(); i++) {
			ForwardTarget target = eligible.get(i);
			CardData card = target.isP1()
					? (p1ForwardPrimedTop.get(target.idx()) != null
							? p1ForwardPrimedTop.get(target.idx()) : p1ForwardCards.get(target.idx()))
					: p2ForwardCards.get(target.idx());
			final int fi = i;
			JButton btn = new JButton("<html><center>"
					+ (target.isP1() ? "[You] " : "[P2] ")
					+ card.name() + "<br>(" + card.power() + ")</center></html>");
			btn.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			btn.setPreferredSize(new Dimension(130, 56));
			btn.addActionListener(ae -> {
				if (sel.contains(fi)) {
					sel.remove(fi);
					btn.setBackground(null);
				} else {
					if (sel.size() >= maxCount) return;
					sel.add(fi);
					btn.setBackground(Color.YELLOW);
					if (!upTo && sel.size() == maxCount) {
						for (int si : sel) chosen.add(eligible.get(si));
						dlg.dispose();
						return;
					}
				}
				confirmBtn.setEnabled(upTo || sel.size() == maxCount);
			});
			btns[i] = btn;
			btnsPanel.add(btn);
		}

		confirmBtn.addActionListener(ae -> {
			for (int si : sel) chosen.add(eligible.get(si));
			dlg.dispose();
		});
		JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
		south.add(confirmBtn);
		if (upTo) {
			JButton skipBtn = new JButton("Skip");
			skipBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
			skipBtn.addActionListener(ae -> dlg.dispose());
			south.add(skipBtn);
		}

		JLabel hdr = new JLabel(title, SwingConstants.CENTER);
		hdr.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		hdr.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

		dlg.getContentPane().setLayout(new BorderLayout(0, 4));
		dlg.getContentPane().add(hdr,      BorderLayout.NORTH);
		dlg.getContentPane().add(btnsPanel, BorderLayout.CENTER);
		dlg.getContentPane().add(south,     BorderLayout.SOUTH);
		dlg.pack();
		dlg.setLocationRelativeTo(frame);
		dlg.setVisible(true);
		return java.util.List.copyOf(chosen);
	}

	// -------------------------------------------------------------------------

	private void showBackupContextMenu(int idx, JLabel slot, MouseEvent e) {
		JPopupMenu menu = new JPopupMenu();

		CardData card = p1BackupCards[idx];
		if (card != null) {
			addAbilityMenuItems(menu, card, p1BackupStates[idx], p1BackupPlayedOnTurn[idx],
					() -> { p1BackupStates[idx] = CardState.DULLED; animateDullBackup(idx, true); });
		}

		if (AppSettings.isDebugMode()) {
			if (menu.getComponentCount() > 0) menu.addSeparator();
			JMenuItem dullItem = new JMenuItem("Debug: Dull");
			dullItem.addActionListener(ae -> {
				boolean dulling = p1BackupStates[idx] != CardState.DULLED;
				p1BackupStates[idx] = dulling ? CardState.DULLED : CardState.NORMAL;
				animateDullBackup(idx, dulling);
			});
			menu.add(dullItem);

			JMenuItem freezeItem = new JMenuItem("Debug: Freeze");
			freezeItem.addActionListener(ae -> {
				boolean freezing = p1BackupStates[idx] != CardState.FROZEN;
				CardState prevState = p1BackupStates[idx];
				p1BackupStates[idx] = freezing ? CardState.FROZEN : CardState.NORMAL;
				animateFreezeBackup(idx, freezing, prevState);
			});
			menu.add(freezeItem);
		}

		if (menu.getComponentCount() > 0) menu.show(slot, e.getX(), e.getY());
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
	private static final int FORWARD_ZONE_H = CARD_H * 5 / 4;

	private JScrollPane buildForwardZonePanel(boolean isP1) {
		JPanel forwardInner = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
			@Override
			public Dimension getPreferredSize() {
				int gap   = 4;
				int slots = getComponentCount();
				int width = gap + (CARD_H + gap) * slots;
				return new Dimension(Math.max(width, gap * 2), FORWARD_ZONE_H);
			}
		};
		forwardInner.setOpaque(false);
		if (isP1) p1ForwardPanel = forwardInner;
		else      p2ForwardPanel = forwardInner;

		JPanel monsterInner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)) {
			@Override
			public Dimension getPreferredSize() {
				int gap   = 4;
				int slots = getComponentCount();
				int width = slots > 0 ? gap + (CARD_H + gap) * slots : 0;
				return new Dimension(width, FORWARD_ZONE_H);
			}
		};
		monsterInner.setOpaque(false);
		if (isP1) p1MonsterPanel = monsterInner;

		// Monster panel sits at the bottom of the EAST area for "lower-right" appearance
		JPanel monsterContainer = new JPanel(new BorderLayout());
		monsterContainer.setOpaque(false);
		monsterContainer.add(monsterInner, BorderLayout.SOUTH);

		JPanel outer = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Dimension fwd = forwardInner.getPreferredSize();
				Dimension mon = monsterInner.getPreferredSize();
				return new Dimension(fwd.width + mon.width, FORWARD_ZONE_H);
			}
		};
		outer.setOpaque(false);
		outer.add(forwardInner,    BorderLayout.CENTER);
		outer.add(monsterContainer, BorderLayout.EAST);

		JScrollPane scroll = new JScrollPane(outer,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setPreferredSize(new Dimension(0, FORWARD_ZONE_H));
		return scroll;
	}

	/** Adds a Forward card to P1's forward zone and wires up the debug context menu. */
	private void placeCardInForwardZone(CardData card) {
		if (p1ForwardPanel == null) return;
		int idx = p1ForwardLabels.size();

		JLabel lbl = new JLabel("", SwingConstants.CENTER);
		lbl.setPreferredSize(new Dimension(CARD_H, CARD_H));
		lbl.setMinimumSize(new Dimension(CARD_H, CARD_H));
		lbl.setOpaque(false);
		lbl.setForeground(Color.DARK_GRAY);
		lbl.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		lbl.setBorder(BorderFactory.createEmptyBorder());
		lbl.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) {
				if (lbl.getIcon() == null) return;
				if (SwingUtilities.isLeftMouseButton(e)
						&& gameState.getCurrentPhase() == GameState.GamePhase.ATTACK) {
					toggleAttackSelection(idx);
				} else {
					showForwardContextMenu(idx, lbl, e);
				}
			}
			@Override public void mouseEntered(MouseEvent e) {
				if (lbl.getIcon() == null) return;
				CardData top = p1ForwardPrimedTop.get(idx);
				showZoomAt(top != null ? top.imageUrl() : p1ForwardUrls.get(idx), lbl);
			}
			@Override public void mouseExited(MouseEvent e) { hideZoom(); }
		});

		p1ForwardUrls.add(card.imageUrl());
		p1ForwardCards.add(card);
		p1ForwardStates.add(CardState.NORMAL);
		p1ForwardPlayedOnTurn.add(gameState.getTurnNumber());
		p1ForwardDamage.add(0);
		p1ForwardPrimedTop.add(null);
		p1ForwardLabels.add(lbl);

		p1ForwardPanel.add(lbl);
		p1ForwardPanel.revalidate();
		p1ForwardPanel.repaint();

		refreshP1ForwardSlot(idx);
	}

	/** Adds a Monster card to P1's monster zone (right side of forward zone, newest leftmost). */
	private void placeCardInMonsterZone(CardData card) {
		if (p1MonsterPanel == null) return;
		int idx = p1MonsterLabels.size();

		JLabel lbl = new JLabel("", SwingConstants.CENTER);
		lbl.setPreferredSize(new Dimension(CARD_H, CARD_H));
		lbl.setMinimumSize(new Dimension(CARD_H, CARD_H));
		lbl.setOpaque(false);
		lbl.setForeground(Color.DARK_GRAY);
		lbl.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		lbl.setBorder(BorderFactory.createEmptyBorder());
		lbl.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) {
				if (lbl.getIcon() != null) showMonsterContextMenu(idx, lbl, e);
			}
			@Override public void mouseEntered(MouseEvent e) {
				if (lbl.getIcon() != null) showZoomAt(p1MonsterUrls.get(idx), lbl);
			}
			@Override public void mouseExited(MouseEvent e) { hideZoom(); }
		});

		p1MonsterUrls.add(card.imageUrl());
		p1MonsterCards.add(card);
		p1MonsterStates.add(CardState.NORMAL);
		p1MonsterPlayedOnTurn.add(gameState.getTurnNumber());
		p1MonsterLabels.add(lbl);

		// Insert at front so newest monster appears leftmost
		p1MonsterPanel.add(lbl, 0);
		p1MonsterPanel.revalidate();
		p1MonsterPanel.repaint();

		refreshP1MonsterSlot(idx);
	}

	/** Reloads and re-renders a single P1 monster slot using its stored URL and state. */
	private void refreshP1MonsterSlot(int idx) {
		String url   = p1MonsterUrls.get(idx);
		CardState state = p1MonsterStates.get(idx);
		JLabel slot  = p1MonsterLabels.get(idx);
		if (url == null) return;
		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				if (raw == null) return null;
				BufferedImage card = toARGB(raw, CARD_W, CARD_H);
				return new ImageIcon(renderBackupCard(card, state, false));
			}
			@Override protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) { slot.setIcon(icon); slot.setText(null); }
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	/** Shows a context menu for a P1 monster slot. */
	private void showMonsterContextMenu(int idx, JLabel slot, MouseEvent e) {
		JPopupMenu menu = new JPopupMenu();

		// Action abilities
		addAbilityMenuItems(menu, p1MonsterCards.get(idx), p1MonsterStates.get(idx),
				p1MonsterPlayedOnTurn.get(idx),
				() -> { p1MonsterStates.set(idx, CardState.DULLED); refreshP1MonsterSlot(idx); });

		if (AppSettings.isDebugMode()) {
			JMenuItem dullItem = new JMenuItem("Debug: Dull");
			dullItem.addActionListener(ae -> {
				p1MonsterStates.set(idx,
						p1MonsterStates.get(idx) == CardState.DULLED ? CardState.NORMAL : CardState.DULLED);
				refreshP1MonsterSlot(idx);
			});
			menu.add(dullItem);

			JMenuItem freezeItem = new JMenuItem("Debug: Freeze");
			freezeItem.addActionListener(ae -> {
				p1MonsterStates.set(idx,
						p1MonsterStates.get(idx) == CardState.FROZEN ? CardState.NORMAL : CardState.FROZEN);
				refreshP1MonsterSlot(idx);
			});
			menu.add(freezeItem);
		}

		if (menu.getComponentCount() > 0) menu.show(slot, e.getX(), e.getY());
	}

	/** Reloads and re-renders a single P1 forward slot using its stored URL and state. */
	private void refreshP1ForwardSlot(int idx) {
		CardData topCard = p1ForwardPrimedTop.get(idx);
		boolean  primed  = topCard != null;
		// Primed: display and stats come from the top card
		String    url    = primed ? topCard.imageUrl() : p1ForwardUrls.get(idx);
		CardState state  = p1ForwardStates.get(idx);
		JLabel    slot   = p1ForwardLabels.get(idx);
		if (url == null) return;
		boolean hasHaste  = p1ForwardCards.get(idx).hasTrait(CardData.Trait.HASTE)
				|| (primed && topCard.hasTrait(CardData.Trait.HASTE));
		boolean canAttack = gameState.getCurrentPhase() == GameState.GamePhase.ATTACK
				&& state == CardState.NORMAL
				&& (hasHaste || p1ForwardPlayedOnTurn.get(idx) != gameState.getTurnNumber());
		int damage = p1ForwardDamage.get(idx);
		int power  = primed ? topCard.power() : p1ForwardCards.get(idx).power();
		boolean selected = p1AttackSelection.contains(idx);
		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				if (raw == null) return null;
				BufferedImage canvas = renderBackupCard(toARGB(raw, CARD_W, CARD_H), state, canAttack, selected);
				if (damage > 0 && power > 0) renderDamageOverlay(canvas, power - damage);
				return new ImageIcon(canvas);
			}
			@Override protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) { slot.setIcon(icon); slot.setText(null); }
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	private void refreshAllForwardSlots() {
		for (int i = 0; i < p1ForwardLabels.size(); i++) refreshP1ForwardSlot(i);
		for (int i = 0; i < p1MonsterLabels.size(); i++) refreshP1MonsterSlot(i);
	}

	private boolean isForwardSelectable(int idx) {
		if (gameState.getCurrentPhase() != GameState.GamePhase.ATTACK) return false;
		if (idx < 0 || idx >= p1ForwardStates.size()) return false;
		if (p1ForwardStates.get(idx) != CardState.NORMAL) return false;
		boolean hasHaste = p1ForwardCards.get(idx).hasTrait(CardData.Trait.HASTE);
		return hasHaste || p1ForwardPlayedOnTurn.get(idx) != gameState.getTurnNumber();
	}

	private void toggleAttackSelection(int idx) {
		if (!isForwardSelectable(idx)) return;
		if (p1AttackSelection.contains(idx)) {
			p1AttackSelection.remove((Integer) idx);
			refreshAttackButton();
			refreshP1ForwardSlot(idx);
			return;
		}
		if (!p1AttackSelection.isEmpty()) {
			String partyElement = p1ForwardCards.get(p1AttackSelection.get(0)).elements()[0];
			if (!p1ForwardCards.get(idx).containsElement(partyElement)) {
				logEntry("Cannot add to party — different element");
				return;
			}
		}
		p1AttackSelection.add(idx);
		refreshAttackButton();
		refreshP1ForwardSlot(idx);
	}

	private void refreshAttackButton() {
		if (attackButton == null) return;
		boolean inAttack = gameState.getCurrentPhase() == GameState.GamePhase.ATTACK
				&& gameState.getCurrentPlayer() == GameState.Player.P1;
		int n = p1AttackSelection.size();
		attackButton.setEnabled(inAttack && n > 0);
		attackButton.setText(n > 1 ? "Party Attack" : "Attack");
	}

	private void executeP1Attack(List<Integer> selection) {
		if (selection.isEmpty()) return;
		for (int idx : selection) {
			CardData c = p1ForwardCards.get(idx);
			if (c.hasTrait(CardData.Trait.BRAVE)) {
				p1ForwardStates.set(idx, CardState.BRAVE_ATTACKED);
			} else {
				p1ForwardStates.set(idx, CardState.DULLED);
			}
			refreshP1ForwardSlot(idx);
		}
		if (selection.size() == 1) {
			int idx = selection.get(0);
			CardData attacker = p1ForwardCards.get(idx);
			logEntry(attacker.name() + " attacks!");
			p2OfferBlock(attacker, idx);
		} else {
			int combinedPower = 0;
			StringBuilder names = new StringBuilder();
			for (int idx : selection) {
				combinedPower += p1ForwardCards.get(idx).power();
				if (names.length() > 0) names.append(", ");
				names.append(p1ForwardCards.get(idx).name());
			}
			logEntry("Party Attack! " + names + " (" + combinedPower + " combined)");
			p2OfferBlockParty(selection, combinedPower);
		}
	}

	private void p2OfferBlockParty(List<Integer> attackerIndices, int combinedPower) {
		int bestBlockerIdx = -1, bestBlockerPower = 0;
		int minAttackerPower = Integer.MAX_VALUE;
		for (int idx : attackerIndices) {
			if (idx < p1ForwardCards.size())
				minAttackerPower = Math.min(minAttackerPower,
						p1ForwardCards.get(idx).power() - p1ForwardDamage.get(idx));
		}
		for (int i = 0; i < p2ForwardStates.size(); i++) {
			if (p2ForwardStates.get(i) != CardState.NORMAL) continue;
			int pw = p2ForwardCards.get(i).power();
			if (pw >= minAttackerPower && pw > bestBlockerPower) {
				bestBlockerPower = pw;
				bestBlockerIdx = i;
			}
		}
		if (bestBlockerIdx >= 0) {
			CardData blocker = p2ForwardCards.get(bestBlockerIdx);
			int blockerPower = blocker.power();
			logEntry("[P2] " + blocker.name() + " blocks the party!");
			if (combinedPower >= blockerPower) breakP2Forward(bestBlockerIdx);
			p2AiDistributeDamage(attackerIndices, blockerPower);
		} else {
			p2TakeDamage();
		}
	}

	private void p2AiDistributeDamage(List<Integer> attackerIndices, int blockerPower) {
		if (attackerIndices.isEmpty() || blockerPower <= 0) return;
		List<int[]> targets = new ArrayList<>();
		for (int idx : attackerIndices) {
			if (idx < p1ForwardCards.size()) {
				int hp = p1ForwardCards.get(idx).power() - p1ForwardDamage.get(idx);
				targets.add(new int[]{ idx, hp });
			}
		}
		if (targets.isEmpty()) return;
		targets.sort((a, b) -> Integer.compare(a[1], b[1]));

		Map<Integer, Integer> damageMap = new LinkedHashMap<>();
		int remaining = blockerPower;
		for (int[] t : targets) {
			if (remaining <= 0) break;
			int idx = t[0], hp = t[1];
			int dmg = Math.min(remaining, roundToThousand(hp));
			damageMap.put(idx, dmg);
			remaining -= dmg;
		}
		if (remaining > 0) {
			int lastIdx = targets.get(targets.size() - 1)[0];
			damageMap.merge(lastIdx, remaining, Integer::sum);
		}

		for (Map.Entry<Integer, Integer> entry : damageMap.entrySet()) {
			int idx = entry.getKey(), dmg = entry.getValue();
			p1ForwardDamage.set(idx, p1ForwardDamage.get(idx) + dmg);
			logEntry("[P2] Deals " + dmg + " damage to " + p1ForwardCards.get(idx).name());
		}

		List<Integer> toBreak = new ArrayList<>();
		for (int idx : damageMap.keySet()) {
			if (p1ForwardDamage.get(idx) >= p1ForwardCards.get(idx).power()) toBreak.add(idx);
		}
		toBreak.sort(Collections.reverseOrder());
		for (int idx : toBreak) breakP1Forward(idx);

		for (int i = 0; i < p1ForwardCards.size(); i++) refreshP1ForwardSlot(i);
	}

	private static int roundToThousand(int value) {
		return ((value + 999) / 1000) * 1000;
	}

	private boolean hasBackAttackInHand() {
		return gameState.getP1Hand().stream()
				.anyMatch(c -> c.hasTrait(CardData.Trait.BACK_ATTACK));
	}

	private boolean hasAttackableForward() {
		int turn = gameState.getTurnNumber();
		for (int i = 0; i < p1ForwardStates.size(); i++) {
			if (p1ForwardStates.get(i) == CardState.NORMAL
					&& (p1ForwardCards.get(i).hasTrait(CardData.Trait.HASTE)
					    || p1ForwardPlayedOnTurn.get(i) != turn))
				return true;
		}
		return false;
	}

	/** Shows a context menu for a P1 forward slot. */
	private void showForwardContextMenu(int idx, JLabel slot, MouseEvent e) {
		JPopupMenu menu = new JPopupMenu();

		// Action abilities (use effective card — top card when primed)
		CardData effectiveFwd = p1ForwardPrimedTop.get(idx) != null
				? p1ForwardPrimedTop.get(idx) : p1ForwardCards.get(idx);
		addAbilityMenuItems(menu, effectiveFwd, p1ForwardStates.get(idx),
				p1ForwardPlayedOnTurn.get(idx),
				() -> { p1ForwardStates.set(idx, CardState.DULLED); refreshP1ForwardSlot(idx); });

		// Prime — visible whenever the forward has the Priming trait
		CardData fwd = p1ForwardCards.get(idx);
		if (fwd.hasPriming()) {
			boolean alreadyPrimed = p1ForwardPrimedTop.get(idx) != null;
			GameState.GamePhase phase = gameState.getCurrentPhase();
			boolean isMainPhase = phase == GameState.GamePhase.MAIN_1 || phase == GameState.GamePhase.MAIN_2;
			JMenuItem primeItem = new JMenuItem("Prime (" + fwd.primingTarget() + ")");
			primeItem.setEnabled(isMainPhase && !alreadyPrimed && canAffordPrimingCost(fwd));
			primeItem.addActionListener(ae -> showPrimingPaymentDialog(fwd, idx));
			menu.add(primeItem);
		}

		if (AppSettings.isDebugMode()) {
			JMenuItem dullItem = new JMenuItem("Debug: Dull");
			dullItem.addActionListener(ae -> {
				p1ForwardStates.set(idx,
						p1ForwardStates.get(idx) == CardState.DULLED ? CardState.NORMAL : CardState.DULLED);
				refreshP1ForwardSlot(idx);
			});
			menu.add(dullItem);

			JMenuItem freezeItem = new JMenuItem("Debug: Freeze");
			freezeItem.addActionListener(ae -> {
				boolean freezing = p1ForwardStates.get(idx) != CardState.FROZEN;
				CardState prevState = p1ForwardStates.get(idx);
				p1ForwardStates.set(idx, freezing ? CardState.FROZEN : CardState.NORMAL);
				animateFreezeForward(idx, freezing, prevState);
			});
			menu.add(freezeItem);
		}

		if (menu.getComponentCount() > 0) menu.show(slot, e.getX(), e.getY());
	}

	/** Returns true if the player can afford the Priming cost of {@code card} (card is on the field, not in hand). */
	private boolean canAffordPrimingCost(CardData card) {
		List<String> cost = card.primingCost();
		if (cost.isEmpty()) return true;

		boolean hasGeneric = cost.contains("");
		LinkedHashMap<String, Integer> needed = new LinkedHashMap<>();
		for (String e : cost) if (!e.isEmpty()) needed.merge(e, 1, Integer::sum);
		String[] elems = needed.keySet().toArray(String[]::new);
		int total = cost.size();

		boolean[] hasSrc = new boolean[elems.length];
		int available = 0;

		for (int ei = 0; ei < elems.length; ei++) {
			int b = gameState.getP1CpForElement(elems[ei]);
			available += b;
			if (b > 0) hasSrc[ei] = true;
		}
		if (hasGeneric) {
			available += gameState.getP1CpByElement().values().stream().mapToInt(Integer::intValue).sum();
			for (int ei = 0; ei < elems.length; ei++) available -= gameState.getP1CpForElement(elems[ei]);
		}
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] == null || p1BackupStates[i] != CardState.NORMAL) continue;
			boolean matched = false;
			for (int ei = 0; ei < elems.length; ei++) {
				if (p1BackupCards[i].containsElement(elems[ei])) { available++; hasSrc[ei] = true; matched = true; break; }
			}
			if (!matched && hasGeneric) available++;
		}
		List<CardData> hand = gameState.getP1Hand();
		for (CardData h : hand) {
			if (h.isLightOrDark()) continue;
			available += 2;
			for (int ei = 0; ei < elems.length; ei++) if (h.containsElement(elems[ei])) hasSrc[ei] = true;
		}
		for (boolean s : hasSrc) if (!s) return false;
		return available >= total;
	}

	/**
	 * Payment dialog for the Priming ability cost. On confirm, searches the
	 * main deck for the target card and places it on top of the priming forward.
	 */
	private void showPrimingPaymentDialog(CardData card, int slotIdx) {
		List<String> rawCost = card.primingCost();
		long genericNeeded = rawCost.stream().filter(String::isEmpty).count();
		LinkedHashMap<String, Integer> costByElem = new LinkedHashMap<>();
		for (String e : rawCost) if (!e.isEmpty()) costByElem.merge(e, 1, Integer::sum);
		String[] elems   = costByElem.keySet().toArray(String[]::new);
		int totalCost    = rawCost.size();

		// If cost is empty, no dialog needed — go straight to execution
		if (totalCost == 0) {
			executePriming(card, slotIdx, new ArrayList<>(), new ArrayList<>());
			return;
		}

		JDialog dlg = new JDialog(frame, "Prime: " + card.name(), true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		List<CardData> hand = gameState.getP1Hand();

		Map<String, Integer> bankCpByElem = new LinkedHashMap<>(costByElem);
		for (String k : bankCpByElem.keySet()) bankCpByElem.put(k, 0);

		List<Integer> selectedBackups  = new ArrayList<>();
		List<Integer> selectedDiscards = new ArrayList<>();

		List<Integer> eligibleBackupSlots = new ArrayList<>();
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] != null && p1BackupStates[i] == CardState.NORMAL
					&& (genericNeeded > 0 || matchesAnyElement(p1BackupCards[i], elems)))
				eligibleBackupSlots.add(i);
		}

		JLabel cpLabel = new JLabel();
		cpLabel.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cpLabel.setHorizontalAlignment(SwingConstants.CENTER);

		JButton confirmBtn = new JButton("Confirm (Prime)");
		confirmBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));

		List<JLabel>   backupLbls  = new ArrayList<>();
		List<Integer>  backupSlots = new ArrayList<>();
		List<JLabel>   discardLbls = new ArrayList<>();
		List<Integer>  discardIdxs = new ArrayList<>();

		boolean[] canAddDiscard = {false};
		Runnable updateAll = () -> {
			Map<String, Integer> cpByElem = new LinkedHashMap<>(bankCpByElem);
			int extraCp = 0;
			for (int slot : selectedBackups) {
				if (matchesAnyElement(p1BackupCards[slot], elems))
					cpByElem.merge(contributingElement(p1BackupCards[slot], elems, cpByElem, costByElem), 1, Integer::sum);
				else extraCp++;
			}
			for (int idx : selectedDiscards) {
				if (matchesAnyElement(hand.get(idx), elems))
					cpByElem.merge(contributingElement(hand.get(idx), elems, cpByElem, costByElem), 2, Integer::sum);
				else extraCp += 2;
			}
			int total      = cpByElem.values().stream().mapToInt(Integer::intValue).sum() + extraCp;
			int unsatisfied = (int) java.util.stream.IntStream.range(0, elems.length)
					.filter(ei -> cpByElem.getOrDefault(elems[ei], 0) < costByElem.get(elems[ei])).count();
			int maxAllowed  = totalCost + elems.length + (totalCost % 2);
			boolean canAddBackup = total < totalCost;
			canAddDiscard[0] = (total + 2 <= maxAllowed) && (total < totalCost || unsatisfied > 0);
			boolean satisfied = cpByElem.entrySet().stream()
					.allMatch(en -> en.getValue() >= costByElem.getOrDefault(en.getKey(), 0));
			confirmBtn.setEnabled(total >= totalCost && satisfied);

			StringBuilder sb = new StringBuilder("Prime CP: " + total + " / " + totalCost + "  (");
			boolean first = true;
			for (String en : elems) {
				if (!first) sb.append(", ");
				sb.append(en).append(": ").append(cpByElem.getOrDefault(en, 0)).append("/").append(costByElem.get(en));
				first = false;
			}
			if (genericNeeded > 0) {
				if (!first) sb.append(", ");
				sb.append("any: ").append(Math.min(extraCp, (int) genericNeeded)).append("/").append((int) genericNeeded);
			}
			if (first) sb.append("free");
			sb.append(")");
			cpLabel.setText(sb.toString());

			for (int i = 0; i < backupLbls.size(); i++) {
				JLabel lbl = backupLbls.get(i); boolean sel = selectedBackups.contains(backupSlots.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(sel ? Color.YELLOW : (canAddBackup ? Color.GRAY : new Color(80,80,80)), sel ? 3 : 1));
				lbl.setBackground(sel || canAddBackup ? Color.DARK_GRAY : new Color(50,50,50));
				lbl.setCursor(sel || canAddBackup ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
			for (int i = 0; i < discardLbls.size(); i++) {
				JLabel lbl = discardLbls.get(i); boolean sel = selectedDiscards.contains(discardIdxs.get(i));
				lbl.setBorder(BorderFactory.createLineBorder(sel ? Color.YELLOW : (canAddDiscard[0] ? Color.GRAY : new Color(80,80,80)), sel ? 3 : 1));
				lbl.setBackground(sel || canAddDiscard[0] ? Color.DARK_GRAY : new Color(50,50,50));
				lbl.setCursor(sel || canAddDiscard[0] ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
		};
		updateAll.run();

		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

		if (!eligibleBackupSlots.isEmpty()) {
			JLabel hdr = new JLabel("Backups — dull for 1 CP each:");
			hdr.setFont(new Font("Pixel NES", Font.PLAIN, 9)); hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
			JPanel bp = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6)); bp.setAlignmentX(Component.LEFT_ALIGNMENT);
			for (int slot : eligibleBackupSlots) {
				JLabel lbl = new JLabel("...", SwingConstants.CENTER);
				lbl.setPreferredSize(new Dimension(CARD_W, CARD_H)); lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
				lbl.setOpaque(true); lbl.setBackground(Color.DARK_GRAY); lbl.setForeground(Color.WHITE);
				lbl.setFont(new Font("Pixel NES", Font.PLAIN, 10)); lbl.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
				lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				final String url = p1BackupUrls[slot];
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mousePressed(MouseEvent ev) {
						int tot = bankCpByElem.values().stream().mapToInt(Integer::intValue).sum() + selectedBackups.size() + selectedDiscards.size() * 2;
						if (selectedBackups.remove(Integer.valueOf(slot))) { /* deselect */ } else if (tot < totalCost) selectedBackups.add(slot);
						updateAll.run();
					}
					@Override public void mouseEntered(MouseEvent ev) { if (lbl.getIcon() != null) showZoomAt(url, lbl); }
					@Override public void mouseExited(MouseEvent ev)  { hideZoom(); }
				});
				new SwingWorker<ImageIcon, Void>() {
					@Override protected ImageIcon doInBackground() throws Exception {
						Image img = ImageCache.load(url);
						return img == null ? null : new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
					}
					@Override protected void done() {
						try { ImageIcon ic = get(); if (ic != null) { lbl.setIcon(ic); lbl.setText(null); } }
						catch (InterruptedException | ExecutionException ignored) {}
					}
				}.execute();
				backupLbls.add(lbl); backupSlots.add(slot); bp.add(lbl);
			}
			centerPanel.add(hdr); centerPanel.add(bp);
		}

		JLabel discardHdr = new JLabel("Hand — discard for 2 CP each:");
		discardHdr.setFont(new Font("Pixel NES", Font.PLAIN, 9)); discardHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel dp = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6)); dp.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < hand.size(); i++) {
			final int hi = i; CardData hc = hand.get(i); boolean payable = !hc.isLightOrDark();
			JLabel lbl = new JLabel("...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H)); lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true); lbl.setBackground(payable ? Color.DARK_GRAY : new Color(50,50,50));
			lbl.setForeground(Color.WHITE); lbl.setFont(new Font("Pixel NES", Font.PLAIN, 10));
			lbl.setBorder(BorderFactory.createLineBorder(payable ? Color.GRAY : new Color(80,80,80), 1));
			lbl.setCursor(payable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			final String imgUrl = hc.imageUrl();
			if (payable) {
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mousePressed(MouseEvent ev) {
						if (!selectedDiscards.remove(Integer.valueOf(hi)) && canAddDiscard[0]) selectedDiscards.add(hi);
						updateAll.run();
					}
					@Override public void mouseEntered(MouseEvent ev) { if (lbl.getIcon() != null) showZoomAt(imgUrl, lbl); }
					@Override public void mouseExited(MouseEvent ev)  { hideZoom(); }
				});
				discardLbls.add(lbl); discardIdxs.add(hi);
			} else {
				lbl.addMouseListener(new MouseAdapter() {
					@Override public void mouseEntered(MouseEvent ev) { if (lbl.getIcon() != null) showZoomAt(imgUrl, lbl); }
					@Override public void mouseExited(MouseEvent ev)  { hideZoom(); }
				});
			}
			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(imgUrl);
					return img == null ? null : new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
				}
				@Override protected void done() {
					try { ImageIcon ic = get(); if (ic != null) { lbl.setIcon(ic); lbl.setText(null); } }
					catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();
			dp.add(lbl);
		}
		centerPanel.add(discardHdr); centerPanel.add(dp);

		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		cancelBtn.addActionListener(ev -> dlg.dispose());
		confirmBtn.addActionListener(ev -> {
			dlg.dispose();
			executePriming(card, slotIdx, new ArrayList<>(selectedDiscards), new ArrayList<>(selectedBackups));
		});

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
		buttonPanel.add(confirmBtn); buttonPanel.add(cancelBtn);

		StringBuilder costDesc = new StringBuilder();
		boolean f = true;
		for (Map.Entry<String, Integer> en : costByElem.entrySet()) {
			if (!f) costDesc.append(" + ");
			costDesc.append(en.getValue()).append(" ").append(en.getKey()).append(" CP"); f = false;
		}
		if (genericNeeded > 0) { if (!f) costDesc.append(" + "); costDesc.append((int) genericNeeded).append(" any CP"); }
		JLabel titleLabel = new JLabel(
				"Priming cost for: " + card.name() + "  (" + (costDesc.length() > 0 ? costDesc : "free") + ")",
				SwingConstants.CENTER);
		titleLabel.setFont(new Font("Pixel NES", Font.PLAIN, 11));

		JPanel topPanel = new JPanel(new BorderLayout(0, 4));
		topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
		topPanel.add(titleLabel, BorderLayout.NORTH); topPanel.add(cpLabel, BorderLayout.CENTER);

		JPanel mainPanel = new JPanel(new BorderLayout(0, 4));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
		mainPanel.add(new JScrollPane(centerPanel), BorderLayout.CENTER);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);

		dlg.getContentPane().setLayout(new BorderLayout());
		dlg.getContentPane().add(topPanel, BorderLayout.NORTH);
		dlg.getContentPane().add(mainPanel, BorderLayout.CENTER);
		dlg.pack(); dlg.setLocationRelativeTo(frame); dlg.setVisible(true);
	}

	/**
	 * Pays the Priming cost, searches the main deck for the target card, and if
	 * found places it as the top card of the primed forward.  The deck is shuffled
	 * after the search regardless of whether the card was found.
	 */
	private void executePriming(CardData card, int slotIdx,
			List<Integer> discardIndices, List<Integer> backupDullIndices) {
		List<String> rawCost = card.primingCost();
		LinkedHashMap<String, Integer> costByElem = new LinkedHashMap<>();
		for (String e : rawCost) if (!e.isEmpty()) costByElem.merge(e, 1, Integer::sum);
		String[] elems = costByElem.keySet().toArray(String[]::new);

		// Pay cost
		for (int bi : backupDullIndices) {
			p1BackupStates[bi] = CardState.DULLED;
			animateDullBackup(bi, true);
			String cpElem = matchesAnyElement(p1BackupCards[bi], elems)
					? contributingElement(p1BackupCards[bi], elems) : (elems.length > 0 ? elems[0] : "");
			if (!cpElem.isEmpty()) gameState.addP1Cp(cpElem, 1);
		}
		discardIndices.sort(Collections.reverseOrder());
		for (int di : discardIndices) {
			CardData discarded = gameState.getP1Hand().get(di);
			String cpElem = matchesAnyElement(discarded, elems)
					? contributingElement(discarded, elems) : (elems.length > 0 ? elems[0] : "");
			if (!cpElem.isEmpty()) gameState.addP1Cp(cpElem, 2);
			gameState.breakFromHand(di);
		}
		for (String e : elems) { gameState.spendP1Cp(e, gameState.getP1CpForElement(e)); gameState.clearP1Cp(e); }

		// Search deck — find all versions of the target card
		String target = card.primingTarget();
		List<CardData> matches = gameState.findMatchingNamesInP1MainDeck(target);

		if (matches.isEmpty()) {
			shuffleP1MainDeck();
			logEntry("Priming: \"" + target + "\" not found in deck — no card placed");
			refreshP1HandLabel();
			refreshP1BreakLabel();
		} else if (matches.size() == 1) {
			gameState.removeFromP1MainDeck(matches.get(0));
			shuffleP1MainDeck();
			applyPrimedCard(matches.get(0), card, slotIdx);
			refreshP1HandLabel();
			refreshP1BreakLabel();
		} else {
			// Multiple printings found — let the player choose; shuffle and refresh happen inside the dialog
			showPrimingVersionSelectDialog(matches, card, slotIdx);
		}
	}

	/** Shuffles P1's main deck in-place and refreshes the deck label. */
	private void shuffleP1MainDeck() {
		List<CardData> list = new ArrayList<>(gameState.getP1MainDeck());
		Collections.shuffle(list);
		gameState.getP1MainDeck().clear();
		gameState.getP1MainDeck().addAll(list);
		refreshP1DeckLabel();
	}

	/** Places {@code chosen} as the primed top card on {@code slotIdx} and logs the action. */
	private void applyPrimedCard(CardData chosen, CardData primingCard, int slotIdx) {
		p1ForwardPrimedTop.set(slotIdx, chosen);
		logEntry("Primed: \"" + primingCard.name() + "\" topped with \"" + chosen.name() + "\"");
		refreshP1ForwardSlot(slotIdx);
	}

	/**
	 * Shows a modal dialog letting the player pick which version of the priming
	 * target to pull from the deck when multiple printings are present.
	 * Closing without a choice auto-selects the first match.
	 */
	private void showPrimingVersionSelectDialog(List<CardData> matches, CardData primingCard, int slotIdx) {
		JDialog dlg = new JDialog(frame,
				"Choose version: " + primingCard.primingTarget() + " (" + matches.size() + " found)", true);
		dlg.setResizable(false);
		dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

		// Tracks the player's selection; default to first match so closing = auto-pick
		CardData[] selection = {matches.get(0)};

		JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));

		for (CardData candidate : matches) {
			JPanel wrapper = new JPanel(new BorderLayout(0, 4));
			wrapper.setBackground(cardsPanel.getBackground());

			JLabel lbl = new JLabel("...", SwingConstants.CENTER);
			lbl.setPreferredSize(new Dimension(CARD_W, CARD_H));
			lbl.setMinimumSize(new Dimension(CARD_W, CARD_H));
			lbl.setOpaque(true);
			lbl.setBackground(Color.DARK_GRAY);
			lbl.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
			lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			lbl.addMouseListener(new MouseAdapter() {
				@Override public void mouseEntered(MouseEvent e) {
					if (lbl.getIcon() != null) showZoomAt(candidate.imageUrl(), lbl);
					lbl.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
				}
				@Override public void mouseExited(MouseEvent e) {
					hideZoom();
					lbl.setBorder(BorderFactory.createLineBorder(
							selection[0].equals(candidate) ? Color.YELLOW : Color.GRAY, 2));
				}
				@Override public void mousePressed(MouseEvent e) {
					selection[0] = candidate;
					dlg.dispose();
				}
			});

			new SwingWorker<ImageIcon, Void>() {
				@Override protected ImageIcon doInBackground() throws Exception {
					Image img = ImageCache.load(candidate.imageUrl());
					return img == null ? null
							: new ImageIcon(img.getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH));
				}
				@Override protected void done() {
					try { ImageIcon ic = get(); if (ic != null) { lbl.setIcon(ic); lbl.setText(null); } }
					catch (InterruptedException | ExecutionException ignored) {}
				}
			}.execute();

			JLabel nameLabel = new JLabel(candidate.name(), SwingConstants.CENTER);
			nameLabel.setFont(new Font("Pixel NES", Font.PLAIN, 9));
			nameLabel.setPreferredSize(new Dimension(CARD_W, 18));

			wrapper.add(lbl, BorderLayout.CENTER);
			wrapper.add(nameLabel, BorderLayout.SOUTH);
			cardsPanel.add(wrapper);
		}

		JLabel hint = new JLabel("Click a card to select it", SwingConstants.CENTER);
		hint.setFont(new Font("Pixel NES", Font.PLAIN, 9));

		dlg.getContentPane().setLayout(new BorderLayout(0, 6));
		dlg.getContentPane().add(cardsPanel, BorderLayout.CENTER);
		dlg.getContentPane().add(hint, BorderLayout.SOUTH);
		dlg.pack();
		dlg.setLocationRelativeTo(frame);
		dlg.setVisible(true); // blocks until a card is clicked (dlg.dispose())

		// Execution resumes here after dialog closes
		gameState.removeFromP1MainDeck(selection[0]);
		shuffleP1MainDeck();
		applyPrimedCard(selection[0], primingCard, slotIdx);
		refreshP1HandLabel();
		refreshP1BreakLabel();
	}

	private JPanel buildBackupZonePanel(JLabel[] labelStorage) {
		JPanel slotsPanel = new JPanel(new GridLayout(1, 5, 2, 0));
		slotsPanel.setOpaque(false);
		for (int i = 0; i < 5; i++) {
			JLabel slot = new JLabel();
			slot.setFont(new Font("Pixel NES", Font.PLAIN, 11));
			slot.setBorder(BorderFactory.createEmptyBorder());
			slot.setOpaque(false);
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
						if (gameActive && !gameState.getP1MainDeck().isEmpty() && AppSettings.isDebugMode()) {
							JMenuItem dmgItem = new JMenuItem("Debug: Take 1 Damage");
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
			// P2: mirrored damage slots — card on right, letter centred, EX in upper-left
			String[] letters = { "D", "A", "M", "A", "G", "E", playerLabel };
			slotsPanel = new JPanel(new GridLayout(7, 1, 2, 2)) {
				@Override public void setBackground(Color c) { /* paintComponent owns background */ }
				@Override protected void paintComponent(Graphics g) {
					g.setColor(Color.DARK_GRAY);
					g.fillRect(0, 0, getWidth(), getHeight());
				}
			};
			slotsPanel.setOpaque(true);
			for (int i = 0; i < letters.length; i++) {
				final String letter = letters[i];
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
								int dx = getWidth() - dw;
								g2.drawImage(img, dx, dy, dx + dw, dy + dh, 0, 0, iw, ih, null);
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
							int exY = exFm.getAscent() + 2;
							g2.setColor(Color.BLACK);
							g2.drawString("EX", 4, exY + 1);
							g2.setColor(Color.YELLOW);
							g2.drawString("EX", 3, exY);
						}
						g2.dispose();
					}
				};
				slot.setOpaque(true);
				slot.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80), 1));
				slotsPanel.add(slot);
				p2DamageSlots[i] = slot;
			}
			slotsPanel.addMouseListener(new MouseAdapter() {
				@Override public void mousePressed(MouseEvent e) {
					if (!gameState.getP2DamageZone().isEmpty()) showP2DamageZoneDialog();
				}
			});
			p2DamageSlotPanel = slotsPanel;
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

	// -------------------------------------------------------------------------
	// Crystal display — elongated upright hexagon badge with ": N" label
	// -------------------------------------------------------------------------

	private static final int CRYSTAL_H = 36;  // component height

	/**
	 * A compact custom component that renders an elongated upright hexagon
	 * (point-top, flat left/right sides) in crystal-blue and draws ": N"
	 * centred inside it, where N is the current Crystal count.
	 */
	private class CrystalDisplay extends javax.swing.JComponent {
		private int     count;
		/** Once true (count has been > 0 this game), the display stays visible. */
		private boolean hasBeenNonZero = false;
		/** Index into ElementColor.values(); starts at ICE to match the original blue. */
		private int     colorIndex     = ElementColor.ICE.ordinal();

		CrystalDisplay(int initial) {
			this.count = initial;
			setPreferredSize(new java.awt.Dimension(CARD_W, CRYSTAL_H));
			setMinimumSize(new java.awt.Dimension(CARD_W, CRYSTAL_H));
			setMaximumSize(new java.awt.Dimension(CARD_W, CRYSTAL_H));
			setOpaque(false);
			setToolTipText("Crystals — click to change element colour");
			addMouseListener(new java.awt.event.MouseAdapter() {
				@Override public void mousePressed(java.awt.event.MouseEvent e) {
					colorIndex = (colorIndex + 1) % ElementColor.values().length;
					repaint();
				}
			});
			updateVisibility();
		}

		/** Updates count, latches persistence when count first exceeds zero, then repaints. */
		void setCount(int n) {
			this.count = n;
			if (n > 0) hasBeenNonZero = true;
			updateVisibility();
			repaint();
		}

		/** Shown in debug mode or once the count has ever been > 0 this game. */
		void updateVisibility() {
			setVisible(AppSettings.isDebugMode() || hasBeenNonZero);
		}

		/** Fully resets for a new game: count, persistence flag, and visibility. */
		void hardReset() {
			count          = 0;
			hasBeenNonZero = false;
			updateVisibility();
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g0) {
			super.paintComponent(g0);
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			int cw = getWidth(), ch = getHeight();

			// Elongated upright hexagon: 22 px wide, 32 px tall, centred
			int hxW = 22, hxH = 32;
			int hxX = (cw - hxW) / 2;
			int hxY = (ch - hxH) / 2;

			int shoulder = hxH / 5;
			int[] xp = { hxX + hxW/2, hxX + hxW, hxX + hxW, hxX + hxW/2, hxX,                   hxX           };
			int[] yp = { hxY,          hxY + shoulder, hxY + hxH - shoulder, hxY + hxH, hxY + hxH - shoulder, hxY + shoulder };

			Color base = ElementColor.values()[colorIndex].color;
			g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 210));
			g.fillPolygon(xp, yp, 6);
			g.setColor(base.darker());
			g.setStroke(new BasicStroke(1.5f));
			g.drawPolygon(xp, yp, 6);

			// Count number centred inside the hexagon (no colon)
			g.setFont(new Font("Pixel NES", Font.PLAIN, 10));
			g.setColor(Color.WHITE);
			String text = String.valueOf(count);
			FontMetrics fm = g.getFontMetrics();
			int tx = (cw - fm.stringWidth(text)) / 2;
			int ty = hxY + hxH / 2 + fm.getAscent() / 2 - 1;
			g.drawString(text, tx, ty);

			g.dispose();
		}
	}

	/** Reads current crystal counts from game state and repaints both badges. */
	private void refreshCrystalDisplays() {
		if (p1CrystalDisplay != null) p1CrystalDisplay.setCount(gameState.getP1Crystals());
		if (p2CrystalDisplay != null) p2CrystalDisplay.setCount(gameState.getP2Crystals());
	}

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

	// -------------------------------------------------------------------------
	// P2 rendering helpers
	// -------------------------------------------------------------------------

	private boolean p2HasAvailableBackupSlot() {
		for (int i = 0; i < p2BackupCards.length; i++) {
			if (p2BackupCards[i] == null) return true;
		}
		return false;
	}

	private void placeP2CardInForwardZone(CardData card) {
		if (p2ForwardPanel == null) return;
		int idx = p2ForwardLabels.size();

		JLabel lbl = new JLabel("", SwingConstants.CENTER);
		lbl.setPreferredSize(new Dimension(CARD_H, CARD_H));
		lbl.setMinimumSize(new Dimension(CARD_H, CARD_H));
		lbl.setOpaque(false);
		lbl.setFont(new Font("Pixel NES", Font.PLAIN, 11));
		lbl.setBorder(BorderFactory.createEmptyBorder());
		lbl.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) {
				if (lbl.getIcon() != null) showZoomAt(p2ForwardUrls.get(idx), lbl);
			}
			@Override public void mouseExited(MouseEvent e) { hideZoom(); }
		});

		p2ForwardUrls.add(card.imageUrl());
		p2ForwardCards.add(card);
		p2ForwardStates.add(CardState.NORMAL);
		p2ForwardPlayedOnTurn.add(gameState.getTurnNumber());
		p2ForwardDamage.add(0);
		p2ForwardLabels.add(lbl);

		p2ForwardPanel.add(lbl);
		p2ForwardPanel.revalidate();
		p2ForwardPanel.repaint();

		refreshP2ForwardSlot(idx);
	}

	private void placeP2CardInFirstBackupSlot(CardData card) {
		for (int i = 0; i < p2BackupCards.length; i++) {
			if (p2BackupCards[i] != null) continue;
			p2BackupUrls[i]   = card.imageUrl();
			p2BackupCards[i]  = card;
			p2BackupStates[i] = CardState.DULLED;
			refreshP2BackupSlot(i);
			return;
		}
	}

	private void refreshP2BackupSlot(int idx) {
		String url    = p2BackupUrls[idx];
		JLabel slot   = p2BackupLabels[idx];
		CardState state = p2BackupStates[idx];
		if (url == null || slot == null) return;
		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				if (raw == null) return null;
				return new ImageIcon(renderBackupCard(toARGB(raw, CARD_W, CARD_H), state));
			}
			@Override protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) { slot.setIcon(icon); slot.setText(null); }
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	private void refreshP2ForwardSlot(int idx) {
		String url      = p2ForwardUrls.get(idx);
		CardState state = p2ForwardStates.get(idx);
		JLabel slot     = p2ForwardLabels.get(idx);
		if (url == null) return;
		int damage = p2ForwardDamage.get(idx);
		int power  = p2ForwardCards.get(idx).power();
		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				if (raw == null) return null;
				BufferedImage canvas = renderBackupCard(toARGB(raw, CARD_W, CARD_H), state);
				if (damage > 0 && power > 0) renderDamageOverlay(canvas, power - damage);
				return new ImageIcon(canvas);
			}
			@Override protected void done() {
				try {
					ImageIcon icon = get();
					if (icon != null) { slot.setIcon(icon); slot.setText(null); }
				} catch (InterruptedException | ExecutionException ignored) {}
			}
		}.execute();
	}

	private void refreshAllP2ForwardSlots() {
		for (int i = 0; i < p2ForwardLabels.size(); i++) refreshP2ForwardSlot(i);
	}

	// -------------------------------------------------------------------------
	// Computer player (P2 AI)
	// -------------------------------------------------------------------------

	private class ComputerPlayer {
		private static final int PAUSE_MS = 500;

		/** Schedules {@code r} to run after {@link #PAUSE_MS} ms on the EDT. */
		private void step(Runnable r) {
			javax.swing.Timer t = new javax.swing.Timer(PAUSE_MS, e -> {
				if (!gameState.isP1GameOver()) r.run();
			});
			t.setRepeats(false);
			t.start();
		}

		/** Entry point: called when P2's ACTIVE phase begins. */
		void runTurn() {
			step(this::doActivePhase);
		}

		// ── Active Phase ─────────────────────────────────────────────────────

		private void doActivePhase() {
			int activated = 0, thawed = 0;
			for (int i = 0; i < p2BackupStates.length; i++) {
				if (p2BackupCards[i] == null) continue;
				switch (p2BackupStates[i]) {
					case FROZEN -> { p2BackupStates[i] = CardState.DULLED;  refreshP2BackupSlot(i); thawed++;    }
					case DULLED -> { p2BackupStates[i] = CardState.NORMAL;  refreshP2BackupSlot(i); activated++; }
					default -> {}
				}
			}
			for (int i = 0; i < p2ForwardStates.size(); i++) {
				p2ForwardDamage.set(i, 0);
				switch (p2ForwardStates.get(i)) {
					case FROZEN -> {
						p2ForwardStates.set(i, CardState.DULLED);
						refreshP2ForwardSlot(i);
						thawed++;
					}
					case DULLED, BRAVE_ATTACKED -> {
						p2ForwardStates.set(i, CardState.NORMAL);
						refreshP2ForwardSlot(i);
						activated++;
					}
					default -> refreshP2ForwardSlot(i);
				}
			}
			StringBuilder msg = new StringBuilder("Turn " + gameState.getTurnNumber() + " — P2 Active Phase");
			if (activated > 0) msg.append(" (").append(activated).append(" activated");
			if (thawed > 0)    msg.append(activated > 0 ? ", " : " (").append(thawed).append(" thawed");
			if (activated > 0 || thawed > 0) msg.append(")");
			logEntry(msg.toString());

			gameState.advancePhase(); // ACTIVE → DRAW
			step(this::doDrawPhase);
		}

		// ── Draw Phase ───────────────────────────────────────────────────────

		private void doDrawPhase() {
			int drawCount = gameState.getTurnNumber() == 1 ? 1 : 2;
			List<CardData> drawn = gameState.drawP2ToHand(drawCount);
			refreshP2DeckLabel();
			refreshP2HandCountLabel();
			if (drawn.size() < drawCount) {
				triggerGameOver("P2 milled out — You Win!");
				return;
			}
			logEntry("[P2] Draw Phase — Drew " + drawn.size() + " card(s) (hand: " + gameState.getP2Hand().size() + ")");
			gameState.advancePhase(); // DRAW → MAIN_1
			logEntry("[P2] Main Phase 1");
			step(() -> doMainPhase(() -> {
				gameState.advancePhase(); // MAIN_1 → ATTACK
				boolean canAttack = false;
				for (int i = 0; i < p2ForwardStates.size(); i++) {
					if (p2ForwardCanAttack(i)) { canAttack = true; break; }
				}
				if (!canAttack) {
					logEntry("[P2] Attack Phase — No attackers, skipping");
					gameState.advancePhase(); // ATTACK → MAIN_2
					logEntry("[P2] Main Phase 2");
					step(() -> doMainPhase(this::doEndPhase));
				} else {
					logEntry("[P2] Attack Phase");
					refreshAllP2ForwardSlots();
					step(() -> doAttackPhase(() -> {
						gameState.advancePhase(); // ATTACK → MAIN_2
						logEntry("[P2] Main Phase 2");
						step(() -> doMainPhase(this::doEndPhase));
					}));
				}
			}));
		}

		// ── Main Phase (shared for Main 1 and Main 2) ────────────────────────

		private void doMainPhase(Runnable onDone) {
			if (gameState.isP1GameOver()) return;

			// Try LB plays first
			int[] lbPlan = findLbPlayPlan();
			if (lbPlan != null) {
				int castIdx = lbPlan[0];
				CardData card = gameState.getP2LbDeck().get(castIdx);
				p2SpentLbIndices.add(castIdx);
				for (int i = 1; i < lbPlan.length; i++) p2SpentLbIndices.add(lbPlan[i]);
				String element = card.elements()[0];
				gameState.spendP2Cp(element, Math.min(card.cost(), gameState.getP2CpForElement(element)));
				refreshP2LimitButton();
				logEntry("[P2] Plays LB \"" + card.name() + "\"");
				if (card.isForward())     placeP2CardInForwardZone(card);
				else if (card.isBackup()) placeP2CardInFirstBackupSlot(card);
				step(() -> doMainPhase(onDone));
				return;
			}

			int[] plan = findPlayPlan();
			if (plan == null) { onDone.run(); return; }

			int cardIdx = plan[0];
			List<Integer> discards = new ArrayList<>();
			for (int i = 1; i < plan.length; i++) discards.add(plan[i]);
			discards.sort(Collections.reverseOrder());

			// Adjust card index after descending discards
			int adjustedIdx = cardIdx;
			for (int di : discards) {
				if (di < cardIdx) adjustedIdx--;
			}

			for (int di : discards) {
				CardData d = gameState.discardP2FromHand(di);
				if (d != null) logEntry("[P2] Discards " + d.name() + " for CP");
			}
			refreshP2BreakLabel();

			CardData toPlay = gameState.removeP2FromHand(adjustedIdx);
			refreshP2HandCountLabel();
			if (toPlay != null) {
				String element = toPlay.elements()[0];
				gameState.spendP2Cp(element, Math.min(toPlay.cost(), gameState.getP2CpForElement(element)));
				logEntry("[P2] Plays " + toPlay.name());
				if (toPlay.isForward())     placeP2CardInForwardZone(toPlay);
				else if (toPlay.isBackup()) placeP2CardInFirstBackupSlot(toPlay);
			}
			step(() -> doMainPhase(onDone));
		}

		// ── Attack Phase ─────────────────────────────────────────────────────

		private void doAttackPhase(Runnable onDone) {
			if (gameState.isP1GameOver()) return;
			for (int i = 0; i < p2ForwardStates.size(); i++) {
				if (!p2ForwardCanAttack(i)) continue;
				CardData attacker = p2ForwardCards.get(i);
				logEntry("[P2] " + attacker.name() + " attacks!");
				if (attacker.hasTrait(CardData.Trait.BRAVE)) {
					p2ForwardStates.set(i, CardState.BRAVE_ATTACKED);
				} else {
					p2ForwardStates.set(i, CardState.DULLED);
				}
				refreshP2ForwardSlot(i);
				final int fi = i;
				p1ChooseBlockerDialog(attacker, fi, () -> {
					if (!gameState.isP1GameOver()) step(() -> doAttackPhase(onDone));
				});
				return;
			}
			onDone.run();
		}

		// ── End Phase ────────────────────────────────────────────────────────

		private void doEndPhase() {
			List<CardData> hand = gameState.getP2Hand();
			while (hand.size() > 5) {
				int idx = pickWorstHandCard(hand);
				CardData d = gameState.discardP2FromHand(idx);
				if (d != null) logEntry("[P2] End Phase — discards " + d.name());
			}
			refreshP2BreakLabel();
			refreshP2HandCountLabel();
			for (int i = 0; i < p2ForwardDamage.size(); i++) p2ForwardDamage.set(i, 0);
			for (int i = 0; i < p2ForwardCards.size(); i++) refreshP2ForwardSlot(i);
			for (int i = 0; i < p1ForwardDamage.size(); i++) p1ForwardDamage.set(i, 0);
			for (int i = 0; i < p1ForwardCards.size(); i++) refreshP1ForwardSlot(i);
			gameState.advancePhase(); // MAIN_2 → END
			logEntry("[P2] End Phase");
			gameState.advancePhase(); // END → ACTIVE (switches to P1, increments turn)
			step(this::startP1Turn);  // startP1Turn expects phase == ACTIVE
		}

		// ── P1 turn start (Active + Draw, then hand control back to player) ──

		private void startP1Turn() {
			int activated = 0, thawed = 0;
			for (int i = 0; i < p1BackupStates.length; i++) {
				if (p1BackupStates[i] == CardState.FROZEN) {
					p1BackupStates[i] = CardState.DULLED;
					refreshP1BackupSlot(i);
					thawed++;
				} else if (p1BackupStates[i] == CardState.DULLED) {
					p1BackupStates[i] = CardState.NORMAL;
					refreshP1BackupSlot(i);
					activated++;
				}
			}
			for (int i = 0; i < p1ForwardStates.size(); i++) {
				CardState fs = p1ForwardStates.get(i);
				if (fs == CardState.FROZEN) {
					p1ForwardStates.set(i, CardState.DULLED);
					refreshP1ForwardSlot(i);
					thawed++;
				} else if (fs == CardState.DULLED || fs == CardState.BRAVE_ATTACKED) {
					p1ForwardStates.set(i, CardState.NORMAL);
					refreshP1ForwardSlot(i);
					activated++;
				}
			}
			StringBuilder msg = new StringBuilder("Turn " + gameState.getTurnNumber() + " — Active Phase");
			if (activated > 0) msg.append(" (").append(activated).append(" activated");
			if (thawed > 0)    msg.append(activated > 0 ? ", " : " (").append(thawed).append(" thawed");
			if (activated > 0 || thawed > 0) msg.append(")");
			logEntry(msg.toString());

			gameState.advancePhase(); // ACTIVE → DRAW

			List<CardData> drawn = gameState.drawToHand(2);
			refreshP1HandLabel();
			refreshP1DeckLabel();
			if (drawn.size() < 2) {
				triggerGameOver("Milled Out - You Lose!");
				return;
			}
			logEntry("Draw Phase — Drew " + drawn.size() + " card(s)");
			gameState.advancePhase(); // DRAW → MAIN_1
			logEntry("Main Phase 1");
			processWarpCounters();
			nextPhaseButton.setEnabled(true);
		}

		// ── Helpers ──────────────────────────────────────────────────────────

		private boolean p2ForwardCanAttack(int idx) {
			return p2ForwardStates.get(idx) == CardState.NORMAL
				&& (p2ForwardCards.get(idx).hasTrait(CardData.Trait.HASTE)
					|| p2ForwardPlayedOnTurn.get(idx) != gameState.getTurnNumber());
		}

		/** Returns the index of the least-valuable card in {@code hand} for end-phase discard. */
		private int pickWorstHandCard(List<CardData> hand) {
			int worstIdx = 0, worstScore = Integer.MAX_VALUE;
			for (int i = 0; i < hand.size(); i++) {
				CardData c = hand.get(i);
				// Prefer to keep forwards and higher-cost cards; sacrifice backups and cheap cards first
				int score = c.cost() + (c.isForward() ? 10 : 0);
				if (score < worstScore) { worstScore = score; worstIdx = i; }
			}
			return worstIdx;
		}

		/**
		 * Finds the best card P2 can play from hand, along with the minimum
		 * discards needed to afford it.
		 *
		 * @return {@code int[]} where {@code [0]} is the hand index of the card to
		 *         play and {@code [1..n]} are hand indices to discard first (sorted
		 *         ascending), or {@code null} if nothing is playable.
		 */
		/** Returns [castIdx, paymentIdx…] if any unspent LB card is affordable, else null. */
		private int[] findLbPlayPlan() {
			List<CardData> lbDeck = gameState.getP2LbDeck();
			boolean p2HasLD = hasLightOrDarkOnField(false);
			for (int i = 0; i < lbDeck.size(); i++) {
				if (p2SpentLbIndices.contains(i)) continue;
				CardData card = lbDeck.get(i);
				if (card.isSummon()) continue; // skip summons — no simple board placement
				if (!card.multicard() && p2HasCharacterNameOnField(card.name())) continue;
				if (card.isLightOrDark() && p2HasLD) continue;
				if (card.isBackup() && !p2HasAvailableBackupSlot()) continue;
				// Count unspent LB cards available as payment (excluding this card)
				List<Integer> available = new ArrayList<>();
				for (int j = 0; j < lbDeck.size(); j++) {
					if (j != i && !p2SpentLbIndices.contains(j)) available.add(j);
				}
				if (available.size() < card.lbCost()) continue;
				// Check CP
				String element = card.elements()[0];
				if (gameState.getP2CpForElement(element) < card.cost()) continue;
				// Build result: [castIdx, payment…]
				int[] result = new int[1 + card.lbCost()];
				result[0] = i;
				for (int k = 0; k < card.lbCost(); k++) result[k + 1] = available.get(k);
				return result;
			}
			return null;
		}

		private int[] findPlayPlan() {
			List<CardData> hand = gameState.getP2Hand();
			if (hand.isEmpty()) return null;

			// Candidates: forwards (highest cost first), then backups (highest cost first)
			// Skip non-Multicard characters whose name is already on P2's field or backups.
			List<Integer> candidates = new ArrayList<>();
			boolean p2HasLD = hasLightOrDarkOnField(false);
			for (int i = 0; i < hand.size(); i++) {
				CardData c = hand.get(i);
				if (!c.isForward()) continue;
				if (!c.multicard() && p2HasCharacterNameOnField(c.name())) continue;
				if (c.isLightOrDark() && p2HasLD) continue;
				candidates.add(i);
			}
			candidates.sort((a, b) -> hand.get(b).cost() - hand.get(a).cost());
			List<Integer> backupCands = new ArrayList<>();
			for (int i = 0; i < hand.size(); i++) {
				CardData c = hand.get(i);
				if (!c.isBackup() || !p2HasAvailableBackupSlot()) continue;
				if (!c.multicard() && p2HasCharacterNameOnField(c.name())) continue;
				if (c.isLightOrDark() && p2HasLD) continue;
				backupCands.add(i);
			}
			backupCands.sort((a, b) -> hand.get(b).cost() - hand.get(a).cost());
			candidates.addAll(backupCands);

			for (int cardIdx : candidates) {
				CardData card    = hand.get(cardIdx);
				String element   = card.elements()[0];
				int haveCp       = gameState.getP2CpForElement(element);
				int needed       = Math.max(0, card.cost() - haveCp);

				if (needed == 0) return new int[]{ cardIdx };

				// Cheapest non-Light/Dark matching-element cards available as discards
				List<Integer> discardable = new ArrayList<>();
				for (int i = 0; i < hand.size(); i++) {
					if (i == cardIdx) continue;
					CardData c = hand.get(i);
					if (!c.isLightOrDark() && c.containsElement(element)) discardable.add(i);
				}
				int discardCount = (needed + 1) / 2; // ceil(needed / 2)
				if (discardable.size() >= discardCount) {
					discardable.sort((a, b) -> hand.get(a).cost() - hand.get(b).cost());
					int[] result = new int[1 + discardCount];
					result[0] = cardIdx;
					for (int i = 0; i < discardCount; i++) result[i + 1] = discardable.get(i);
					return result;
				}
			}
			return null;
		}
	}

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

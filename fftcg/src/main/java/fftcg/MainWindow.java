package fftcg;

import fftcg.menu.FileMenu;
import fftcg.menu.HelpMenu;
import fftcg.menu.MultiplayerMenu;
import fftcg.net.ActionType;
import fftcg.net.GameConnection;

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
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
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
	private JLabel p2DeckLabel;
	private JLabel p1LimitLabel;
	private JPanel handPanel;
	private JLabel p1BreakLabel;
	private GrayscaleLabel p1RemoveLabel;
	private GrayscaleLabel p2RemoveLabel;
	// Game event log
	private JTextArea gameLog;
	// Chat bar (enabled only when connected to multiplayer)
	private JTextField chatInput;
	private JButton    chatSendBtn;
	// Multiplayer menu reference (to access active connection)
	private MultiplayerMenu multiplayerMenu;
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
	private static final int STATE_NORMAL        = 0;
	private static final int STATE_DULLED        = 1;
	private static final int STATE_FROZEN        = 2;
	private static final int STATE_BRAVE_ATTACKED = 3; // Brave forward: attacked this turn but stays active

	// --- Game state ---
	private final GameState gameState   = new GameState();
	// UI-only state (not owned by GameState)
	private JLabel[]    p1BackupLabels = new JLabel[5];
	private String[]    p1BackupUrls   = new String[5];
	private CardData[]  p1BackupCards  = new CardData[5];
	private int[]       p1BackupStates = new int[5];

	private final List<JLabel>   p1ForwardLabels      = new ArrayList<>();
	private final List<String>   p1ForwardUrls;
	private final List<CardData> p1ForwardCards       = new ArrayList<>();
	private final List<Integer>  p1ForwardStates      = new ArrayList<>();
	private final List<Integer>  p1ForwardPlayedOnTurn = new ArrayList<>();
	private JPanel p1ForwardPanel;

	private final List<JLabel>   p1MonsterLabels      = new ArrayList<>();
	private final List<String>   p1MonsterUrls        = new ArrayList<>();
	private final List<CardData> p1MonsterCards       = new ArrayList<>();
	private final List<Integer>  p1MonsterStates      = new ArrayList<>();
	private final List<Integer>  p1MonsterPlayedOnTurn = new ArrayList<>();
	private JPanel p1MonsterPanel;

	private int      p2DamageCount = 0;
	private JPanel[] p2DamageSlots = new JPanel[7];

	private int             p1LbIndex   = 0;
	private final Set<Integer> spentLbIndices = new HashSet<>();

	// Damage zone UI
	private JPanel   p1DamageSlotPanel;
	private JPanel[] p1DamageSlots = new JPanel[7];
	private JPanel   p2DamageSlotPanel;

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

		p2DeckLabel = new JLabel("DECK");
		p2DeckLabel.setFont(new Font("Pixel NES", Font.PLAIN, 18));
		p2DeckLabel.setToolTipText("Player 2 Deck");
		p2DeckLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p2DeckLabel.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
		p2DeckLabel.setBackground(Color.DARK_GRAY);
		p2DeckLabel.setForeground(Color.WHITE);
		p2DeckLabel.setOpaque(true);

		JPanel p2CornerPanel = new JPanel(new GridLayout(2, 2));
		p2CornerPanel.add(lblRemove_1);
		p2CornerPanel.add(break1_1);
		p2CornerPanel.add(lblLimit_1);
		p2CornerPanel.add(p2DeckLabel);

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

		JPanel p2ZonesPanel = new JPanel(new GridBagLayout());
		{
			GridBagConstraints z = new GridBagConstraints();
			z.gridy = 0; z.fill = GridBagConstraints.NONE; z.anchor = GridBagConstraints.NORTH; z.weightx = 0;
			z.gridx = 0; p2ZonesPanel.add(p2CornerPanel, z);
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

		// ── Chat bar ─────────────────────────────────────────────────────────
		chatInput = new JTextField();
		chatInput.setFont(loadLatinia(11));
		chatInput.setEnabled(false);
		chatInput.setToolTipText("Connect to multiplayer to chat");

		chatSendBtn = new JButton("Send");
		chatSendBtn.setFont(loadLatinia(11));
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
					CardData cd = new CardData(card.imageUrl(), card.name(), card.element(),
							card.cost(), card.type(), card.isLb(), card.lbCost(), card.exBurst(),
							CardData.parseTraits(card.textEn()));
					if (card.isLb()) lb.add(cd);
					else             main.add(cd);
				}
				gameState.initializeDeck(main, lb);
				refreshP1DeckLabel();
				refreshP1LimitLabel();
				drawOpeningHand();

				if (p2Cards != null) {
					List<CardData> p2Main = new ArrayList<>();
					for (DeckCardDetail card : p2Cards) {
						if (!card.isLb()) {
							p2Main.add(new CardData(card.imageUrl(), card.name(), card.element(),
									card.cost(), card.type(), card.isLb(), card.lbCost(), card.exBurst(),
									CardData.parseTraits(card.textEn())));
						}
					}
					gameState.initializeP2Deck(p2Main);
					refreshP2DeckLabel();
					logEntry("P2 deck: " + p2DeckName);
				}
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
			gameState.startFirstTurn();
			logEntry("Turn 1 — Active Phase");
			if (nextPhaseButton != null) nextPhaseButton.setEnabled(true);
			refreshP1HandLabel();
			onNextPhase();
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
				refreshAllForwardSlots();
				if (!hasAttackableForward() && !hasBackAttackInHand()) {
					logEntry("No attackers available — skipping to Main Phase 2");
					onNextPhase();
				}
				break;

			case ATTACK:
				gameState.advancePhase();   // ATTACK → MAIN_2
				refreshAllForwardSlots();
				logEntry("Main Phase 2");
				break;

			case MAIN_2:
				gameState.advancePhase();   // MAIN_2 → END
				logEntry("End Phase");
				showEndPhaseDiscardDialog();
				onNextPhase();             // END → ACTIVE (auto-advance)
				break;

			case END: {
				// Advance (turn number increments inside advancePhase)
				gameState.advancePhase();   // END → ACTIVE

				// Execute Active Phase: dull → normal, frozen → dull
				int activated = 0, thawed = 0;
				for (int i = 0; i < p1BackupStates.length; i++) {
					if (p1BackupStates[i] == STATE_FROZEN) {
						p1BackupStates[i] = STATE_DULLED;
						refreshP1BackupSlot(i);
						thawed++;
					} else if (p1BackupStates[i] == STATE_DULLED) {
						p1BackupStates[i] = STATE_NORMAL;
						refreshP1BackupSlot(i);
						activated++;
					}
				}
				for (int i = 0; i < p1ForwardStates.size(); i++) {
					int fs = p1ForwardStates.get(i);
					if (fs == STATE_FROZEN) {
						p1ForwardStates.set(i, STATE_DULLED);
						refreshP1ForwardSlot(i);
						thawed++;
					} else if (fs == STATE_DULLED || fs == STATE_BRAVE_ATTACKED) {
						p1ForwardStates.set(i, STATE_NORMAL);
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
				// No choices during Active Phase — advance automatically
				onNextPhase();
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
			p1BackupStates[i] = 0;
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
		playItem.setEnabled(isMainPhase && canAffordCard(card, handIdx)
				&& (!card.isBackup() || hasAvailableBackupSlot()));
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
	/** Returns the first element of {@code source} that matches one of {@code playedElems}. */
	private String contributingElement(CardData source, String[] playedElems) {
		for (String pe : playedElems)
			if (source.containsElement(pe)) return pe;
		return playedElems[0];
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
				if (p1BackupCards[i] != null && p1BackupStates[i] == STATE_NORMAL)
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
			if (p1BackupCards[i] != null && p1BackupStates[i] == STATE_NORMAL) {
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

		// For L/D cards any element pays, so bank the total across all elements in one pool.
		// For other cards track per-element as before.
		Map<String, Integer> bankCpByElem = new LinkedHashMap<>();
		if (isLD) {
			int total = gameState.getP1CpByElement().values().stream().mapToInt(Integer::intValue).sum();
			bankCpByElem.put(elem, total);
		} else {
			for (String e : elems) bankCpByElem.put(e, gameState.getP1CpForElement(e));
		}

		List<Integer> selectedBackups  = new ArrayList<>();
		List<Integer> selectedDiscards = new ArrayList<>();

		// L/D cards: any undulled backup is eligible. Others: must match an element.
		List<Integer> eligibleBackupSlots = new ArrayList<>();
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] != null && p1BackupStates[i] == STATE_NORMAL
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
			p1BackupStates[bi] = STATE_DULLED;
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

		Map<String, Integer> bankCpByElem = new LinkedHashMap<>();
		if (isLD) {
			int total = gameState.getP1CpByElement().values().stream().mapToInt(Integer::intValue).sum();
			bankCpByElem.put(elem, total);
		} else {
			for (String e : elems) bankCpByElem.put(e, gameState.getP1CpForElement(e));
		}

		List<Integer> selectedBackups  = new ArrayList<>();
		List<Integer> selectedDiscards = new ArrayList<>();

		List<Integer> eligibleBackupSlots = new ArrayList<>();
		for (int i = 0; i < p1BackupCards.length; i++) {
			if (p1BackupCards[i] != null && p1BackupStates[i] == STATE_NORMAL
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
			p1BackupStates[bi] = STATE_DULLED;
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
			p1BackupUrls[i]   = card.imageUrl();
			p1BackupCards[i]  = card;
			p1BackupStates[i] = STATE_DULLED;
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

					double startAngle = freezing ? (prevState == STATE_DULLED ? Math.PI / 2 : 0.0) : Math.PI;
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

					double startAngle = freezing ? (prevState == STATE_DULLED ? Math.PI / 2 : 0.0) : Math.PI;
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
		return renderBackupCard(card, state, false);
	}

	private static BufferedImage renderBackupCard(BufferedImage card, int state, boolean highlight) {
		BufferedImage canvas = new BufferedImage(CARD_H, CARD_H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		switch (state) {
			case STATE_DULLED -> {
				BufferedImage rotated = rotateCW90(card);          // now CARD_H × CARD_W
				g.drawImage(rotated, 0, CARD_H - CARD_W, null);   // pinned to bottom-left
			}
			case STATE_FROZEN -> {
				BufferedImage flipped = rotate180(card);
				g.drawImage(applyBlueTint(flipped), 0, 0, null);  // pinned to top-left
			}
			default -> g.drawImage(card, 0, 0, null);             // pinned to top-left
		}
		if (highlight) {
			g.setColor(new Color(0, 220, 0));
			g.setStroke(new BasicStroke(3f));
			g.drawRect(1, 1, CARD_W - 3, CARD_H - 3);
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
			boolean dulling = p1BackupStates[idx] != STATE_DULLED;
			p1BackupStates[idx] = dulling ? STATE_DULLED : STATE_NORMAL;
			animateDullBackup(idx, dulling);
		});
		menu.add(dullItem);

		JMenuItem freezeItem = new JMenuItem("Debug: Freeze");
		freezeItem.addActionListener(ae -> {
			boolean freezing = p1BackupStates[idx] != STATE_FROZEN;
			int prevState = p1BackupStates[idx];
			p1BackupStates[idx] = freezing ? STATE_FROZEN : STATE_NORMAL;
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
				if (lbl.getIcon() != null) showForwardContextMenu(idx, lbl, e);
			}
			@Override public void mouseEntered(MouseEvent e) {
				if (lbl.getIcon() != null) showZoomAt(p1ForwardUrls.get(idx), lbl);
			}
			@Override public void mouseExited(MouseEvent e) { hideZoom(); }
		});

		p1ForwardUrls.add(card.imageUrl());
		p1ForwardCards.add(card);
		p1ForwardStates.add(STATE_NORMAL);
		p1ForwardPlayedOnTurn.add(gameState.getTurnNumber());
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
		p1MonsterStates.add(STATE_NORMAL);
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
		int    state = p1MonsterStates.get(idx);
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

		if (AppSettings.isDebugMode()) {
			JMenuItem dullItem = new JMenuItem("Debug: Dull");
			dullItem.addActionListener(ae -> {
				p1MonsterStates.set(idx,
						p1MonsterStates.get(idx) == STATE_DULLED ? STATE_NORMAL : STATE_DULLED);
				refreshP1MonsterSlot(idx);
			});
			menu.add(dullItem);

			JMenuItem freezeItem = new JMenuItem("Debug: Freeze");
			freezeItem.addActionListener(ae -> {
				p1MonsterStates.set(idx,
						p1MonsterStates.get(idx) == STATE_FROZEN ? STATE_NORMAL : STATE_FROZEN);
				refreshP1MonsterSlot(idx);
			});
			menu.add(freezeItem);
		}

		if (menu.getComponentCount() > 0) menu.show(slot, e.getX(), e.getY());
	}

	/** Reloads and re-renders a single P1 forward slot using its stored URL and state. */
	private void refreshP1ForwardSlot(int idx) {
		String url   = p1ForwardUrls.get(idx);
		int    state = p1ForwardStates.get(idx);
		JLabel slot  = p1ForwardLabels.get(idx);
		if (url == null) return;
		boolean hasHaste  = p1ForwardCards.get(idx).hasTrait(CardData.Trait.HASTE);
		boolean canAttack = gameState.getCurrentPhase() == GameState.GamePhase.ATTACK
				&& state == STATE_NORMAL
				&& (hasHaste || p1ForwardPlayedOnTurn.get(idx) != gameState.getTurnNumber());
		new SwingWorker<ImageIcon, Void>() {
			@Override protected ImageIcon doInBackground() throws Exception {
				Image raw = ImageCache.load(url);
				if (raw == null) return null;
				BufferedImage card = toARGB(raw, CARD_W, CARD_H);
				return new ImageIcon(renderBackupCard(card, state, canAttack));
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

	private boolean hasBackAttackInHand() {
		return gameState.getP1Hand().stream()
				.anyMatch(c -> c.hasTrait(CardData.Trait.BACK_ATTACK));
	}

	private boolean hasAttackableForward() {
		int turn = gameState.getTurnNumber();
		for (int i = 0; i < p1ForwardStates.size(); i++) {
			if (p1ForwardStates.get(i) == STATE_NORMAL
					&& (p1ForwardCards.get(i).hasTrait(CardData.Trait.HASTE)
					    || p1ForwardPlayedOnTurn.get(i) != turn))
				return true;
		}
		return false;
	}

	/** Shows a context menu for a P1 forward slot. */
	private void showForwardContextMenu(int idx, JLabel slot, MouseEvent e) {
		JPopupMenu menu = new JPopupMenu();

		GameState.GamePhase phase = gameState.getCurrentPhase();
		boolean hasHaste  = p1ForwardCards.get(idx).hasTrait(CardData.Trait.HASTE);
		boolean canAttack = phase == GameState.GamePhase.ATTACK
				&& p1ForwardStates.get(idx) == STATE_NORMAL
				&& (hasHaste || p1ForwardPlayedOnTurn.get(idx) != gameState.getTurnNumber());
		if (canAttack) {
			JMenuItem attackItem = new JMenuItem("Attack");
			attackItem.addActionListener(ae -> {
				CardData attacker = p1ForwardCards.get(idx);
				logEntry(attacker.name() + " attacks!");
				if (attacker.hasTrait(CardData.Trait.BRAVE)) {
					p1ForwardStates.set(idx, STATE_BRAVE_ATTACKED);
					refreshP1ForwardSlot(idx);
					p2TakeDamage();
				} else {
					p1ForwardStates.set(idx, STATE_DULLED);
					animateDullForward(idx, () -> p2TakeDamage());
				}
			});
			menu.add(attackItem);
		}

		if (AppSettings.isDebugMode()) {
			JMenuItem dullItem = new JMenuItem("Debug: Dull");
			dullItem.addActionListener(ae -> {
				p1ForwardStates.set(idx,
						p1ForwardStates.get(idx) == STATE_DULLED ? STATE_NORMAL : STATE_DULLED);
				refreshP1ForwardSlot(idx);
			});
			menu.add(dullItem);

			JMenuItem freezeItem = new JMenuItem("Debug: Freeze");
			freezeItem.addActionListener(ae -> {
				boolean freezing = p1ForwardStates.get(idx) != STATE_FROZEN;
				int prevState = p1ForwardStates.get(idx);
				p1ForwardStates.set(idx, freezing ? STATE_FROZEN : STATE_NORMAL);
				animateFreezeForward(idx, freezing, prevState);
			});
			menu.add(freezeItem);
		}

		if (menu.getComponentCount() > 0) menu.show(slot, e.getX(), e.getY());
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

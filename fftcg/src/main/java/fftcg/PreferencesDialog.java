package fftcg;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

@SuppressWarnings("serial")
public class PreferencesDialog extends JDialog {

	public PreferencesDialog(Frame owner) {
		this(owner, null);
	}

	public PreferencesDialog(Frame owner, Runnable onLayoutChanged) {
		super(owner, "Preferences", true);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setResizable(false);

		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBorder(new EmptyBorder(12, 16, 8, 16));

		// ── Developer ────────────────────────────────────────────────────────
		JPanel devPanel = new JPanel();
		devPanel.setLayout(new BoxLayout(devPanel, BoxLayout.Y_AXIS));
		devPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), "Developer",
				TitledBorder.LEFT, TitledBorder.TOP));

		JCheckBox debugCheckBox = new JCheckBox("Enable Debug Mode");
		debugCheckBox.setSelected(AppSettings.isDebugMode());
		debugCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		debugCheckBox.addActionListener(e -> {
			AppSettings.setDebugMode(debugCheckBox.isSelected());
			AppSettings.save();
		});
		devPanel.add(debugCheckBox);

		JLabel debugHint = new JLabel(
				"<html><font color='gray' size='2'>Enables debug actions (e.g. adding test cards to zones).</font></html>");
		debugHint.setBorder(new EmptyBorder(2, 20, 4, 4));
		debugHint.setAlignmentX(Component.LEFT_ALIGNMENT);
		devPanel.add(debugHint);

		devPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		contentPanel.add(devPanel);
		contentPanel.add(javax.swing.Box.createVerticalStrut(8));

		// ── Layout ───────────────────────────────────────────────────────────
		JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		layoutPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), "Layout",
				TitledBorder.LEFT, TitledBorder.TOP));

		JPanel sidePanelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		sidePanelRow.add(new JLabel("Side Panel:"));
		JComboBox<String> sidePanelCombo = new JComboBox<>(new String[]{"Left", "Right"});
		sidePanelCombo.setSelectedItem("right".equals(AppSettings.getSidePanelSide()) ? "Right" : "Left");
		sidePanelCombo.addActionListener(e -> {
			AppSettings.setSidePanelSide("Right".equals(sidePanelCombo.getSelectedItem()) ? "right" : "left");
			AppSettings.save();
			if (onLayoutChanged != null) onLayoutChanged.run();
		});
		sidePanelRow.add(sidePanelCombo);
		sidePanelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		layoutPanel.add(sidePanelRow);

		layoutPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		contentPanel.add(layoutPanel);

		// ── Buttons ──────────────────────────────────────────────────────────
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(e -> dispose());
		buttonPanel.add(closeButton);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		getContentPane().add(buttonPanel,  BorderLayout.SOUTH);

		getRootPane().registerKeyboardAction(
			e -> dispose(),
			KeyStroke.getKeyStroke("ESCAPE"),
			JComponent.WHEN_IN_FOCUSED_WINDOW
		);

		pack();
		setLocationRelativeTo(owner);
	}
}

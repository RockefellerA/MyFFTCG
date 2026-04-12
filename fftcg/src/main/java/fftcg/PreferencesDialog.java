package fftcg;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
		super(owner, "Preferences", true);
		setIconImage(Toolkit.getDefaultToolkit().getImage(PreferencesDialog.class.getResource("/resources/MyFF20.png")));
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setResizable(false);

		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBorder(new EmptyBorder(12, 16, 8, 16));

		// ── General ──────────────────────────────────────────────────────────
		JPanel generalPanel = new JPanel();
		generalPanel.setLayout(new BoxLayout(generalPanel, BoxLayout.Y_AXIS));
		generalPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), "General",
				TitledBorder.LEFT, TitledBorder.TOP));

		JCheckBox dynamicBgCheckBox = new JCheckBox("Enable Dynamic Background Colors");
		dynamicBgCheckBox.setEnabled(false);
		generalPanel.add(dynamicBgCheckBox);

		contentPanel.add(generalPanel);
		contentPanel.add(javax.swing.Box.createVerticalStrut(8));

		// ── Developer ────────────────────────────────────────────────────────
		JPanel devPanel = new JPanel();
		devPanel.setLayout(new BoxLayout(devPanel, BoxLayout.Y_AXIS));
		devPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), "Developer",
				TitledBorder.LEFT, TitledBorder.TOP));

		JCheckBox debugCheckBox = new JCheckBox("Enable Debug Mode");
		debugCheckBox.setSelected(AppSettings.isDebugMode());
		debugCheckBox.addActionListener(e -> {
			AppSettings.setDebugMode(debugCheckBox.isSelected());
			AppSettings.save();
		});
		devPanel.add(debugCheckBox);

		JLabel debugHint = new JLabel(
				"<html><font color='gray' size='2'>Enables debug actions (e.g. adding test cards to zones).</font></html>");
		debugHint.setBorder(new EmptyBorder(2, 20, 4, 4));
		devPanel.add(debugHint);

		contentPanel.add(devPanel);

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

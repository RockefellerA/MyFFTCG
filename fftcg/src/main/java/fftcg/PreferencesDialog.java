package fftcg;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Toolkit;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

@SuppressWarnings("serial")
public class PreferencesDialog extends JDialog {

	public PreferencesDialog(Frame owner) {
		super(owner, "Preferences", true);
		setIconImage(Toolkit.getDefaultToolkit().getImage(PreferencesDialog.class.getResource("/resources/MyFF20.png")));
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setResizable(false);

		JPanel checkboxPanel = new JPanel();
		checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));
		checkboxPanel.setBorder(new EmptyBorder(12, 16, 8, 16));

		JCheckBox dynamicBgCheckBox = new JCheckBox("Enable Dynamic Background Colors");
		dynamicBgCheckBox.setEnabled(false);
		checkboxPanel.add(dynamicBgCheckBox);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(e -> dispose());
		buttonPanel.add(closeButton);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(checkboxPanel, BorderLayout.CENTER);
		getContentPane().add(buttonPanel,   BorderLayout.SOUTH);

		getRootPane().registerKeyboardAction(
			e -> dispose(),
			KeyStroke.getKeyStroke("ESCAPE"),
			JComponent.WHEN_IN_FOCUSED_WINDOW
		);

		pack();
		setLocationRelativeTo(owner);
	}
}

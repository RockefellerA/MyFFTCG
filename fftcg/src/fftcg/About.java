package fftcg;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.Toolkit;

public class About extends JDialog {

	private final JPanel contentPanel = new JPanel();

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			About dialog = new About();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	public About() {
		setTitle("About MyFFTCG");
		setAlwaysOnTop(true);
		setIconImage(Toolkit.getDefaultToolkit().getImage(About.class.getResource("/resources/MyFF20.png")));
		setBounds(0, 0, 350, 200);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setLayout(new FlowLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
			JLabel lblNewLabel = new JLabel("<html>Author: Andrew Rockefeller © 2023<br/>Pixel NES font by Neale Davidson</html>");
			lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
			getContentPane().add(lblNewLabel, BorderLayout.CENTER);
		}
	}

}

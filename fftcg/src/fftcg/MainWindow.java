package fftcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

public class MainWindow {

	private JFrame frame;
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MainWindow window = new MainWindow();
					window.frame.setVisible(true);
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
		frame.setBackground(new Color(255, 255, 255));
		frame.getContentPane().setBackground(new Color(0, 0, 160));
		frame.setBounds(800, 500, 800, 600);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		ImageIcon icon = new ImageIcon(getClass().getResource("/resources/MyFF20.png"));
		frame.setIconImage(icon.getImage());
		
		JLabel lblPlayerSide = new JLabel("Player 2 Side");
		lblPlayerSide.setForeground(new Color(255, 255, 255));
		frame.getContentPane().add(lblPlayerSide, BorderLayout.NORTH);
		
		JButton btnNewButton = new JButton("Pass Turn");
		frame.getContentPane().add(btnNewButton, BorderLayout.SOUTH);
	}

}

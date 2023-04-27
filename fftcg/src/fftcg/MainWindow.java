package fftcg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class MainWindow {

	private JFrame frame;
	
	int i = 0;
	
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
		frame.setBounds(800, 500, 1280, 720);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		ImageIcon icon = new ImageIcon(getClass().getResource("/resources/MyFF20.png"));
		frame.setIconImage(icon.getImage());
		
		JLabel lblPlayerSide = new JLabel("Player 2 Side");
		lblPlayerSide.setForeground(new Color(255, 255, 255));
		frame.getContentPane().add(lblPlayerSide, BorderLayout.NORTH);
		
		JButton btnNewButton = new JButton("Active Phase");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				switch (i) {
				case 0: btnNewButton.setText("Draw Phase");
						i++;
						break;
				case 1: btnNewButton.setText("Main Phase 1");
						i++;
						break;
				case 2: btnNewButton.setText("Attack Phase");
						i++;
						break;
				case 3: btnNewButton.setText("Main Phase 2");
						i++;
						break;
				case 4: btnNewButton.setText("End Phase");
						i++;
						break;
				case 5: btnNewButton.setText("Active Phase");
						i=0;
						break;
				}
			}
		});
		frame.getContentPane().add(btnNewButton, BorderLayout.SOUTH);
	}

}

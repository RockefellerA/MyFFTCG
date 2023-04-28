package fftcg;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

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
					ImageIcon icon40 = new ImageIcon(getClass().getResource("/resources/MyFF40.png"));
					window.frame.setIconImage(icon40.getImage());
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
		frame.getContentPane().setLayout(null);
		
		JLabel labelPlayerSide2 = new JLabel("Player 2 Side");
		labelPlayerSide2.setBounds(0, 0, 1264, 14);
		labelPlayerSide2.setForeground(new Color(255, 255, 255));
		frame.getContentPane().add(labelPlayerSide2);
		
		JButton btnNewButton = new JButton("Active Phase");
		btnNewButton.setBounds(0, 658, 1264, 23);
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
		frame.getContentPane().add(btnNewButton);
		
		Label labelPlayerSide1 = new Label("Player 1 Side");
		labelPlayerSide1.setBounds(0, 630, 84, 22);
		labelPlayerSide1.setForeground(new Color(255, 255, 255));
		frame.getContentPane().add(labelPlayerSide1);
		
		Panel panel = new Panel();
		panel.setBackground(new Color(255, 255, 255));
		panel.setBounds(1135, 329, 119, 134);
		frame.getContentPane().add(panel);
		
		Panel panel_1 = new Panel();
		panel_1.setBackground(Color.WHITE);
		panel_1.setBounds(1135, 487, 119, 134);
		frame.getContentPane().add(panel_1);
		
		Panel panel_1_1 = new Panel();
		panel_1_1.setBackground(Color.LIGHT_GRAY);
		panel_1_1.setBounds(10, 329, 127, 295);
		frame.getContentPane().add(panel_1_1);
		
		Panel panel_2 = new Panel();
		panel_2.setBackground(Color.WHITE);
		panel_2.setBounds(18, 165, 119, 134);
		frame.getContentPane().add(panel_2);
		
		Panel panel_3 = new Panel();
		panel_3.setBackground(Color.WHITE);
		panel_3.setBounds(18, 20, 119, 134);
		frame.getContentPane().add(panel_3);
		
		Panel panel_1_1_1 = new Panel();
		panel_1_1_1.setBackground(Color.LIGHT_GRAY);
		panel_1_1_1.setBounds(1127, 16, 127, 295);
		frame.getContentPane().add(panel_1_1_1);
	}
}

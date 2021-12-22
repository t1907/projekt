package projekt;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

class MeetingAgentGui extends JFrame {
	private MeetingAgent myAgent;
	
	private JTextField dayField;
	
	MeetingAgentGui(MeetingAgent a) {
		super(a.getLocalName());
		
		myAgent = a;
		
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(2, 2));
		p.add(new JLabel("Day for meeting:"));
		dayField = new JTextField(15);
		p.add(dayField);
		getContentPane().add(p, BorderLayout.CENTER);
		
		JButton addButton = new JButton("Search");
		addButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				try {
					String day = dayField.getText().trim();
					//myAgent.requestMeeting(Integer.parseInt(day));
					dayField.setText("");
				}
				catch (Exception e) {
					JOptionPane.showMessageDialog(MeetingAgentGui.this, "Invalid values. " + e.getMessage(),
							"Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		} );
		p = new JPanel();
		p.add(addButton);
		getContentPane().add(p, BorderLayout.SOUTH);
		
		addWindowListener(new	WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				myAgent.doDelete();
			}
		} );
		
		setResizable(false);
	}
	
	public void display() {
		pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int centerX = (int)screenSize.getWidth() / 2;
		int centerY = (int)screenSize.getHeight() / 2;
		setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
		setVisible(true);
	}	
}

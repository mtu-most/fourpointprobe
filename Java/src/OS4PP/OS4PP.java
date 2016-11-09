package OS4PP;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.SwingWorker.StateValue;
import javax.swing.border.EtchedBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.serialpundit.core.SerialComException;
import com.serialpundit.serial.SerialComManager;
import com.serialpundit.serial.SerialComManager.BAUDRATE;
import com.serialpundit.serial.SerialComManager.DATABITS;
import com.serialpundit.serial.SerialComManager.FLOWCONTROL;
import com.serialpundit.serial.SerialComManager.PARITY;
import com.serialpundit.serial.SerialComManager.STOPBITS;

/**
 * @author Handy Chandra Spencer Allen
 * @version 2.0
 * @since 1.0
 * 
 *        OS4PP GUI version 2 based on previous version made by Spencer Allen 
 *        Uses SerialPundit library from RishiGupta12
 *        (https://github.com/RishiGupta12/SerialPundit) 
 *        Uses Serial Protocol Framing from Eli Bendersky
 *        (http://eli.thegreenplace.net/2009/08/12/framing-in-serial-communications/)
 */
public class OS4PP {

	private JFrame frmOspp;
	private JTextField textField_2;
	private JTextField textField_3;
	private JTextField textField_5;
	private JTextField textField_4;
	private JTextField textField_7;
	private JTextField textField_8;
	private JTextField textField_6;
	private JTextField textField_9;
	private static SerialComManager scm;
	private DataPoller dataPoller;
	private static String[] comPortsFound;
	private long comPortHandle;
	private ArrayList<float[]> waferPoints = new ArrayList<>();
	private int waferShape = 0; // 1 = circular, 2 = rectangular
	private int waferDiameter; // in mm
	private int waferHeight; // in mm
	private int waferWidth; // in mm
	private MyPanel waferImage; // a Panel holding the image data wafer to
								// display
	private boolean currentset = false;
	private JTextField textField_10;
	private JTextField textField_11;
	private JTextField textField;
	private JTextField textField_1;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					OS4PP window = new OS4PP();
					window.frmOspp.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public OS4PP() {
		initialize();
		try {
			initializeSerial();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frmOspp, e);
			System.exit(0);
		}
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {

		frmOspp = new JFrame();
		frmOspp.setTitle("OS4PP - hover over the buttons to see tooltip help");
		frmOspp.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				DataPoller.exit = true;
				frmOspp.setVisible(false);
				frmOspp.dispose();
				System.exit(0);
			}
		});
		frmOspp.setBounds(100, 100, 950, 635);
		frmOspp.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		SpringLayout springLayout = new SpringLayout();
		frmOspp.getContentPane().setLayout(springLayout);
		
		JPanel panel = new JPanel();
		springLayout.putConstraint(SpringLayout.WEST, panel, 10, SpringLayout.WEST, frmOspp.getContentPane());
		springLayout.putConstraint(SpringLayout.SOUTH, panel, -10, SpringLayout.SOUTH, frmOspp.getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, panel, 500, SpringLayout.WEST, frmOspp.getContentPane());
		panel.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		frmOspp.getContentPane().add(panel);

		JPanel panel_2 = new JPanel();
		springLayout.putConstraint(SpringLayout.NORTH, panel, 0, SpringLayout.NORTH, panel_2);
		springLayout.putConstraint(SpringLayout.NORTH, panel_2, 10, SpringLayout.NORTH, frmOspp.getContentPane());
		springLayout.putConstraint(SpringLayout.WEST, panel_2, -137, SpringLayout.EAST, frmOspp.getContentPane());
		springLayout.putConstraint(SpringLayout.SOUTH, panel_2, -10, SpringLayout.SOUTH, frmOspp.getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, panel_2, -10, SpringLayout.EAST, frmOspp.getContentPane());
		panel_2.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		frmOspp.getContentPane().add(panel_2);

		JScrollPane scrollPane = new JScrollPane();
		springLayout.putConstraint(SpringLayout.NORTH, scrollPane, 10, SpringLayout.NORTH, frmOspp.getContentPane());
		springLayout.putConstraint(SpringLayout.WEST, scrollPane, 10, SpringLayout.EAST, panel);
		springLayout.putConstraint(SpringLayout.SOUTH, scrollPane, -10, SpringLayout.SOUTH, frmOspp.getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, scrollPane, -10, SpringLayout.WEST, panel_2);
		frmOspp.getContentPane().add(scrollPane);

		JTextArea textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setAutoscrolls(true);
		scrollPane.setViewportView(textArea);

		JButton btnEnableDebugMode = new JButton("Debug Mode");
		btnEnableDebugMode.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				byte buffer[] = { 0x7E, 0x03, 0x00, 0x7E };
				byte data[];
				if (comPortHandle == -1) {
					JOptionPane.showMessageDialog(frmOspp, "Please connect the measurement circuit first");
				} else {
					if (textField_1.getText().length() != 1) {
						JOptionPane.showMessageDialog(frmOspp, "Invalid Debug Mode Parameter. Please insert 0 or 1");
					} else {
						data = textField_1.getText().getBytes();
						if ((data[0] == '0') | (data[0] == '1')) {
							buffer[2] = (byte) (data[0] - 0x30);
							try {
								scm.writeBytes(comPortHandle, buffer);
							} catch (SerialComException e1) {
								JOptionPane.showMessageDialog(frmOspp, e1);
							}
						} else {
							JOptionPane.showMessageDialog(frmOspp,
									"Invalid Debug Mode Parameter. Please insert 0 or 1");
						}
					}
				}
			}
		});
		btnEnableDebugMode.setToolTipText("Toggle Debug Mode");
		SpringLayout sl_panel_2 = new SpringLayout();
		sl_panel_2.putConstraint(SpringLayout.NORTH, btnEnableDebugMode, 55, SpringLayout.NORTH, panel_2);
		sl_panel_2.putConstraint(SpringLayout.WEST, btnEnableDebugMode, 6, SpringLayout.WEST, panel_2);
		sl_panel_2.putConstraint(SpringLayout.EAST, btnEnableDebugMode, -6, SpringLayout.EAST, panel_2);
		panel_2.setLayout(sl_panel_2);

		JLabel lblDebugMode = new JLabel("Debug Mode");
		sl_panel_2.putConstraint(SpringLayout.NORTH, lblDebugMode, 3, SpringLayout.NORTH, panel_2);
		sl_panel_2.putConstraint(SpringLayout.WEST, lblDebugMode, 3, SpringLayout.WEST, panel_2);
		sl_panel_2.putConstraint(SpringLayout.SOUTH, lblDebugMode, -38, SpringLayout.NORTH, btnEnableDebugMode);
		sl_panel_2.putConstraint(SpringLayout.EAST, lblDebugMode, 121, SpringLayout.WEST, panel_2);
		lblDebugMode.setToolTipText("This section contains direct command to the Teensy board");
		panel_2.add(lblDebugMode);
		panel_2.add(btnEnableDebugMode);
		SpringLayout sl_panel = new SpringLayout();
		panel.setLayout(sl_panel);

		waferImage = new MyPanel();
		waferImage.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent arg0) {
				if (waferShape == 1) {
					if ((Math.pow(((float) arg0.getX() - (waferImage.getWidth() / 2)), 2)
							+ Math.pow(((float) arg0.getY() - (waferImage.getHeight() / 2)), 2)) <= Math
									.pow(((waferImage.getWidth() - 10) / 2), 2)) {
						waferImage.setToolTipText(String.format("%.1f",
								(float) waferDiameter / (waferImage.getWidth() - 10) * (arg0.getX() - 5)) + ","
								+ String.format("%.1f",
										(float) waferDiameter / (waferImage.getHeight() - 10) * (arg0.getY() - 5)));
					} else {
						waferImage.setToolTipText(null);
					}
				} else if (waferShape == 2) {
					if (waferWidth >= waferHeight) {
						float y1 = (((waferImage.getHeight() - 10)
								- ((float) waferHeight / waferWidth * (waferImage.getWidth() - 10))) / 2) + 5;
						float y2 = (float) waferHeight / waferWidth * (waferImage.getWidth() - 10);
						if ((arg0.getX() >= 5) & (arg0.getX() <= (waferImage.getWidth() - 5)) & (arg0.getY() >= y1)
								& (arg0.getY() <= (y1 + y2))) {
							waferImage.setToolTipText(String.format("%.1f",
									(float) waferWidth / (waferImage.getWidth() - 10) * (arg0.getX() - 5)) + ","
									+ String.format("%.1f", waferHeight / y2 * (arg0.getY() - y1)));
						} else {
							waferImage.setToolTipText(null);
						}
					} else {
						float x1 = (((waferImage.getWidth() - 10)
								- ((float) waferWidth / waferHeight * (waferImage.getHeight() - 10))) / 2) + 5;
						float x2 = (float) waferWidth / waferHeight * (waferImage.getHeight() - 10);
						if ((arg0.getY() >= 5) & (arg0.getY() <= (waferImage.getHeight() - 5)) & (arg0.getX() >= x1)
								& (arg0.getX() <= (x1 + x2))) {
							waferImage.setToolTipText(String.format("%.1f", waferWidth / x2 * (arg0.getX() - x1)) + ","
									+ String.format("%.1f",
											(float) waferHeight / (waferImage.getHeight() - 10) * (arg0.getY() - 5)));
						} else {
							waferImage.setToolTipText(null);
						}
					}
				} else {
					waferImage.setToolTipText(null);
				}
			}
		});
		waferImage.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				float[] tempPoint = new float[2];
				if (waferShape == 1) {
					if ((Math.pow(((float) arg0.getX() - (waferImage.getWidth() / 2)), 2)
							+ Math.pow(((float) arg0.getY() - (waferImage.getHeight() / 2)), 2)) <= Math
									.pow(((waferImage.getWidth() - 10) / 2), 2)) {
						tempPoint[0] = (float) waferDiameter / (waferImage.getWidth() - 10) * (arg0.getX() - 5);
						tempPoint[1] = (float) waferDiameter / (waferImage.getHeight() - 10) * (arg0.getY() - 5);
						if (waferPoints.size() < 999999) {
							waferPoints.add(tempPoint);
							waferImage.drawPoints(waferPoints);
						} else {
							JOptionPane.showMessageDialog(frmOspp, "Maximum number of points reached");
						}
					}
				} else if (waferShape == 2) {
					if (waferWidth >= waferHeight) {
						float y1 = (((waferImage.getHeight() - 10)
								- ((float) waferHeight / waferWidth * (waferImage.getWidth() - 10))) / 2) + 5;
						float y2 = (float) waferHeight / waferWidth * (waferImage.getWidth() - 10);
						if ((arg0.getX() >= 5) & (arg0.getX() <= (waferImage.getWidth() - 5)) & (arg0.getY() >= y1)
								& (arg0.getY() <= (y1 + y2))) {
							tempPoint[0] = (float) waferWidth / (waferImage.getWidth() - 10) * (arg0.getX() - 5);
							tempPoint[1] = waferHeight / y2 * (arg0.getY() - y1);
							if (waferPoints.size() < 999999) {
								waferPoints.add(tempPoint);
								waferImage.drawPoints(waferPoints);
							} else {
								JOptionPane.showMessageDialog(frmOspp, "Maximum number of points reached");
							}
						}
					} else {
						float x1 = (((waferImage.getWidth() - 10)
								- ((float) waferWidth / waferHeight * (waferImage.getHeight() - 10))) / 2) + 5;
						float x2 = (float) waferWidth / waferHeight * (waferImage.getHeight() - 10);
						if ((arg0.getY() >= 5) & (arg0.getY() <= (waferImage.getHeight() - 5)) & (arg0.getX() >= x1)
								& (arg0.getX() <= (x1 + x2))) {
							tempPoint[0] = waferWidth / x2 * (arg0.getX() - x1);
							tempPoint[1] = (float) waferHeight / (waferImage.getHeight() - 10) * (arg0.getY() - 5);
							if (waferPoints.size() < 999999) {
								waferPoints.add(tempPoint);
								waferImage.drawPoints(waferPoints);
							} else {
								JOptionPane.showMessageDialog(frmOspp, "Maximum number of points reached");
							}
						}
					}
				} else {
					JOptionPane.showMessageDialog(frmOspp, "Please insert Wafer shape and dimensions first");
				}
			}
		});
		waferImage.setBackground(Color.BLACK);
		panel.add(waferImage);

		JButton btnNewButton_5 = new JButton("Round");
		btnNewButton_5.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if ((textField_6.getText().length() < 1) | (textField_6.getText().length() > 5)) {
					JOptionPane.showMessageDialog(frmOspp, "Invalid Diameter. Please insert between 1 - 99999 mm");
				} else {
					try {
						waferDiameter = Integer.parseInt(textField_6.getText());
						if (waferDiameter > 0) {
							waferShape = 1;
							waferPoints.clear();
							waferImage.drawCircle(waferDiameter);
						} else {
							JOptionPane.showMessageDialog(frmOspp,
									"Invalid Diameter. Please insert between 1 - 99999 mm");
						}
					} catch (NumberFormatException e1) {
						JOptionPane.showMessageDialog(frmOspp, "Invalid Diameter. Please insert between 1 - 99999 mm");
					}
				}
			}
		});
		btnNewButton_5.setToolTipText("Select Round Wafer shape and set the size");
		btnNewButton_5.setMinimumSize(new Dimension(110, 23));
		btnNewButton_5.setMaximumSize(new Dimension(110, 23));
		btnNewButton_5.setPreferredSize(new Dimension(110, 23));
		panel.add(btnNewButton_5);

		JButton btnNewButton_6 = new JButton("Rectangular");
		btnNewButton_6.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if ((textField_7.getText().length() < 1) | (textField_7.getText().length() > 5)) {
					JOptionPane.showMessageDialog(frmOspp, "Invalid Dimensions. Please insert between 1 - 99999 mm");
				} else if ((textField_8.getText().length() < 1) | (textField_8.getText().length() > 5)) {
					JOptionPane.showMessageDialog(frmOspp, "Invalid Dimensions. Please insert between 1 - 99999 mm");
				} else {
					try {
						waferWidth = Integer.parseInt(textField_7.getText());
						waferHeight = Integer.parseInt(textField_8.getText());
						if ((waferWidth > 0) & (waferHeight > 0)) {
							waferShape = 2;
							waferPoints.clear();
							waferImage.drawRectangular(waferWidth, waferHeight);
						} else {
							JOptionPane.showMessageDialog(frmOspp,
									"Invalid Dimensions. Please insert between 1 - 99999 mm");
						}
					} catch (NumberFormatException e1) {
						JOptionPane.showMessageDialog(frmOspp,
								"Invalid Dimensions. Please insert between 1 - 99999 mm");
					}
				}
			}
		});
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_6, 10, SpringLayout.SOUTH, btnNewButton_5);
		btnNewButton_6.setToolTipText("Select Rectangular Wafer shape and set the size");
		btnNewButton_6.setPreferredSize(new Dimension(110, 23));
		btnNewButton_6.setMinimumSize(new Dimension(110, 23));
		btnNewButton_6.setMaximumSize(new Dimension(110, 23));
		panel.add(btnNewButton_6);

		JButton btnNewButton_7 = new JButton("Generate Points");
		btnNewButton_7.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (waferShape == 0) {
					JOptionPane.showMessageDialog(frmOspp, "Please insert Wafer shape and dimensions first");
				} else {
					if ((textField_3.getText().length() < 1) | (textField_3.getText().length() > 6)) {
						JOptionPane.showMessageDialog(frmOspp,
								"Invalid number of points. Please insert between 1 - 999999");
					} else {
						try {
							int numpoints = Integer.parseInt(textField_3.getText());
							if (numpoints > 0) {
								waferPoints.clear();
								GeneratePoints(numpoints);
								JOptionPane.showMessageDialog(frmOspp,
										"Generated " + Integer.toString(waferPoints.size()) + " points");
								waferImage.drawPoints(waferPoints);
							} else {
								JOptionPane.showMessageDialog(frmOspp,
										"Invalid number of points. Please insert between 1 - 999999");
							}
						} catch (NumberFormatException e1) {
							JOptionPane.showMessageDialog(frmOspp,
									"Invalid number of points. Please insert between 1 - 999999");
						}
					}
				}
			}
		});
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_7, 10, SpringLayout.EAST, waferImage);
		btnNewButton_7.setToolTipText("Auto Generate Points distributed on the Wafer. Will clear  current points");
		btnNewButton_7.setMinimumSize(new Dimension(130, 23));
		btnNewButton_7.setMaximumSize(new Dimension(130, 23));
		btnNewButton_7.setPreferredSize(new Dimension(130, 23));
		panel.add(btnNewButton_7);

		textField_3 = new JTextField();
		sl_panel.putConstraint(SpringLayout.NORTH, textField_3, 0, SpringLayout.NORTH, btnNewButton_7);
		textField_3.setToolTipText("Number of points to generate. Enter 1 - 999999)");
		textField_3.setPreferredSize(new Dimension(7, 23));
		textField_3.setMinimumSize(new Dimension(7, 23));
		sl_panel.putConstraint(SpringLayout.WEST, textField_3, 10, SpringLayout.EAST, btnNewButton_7);
		panel.add(textField_3);
		textField_3.setColumns(4);

		JButton btnNewButton_8 = new JButton("Place");
		btnNewButton_8.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (waferShape == 0) {
					JOptionPane.showMessageDialog(frmOspp, "Please insert Wafer shape and dimensions first");
				} else {
					try {
						float[] tempPoint = new float[2];
						tempPoint[0] = Float.parseFloat(textField_4.getText());
						tempPoint[1] = Float.parseFloat(textField_5.getText());
						if (waferShape == 1) {
							if ((Math.pow((tempPoint[0] - (waferDiameter / 2)), 2)
									+ Math.pow((tempPoint[1] - (waferDiameter / 2)), 2)) <= Math
											.pow((waferDiameter / 2), 2)) {
								if (waferPoints.size() < 999999) {
									waferPoints.add(tempPoint);
									waferImage.drawPoints(waferPoints);
								} else {
									JOptionPane.showMessageDialog(frmOspp, "Maximum number of points reached");
								}
							} else {
								JOptionPane.showMessageDialog(frmOspp,
										"Invalid Dimensions. Please insert points inside the wafer");
							}
						} else if (waferShape == 2) {

							if ((tempPoint[0] >= 0) & (tempPoint[0] <= waferWidth) & (tempPoint[1] >= 0)
									& (tempPoint[1] <= waferHeight)) {
								if (waferPoints.size() < 999999) {
									waferPoints.add(tempPoint);
									waferImage.drawPoints(waferPoints);
								} else {
									JOptionPane.showMessageDialog(frmOspp, "Maximum number of points reached");
								}
							} else {
								JOptionPane.showMessageDialog(frmOspp,
										"Invalid Dimensions. Please insert points inside the wafer");
							}

						} else {
							JOptionPane.showMessageDialog(frmOspp, "Please insert Wafer shape and dimensions first");
						}
					} catch (NumberFormatException e1) {
						JOptionPane.showMessageDialog(frmOspp,
								"Invalid Dimensions. Please insert points inside the wafer");
					}
				}
			}
		});
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_8, 0, SpringLayout.WEST, btnNewButton_7);
		btnNewButton_8.setToolTipText("Manually place a point on to the Wafer");
		btnNewButton_8.setMinimumSize(new Dimension(70, 23));
		btnNewButton_8.setMaximumSize(new Dimension(70, 23));
		btnNewButton_8.setPreferredSize(new Dimension(70, 23));
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_8, 10, SpringLayout.SOUTH, btnNewButton_7);
		panel.add(btnNewButton_8);

		textField_5 = new JTextField();
		sl_panel.putConstraint(SpringLayout.NORTH, textField_5, 11, SpringLayout.SOUTH, btnNewButton_7);
		textField_5.setToolTipText("Y coordinate to place in the Wafer in mm unit (float format)");
		textField_5.setPreferredSize(new Dimension(4, 23));
		textField_5.setMinimumSize(new Dimension(4, 23));
		textField_5.setColumns(4);
		panel.add(textField_5);

		textField_4 = new JTextField();
		sl_panel.putConstraint(SpringLayout.NORTH, textField_4, 11, SpringLayout.SOUTH, btnNewButton_7);
		textField_4.setToolTipText("X coordinate to place in the Wafer in mm unit (float format)");
		sl_panel.putConstraint(SpringLayout.WEST, textField_4, 30, SpringLayout.EAST, btnNewButton_8);
		sl_panel.putConstraint(SpringLayout.WEST, textField_5, 20, SpringLayout.EAST, textField_4);
		textField_4.setPreferredSize(new Dimension(4, 23));
		textField_4.setMinimumSize(new Dimension(4, 23));
		textField_4.setColumns(4);
		panel.add(textField_4);

		JButton btnNewButton_9 = new JButton("Undo");
		btnNewButton_9.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				int i = waferPoints.size();
				if (i > 0) {
					waferPoints.remove(i - 1);
					waferImage.drawPoints(waferPoints);
				}
			}
		});
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_9, 0, SpringLayout.WEST, btnNewButton_8);
		btnNewButton_9.setToolTipText("Delete the last point that were placed");
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_9, 10, SpringLayout.SOUTH, btnNewButton_8);
		btnNewButton_9.setPreferredSize(new Dimension(70, 23));
		btnNewButton_9.setMinimumSize(new Dimension(70, 23));
		btnNewButton_9.setMaximumSize(new Dimension(70, 23));
		panel.add(btnNewButton_9);

		JButton btnNewButton_10 = new JButton("Clear");
		btnNewButton_10.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				waferPoints.clear();
				waferImage.drawPoints(waferPoints);
			}
		});
		btnNewButton_10.setToolTipText("Clear all points on the Wafer");
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_10, 10, SpringLayout.SOUTH, btnNewButton_8);
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_10, 10, SpringLayout.EAST, btnNewButton_9);
		btnNewButton_10.setMinimumSize(new Dimension(70, 23));
		btnNewButton_10.setMaximumSize(new Dimension(70, 23));
		btnNewButton_10.setPreferredSize(new Dimension(70, 23));
		panel.add(btnNewButton_10);

		JLabel lblNewLabel = new JLabel("X:");
		lblNewLabel.setToolTipText("X coordinate to place in the Wafer in mm unit (float format)");
		sl_panel.putConstraint(SpringLayout.NORTH, lblNewLabel, 4, SpringLayout.NORTH, textField_4);
		sl_panel.putConstraint(SpringLayout.WEST, lblNewLabel, -15, SpringLayout.WEST, textField_4);
		panel.add(lblNewLabel);

		JLabel lblY = new JLabel("Y:");
		lblY.setToolTipText("Y coordinate to place in the Wafer in mm unit (float format)");
		sl_panel.putConstraint(SpringLayout.NORTH, lblY, 4, SpringLayout.NORTH, textField_5);
		sl_panel.putConstraint(SpringLayout.WEST, lblY, -15, SpringLayout.WEST, textField_5);
		panel.add(lblY);

		JLabel label = new JLabel("X:");
		label.setToolTipText("X dimension of the Wafer in mm");
		panel.add(label);

		textField_7 = new JTextField();
		sl_panel.putConstraint(SpringLayout.NORTH, textField_7, 11, SpringLayout.SOUTH, btnNewButton_5);
		sl_panel.putConstraint(SpringLayout.WEST, textField_7, 25, SpringLayout.EAST, btnNewButton_6);
		sl_panel.putConstraint(SpringLayout.NORTH, label, 4, SpringLayout.NORTH, textField_7);
		sl_panel.putConstraint(SpringLayout.WEST, label, -15, SpringLayout.WEST, textField_7);
		textField_7.setToolTipText("X dimension of the Wafer in mm");
		textField_7.setPreferredSize(new Dimension(3, 23));
		textField_7.setMinimumSize(new Dimension(3, 23));
		textField_7.setColumns(4);
		panel.add(textField_7);

		JLabel label_1 = new JLabel("Y:");
		label_1.setToolTipText("Y dimension of the Wafer in mm");
		panel.add(label_1);

		textField_8 = new JTextField();
		sl_panel.putConstraint(SpringLayout.NORTH, textField_8, 11, SpringLayout.SOUTH, btnNewButton_5);
		sl_panel.putConstraint(SpringLayout.NORTH, label_1, 4, SpringLayout.NORTH, textField_8);
		sl_panel.putConstraint(SpringLayout.WEST, label_1, -15, SpringLayout.WEST, textField_8);
		sl_panel.putConstraint(SpringLayout.WEST, textField_8, 20, SpringLayout.EAST, textField_7);
		textField_8.setToolTipText("Y dimension of the Wafer in mm");
		textField_8.setPreferredSize(new Dimension(3, 23));
		textField_8.setMinimumSize(new Dimension(3, 23));
		textField_8.setColumns(4);
		panel.add(textField_8);

		textField_6 = new JTextField();
		sl_panel.putConstraint(SpringLayout.WEST, textField_6, 50, SpringLayout.EAST, btnNewButton_5);
		textField_6.setToolTipText("Diameter of the Round Wafer in mm");
		textField_6.setPreferredSize(new Dimension(4, 23));
		textField_6.setMinimumSize(new Dimension(4, 23));
		textField_6.setColumns(4);
		panel.add(textField_6);

		JLabel lblDia = new JLabel("Dia:");
		lblDia.setToolTipText("Diameter of the Round Wafer in mm");
		sl_panel.putConstraint(SpringLayout.NORTH, lblDia, 4, SpringLayout.NORTH, textField_6);
		sl_panel.putConstraint(SpringLayout.WEST, lblDia, -25, SpringLayout.WEST, textField_6);
		panel.add(lblDia);

		JLabel lblNewLabel_1 = new JLabel("1. Sample Geometry");
		sl_panel.putConstraint(SpringLayout.NORTH, textField_6, 11, SpringLayout.SOUTH, lblNewLabel_1);
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_6, 10, SpringLayout.WEST, lblNewLabel_1);
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_5, 10, SpringLayout.WEST, lblNewLabel_1);
		lblNewLabel_1.setToolTipText("Geometry of the sample");
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_5, 10, SpringLayout.SOUTH, lblNewLabel_1);
		sl_panel.putConstraint(SpringLayout.NORTH, lblNewLabel_1, 3, SpringLayout.NORTH, panel);
		sl_panel.putConstraint(SpringLayout.WEST, lblNewLabel_1, 3, SpringLayout.WEST, panel);
		panel.add(lblNewLabel_1);

		JButton btnCurrent = new JButton("Current");
		btnCurrent.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int number;
				if ((textField_9.getText().length() < 1) | (textField_9.getText().length() > 8)) {
					JOptionPane.showMessageDialog(frmOspp,
							"Invalid Current Parameter. Please insert 0 or between 10 - 10,000,000 nA");
				}
				if (DataPoller.exit == true) {
					JOptionPane.showMessageDialog(frmOspp, "Please connect to the measurement circuit first");
				} else {
					try {
						number = Integer.parseInt(textField_9.getText());
						if (((number >= 10) & (number <= 10000000)) | (number == 0)) {
							dataPoller.setCurrent(number);
							currentset = true;
						} else {
							JOptionPane.showMessageDialog(frmOspp,
									"Invalid Current Parameter. Please insert 0 or between 10 - 10,000,000 nA");
						}
					} catch (NumberFormatException e1) {
						JOptionPane.showMessageDialog(frmOspp,
								"Invalid Current Parameter. Please insert 0 or between 10 - 10,000,000 nA");
					}
				}
			}
		});
		btnCurrent.setToolTipText("Value of current to apply to the sample.  0 to enable automatic current adjustment");
		btnCurrent.setPreferredSize(new Dimension(80, 23));
		btnCurrent.setMinimumSize(new Dimension(80, 23));
		btnCurrent.setMaximumSize(new Dimension(80, 23));
		panel.add(btnCurrent);

		textField_9 = new JTextField();
		sl_panel.putConstraint(SpringLayout.NORTH, textField_9, 0, SpringLayout.NORTH, btnCurrent);
		sl_panel.putConstraint(SpringLayout.WEST, textField_9, 10, SpringLayout.EAST, btnCurrent);
		textField_9.setToolTipText(
				"Allowable range from 10 nA - 10,000,000 nA. Enter 0 to enable automatic current adjustment");
		textField_9.setPreferredSize(new Dimension(6, 23));
		textField_9.setMinimumSize(new Dimension(6, 23));
		textField_9.setColumns(6);
		panel.add(textField_9);

		JLabel lblNa = new JLabel("nA");
		lblNa.setToolTipText(
				"Allowable range from 1 nA - 10,000,000 nA. Enter 0 to enable automatic current adjustment");
		sl_panel.putConstraint(SpringLayout.NORTH, lblNa, 4, SpringLayout.NORTH, textField_9);
		sl_panel.putConstraint(SpringLayout.WEST, lblNa, 5, SpringLayout.EAST, textField_9);
		panel.add(lblNa);

		JButton button = new JButton("Save");
		sl_panel.putConstraint(SpringLayout.NORTH, button, 10, SpringLayout.SOUTH, btnNewButton_9);
		sl_panel.putConstraint(SpringLayout.WEST, button, 0, SpringLayout.WEST, btnNewButton_8);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				saveConfiguration();
			}
		});
		button.setToolTipText("Save all measurement configurations");
		button.setPreferredSize(new Dimension(70, 23));
		button.setMinimumSize(new Dimension(70, 23));
		button.setMaximumSize(new Dimension(70, 23));
		panel.add(button);

		JButton button_1 = new JButton("Load");
		button_1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				loadConfiguration();
			}
		});
		sl_panel.putConstraint(SpringLayout.NORTH, button_1, 10, SpringLayout.SOUTH, btnNewButton_9);
		sl_panel.putConstraint(SpringLayout.WEST, button_1, 10, SpringLayout.EAST, button);
		button_1.setToolTipText("Load previously saved measurement configurations");
		button_1.setPreferredSize(new Dimension(70, 23));
		button_1.setMinimumSize(new Dimension(70, 23));
		button_1.setMaximumSize(new Dimension(70, 23));
		panel.add(button_1);

		JButton btnNewButton_16 = new JButton("Stop Measurement\r\n");
		JButton btnNewButton_12 = new JButton("Start Measurement");
		btnNewButton_12.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if ((waferShape == 0) | (waferPoints.size() == 0) | (DataPoller.exit == true)
						| (DataPoller.positioningHandle == -1) | (currentset == false)) {
					JOptionPane.showMessageDialog(frmOspp,
							"Please insert measurement points, current value, connect to the measurement device and positioning system");
				} else {
					if (waferShape == 1) {
						dataPoller.setWafer(waferShape, waferDiameter, 0);
					} else if (waferShape == 2) {
						dataPoller.setWafer(waferShape, waferWidth, waferHeight);
					}
					DataPoller.measure = true;
					dataPoller.addPropertyChangeListener(new PropertyChangeListener() {
						@Override
						public void propertyChange(PropertyChangeEvent evt) {
							if (DataPoller.measure == false) {
								btnNewButton_16.doClick();
							}
						}
					});
					dataPoller.measure(waferPoints);
					btnNewButton_16.setEnabled(true);
					btnNewButton_16.setVisible(true);
					btnNewButton_12.setEnabled(false);
					btnNewButton_12.setVisible(false);
				}
			}
		});
		btnNewButton_12.setToolTipText("Start Measurement of the sample for the points configured above");
		panel.add(btnNewButton_12);

		JComboBox<String> comboBox = new JComboBox<>();
		comboBox.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				try {
					comPortsFound = scm.listAvailableComPorts();
					comboBox.removeAllItems();
					for (int x = 0; x < comPortsFound.length; x++) {
						comboBox.addItem(comPortsFound[x]);
					}
				} catch (SerialComException e1) {
					JOptionPane.showMessageDialog(frmOspp, e1);
				}
			}
		});
		comboBox.setToolTipText("Select the Serial Port connected to the Teensy board");
		panel.add(comboBox);

		JButton btnNewButton_11 = new JButton("Connect");
		JComboBox<String> comboBox_1 = new JComboBox<>();
		JButton btnNewButton_17 = new JButton("Close");
		btnNewButton_11.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if ((textField_10.getText().length() > 6) | (textField_11.getText().length() > 6)) {
					JOptionPane.showMessageDialog(frmOspp,
							"Invalid Center Dimensions. Please insert between -99999 to 99999 mm");
				}
				if (DataPoller.exit == true) {
					JOptionPane.showMessageDialog(frmOspp, "Please connect to the measurement circuit first");
				} else {
					try {
						int x = Integer.parseInt(textField_10.getText());
						int y = Integer.parseInt(textField_11.getText());
						if ((x > 99999) | (y > 99999)) {
							JOptionPane.showMessageDialog(frmOspp,
									"Invalid Center Dimensions. Please insert between -99999 to 99999 mm");
						} else {
							if (comboBox_1.getSelectedItem() != null) {
								if (dataPoller.connectPositioningSystem((String) comboBox_1.getSelectedItem(), x,
										y) == true) {
									dataPoller.addPropertyChangeListener(new PropertyChangeListener() {
										@Override
										public void propertyChange(PropertyChangeEvent evt) {
											if (dataPoller.getState() == StateValue.DONE) {
												btnNewButton_17.doClick();
											}
										}
									});
									try {
										TimeUnit.MILLISECONDS.sleep(1000);  // wait for printer to be ready
									} catch (InterruptedException e1) {
										JOptionPane.showMessageDialog(frmOspp, e1);
									}
									btnNewButton_11.setEnabled(false);
									btnNewButton_11.setVisible(false);
									btnNewButton_17.setEnabled(true);
									btnNewButton_17.setVisible(true);
								}
							} else {
								JOptionPane.showMessageDialog(frmOspp, "Please select which serial port to connect");
							}
						}
					} catch (NumberFormatException e) {
						JOptionPane.showMessageDialog(frmOspp,
								"Invalid Center Dimensions. Please insert between -99999 to 99999 mm");
					}
				}
			}
		});
		btnNewButton_11.setToolTipText("Connect to the Positioning System. Make sure correct Serial Port is selected");
		panel.add(btnNewButton_11);

		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_17, 0, SpringLayout.WEST, btnNewButton_11);
		sl_panel.putConstraint(SpringLayout.SOUTH, btnNewButton_17, 0, SpringLayout.SOUTH, btnNewButton_11);
		sl_panel.putConstraint(SpringLayout.EAST, btnNewButton_17, 0, SpringLayout.EAST, btnNewButton_11);
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_17, 0, SpringLayout.NORTH, btnNewButton_11);
		btnNewButton_17.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				btnNewButton_16.doClick();
				if (DataPoller.positioningHandle != -1) {
					dataPoller.closePositioningSystem();
				}
				btnNewButton_17.setEnabled(false);
				btnNewButton_17.setVisible(false);
				btnNewButton_11.setEnabled(true);
				btnNewButton_11.setVisible(true);
			}
		});
		btnNewButton_17.setToolTipText("Close Serial connection to the Positioning System");
		panel.add(btnNewButton_17);

		comboBox_1.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuCanceled(PopupMenuEvent arg0) {
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent arg0) {
			}

			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent arg0) {
				try {
					comPortsFound = scm.listAvailableComPorts();
					comboBox_1.removeAllItems();
					for (int x = 0; x < comPortsFound.length; x++) {
						comboBox_1.addItem(comPortsFound[x]);
					}
				} catch (SerialComException e1) {
					JOptionPane.showMessageDialog(frmOspp, e1);
				}
			}
		});
		comboBox_1.setToolTipText("Select the Serial Port connected to the Positioning System");
		sl_panel.putConstraint(SpringLayout.NORTH, comboBox_1, 2, SpringLayout.NORTH, btnNewButton_11);
		sl_panel.putConstraint(SpringLayout.WEST, comboBox_1, 10, SpringLayout.EAST, btnNewButton_11);
		sl_panel.putConstraint(SpringLayout.EAST, comboBox_1, 70, SpringLayout.EAST, btnNewButton_11);
		panel.add(comboBox_1);

		JLabel lblNewLabel_2 = new JLabel("4. Current Source");
		sl_panel.putConstraint(SpringLayout.NORTH, lblNewLabel_2, 10, SpringLayout.SOUTH, btnNewButton_11);
		sl_panel.putConstraint(SpringLayout.NORTH, btnCurrent, 10, SpringLayout.SOUTH, lblNewLabel_2);
		sl_panel.putConstraint(SpringLayout.WEST, btnCurrent, 10, SpringLayout.WEST, lblNewLabel_2);
		sl_panel.putConstraint(SpringLayout.WEST, lblNewLabel_2, 3, SpringLayout.WEST, panel);
		lblNewLabel_2.setToolTipText("Set value of Current used for measurements");
		panel.add(lblNewLabel_2);

		JLabel lblNewLabel_3 = new JLabel("2. Test Points");
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_7, 10, SpringLayout.SOUTH, lblNewLabel_3);
		sl_panel.putConstraint(SpringLayout.WEST, waferImage, 10, SpringLayout.WEST, lblNewLabel_3);
		sl_panel.putConstraint(SpringLayout.EAST, waferImage, 260, SpringLayout.WEST, lblNewLabel_3);
		sl_panel.putConstraint(SpringLayout.NORTH, lblNewLabel_3, 10, SpringLayout.SOUTH, btnNewButton_6);
		lblNewLabel_3.setToolTipText("Points on the sample to test");
		sl_panel.putConstraint(SpringLayout.NORTH, waferImage, 10, SpringLayout.SOUTH, lblNewLabel_3);
		sl_panel.putConstraint(SpringLayout.SOUTH, waferImage, 260, SpringLayout.SOUTH, lblNewLabel_3);
		sl_panel.putConstraint(SpringLayout.WEST, lblNewLabel_3, 3, SpringLayout.WEST, panel);
		panel.add(lblNewLabel_3);

		JLabel lblNewLabel_5 = new JLabel("3. Measurement Board");
		sl_panel.putConstraint(SpringLayout.NORTH, lblNewLabel_5, 10, SpringLayout.SOUTH, waferImage);
		sl_panel.putConstraint(SpringLayout.WEST, lblNewLabel_5, 3, SpringLayout.WEST, panel);
		sl_panel.putConstraint(SpringLayout.NORTH, comboBox, 11, SpringLayout.SOUTH, lblNewLabel_5);
		lblNewLabel_5.setToolTipText("Select the serial port and connect to the measurement circuit");
		panel.add(lblNewLabel_5);

		JButton btnNewButton_13 = new JButton("Connect");
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_13, 10, SpringLayout.SOUTH, lblNewLabel_5);
		JButton btnNewButton_14 = new JButton("Close");
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_14, 0, SpringLayout.NORTH, btnNewButton_13);
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_14, 0, SpringLayout.WEST, btnNewButton_13);
		sl_panel.putConstraint(SpringLayout.SOUTH, btnNewButton_14, 0, SpringLayout.SOUTH, btnNewButton_13);
		sl_panel.putConstraint(SpringLayout.EAST, btnNewButton_14, 0, SpringLayout.EAST, btnNewButton_13);

		btnNewButton_13.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (comboBox.getSelectedItem() == null) {
					JOptionPane.showMessageDialog(frmOspp, "Please select which serial port to connect");
				} else {
					try {
						comPortHandle = scm.openComPort((String) comboBox.getSelectedItem(), true, true, true);
						scm.configureComPortData(comPortHandle, DATABITS.DB_8, STOPBITS.SB_1, PARITY.P_NONE,
								BAUDRATE.B9600, 0);
						scm.configureComPortControl(comPortHandle, FLOWCONTROL.NONE, 'x', 'x', false, false);
						DataPoller.exit = false;
						dataPoller = new DataPoller(scm, textArea, comPortHandle);
						dataPoller.addPropertyChangeListener(new PropertyChangeListener() {
							@Override
							public void propertyChange(PropertyChangeEvent evt) {
								if (dataPoller.getState() == StateValue.DONE) {
									btnNewButton_14.doClick();
								}
							}
						});
						dataPoller.execute();
						btnNewButton_13.setEnabled(false);
						btnNewButton_13.setVisible(false);
						btnNewButton_14.setEnabled(true);
						btnNewButton_14.setVisible(true);
					} catch (SerialComException e1) {
						JOptionPane.showMessageDialog(frmOspp, e1);
					}
				}
			}
		});
		btnNewButton_13
				.setToolTipText("Connect to the Teensy board. Make sure correct Serial Port to the Teensy is selected");
		sl_panel.putConstraint(SpringLayout.WEST, comboBox, 10, SpringLayout.EAST, btnNewButton_13);
		sl_panel.putConstraint(SpringLayout.EAST, comboBox, 70, SpringLayout.EAST, btnNewButton_13);
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_13, 17, SpringLayout.WEST, lblNewLabel_5);
		panel.add(btnNewButton_13);

		btnNewButton_14.setEnabled(false);
		btnNewButton_14.setVisible(false);
		btnNewButton_14.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				DataPoller.exit = true;
				if (comPortHandle != -1) {
					comPortHandle = -1;
					btnNewButton_14.setEnabled(false);
					btnNewButton_14.setVisible(false);
					btnNewButton_13.setEnabled(true);
					btnNewButton_13.setVisible(true);
				}
			}
		});
		btnNewButton_14.setToolTipText("Close Serial connection to the Teensy board");
		panel.add(btnNewButton_14);

		JLabel lblNewLabel_4 = new JLabel("5. Positioning System");
		sl_panel.putConstraint(SpringLayout.NORTH, lblNewLabel_4, 10, SpringLayout.SOUTH, waferImage);
		sl_panel.putConstraint(SpringLayout.WEST, lblNewLabel_4, 10, SpringLayout.EAST, waferImage);
		lblNewLabel_4.setToolTipText(
				"Connect to the positioning system. Any system that accepts ascii gcode over serial will do.");
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_11, 10, SpringLayout.WEST, lblNewLabel_4);
		panel.add(lblNewLabel_4);

		JLabel lblNewLabel_6 = new JLabel("6. Measurement");
		sl_panel.putConstraint(SpringLayout.NORTH, lblNewLabel_6, 10, SpringLayout.SOUTH, btnNewButton_11);
		lblNewLabel_6.setToolTipText("Start the measurement process");
		sl_panel.putConstraint(SpringLayout.WEST, lblNewLabel_6, 10, SpringLayout.EAST, waferImage);
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_12, 10, SpringLayout.SOUTH, lblNewLabel_6);
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_12, 10, SpringLayout.WEST, lblNewLabel_6);
		panel.add(lblNewLabel_6);

		JLabel lblNewLabel_7 = new JLabel("Center: X:");
		sl_panel.putConstraint(SpringLayout.WEST, lblNewLabel_7, 14, SpringLayout.WEST, lblNewLabel_4);
		lblNewLabel_7.setToolTipText("X position on the system that correspond to the middle of the sample");
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_11, 10, SpringLayout.SOUTH, lblNewLabel_7);
		sl_panel.putConstraint(SpringLayout.NORTH, lblNewLabel_7, 10, SpringLayout.SOUTH, lblNewLabel_4);
		panel.add(lblNewLabel_7);

		textField_10 = new JTextField();
		textField_10.setToolTipText("X position on the system that correspond to the middle of the sample");
		sl_panel.putConstraint(SpringLayout.NORTH, textField_10, 8, SpringLayout.SOUTH, lblNewLabel_4);
		sl_panel.putConstraint(SpringLayout.WEST, textField_10, 10, SpringLayout.EAST, lblNewLabel_7);
		panel.add(textField_10);
		textField_10.setColumns(4);

		textField_11 = new JTextField();
		textField_11.setToolTipText("Y position on the system that correspond to the middle of the sample");
		sl_panel.putConstraint(SpringLayout.NORTH, textField_11, 8, SpringLayout.SOUTH, lblNewLabel_4);
		panel.add(textField_11);
		textField_11.setColumns(4);

		JLabel lblNewLabel_8 = new JLabel("Y:\r\n");
		sl_panel.putConstraint(SpringLayout.NORTH, lblNewLabel_8, 10, SpringLayout.SOUTH, lblNewLabel_4);
		sl_panel.putConstraint(SpringLayout.WEST, lblNewLabel_8, 10, SpringLayout.EAST, textField_10);
		lblNewLabel_8.setToolTipText("Y position on the system that correspond to the middle of the sample");
		sl_panel.putConstraint(SpringLayout.WEST, textField_11, 10, SpringLayout.EAST, lblNewLabel_8);
		panel.add(lblNewLabel_8);

		btnNewButton_16.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				DataPoller.measure = false;
				btnNewButton_16.setEnabled(false);
				btnNewButton_16.setVisible(false);
				btnNewButton_12.setEnabled(true);
				btnNewButton_12.setVisible(true);
			}
		});
		btnNewButton_16.setToolTipText("Stop ongoing measurement");
		sl_panel.putConstraint(SpringLayout.NORTH, btnNewButton_16, 10, SpringLayout.SOUTH, lblNewLabel_6);
		sl_panel.putConstraint(SpringLayout.WEST, btnNewButton_16, 10, SpringLayout.WEST, lblNewLabel_6);
		panel.add(btnNewButton_16);

		JButton btnNewButton = new JButton("Auto Current\r\n");
		sl_panel_2.putConstraint(SpringLayout.NORTH, btnNewButton, 38, SpringLayout.SOUTH, btnEnableDebugMode);
		sl_panel_2.putConstraint(SpringLayout.WEST, btnNewButton, 6, SpringLayout.WEST, panel_2);
		sl_panel_2.putConstraint(SpringLayout.EAST, btnNewButton, -6, SpringLayout.EAST, panel_2);
		btnNewButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				byte buffer[] = { 0x7E, 0x01, 0x7E };
				if (comPortHandle == -1) {
					JOptionPane.showMessageDialog(frmOspp, "Please connect the measurement circuit first");
				} else {
					try {
						scm.writeBytes(comPortHandle, buffer);
					} catch (SerialComException e1) {
						JOptionPane.showMessageDialog(frmOspp, e1);
					}
				}
			}
		});
		btnNewButton.setToolTipText(
				"Automatically adjust the value of Current Source based on the sample. Make sure the probe is touching sample");
		panel_2.add(btnNewButton);

		JButton btnNewButton_1 = new JButton("Set Current");
		sl_panel_2.putConstraint(SpringLayout.NORTH, btnNewButton_1, 10, SpringLayout.SOUTH, btnNewButton);
		sl_panel_2.putConstraint(SpringLayout.WEST, btnNewButton_1, 6, SpringLayout.WEST, panel_2);
		sl_panel_2.putConstraint(SpringLayout.EAST, btnNewButton_1, -6, SpringLayout.EAST, panel_2);
		btnNewButton_1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				byte buffer[] = { 0x7E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7E };
				int number;
				if (comPortHandle == -1) {
					JOptionPane.showMessageDialog(frmOspp, "Please connect the measurement circuit first");
				} else {
					if ((textField.getText().length() < 1) | (textField.getText().length() > 8)) {
						JOptionPane.showMessageDialog(frmOspp,
								"Invalid Set Current Parameter. Please insert 0 or between 10 - 10,000,000 nA");
					} else {
						try {
							number = Integer.parseInt(textField.getText());
							if ((number == 0) | ((number >= 10) & (number <= 10000000))) {
								buffer[2] = (byte) (number >> 24);
								buffer[3] = (byte) (number >> 16);
								buffer[4] = (byte) (number >> 8);
								buffer[5] = (byte) number;
								try {
									scm.writeBytes(comPortHandle, buffer);
								} catch (SerialComException e2) {
									JOptionPane.showMessageDialog(frmOspp, e2);
								}
							} else {
								JOptionPane.showMessageDialog(frmOspp,
										"Invalid Set Current Parameter. Please insert 0 or between 10 - 10,000,000");
							}
						} catch (NumberFormatException e1) {
							JOptionPane.showMessageDialog(frmOspp,
									"Invalid Set Current Parameter. Please insert 0 or between 10 - 10,000,000");
						}
					}
				}
			}
		});
		btnNewButton_1.setToolTipText(
				"Adjust the value of Current Source manually in nA unit. Make sure the probe is touching sample");
		panel_2.add(btnNewButton_1);

		JButton btnNewButton_2 = new JButton("Direction");
		sl_panel_2.putConstraint(SpringLayout.WEST, btnNewButton_2, 6, SpringLayout.WEST, panel_2);
		sl_panel_2.putConstraint(SpringLayout.EAST, btnNewButton_2, -6, SpringLayout.EAST, panel_2);
		btnNewButton_2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				byte buffer[] = { 0x7E, 0x02, 0x00, 0x7E };
				byte data[];

				if (comPortHandle == -1) {
					JOptionPane.showMessageDialog(frmOspp, "Please connect the measurement circuit first");
				} else {
					if (textField_2.getText().length() != 1) {
						JOptionPane.showMessageDialog(frmOspp, "Invalid Direction Parameter. Please insert 0 or 1");
					} else {
						data = textField_2.getText().getBytes();
						if ((data[0] == '0') | (data[0] == '1')) {
							buffer[2] = (byte) (data[0] - 0x30);
							try {
								scm.writeBytes(comPortHandle, buffer);
							} catch (SerialComException e1) {
								JOptionPane.showMessageDialog(frmOspp, e1);
							}
						} else {
							JOptionPane.showMessageDialog(frmOspp, "Invalid Direction Parameter. Please insert 0 or 1");
						}
					}
				}
			}
		});
		btnNewButton_2.setToolTipText("Change the direction of Current Source");
		panel_2.add(btnNewButton_2);

		textField_2 = new JTextField();
		sl_panel_2.putConstraint(SpringLayout.NORTH, textField_2, 10, SpringLayout.SOUTH, btnNewButton_2);
		sl_panel_2.putConstraint(SpringLayout.WEST, textField_2, 56, SpringLayout.WEST, panel_2);
		textField_2.setToolTipText("0 = forward or 1 = backward");
		panel_2.add(textField_2);
		textField_2.setColumns(1);

		JButton btnNewButton_3 = new JButton("Check Probe");
		sl_panel_2.putConstraint(SpringLayout.NORTH, btnNewButton_3, 10, SpringLayout.SOUTH, textField_2);
		sl_panel_2.putConstraint(SpringLayout.WEST, btnNewButton_3, 6, SpringLayout.WEST, panel_2);
		sl_panel_2.putConstraint(SpringLayout.EAST, btnNewButton_3, -6, SpringLayout.EAST, panel_2);
		btnNewButton_3.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				byte buffer[] = { 0x7E, 0x05, 0x7E };
				if (comPortHandle == -1) {
					JOptionPane.showMessageDialog(frmOspp, "Please connect the measurement circuit first");
				} else {
					try {
						scm.writeBytes(comPortHandle, buffer);
					} catch (SerialComException e1) {
						JOptionPane.showMessageDialog(frmOspp, e1);
					}
				}
			}
		});
		btnNewButton_3.setToolTipText("Check whether the probe is touching sample or not");
		panel_2.add(btnNewButton_3);

		JButton btnNewButton_4 = new JButton("Measure");
		sl_panel_2.putConstraint(SpringLayout.NORTH, btnNewButton_4, 10, SpringLayout.SOUTH, btnNewButton_3);
		sl_panel_2.putConstraint(SpringLayout.WEST, btnNewButton_4, 6, SpringLayout.WEST, panel_2);
		sl_panel_2.putConstraint(SpringLayout.EAST, btnNewButton_4, -6, SpringLayout.EAST, panel_2);
		btnNewButton_4.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				byte buffer[] = { 0x7E, 0x04, 0x7E };

				if (comPortHandle == -1) {
					JOptionPane.showMessageDialog(frmOspp, "Please connect the measurement circuit first");
				} else {
					try {
						scm.writeBytes(comPortHandle, buffer);
					} catch (SerialComException e1) {
						JOptionPane.showMessageDialog(frmOspp, e1);
					}
				}
			}
		});
		btnNewButton_4.setToolTipText("Measure the voltage on the voltage probe");
		panel_2.add(btnNewButton_4);

		textField = new JTextField();
		sl_panel_2.putConstraint(SpringLayout.WEST, textField, 28, SpringLayout.WEST, panel_2);
		sl_panel_2.putConstraint(SpringLayout.NORTH, btnNewButton_2, 10, SpringLayout.SOUTH, textField);
		sl_panel_2.putConstraint(SpringLayout.NORTH, textField, 10, SpringLayout.SOUTH, btnNewButton_1);
		panel_2.add(textField);
		textField.setColumns(6);

		textField_1 = new JTextField();
		sl_panel_2.putConstraint(SpringLayout.NORTH, textField_1, 10, SpringLayout.SOUTH, btnEnableDebugMode);
		sl_panel_2.putConstraint(SpringLayout.WEST, textField_1, 56, SpringLayout.WEST, panel_2);
		panel_2.add(textField_1);
		textField_1.setColumns(1);
	}

	/**
	 * Initialize serial handle and create new SerialComManager object
	 *
	 * @param None
	 * @return None
	 */
	private void initializeSerial() throws Exception {
		comPortHandle = -1;
		scm = new SerialComManager();
	}

	/**
	 * Generate points automatically distributed across the wafer sample based
	 * on the amount of points inserted on the GUI
	 * <p>
	 * The wafer shape and dimensions needs to be inserted in the GUI first
	 * before calling this method
	 * <p>
	 * Created by Spencer Allen
	 *
	 * @param amount number of points to generate
	 * @return None
	 */
	private void GeneratePoints(int amount) {

		if (waferShape == 1) { // circular shape
			int numberOfRows = 1;
			float power = 2;

			// figure out the number of rows needed
			// fill in rows until we run out of points to place
			// the highest row number is the needed number
			int pointsPlaced = 0;
			int pointsThisRow = 0;
			while (true) {
				pointsPlaced++;
				pointsThisRow++;
				if (pointsPlaced == amount) {
					break;
				}
				if (pointsThisRow == Math.pow(numberOfRows, power)) {
					pointsThisRow = 0;
					numberOfRows++;
				}
			}

			// now place all of the points
			pointsPlaced = 0;
			for (int i = 1; i <= numberOfRows; i++) {
				for (int j = 0; j < Math.pow(i, power); j++) {
					if (pointsPlaced >= amount) {
						break;
					}

					// figure out the location of the point based off of the row
					// number (i) and point number (j)
					float distance = (float) waferDiameter * (i - 1) / (numberOfRows * 2);
					float angle = 0;
					if (i == numberOfRows) {
						angle = (float) j * 360 / pointsThisRow;
					} else {
						angle = (float) ((float) j * 360 / (Math.pow(i, power)));
					}

					float[] tempPoint = new float[2];
					tempPoint[0] = (float) ((float) waferDiameter / 2 + distance * Math.sin(Math.toRadians(angle)));
					tempPoint[1] = (float) ((float) waferDiameter / 2 + distance * Math.cos(Math.toRadians(angle)));
					waferPoints.add(tempPoint);
					pointsPlaced++;
				}
			}
		} else {
			if (amount % 2 != 0)
				amount++;

			// simple optimization script
			float closestValue = 1;
			int closestI = -1;
			while (true) {
				for (int i = 1; i <= amount; i++) {
					float x = i;
					float y = amount / x;

					if (amount % x != 0) {
						continue;
					}

					float val = Math.abs((float) waferWidth / waferHeight - x / y);
					if (val < closestValue) {
						closestValue = val;
						closestI = i;
					}
				}

				// sometimes a good fill can't be found for the number of points
				// added
				// so we add a few points to make it work
				if (closestI < 0) {
					amount += 2;
				} else {
					break;
				}
			}

			int pointsPerColumn = closestI;
			int pointsPerRow = amount / closestI;

			// now fill in the points
			for (int i = 1; i <= pointsPerRow; i++) {
				for (int j = 1; j <= pointsPerColumn; j++) {
					float[] tempPoint = new float[2];
					tempPoint[0] = (float) j * waferWidth / (pointsPerColumn + 1);
					tempPoint[1] = (float) i * waferHeight / (pointsPerRow + 1);
					waferPoints.add(tempPoint);
				}
			}
		}
	}

	/**
	 * Save measurement configuration to config.csv file located on the same
	 * directory as the application.
	 *
	 * @param None
	 * @return None
	 */
	private void saveConfiguration() {

		String[] lineEntry = new String[4];
		CSVWriter writer = null;

		try {
			writer = new CSVWriter(new FileWriter("config.csv"), ',');

			if (waferShape == 1) {
				lineEntry[0] = "wafershape";
				lineEntry[1] = "circular";
				lineEntry[2] = Integer.toString(waferDiameter);
				lineEntry[3] = "";
				writer.writeNext(lineEntry);
			} else if (waferShape == 2) {
				lineEntry[0] = "wafershape";
				lineEntry[1] = "rectangular";
				lineEntry[2] = Integer.toString(waferWidth);
				lineEntry[3] = Integer.toString(waferHeight);
				writer.writeNext(lineEntry);
			}

			if (waferShape != 0) {
				lineEntry[0] = "point";
				for (int i = 0; i < waferPoints.size(); i++) {
					float[] tempPoint = waferPoints.get(i);
					lineEntry[1] = Float.toString(tempPoint[0]);
					lineEntry[2] = Float.toString(tempPoint[1]);
					lineEntry[3] = "";
					writer.writeNext(lineEntry);
				}
			}

		} catch (Exception e) {
			JOptionPane.showMessageDialog(frmOspp, e);
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					JOptionPane.showMessageDialog(frmOspp, e);
				}
			}
		}
	}

	/**
	 * Load measurement configuration from config.csv file located on the same
	 * directory as the application.
	 *
	 * @param None
	 * @return None
	 */
	private void loadConfiguration() {

		String[] nextLine = new String[4];
		int number;
		int number2;
		int shape = 0;
		int dia = 0;
		int width = 0;
		int height = 0;
		CSVReader reader = null;

		// parse the file twice. first check for error in the file
		try {
			reader = new CSVReader(new FileReader("config.csv"), ',');
			nextLine = reader.readNext();
			if (nextLine == null) {
				JOptionPane.showMessageDialog(frmOspp, "Empty config file");
				return;
			}

			if (nextLine[0].equals("wafershape") == false) {
				JOptionPane.showMessageDialog(frmOspp, "Invalid config file");
				return;
			}
			if (nextLine[1].equals("circular")) {
				try {
					number = Integer.parseInt(nextLine[2]);
					if ((number == 0) | (number > 99999)) {
						JOptionPane.showMessageDialog(frmOspp, "Invalid wafer diameter");
						return;
					}
					shape = 1;
					dia = number;
				} catch (NumberFormatException e) {
					JOptionPane.showMessageDialog(frmOspp, "Invalid wafer diameter");
					return;
				}

			} else if (nextLine[1].equals("rectangular")) {
				try {
					number = Integer.parseInt(nextLine[2]);
					number2 = Integer.parseInt(nextLine[3]);
					if ((number == 0) | (number > 99999) | (number2 == 0) | (number2 > 99999)) {
						JOptionPane.showMessageDialog(frmOspp, "Invalid wafer dimensions");
						return;
					}
					shape = 2;
					width = number;
					height = number2;
				} catch (NumberFormatException e) {
					JOptionPane.showMessageDialog(frmOspp, "Invalid wafer dimensions");
					return;
				}
			} else {
				JOptionPane.showMessageDialog(frmOspp, "Invalid config file");
				return;
			}
			while ((nextLine = reader.readNext()) != null) {
				if (nextLine[0].equals("point") == false) {
					JOptionPane.showMessageDialog(frmOspp, "Invalid config file");
					return;
				}
				float[] tempPoint = new float[2];
				try {
					tempPoint[0] = Float.parseFloat(nextLine[1]);
					tempPoint[1] = Float.parseFloat(nextLine[2]);
					if (shape == 1) {
						if ((Math.pow((tempPoint[0] - (dia / 2)), 2) + Math.pow((tempPoint[1] - (dia / 2)), 2)) > Math
								.pow((dia / 2), 2)) {
							JOptionPane.showMessageDialog(frmOspp, "Invalid point data");
							return;
						}
					} else if (shape == 2) {
						if ((tempPoint[0] < 0) & (tempPoint[0] > width) & (tempPoint[1] < 0)
								& (tempPoint[1] > height)) {
							JOptionPane.showMessageDialog(frmOspp, "Invalid point data");
							return;
						}
					}
				} catch (NumberFormatException e) {
					JOptionPane.showMessageDialog(frmOspp, "Invalid point data");
					return;
				}
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frmOspp, e);
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
				JOptionPane.showMessageDialog(frmOspp, e);
			}
		}

		// after the file verified, now get the data
		try {
			reader = new CSVReader(new FileReader("config.csv"), ',');
			nextLine = reader.readNext();
			if (nextLine[1].equals("circular")) {
				try {
					number = Integer.parseInt(nextLine[2]);
					waferDiameter = number;
					waferShape = 1;
					waferPoints.clear();
					waferImage.drawCircle(waferDiameter);
				} catch (NumberFormatException e) {
					JOptionPane.showMessageDialog(frmOspp, "Invalid wafer diameter");
					return;
				}
			} else if (nextLine[1].equals("rectangular")) {
				try {
					number = Integer.parseInt(nextLine[2]);
					number2 = Integer.parseInt(nextLine[3]);
					waferWidth = number;
					waferHeight = number2;
					waferShape = 2;
					waferPoints.clear();
					waferImage.drawRectangular(waferWidth, waferHeight);
				} catch (NumberFormatException e) {
					JOptionPane.showMessageDialog(frmOspp, "Invalid wafer dimensions");
					return;
				}
			}
			while ((nextLine = reader.readNext()) != null) {
				float[] tempPoint = new float[2];
				try {
					tempPoint[0] = Float.parseFloat(nextLine[1]);
					tempPoint[1] = Float.parseFloat(nextLine[2]);
					if (waferShape == 1) {
						if (waferPoints.size() < 999999) {
							waferPoints.add(tempPoint);
							waferImage.drawPoints(waferPoints);
						} else {
							JOptionPane.showMessageDialog(frmOspp, "Maximum number of points reached");
							return;
						}
					} else if (waferShape == 2) {
						if (waferPoints.size() < 999999) {
							waferPoints.add(tempPoint);
							waferImage.drawPoints(waferPoints);
						} else {
							JOptionPane.showMessageDialog(frmOspp, "Maximum number of points reached");
							return;
						}
					}
				} catch (NumberFormatException e) {
					JOptionPane.showMessageDialog(frmOspp, "Invalid point data");
					return;
				}
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frmOspp, e);
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
				JOptionPane.showMessageDialog(frmOspp, e);
			}
		}
	}
}

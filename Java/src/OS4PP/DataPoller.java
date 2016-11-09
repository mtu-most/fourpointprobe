package OS4PP;

import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

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
 *
 *        This class will run in the background after this application is connected to
 *        the measurement circuit. 
 *        If there is data from serial port it will be displayed in the
 *        TextArea on the GUI. 
 *        This thread will also exchange commands to the
 *        serial port to perform measurements.
 */
public class DataPoller extends SwingWorker<Integer, String> {

	private final SerialComManager scm;
	private final JTextArea text;
	private final long comPortHandle;

	public static boolean exit = true;
	public static boolean measure = false;
	public static long positioningHandle = -1;

	private int mode = 0;
	private int current = 0;
	private int retry = 0;
	private int retryprobe = 0;
	private int numpoints = 0;
	private int currentpoint = 1;
	private long initialtime = 0;
	private long currenttime = 0;
	private long elapsedtime = 0;
	private int xcenter = 0;
	private int ycenter = 0;
	private boolean raiseonce = false;
	private int waferShape = 0;
	private int waferDiameter = 0;
	private int waferHeight = 0;
	private int waferWidth = 0;
	float voltagemeasuredf = 0;
	float currentmeasuredf = 0;
	float voltagemeasuredb = 0;
	float currentmeasuredb = 0;
	float resf = 0;
	float resb = 0;

	private CSVWriter writer = null;
	private ArrayList<float[]> pointdata;

	private enum Measurestate {
		raise1, waitraise1, home, waithome, setunits, setabsolute, raise2, waitraise2, center, waitcenter, points, waitpoints, lower, waitlower, enablecurrent, waitenablecurrent1, waitenablecurrent2, delayenablecurrent, checkprobe, waitcheckprobe, disablecurrent1, waitdisablecurrent1, wait2disablecurrent1, delaydisablecurrent1, setcurrent, waitsetcurrent1, waitsetcurrent2, delaysetcurrent, forward, waitforward, waitforward2, measure1, waitmeasure1, waitvoltage1, waitcurrent1, reverse, waitreverse, waitreverse2, measure2, waitmeasure2, waitvoltage2, waitcurrent2, store, disablecurrent2, waitdisablecurrent2, wait2disablecurrent2, delaydisablecurrent2, raise3, waitraise3
	}

	private Measurestate measurestate = Measurestate.raise1;

	private int stateread = 0;
	private byte[] databuf = new byte[5];
	private int dataptr = 0;
	private boolean packetrecv = false;

	/**
	 * Constructor for DataPoller
	 *
	 * @param scm connected serial port object
	 * @param text JTextArea object on the GUI to display incoming serial data to
	 * @param comPortHandle serial port handle used by the SerialComManager
	 * @return None
	 */
	public DataPoller(SerialComManager scm, JTextArea text, long comPortHandle) {
		this.scm = scm;
		this.comPortHandle = comPortHandle;
		this.text = text;
	}

	/**
	 * Set sample shape and size
	 * <p>
	 * This method is called when Start Measurement button on GUI is clicked
	 *
	 * @param shape shape of the sample
	 * @param d1 diameter of the sample if circular or width of the sample if rectangular
	 * @param d2 height of the sample
	 * @return None
	 */
	public void setWafer(int shape, int d1, int d2) {
		if (shape == 1) {
			this.waferShape = 1;
			this.waferDiameter = d1;
		} else {
			this.waferShape = 2;
			this.waferWidth = d1;
			this.waferHeight = d2;
		}
	}

	/**
	 * Connect to the positioning system
	 * <p>
	 * This method is called when Connect to the positioning system button is clicked
	 *
	 * @param port name of the port to connect as a String object
	 * @param x x-coordinate of the positioning system corresponding to the center of the sample
	 * @param y y-coordinate of the positioning system corresponding to the center of the sample
	 * @return true if connection attempt is successful
	 */
	public boolean connectPositioningSystem(String port, int x, int y) {
		try {
			positioningHandle = this.scm.openComPort(port, true, true, true);
			this.scm.configureComPortData(positioningHandle, DATABITS.DB_8, STOPBITS.SB_1, PARITY.P_NONE,
					BAUDRATE.B115200, 0);
			this.scm.configureComPortControl(positioningHandle, FLOWCONTROL.NONE, 'x', 'x', false, false);
			this.xcenter = x;
			this.ycenter = y;
			return true;
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this.text, e);
			return false;
		}
	}

	/**
	 * Close connection to the positioning system
	 * <p>
	 * Called when Close positioning system button is clicked. Will also get
	 * called when positioning system got disconnected or when DataPoller thread
	 * is done
	 *
	 * @param None
	 * @return None
	 */
	public void closePositioningSystem() {
		
		// raise the probe once after measurement done
		if(raiseonce == true) {
			raiseonce = false;
			
			// Disable current source and wait for 200 ms
			byte[] buffer3 = new byte[7];
			
			buffer3[0] = (byte) 0x7E;
			buffer3[1] = (byte) 0x00;
			buffer3[2] = (byte) 0x00;
			buffer3[3] = (byte) 0x00;
			buffer3[4] = (byte) 0x00;
			buffer3[5] = (byte) 0x00;
			buffer3[6] = (byte) 0x7E;
			try {
				this.scm.writeBytes(this.comPortHandle, buffer3);
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
			}
			try {
				TimeUnit.MILLISECONDS.sleep(200);
			} catch (InterruptedException e1) {
				JOptionPane.showMessageDialog(this.text, e1);
			}
			
			// raise the probe after measurement to make it easier to remove the sample
			try {
				byte[] gcode = "G1 Z20 F3000\r\n".getBytes();
				this.scm.writeBytes(positioningHandle, gcode);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this.text, e);
			}
		}
		
		if (positioningHandle != -1) {
			try {
				this.scm.closeComPort(positioningHandle);
			} catch (Exception e1) {
				JOptionPane.showMessageDialog(this.text, e1);
			}
			positioningHandle = -1;
		}
	}

	/**
	 * Set current value on the adjustable current source to inserted value on
	 * the GUI
	 * <p>
	 * This method is intended to be called by main GUI when Current button is
	 * clicked. Will set the current variable so that when measurement starts,
	 * current will be automatically adjusted
	 *
	 * @param data value of current in nA, or zero for auto current
	 * @return None
	 */
	public void setCurrent(int data) {
		this.current = data;
	}

	/**
	 * Start measurement on the wafer
	 * <p>
	 * This method is intended to be called by main GUI when start measurement
	 * button is clicked. Intended to be called after all the information needed
	 * has been inserted on the GUI. The code in main GUI will check for the 
	 * requirements before calling this method
	 * <p>
	 * Will set mode = 1 so that doInBackground() thread will call
	 * measureMode() state machine to perform measurements. The results will be
	 * saved in a CSV file while measurement is ongoing
	 *
	 * @param pointlist ArrayList of float[2] containing real coordinate of points to be measured on the wafer
	 * @return None
	 */
	public void measure(ArrayList<float[]> pointlist) {
		this.mode = 1;
		this.measurestate = Measurestate.raise1;
		this.numpoints = pointlist.size();
		this.currentpoint = 1;
		this.retry = 0;
		this.packetrecv = false;
		this.pointdata = pointlist;
		this.raiseonce = true;
		String[] lineEntry = new String[9];

		lineEntry[0] = "Point";
		lineEntry[1] = "X (0 means leftmost of the wafer)";
		lineEntry[2] = "Y (0 means topmost of the wafer)";
		lineEntry[3] = "Voltage Forward (uV)";
		lineEntry[4] = "Current Forward (nA)";
		lineEntry[5] = "Sheet Resistance Forward (ohm/sq)";
		lineEntry[6] = "Voltage Backward (uV)";
		lineEntry[7] = "Current Backward (nA)";
		lineEntry[8] = "Sheet Resistance Backward (ohm/sq)";

		try {
			this.writer = new CSVWriter(new FileWriter("results.csv"), ',');
			this.writer.writeNext(lineEntry);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this.text, e);
		}
	}

	/**
	 * Performs measurement on multiple points on the sample
	 * <p>
	 * This is a state machine called by doInBackground().
	 * Will exchange data to the positioning system and measurement circuit to perform measurement.
	 * <p>
	 * Each step will send data to the positioning system or measurement circuit,
	 * and then wait for a predetermined amount of time and checks for reply if sending to measurement circuit.
	 * Before lowering the probe to the sample, current source will be turned off, and turned on again after probe lowered.
	 * Then will check if sample if detected or not, if not the program will try again one more time before deciding
	 * to move on to another point. It will try again by raising and lowering the probe once again
	 *
	 * @param None
	 * @return None
	 */
	private void measureMode() {
		byte[] buffer = new byte[3];
		byte[] buffer2 = new byte[4];
		byte[] buffer3 = new byte[7];
		String[] lineEntry = new String[9];
		byte[] gcode;

		switch (this.measurestate) {

		case raise1:
			gcode = "G1 Z10 F3000\r\n".getBytes();
			try {
				this.scm.writeBytes(positioningHandle, gcode);
				this.measurestate = Measurestate.waitraise1;
				this.initialtime = System.nanoTime();
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
				measure = false;
			}
			break;

		case waitraise1:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 3000) {
				this.measurestate = Measurestate.home;
			}
			break;

		case home:
			gcode = "G28\r\n".getBytes();
			try {
				this.scm.writeBytes(positioningHandle, gcode);
				this.measurestate = Measurestate.waithome;
				this.initialtime = System.nanoTime();
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
				measure = false;
			}
			break;

		case waithome:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 30000) {
				this.measurestate = Measurestate.setunits;
			}
			break;

		case setunits:
			gcode = "G21\r\n".getBytes();
			try {
				this.scm.writeBytes(positioningHandle, gcode);
				this.measurestate = Measurestate.setabsolute;
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
				measure = false;
			}
			break;

		case setabsolute:
			gcode = "G90\r\n".getBytes();
			try {
				this.scm.writeBytes(positioningHandle, gcode);
				this.measurestate = Measurestate.raise2;
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
				measure = false;
			}
			break;

		case raise2:
			gcode = "G1 Z10 F3000\r\n".getBytes();
			try {
				this.scm.writeBytes(positioningHandle, gcode);
				this.measurestate = Measurestate.waitraise2;
				this.initialtime = System.nanoTime();
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
				measure = false;
			}
			break;

		case waitraise2:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 3000) {
				this.measurestate = Measurestate.center;
			}
			break;

		case center:
			gcode = String.format("G1 X%d Y%d Z10 F3000\r\n", this.xcenter, this.ycenter).getBytes();
			try {
				this.scm.writeBytes(positioningHandle, gcode);
				this.measurestate = Measurestate.waitcenter;
				this.initialtime = System.nanoTime();
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
				measure = false;
			}
			break;

		case waitcenter:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				this.measurestate = Measurestate.points;
				this.retryprobe = 0;
			}
			break;

		case points:
			this.voltagemeasuredf = 0;
			this.currentmeasuredf = 0;
			this.voltagemeasuredb = 0;
			this.currentmeasuredb = 0;
			if (this.waferShape == 1) {
				gcode = String
						.format("G1 X%f Y%f Z5 F3000\r\n",
								this.pointdata.get(this.currentpoint - 1)[0] - (this.waferDiameter / 2) + this.xcenter,
								this.pointdata.get(this.currentpoint - 1)[1] - (this.waferDiameter / 2) + this.ycenter)
						.getBytes();
			} else {
				gcode = String
						.format("G1 X%f Y%f Z5 F3000\r\n",
								this.pointdata.get(this.currentpoint - 1)[0] - (this.waferWidth / 2) + this.xcenter,
								this.pointdata.get(this.currentpoint - 1)[1] - (this.waferHeight / 2) + this.ycenter)
						.getBytes();
			}
			try {
				this.scm.writeBytes(positioningHandle, gcode);
				this.measurestate = Measurestate.waitpoints;
				this.initialtime = System.nanoTime();
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
				measure = false;
			}
			break;

		case waitpoints:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 1500) {
				this.measurestate = Measurestate.lower;
			}
			break;

		case lower:
			gcode = "G1 Z0 F3000\r\n".getBytes();
			try {
				this.scm.writeBytes(positioningHandle, gcode);
				this.measurestate = Measurestate.waitlower;
				this.initialtime = System.nanoTime();
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
				measure = false;
			}
			break;

		case waitlower:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 1500) {
				this.measurestate = Measurestate.enablecurrent;
				this.retry = 0;
			}
			break;

		case enablecurrent:
			this.packetrecv = false;
			buffer3[0] = (byte) 0x7E;
			buffer3[1] = (byte) 0x00;
			buffer3[2] = (byte) 0x00;
			buffer3[3] = (byte) 0x00;
			buffer3[4] = (byte) 0x00;
			buffer3[5] = (byte) 0x0A;
			buffer3[6] = (byte) 0x7E;
			try {
				this.scm.writeBytes(this.comPortHandle, buffer3);
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
			}
			this.initialtime = System.nanoTime();
			this.measurestate = Measurestate.waitenablecurrent1;
			break;

		case waitenablecurrent1:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				if (this.retry < 1) {
					this.retry++;
					this.measurestate = Measurestate.enablecurrent;
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot enable current. Communication Error with Arduino");
					measure = false;
					break;
				}
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] != (byte) 0xFC) {
					if (this.retry < 1) {
						this.retry++;
						this.measurestate = Measurestate.enablecurrent;
					} else {
						JOptionPane.showMessageDialog(this.text,
								"Cannot enable current. Communication Error with Arduino");
						measure = false;
					}
				} else {
					this.measurestate = Measurestate.waitenablecurrent2;
					this.initialtime = System.nanoTime();
				}
			}
			break;

		case waitenablecurrent2:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				JOptionPane.showMessageDialog(this.text,
						"Cannot enable current. Communication Error with Arduino");
				measure = false;
				break;

			}
			if (this.packetrecv) {
				this.packetrecv = false;
				this.measurestate = Measurestate.delayenablecurrent;
				this.initialtime = System.nanoTime();
			}
			break;
			
		case delayenablecurrent:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 500) {
				this.measurestate = Measurestate.checkprobe;
			}
			break;

		case checkprobe:
			this.packetrecv = false;
			buffer[0] = (byte) 0x7E;
			buffer[1] = (byte) 0x05;
			buffer[2] = (byte) 0x7E;
			try {
				this.scm.writeBytes(this.comPortHandle, buffer);
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
			}
			this.initialtime = System.nanoTime();
			this.measurestate = Measurestate.waitcheckprobe;
			break;

		case waitcheckprobe:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				if (this.retry < 1) {
					this.retry++;
					this.measurestate = Measurestate.checkprobe;
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot check probe. Communication Error with Arduino");
					measure = false;
					break;
				}
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] == (byte) 0xFC) {
					this.initialtime = System.nanoTime();
					this.measurestate = Measurestate.setcurrent;
					this.retry = 0;
				} else if (this.databuf[0] == (byte) 0xFD) {
					if (this.retryprobe < 2) {
						this.measurestate = Measurestate.disablecurrent1;
					} else {
						// point not detected. skip this point
						this.publish(String.format("Sample not detected. Skip this point\n"));
						this.measurestate = Measurestate.disablecurrent2;
						this.retry = 0;
						this.retryprobe = 0;
					}
				} else {
					if (this.retry < 1) {
						this.retry++;
						this.measurestate = Measurestate.checkprobe;
					} else {
						JOptionPane.showMessageDialog(this.text,
								"Cannot check probe. Communication Error with Arduino");
						measure = false;
					}
				}
			}
			break;

		case disablecurrent1:
			this.packetrecv = false;
			buffer3[0] = (byte) 0x7E;
			buffer3[1] = (byte) 0x00;
			buffer3[2] = (byte) 0x00;
			buffer3[3] = (byte) 0x00;
			buffer3[4] = (byte) 0x00;
			buffer3[5] = (byte) 0x00;
			buffer3[6] = (byte) 0x7E;
			try {
				this.scm.writeBytes(this.comPortHandle, buffer3);
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
			}
			this.initialtime = System.nanoTime();
			this.measurestate = Measurestate.waitdisablecurrent1;
			break;

		case waitdisablecurrent1:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				if (this.retry < 1) {
					this.retry++;
					this.measurestate = Measurestate.disablecurrent1;
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot disable current. Communication Error with Arduino");
					measure = false;
					break;
				}
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] != (byte) 0xFC) {
					if (this.retry < 1) {
						this.retry++;
						this.measurestate = Measurestate.disablecurrent1;
					} else {
						JOptionPane.showMessageDialog(this.text,
								"Cannot disable current. Communication Error with Arduino");
						measure = false;
					}
				} else {
					this.initialtime = System.nanoTime();
					this.measurestate = Measurestate.delaydisablecurrent1;
				}
			}
			break;

		case wait2disablecurrent1:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				JOptionPane.showMessageDialog(this.text,
						"Cannot disable current. Communication Error with Arduino");
				measure = false;
				break;

			}
			if (this.packetrecv) {
				this.packetrecv = false;
				this.measurestate = Measurestate.delaydisablecurrent1;
			}
			break;
			
		case delaydisablecurrent1:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 500) {
				this.measurestate = Measurestate.points;
				this.retryprobe++;
			}
			break;
			
		case setcurrent:
			this.packetrecv = false;
			if (this.current == 0) {
				buffer[0] = 0x7E;
				buffer[1] = 0x01;
				buffer[2] = 0x7E;
				try {
					this.scm.writeBytes(this.comPortHandle, buffer);
				} catch (SerialComException e2) {
					JOptionPane.showMessageDialog(this.text, e2);
				}
			} else {
				buffer3[0] = (byte) 0x7E;
				buffer3[1] = (byte) 0x00;
				buffer3[2] = (byte) (this.current >> 24);
				buffer3[3] = (byte) (this.current >> 16);
				buffer3[4] = (byte) (this.current >> 8);
				buffer3[5] = (byte) this.current;
				buffer3[6] = (byte) 0x7E;
				try {
					this.scm.writeBytes(this.comPortHandle, buffer3);
				} catch (SerialComException e2) {
					JOptionPane.showMessageDialog(this.text, e2);
				}
			}
			this.initialtime = System.nanoTime();
			this.measurestate = Measurestate.waitsetcurrent1;
			break;

		case waitsetcurrent1:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 10000) {
				if (this.retry < 1) {
					this.retry++;
					this.measurestate = Measurestate.setcurrent;
				} else {
					JOptionPane.showMessageDialog(this.text, "Cannot Set Current. Communication Error with Arduino");
					measure = false;
					break;
				}
			}
			if (this.packetrecv) {
				this.packetrecv = false;	
				if (this.databuf[0] == (byte) 0xFC) {
					this.measurestate = Measurestate.waitsetcurrent2;
					this.initialtime = System.nanoTime();
				} else if (this.databuf[0] == (byte) 0xFD) {
					if(this.current == 0) {
						if (this.retryprobe < 2) {
							this.measurestate = Measurestate.disablecurrent1;
						} else {
							// point not detected. skip this point
							this.publish(String.format("Sample not detected. Skip this point\n"));
							this.measurestate = Measurestate.disablecurrent2;
							this.retry = 0;
							this.retryprobe = 0;
						}
					}
					else {
						JOptionPane.showMessageDialog(this.text,
								"Cannot Set Current. Communication Error with Arduino");
						measure = false;
					}
				} else {
					if (this.retry < 1) {
						this.retry++;
						this.measurestate = Measurestate.setcurrent;
					} else {
						JOptionPane.showMessageDialog(this.text,
								"Cannot Set Current. Communication Error with Arduino");
						measure = false;
					}
				}
			}
			break;

		case waitsetcurrent2:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 1000) {
				JOptionPane.showMessageDialog(this.text, "Cannot Set Current. Communication Error with Arduino");
				measure = false;
				break;
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] == (byte) 0xFB) {
					if (this.current == 0) {
						byte[] t = { this.databuf[1], this.databuf[2], this.databuf[3], this.databuf[4] };
						this.current = (int)ByteBuffer.wrap(t).order(ByteOrder.LITTLE_ENDIAN).getFloat();
					}
					this.measurestate = Measurestate.delaysetcurrent;
					this.initialtime = System.nanoTime();
				} 
				else if(this.databuf[0] == (byte) 0xFD) {  // means sample not detected
					this.measurestate = Measurestate.delaysetcurrent;
					this.initialtime = System.nanoTime();
				}
				else {
					JOptionPane.showMessageDialog(this.text, "Cannot Set Current. Communication Error with Arduino");
					measure = false;
				}
			}
			break;

		case delaysetcurrent:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 1) {
				this.measurestate = Measurestate.forward;
			}
			break;

		case forward:
			this.packetrecv = false;
			buffer2[0] = (byte) 0x7E;
			buffer2[1] = (byte) 0x02;
			buffer2[2] = (byte) 0x00;
			buffer2[3] = (byte) 0x7E;
			try {
				this.scm.writeBytes(this.comPortHandle, buffer2);
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
			}
			this.initialtime = System.nanoTime();
			this.measurestate = Measurestate.waitforward;
			break;

		case waitforward:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				if (this.retry < 1) {
					this.retry++;
					this.measurestate = Measurestate.forward;
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot switch forward. Communication Error with Arduino");
					measure = false;
					break;
				}
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] == (byte) 0xFC) {
					this.measurestate = Measurestate.waitforward2;
					this.initialtime = System.nanoTime();
				} else {
					if (this.retry < 1) {
						this.retry++;
						this.measurestate = Measurestate.forward;
					} else {
						JOptionPane.showMessageDialog(this.text,
								"Cannot switch forward. Communication Error with Arduino");
						measure = false;
					}
				}
			}
			break;

		case waitforward2:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 1000) {
				this.measurestate = Measurestate.measure1;
				this.retry = 0;
			}
			break;

		case measure1:
			this.packetrecv = false;
			buffer[0] = (byte) 0x7E;
			buffer[1] = (byte) 0x04;
			buffer[2] = (byte) 0x7E;
			try {
				this.scm.writeBytes(this.comPortHandle, buffer);
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
			}
			this.initialtime = System.nanoTime();
			this.measurestate = Measurestate.waitmeasure1;
			break;

		case waitmeasure1:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 10000) {
				if (this.retry < 1) {
					this.retry++;
					this.measurestate = Measurestate.measure1;
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot perform measurement. Communication Error with Arduino");
					measure = false;
					break;
				}
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] == (byte) 0xFC) {
					this.measurestate = Measurestate.waitvoltage1;
					this.initialtime = System.nanoTime();
				} else if (this.databuf[0] == (byte) 0xFD) {
					if (this.retryprobe < 2) {
						this.measurestate = Measurestate.disablecurrent1;
					} else {
						// point not detected. skip this point
						this.publish(String.format("Sample not detected. Skip this point\n"));
						this.measurestate = Measurestate.disablecurrent2;
						this.retry = 0;
						this.retryprobe = 0;
					}
				} else {
					if (this.retry < 1) {
						this.retry++;
						this.measurestate = Measurestate.measure1;
					} else {
						JOptionPane.showMessageDialog(this.text,
								"Cannot perform measurement. Communication Error with Arduino");
						measure = false;
					}
				}
			}
			break;

		case waitvoltage1:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				JOptionPane.showMessageDialog(this.text,
						"Cannot perform measurement. Communication Error with Arduino");
				measure = false;
				break;
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] == (byte) 0xFA) {
					byte[] t = { this.databuf[1], this.databuf[2], this.databuf[3], this.databuf[4] };

					this.voltagemeasuredf = ByteBuffer.wrap(t).order(ByteOrder.LITTLE_ENDIAN).getFloat();
					if (this.voltagemeasuredf > 1000000) {
						JOptionPane.showMessageDialog(this.text,
								"Input voltage range of ADC exceeded. Please reduce current");
						measure = false;
						break;
					}
					this.measurestate = Measurestate.waitcurrent1;
					this.initialtime = System.nanoTime();
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot perform measurement. Communication Error with Arduino");
					measure = false;
				}
			}
			break;

		case waitcurrent1:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				JOptionPane.showMessageDialog(this.text,
						"Cannot perform measurement. Communication Error with Arduino");
				measure = false;
				break;
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] == (byte) 0xFB) {
					byte[] t = { this.databuf[1], this.databuf[2], this.databuf[3], this.databuf[4] };
					this.currentmeasuredf = ByteBuffer.wrap(t).order(ByteOrder.LITTLE_ENDIAN).getFloat();
					this.measurestate = Measurestate.reverse;
					this.retry = 0;
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot perform measurement. Communication Error with Arduino");
					measure = false;
				}
			}
			break;

		case reverse:
			this.packetrecv = false;
			buffer2[0] = (byte) 0x7E;
			buffer2[1] = (byte) 0x02;
			buffer2[2] = (byte) 0x01;
			buffer2[3] = (byte) 0x7E;
			try {
				this.scm.writeBytes(this.comPortHandle, buffer2);
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
			}
			this.initialtime = System.nanoTime();
			this.measurestate = Measurestate.waitreverse;
			break;

		case waitreverse:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				if (this.retry < 1) {
					this.retry++;
					this.measurestate = Measurestate.reverse;
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot switch reverse. Communication Error with Arduino");
					measure = false;
					break;
				}
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] == (byte) 0xFC) {
					this.measurestate = Measurestate.waitreverse2;
					this.initialtime = System.nanoTime();
				} else {
					if (this.retry < 1) {
						this.retry++;
						this.measurestate = Measurestate.reverse;
					} else {
						JOptionPane.showMessageDialog(this.text,
								"Cannot switch reverse. Communication Error with Arduino");
						measure = false;
					}
				}
			}
			break;

		case waitreverse2:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 1000) {
				this.measurestate = Measurestate.measure2;
				this.retry = 0;
			}
			break;

		case measure2:
			this.packetrecv = false;
			buffer[0] = (byte) 0x7E;
			buffer[1] = (byte) 0x04;
			buffer[2] = (byte) 0x7E;
			try {
				this.scm.writeBytes(this.comPortHandle, buffer);
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
			}
			this.initialtime = System.nanoTime();
			this.measurestate = Measurestate.waitmeasure2;
			break;

		case waitmeasure2:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 10000) {
				if (this.retry < 1) {
					this.retry++;
					this.measurestate = Measurestate.measure2;
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot perform measurement. Communication Error with Arduino");
					measure = false;
					break;
				}
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] == (byte) 0xFC) {
					this.measurestate = Measurestate.waitvoltage2;
					this.initialtime = System.nanoTime();
				} else if (this.databuf[0] == (byte) 0xFD) {
					if (this.retryprobe < 2) {
						this.measurestate = Measurestate.disablecurrent1;
					} else {
						// point not detected. skip this point
						this.publish(String.format("Sample not detected. Skip this point\n"));
						this.measurestate = Measurestate.disablecurrent2;
						this.retry = 0;
						this.retryprobe = 0;
					}
				} else {
					if (this.retry < 1) {
						this.retry++;
						this.measurestate = Measurestate.measure2;
					} else {
						JOptionPane.showMessageDialog(this.text,
								"Cannot perform measurement. Communication Error with Arduino");
						measure = false;
					}
				}
			}
			break;

		case waitvoltage2:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				JOptionPane.showMessageDialog(this.text,
						"Cannot perform measurement. Communication Error with Arduino");
				measure = false;
				break;
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] == (byte) 0xFA) {
					byte[] t = { this.databuf[1], this.databuf[2], this.databuf[3], this.databuf[4] };
					this.voltagemeasuredb = ByteBuffer.wrap(t).order(ByteOrder.LITTLE_ENDIAN).getFloat();
					if (this.voltagemeasuredb > 1000000) {
						JOptionPane.showMessageDialog(this.text,
								"Input voltage range of ADC exceeded. Please reduce current");
						measure = false;
						break;
					}
					this.measurestate = Measurestate.waitcurrent2;
					this.initialtime = System.nanoTime();
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot perform measurement. Communication Error with Arduino");
					measure = false;
				}
			}
			break;

		case waitcurrent2:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				JOptionPane.showMessageDialog(this.text,
						"Cannot perform measurement. Communication Error with Arduino");
				measure = false;
				break;
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] == (byte) 0xFB) {
					byte[] t = { this.databuf[1], this.databuf[2], this.databuf[3], this.databuf[4] };
					this.currentmeasuredb = ByteBuffer.wrap(t).order(ByteOrder.LITTLE_ENDIAN).getFloat();
					if ((Math.abs(this.voltagemeasuredb - this.voltagemeasuredf)
							/ Math.min(this.voltagemeasuredf, this.voltagemeasuredb)) > 0.1) {
						if (this.retryprobe < 1) {
							this.publish(String
									.format("Too much difference between forward and reverse current, trying again\n"));
							this.initialtime = System.nanoTime();
							this.retry = 0;
							this.measurestate = Measurestate.disablecurrent1;
						} else {
							this.measurestate = Measurestate.store;
							this.retryprobe = 0;
							this.initialtime = System.nanoTime();
						}
					} else {
						this.measurestate = Measurestate.store;
					}
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot perform measurement. Communication Error with Arduino");
					measure = false;
				}
			}
			break;

		case store:
			this.resf = (this.voltagemeasuredf / this.currentmeasuredf) * (float) 4.532 * 1000;
			this.resb = (this.voltagemeasuredb / this.currentmeasuredb) * (float) 4.532 * 1000;
			this.publish(String.format("Forward sheet resistance at point %d is %.2f ohm/sq\n", this.currentpoint,
					this.resf));
			this.publish(String.format("Reverse sheet resistance at point %d is %.2f ohm/sq\n", this.currentpoint,
					this.resb));

			lineEntry[0] = Integer.toString(this.currentpoint);
			lineEntry[1] = String.format("%.2f", this.pointdata.get(this.currentpoint - 1)[0]);
			lineEntry[2] = String.format("%.2f", this.pointdata.get(this.currentpoint - 1)[1]);
			lineEntry[3] = String.format("%.0f", this.voltagemeasuredf);
			lineEntry[4] = String.format("%.0f", this.currentmeasuredf);
			lineEntry[5] = String.format("%.2f", this.resf);
			lineEntry[6] = String.format("%.0f", this.voltagemeasuredb);
			lineEntry[7] = String.format("%.0f", this.currentmeasuredb);
			lineEntry[8] = String.format("%.2f", this.resb);
			try {
				this.writer.writeNext(lineEntry);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this.text, e);
				measure = false;
			}
			this.measurestate = Measurestate.disablecurrent2;
			break;

		case disablecurrent2:
			this.packetrecv = false;
			buffer3[0] = (byte) 0x7E;
			buffer3[1] = (byte) 0x00;
			buffer3[2] = (byte) 0x00;
			buffer3[3] = (byte) 0x00;
			buffer3[4] = (byte) 0x00;
			buffer3[5] = (byte) 0x00;
			buffer3[6] = (byte) 0x7E;
			try {
				this.scm.writeBytes(this.comPortHandle, buffer3);
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
			}
			this.initialtime = System.nanoTime();
			this.measurestate = Measurestate.waitdisablecurrent2;
			break;

		case waitdisablecurrent2:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 5000) {
				if (this.retry < 1) {
					this.retry++;
					this.measurestate = Measurestate.disablecurrent2;
				} else {
					JOptionPane.showMessageDialog(this.text,
							"Cannot disable current. Communication Error with Arduino");
					measure = false;
					break;
				}
			}
			if (this.packetrecv) {
				this.packetrecv = false;
				if (this.databuf[0] != (byte) 0xFC) {
					if (this.retry < 1) {
						this.retry++;
						this.measurestate = Measurestate.disablecurrent2;
					} else {
						JOptionPane.showMessageDialog(this.text,
								"Cannot disable current. Communication Error with Arduino");
						measure = false;
					}
				} else {
					this.initialtime = System.nanoTime();
					this.measurestate = Measurestate.delaydisablecurrent2;
				}
			}
			break;

		case delaydisablecurrent2:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 500) {
				this.measurestate = Measurestate.raise3;
				this.retry = 0;
			}
			break;

		case raise3:
			gcode = "G1 Z5 F3000\r\n".getBytes();
			try {
				this.scm.writeBytes(positioningHandle, gcode);
				this.measurestate = Measurestate.waitraise3;
				this.initialtime = System.nanoTime();
			} catch (SerialComException e2) {
				JOptionPane.showMessageDialog(this.text, e2);
				measure = false;
			}
			break;

		case waitraise3:
			this.currenttime = System.nanoTime();
			if (this.currenttime >= this.initialtime) {
				this.elapsedtime = this.currenttime - this.initialtime;
			} else {
				this.elapsedtime = this.currenttime + (Long.MAX_VALUE - this.initialtime);
			}
			if (TimeUnit.NANOSECONDS.toMillis(this.elapsedtime) > 500) {
				if (this.currentpoint < this.numpoints) {
					this.currentpoint++;
					this.measurestate = Measurestate.points;
					this.retryprobe = 0;
				} else {
					JOptionPane.showMessageDialog(this.text, "Measurement Finished");
					measure = false;
				}
			}
			break;

		default:
			break;
		}
	}

	/**
	 * Function to check the data received from measurement circuit for framing error
	 * <p>
	 * This function will only process one byte at a time. It is intended to be called continuously
	 * until it returns 0
	 * Uses Serial Protocol Framing from Eli Bendersky
	 * (http://eli.thegreenplace.net/2009/08/12/framing-in-serial-communications/)
	 *
	 * @param serialdata a 
	 * @return 0 if valid packet received, 1 if framing error, 2 means packet still in processing
	 */
	private int CheckSerialData(byte serialdata) {

		switch (this.stateread) {
		case 0: // wait_header
			if (serialdata == (byte) 0x7E) {
				this.stateread = 2;
			} else if (serialdata == (byte) 0x7D) {
				this.stateread = 1;
				this.publish(String.format("%c", serialdata));
			} else {
				this.publish(String.format("%c", serialdata));
			}
			break;
		case 1: // ignore_next
			this.stateread = 0;
			this.publish(String.format("%c", serialdata));
			break;
		case 2: // in_msg
			if (serialdata == (byte) 0x7E) {
				this.stateread = 0;
				this.dataptr = 0;
				return 0;
			} else if (serialdata == (byte) 0x7D) {
				this.stateread = 3;
			} else {
				if (this.dataptr < this.databuf.length) {
					this.databuf[this.dataptr] = serialdata;
					this.dataptr++;
				} else {
					this.stateread = 0;
					this.dataptr = 0;
					return 1; // shouldn't be more than databuf.length
								// characters
				}
			}
			break;
		case 3: // after_esc
			if ((serialdata == (byte) 0x7E) | (serialdata == (byte) 0x7D)) {
				if (this.dataptr < this.databuf.length) {
					this.stateread = 2;
					this.databuf[this.dataptr] = serialdata;
					this.dataptr++;
				} else {
					this.stateread = 0;
					this.dataptr = 0;
					return 1; // shouldn't be more than databuf.length
								// characters
				}
			} else {
				this.stateread = 0;
				this.dataptr = 0;
				return 1;
			}
			break;
		default:
			break;
		}

		return 2;
	}

	/**
	 * Background process that run continuously
	 * <p>
	 * continuously pool from measurement circuit serial port for data.
	 * Also will call measurement state machine when mode == 1.
	 * Also check whether measurement circuit and positioning system still connected or not.
	 * 
	 * @param None
	 * @return Null
	 */
	@Override
	protected Integer doInBackground() {

		byte[] dataRead;

		while (true) {
			if (exit == true) {
				return null;
			}
			// if measure is false, then somewhere in the code is signaling to
			// stop the measurement process
			// then stop the measurement process
			if (measure == false) {
				this.packetrecv = false;
				if (this.mode != 0) {
					this.mode = 0;
					this.measurestate = Measurestate.raise1;
					this.firePropertyChange(null, null, null);
					
					if(this.raiseonce == true) {
						this.raiseonce = false;
						
						// Disable current source and wait for 200 ms
						byte[] buffer3 = new byte[7];
						
						buffer3[0] = (byte) 0x7E;
						buffer3[1] = (byte) 0x00;
						buffer3[2] = (byte) 0x00;
						buffer3[3] = (byte) 0x00;
						buffer3[4] = (byte) 0x00;
						buffer3[5] = (byte) 0x00;
						buffer3[6] = (byte) 0x7E;
						try {
							this.scm.writeBytes(this.comPortHandle, buffer3);
						} catch (SerialComException e2) {
							JOptionPane.showMessageDialog(this.text, e2);
						}
						try {
							TimeUnit.MILLISECONDS.sleep(200);
						} catch (InterruptedException e1) {
							JOptionPane.showMessageDialog(this.text, e1);
						}
						
						// raise the probe after measurement to make it easier to remove the sample
						try {
							byte[] gcode = "G1 Z20 F3000\r\n".getBytes();
							this.scm.writeBytes(positioningHandle, gcode);
						} catch (Exception e) {
							JOptionPane.showMessageDialog(this.text, e);
						}
					}
				}
			}
			// pool serial data from measurement circuit.
			// if exception occurs, means usb cable unplugged. exit this
			// swingworker
			try {
				if(this.packetrecv == false) {   // avoid overwriting old packet
					dataRead = this.scm.readBytes(this.comPortHandle, 1);
					if (dataRead != null) {
						if (CheckSerialData(dataRead[0]) == 0) {
							this.packetrecv = true;
						}
					}
				}
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this.text, e);
				return null;
			}
			// checks if positioning system is still connected or not. If not,
			// close the port.
			if (positioningHandle != -1) {
				try {
					int[] stat = this.scm.getLinesStatus(positioningHandle);
				} catch (Exception e) {
					JOptionPane.showMessageDialog(this.text, e);
					closePositioningSystem();
				}
			}
			if (this.mode == 0) {
				if (this.writer != null) {
					try {
						this.writer.close();
						this.writer = null;
					} catch (Exception e) {
						JOptionPane.showMessageDialog(this.text, e);
					}
				}
			} else {
				measureMode();
			}
		}
	}

	/**
	 * This function is called when this.publish(String) is called and 
	 * The String will be passed to this function.
	 * This function will add text to the textArea on GUI
	 *
	 * @param None
	 * @return None
	 */
	@Override
	protected void process(List<String> chunks) {
		for (String str : chunks) {
			this.text.append(str);
		}
	}

	/**
	 * Function is called when the SwingWorker is terminated.
	 * <p>
	 * Will attempt to close the positioning system and writer to CSV file before closing.
	 *
	 * @param None
	 * @return None
	 */
	@Override
	protected void done() {
		try {
			if (positioningHandle != -1) {
				closePositioningSystem();
			}
			this.scm.closeComPort(this.comPortHandle);
			if (this.writer != null) {
				this.writer.close();
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this.text, e);
		}
	}
}

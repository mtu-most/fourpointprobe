package OS4PP;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;

import javax.swing.JPanel;

/**
 * @author Handy Chandra Spencer Allen
 * @version 2.0
 * @since 1.0
 * 
 *        OS4PP GUI version 2 based on previous version made by Spencer Allen
 *        Uses SerialPundit library from RishiGupta12
 *        (https://github.com/RishiGupta12/SerialPundit) Uses Serial Protocol
 *        Framing from Eli Bendersky
 *        (http://eli.thegreenplace.net/2009/08/12/framing-in-serial-
 *        communications/)
 *
 *        This class provides function to draw sample image on black space
 *        provided in the GUI
 */
public class MyPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private int shape = 0;
	private int dia;
	private int width;
	private int height;
	private ArrayList<float[]> points;

	/**
	 * Method for drawing on the panel
	 * <p>
	 * This method is called by java automatically whenever it needs to redraw
	 * the panel. Or could also be invoked manually with this.repaint() method
	 * <p>
	 * Draws circle or rectangle to display wafer shape if shape variable not
	 * zero. shape == 1 will draw circle and shape == 2 will draw rectangle.
	 * Will also draw points on the canvas if points this.drawPoints() called
	 * beforehand
	 *
	 * @param g Graphics object
	 * @return None
	 */
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (this.shape == 1) {
			g.setColor(Color.white);
			g.drawOval(5, 5, this.getWidth() - 10, this.getHeight() - 10);
			if (this.points != null) {
				float x, y;
				g.setColor(Color.green);
				for (float[] tmp : this.points) {
					x = (((float) this.getWidth() - 10) / this.dia) * tmp[0];
					y = (((float) this.getHeight() - 10) / this.dia) * tmp[1];
					g.drawOval((int) x + 5, (int) y + 5, 1, 1);
				}
			}
		} else if (this.shape == 2) {
			g.setColor(Color.white);
			if (this.width >= this.height) {
				float y1 = ((this.getHeight() - 10) - ((float) this.height / this.width * (this.getWidth() - 10))) / 2;
				float y2 = (float) this.height / this.width * (this.getWidth() - 10);
				g.drawRect(5, (int) y1 + 5, this.getWidth() - 10, (int) y2);
				if (this.points != null) {
					float x, y;
					g.setColor(Color.green);
					for (float[] tmp : this.points) {
						x = (((float) this.getWidth() - 10) / this.width) * tmp[0];
						y = ((y2 / this.height) * tmp[1]) + y1;
						g.drawOval((int) x + 5, (int) y + 5, 1, 1);
					}
				}
			} else {
				float x1 = ((this.getWidth() - 10) - ((float) this.width / this.height * (this.getHeight() - 10))) / 2;
				float x2 = (float) this.width / this.height * (this.getHeight() - 10);
				g.drawRect((int) x1 + 5, 5, (int) x2, this.getHeight() - 10);
				if (this.points != null) {
					float x, y;
					g.setColor(Color.green);
					for (float[] tmp : this.points) {
						x = ((x2 / this.width) * tmp[0]) + x1;
						y = (((float) this.getHeight() - 10) / this.height) * tmp[1];
						g.drawOval((int) x + 5, (int) y + 5, 1, 1);
					}
				}
			}
		}
	}

	/**
	 * Method to draw circle on the canvas
	 * <p>
	 * This method would set variable so that paintComponent() method will draw
	 * a circle on the canvas
	 *
	 * @param size real diameter of the circle in mm
	 * @return None
	 */
	public void drawCircle(int size) {
		this.shape = 1;
		this.dia = size;
		this.repaint();
	}

	/**
	 * Method to draw rectangle on the canvas
	 * <p>
	 * This method would set variable so that paintComponent() method will draw
	 * a rectangle on the canvas
	 *
	 * @param dim1 real width of the rectangle in mm
	 * @param dim2 real heigth of the rectangle in mm
	 * @return None
	 */
	public void drawRectangular(int dim1, int dim2) {
		this.shape = 2;
		this.width = dim1;
		this.height = dim2;
		this.repaint();
	}

	/**
	 * Draw Points on the canvas
	 * <p>
	 * This method would set variable so that paintComponent() method will draw
	 * points on the canvas
	 *
	 * @param realpoints ArrayList of float[2] containing x and y coordinate of each point on the wafer
	 * @return None
	 */
	public void drawPoints(ArrayList<float[]> realpoints) {
		this.points = realpoints;
		this.repaint();
	}
}

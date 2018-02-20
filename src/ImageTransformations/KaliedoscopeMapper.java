package ImageTransformations;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import pixeljelly.utilities.InverseMapper;

public class KaliedoscopeMapper extends InverseMapper {

	private int centerX, centerY;
	private int angle, slices;

	public KaliedoscopeMapper(int angle, int slices) {
		this.angle = angle;
		this.slices = slices;
	}

	public void initializeMapping(BufferedImage src) {
		centerX = (src.getWidth() - 1) / 2;
		centerY = (src.getHeight() - 1) / 2;
	}

	@Override
	public Point2D inverseTransform(Point2D destPt, Point2D srcPt) {
		if (srcPt == null) {
			srcPt = new Point2D.Double();
		}
		double angle = (double) ((this.angle % 359) / 359.0);
		double dx = destPt.getX() - centerX;
		double dy = destPt.getY() - centerY;
		double fieldWidth = 2 * Math.PI / slices;
		double r = Math.sqrt(dx * dx + dy * dy);
		double theta = ((Math.atan2(dy, dx) + 2 * Math.PI) / fieldWidth) % 1.0;
		double location = fieldWidth * (theta < 0.5 ? 1 - theta : theta);
		double srcX = r * Math.cos(location + angle) + centerX;
		double srcY = r * Math.sin(location + angle) + centerY;
		srcPt.setLocation(srcX, srcY);
		return srcPt;
	}
}

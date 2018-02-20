package ImageTransformations;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import pixeljelly.utilities.InverseMapper;

public class FishLensMapper extends InverseMapper {

	private int centerX, centerY;
	private double focalLength, weight;
	private boolean isInverted;

	public FishLensMapper(double weight, boolean isInverted) {
		this.weight = weight;
		this.isInverted = isInverted;
	}

	public void initializeMapping(BufferedImage src) {
		centerX = (src.getWidth() - 1) / 2;
		centerY = (src.getHeight() - 1) / 2;
		focalLength = Math.max(src.getHeight(), src.getWidth()) / 2.0;
	}

	@Override
	public Point2D inverseTransform(Point2D destPt, Point2D srcPt) {
		if (srcPt == null) {
			srcPt = new Point2D.Double();
		}
		double radius;

		double dx = destPt.getX() - centerX;
		double dy = destPt.getY() - centerY;
		double r = Math.sqrt(dx * dx + dy * dy);
		double t = Math.atan2(dy, dx);

		double scale = focalLength / Math.log(weight * focalLength + 1);

		if (r >= focalLength) {
			radius = r;
		} else if (r < focalLength && isInverted) {
			radius = scale * Math.log(weight * r + 1);
		} else {
			radius = (Math.exp(r / scale) - 1) / weight;
		}

		double srcX = Math.cos(t) * radius + centerX;
		double srcY = Math.sin(t) * radius + centerY;
		srcPt.setLocation(srcX, srcY);

		return srcPt;
	}

}

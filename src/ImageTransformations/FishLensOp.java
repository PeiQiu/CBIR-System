package ImageTransformations;

import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;

import java.awt.geom.Point2D;
import pixeljelly.ops.NullOp;
import pixeljelly.ops.PluggableImageOp;
import pixeljelly.scanners.Location;
import pixeljelly.scanners.RasterScanner;
import pixeljelly.utilities.BilinearInterpolant;
import pixeljelly.utilities.ImagePadder;
import pixeljelly.utilities.Interpolant;
import pixeljelly.utilities.InverseMapper;
import pixeljelly.utilities.ReflectivePadder;

public class FishLensOp extends NullOp implements PluggableImageOp {

	private Interpolant interpolant = new BilinearInterpolant();
	private InverseMapper mapper;
	private ImagePadder handler = ReflectivePadder.getInstance();
	private double weight;
	private boolean isInverted;

	public FishLensOp(double weight, boolean isInverted) {
		this.weight = weight;
		this.isInverted = isInverted;
		mapper = new FishLensMapper(this.weight, this.isInverted);
	}

	public FishLensOp() {
		this.weight = 5;
		this.isInverted = false;
		mapper = new FishLensMapper(weight, isInverted);
	}

	public double getWeight() {
		return weight;
	}

	public void setWeight(double weight) {
		this.weight = weight;
	}

	public boolean isInverted() {
		return isInverted;
	}

	public void setInverted(boolean isInverted) {
		this.isInverted = isInverted;
	}

	@Override
	public String getAuthorName() {
		return "Peiqiu Tian";
	}

	@Override
	public BufferedImageOp getDefault(BufferedImage arg0) {
		return new FishLensOp(5, false);
	}

	public BufferedImage filter(BufferedImage src, BufferedImage dest) {
		if (dest == null) {
			dest = createCompatibleDestImage(src, src.getColorModel());
		}

		mapper.initializeMapping(dest);

		Point2D destPt = new Point2D.Double();
		Point2D srcPt = new Point2D.Double();

		for (Location pt : new RasterScanner(dest, false)) {
			destPt.setLocation(pt.col, pt.row);
			mapper.inverseTransform(destPt, srcPt);
			for (int b = 0; b < src.getRaster().getNumBands(); b++) {
				int sample = interpolant.interpolate(src, handler, srcPt, b);
				dest.getRaster().setSample(pt.col, pt.row, b, sample);
			}
		}
		return dest;
	}

}

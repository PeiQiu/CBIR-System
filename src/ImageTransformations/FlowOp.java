package ImageTransformations;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;

import pixeljelly.ops.NullOp;
import pixeljelly.ops.PluggableImageOp;
import pixeljelly.scanners.Location;
import pixeljelly.scanners.RasterScanner;
import pixeljelly.utilities.BilinearInterpolant;
import pixeljelly.utilities.Interpolant;
import pixeljelly.utilities.InverseMapper;
import pixeljelly.utilities.ReflectivePadder;

public class FlowOp extends NullOp implements PluggableImageOp {

	private double strength;
	private boolean isPerpendicular;
	private InverseMapper mapper;
	private Interpolant interpolant = new BilinearInterpolant();
	private ReflectivePadder padder = ReflectivePadder.getInstance();

	public FlowOp() {
		this.strength = 5;
		this.isPerpendicular = false;
		mapper = new FlowMapper(this.strength, this.isPerpendicular);
	}

	public FlowOp(double strength, boolean isPerpendicular) {
		this.isPerpendicular = isPerpendicular;
		this.strength = strength;
		mapper = new FlowMapper(this.strength, this.isPerpendicular);
	}

	public boolean getIsPerpendicular() {
		return this.isPerpendicular;
	}

	public double getStrength() {
		return this.strength;
	}

	@Override
	public String getAuthorName() {
		return "Peiqiu Tian";
	}

	@Override
	public BufferedImageOp getDefault(BufferedImage arg0) {
		return new FlowOp(5, false);
	}

	public BufferedImage filter(BufferedImage src, BufferedImage dest) {
		if (dest == null) {
			dest = createCompatibleDestImage(src, src.getColorModel());
		}
		mapper.initializeMapping(src);
		Point2D srcpt = new Point2D.Double();
		Point2D destpt = new Point2D.Double();
		for (Location pt : new RasterScanner(src, false)) {
			destpt.setLocation(pt.col, pt.row);
			mapper.inverseTransform(destpt, srcpt);
			for (int b = 0; b < src.getRaster().getNumBands(); b++) {
				int sample = interpolant.interpolate(src, padder, srcpt, b);
				dest.getRaster().setSample(pt.col, pt.row, b, sample);
			}
		}
		return dest;
	}

}

package ImageTransformations;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;

import pixeljelly.ops.NullOp;
import pixeljelly.ops.PluggableImageOp;
import pixeljelly.scanners.Location;
import pixeljelly.scanners.RasterScanner;
import pixeljelly.utilities.BilinearInterpolant;
import pixeljelly.utilities.ImagePadder;
import pixeljelly.utilities.Interpolant;
import pixeljelly.utilities.InverseMapper;
import pixeljelly.utilities.ReflectivePadder;

public class KaliedoscopeOp extends NullOp implements PluggableImageOp {

	private int angle;
	private int slices;
	private ImagePadder handler = ReflectivePadder.getInstance();
	private Interpolant interpolant = new BilinearInterpolant();
	private InverseMapper mapper;

	public KaliedoscopeOp(int angle, int slices) {
		this.angle = angle;
		this.slices = slices;
		mapper = new KaliedoscopeMapper(this.angle, this.slices);
	}

	public KaliedoscopeOp() {
		this.angle = 30;
		this.slices = 5;
		mapper = new KaliedoscopeMapper(this.angle, this.slices);
	}

	public int getAngle() {
		return angle;
	}

	public void setAngle(int angle) {
		this.angle = angle;
	}

	public int getSlices() {
		return slices;
	}

	public void setSlices(int slices) {
		this.slices = slices;
	}

	@Override
	public String getAuthorName() {
		return "Peiqiu Tian";
	}

	@Override
	public BufferedImageOp getDefault(BufferedImage arg0) {
		return new KaliedoscopeOp(30, 5);
	}

	public BufferedImage filter(BufferedImage src, BufferedImage dest) {
		if (dest == null) {
			dest = createCompatibleDestImage(src, src.getColorModel());
		}
		mapper.initializeMapping(src);
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

package ImageTransformations;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import pixeljelly.ops.BrightnessBandExtractOp;
import pixeljelly.ops.ConvolutionOp;
import pixeljelly.utilities.BilinearInterpolant;
import pixeljelly.utilities.Interpolant;
import pixeljelly.utilities.InverseMapper;
import pixeljelly.utilities.NonSeperableKernel;
import pixeljelly.utilities.ReflectivePadder;

public class FlowMapper extends InverseMapper {

	private Interpolant interpolant = new BilinearInterpolant();
	private ReflectivePadder padder = ReflectivePadder.getInstance();
	private BufferedImage horizitalImage;
	private BufferedImage verticalImage;
	private double strength;
	private boolean isPerpendicular;
	private NonSeperableKernel kernel = new NonSeperableKernel(5, 5, getValue());

	private float[] getValue() {
		float[] value = new float[25];
		for (int i = 0; i < value.length; i++) {
			value[i] = 1.0f / 25.0f;
		}
		return value;
	}

	public FlowMapper(double strength, boolean isPerpendicular) {
		this.strength = strength;
		this.isPerpendicular = isPerpendicular;
	}

	public void initializeMapping(BufferedImage src) {
		BufferedImage bandofbrightness = new BrightnessBandExtractOp().filter(src, null);
		BufferedImage brightnessband = new ConvolutionOp(this.kernel, true).filter(bandofbrightness, null);

		NonSeperableKernel rowV = new NonSeperableKernel(3, 1, new float[] { 1, 0, -1 });
		NonSeperableKernel colV = new NonSeperableKernel(1, 3, new float[] { 1, 0, -1 });

		ConvolutionOp horizontalop = new ConvolutionOp(rowV, 128);
		ConvolutionOp verticalop = new ConvolutionOp(colV, 128);

		this.verticalImage = verticalop.filter(brightnessband, null);
		this.horizitalImage = horizontalop.filter(brightnessband, null);
	}

	@Override
	public Point2D inverseTransform(Point2D destPt, Point2D srcPt) {
		if (srcPt == null) {
			srcPt = new Point2D.Double();
		}
		int dx = interpolant.interpolate(this.horizitalImage, padder, destPt, 0);
		int dy = interpolant.interpolate(this.verticalImage, padder, destPt, 0);
		dx -= 128;
		dy -= 128;

		int magnitude = (int) (Math.abs(dx) + Math.abs(dy));
		int count = (int) (magnitude * this.strength / 2.0) + 1;
		while (count > 0) {
			transform(destPt);
			count--;
		}
		srcPt.setLocation(destPt);
		return srcPt;
	}

	private void transform(Point2D destPt) {
		double dx = interpolant.interpolate(horizitalImage, padder, destPt, 0);
		double dy = interpolant.interpolate(verticalImage, padder, destPt, 0);
		dx -= 128;
		dy -= 128;

		double angle = Math.atan2(dy, dx);
		if (this.isPerpendicular) {
			angle += Math.PI * 0.5;
		}
		double x = Math.cos(angle) * 0.5 + destPt.getX();
		double y = Math.sin(angle) * 0.5 + destPt.getY();
		destPt.setLocation(x, y);
	}

}

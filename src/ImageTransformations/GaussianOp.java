package ImageTransformations;

import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;

import pixeljelly.ops.ConvolutionOp;
import pixeljelly.ops.NullOp;
import pixeljelly.ops.PluggableImageOp;
import pixeljelly.utilities.SeperableKernel;

public class GaussianOp extends NullOp implements PluggableImageOp {

	private float[] vector;
	private double alpha;
	private double sigma;

	public GaussianOp() {
		this.alpha = 2;
		this.sigma = 3;
		this.vector = getCoefficient(this.alpha, this.sigma);
	}

	public GaussianOp(double alpha, double sigma) {
		this.alpha = alpha;
		this.sigma = sigma;
		this.vector = getCoefficient(this.alpha, this.sigma);
	}

	public float[] getCoefficient(double alpha, double sigma) {
		int w = (int) Math.ceil(alpha * sigma);
		float[] result = new float[2 * w + 1];
		for (int n = 0; n <= w; n++) {
			double coefficient = Math.exp(-(n * n) / (2 * sigma * sigma)) / (Math.sqrt(2 * Math.PI) * sigma);
			result[w - n] = (float) coefficient;
			result[w + n] = (float) coefficient;
		}
		return result;
	}

	public BufferedImage filter(BufferedImage src, BufferedImage dest) {
		SeperableKernel kernel = new SeperableKernel(vector, vector);
		ConvolutionOp convolution = new ConvolutionOp(kernel, true);
		return convolution.filter(src, dest);
	}

	@Override
	public String getAuthorName() {
		return "Peiqiu Tian";
	}

	@Override
	public BufferedImageOp getDefault(BufferedImage arg0) {
		return new GaussianOp(2, 3);
	}

	public double getAlpha() {
		return alpha;
	}

	public double getSigma() {
		return sigma;
	}

	public void setSigma(double sigma) {
		this.sigma = sigma;
	}

	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}

}

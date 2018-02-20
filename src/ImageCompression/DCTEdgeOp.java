package ImageCompression;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;

import pixeljelly.ops.NullOp;
import pixeljelly.ops.PluggableImageOp;
import pixeljelly.utilities.ImagePadder;
import pixeljelly.utilities.ImagingUtilities;
import pixeljelly.utilities.ZeroPadder;

public class DCTEdgeOp extends NullOp implements PluggableImageOp {

	private int tileSize;
	private int offset;
	private double strength;
	private ImagePadder padder = ZeroPadder.getInstance();
	private Rectangle bound;

	public DCTEdgeOp() {
		this.offset = 0;
		this.strength = 8;
		this.tileSize = 20;
	}

	public DCTEdgeOp(int tileSize, int offset, double strength) {
		this.offset = offset;
		this.strength = strength;
		this.tileSize = tileSize;
	}

	@Override
	public String getAuthorName() {
		return "Peiqiu Tian";
	}

	@Override
	public BufferedImageOp getDefault(BufferedImage arg0) {
		return new DCTEdgeOp(20, 0, 8);
	}

	public int getTileSize() {
		return tileSize;
	}

	public void setTileSize(int tileSize) {
		this.tileSize = tileSize;
	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public double getStrength() {
		return strength;
	}

	public void setStrength(double strength) {
		this.strength = strength;
	}

	public BufferedImage filter(BufferedImage src, BufferedImage dest) {
		if (dest == null) {
			dest = createCompatibleDestImage(src, src.getColorModel());
		}
		bound = new Rectangle(this.tileSize, this.tileSize);
		int widthcount = src.getWidth() / bound.width;
		if (src.getWidth() % bound.width != 0) {
			widthcount++;
		}
		int heightcount = src.getHeight() / bound.height;
		if (src.getHeight() % bound.height != 0) {
			heightcount++;
		}
		for (int b = 0; b < src.getRaster().getNumBands(); b++) {
			for (int county = 0; county < heightcount; county++) {

				for (int countx = 0; countx < widthcount; countx++) {
					int x = countx * bound.width;
					int y = county * bound.height;

					float[][] dct = forwardDCT(src, x, y, b);
					float[][] newdct = afterPrecess(dct);
					float[][] samples = backwardDCT(newdct);
					dest = dct2sample(dest, x, y, b, samples);
				}
			}
		}
		return dest;
	}

	private BufferedImage dct2sample(BufferedImage dest, int x, int y, int b, float[][] samples) {
		for (int col = 0; col < samples.length; col++) {
			for (int row = 0; row < samples.length; row++) {
				try {
					dest.getRaster().setSample(x + col, y + row, b, ImagingUtilities.clamp(samples[col][row], 0, 255));
				} catch (Exception e) {

				}
			}
		}
		return dest;
	}

	private float[][] backwardDCT(float[][] dct) {
		float[][] result = new float[dct.length][dct.length];

		for (int x = 0; x < result.length; x++) {
			result[x] = backward1D(dct[x]);
		}

		float[] column = new float[dct.length];
		for (int y = 0; y < result.length; y++) {
			for (int row = 0; row < dct.length; row++) {
				column[row] = result[row][y];
			}
			float[] temp = backward1D(column);
			for (int row = 0; row < dct.length; row++) {
				result[row][y] = temp[row];
			}
		}
		return result;
	}

	private float[] backward1D(float[] data) {
		final float alpha0 = (float) Math.sqrt(1.0 / data.length);
		final float alphaN = (float) Math.sqrt(2.0 / data.length);
		float[] result = new float[data.length];
		for (int x = 0; x < result.length; x++) {
			for (int u = 0; u < data.length; u++) {
				result[x] += (u == 0 ? alpha0 : alphaN) * data[u]
						* (float) Math.cos((2 * x + 1) * u * Math.PI / (2 * data.length));
			}
		}
		return result;
	}

	private float[][] afterPrecess(float[][] dct) {
		for (int i = 0; i < dct.length; i++) {
			for (int j = 0; j < dct.length; j++) {
				dct[i][j] = (float) (dct[i][j]
						* (offset + Math.sqrt(i * i + j * j) * strength / Math.sqrt(2 * tileSize * tileSize)));
			}
		}
		return dct;
	}

	private float[][] forwardDCT(BufferedImage src, int x, int y, int b) {
		float[][] data = new float[bound.width][bound.height];
		float[][] result = new float[bound.width][bound.height];
		data = copyfrom(src, x, y, b, data);
		for (int u = 0; u < result.length; u++) {
			result[u] = forwardDCT1D(data[u]);
		}

		float[] column = new float[data.length];
		for (int v = 0; v < result.length; v++) {
			for (int row = 0; row < data.length; row++) {
				column[row] = result[row][v];
			}

			float[] temp = forwardDCT1D(column);
			for (int row = 0; row < data.length; row++) {
				result[row][v] = temp[row];
			}
		}
		return result;
	}

	private float[] forwardDCT1D(float[] data) {
		final float alpha0 = (float) Math.sqrt(1.0 / data.length);
		final float alphaN = (float) Math.sqrt(2.0 / data.length);
		float[] result = new float[data.length];
		for (int u = 0; u < result.length; u++) {
			for (int x = 0; x < data.length; x++) {
				result[u] += data[x] * (float) Math.cos((2 * x + 1) * u * Math.PI / (2 * data.length));
			}
			result[u] *= (u == 0 ? alpha0 : alphaN);
		}
		return result;
	}

	private float[][] copyfrom(BufferedImage src, int x, int y, int b, float[][] data) {
		for (int i = 0; i < data.length; i++) {
			for (int j = 0; j < data.length; j++) {
				data[i][j] = (float) padder.getSample(src, x + i, y + j, b);
			}
		}
		return data;
	}

}

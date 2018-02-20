package ImageCompression;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import pixeljelly.io.ImageDecoder;
import pixeljelly.io.ImageEncoder;
import pixeljelly.scanners.Location;
import pixeljelly.scanners.RasterScanner;
import pixeljelly.scanners.ZigZagScanner;
import pixeljelly.utilities.ImagePadder;
import pixeljelly.utilities.ImagingUtilities;
import pixeljelly.utilities.ZeroPadder;

public class DCTCompressor {

	public void encode(String file, String N, String out) {
		try {
			new DCTencode().DCTEncode(file, N, out);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void decode(String inputfile, String outfile) {
		try {
			new DCTdecode().DCTDecode(inputfile, outfile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private class DCTencode extends ImageEncoder {
		private ImagePadder padder = ZeroPadder.getInstance();
		private Rectangle tile = new Rectangle(8, 8);
		private int N;

		public void DCTEncode(String file, String N, String out) throws MalformedURLException, IOException {
			BufferedImage src = ImageIO.read(new URL(file));
			this.N = Integer.parseInt(N);
			OutputStream output = new FileOutputStream(new File(out));
			encode(src, output);
			output.close();
		}

		@Override
		public void encode(BufferedImage src, OutputStream os) throws IOException {
			DataOutputStream out = new DataOutputStream(os);
			writeHeader(src, out);
			out.writeInt(this.N);
			process(src, out);
		}

		@Override
		public String getMagicWord() {
			return "DCT";
		}

		private void process(BufferedImage src, DataOutputStream os) throws IOException {
			int countx = (int) (src.getWidth() / tile.getWidth());
			int county = (int) (src.getHeight() / tile.getHeight());
			if (src.getWidth() % tile.width != 0) {
				countx++;
			}
			if (src.getHeight() % tile.getHeight() != 0) {
				county++;
			}
			BufferedImage tileImageSize = new BufferedImage((int) (countx * tile.getWidth()),
					(int) (county * tile.getHeight()), BufferedImage.TYPE_INT_RGB);
			for (int b = 0; b < src.getRaster().getNumBands(); b++) {
				float[][] srcdata = new float[tileImageSize.getHeight()][tileImageSize.getWidth()];
				float[][] data = new float[tile.height][tile.width];
				for (int y = 0; y < county; y++) {
					for (int x = 0; x < countx; x++) {
						int colpoint = (int) (x * tile.getWidth());
						int rowpoint = (int) (y * tile.getHeight());
						for (Location pt : new RasterScanner(tile.getBounds())) {
							data[pt.row][pt.col] = padder.getSample(src, pt.col + colpoint, pt.row + rowpoint, b);
						}
						float[][] dct = forwardDCT(data);
						for (Location pt : new RasterScanner(tile.getBounds())) {
							srcdata[pt.row + rowpoint][pt.col + colpoint] = dct[pt.row][pt.col];
						}
					}
				}
				writeRun(tileImageSize, srcdata, os);
			}
		}

		private void writeRun(BufferedImage tilesrc, float[][] dataofband, DataOutputStream os) throws IOException {
			int n = 0;
			for (Location pt : new ZigZagScanner(tilesrc, 8, 8)) {
				n = n % 64;
				if (n < N) {
					float sf = dataofband[pt.row][pt.col];
					os.writeFloat(sf);
				}
				n++;
			}
		}

		private float[][] forwardDCT(float[][] data) {
			float[][] result = new float[data.length][data.length];

			for (int u = 0; u < result.length; u++) {
				result[u] = forwardDCT(data[u]);
			}

			float[] column = new float[data.length];
			for (int v = 0; v < result.length; v++) {
				for (int row = 0; row < data.length; row++) {
					column[row] = result[row][v];
				}

				float[] temp = forwardDCT(column);
				for (int row = 0; row < data.length; row++) {
					result[row][v] = temp[row];
				}
			}
			return result;
		}

		private float[] forwardDCT(float[] data) {
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
	}

	private class DCTdecode extends ImageDecoder {

		private Rectangle tile = new Rectangle(8, 8);
		private int N;

		public void DCTDecode(String inputfile, String outfile) throws IOException {
			File file = new File(inputfile);
			if (canDecode(file)) {
				InputStream is = new FileInputStream(file);
				BufferedImage dest = decode(is);
				is.close();
				ImageOutputStream output = new FileImageOutputStream(new File(outfile));
				ImageIO.write(dest, "png", output);
			}
		}

		@Override
		public BufferedImage decode(InputStream is) throws IOException {
			DataInputStream input = new DataInputStream(is);
			BufferedImage dest = readHeader(input);
			dest = process(dest, input);
			return dest;
		}

		private BufferedImage process(BufferedImage dest, DataInputStream input) throws IOException {

			int countx = (int) (dest.getWidth() / tile.getWidth());
			int county = (int) (dest.getHeight() / tile.getHeight());
			if (dest.getWidth() % tile.width != 0) {
				countx++;
			}
			if (dest.getHeight() % tile.getHeight() != 0) {
				county++;
			}

			BufferedImage tileImageSize = new BufferedImage((int) (countx * tile.getWidth()),
					(int) (county * tile.getHeight()), BufferedImage.TYPE_INT_RGB);

			for (int b = 0; b < dest.getRaster().getNumBands(); b++) {
				float[][] bandDCT = getDataband(tileImageSize, input);

				for (int x = 0; x < countx; x++) {
					for (int y = 0; y < county; y++) {
						int pointcol = (int) (x * tile.getWidth());
						int pointrow = (int) (y * tile.getHeight());

						float[][] dct = new float[(int) tile.getHeight()][(int) tile.getWidth()];

						for (int col = 0; col < 8; col++) {
							for (int row = 0; row < 8; row++) {
								dct[row][col] = bandDCT[pointrow + row][pointcol + col];
							}
						}

						float[][] data = inversewardDCT(dct);

						for (int row = 0; row < data.length; row++) {
							for (int col = 0; col < data.length; col++) {
								try {
									dest.getRaster().setSample(pointcol + col, pointrow + row, b,
											ImagingUtilities.clamp(data[row][col], 0, 255));
								} catch (Exception e) {
								}
							}
						}
					}
				}
			}
			return dest;
		}

		private float[][] getDataband(BufferedImage tileimage, DataInputStream input) throws IOException {
			float[][] bandDCT = new float[tileimage.getHeight()][tileimage.getWidth()];
			int n = 0;
			for (Location pt : new ZigZagScanner(tileimage, 8, 8)) {
				n %= 64;
				if (n < N) {
					bandDCT[pt.row][pt.col] = input.readFloat();
				}
				n++;
			}
			return bandDCT;
		}

		private BufferedImage readHeader(DataInputStream is) throws IOException {
			is.readUTF();
			int width = is.readShort();
			int height = is.readShort();
			int type = is.readInt();
			this.N = is.readInt();
			return new BufferedImage(width, height, type);
		}

		@Override
		public String getMagicWord() {
			return "DCT";
		}

		private float[][] inversewardDCT(float[][] dct) {
			float[][] result = new float[dct.length][dct.length];

			for (int x = 0; x < result.length; x++) {
				result[x] = inverseward(dct[x]);
			}

			float[] column = new float[dct.length];
			for (int y = 0; y < result.length; y++) {
				for (int row = 0; row < dct.length; row++) {
					column[row] = result[row][y];
				}
				float[] temp = inverseward(column);
				for (int row = 0; row < dct.length; row++) {
					result[row][y] = temp[row];
				}
			}
			return result;
		}

		private float[] inverseward(float[] data) {
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

	}

	public static void main(String[] args) {
		DCTCompressor id = new DCTCompressor();

		if (args[0].equals("encode")) {
			id.encode(args[1], args[2], args[3]);
		}
		if (args[0].equals("decode")) {
			id.decode(args[1], args[2]);
		}
	}
}

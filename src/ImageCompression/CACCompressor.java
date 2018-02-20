package ImageCompression;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import pixeljelly.io.ImageDecoder;
import pixeljelly.io.ImageEncoder;
import pixeljelly.ops.BandExtractOp;
import pixeljelly.scanners.Location;
import pixeljelly.scanners.RasterScanner;
import pixeljelly.utilities.SimpleColorModel;

public class CACCompressor {

	public void encode(String input, String model, String N1, String N2, String N3, String output) {
		try {
			new CACEncode().CACencode(input, model, N1, N2, N3, output);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void decode(String input, String output) {
		try {
			new CACDecode().CACdecode(input, output);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private class CACEncode extends ImageEncoder {

		private String colorModel;
		private double[] tolerances;

		public void CACencode(String input, String model, String N1, String N2, String N3, String output)
				throws MalformedURLException, IOException {
			BufferedImage src = ImageIO.read(new URL(input));
			this.colorModel = model;
			tolerances = new double[] { Double.parseDouble(N1), Double.parseDouble(N2), Double.parseDouble(N3) };
			OutputStream os = new FileOutputStream(output);
			encode(src, os);
		}

		@Override
		public void encode(BufferedImage src, OutputStream os) throws IOException {
			MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(os);
			writeHeader(src, output);
			output.writeUTF(colorModel);
			for (int b = 0; b < 3; b++) {
				double threshold = tolerances[b];
				BufferedImage currentsrc = getBandImage(src, colorModel, b);
				write(currentsrc, output, 0, 0, src.getWidth(), src.getHeight(), threshold);
			}
			output.close();
		}

		private BufferedImage getBandImage(BufferedImage src, String model, int b) {
			if (model.equals("RGB")) {
				return getRGBOneBandImage(src, b);
			}
			if (model.equals("HSB")) {
				return getHSBOneBandImage(src, b);
			}
			return null;
		}

		private BufferedImage getHSBOneBandImage(BufferedImage src, int b) {
			return new BandExtractOp(SimpleColorModel.HSV, b).filter(src, null);
		}

		private BufferedImage getRGBOneBandImage(BufferedImage src, int b) {
			return new BandExtractOp(SimpleColorModel.RGB, b).filter(src, null);
		}

		@Override
		public String getMagicWord() {
			return "CAC";
		}

		private void write(BufferedImage src, MemoryCacheImageOutputStream output, int x, int y, int width, int height,
				double threshold) throws IOException {
			if (width <= 0 || height <= 0)
				return;
			if (isAllWhite(src, x, y, width, height, threshold)) {
				int sample = getAverage(src, x, y, width, height);
				if (sample == 0) {
					sample = 1;
				}
				;
				output.write(sample);
			} else {
				output.write(0);
				write(src, output, x + width / 2, y, width - width / 2, height / 2, threshold);
				write(src, output, x, y, width / 2, height / 2, threshold);
				write(src, output, x, y + height / 2, width / 2, height - height / 2, threshold);
				write(src, output, x + width / 2, y + height / 2, width - width / 2, height - height / 2, threshold);
			}

		}

		private int getAverage(BufferedImage src, int x, int y, int width, int height) {
			double sum = 0;
			for (int row = 0; row < height; row++) {
				for (int col = 0; col < width; col++) {
					sum += src.getRaster().getSample(x + col, y + row, 0);
				}
			}
			double average = sum / (width * height);
			return (int) Math.round(average);
		}

		private boolean isAllWhite(BufferedImage src, int x, int y, int width, int height, double threshold) {
			double average = getAverage(src, x, y, width, height);
			double regionInterance = 0;
			for (int row = 0; row < height; row++) {
				for (int col = 0; col < width; col++) {
					regionInterance += Math.pow((src.getRaster().getSample(x + col, y + row, 0) - average), 2);
				}
			}
			regionInterance = Math.sqrt(regionInterance / (width * height));
			return regionInterance <= threshold;
		}
	}

	private class CACDecode extends ImageDecoder {

		private String colorModel;

		public void CACdecode(String inputfile, String outputfile) throws IOException {
			if (canDecode(new File(inputfile))) {
				InputStream is = new FileInputStream(inputfile);
				BufferedImage dest = decode(is);
				is.close();
				ImageOutputStream out = new FileImageOutputStream(new File(outputfile));
				ImageIO.write(dest, "png", out);
			}
		}

		@Override
		public BufferedImage decode(InputStream is) throws IOException {
			MemoryCacheImageInputStream read = new MemoryCacheImageInputStream(is);
			BufferedImage dest = readHeader(read);
			List<BufferedImage> list = new ArrayList<BufferedImage>();
			if (colorModel.equals("HSB")) {
				for (int b = 0; b < 3; b++) {
					BufferedImage band = new BandExtractOp(SimpleColorModel.HSV, b).filter(dest, null);
					band = process(band, read);
					list.add(band);
				}
				dest = combineBandHSB(list, dest);
			}

			if (colorModel.equals("RGB")) {
				for (int b = 0; b < 3; b++) {
					BufferedImage band = new BandExtractOp(SimpleColorModel.RGB, b).filter(dest, null);
					band = process(band, read);
					list.add(band);
				}
				dest = combineBandRGB(list, dest);
			}
			return dest;
		}

		private BufferedImage readHeader(MemoryCacheImageInputStream read) throws IOException {
			read.readUTF();
			int width = read.readShort();
			int height = read.readShort();
			int type = read.readInt();
			colorModel = read.readUTF();
			return new BufferedImage(width, height, type);
		}

		@Override
		public String getMagicWord() {
			return "CAC";
		}

		private BufferedImage process(BufferedImage band, MemoryCacheImageInputStream read) throws IOException {

			read(band, read, 0, 0, band.getWidth(), band.getHeight());

			return band;
		}

		private void read(BufferedImage band, MemoryCacheImageInputStream is, int x, int y, int w, int h)
				throws IOException {
			if (w <= 0 || h <= 0) {
				return;
			}
			int sample = is.read();
			if (sample != 0) {
				for (int row = 0; row < h; row++) {
					for (int col = 0; col < w; col++) {
						band.getRaster().setSample(x + col, y + row, 0, sample);
					}
				}
				return;
			} else {
				read(band, is, x + w / 2, y, w - w / 2, h / 2);
				read(band, is, x, y, w / 2, h / 2);
				read(band, is, x, y + h / 2, w / 2, h - h / 2);
				read(band, is, x + w / 2, y + h / 2, w - w / 2, h - h / 2);
			}
		}

		private BufferedImage combineBandHSB(List<BufferedImage> list, BufferedImage dest) {
			for (Location pt : new RasterScanner(list.get(0), false)) {
				int band1 = list.get(0).getRaster().getSample(pt.col, pt.row, 0);
				int band2 = list.get(1).getRaster().getSample(pt.col, pt.row, 0);
				int band3 = list.get(2).getRaster().getSample(pt.col, pt.row, 0);
				int rgb = Color.HSBtoRGB((float) (band1 / 255.0), (float) (band2 / 255.0), (float) (band3 / 255.0));
				dest.setRGB(pt.col, pt.row, rgb);
			}
			return dest;
		}

		private BufferedImage combineBandRGB(List<BufferedImage> list, BufferedImage dest) {
			for (Location pt : new RasterScanner(list.get(0), false)) {
				int band1 = list.get(0).getRaster().getSample(pt.col, pt.row, 0);
				int band2 = list.get(1).getRaster().getSample(pt.col, pt.row, 0);
				int band3 = list.get(2).getRaster().getSample(pt.col, pt.row, 0);
				int rgb = new Color(band1, band2, band3).getRGB();
				dest.setRGB(pt.col, pt.row, rgb);
			}
			return dest;
		}

	}

	public static void main(String[] args) {
		CACCompressor ca = new CACCompressor();
		if (args[0].equals("encode")) {
			ca.encode(args[1], args[2], args[3], args[4], args[5], args[6]);
		}
		if (args[0].equals("decode")) {
			ca.decode(args[1], args[2]);
		}
	}

}

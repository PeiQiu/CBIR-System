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
import pixeljelly.utilities.ImagingUtilities;
import pixeljelly.utilities.SimpleColorModel;

public class DeltaCompressor {

	public void encode(String input, String colorModel, String D1, String D2, String D3, String output) {
		try {
			new DeltaEncode().deltaencode(input, colorModel, D1, D2, D3, output);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void decode(String input, String output) {
		try {
			new DeltaDecode().deltadecode(input, output);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private class DeltaEncode extends ImageEncoder {

		private String colorModel;
		private double[] deltas;

		public void deltaencode(String input, String colorModel, String D1, String D2, String D3, String output)
				throws MalformedURLException, IOException {
			BufferedImage src = ImageIO.read(new URL(input));
			this.colorModel = colorModel;
			double d1 = Double.parseDouble(D1);
			double d2 = Double.parseDouble(D2);
			double d3 = Double.parseDouble(D3);
			this.deltas = new double[] { d1, d2, d3 };
			OutputStream os = new FileOutputStream(new File(output));
			encode(src, os);
		}

		@Override
		public void encode(BufferedImage src, OutputStream os) throws IOException {
			MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(os);
			writeHeader(src, output);
			output.writeUTF(this.colorModel);
			for (int i = 0; i < deltas.length; i++) {
				output.writeDouble(deltas[i]);
			}
			for (int b = 0; b < 3; b++) {
				double delta = this.deltas[b];
				BufferedImage currentsrc = getBandImage(src, colorModel, b);
				write(currentsrc, output, delta);
			}
			output.close();
		}

		private void write(BufferedImage currentsrc, MemoryCacheImageOutputStream output, double delta)
				throws IOException {
			for (int row = 0; row < currentsrc.getHeight(); row++) {
				int sample0 = currentsrc.getRaster().getSample(0, row, 0);
				double presample = sample0;
				output.write(sample0);
				for (int col = 1; col < currentsrc.getRaster().getWidth(); col++) {
					int sample = currentsrc.getRaster().getSample(col, row, 0);
					if (sample > presample) {
						presample = presample + delta;
						output.writeBit(1);
					} else {
						presample = presample - delta;
						output.writeBit(0);
					}
				}
			}

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
			return "Delta";
		}
	}

	private class DeltaDecode extends ImageDecoder {

		private String colorModel;
		private double[] deltas = new double[3];

		public void deltadecode(String input, String output) throws IOException {
			if (canDecode(new File(input))) {
				InputStream is = new FileInputStream(new File(input));
				BufferedImage dest = decode(is);
				ImageOutputStream out = new FileImageOutputStream(new File(output));
				ImageIO.write(dest, "png", out);
			}
		}

		@Override
		public BufferedImage decode(InputStream is) throws IOException {
			MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(is);
			BufferedImage dest = readHeader(input);
			List<BufferedImage> list = new ArrayList<BufferedImage>();
			if (colorModel.equals("HSB")) {
				for (int b = 0; b < 3; b++) {
					BufferedImage band = new BandExtractOp(SimpleColorModel.HSV, b).filter(dest, null);
					band = process(band, input, this.deltas[b]);
					list.add(band);
				}
				dest = combineBandHSB(list, dest);
			}

			if (colorModel.equals("RGB")) {
				for (int b = 0; b < 3; b++) {
					BufferedImage band = new BandExtractOp(SimpleColorModel.RGB, b).filter(dest, null);
					band = process(band, input, this.deltas[b]);
					list.add(band);
				}
				dest = combineBandRGB(list, dest);
			}
			return dest;
		}

		private BufferedImage process(BufferedImage band, MemoryCacheImageInputStream input, double delta)
				throws IOException {
			for (int row = 0; row < band.getRaster().getHeight(); row++) {
				if (input.getBitOffset() != 0) {
					input.skipBytes(1);
				}
				int sample0 = input.read();
				double cursample = (double) sample0;
				band.getRaster().setSample(0, row, 0, sample0);
				for (int col = 1; col < band.getRaster().getWidth(); col++) {
					int direct = input.readBit();
					if (direct == 1) {
						cursample += delta;
					} else {
						cursample -= delta;
					}
					band.getRaster().setSample(col, row, 0, ImagingUtilities.clamp(cursample, 0, 255));
				}
			}
			return band;
		}

		private BufferedImage readHeader(MemoryCacheImageInputStream read) throws IOException {
			read.readUTF();
			int width = read.readShort();
			int height = read.readShort();
			int type = read.readInt();
			this.colorModel = read.readUTF();
			for (int i = 0; i < deltas.length; i++) {
				deltas[i] = read.readDouble();
			}
			return new BufferedImage(width, height, type);
		}

		@Override
		public String getMagicWord() {
			return "Delta";
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
		DeltaCompressor s = new DeltaCompressor();
		if (args[0].equals("encode")) {
			s.encode(args[1], args[2], args[3], args[4], args[5], args[6]);
		}

		if (args[0].equals("decode")) {
			s.decode(args[1], args[2]);
		}
	}
}

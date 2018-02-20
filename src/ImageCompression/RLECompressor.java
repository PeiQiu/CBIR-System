package ImageCompression;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import pixeljelly.io.ImageDecoder;
import pixeljelly.io.ImageEncoder;
import pixeljelly.ops.BandExtractOp;
import pixeljelly.scanners.Location;
import pixeljelly.scanners.RasterScanner;
import pixeljelly.utilities.SimpleColorModel;

public class RLECompressor {

	public void encode(String input, String model, String output) {
		new RLEencode().RLEEncode(input, model, output);
	}

	public void decode(String input, String output) {
		new RLEdecode().RLEDecode(input, output);
	}

	private class RLEencode extends ImageEncoder {

		private String colorModel;

		public void RLEEncode(String input, String model, String output) {
			try {
				BufferedImage src = ImageIO.read(new URL(input));
				this.colorModel = model;
				OutputStream os = new FileOutputStream(output);
				encode(src, os);
				os.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void encode(BufferedImage src, OutputStream os) throws IOException {
			DataOutputStream out = new DataOutputStream(os);
			writeHeader(src, out);
			out.writeUTF(colorModel);
			if (colorModel.equals("RGB")) {
				for (int b = 0; b < 3; b++) {
					BufferedImage band = new BandExtractOp(SimpleColorModel.RGB, b).filter(src, null);
					process(band, out);
				}
			}
			if (colorModel.equals("HSB")) {
				for (int b = 0; b < 3; b++) {
					BufferedImage band = new BandExtractOp(SimpleColorModel.HSV, b).filter(src, null);
					process(band, out);
				}
			}
		}

		private void process(BufferedImage band, DataOutputStream out) throws IOException {
			for (int bit = 0; bit < band.getSampleModel().getSampleSize(0); bit++) {
				for (int y = 0; y < band.getHeight(); y++) {
					boolean isWhite = true;
					int pixelsEncoded = 0;
					while (pixelsEncoded < band.getWidth()) {
						int length = getRun(band, pixelsEncoded, y, bit, isWhite);
						isWhite = !isWhite;
						writeRun(length, out);
						pixelsEncoded += length;
					}
				}
			}
		}

		private int getRun(BufferedImage band, int col, int row, int bit, boolean isWhite) {
			int result = 0;
			while (col < band.getWidth() && getBit(band.getRaster().getSample(col, row, 0), bit) == isWhite) {
				result++;
				col++;
			}
			return result;
		}

		private boolean getBit(int sample, int bit) {
			return (sample & (0x1 << bit)) != 0;
		}

		private void writeRun(int length, DataOutputStream out) throws IOException {
			while (length >= 255) {
				out.write(255);
				length -= 255;
			}
			out.write(length);
		}

		@Override
		public String getMagicWord() {
			return "RLE";
		}
	}

	private class RLEdecode extends ImageDecoder {

		private String colorModel;

		public void RLEDecode(String input, String output) {
			try {
				if (canDecode(new File(input))) {
					InputStream is = new FileInputStream(input);
					BufferedImage dest = decode(is);
					is.close();
					ImageOutputStream out = new FileImageOutputStream(new File(output));
					ImageIO.write(dest, "png", out);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public BufferedImage decode(InputStream is) throws IOException {
			DataInputStream input = new DataInputStream(is);
			BufferedImage dest = readHeader(input);
			List<BufferedImage> list = new ArrayList<BufferedImage>();
			if (colorModel.equals("RGB")) {
				for (int b = 0; b < 3; b++) {
					BufferedImage band = new BandExtractOp(SimpleColorModel.RGB, b).filter(dest, null);
					process(band, input);
					list.add(band);
				}
				dest = combineRGBBand(list, dest);
			}
			if (colorModel.equals("HSB")) {
				for (int b = 0; b < 3; b++) {
					BufferedImage band = new BandExtractOp(SimpleColorModel.HSV, b).filter(dest, null);
					process(band, input);
					list.add(band);
				}
				dest = combineHSBBand(list, dest);
			}
			return dest;
		}

		private void process(BufferedImage band, DataInputStream input) throws IOException {
			for (int bit = 0; bit < band.getSampleModel().getSampleSize(0); bit++) {
				for (int y = 0; y < band.getHeight(); y++) {
					int code = 1;
					int pixelsEncoded = 0;
					while (pixelsEncoded < band.getWidth()) {
						int length = input.read();
						if (length == 0) {
							code = 0;
						} else {
							if (length == 255) {
								readRun(band, length, pixelsEncoded, y, bit, code);
							} else {
								readRun(band, length, pixelsEncoded, y, bit, code);
								code = (code + 1) % 2;
							}
							pixelsEncoded += length;
						}
					}
				}
			}
		}

		private void readRun(BufferedImage band, int length, int col, int y, int bit, int code) {
			for (int point = 0; point < length; point++) {
				int sample = band.getRaster().getSample(col + point, y, 0);
				if (code == 0) {
					sample = sample & (~(0x1 << bit));
				}
				if (code == 1) {
					sample = sample | (0x1 << bit);
				}
				band.getRaster().setSample(col + point, y, 0, sample);
			}
		}

		private BufferedImage combineHSBBand(List<BufferedImage> list, BufferedImage dest) {
			for (Location pt : new RasterScanner(list.get(0), false)) {
				int band1 = list.get(0).getRaster().getSample(pt.col, pt.row, 0);
				int band2 = list.get(1).getRaster().getSample(pt.col, pt.row, 0);
				int band3 = list.get(2).getRaster().getSample(pt.col, pt.row, 0);
				int rgb = Color.HSBtoRGB((float) (band1 / 255.0), (float) (band2 / 255.0), (float) (band3 / 255.0));
				dest.setRGB(pt.col, pt.row, rgb);
			}
			return dest;
		}

		private BufferedImage combineRGBBand(List<BufferedImage> list, BufferedImage dest) {
			for (Location pt : new RasterScanner(list.get(0), false)) {
				int band1 = list.get(0).getRaster().getSample(pt.col, pt.row, 0);
				int band2 = list.get(1).getRaster().getSample(pt.col, pt.row, 0);
				int band3 = list.get(2).getRaster().getSample(pt.col, pt.row, 0);
				int rgb = new Color(band1, band2, band3).getRGB();
				dest.setRGB(pt.col, pt.row, rgb);
			}
			return dest;
		}

		private BufferedImage readHeader(DataInputStream input) throws IOException {
			input.readUTF();
			int width = input.readShort();
			int height = input.readShort();
			int type = input.readInt();
			this.colorModel = input.readUTF();
			return new BufferedImage(width, height, type);
		}

		@Override
		public String getMagicWord() {
			return "RLE";
		}

	}

	public static void main(String[] args) {
		RLECompressor d = new RLECompressor();
		if (args[0].equals("encode")) {
			d.encode(args[1], args[2], args[3]);
		}

		if (args[0].equals("decode")) {
			d.decode(args[1], args[2]);
		}
	}

}

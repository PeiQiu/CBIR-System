package ImageCompression;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import pixeljelly.ops.NullOp;
import pixeljelly.ops.PluggableImageOp;
import pixeljelly.scanners.Location;
import pixeljelly.scanners.RasterScanner;
import pixeljelly.utilities.ImagePadder;
import pixeljelly.utilities.ImagingUtilities;
import pixeljelly.utilities.NonSeperableKernel;
import pixeljelly.utilities.ZeroPadder;

public class DitherOp extends NullOp implements PluggableImageOp {
	private DitherOp.Type type;
	private int size;
	private Color[] palette;

	private ImagePadder padder = ZeroPadder.getInstance();

	public enum Type {
		STUCKI, JARVIS, FLOYD_STEINBURG, SIERRA, SIERRA_2_4A
	}

	public DitherOp() {
		this.type = Type.JARVIS;
		this.size = 16;
	}

	public DitherOp(DitherOp.Type type, int paletteSize) {
		this.type = type;
		this.size = paletteSize;
	}

	public DitherOp(DitherOp.Type type, Color[] palette) {
		this.type = type;
		this.palette = palette;
	}

	private NonSeperableKernel getDifusion(Type t) {
		NonSeperableKernel kernel = null;
		float[] value;
		switch (t) {
		case STUCKI:
			value = new float[] { 0.0f, 0.0f, 0.0f, (float) (8 / 42.0), (float) (4 / 42.0), (float) (2 / 42.0),
					(float) (4 / 42.0), (float) (8 / 42.0), (float) (4 / 42.0), (float) (2 / 42.0), (float) (1 / 42.0),
					(float) (2 / 42.0), (float) (4 / 42.0), (float) (2 / 42.0), (float) (1 / 42.0) };
			kernel = new NonSeperableKernel(5, 3, value);
			break;
		case JARVIS:
			value = new float[] { 0.0f, 0.0f, 0.0f, (float) (7 / 48.0), (float) (5 / 48.0), (float) (3 / 48.0),
					(float) (5 / 48.0), (float) (7 / 48.0), (float) (5 / 48.0), (float) (3 / 48.0), (float) (1 / 48.0),
					(float) (3 / 48.0), (float) (5 / 48.0), (float) (3 / 48.0), (float) (1 / 48.0) };
			kernel = new NonSeperableKernel(5, 3, value);
			break;
		case FLOYD_STEINBURG:
			value = new float[] { 0.0f, 0.0f, (float) (7 / 16.0), (float) (3 / 16.0), (float) (5 / 16.0),
					(float) (1 / 16.0) };
			kernel = new NonSeperableKernel(3, 2, value);
			break;
		case SIERRA:
			value = new float[] { 0.0f, 0.0f, 0.0f, (float) (5 / 32.0), (float) (3 / 32.0), (float) (2 / 32.0),
					(float) (4 / 32), (float) (5 / 32.0), (float) (4 / 32.0), (float) (2 / 32.0), 0.0f,
					(float) (2 / 32.0), (float) (3 / 32.0), (float) (2 / 32.0), 0.0f };
			kernel = new NonSeperableKernel(5, 3, value);
			break;
		case SIERRA_2_4A:
			value = new float[] { 0.0f, 0.0f, (float) (2 / 4.0), (float) (1 / 4.0), (float) (1 / 4.0), 0.0f };
			kernel = new NonSeperableKernel(3, 2, value);
		}
		return kernel;
	}

	@Override
	public String getAuthorName() {
		return "Peiqiu Tian";
	}

	public IndexColorModel getColorModel(Color[] palette) {
		byte[] reds = new byte[palette.length];
		byte[] greens = new byte[palette.length];
		byte[] blues = new byte[palette.length];

		for (int i = 0; i < reds.length; i++) {
			reds[i] = (byte) palette[i].getRed();
			greens[i] = (byte) palette[i].getGreen();
			blues[i] = (byte) palette[i].getBlue();
		}
		return new IndexColorModel(8, reds.length, reds, greens, blues);

	}

	public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) {
		return new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_INDEXED,
				getColorModel(this.palette));
	}

	@Override
	public BufferedImageOp getDefault(BufferedImage arg0) {
		return new DitherOp(DitherOp.Type.JARVIS, 16);
	}

	public DitherOp.Type getType() {
		return type;
	}

	public BufferedImage filter(BufferedImage src, BufferedImage dest) {
		if (this.palette == null) {
			this.palette = new Color[this.size];
			createPalette(src, this.size);
		}

		if (dest == null) {
			dest = createCompatibleDestImage(src, null);
		}
		NonSeperableKernel k = getDifusion(this.type);
		BufferedImage difusionsrc = new NullOp().filter(src, null);

		for (Location pt : new RasterScanner(src, false)) {
			int srcpixel = difusionsrc.getRGB(pt.col, pt.row);
			int destindex = getClosedColorfromPalette(this.palette, srcpixel);
			dest.getRaster().setSample(pt.col, pt.row, 0, destindex);
			difusionsrc = diffuseError(difusionsrc, palette[destindex], pt, k);
		}
		return dest;
	}

	private BufferedImage diffuseError(BufferedImage difsrc, Color c, Location pt, NonSeperableKernel kernel) {
		int target = c.getRGB();

		for (int b = 0; b < difsrc.getRaster().getNumBands(); b++) {
			double error = difsrc.getRaster().getSample(pt.col, pt.row, b) - (int) ((target >> ((2 - b) * 8)) & 0xFF);
			if (c.equals(Color.cyan))
				System.out.println("col: " + pt.col + ", row: " + pt.row + ", b: " + b + ", sample: "
						+ difsrc.getRaster().getSample(pt.col, pt.row, b) + ", error:" + error);
			for (Location kernelpoint : new RasterScanner(kernel.getBounds())) {
				float sample = (float) (padder.getSample(difsrc, pt.col + kernelpoint.col, pt.row + kernelpoint.row + 1,
						b) + Math.round((int) error * kernel.getValue(kernelpoint.col, kernelpoint.row)));
				try {
					difsrc.getRaster().setSample(pt.col + kernelpoint.col, pt.row + kernelpoint.row + 1, b,
							ImagingUtilities.clamp(sample, 0, 255));
				} catch (Exception e) {

				}
			}
		}
		return difsrc;
	}

	private double distance(Color c1, Color c2) {
		int dr = c1.getRed() - c2.getRed();
		int dg = c1.getGreen() - c2.getGreen();
		int db = c1.getBlue() - c2.getBlue();
		return Math.sqrt(dr * dr + dg * dg + db * db) / Math.sqrt(3);
	}

	private int getClosedColorfromPalette(Color[] palette, int srcpixel) {
		Color srccolor = new Color(srcpixel);
		int index = 0;
		double min = Double.POSITIVE_INFINITY;
		for (int i = 0; i < palette.length; i++) {
			double target = distance(palette[i], srccolor);
			if (target <= min) {
				min = target;
				index = i;
			}
		}
		return index;
	}

	public int getmax(int red, int green, int blue) {
		if (red > green && red > blue) {
			return 0;
		} else if (green > blue) {
			return 1;
		} else {
			return 2;
		}
	}

	public int getMaxBound(List<Color> list) {
		int redmax, greenmax, bluemax;
		int redmin = redmax = list.get(0).getRed();
		int greenmin = greenmax = list.get(0).getGreen();
		int bluemin = bluemax = list.get(0).getBlue();
		for (int i = 1; i < list.size(); i++) {
			if (list.get(i).getRed() > redmax) {
				redmax = list.get(i).getRed();
			}
			if (list.get(i).getRed() < redmin) {
				redmin = list.get(i).getRed();
			}
			if (list.get(i).getGreen() > greenmax) {
				greenmax = list.get(i).getGreen();
			}
			if (list.get(i).getGreen() < greenmin) {
				greenmin = list.get(i).getGreen();
			}
			if (list.get(i).getBlue() > bluemax) {
				bluemax = list.get(i).getBlue();
			}
			if (list.get(i).getBlue() < bluemin) {
				bluemin = list.get(i).getBlue();
			}
		}
		int redbound = redmax - redmin;
		int greenbound = greenmax - greenmin;
		int bluebound = bluemax - bluemin;
		int location = getmax(redbound, greenbound, bluebound);
		return location;
	}

	private void createPalette(BufferedImage src, int paletteSize) {
		List<Color> list = new ArrayList<Color>();
		for (Location pt : new RasterScanner(src, false)) {
			Color color = new Color(src.getRGB(pt.col, pt.row));
			list.add(color);
		}
		sortComponentInList(list);
		PriorityQueue<List<Color>> queue = new PriorityQueue<List<Color>>(this.size, new Comparator<List<Color>>() {

			@Override
			public int compare(List<Color> list1, List<Color> list2) {
				int l1 = getMaxBound(list1);
				int l2 = getMaxBound(list2);
				int prio1 = getRangeBounds(list1, l1);
				int prio2 = getRangeBounds(list2, l2);
				if (prio1 == prio2) {
					return 0;
				} else if (prio1 > prio2) {
					return -1;
				} else {
					return 1;
				}
			}

			private int getRangeBounds(List<Color> list, int n) {
				switch (n) {
				case 0:
					return list.get(list.size() - 1).getRed() - list.get(0).getRed();
				case 1:
					return list.get(list.size() - 1).getGreen() - list.get(0).getGreen();
				case 2:
					return list.get(list.size() - 1).getBlue() - list.get(0).getBlue();
				default:
					return 0;
				}
			}

		});

		queue.add(list);
		PriorityQueue<List<Color>> box = getAmountofBox(queue, paletteSize);
		transforToColor(box);
	}

	private void transforToColor(PriorityQueue<List<Color>> queuebox) {
		int i = 0;
		while (!queuebox.isEmpty()) {
			List<Color> box = queuebox.remove();
			double sum = 0;
			for (int j = 0; j < box.size(); j++) {
				sum += box.get(j).getRGB();
			}
			sum = sum / box.size();
			palette[i] = new Color((int) sum);
			i++;
		}
	}

	public void sortComponentInList(List<Color> list) {
		int location = getMaxBound(list);
		Collections.sort(list, new Comparator<Color>() {
			private int o1;
			private int o2;

			@Override
			public int compare(Color c1, Color c2) {
				if (location == 0) {
					o1 = c1.getRed();
					o2 = c2.getRed();
				}
				if (location == 1) {
					o1 = c1.getGreen();
					o2 = c2.getGreen();
				}
				if (location == 2) {
					o1 = c1.getBlue();
					o2 = c2.getBlue();
				}
				return Integer.compare(o1, o2);
			}
		});
	}

	private PriorityQueue<List<Color>> getAmountofBox(PriorityQueue<List<Color>> queue, int paletteSize) {
		while (queue.size() < paletteSize) {
			List<Color> list = queue.remove();
			int location = getMaxBound(list);
			int median = getMedain(list, location);
			int current = list.size() / 2;
			int cut = getCutPoint(list, current, median, location);
			List<Color> list1 = list.subList(0, cut);
			List<Color> list2 = list.subList(cut, list.size());

			sortComponentInList(list1);
			queue.add(list1);
			sortComponentInList(list2);
			queue.add(list2);
		}

		return queue;
	}

	private int getCutPoint(List<Color> list, int current, int median, int location) {
		int top = getNumberInList(list, 0, location);
		int low = getNumberInList(list, list.size() - 1, location);
		if (median < low) {
			while (current < list.size() && getNumberInList(list, current, location) == median) {
				current++;
			}
			return current;
		} else if (median > top) {
			while (current > 0 && getNumberInList(list, current, location) == median) {
				current--;
			}
			return current + 1;
		} else {
			return median;
		}

	}

	private int getNumberInList(List<Color> list, int current, int location) {
		switch (location) {
		case 0:
			return list.get(current).getRed();
		case 1:
			return list.get(current).getGreen();
		case 2:
			return list.get(current).getBlue();
		default:
			return 128;
		}
	}

	private int getMedain(List<Color> list, int location) {
		switch (location) {
		case 0:
			return list.get(list.size() / 2).getRed();
		case 1:
			return list.get(list.size() / 2).getGreen();
		case 2:
			return list.get(list.size() / 2).getBlue();
		default:
			return 128;
		}
	}

}

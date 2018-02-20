package ImageDataBase;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Scanner;

import javax.imageio.ImageIO;

import pixeljelly.scanners.Location;
import pixeljelly.scanners.RasterScanner;

public class ImageDatabase extends Thread {
	private static int rn, gn, bn;
	private static float longestDistance;
	private static float[][] A;
	private String[] urls;
	private float[][] histograms;
	public ImageDatabase( String[] urls) {
		this.urls = urls;
	}
	
	public static void main ( String[] args ) {
		
		try {
			if( args.length == 6 ) {
				if( args[0].equals("create") ) {
					rn = Integer.valueOf( args[1] );
					gn = Integer.valueOf( args[2] );
					bn = Integer.valueOf( args[3] );
					
					/* Check if Rn,Gn,Bn are in valid range */
					if( rn < 0|| rn > 8 || gn < 0 || gn > 8 || bn < 0  || bn > 8 || rn + gn + bn > 11 ) {
						throw new Exception("Wrong command.");
					}
					
					File input = new File( args[4] );
					File output = new File( args[5] );
					createImageDatabase( input,  output) ;
					
				} else { 
					/* The first word of args is invalid*/
					throw new Exception("Wrong command.");
				}
			} else if( args.length == 5 ) {
				if( args[0].equals("query") ) {
					File database = new File( args[2] );
					File responseFile = new File( args[3]);
					queryWithImage( args[1] ,  database, responseFile, Integer.valueOf( args[4] ) );
					
				} else { 
					/* The first word of args is invalid*/
					throw new Exception("Wrong command.");
				}
			} else {
				/* The length of args is invalid*/
				throw new Exception("Wrong command.");
			}
		} catch ( Exception e ) {
			System.out.println( e );
		}
	}
	
	public static void queryWithImage( String queryUrl, File database, File responseFile, int K ) throws MalformedURLException, IOException {
		/* Get resolution of pictures */
		Scanner scanner = new Scanner( database );
		String firstLine = scanner.nextLine();
		String[] temp = firstLine.split(" ");
		rn = Integer.valueOf( temp[0] );
		gn = Integer.valueOf( temp[1] );
		bn = Integer.valueOf( temp[2] );
		
		int nR = (int) Math.pow(2, rn);
		int nG = (int) Math.pow(2, gn);
		int nB = (int) Math.pow(2, bn);
		longestDistance = (float) Math.sqrt( Math.pow(128/nR/256f-(256-128/nR)/256f , 2 )
				+ Math.pow(128/nG/256f-(256-128/nG)/256f , 2 )
				+ Math.pow(128/nB/256f-(256-128/nB)/256f , 2 ) );
		A = getAOfH1H2();
		float[] queryGram = getHistogram ( queryUrl );

		ArrayList<Node> resultList = new ArrayList<Node>();
		String[] information ;
		float[] currentGram = new float[ nR * nG * nB];

		while( scanner.hasNextLine() ) {
			String line = scanner.nextLine();
			information = line.split(" ");
			if( information.length > 3 ) {

				for( int i = 0 ; i < currentGram.length; i++ ) {
					currentGram[i] = Float.valueOf( information[ i+3 ] );
				}
				
				Node newNode = new Node();
				newNode.distance = getQuadraticDistance( queryGram, currentGram );
				newNode.urls = information[0] + " "+ information[1] +" "+ information[2];
				resultList.add(newNode);
			}
		}
		
		/* Now we have an unsorted ArrayList of Node */
		Collections.sort( resultList,new Comparator<Node>(){
		    public int compare(Node n1, Node n2) {
		    	if(n1.distance - n2.distance > 0)
		    		return 1;
		    	else 
		    		return -1;
		    }
		});
		writeToResponseFile( queryUrl, resultList, responseFile, K );
	}
	
	public static void writeToResponseFile( String queryUrl, ArrayList resultList , File responseFile, int K ) throws IOException {
		FileWriter fileWriter = new FileWriter(responseFile);
		fileWriter.write( "<!DOCTYPE html><html><head><title>Pictures</title></head><body>" );
		fileWriter.write( "<div class=\"img\"> Query Image:<br> <a href=\""
								+ queryUrl
							    + "\"><img style=\"max-width:300px; max-height:300px;\" src=\""
							    + queryUrl
							    +"\"></a></div>"
						);
		fileWriter.write( "<div class=\"img\" style=\" display: inline-block; margin: 5px;\">"
							+ "Resolution: Rn: "+rn+" Gn: " + gn+ " Bn: "+ bn+"  </div><br>"	);
		int count = 0 ;
		while( count < K ) {
			Node temp = (Node) resultList.get(count);
			String[] url = temp.urls.split(" ");
			fileWriter.write( "<div style=\"display: inline-block; margin: 5px;padding:0;\" class=\"img\">"
					+ "				<a style=\" display: block;height: 10px;width: 10px;background-color: #aaa; \"  href=\"" 
								+ url[0]
								+ "\" class=\"flickr\"></a> <p>distance: "+temp.distance +" </p><a href=\""
								+ url[2]
							    + "\"><img  src=\""
							    + url[1]
							    +"\"></a> </div>"
						);
			count++;
		}
		
		fileWriter.write( "</body></html>" );
		fileWriter.close();
	}
	
	public static void createImageDatabase( File input, File output) throws IOException {

		/* Get the number of pictures in file */
		Scanner scanner = new Scanner( input );
		int count = 0;
		while( scanner.hasNextLine() ) {
			scanner.nextLine();
			count++;
		}
		scanner = new Scanner( input );
		ImageDatabase threads[];
		threads = new ImageDatabase[10];
		int numOfPicturePerThread = (int) Math.ceil( count/10f );
		
		/* For each thread, assign the url array and pass it into the thread. */
		for( int threadNo = 0; threadNo < 10; threadNo++ ) {
			String[] urlArray = new String[numOfPicturePerThread];
			
			for( int i = 0; i < numOfPicturePerThread; i++ ) {
				if( scanner.hasNextLine() ){
					urlArray[i] = scanner.nextLine();
				} else {
					break;
				}
			}
			
			threads[threadNo] = new ImageDatabase( urlArray );
		}
		scanner.close();
		
		for(int i = 0; i < 10; i++) {
			threads[i].start();
		}
		
		for(int i = 0; i < 10; i++) {
			try{
				threads[i].join();
			}
			catch(InterruptedException e) { 
				System.out.println(e);
			}
		}
		
		/* Now we have all the histograms, write them to the destination file. */
		FileWriter fileWriter = new FileWriter(output);
		fileWriter.write( rn +" "+ gn + " " + bn + " \n" );
		
		for( int i = 0; i < 10; i++ ) {
			String[] tempUrls = threads[i].getUrls();
			float[][] tempHistograms = threads[i].getHistograms();
			
			for( int indexOfPicture = 0; indexOfPicture < tempUrls.length; indexOfPicture++) {
				/* Since I use thread when creating database,
				 * not all thread have a full "tempUrls" array,
				 * so before writing url and histogram to file,
				 * I check if tempUrls[indexOfPicture] is null. */
				if( tempUrls[indexOfPicture] != null && tempUrls[indexOfPicture].length() > 0 ){
					fileWriter.write( tempUrls[indexOfPicture] + " " );
					
					for( int j = 0; j < tempHistograms[indexOfPicture].length; j++) {
						fileWriter.write( tempHistograms[indexOfPicture][j] +" " );
					}
					
					fileWriter.write("\n");
				} else {
					break;
				}
			}
		}

		fileWriter.close();
	}
	
	public void run() {
		histograms = new float[urls.length][];
		for( int i = 0; i < urls.length; i++) {
			// the url might be null, so we use try catch
			try{
				if( urls[i] !=null) {

					String[] temp = urls[i].split(" ");
					histograms[i] = getHistogram( temp[2] );
				}
			} catch( Exception e ){
				System.out.println(e);
			}
		}
	}
	
	public static float[] getHistogram( String url ) throws MalformedURLException, IOException {
		try{

			BufferedImage src = ImageIO.read( new URL( url ) );
			int nR = (int) Math.pow(2, rn);
			int nG = (int) Math.pow(2, gn);
			int nB = (int) Math.pow(2, bn);
			int length =  nR* nG * nB;
			float numberOfPixel = src.getHeight() * src.getWidth();
			float[] histogram = new float[ length ];
			for( Location pt : new RasterScanner( src, false)) { 
				Color c = new Color( src.getRGB( pt.col,  pt.row ) );
				int red = c.getRed();
				int green = c.getGreen();
				int blue = c.getBlue();
				int index = (red* nR/256)* nG * nB + green* nG/256  * nB+ blue * nB/256;
				histogram[ index ] ++;
			}
			// Normalize histogram
			for( int i=0; i<histogram.length; i++ ) {
				histogram[i] /= numberOfPixel;
			}
			return histogram;
			
		} catch( Exception e ) {
			System.out.println(e +" Please check if url of image is valid.");
				return null;
		}
	}
	
	public static float getQuadraticDistance( float[] queryGram, float[] gram2 ) {
		
		float[] difference = getDifferenceOfH1H2( queryGram, gram2 );
		float[] temp = new float[ difference.length ];
		float result = 0;
		for( int i = 0; i< temp.length; i++ ) {
			for( int j = 0 ; j < temp.length; j++ ) {
				temp[i] += difference[i] * A[j][i];
			}
		}
		for( int i=0; i< temp.length; i++ ) {
			result += temp[i]* difference[i];
		}
		return (float) Math.sqrt(result);
	}
	
	public static float[][] getAOfH1H2(){

		int nR = (int) Math.pow(2, rn);
		int nG = (int) Math.pow(2, gn);
		int nB = (int) Math.pow(2, bn);
		int length =  nR * nG * nB ;
		float[][] A = new float[ length ][ length ];
		
		for( int row = 0 ; row < length; row++ ) {
			for( int col = 0 ; col < length; col++ ) {
				
				float r1 = (row / nG / nB * (256/nR)  + 128 / nR) /256f ;
				float g1 = (row % ( nG * nB ) /nB * (256/nG) + 128 / nG) /256f;
				float b1 = (row % nB * (256/nB) + 128 / nB) /256f;
				

				float r2 = (col / nG / nB * (256/nR) + 128 / nR) /256f;
				float g2 = (col % ( nG * nB ) /nB * (256/nG) + 128 / nG) /256f;
				float b2 = (col % nB * (256/nB) + 128 / nB) /256f;
				
				A[row][col] = 1 - (float) Math.sqrt( Math.pow( r1 - r2 , 2 ) + Math.pow( g1 - g2 , 2 )+ Math.pow( b1 - b2 , 2 ) )/ longestDistance;
			}
		}
		return A;
	}
	
	public static float[] getDifferenceOfH1H2( float[] gram1, float[] gram2 ) {
		
		float[] difference = new float[ gram1.length ];
		for( int i = 0; i< gram1.length; i++ ) {
			difference[i] = gram1[i] - gram2[i];
		}
		return difference;

	}
	
	public String[] getUrls(){
		return urls;
	}
	public float[][] getHistograms(){
		return histograms;
	}
	
}

class Node {
	float distance;
	String urls;
}
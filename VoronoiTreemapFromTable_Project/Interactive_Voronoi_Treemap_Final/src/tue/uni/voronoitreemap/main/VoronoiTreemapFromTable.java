package tue.uni.voronoitreemap.main;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.opencsv.CSVReader;

import kn.uni.voronoitreemap.IO.WriteStatusObject;
import kn.uni.voronoitreemap.interfaces.data.TreeData;
import kn.uni.voronoitreemap.j2d.PolygonSimple;
import kn.uni.voronoitreemap.treemap.VoronoiTreemap;

public class VoronoiTreemapFromTable {
	
	private static String classpath = null;
	
	private static String inFile;
	private static List<String> columnNames = new ArrayList<String>();
	
	private static final int border = 6;
	private static double size = 800;
	
	/**
	 * reads a csv or tsv file and creates a voronoi treemap from the given data
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		if (args.length > 0) {
			inFile = args[0].replace('\\', '/');
			for (int i = 1; i < args.length; i++) {
				if(isNumeric(args[i]))
					size = Integer.parseInt(args[i]);
				else
					columnNames.add(args[i]);
			}
		}
		
		// create a convex root polygon
		PolygonSimple rootPolygon = new PolygonSimple();
		
		rootPolygon.add(border, border);
		rootPolygon.add(size+border, border);
		rootPolygon.add(size+border, size+border);
		rootPolygon.add(border, size+border);
		
//		int numPoints = 7;
//		for (int j = 0; j < numPoints; j++) {
//			double angle = 2.0 * Math.PI * (j * 1.0 / numPoints);
//			double rotate = 2.0 * Math.PI / numPoints / 2;
//			double y = Math.sin(angle + rotate) * height + height;
//			double x = Math.cos(angle + rotate) * width + width;
//			rootPolygon.add(x, y);
//		}
		
		List<RowData> csvRows= parseCSV(inFile, columnNames);
		Collections.sort(csvRows, new RowDataComparator());
				
		TreeData data = createHierarchy(csvRows);
		// data.setWeight("file036", 4);// increase cell size (leafs only)	
		
		VoronoiTreemap treemap = new VoronoiTreemap();
		// VoronoiCore.setDebugMode(); //shows iteration process
		treemap.setRootPolygon(rootPolygon);
		treemap.setTreeData(data);
		treemap.setCancelOnMaxIteration(true);
		treemap.setNumberMaxIterations(1500);
		treemap.setCancelOnThreshold(true);
		treemap.setErrorAreaThreshold(0.01);
//		 treemap.setUniformWeights(true);
		treemap.setNumberThreads(1);

		// add result handler
//		treemap.setStatusObject(new PDFStatusObject("miniHierarchy", treemap));
//		treemap.setStatusObject(new PNGStatusObject("miniHierarchy", treemap));
		try {
			classpath = VoronoiTreemapFromTable.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath().replace("VoronoiTreemapFromTable.jar", "");
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		treemap.setStatusObject(new WriteStatusObject(classpath + "VoroTreemap", treemap));
		treemap.computeLocked();
		List<PolygonData> polygonData = readPolygonData(classpath + "VoroTreemap.txt");
		
		createColorEncoding(polygonData, csvRows);
		
		writeToHtml(polygonData, "VoroTreemap");
		
	}
	
	/**
	 * reads a .csv file by given column names and returns a list consisting of VoroCell objects that hold
	 * the given data.
	 * 
	 * @param csvFilePath
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static List<RowData> parseCSV(String csvFilePath, List<String> columns) throws FileNotFoundException, IOException {
		
		List<RowData> voroCells = new ArrayList<RowData>();
		
		String filePath = csvFilePath;
		CSVReader reader = new CSVReader(new FileReader(filePath), '\t');
		String[] nextLine;
		String[] header = reader.readNext();
		
		List<String> levels = new ArrayList<String>();
		List<Double> ratios = new ArrayList<Double>();
		
		while ((nextLine = reader.readNext()) != null) {
			// search for given columns
			for (String c : columns) {
				// nextLine[] is an array of values from the line
				for (int i = 0; i < nextLine.length; i++) {					
					if (header[i].equals(c)) {
						String s = nextLine[i];
						if (s.contains("_"))
							s = s.replace("_", " ");
//						if (s.contains("GO:"))
//							s = s.substring(s.indexOf("~")+1, s.length());
						if(isNumeric(s)) {
							double d = Double.parseDouble(s);
							ratios.add(d);
						} else
							levels.add(s);
					}
				}
			}
			voroCells.add(new RowData(levels, ratios));
			levels.clear();
			ratios.clear();
		}
		reader.close();
		return voroCells;
	}
	
	/**
	 * checks if a string is numeric and return result as boolean.
	 * 
	 * @param str
	 * @return
	 */
	public static boolean isNumeric(String str) {  
		try {  
			Double.parseDouble(str);  
		}  
		catch(NumberFormatException nfe) {  
			return false;  
		}  
		return true;  
	}
	
	/**
	 * creates a hierarchy from a alphabetically sorted list of VoroCell objects 
	 * and returns the structure as TreeData object.
	 * 
	 * @param cellData
	 * @return TreeData object
	 */
	private static TreeData createHierarchy(List<RowData> cellData) {
		
		TreeData data = new TreeData();
				
		List<String> duplicateHelper = new ArrayList<String>();
		
		int i = 0;
		while (true) {	// add level1 to root
			String p = cellData.get(i).getLevels().get(0);
			
			data.addLink(p, "root");
			data.setRoot("root");
			
			List<String> parentList = new ArrayList<String>();
			parentList.add(p);
			
			i = addLevel(data, cellData, parentList, duplicateHelper, "", 0, i);	// add next level to root
			if (i >= cellData.size())
				return data;
		}
	}
	
	/**
	 * 
	 * add hierarchy levels recursively for dynamic amount of levels.
	 * 
	 * @param data
	 * @param cellData
	 * @param prevParentList
	 * @param duplicateHelper
	 * @param parentTag
	 * @param prevNum
	 * @param i
	 * @return
	 */
	private static int addLevel(TreeData data, List<RowData> cellData, List<String> prevParentList, List<String> duplicateHelper, String parentTag, int prevNum, int i) {
		
		List<String> parentList = cellData.get(i).getLevels().subList(0, prevNum+1);
		
		while (parentList.equals(prevParentList)) {
			String currName = cellData.get(i).getLevels().get(prevNum+1);
			String prevName = cellData.get(i).getLevels().get(prevNum);
			
			String duplicateTag = "";
			int occurences = 1;
			if (duplicateHelper.contains(currName)) {
				occurences += Collections.frequency(duplicateHelper, currName);
				duplicateTag = "(" + occurences + ")";
			}
			
			data.addLink(currName+duplicateTag, prevName+parentTag);			
			
			// set weight based on occurences of this element in the data
			double weight = 1.0d/(occurences);
			if(prevNum+1 == cellData.get(i).getLevels().size()-1) {
				data.setWeight(currName, weight);					
				for (int j = 2; j <= occurences; j++) {				
					data.setWeight(currName+"("+j+")", weight);
				}
			}
			
			duplicateHelper.add(currName);
			
			if (prevNum+2 < cellData.get(i).getLevels().size()) {
				prevParentList.add(currName);				
				i = addLevel(data, cellData, prevParentList, duplicateHelper, duplicateTag, prevNum+1, i);	// add next level to this level
				prevParentList.remove(prevParentList.size()-1);
			} else
				i++;
			
			if(i >= cellData.size())
				return cellData.size();
			
			parentList = cellData.get(i).getLevels().subList(0, prevNum+1);
		}
		return i;
	}
	
	/**
	 * read all the data of each polygon in the given text file and return it as PolygonData object.
	 * 
	 * @param txtFile
	 * @return complete data of each polygon
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static List<PolygonData> readPolygonData(String txtFile) throws FileNotFoundException, IOException {
		
		List<PolygonData> polygonData = new ArrayList<PolygonData>();
		
		String name = null;
		int level = 0;
		PolygonSimple poly = null;		
		
		CSVReader reader = new CSVReader(new FileReader(txtFile), ';');
		String[] nextLine;
		String[] header = reader.readNext();
		
		while ((nextLine = reader.readNext()) != null) {
			for (int i = 0; i < nextLine.length; i++) {
				switch (header[i]) {
				case "name":
					//remove duplicate tags
					if(nextLine[i].contains("(")){
						name = nextLine[i].substring(0, nextLine[i].indexOf("("));
						break;
					}
					name = nextLine[i]	;
					break;
				
				case "hierarchyLevel":
					level = Integer.parseInt(nextLine[i]);
					break;
				
				case "polygonPoints x1,y2,x2,y2":					
					String[] points = nextLine[i].split(",");
					double[] x = new double[points.length/2];
					double[] y = new double[points.length/2];
					
					int count = 1;
					for (int j = 0; j < points.length; j++) {						
						if (count%2 != 0) {
							x[j/2] = Double.parseDouble(points[j]);
						} else {
							y[(j-1)/2] = Double.parseDouble(points[j]);
						}
						count++;
					}
					poly = new PolygonSimple(x, y);
					break;
					
				default:
					break;
				}
			}
			polygonData.add(new PolygonData(name, level, poly));
		}	
		
		reader.close();
		return polygonData;
	}
	
	/**
	 * set the color of each polygon to visualize the ratio of the represented protein
	 * 
	 * @param polygonData
	 * @param proteinData
	 */
	private static void createColorEncoding(List<PolygonData> polygonData, List<RowData> rowsData) {
		
		List<Double> ratios = new ArrayList<Double>();
		List<Double> ratiosLower = new ArrayList<Double>();
		List<Double> ratiosUpper = new ArrayList<Double>();
		
		double threshold = 0.0d;
		
		for (RowData row : rowsData) {
			ratios.add(row.getRatios().get(0));
		}
		
		double sum = 0.0d;
		for (double r : ratios) {
			sum += r;
		}
		threshold = sum/ratios.size();
		
		for (Double r : ratios) {
			if (r < threshold)
				ratiosLower.add(r);
			if (r > threshold)
				ratiosUpper.add(r);
		}

		for (PolygonData pDat : polygonData) {
			for (RowData row : rowsData) {
				if (!pDat.getName().equals(row.getLevels().get(row.getLevels().size()-1)))
					continue;
					
				pDat.setRatios(row.getRatios());
				
				double r = pDat.getRatios().get(0);
				
				if(r == 0)
					continue;
				
				if (r < threshold) {
					int normRatioLower = (int)normalize(r, getMin(ratiosLower), getMax(ratiosLower), 30.0d, 100.0d);
					Color colBlue = new Color(0,100,255-normRatioLower);
					pDat.setColor(colBlue);
				}
				
				if (r > threshold) {
					int normRatioUpper = (int)normalize(r, getMin(ratiosUpper), getMax(ratiosUpper), 30.0d, 100.0d);
					Color colRed = new Color(255-normRatioUpper,100,0);
					pDat.setColor(colRed);
				}
			}
		}
		
	}

	/**
	 * returns the minimum value of the given list of doubles
	 * 
	 * @param values
	 * @return minimum value of the list
	 */
	public static double getMin(List<Double> values) {
		
		double min = Double.MAX_VALUE;
		
		for (double d : values) {
			if (d < min)
				min = d;
		}
		return min;
	}
	
	/**
	 * returns the maximum value of the given list of doubles
	 * 
	 * @param values
	 * @return maximum value of the list
	 */
	public static double getMax(List<Double> values) {
		
		double max = -Double.MAX_VALUE;
		
		for (double d : values) {
			if (d > max)
				max = d;
		}
		return max;
	}
	
	/**
	 * normalize a single double value into the range [left, right] 
	 * given a minimum and maximum and return the normalized value.
	 * 
	 * @param values
	 * @param left
	 * @param right
	 * @return normalize double value
	 */
	public static double normalize(double d, double min, double max, double left, double right) {
		return (((d-min)/(max-min))*(right-left))+left;
	}
	
	/**
	 * Write several information and svg content that shows the finished Voronoi treemap into existing html file.
	 * 
	 * @param polygonData
	 * @param outFile
	 * @throws IOException
	 */
	private static void writeToHtml(List<PolygonData> polygonData, String outFile) throws IOException {
		StringBuilder contentBuilder = new StringBuilder();
		try {
		    BufferedReader in = new BufferedReader(new FileReader(classpath + outFile + "Template" + ".html"));
		    String str;		    
		    while ((str = in.readLine()) != null) {
		    	if(str.contains("~TreemapDepth~")) {
		    		str = str.replace("~TreemapDepth~", "var depth = 3" + ";");
//		    	} else if(str.contains("~LevelNames~")) {
//	    			str = str.replace("~LevelNames~", "var levelNames = " + ";");
	    		} else if(str.contains("~RatioCount~")) {
	    			str = str.replace("~RatioCount~", "var ratioCount = " + polygonData.get(2).getRatios().size() + ";");
//	    		} else if(str.contains("~RatioNames~")) {
//	    			str = str.replace("~RatioNames~", "var ratioNames = " + ";");
	    		} else if(str.contains("~SVG~")) {
		        	str = str.replace("~SVG~", "var svgContent = ");
		        	
		        	//head and opening tags
		        	str += "\"<svg class='treemap' width='"+size+"' height='"+size+"' viewBox='0 0 "+(size+border*2)+" "+(size+border*2)+"' "
		        			+ "xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'>";
		        	
		        	// polygon settings
		        	str += "<g id='polygons'>";
		    		for (PolygonData pDat : polygonData) {
		    			str += "<polygon class='lvl" + pDat.getLevel() + "' name='" + pDat.getName() + "' ";
		    			
		    			// ratios
		    			if (pDat.getRatios() != null) {
		    				for (int i = 0; i < pDat.getRatios().size(); i++) {				
		    					str += "ratio" + (i+1) + "='" + pDat.getRatios().get(i) + "' ";
		    				}
		    			}
		    			//polygon points
		    			str += "points='";

		    			PolygonSimple p = pDat.getPolygon();
		    			for (int i = 0; i < p.length; i++) {				
		    				str += p.getXPoints()[i] + "," + p.getYPoints()[i] + " ";
		    			}
		    			
		    			double strokeWidth = 8-normalize(Math.log(pDat.getLevel()), 0, 1.5, 2, 8);
		    						
		    			String hexColor = String.format("#%02x%02x%02x", pDat.getColor().getRed(), pDat.getColor().getGreen(), pDat.getColor().getBlue());
		    			
		    			str += "' style='fill:" + hexColor + ";stroke:black;stroke-width:" + strokeWidth + "' />";
		    		}
		    		str += "</g>";
		        	
		    		// text settings
		    		str += "<g id='names'>";
		    		String name = null;

		    		float fontSize = 12;
		    		
		    		Rectangle2D bounds = null;
		    		double width = 0.0d;
		    		double height = 0.0d;
		    		
		    		for (PolygonData pDat : polygonData) {
		    			name = pDat.getName().replace(" ", "\n");
		    			
		    			Font font = new Font("Helvetica", Font.PLAIN, (int)fontSize);
		    			fontSize = fitTextIntoPolygon(name, font, pDat);
		    			font = font.deriveFont(fontSize);
		    			
		    			bounds = font.getStringBounds(name, new FontRenderContext(font.getTransform(), true, true));
		    			width = bounds.getWidth();
		    			height = bounds.getHeight();
		    			
		    			double posX = pDat.getPolygon().getCentroid().getX();
		    			double posY = pDat.getPolygon().getCentroid().getY();
		    			
		    			// line break adjustment
		    			if (name.contains("\n")) {				
		    				int count = name.length()-name.replace("\n", "").length() + 1;
		    				
		    				posY -= bounds.getHeight()*count/1.5/2;
		    				
		    				for (String line : name.split("\n")) {
		    					bounds = font.getStringBounds(line, new FontRenderContext(font.getTransform(), true, true));
		    					
		    					posX = (pDat.getPolygon().getCentroid().getX() - bounds.getWidth()/2.0);
		    					posY += bounds.getHeight()/1.5;
		    					
		    					str += "<text class='lvl" + pDat.getLevel() + "' x='" + posX + "' y='" + posY + "' style='font-size:" + fontSize + "px;fill:white;'>" + line + "</text>";					
		    				}
		    			} else
		    				str += "<text class='lvl" + pDat.getLevel() + "' x='" + (posX-width/2.0) + "' y='" + (posY+height/2.0) + "' style='font-size:" + fontSize + "px;fill:white;'>" + name + "</text>";
		    		}
		    		str += "</g>";

		    		// closing tags
		    		str += "</svg>\";";
		        }
		        contentBuilder.append(str+"\n");
		    }
		    in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String content = contentBuilder.toString();
		
		try {
			FileWriter htmlWriter = new FileWriter(classpath + outFile + ".html");
			htmlWriter.write(content);
			htmlWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * find a size for the given text to fit into the given polygon and return it
	 * 
	 * @param name
	 * @param font
	 * @param pDat
	 * @return fitting size for the given text
	 */
	private static float fitTextIntoPolygon(String name, Font font, PolygonData pDat) {
		
		Font f = font;
		float size = 1000;
		f = f.deriveFont(size);
		
		double cx = pDat.getPolygon().getCentroid().getX();
		double cy = pDat.getPolygon().getCentroid().getY();
		
		Rectangle2D bounds = f.getStringBounds(name, new FontRenderContext(f.getTransform(), true, true));
		double width = bounds.getWidth();
		double height = bounds.getHeight();
		
		Rectangle2D rect = new Rectangle2D.Double(cx-width/2, cy-height/2, width, height);
		
		while (!pDat.getPolygon().contains(rect)) {
			size *= 0.9;
			f = f.deriveFont(size);
			
			bounds = f.getStringBounds(name, new FontRenderContext(f.getTransform(), true, true));
			width = bounds.getWidth();
			height = bounds.getHeight();
			
			// adjust size of text with line breaks
			if (name.contains("\n")) {
				
				String longest = "";
				int count = 0;

				for (String line : name.split("\n")) {
					if(longest.length() < line.length())
						longest = line;
					count++;
				}
				
				bounds = f.getStringBounds(longest, new FontRenderContext(f.getTransform(), true, true));
				width = bounds.getWidth();
				height = bounds.getHeight()*count;
				
				rect = new Rectangle2D.Double(cx-width/2, cy-height/2,  bounds.getWidth(), bounds.getHeight()*count);
			}
			
			rect = new Rectangle2D.Double(cx-width/2, cy-height/2, width, height);
		}
		size*=0.7;
		return size;
	}
	
}

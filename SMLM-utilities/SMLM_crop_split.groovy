// @File (label="Select a directory containing the files to process", style="directory") directory
// @File (label="Select a directory to save the even indexed frames", style="directory") outDirectoryOdd
// @File (label="Select a directory to save the odd indexed frames", style="directory") outDirectoryEven
// @String(label="Specify a file extension", value="nd2") ext
// @Boolean(label="Search sub-directories", value=false) subDirSearch
// @Integer(label="Number of frames to load for ROI selection", value=10) frameRange

// Author: Jeremy Pike

/* For preprocessing of raw SMLM data to calcualte FRC values: 
 * - Processes all file in a specified directory (and optionally sub-directories)
 * - Crops all frames based based on user specified ROIs which are selected using a subset of frames
 * - Splits the data into two stacks containing the odd and even frames
 * - Saves these two stacks as .tif files in specified directories
 */

import org.apache.commons.io.FileUtils
import java.util.Iterator
import java.io.File
import java.awt.Rectangle
import java.util.ArrayList
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.HyperStackConverter;
import ij.measure.Calibration;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.gui.WaitForUserDialog
import loci.formats.ChannelSeparator;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLServiceImpl;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import ome.units.UNITS;

// create string array of allowed extensions 
String[] extArray = new String[1];
extArray[0] = ext;
// get iterator for all files in specified directory (and optionally sub-directories) 
Collection<File> fileCollection = FileUtils.listFiles(directory, extArray, subDirSearch);
Iterator<File> fileIterator = fileCollection.iterator();

// to store user defined rectangular ROIs
ArrayList croppingRects = new ArrayList();

int numFiles = 0;
// iterate through all files
while (fileIterator.hasNext()) {
	
	numFiles ++;
	File file = fileIterator.next();
	// print current file path to console
	println(file.getAbsolutePath());
	// reader to load specified file
	ImageProcessorReader  r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
	// set up metadata reading
	OMEXMLServiceImpl OMEXMLService = new OMEXMLServiceImpl();
	r.setMetadataStore(OMEXMLService.createOMEXMLMetadata());
	// set reader to specified file
	r.setId(file.getAbsolutePath());
	// get metadata
	MetadataRetrieve meta = (MetadataRetrieve) r.getMetadataStore();
	// set reader to first series
	r.setSeries(0);
	// load first few frames (user defined) as a stack
	ImageStack stack = new ImageStack(r.getSizeX(), r.getSizeY());
	for (int n = 0; n < frameRange; n++) {
		ImageProcessor ip = r.openProcessors(n)[0];
		stack.addSlice("" + (n + 1), ip);
	}
	// create ImagePlus using stack
	ImagePlus imp = new ImagePlus("", stack);
	// convert to HyperStack with correct dimensions
	imp = HyperStackConverter.toHyperStack(imp, 1, 1, frameRange);
	// set calibration for data using metadata
	Calibration cali = new Calibration();
	cali.pixelWidth = meta.getPixelsPhysicalSizeX(0).value(UNITS.MICROMETER).doubleValue();
	cali.pixelHeight = meta.getPixelsPhysicalSizeY(0).value(UNITS.MICROMETER).doubleValue();
	cali.setUnit("micron");
	imp.setGlobalCalibration(cali);
	// set imp title to series name
	imp.setTitle(meta.getImageName(0));
	// diplay data 
	imp.show();
	// ask user to draw a rectangular roi
	new WaitForUserDialog("Please select a rectangular ROI").show();
	// get and store this rectangle
	croppingRects.add(imp.getRoi().getBounds());
	// close open window
	imp.close();
	// close reader
	r.close();
}

// iterators for all files and ROIs 
fileIterator = fileCollection.iterator();
Iterator<Rectangle> rectIterator = croppingRects.iterator();
// iterate through all files
int count = 0;
while (fileIterator.hasNext()) {
	count ++;
	println ("Processing file " + count + " of " + numFiles)
	File file = fileIterator.next();
	// reader to load specified file
	ImageProcessorReader  r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
	// set up metadata reading
	OMEXMLServiceImpl OMEXMLService = new OMEXMLServiceImpl();
	r.setMetadataStore(OMEXMLService.createOMEXMLMetadata());
	// set reader to specified file
	r.setId(file.getAbsolutePath());
	// get metadata
	MetadataRetrieve meta = (MetadataRetrieve) r.getMetadataStore();
	// set reader to first series
	r.setSeries(0);
	
	// retrive position and size of ROI and round these values to nearest integer
	Rectangle rect = rectIterator.next();
	int x = (int) (rect.x + 0.5);
	int y = (int) (rect.y + 0.5);
	int width = (int) (rect.width + 0.5);
	int height = (int) (rect.height + 0.5);
	
	// load cropped data all frames and store in two stacks for odd and even numbered frames
	ImageStack stackOdd = new ImageStack(width, height);
	ImageStack stackEven = new ImageStack(width, height);
	for (int n = 0; n < r.getImageCount(); n++) {
		ImageProcessor ip = r.openProcessors(n, x, y, width, height)[0];
		// if even
		if((n % 2) == 0) {
			stackEven.addSlice("" + (n + 1), ip);
		} else {
			stackOdd.addSlice("" + (n + 1), ip);
		}
	}
	// create ImagePlus objects using stack
	ImagePlus impOdd = new ImagePlus("", stackOdd);
	ImagePlus impEven = new ImagePlus("", stackEven);
	// convert to HyperStacks with correct dimensions
	impOdd = HyperStackConverter.toHyperStack(impOdd, 1, 1, stackOdd.getSize());
	impEven = HyperStackConverter.toHyperStack(impEven, 1, 1, stackEven.getSize());
	// set calibration for data using metadata
	Calibration cali = new Calibration();
	cali.pixelWidth = meta.getPixelsPhysicalSizeX(0).value(UNITS.MICROMETER).doubleValue();
	cali.pixelHeight = meta.getPixelsPhysicalSizeY(0).value(UNITS.MICROMETER).doubleValue();
	cali.setUnit("micron");
	impOdd.setGlobalCalibration(cali);
	impEven.setGlobalCalibration(cali);
	// set title to series name
	impOdd.setTitle(meta.getImageName(0) + "_odd");
	impEven.setTitle(meta.getImageName(0) + "_even");
	// save two stacks in specified directories
	IJ.saveAs(impOdd, "Tiff", outDirectoryOdd.getPath() + "\\" + file.getName() + ".tif");
	IJ.saveAs(impEven, "Tiff", outDirectoryEven.getPath() + "\\" + file.getName() + ".tif");
	// close reader
	r.close();
}

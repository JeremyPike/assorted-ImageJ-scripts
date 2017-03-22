// @ImageJ ij 
// @File (label="Select a directory containing the files to process", style="directory") directory
// @File (label="Select an output directory for the output files", style="directory") outDirectory
// @String(label="Specify a file extension", value=".czi") ext

// Author: Jeremy Pike

/*
 * Converts each series in every file (with a specified extension) in a given directory to a tif file
 * Useful for import into Ilastik.
 * 
 * To load each tile as an individual series then bioformats should be configured to do this 
 * for czi files go to Bio-Formats Plugin Configuration -> Formats -> Zeiss CZI -> untick Autostitch
 * this works using Bio-formats version 5.3.4
 */


import ij.IJ;
import ij.ImagePlus;
import ij.plugin.HyperStackConverter;
import ij.measure.Calibration;
import ij.ImageStack;
import ij.process.ImageProcessor;
import loci.formats.ChannelSeparator;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLServiceImpl;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import ome.units.UNITS;


// Get all files in specified directory 
String[] fileList = directory.list();


for (int f = 0; f < fileList.length; f++) {

	// Check current file has specified extension
	if (fileList[f].toLowerCase().endsWith(ext)) { 

		// reader to load specified file
		ImageProcessorReader  r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
		// set up metadata reading
		OMEXMLServiceImpl OMEXMLService = new OMEXMLServiceImpl();
		r.setMetadataStore(OMEXMLService.createOMEXMLMetadata());
		// set reader to specified file
		r.setId(directory.getPath() + "\\" + fileList[f]);
		// get metadata
		MetadataRetrieve meta = (MetadataRetrieve) r.getMetadataStore();
		// get number of series in specified file
		int numSeries = meta.getImageCount();
		
		
		// loop through all series in files
		for (int i = 0; i < numSeries; i++) {
			println ("Processing file " + (f + 1) + " of " + fileList.length + ", series " + (i + 1) + " of " + numSeries)
			// set reader to current series
			r.setSeries(i);
			// load data for current series as a stack
			ImageStack stack = new ImageStack(r.getSizeX(), r.getSizeY());
			for (int n = 0; n < r.getImageCount(); n++) {
				ImageProcessor ip = r.openProcessors(n)[0];
				stack.addSlice("" + (n + 1), ip);
			}
			// create ImagePlus using stack
			ImagePlus imp = new ImagePlus("", stack);
			// convert to HyperStack with correct dimensions
			imp = HyperStackConverter.toHyperStack(imp, r.getSizeC(), r.getSizeZ(), r.getSizeT());
			// set calibration for data using metadata
			Calibration cali = new Calibration();
			cali.pixelWidth = meta.getPixelsPhysicalSizeX(i).value(UNITS.MICROMETER).doubleValue();
			cali.pixelHeight = meta.getPixelsPhysicalSizeY(i).value(UNITS.MICROMETER).doubleValue();
			if (r.getSizeZ() > 1) {
				cali.pixelDepth = meta.getPixelsPhysicalSizeZ(i).value(UNITS.MICROMETER).doubleValue();
			}
			cali.setUnit("micron");
			imp.setGlobalCalibration(cali);
			// set imp title to series name
			imp.setTitle(meta.getImageName(i));

			// save series as tif in output directory
			IJ.saveAs(imp, "Tiff", outDirectory.getPath() + "\\" + fileList[f].substring(0, fileList[f].length() - ext.length())  + "_series" + i + ".tif");
		}
		// close the reader
		r.close();
	}

}


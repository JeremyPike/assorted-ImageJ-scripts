
// Author: Jeremy Pike, Image Analyst for COMPARE

/*
 * Macro to process "Simple Segmentation" files produced by ilastik pixel classification project
 * Ilastik output files should end in "_Simple Segmentation.tif" and be "unsigned 8-bit" tif format
 * Raw data should be saved as tif files in the same directory as the segmentations
 */


#@File(label = "specify a folder containing images", style = "directory") directory
#@Integer(label = "specify the label to be analysed") labelNumber

// Turn on batch processing

setBatchMode(false);

// close any open windows
run("Close All");

// Get the names of all files in given directory
fileNames = getFileList(directory);

// ilastik segmentation files should end with this
segIdentifier = "_Simple Segmentation.tif"; 


// Loop through the number of files in the directory
for  (i = 0; i < fileNames.length; i = i + 1)  {
 	
	// Check if its a segmentatyion file
	if (endsWith(fileNames[i], segIdentifier)) {
		
		// Open the segmentation
		open(directory + File.separator + fileNames[i]);
		rename("segmentation");
		
		// open the corresponding data
		rawFileName = substring(fileNames[i], 0, lengthOf(fileNames[i]) - lengthOf(segIdentifier)) + ".tif";
		open(directory + File.separator + rawFileName);
		rename(rawFileName);
		// redirect measurments to original data
		run("Set Measurements...", "area mean standard min integrated median display redirect=" + rawFileName + " decimal=3");
		
		// threshold chosen label on segmentation channel
		selectWindow("segmentation");
		setAutoThreshold("Default dark");
		setThreshold(labelNumber, labelNumber);
		setOption("BlackBackground", false);
		run("Convert to Mask");
		//run("Fill Holes");

		//////// put recorded commands here //////////
		
		// summary of measuresments for each connected component
		run("Analyze Particles...", "size=0-Infinity summarize");
		// create selection around binary area
		run("Create Selection");
		// measure some parameters
		run("Measure");
		/////////////////////////////////////////////
		
		// Close all windows
		run("Close All");
	}
}
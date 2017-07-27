
// Author: Jeremy Pike, Image Analyst for COMPARE, The University of Birmingham

/*
 * This macro batch proccesses two channel 2D data and computes 
 * the Manders coefficients. Proprocessing and automated 
 * thresholding are peformed. 
 * 
 * Note the file reading is performed for a specific naming convention 
 * and format and would need to be adapted for othe use cases
 */

// If this script is useful then please cite:
// Pike, Jeremy A., et al. "Quantifying receptor trafficking and colocalization with confocal microscopy." Methods 115 (2017): 42-54.

///////////////////////////////////////////////////////////////////////

// Ask the user for a directory containing the file
#@File directory (label = "please specify a directory", style = "directory")
// Ask user for pre-processing parameters
#@Double medianRad (label = "please specify radius for median filter (pixels)", value = 1)
#@Double BSRad (label = "please specify radius for background subtraction (pixels)", value = 20)
// Ask if user wants to inspect the results
#@Boolean inspect (label = "do you want to inspect the results as they are calculated", value = false)


// Turn batch mode on or off as appropriate
if (inspect) {
	setBatchMode(false);
} else {
	setBatchMode(true);
}

// Get all files in specified directory
fileNames = getFileList(directory);

// Close any open windows and clear results table
run("Close All");
run("Clear Results");

// Count to store the number of il36r files
count = 0;
// iterate through all files
for (i = 0; i < fileNames.length; i = i + 1) {
	// increase count by one if current file is a il36r
	if (startsWith(fileNames[i], "cd31 and il-36R-pos_CY5") == true) {
		count = count + 1;
	}
}

// create empty arrays for Manders coefficients and file names
M1 = newArray(count);
M2 = newArray(count);
il36rLabels = newArray(count);
cd31Labels = newArray(count);


// reset count to zero as will use again
count = 0;

// iterate through all files
for (i = 0; i < fileNames.length; i = i + 1) {
	
	// if file is for the il36r channel
	if (startsWith(fileNames[i], "cd31 and il-36R-pos_CY5") == true) {
		
		//open, rename and close all channels apart from the red
		open(fileNames[i]);
		rename("il36r");
		run("Split Channels");
		selectWindow("il36r (blue)");
		close();
		selectWindow("il36r (green)");
		close();
		selectWindow("il36r (red)");
		rename("il36r");
		
		//median filter with specified radius
		run("Median...", "radius=" + medianRad);
		//rolling ball background subtractrion with specified radius
		run("Subtract Background...", "rolling=" + BSRad);
		// duplicate and Otsu threshold
		run("Duplicate...", "title=il36r_thresh");
		setAutoThreshold("Otsu dark");
		setOption("BlackBackground", false);
		run("Convert to Mask");

		// find and open the correspond image for the cd31 channel
		for (j = 0; j < fileNames.length; j = j + 1) {
			if (startsWith(fileNames[j], "cd31 and il-36R-pos_RFP") == true && endsWith(fileNames[j], substring(fileNames[i], lengthOf(fileNames[i]) - 8, lengthOf(fileNames[i]))) == true) {
				cd31FileName = fileNames[j];
			}
		}
		open(cd31FileName);	
		
		//open, rename and close all channels apart from the blue
		rename("cd31");
		run("Split Channels");
		selectWindow("cd31 (blue)");
		rename("cd31");
		selectWindow("cd31 (green)");
		close();
		selectWindow("cd31 (red)");
		close();
		
		//median filter with specified radius
		run("Median...", "radius=" + medianRad);
		//rolling ball background subtractrion with specified radius
		run("Subtract Background...", "rolling=" + BSRad);
		// duplicate and Otsu threshold
		run("Duplicate...", "title=cd31_thresh");
		setAutoThreshold("Otsu dark");
		setOption("BlackBackground", false);
		run("Convert to Mask");
		
		// measure the total signal of each channels within their respective masks
		run("Set Measurements...", "area mean min integrated display redirect=il36r decimal=3");
		selectWindow("il36r_thresh");
		run("Create Selection");
		run("Measure");
		run("Set Measurements...", "area mean min integrated display redirect=cd31 decimal=3");
		selectWindow("cd31_thresh");
		run("Create Selection");
		run("Measure");

		// calculate the mask of colocalizing pixels
		imageCalculator("AND create", "il36r_thresh","cd31_thresh");
		selectWindow("Result of il36r_thresh");
		rename("colocalizing mask");

		// measure the total signal of each channels within the colocalizing mask
		run("Set Measurements...", "area mean min integrated display redirect=il36r decimal=3");
		selectWindow("colocalizing mask");
		run("Create Selection");
		run("Measure");
		run("Set Measurements...", "area mean min integrated display redirect=cd31 decimal=3");
		selectWindow("colocalizing mask");
		run("Create Selection");
		run("Measure");
		
		// calculate the Manders coefficients from the measurement results
		il36rLabels[count] = fileNames[i];
		cd31Labels[count] = cd31FileName;
		M1[count] = getResult("IntDen", 2) / getResult("IntDen", 0);
		M2[count] = getResult("IntDen", 3) / getResult("IntDen", 1);

		// if inspecting the data then print the calculated values to the log
		if (inspect) {
			print("il36r image: " + il36rLabels[count]);
			print("cd31 image: " + cd31Labels[count]);
			print("M1: " + M1[count]);
			print("M2: " + M2[count]);
			// wait for user confirmation to continue
			waitForUser("Continue");
		}

		// add one to count so we keep track of array indices
		count = count + 1;
		
	}
	// close open windows and clear the results table
	run("Close All");
	run("Clear Results");
}

// show the filenames and calculated Manders coefficients in a new table
Array.show("Manders coeffecients", il36rLabels, cd31Labels, M1, M2);

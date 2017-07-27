

// Author: Jeremy Pike, Image Analyst for COMPARE

/*
 * Detects ROI of interest using a median filter and Otsu thresholding.
 * Spots are detected using a Laplacian of Gaussian filter for the whole image.
 * The number of spots in each ROI is then calculated and displated in a results table
 */

// This script makes use of the TrackMate plugin to detect spots:
// Tinevez, Jean-Yves, et al. "TrackMate: An open and extensible platform for single-particle tracking." Methods (2016).

import ij.IJ
import ij.ImagePlus
import ij.plugin.ChannelSplitter
import ij.measure.ResultsTable
import ij.measure.Calibration;
import ij.gui.WaitForUserDialog
import ij.plugin.frame.RoiManager
import fiji.plugin.trackmate.Model
import fiji.plugin.trackmate.Settings
import fiji.plugin.trackmate.detection.LogDetectorFactory
import fiji.plugin.trackmate.features.FeatureFilter
import fiji.plugin.trackmate.TrackMate
import fiji.plugin.trackmate.features.spot.SpotIntensityAnalyzerFactory
import fiji.plugin.trackmate.Spot
import fiji.plugin.trackmate.SelectionModel
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer
import org.apache.commons.io.FileUtils

// Ask user for a bunch of paramters
#@ File(label="Select a directory containing the files to process", style="directory") directory
#@ String(label="Specify a file extension", value="tif", persist=false) ext
#@Double(label="Specify radius for median filter (pixels)", value=5, persist=false) medianRadius
#@Double(label="Specify minimumNucleusSize (pixels)", value=3000, persist=false) minNucSize
#@ Double(label="Specify spot radius (pixels)", value=6, persist=false) spotRadius
#@ Double(label="Specify quality threshold for spots", value=25, persist=false) spotThreshQual
#@ Integer(label="Specify channel for spots", value=1, persist=false) spotChannel
#@ Integer(label="Specify channel for roi", value=2, persist=false) roiChannel
#@ boolean(label="Display results", value=false, persist=false) display


// Get the current roi manager if it exists
#@ RoiManager (required = false) rm

// create RoiManager if none is present
if (rm == null) {
	rm = new RoiManager()
}


///// settings for spot detections /////
settings = new Settings()
// use a Laplacian of Gaussian detector
settings.detectorFactory = new LogDetectorFactory()
// threshold spots based on the quality measure
settings.addSpotFilter(new FeatureFilter('QUALITY', spotThreshQual, true))
// create and fill map with detector settings
detectMap = settings.detectorFactory.getDefaultSettings()
detectMap.put('RADIUS', spotRadius)
detectMap.put('DO_MEDIAN_FILTERING', false)
detectMap.put('DO_SUBPIXEL_LOCALIZATION', true)
detectMap.put('THRESHOLD', 0.0d)
detectMap.put('TARGET_CHANNEL', spotChannel - 1)
settings.detectorSettings = detectMap; 

// find all files in specified directory with specified file format
exts = new String[1]
exts[0] = ext
files = FileUtils.listFiles(directory, exts, true)

// create empty table
rt = new ResultsTable()
// fill empty cells of results table to NaN rather than zero
rt.setNaNEmptyCells(true)


// loop though files
for (int i = 0; i < files.size(); i++) {
	println("Processing file " + (i + 1) + " of " + files.size())
	File file = files.get(i)
	// open file
	imp = IJ.openImage(file.getAbsolutePath())

	// clear any calibration (for simplicity)
	cali = new Calibration()
	cali.pixelWidth = 1
	cali.pixelHeight = 1
	cali.pixelDepth = 1
	cali.setUnit("pixel")
	imp.setCalibration(cali)

	// point TrackMate at image
	settings.setFrom(imp)
	// detect spots
	trackmate = detectSpots(settings)
	
	//// Detect ROI ////

	// split channels
	channels = ChannelSplitter.split(imp)
	// median filter on ROI channel
	IJ.run(channels[roiChannel - 1], "Median...", "radius=" + medianRadius)
	// otsu threshold on ROI channel
	IJ.setAutoThreshold(channels[roiChannel - 1], "Otsu dark")
	IJ.run(channels[roiChannel - 1], "Convert to Mask", "")
	// fill holes and watershed to seperate touching ROI (assumed convex)
	IJ.run(channels[roiChannel - 1], "Fill Holes", "")
	IJ.run(channels[roiChannel - 1], "Watershed", "")

	// clear ROI manager and fill with new ROIs from current image with specified minimum size
	// ROI touching the edge are excluded
	rm.reset()
	IJ.run(channels[roiChannel - 1], "Analyze Particles...", "size=" + minNucSize + "-Infinity exclude clear add")

	// array to hold spot counts in each ROI
	spotRoiCounts = new int[rm.getCount()]
	
	// iterate though all visible spots
	iterable = trackmate.getModel().getSpots().iterable(true)
	for (Spot spot : iterable) {
		// get coordinates of spot
		x = (int) Math.round(spot.getFeature(Spot.POSITION_X));
		y = (int) Math.round(spot.getFeature(Spot.POSITION_Y));
		// iterate through all ROIs
		for (j = 0; j < rm.getCount(); j++) {
			// check if current spot is in current ROI
			if (rm.getRoi(j).contains(x, y))
				// add one to the current ROI count if true
				spotRoiCounts[j]++
		}
	}

	// record spot counts in results table
	for (j = 0; j < spotRoiCounts.length; j++) 
			rt.setValue(file.getName(), j, spotRoiCounts[j])

	// if user requested to inspect results
	if (display) {
		
		// display spots on image
	    SelectionModel selectionModel = new SelectionModel(trackmate.getModel());
		HyperStackDisplayer displayer =  new HyperStackDisplayer(trackmate.getModel(), selectionModel, imp);
		displayer.render();
		displayer.refresh();
		// display ROIs
		rm.runCommand(imp,"Show All");
		// wait for user to move on
		new WaitForUserDialog("ok?").show();
		//close window
		imp.close();	
	}
}

// show the final results table
rt.show("roi spot counts")




public TrackMate detectSpots(Settings settings) {
	// create empty TrackMate Model
	Model model = new Model()
	// create TrackMate object to peform spot detection and tracking
    TrackMate trackmate = new TrackMate(model, settings)
    // find spots
    ok = trackmate.execDetection()
    if (ok == false) {
        println(trackmate.getErrorMessage())
    }
   // compute spot featues
   ok = trackmate.computeSpotFeatures(true)
    if (ok == false) {
        println(trackmate.getErrorMessage())
    }
    // filter spots
   ok = trackmate.execSpotFiltering(true)
    if (ok == false) {
        println(trackmate.getErrorMessage())
    }
    return trackmate

}


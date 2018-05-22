
// Author: Jeremy Pike

/*
 * Batch processes all files in a given directory. Uses TrackMate to detect and track spots.
 * Exports results as .csv file. 
 * Uses a Laplacian of Gaussian filter for spot detection and a simple LAP tracker with no gap linking
 */

// This script makes use of the TrackMate plugin to detect spots:
// Tinevez, Jean-Yves, et al. "TrackMate: An open and extensible platform for single-particle tracking." Methods (2016).
 
import java.io.File
import java.util.Collection;

import ij.IJ
import ij.ImagePlus
import ij.gui.WaitForUserDialog
import ij.measure.ResultsTable;

import fiji.plugin.trackmate.Model
import fiji.plugin.trackmate.Settings
import fiji.plugin.trackmate.detection.LogDetectorFactory
import fiji.plugin.trackmate.features.FeatureFilter
import fiji.plugin.trackmate.TrackMate
import fiji.plugin.trackmate.SelectionModel
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer
import fiji.plugin.trackmate.features.spot.SpotIntensityAnalyzerFactory
import fiji.plugin.trackmate.SpotCollection
import fiji.plugin.trackmate.Spot
import fiji.plugin.trackmate.tracking.sparselap.SimpleSparseLAPTrackerFactory
import fiji.plugin.trackmate.SelectionModel
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer
import fiji.plugin.trackmate.FeatureModel;
import fiji.plugin.trackmate.Spot;

import org.apache.commons.io.FileUtils


#@File (label="Select a directory containing the files to process", style="directory") directory
#@File (label="Select an output directory for the excel files", style="directory") outDirectory
#@String(label="Specify a file extension", value="tif") ext
#@Double(label="Specify spot radius (microns)", value=0.25) spotRadius
#@Double(label="Specify quality threshold for spots", value=2000) spotThreshQual
#@Double(label="Specify mean intensity threshold for spots", value=0) spotThreshMeanInt
#@Double(label="Specify maximum track linking distance (microns)", value=0.1) maxLinkDist
#@Double(label="Specify the max linking distance (gap closing)", value= 0.1) maxLinkingDistGC
#@Integer(label="Specify the max gap in frames", value=2) maxFrameGap

#@Boolean(label="Display tracks", value=false) display
#@Boolean(label="Save spot statistics", value=true) save


// create empty TrackMate Settings
Settings settings = new Settings();
// use a Laplacian of Gaussian detector
settings.detectorFactory = new LogDetectorFactory();
// specify that inensity based features should be calculated for spots
settings.addSpotAnalyzerFactory(new SpotIntensityAnalyzerFactory<>());
// specify the quality threshold for spots
settings.addSpotFilter(new FeatureFilter('QUALITY', spotThreshQual, true))
// specify the mean intensity threshold for spots
settings.addSpotFilter(new FeatureFilter('MEAN_INTENSITY', spotThreshMeanInt, true))
// create and fill map with detector settings
HashMap detectMap = settings.detectorFactory.getDefaultSettings();
detectMap.put('RADIUS', spotRadius);
detectMap.put('DO_MEDIAN_FILTERING', false);
detectMap.put('DO_SUBPIXEL_LOCALIZATION', true)
detectMap.put('THRESHOLD', 0.0d);
detectMap.put('TARGET_CHANNEL', 0);
settings.detectorSettings = detectMap; 
// use the simple LAP tracker
settings.trackerFactory = new SimpleSparseLAPTrackerFactory();
// create and fill map with tracking settings
HashMap trackMap = settings.trackerFactory.getDefaultSettings();
trackMap.put('LINKING_MAX_DISTANCE', maxLinkDist);
// no gap closing
trackMap.put('GAP_CLOSING_MAX_DISTANCE', maxLinkingDistGC);
trackMap.put('MAX_FRAME_GAP', maxFrameGap);
settings.trackerSettings = trackMap;

// find all files in specified directory with specified extension
String[] exts = new String[1];
exts[0] = ext;
List<File> files = FileUtils.listFiles(directory, exts, true);

// loop though files
for (int i = 0; i < files.size(); i++) {
	
	println("Processing file " + (i + 1) + " of " + files.size());
	File file = files.get(i);
	
	// open image 
	ImagePlus imp = IJ.openImage(file.getAbsolutePath());
	// point TrackMate at image
	settings.setFrom(imp);
	// create empty TrackMate Model
	Model model = new fiji.plugin.trackmate.Model();
	// create TrackMate object to peform spot detection and tracking
    TrackMate trackmate = new TrackMate(model, settings);
    // Find spots
    ok = trackmate.execDetection();
    if (ok == false) {
        println(trackmate.getErrorMessage());
    }
   // Compute spot featues (using first channel)
   ok = trackmate.computeSpotFeatures(true);
    if (ok == false) {
        println(trackmate.getErrorMessage());
    }
    // Filter spots
   ok = trackmate.execSpotFiltering(true);
    if (ok == false) {
        println(trackmate.getErrorMessage());
    }
	// Tracking
   ok = trackmate.execTracking();
    if (ok == false) {
        println(trackmate.getErrorMessage());
    }

	// Export all spot features to a ResultsTable
	ResultsTable spotTable = calcAllSpotFeatures(trackmate);

	// if display is reuqested
	if (display) {
		// display spots and tracks on dataset
	    SelectionModel selectionModel = new SelectionModel(model);
		HyperStackDisplayer displayer =  new HyperStackDisplayer(model, selectionModel, imp);
		displayer.render();
		displayer.refresh();
		// show spot statistics
		spotTable.show("All Spots statistics");
		// wait for user to move on
		new WaitForUserDialog("ok?").show();
		//close window
		imp.close();	
	}
	// if csv file save is requested
	if (save) {
		// save ResultsTable
		spotTable.save(outDirectory.getAbsolutePath() + File.separator + file.getName().substring(0, file.getName().length() - ext.length() - 1)  + '_spotStats.csv');
	}

}

/**
 * Construct a ResultsTable containing spot statstics for all visible spots
 * Adapted ij.plugin.trackmate.action.ExportAllSpotsStatsAction class so
 * as not to allways display ResultsTable
 * 
 * @param trackMate
 *            TrackMate object containing the spot detection 
 *            and tracking results
 * 
 * @return the ResultsTable containg the Spot statistics
 * 
 */
	 
public ResultsTable calcAllSpotFeatures(TrackMate trackmate) {
	
	// Create table
	ResultsTable spotTable = new ResultsTable();
	FeatureModel fm = trackmate.getModel().getFeatureModel();
	// Get list of all spot features
	Collection< String > spotFeatures = trackmate.getModel().getFeatureModel().getSpotFeatures();

	// Iterate though all visible spots
	Iterable< Spot > iterable = trackmate.getModel().getSpots().iterable( true );
	for (Spot spot : iterable) {

			// add spot name and ID to table
			spotTable.incrementCounter();
			spotTable.addLabel( spot.getName() );
			spotTable.addValue( "ID", "" + spot.ID() );

			// Check if current spot is in a track and add Track ID to table
			Integer trackID = trackmate.getModel().getTrackModel().trackIDOf( spot );
			if ( null != trackID ) {
				spotTable.addValue( "TRACK_ID", "" + trackID.intValue() );
			}
			else {
				spotTable.addValue( "TRACK_ID", -1);
			}
			// add remaining spot features to table
			for (String feature : spotFeatures ) {
				Double val = spot.getFeature( feature );
				if ( null == val ) {
					spotTable.addValue( feature, "None" );
				}
				else {
					if ( fm.getSpotFeatureIsInt().get( feature ).booleanValue() ) {
						spotTable.addValue( feature, "" + val.intValue() );
					}
					else {
						spotTable.addValue( feature, val.doubleValue() );
					}
				}
			}
		}
	return spotTable;
}
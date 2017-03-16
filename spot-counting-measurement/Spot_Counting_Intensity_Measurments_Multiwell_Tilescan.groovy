// @ImageJ ij
// @File(label="Select a multiwell file") file
// @Integer(label="Specify number of wells used in experiment", value="80") numWells
// @Integer(label="Specify number of well columns", value=10) numWellColumns
// @Double(label="Specify spot radius (microns)", value=5) spotRadius
// @Double(label="Specify quality threshold for spots", value=14) spotThresh

// Author: Jeremy Pike

/*
 * To load each tile as an indivudal series then bioformats should be configured to do this 
 * for czi files go to Bio-Formats Plugin Configuration -> Formats -> Zeiss CZI -> untick Autostitch
 * this works using Bio-formats version 5.3.4
 */

// We assume that each row is aquired in turn and that the scan always works left to right

// We assume no tile overlap

// This script makes use of the TrackMate plugin to detect spots:
// Tinevez, Jean-Yves, et al. "TrackMate: An open and extensible platform for single-particle tracking." Methods (2016).
 
import java.util.Iterator;
import ij.ImagePlus;
import ij.IJ;
import ij.ImageStack;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.measure.Calibration;
import ij.plugin.HyperStackConverter;
import loci.formats.ChannelSeparator;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLServiceImpl;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import ome.units.UNITS;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.detection.LogDetectorFactory;
import fiji.plugin.trackmate.features.FeatureFilter;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import fiji.plugin.trackmate.features.spot.SpotIntensityAnalyzerFactory;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.Spot;

// create empty TrackMate Model
Model model = new fiji.plugin.trackmate.Model();

// create empty TrackMate Settings
Settings settings = new Settings();
// use a Laplacian of Gaussian detector
settings.detectorFactory = new LogDetectorFactory();
// specify that inensity based features should be calculated for spots
settings.addSpotAnalyzerFactory(new SpotIntensityAnalyzerFactory<>());
// specify the quality threshold for spots
settings.addSpotFilter(new FeatureFilter('QUALITY', spotThresh, true))
// create and fill map with detector settings
HashMap map = settings.detectorFactory.getDefaultSettings();
map.put('RADIUS', spotRadius);
map.put('DO_MEDIAN_FILTERING', false);
map.put('DO_SUBPIXEL_LOCALIZATION', false)
map.put('THRESHOLD', spotThresh);

// get current results table and reset
ResultsTable rt = ResultsTable.getResultsTable();
rt.reset();

// reader to load specified file
ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
// set up metadata reading
OMEXMLServiceImpl OMEXMLService = new OMEXMLServiceImpl();
r.setMetadataStore(OMEXMLService.createOMEXMLMetadata());
// set reader to specified file
r.setId(file.getPath());
// get metadata
MetadataRetrieve meta = (MetadataRetrieve) r.getMetadataStore();

// get number of series in specified file
int numSeries = meta.getImageCount();

// caclulate number of tiles per well
double numTilesPerWell =  numSeries / numWells;

// variable to hold spot counts for current well
int numDapiSpots = 0;
// variable to hold mean sum spot intensity in second channel for current well
double meanTotalRedIntensity = 0;

// loop through all tiles in files
for (int i = 0; i < numSeries; i++) {

	// find which well the series is in
	int wellInd = Math.floor(i/numTilesPerWell);
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

	// point TrackMate to the first channel of the data
	settings.setFrom(imp);
    map.put('TARGET_CHANNEL', 1);
	settings.detectorSettings = map;     

	// create TrackMate object to peform spot detection
    TrackMate trackmate = new TrackMate(model, settings);

    // Find spots
    ok = trackmate.execDetection();
    if (ok == false) {
        println(trackmate.getErrorMessage())
    }
   
   // Compute spot featues (using first channel)
   ok = trackmate.computeSpotFeatures(true);
    if (ok == false) {
        println(trackmate.getErrorMessage())
    }
  
    // Filter spots
   ok = trackmate.execSpotFiltering(true);
    if (ok == false) {
        println(trackmate.getErrorMessage())
    }
	// point TrackMate to the second channel of the data
    settings.setFrom(imp);
    map.put('TARGET_CHANNEL', 2);
    
	// Compute spot featues (using second channel)
   ok = trackmate.computeSpotFeatures(true);
    if (ok == false) {
        println(trackmate.getErrorMessage())
    }
    
    /*
    SelectionModel selectionModel = new SelectionModel(model)
	HyperStackDisplayer displayer =  new HyperStackDisplayer(model, selectionModel, imp)
	displayer.render()
	displayer.refresh()
	*/
	
    // Get spots which were detected in the first (dapi) channel
	SpotCollection dapiSpots = model.getSpots();
	
	// add number of spots to well count
    numDapiSpots += dapiSpots.getNSpots(true);

	// iterate through all spots and add sum intensity to the count (calculated using second channel)
    Iterator<Spot> dapiIterator = dapiSpots.iterator(true);
	while (dapiIterator.hasNext()) {
		Spot spot = dapiIterator.next();
		meanTotalRedIntensity += spot.getFeature("TOTAL_INTENSITY"); 
		
	}
	// if the next series is in a different well
	if (wellInd != Math.floor((i + 1) / numTilesPerWell)) {

		// find the well row index 
		int wellRow = Math.floor(wellInd/ numWellColumns);
		// find the column row index
		int wellColumn = wellInd - numWellColumns * wellRow;
		// find the mean sum spot intensity
		meanTotalRedIntensity = meanTotalRedIntensity / numDapiSpots;
		// increment the results table
		rt.incrementCounter();
		// add values to results table
		rt.addValue("Well row ", wellRow + 1);
		rt.addValue("Well column ", wellColumn + 1);
		rt.addValue("Number of dapi spots", numDapiSpots);
		rt.addValue("Mean total red intensity", meanTotalRedIntensity);

		// reset spot and sum intensity counts
		numDapiSpots = 0;
		meanTotalRedIntensity = 0;

		println("Finished processing well " + (wellInd + 1) + " of " + numWells);
	}	
	

}
// close reader
r.close();
// show and update ResultsTable
rt.show("Results");
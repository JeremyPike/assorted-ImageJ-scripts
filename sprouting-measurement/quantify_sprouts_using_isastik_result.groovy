
// Author: Jeremy Pike, Image Analyst for COMPARE

/*
 * Quantifies sprout statistics for bead sprouting assays. Segmentation should have been performed previously
 * using an ilastik pixel classification project and outputed as probility images.
 */

// This script adapts code from Jan Eglinger's Sprout Morphology plugin:
// Eglinger, J. et al. Quantitative assessment of angiogenesis and pericyte coverage in human cell-derived vascular sprouts", Inflammation and Regeneration 37(2) 2017.

import java.util.ArrayList
import java.io.File

import ij.IJ
import ij.ImagePlus
import ij.ImageStack;
import ij.process.ImageProcessor
import ij.plugin.filter.ParticleAnalyzer
import ij.measure.ResultsTable
import ij.plugin.frame.RoiManager
import ij.plugin.ImageCalculator
import ij.gui.WaitForUserDialog
import ij.gui.Roi
import ij.plugin.filter.EDM
import ij.process.FloatProcessor
import ij.measure.Calibration
import ij.gui.PointRoi

import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_
import sc.fiji.analyzeSkeleton.SkeletonResult
import sc.fiji.analyzeSkeleton.Point

import loci.plugins.util.ImageProcessorReader
import loci.plugins.util.LociPrefs
import loci.formats.ChannelSeparator
import loci.formats.services.OMEXMLServiceImpl
import loci.formats.meta.MetadataRetrieve

// Ask user for a bunch of paramters
#@File(label="Select a directory containing the image files to process", style="directory") dirImages
#@File(label="Select a directory containing the probability ilastik files to process", style="directory") dirProbabilities
#@String(label="Specify a file extension for images", value="tif", persist=false) ext
#@String(label="Specify a matching term for probability files", value="_Probabilities.tif", persist=false) probMatch
#@Double(label="Specify minimum seed size (pixels)", value=5000, persist=false) minSeedSize
#@Double(label="Specify minimum protrusion size (pixels)", value=100, persist=false) minProtrusionSize
#@Double(label="Specify a structral element size for opening", value=30, persist=false) SESize
#@Double(label="Specify a structral element size for seed dilation", value=10, persist=false) SESizeDil
#@boolean(label="Display results", value=false, persist=false) display
#@boolean(label="Save results", value=true, persist=false) saveResults
#@File(label="Select a ouput directory for the excel file", style="directory") dirOutput

// create lists to hold output statistics
ArrayList<String> filenamesArray = new ArrayList<String>()
ArrayList<Double> seedCoreAreaArray = new ArrayList<Double>()
ArrayList<Integer> numProtrusionsArray = new ArrayList<Integer>()
ArrayList<Double> meanProtrusionLengthArray = new ArrayList<Double>()
ArrayList<Integer> numJunctionsArray = new ArrayList<Integer>()
ArrayList<Integer> numEndPointsArray = new ArrayList<Integer>()
ArrayList<Double> meanEndPointDistArray = new ArrayList<Double>()
ArrayList<Double> meanMaxEndPointDistArray = new ArrayList<Double>()
ArrayList<Double> meanProtrsionAreaArray = new ArrayList<Double>()

// Get and reset the current RoiManager if it exists
#@ RoiManager (required = false) rm
// create RoiManager if none is present
if (rm == null) {
	rm = new RoiManager()
}

// list all files in input raw image directory
File[] fileListImagesDir = dirImages.listFiles()
// list all files in probability image directory
File[] fileListProbabilitiesDir = dirProbabilities.listFiles()
// list to hold raw image files
ArrayList<File> fileListImageArray = new ArrayList<File>()
for (File file : fileListImagesDir) {
	// if image has specifed extension and dosnt contain the probability image matching term add to list
	if (file.getName().endsWith(ext) && !file.getName().contains(probMatch)) 
		fileListImageArray.add(file)
}
// convert ArrayList to array and extract number of files
File[] fileListImages = fileListImageArray.toArray()
int numImages =  fileListImages.length

// array to hold probability image files
File[] fileListProbabilities = new File[numImages]
// loop through raw images
for (int i = 0; i < numImages; i++) {
	// get raw image filename with extension removed
	String filenameImage = fileListImages[i].getName().substring(0, fileListImages[i].getName().length() - ext.length() - 1)
	// loop through probability images
	for (File fileProbailty : fileListProbabilitiesDir) {
		String filenameProbability = fileProbailty.getName()
		// if image file names concatanated to probability matching term matches current filename
		if (filenameProbability.equals(filenameImage.concat(probMatch)))
			fileListProbabilities[i] = fileProbailty
	}	
}

// create results table
ResultsTable rt = new ResultsTable()
// image calculator for binary image operations
ImageCalculator ic = new ImageCalculator()
// particle analyzer to find seeds (with specified minimum size)
ParticleAnalyzer pa = new ParticleAnalyzer(ParticleAnalyzer.ADD_TO_MANAGER + ParticleAnalyzer.IN_SITU_SHOW + ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES, 0, rt, minSeedSize, Double.MAX_VALUE)
// particle analyzer to find protrusions (with specified minimum size)
ParticleAnalyzer paProtusion = new ParticleAnalyzer(ParticleAnalyzer.ADD_TO_MANAGER + ParticleAnalyzer.SHOW_NONE, 0, rt, minProtrusionSize, Double.MAX_VALUE)
// to calculate distance maps
EDM edm = new EDM()
// to reset any calibration in image files
Calibration cali = new Calibration()
cali.pixelWidth = 1
cali.pixelHeight = 1
cali.pixelDepth = 1
cali.setUnit("pixel")

// loop through all raw images
for (int i = 0; i < numImages; i++) {
	
	println("Processing image " + (i + 1) + " of " + numImages)
	println("Image file: " + fileListImages[i])
	println("Probability file: " + fileListProbabilities[i])

	// open raw image and reset calibration
	ImagePlus impImage = IJ.openImage(fileListImages[i].getAbsolutePath())
	impImage.setCalibration(cali)

	// if requested display raw image
	if (display)
		impImage.show()

	// load corresponding ilastik probability output file and reset calibration
	ImagePlus impMask= loadProbabilityMap(fileListProbabilities[i])
	impMask.setCalibration(cali)
	// threshold at 0.5 probability
	IJ.setRawThreshold(impMask, 0.5, 1, null)
	IJ.run(impMask, "Convert to Mask", "")
	
	// reset roi manager and find seed ROIs
	rm.reset()
	pa.analyze(impMask)
	int numSeeds = rm.getCount()

	// loop through seeds
	for (int j = 0; j < numSeeds; j++) {

		// store current filename in output list
		filenamesArray.add(fileListImages[i].getName())

		// create image to hold mask for current seed
		ImagePlus seedMask = IJ.createImage("Seed mask", "8-bit black", impMask.width, impMask.height, 1)
		// fill using current seed ROI
		rm.select(j)
		rm.runCommand(seedMask, "Fill")
		// check mask contains same holds as joint seed mask
		seedMask = ic.run("AND create", seedMask, impMask)

		// duplicate to hold mask for central seed
		ImagePlus centralSeedMask = seedMask.duplicate()
		// use imaging opening to fins central seed
		IJ.run(centralSeedMask, "Gray Morphology", "radius=" + SESize + " type=circle operator=open")
		// dilate central seed a bit, this helps to seperating srounts which touch at the base
		IJ.run(centralSeedMask, "Gray Morphology", "radius=" + SESizeDil + " type=circle operator=dilate")
		
		// create ROI from central seed mask and measure area
		IJ.run(centralSeedMask, "Create Selection", "")
		IJ.run(centralSeedMask, "Make Inverse", "");
		Roi centralSeedRoi = centralSeedMask.getRoi()
		centralSeedRoi.setName("seed core")
		seedCoreAreaArray.add(centralSeedMask.getStatistics().area)
		IJ.run(centralSeedMask, "Select None", "");
	
		// create protrusion mask by subtracting the central seed mask from the seed mask
		ImagePlus protusionMask = ic.run("Subtract create", seedMask, centralSeedMask)

		// reset the results table and ROI manager before finding protrusion ROIs
		rt.reset()
		rm.reset()
		paProtusion.analyze(protusionMask)
		int numProtrusions = rt.getCounter()
		// store number of protrusions in output list
		numProtrusionsArray.add(numProtrusions)
		
		// find the mean area of all protrusions for this seed
		double meanProtrusionArea = 0
		for (int prot = 0; prot < numProtrusions; prot++) {
			protusionMask.setRoi(rm.getRoi(prot))
			meanProtrusionArea += protusionMask.getStatistics().area
		}
		meanProtrusionArea = meanProtrusionArea / numProtrusions
		// store in output list
		meanProtrsionAreaArray.add(meanProtrusionArea)
		
		// skelontonize protrusion mask
		IJ.run(protusionMask, "Skeletonize (2D/3D)", "")

		// use the AnalyzeSkeleton plugin the calculate skeleton feature for the protrusions
		AnalyzeSkeleton_ skel = new AnalyzeSkeleton_()
		AnalyzeSkeleton_.calculateShortestPath = true
		skel.setup("", protusionMask)
		SkeletonResult sr = skel.run(AnalyzeSkeleton_.NONE, false, true, null, true, false)

		// pull out the skeleton features we need
		double[] branchMeanLengths = sr.getAverageBranchLength()
		int[] branchCounts = sr.getBranches()
		int[] junctionCounts = sr.getJunctions()
		int[] endPointCounts = sr.getEndPoints()
		ArrayList<Point> endPoints = sr.getListOfEndPoints()

		// duplicate and invert the central seed mask
		ImagePlus centralSeedMaskInvert = centralSeedMask.duplicate()
		IJ.run(centralSeedMaskInvert, "Invert", "")
		// use inverted mask to calculate a distance map
		FloatProcessor centralSeedDistanceProc = edm.makeFloatEDM(centralSeedMaskInvert.getProcessor(), 0 , false)

		// to store the branch end point for each protrusion which is the furthest from the central seed
		Point[] maxEndPoints = new Point[numProtrusions]
		// to store the corresponding distances 
		int[] maxEndPointDist = new int[numProtrusions] 
		// to store sum distance from end points to seed core
		double endPointDistSum = 0
		// loop through end points and protrusions
		for (Point endPoint : endPoints) {
			// retrieve value of distance map at this end point
			float endPointDist =  Float.intBitsToFloat(centralSeedDistanceProc.getPixel(endPoint.x, endPoint.y))
			endPointDistSum += endPointDist
			for (int prot = 0; prot < numProtrusions; prot++) {
				// if current end point is in current protrusion ROI and distance is larger than previously stored value for this protrusion update
				if (rm.getRoi(prot).contains(endPoint.x, endPoint.y) && endPointDist > maxEndPointDist[prot]) {			
					maxEndPointDist[prot] = endPointDist
					maxEndPoints[prot] = endPoint
				}
			}
		}
		
		// find the mean distance for the extremal end points and store in output list
		double meanMaxEndPointDist = 0;
		for (int dist : maxEndPointDist) 
			meanMaxEndPointDist += dist
		meanMaxEndPointDist = meanMaxEndPointDist / numProtrusions
		meanMaxEndPointDistArray.add(meanMaxEndPointDist)

		// calculate mean protrusion lengths and total number of junctions and end-points
		double totalProtrusionLength = 0
		int numJunctions = 0
		int numEndPoints = 0
		if (branchCounts != null) {
			for (int b = 0; b < branchCounts.length; b++) {
				totalProtrusionLength  += branchCounts[b] * branchMeanLengths[b]
				numJunctions += junctionCounts[b]
				numEndPoints += endPointCounts[b]
				
			}
		}
		// store in output lists
		meanProtrusionLengthArray.add(totalProtrusionLength  / numProtrusions)
		numJunctionsArray.add(numJunctions)
		numEndPointsArray.add(numEndPoints)
		meanEndPointDistArray.add(endPointDistSum / numEndPoints)
		// if user requested to inspect results
		if (display) {

			// reset roi manager
			rm.reset()
			// create ROI from seed mask and add to ROI manager
			IJ.run(seedMask, "Create Selection", "")
			IJ.run(seedMask, "Make Inverse", "");
			Roi seedRoi = seedMask.getRoi()
			seedRoi.setName("seed")
			rm.addRoi(seedRoi)
			// add central seed roi to ROI manager
			rm.addRoi(centralSeedRoi)
			// create ROI from protrusion mask and add to ROI manager
			IJ.run(protusionMask, "Create Selection", "")
			IJ.run(protusionMask, "Make Inverse", "");
			Roi protrusionRoi = protusionMask.getRoi()
			protrusionRoi.setName("protrusions")
			rm.addRoi(protrusionRoi)

			// create ROIs for extremal end points and add to ROI manager
			for (Point point : maxEndPoints) { 
				PointRoi roiPoint = new PointRoi((double)point.x, (double)point.y)
				roiPoint.setName("end point")
				rm.addRoi(roiPoint)
			}
		
			// display ROIs on raw image
			rm.runCommand(impImage,"Show All")
			// highlight the protrusion ROI
			rm.select(2)
			// wait for user to move on
			new WaitForUserDialog("ok?").show()
			//close window
			
		}
		// reset ROI manager and re-add ROIs for seeds
		rm.reset()
		pa.analyze(impMask)
	}
	// image raw image was opened close it
	if (display)
		impImage.close()
	
}

// reset the results table before filling with calculated statstics
rt.reset()
int numSeeds = filenamesArray.size()
for (int i = 0; i < numSeeds; i++) {
	rt.setValue("filename", i, filenamesArray.get(i))
	rt.setValue("seed core area", i, seedCoreAreaArray.get(i))
	rt.setValue("number of protrusions", i, numProtrusionsArray.get(i))
	rt.setValue("number of endpoints", i, numEndPointsArray.get(i))
	rt.setValue("number of junctions", i, numJunctionsArray.get(i))
	rt.setValue("mean protrusion area", i, meanProtrsionAreaArray.get(i))
	rt.setValue("mean protrusion length", i, meanProtrusionLengthArray.get(i))
	rt.setValue("mean endpoint distance", i, meanEndPointDistArray.get(i))
	rt.setValue("mean max endpoint distance", i, meanMaxEndPointDistArray.get(i))
	
}
// show the results
rt.show("Results")

// if requested save results
if (saveResults)
	IJ.saveAs("Results", dirOutput.getAbsolutePath() + File.separator + "sproutMeasurements.csv");


public ImagePlus loadProbabilityMap(File file) {
	// reader to load specified file
	ImageProcessorReader  r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()))
	// set up metadata reading
	OMEXMLServiceImpl OMEXMLService = new OMEXMLServiceImpl()
	r.setMetadataStore(OMEXMLService.createOMEXMLMetadata())
	// set reader to specified file
	r.setId(file.getAbsolutePath())
	// get metadata
	MetadataRetrieve meta = (MetadataRetrieve) r.getMetadataStore()
	r.setSeries(0);
	// load data for current series as a stack
	ImageStack stack = new ImageStack(r.getSizeX(), r.getSizeY())
	ImageProcessor ip = r.openProcessors(0)[0]
	stack.addSlice("1", r.openProcessors(0)[0])
	// create ImagePlus using stack
	r.close();
	return new ImagePlus("mask", stack)
}
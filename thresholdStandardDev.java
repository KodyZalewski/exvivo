public class thresholdStandardDev {
	
	public static double LINEVAL = 0; // for first half of the scan
	public static double LINEVAL2 = 0;// for the opposite half of each dimension of the scan
	public static int COUNT = 0;
	
	// global variable storing avgs of each half: [X left, X right, Y superior, Y inferior, Z anterior, Z posterior]
	public static double[] AVGDELTA = new double[6]; 

	/** @Author KJZ 12.22.2019
	 * @Params Takes nifti scan data as 3-dimensional matrix of doubles. 
	 * @param Takes x, y, z dimensions as boolean. 
	 * @param Takes standard deviation as a double val, 
	 * @param Lowering the standard dev threshold decreases the boundary at which the voxels are masked out.
	 * @param Takes bound as int, corresponds to how the scan should be divided up to calculate the standard dev. (e.g, 2 = halves, 3 = thirds etc)
	 * @param Takes voxelBound as double, what intensity the gradient should halt calculation at. 
	 * @returns the altered data passed to findGradient() as a matrix of doubles to be written to a new nifti scan.  
	 */

	public static double[][][] findGradient(double[][][] data, boolean x, boolean y, boolean z, 
		double stdev, int bound, double voxelBound, boolean firstHalf, boolean secondHalf) {
		
		int XDIM = data[0][0].length;
		int YDIM = data[0].length;
		int ZDIM = data.length;
		
		System.out.println("X, Y, Z Dimensions are: " + XDIM + " " + YDIM + " " + ZDIM);
		
		// matrices for storing the standard deviation for each row in a grid representing both sides of the scan
		if (x) {
			data = traverseData(data, findGradientHelper(data, ZDIM, YDIM, XDIM, bound, "x"), 
				ZDIM, YDIM, XDIM, bound, voxelBound, "x", stdev, firstHalf, secondHalf);
		}
		if (y) {
			data = traverseData(data, findGradientHelper(data, ZDIM, XDIM, YDIM, bound, "y"), 
				ZDIM, XDIM, YDIM, bound, voxelBound, "y", stdev, firstHalf, secondHalf);
		}
		if (z) {
			data = traverseData(data, findGradientHelper(data, XDIM, YDIM, ZDIM, bound, "z"), 
				XDIM, YDIM, ZDIM, bound, voxelBound, "z", stdev, firstHalf, secondHalf);
		}
		// TODO: Double-check that the anatomy corresponds with the x, y, z dimensions
		// UPDATE: Y and Z are swapped I guess, fix at some point, not critical right now as long as it works
		// Will be necessary to address if this is ever published so as not to confuse the client.
		// anterior and posterior are mixed up, this could also be b/c the scans are in an odd orientation
		
		System.out.println("Average change in each dimension: left = " + round(AVGDELTA[0],2) + " right = " + round(AVGDELTA[1],2) + " dorsal = " + round(AVGDELTA[2],2) + 
				" ventral = " + round(AVGDELTA[3],2) + " anterior = " + round(AVGDELTA[4],2) + " posterior = " + round(AVGDELTA[5],2));
		return data;
	}
	
	
	/** @author KJZ
	 * @params Helper method for FindGradient takes scan data as 3-dimensional matrix of doubles. 
	 * @params Takes int as a, b, c, corresponding to the dimensions of the scan. 
	 * @params Takes a string as whether we are traversing the x, y, or z dimensions of the scan.
	 * @returns 3-dimensional matrix. Columns of data correspond to the first and second halves of the
	 * scan being processed and contain a grid of gradients corresponding to each row of data.  
	*/
	// a, b and c are the dimensions used for traversing the scan
	public static double[][][] findGradientHelper(double[][][] data, int a, int b, int c, int bound, String dimension) {

		// two gradients for moving toward the center of the scan and two measures of averages for each dimension
		double[][][] gradients = new double[2][a][b]; double[][][] averages = new double[2][a][b];

		// for calculating the standard deviation of the last dimensional row
		double[] residuals = new double[c/bound+1]; double[] residuals2 = new double[c/bound+1];
		
		// counter for traversing the scan and counting non-zero voxels
		COUNT = 0;

		for (int i = 0; i < a; i++) {
			for (int j = 0; j < b; j++) {
				for (int k = 0; k < c/bound; k++) {
					residuals = calcResiduals(data, residuals, true, i, j, k, k, dimension); 
					
				}
				
				COUNT = 0;
				averages[0][i][j] = takeAverage(residuals); //removed absolute val
				gradients[0][i][j] = standardDeviation(residuals, averages[0][i][j]); //removed absolute val
				
				
				for (int k = c - 1; k > (c - c/bound); k--) {
					residuals2 = calcResiduals(data, residuals2, false, i, j, k-1, (c-k-1), dimension);
					
				}
				COUNT = 0;
				averages[1][i][j] = takeAverage(residuals); //removed absolute val
				gradients[1][i][j] = standardDeviation(residuals2, averages[1][i][j]); //removed absolute val
				
			}
		}
		
		setAvgDelta(a, b, c, dimension, bound);
		LINEVAL = 0;
		LINEVAL2 = 0;
		
		return gradients;
	}
	
	/** @params The original data as a 3-dimensional matrix of double values. 
	 * The array of residuals for deriving the standard dev and average from. 
	 * Forward or backward will equal 0 or 1 as int depending on which half of the scan dimension is being traversed.
	 * i, j, k, locate the voxel value on the scan in the 3-dimensional matrix.
	 * resid value is what half of the scan is where the value is being stored as int.
	 * Dimension is the x, y, or z dimension of the scan as string.  
	   @returns the gradient of the scan with the standard deviation at each voxel location 
	 */

	public static double[] calcResiduals(double[][][] data, double[] residuals, 
			boolean forward, int i, int j, int k, int residValue, String dimension) {

		if (dimension.equals("x")) { // for x dimension
			if (data[i][j][k] != 0) {	
				if (forward) {
					LINEVAL += (data[i][j][k+1] - data[i][j][k]);
					residuals[COUNT] = (data[i][j][k+1] - data[i][j][k]);
				} else {
					LINEVAL2 += (data[i][j][k] - data[i][j][k-1]);
					residuals[COUNT] = (data[i][j][k] - data[i][j][k-1]);
				}
				COUNT++;
			}
			
		} else if (dimension.equals("y")) { // for y dimension
			if (data[i][k][j] != 0) {
				if (forward) {
					LINEVAL += (data[i][k+1][j] - data[i][k][j]);
					residuals[COUNT] = (data[i][k+1][j] - data[i][k][j]);
				} else {
					LINEVAL2 += (data[i][k][j] - data[i][k-1][j]);
					residuals[COUNT] = (data[i][k][j] - data[i][k-1][j]);
				}
				COUNT++;
			}
			
		} else if (dimension.equals("z")) { // for z dimension
			if (data[k][j][i] != 0 ) {
				if (forward) {
					LINEVAL += (data[k+1][j][i] - data[k][j][i]);
					residuals[COUNT] = (data[k+1][j][i] - data[k][j][i]);
				} else {
					LINEVAL2 += (data[k][j][i] - data[k-1][j][i]);
					residuals[COUNT] = (data[k][j][i] - data[k-1][j][i]);
				}	
				COUNT++;
			}
			
		} else {
			residuals[COUNT] = 0.0;
		}
		return residuals;
	}


	/** @params residuals are single double array of voxels from mri data, 
	 *  the average intensity of the line of voxel data as double
	 *  @returns the standard deviation of the voxel residuals
	 */
	public static double standardDeviation(double[] residuals, double average) {
		
		double standardDev = 0; int power = 1; int n = 0;
		
		for (int counter = 0; counter < residuals.length; counter++) {
			if (residuals[counter] != 0) {
				standardDev += ((residuals[counter]-average)*(residuals[counter]-average));
				n++;
			}
		}
		return Math.sqrt((standardDev/(n - power)));
	}
	
	public static double takeAverage(double[] residuals) {
		
		double average = 0; int n = 0;
		
		for (int counter = 0; counter < residuals.length; counter++) {
			if (residuals[counter] != 0) {
				average += average;
				n++;
			}
		}
		return average/n;
	}

	/**@params Takes dimensions x, y, z as integers corresponding to a, b, and c. 
	 * Takes which dimension is being measured (x,y,z) as string
	 * Bound is the dimension by which the scan is divided into (halves = 2, thirds = 3 etc.) as int'
	 * Sets the average change in dimension to the global array of averages AVGDELTA.
	 */
	public static void setAvgDelta(int a, int b, int c, String dimension, int bound) {
		
		if (dimension.equals("x")) {
			AVGDELTA[0] = Math.abs(LINEVAL/((c/bound) * b * a));
			AVGDELTA[1] = Math.abs(LINEVAL2/((c/bound) * b * a)); 	
			
		} else if (dimension.equals("y")) {
			AVGDELTA[2] = Math.abs(LINEVAL/(c * (b/bound) * a));
			AVGDELTA[3] = Math.abs(LINEVAL2/(c * (b/bound) * a));
			
		} else if (dimension.equals("z")) {
			AVGDELTA[4] = Math.abs(LINEVAL/(c * b * (a/bound)));
			AVGDELTA[5] = Math.abs(LINEVAL2/(c * b * (a/bound)));
			
		} else {
			System.out.println("No dimension specified");
		}
	}

	//TODO: break up everything below this line into it's own separate .java file at some point
	
	/** @author KJZ
	 * @param data is double 3D matrix of T1 mri data.
	 * @param is the mapping of the avg change in each dimension.
	 * @param a, b, c as int correspond to the desired dimensions to traverse the scan.
	 * @param bound as integer corresponds with how the scan should be broken down (i.e. into 4=quarters, 3=thirds, 2=halves etc).
	 * @param voxelBound as double is the given lowest intensity to stop the thresholding at.
	 * @param dimension as string x/y/z as to which dimension should be processed.
	 * @param stdev as double is how many standard deviations should be the cutoff for outlier intensities denoting 
	 * the outer boundary of the brain (default=2).
	 * @param firstHalf/secondHalf are boolean as to whether or not to process only one half of a given dimension of the brain.
	 * 
	 * @return double as 3D matrix to be written to a new scan with outlier values thresholded out */
	
	// add average[][][] grid at some point to arguments to correspond with the stdev grid.
	// removed Math.abs(), maybe this will work better
	
	public static double[][][] traverseData(double[][][] data, double gradient[][][], int a, int b, int c, int bound, 
			double voxelBound, String dimension, double stdev, boolean firstHalf, boolean secondHalf) {

		boolean voxelBoundary = false;

		for (int i = 0; i < a; i++) {
			for (int j = 0; j < b; j++) {
				if (firstHalf) {
					for (int k = 0; k < c/bound; k++) {
						voxelBoundary = returnVoxel(data, gradient, true, i, j, k, dimension, stdev, voxelBound, voxelBoundary); 
						if (voxelBoundary) {
							break;
						} else {
							data = replaceVoxel(data, i, j, k, dimension);
						}
					}
					voxelBoundary = false; 
				}
				if (secondHalf) {
					for (int k = c - 1; k > (c - c/bound); k--) {
						voxelBoundary = returnVoxel(data, gradient, false, i, j, k, dimension, stdev, voxelBound, voxelBoundary);
						if (voxelBoundary) {
							break; 
						} else {
							data = replaceVoxel(data, i, j, k, dimension); 
						}
					}
					voxelBoundary = false; 
				}
			}
		}
		return data;
	}


	public static double[][][] replaceVoxel(double[][][] data, int i, int j, int k, String dimension) {
		
		if (dimension.equals("x")) {
			data[i][j][k] = 0; 
		} else if (dimension.equals("y")) {
			data[i][k][j] = 0; 
		} else if (dimension.equals("z")) { 
			data[k][j][i] = 0;
		} else {
			System.out.println("No valid x, y, z dimension argument provided.");		
		}
		return data;
	}

	// Assuming T1 weighted imaging with dark outer boundary, otherwise should be less-than the standard deviation
	// note that stdev is also given as a negative value,
	
	public static boolean returnVoxel(double[][][] data, double[][][] gradient, boolean forward, int i, int j, int k, 
			String dimension, double stdev, double voxelBound, boolean voxelBoundary) {
		
		if (dimension.equals("x")) { // for x dimension data[i][j][k] != 0
			if (forward) {
				if (((data[i][j][k+1] - data[i][j][k] - AVGDELTA[0]) / gradient[0][i][j]) > -stdev && data[i][j][k] > voxelBound) {
					voxelBoundary = false; 
				} else if (data[i][j][k] > 1) {
					voxelBoundary = true;  
				}
			} else {
				if (((data[i][j][k-1] - data[i][j][k] - AVGDELTA[1]) / gradient[1][i][j]) > -stdev && data[i][j][k] > voxelBound) {
					voxelBoundary = false;
				} else if (data[i][j][k] > 1) {
					voxelBoundary = true;  
				}
			}
		}

		if (dimension.equals("y")) { // for y dimension data[i][k][j] != 0
			if (forward) {
				if ((data[i][k+1][j] - data[i][k][j] - AVGDELTA[2] / gradient[0][i][j]) > -stdev && data[i][k][j] > voxelBound) {
					voxelBoundary = false;
				} else if (data[i][k][j] > 1) {
					voxelBoundary = true; 
				}
			} else {
				if ((data[i][k-1][j] - data[i][k][j] - AVGDELTA[3] / gradient[1][i][j]) > -stdev && data[i][k][j] > voxelBound) {
					voxelBoundary = false; 
				} else if (data[i][k][j] > 1) {
					voxelBoundary = true; 
				}
			} 

		}

		if (dimension.equals("z")) { // for z dimension data[k][j][i] != 0
			if (forward) {
				if ((data[k+1][j][i] - data[k][j][i] - AVGDELTA[4] / gradient[0][i][j]) > -stdev && data[k][j][i] > voxelBound) {
					voxelBoundary = false; 
				}  else if (data[k][j][i] > 1 || data[k][j][i] < voxelBound) {
					voxelBoundary = true; 
				}
			} else {
				if ((data[k-1][j][i] - data[k][j][i] - AVGDELTA[5] / gradient[1][i][j]) > -stdev && data[k][j][i] > voxelBound) {
					voxelBoundary = false; 
				} else if (data[k][j][i] > 1) {
					voxelBoundary = true; 
				}
			} 
		}
		return voxelBoundary;
	}
	
	/**
	 * Finds the total average of a nifti dataset for all non-zero voxels
	 */
	public static double findTotalAverage(double[][][] data) {
		double avg = 0;
		int counter = 0;
		for (int i = 0; i < data.length; i++) {		
			for (int j = 0; j < data[i].length; j++) {
				for (int k = 0; k < data[i][j].length; k++) {
					if (data[i][j][k] != 0) {
						avg = avg+=data[i][j][k];
						counter++;
					}
				}
			}
		}
		return avg/counter;
	}
	
	public static void outerAverage(double[][][] data) {
		for (int i = 0; i < data.length; i++) {
			for(int j = 0; j < data[i].length; j++) {
				for (int k = 0; k < data[i][j].length/2; k++) {
				}
			}
		}
	}
	
	public static void phaseEncodeNorm(double[][][] data, double average1, double average2) {
		for (int i = 0; i < data.length; i++) {
			for(int j = 0; j < data[i].length; j++) {
				if (average1 < average2) { 
					for (int k = 0; k < data[i][j].length/2; k++) {
						
					}
				} else {
					//for (int k = c - 1; k > (c - c/bound); k--) {
						
					//}
				}
			}
		}
	}
	
	/**
	 * @author KJZ
	 * @param Takes data as double 3D matrix, int a, b, c represent respective dimensions depending on which
	 * dimension (passed as String) is being traversed, smoothLength denotes how many voxels along the dimension to incorporate
	 * into smoothing (default is 1). Number of outliers is reduced across the scan. 
	 * @return double 3D matrix to be written to a new scan as a smoothed volume.
	 */
	
	public static double[][][] movingAverage(double[][][] data, int smoothLength) {
	
		int x = data[0][0].length; int y = data[0].length; int z = data.length; // fix from being [0] at some point
		
		data = movingAverageHelper(data, z, y, x, smoothLength, "x");
		data = movingAverageHelper(data, z, x, y, smoothLength, "y");
		data = movingAverageHelper(data, x, y, z, smoothLength, "z");
		return data; 
	}
	
	public static double[][][] movingAverageHelper(double[][][] data, int a, int b, int c, int smoothLength, String dimension) {
		for (int i = smoothLength; i < a; i++) {
			for (int j = smoothLength; j < b; j++) { 
				for (int k = smoothLength; k < c - smoothLength; k++) {
					int newData = 0;
					if (dimension.equals("x")) {			
						for (int l = 0; l < smoothLength; l++) {
							newData += (data[i][j][k-l] + data[i][j][k+l]);
						} 
						data[i][j][k] = (newData + data[i][j][k])/((smoothLength*2)+1); 
					} 
					if (dimension.equals("y")) {
						for (int l = 0; l < smoothLength; l++) {
							newData += (data[i][k-l][j] + data[i][k+1][j]);
						}
						data[i][k][j] = (newData + data[i][k][j])/((smoothLength*2)+1); 				
					} 
					if (dimension.equals("z")) {
						for (int l = 0; l < smoothLength; l++) {
							newData += (data[k-l][j][i] + data[k+l][j][i]);
						}
						data[k][j][i] = (newData + data[k][j][i])/((smoothLength*2)+1); 
					} 
				}
			}
		}
		return data;
	}
	
	public static double round(double val, int places) {
	    if (places < 0) throw new IllegalArgumentException();
	    long factor = (long) Math.pow(10, places);
	    val = val * factor;
	    long tmp = Math.round(val);
	    return (double) tmp / factor;
	}
}

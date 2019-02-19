package org.seqcode.projects.sem.GMM;

import java.util.*;
import java.io.*;

import org.seqcode.projects.sem.events.BindingSubtype;
import org.seqcode.projects.sem.framework.SEMConfig;
import org.seqcode.deepseq.experiments.ExperimentManager;
import org.seqcode.deepseq.experiments.ExperimentCondition;
import org.seqcode.deepseq.experiments.ControlledExperiment;
import org.seqcode.gseutils.Pair;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.distribution.NormalDistribution;

public class FiniteGaussianMixture extends AbstractCluster{
	protected ExperimentCondition cond;
	protected SEMConfig semconfig;
	protected Map<Integer, Integer> mergeFragSizeFrequency;
	protected List<HashMap<Integer, Integer>> fragSizeFrequency;
	
	protected int[] trainingData;
	protected int size = 0;
	protected int mixNum = 3;
	protected double[] weights;
	protected double[] m_means;
	protected double[] m_vars;
	protected double[] m_minVars;
	
	protected Map<Integer, RealVector> clusterMu;
	protected Map<Integer, RealMatrix> clusterSigma;
	
	protected static final int dimension = 1;
	protected static final double MIN_VAR = 1E-10;
	
	/**
	 * Standard constructor
	 * @param cond
	 * @param s
	 * @param frequency
	 * @author Jianyu Yang
	 */
	public FiniteGaussianMixture(ExperimentCondition cond, SEMConfig s, List<HashMap<Integer, Integer>> frequency, int mixNum) {
		this.cond = cond;
		this.semconfig = s;
		this.mixNum = mixNum;
		fragSizeFrequency = frequency;
		mergeFragSizeFrequency = new HashMap<Integer, Integer>();
		
		//Merge all fragment size frequency to a single frequency
		for(HashMap<Integer, Integer> d: fragSizeFrequency) {
			d.forEach((k,v) -> mergeFragSizeFrequency.merge(k, v, (a,b)->a+b));
			d.forEach((k,v) -> size += v);
		}
		
		//Generate dataset
		trainingData = new int[size];
		int index=0;
		for(int fz: mergeFragSizeFrequency.keySet()) {
			for(int count=0; count<mergeFragSizeFrequency.get(fz); count++) {
				trainingData[index] = fz;
				index++;
			}
		}
		
		//Initialize matrix
		weights = new double[mixNum];
		m_means = new double[mixNum];
		m_vars = new double[mixNum];
		m_minVars = new double[mixNum];
	}
	
	/**
	 * Test constructor
	 * @param csvFile represents file containing fragment size frequency information
	 */
	public FiniteGaussianMixture(String csvFile, String csvSplitBy) {
		//Merge all fragment size frequency to a single frequency
		
        String line = "";
        Map<Integer, Integer> frequency = new HashMap<Integer, Integer>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {

            while ((line = br.readLine()) != null) {

                // use tab as separator
                String[] info = line.split(csvSplitBy);
                frequency.put(Integer.parseInt(info[0]), Integer.parseInt(info[1]));
            }
            System.out.println(frequency);

        } catch (IOException e) {
            e.printStackTrace();
        }
        
        mergeFragSizeFrequency  = frequency;
        for(int i: mergeFragSizeFrequency.keySet()) {
        	size += mergeFragSizeFrequency.get(i);
        }
        
		//Generate dataset
        trainingData = new int[size];
		int index=0;
		for(int fz: mergeFragSizeFrequency.keySet()) {
			for(int count=0; count<mergeFragSizeFrequency.get(fz); count++) {
				trainingData[index] = fz;
				index++;
			}
		}
		
		//Initialize matrix
		weights = new double[mixNum];
		m_means = new double[mixNum];
		m_vars = new double[mixNum];
		m_minVars = new double[mixNum];
	}
	
	//Setters
	
	//Accessors
	public Map<Integer, RealVector> getMu() {return clusterMu;}
	public Map<Integer, RealMatrix> getSigma() {return clusterSigma;}
	
	/**
	 * Fit finite Gaussian Mixture on trainingData
	 */
	@Override
	public void excute() {	
		int m_maxIterNum = 200;
		double err = 0.00001;
		
		boolean loop = true;
		double iterNum = 0;
		double lastL = 0;
		double currL = 0;
		int unchanged = 0;
		
//		initParameters(trainingData);
		
		m_means = new double[] {100, 200, 300};
		m_vars = new double[] {10, 10, 40};
		weights = new double[] {0.3, 0.4, 0.3};
		
		double[] next_means = new double[mixNum];
		double[] next_weights = new double[mixNum];
		double[] next_vars = new double[mixNum];
		double[][] resp = new double[mixNum][size];
		List<DataNode> cList = new ArrayList<DataNode>();
		
		while(loop) {
			System.out.println("========================Round"+iterNum+"===================================");
			Arrays.fill(next_weights, 0);
			cList.clear();
			for(int i=0; i<mixNum; i++) {
				Arrays.fill(next_means, 0);
				Arrays.fill(next_vars, 0);
			}
			
			lastL = currL;
			currL = 0;
			for(int k=0; k<size; k++) {
				double p = getProbability(trainingData[k]);	// Sum of all probability density function on this data point
				DataNode dn = new DataNode(trainingData[k]);
				dn.index = k;
				cList.add(dn);
				double maxResp = Double.MIN_VALUE;
				for(int j=0; j<mixNum; j++) {					
					resp[j][k] = getProbability(trainingData[k], j) * weights[j] / p;	// Proportion of a specific PDF on this data point		
					next_weights[j] += resp[j][k];
					if(resp[j][k]>maxResp) {
						maxResp = resp[j][k];
						dn.cindex = j;
					}
				}
				currL += (p > 1E-20) ? Math.log(p) : -20;
			}
			currL /= size;
			
			// Re-estimation: generate new weights, means and variances.
			for (int j=0; j<mixNum; j++) {
				weights[j] = next_weights[j] / size;
			}
			
			// means
			for (DataNode dn: cList) {
				for(int j=0; j<mixNum; j++) {
				if(weights[j]>0) {
					next_means[j] += resp[j][dn.index] * trainingData[dn.index] / next_weights[j];
				}
				}
			}
			
			// variances
			for (DataNode dn: cList) {
				for(int j=0; j<mixNum; j++) {
				if(weights[j]>0) {
					next_vars[j] += resp[j][dn.index] * Math.pow((trainingData[dn.index]-next_means[j]), 2) / next_weights[j];
				}
				}
			}
			
			// Check termination			
			m_means = next_means.clone();
			m_vars = next_vars.clone();
			System.out.println("next_weights: "+Arrays.toString(weights));		
			System.out.println("next_means: "+Arrays.toString(m_means));
			System.out.println("next_vars: "+Arrays.toString(m_vars));
			iterNum++;
			if(Math.abs(currL - lastL) < err * Math.abs(lastL)) {
				unchanged++;
			}
			if(iterNum >= m_maxIterNum || unchanged >= 5 ) {
				loop = false;
			}
		}
		
		// Print result
		System.out.println("======================Final Results===================");
		for(int j=0; j<mixNum; j++) {
				System.out.println("["+j+"]");
				System.out.println("means: "+m_means[j]);
				System.out.println("vars: "+m_vars[j]);
				System.out.println();
		}
		
//		// Get cluster assignment
//		for(int i=0; i<size; i++) {
//			System.out.println("data[" + i + "]=" + trainingData[i] + " cindex : " + cList.get(i).cindex);
//		}
		
		// Put data into map
		clusterMu = new HashMap<Integer, RealVector>();
		clusterSigma = new HashMap<Integer, RealMatrix>();
		
		for(int j=0; j<mixNum; j++) {
			clusterMu.put(j, MatrixUtils.createRealVector(new double[] {m_means[j]}));
			clusterSigma.put(j, MatrixUtils.createRealMatrix(new double[][] { new double[] {m_vars[j]}}));
		}
	}

	/**
	 * @param data
	 */
	private void initParameters(int[] data) {
		// Initialize cluster means randomly
		System.out.println(Arrays.toString(data));
		List<DataNode> cList = new ArrayList<DataNode>();
		for(int i=0; i<mixNum; i++) {
				m_means[i] = data[(int)(Math.random()*size)];
		}
		
		// Assign data points to the closest cluster
		int[] types = new int[size];
		double[] counts = new double[mixNum];
		for(int k=0; k<size; k++) {
			DataNode dn = new DataNode(data[k]);
			double min = Double.MAX_VALUE;
			for(int i=0; i<mixNum; i++) {
				double v=0;
				v = Math.abs(data[k]-m_means[i]);
				if(v<min && counts[i]<=size/3) {
					min=v;
					dn.cindex = i;
				}
			}
			counts[dn.cindex]++;
			cList.add(dn);
		}
		
		// Compute weights
		for(int i=0; i<mixNum; i++) {
			weights[i] = counts[i] / size;
		}
		
		// Compute variance
		for(DataNode dn: cList) {
			// Count each Gaussian
				m_vars[dn.cindex] += Math.pow((dn.value - m_means[dn.cindex]), 2);
		}
		
		// Initialize each Gaussian.
		for(int i=0; i<mixNum; i++) {
			if(weights[i]>0) {
					m_vars[i] = m_vars[i] / counts[i];
			}
		}
		
		System.out.println("=================Initialization=================");
		for(int i=0; i<mixNum; i++) {
				System.out.println("[" + i + "]: ");
				System.out.println("means : " + m_means[i]);
				System.out.println("var : " + m_vars[i]);
				System.out.println("weights: "+weights[i]);
		}
	}
	
	public double getProbability(double data) {
		double p = 0;
		for(int i=0; i<mixNum; i++) {
			p += weights[i] * getProbability(data, i);
		}
		return p;
	}
	
	public double getProbability(double x, int j) {
		double p = 1;
		if(Math.abs((x-m_means[j])/m_vars[j]) <= 1.96 ) {
			p *= 1 / Math.sqrt(2 * Math.PI * m_vars[j]);
			p *= Math.exp(-0.5 * Math.pow((x - m_means[j]), 2) / m_vars[j]);
		} else {
			p = 1e-20;
		}
		return p;
	}
	
	//Save cluster parameters to csv file
	public void save(String outFile, String csvSplitBy) {
		
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
        	
        	for(Object o: clusterMu.keySet()) {
        		bw.write(Double.toString(clusterMu.get(o).getEntry(0)));
        		bw.write(csvSplitBy);
        		bw.write(Double.toString(clusterSigma.get(o).getEntry(0, 0)));
        		bw.newLine();
        	}
        	
        	
        } catch (IOException e) {
        	e.printStackTrace();
        }
	}
	
	public class DataNode {
		public int cindex; // cluster
		public int index;
		public double value;
		
		public DataNode(double v) {
			this.value = v;
			cindex = -1;
			index = -1;
		}
	}
	
	//GMM on low MNase data from MPE-seq
	public static void main(String[] args) {
		String csvSplitBy = "\t";
		String folderName = "C:\\Users\\Administrator\\Dropbox\\Code\\GaussianDPMM_fragSize_sample\\LM_H3_rep2\\";
		for(int index=0; index<1; index++) {
			String csvFile = folderName + "fragSizeFrequencySample" + index;
			String outFile = folderName + "ClusterParameters" + index;
		
			FiniteGaussianMixture g = new FiniteGaussianMixture(csvFile, csvSplitBy);
			g.excute();
			g.save(outFile, csvSplitBy);
		}
	}
}

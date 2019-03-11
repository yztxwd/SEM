package org.seqcode.projects.sem.events;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import org.seqcode.gseutils.Pair;
import org.seqcode.math.stats.StatUtil;

import org.seqcode.deepseq.StrandedPair;
import org.seqcode.deepseq.experiments.ExperimentManager;
import org.seqcode.deepseq.experiments.ControlledExperiment;
import org.seqcode.deepseq.experiments.ExperimentCondition;
import org.seqcode.genome.GenomeConfig;
import org.seqcode.genome.location.Region;

/**
 * BindingModel defines a (probabilistic) model of read occurrences around a binding event.
 * In SEM, we assume fragment midpoints follow a Gaussian distribution around nucleosome dyad location
 * with different variance (which we call "fuzziness")
 * @author Jianyu Yang
 *
 */
public class BindingModel {
	protected ExperimentManager manager;
	protected GenomeConfig gconfig;
	protected ExperimentCondition cond;
	
	protected double initialFuzziness;
	protected List<Pair<String, Integer>> initialDyad;
	protected Map<Integer, Double> pairFreqAroundInitialDyad;
		
	protected static final double LOG2 = Math.log(2);
	protected static final double ROOT2PI = Math.sqrt(2*Math.PI);
	public static final int max = 100;
	protected static double bgProb=Double.MIN_VALUE;
	protected static double logBgProb=-1074;
	
	// Constructor: Read in dyad location of nucleosome to initialize fuzziness
	public BindingModel(String dyadFile, ExperimentManager eman, ExperimentCondition ec, GenomeConfig gc) {
		manager = eman;	
		gconfig = gc;
		cond = ec;
		initialDyad = new ArrayList<Pair<String, Integer>>();
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(dyadFile));
			String line = "";
			while( (line=br.readLine()) != null) {
				if(line.charAt(0) == '#') {continue;}
				// use \t as delimiter
				String[] info = line.split("\t");
				initialDyad.add(new Pair<String, Integer>(info[0].replaceFirst("^chromosome", "").
																	replaceFirst("^chrom", "").replaceFirst("^chr", ""), Integer.parseInt(info[1])));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		initializeFuzziness();		
	};
	
	//Accessors
	public double getIntialFuzziness() {return initialFuzziness;}
	public static int getMaxInfluenceRange() {return max*2;}
	
	//Look up the probability corresponding to a distance
	//Distance should be defined as (Read position - Peak position)
	public static double probability(double variance, int distance) throws Exception {
//		double z = distance/Math.sqrt(variance);
//		if(z<-1.96 || z>1.96 || distance<min || distance>max ) {
//			return(bgProb);
//		} else {
//			double prob = 1/(Math.sqrt(variance)*ROOT2PI) * Math.exp(-Math.pow(distance, 2)/(2*variance));
//			return prob;
//		}
		
		if(Math.abs(distance) > max) {
			return 0;
		} else if (variance > 0) {
			double prob = 1/(Math.sqrt(variance)*ROOT2PI) * Math.exp(-Math.pow(distance, 2)/(2*variance));
			return prob;
		} else if (variance == 0 && distance == 0) {
			return 1;
		} else if (variance == 0 && distance > 0) {
			return 0;
		} else {
			throw new Exception("Variance must >= 0!");
		}
	}
	
	public static double logProbability(double variance, int distance) throws Exception {
//		double z = distance/Math.sqrt(variance);
//		if(z<-1.96 || z>1.96 || distance<min || distance>max ){
//		  return(logBgProb);
//		}else{
//		  return(Math.log(probability(variance, distance)));
//		}	
		
		try {
			double prob = probability(variance, distance);
			if(prob > 0) {
				return Math.log(prob);
			} else if (prob == 0) {
				return -Double.MAX_VALUE;
			} else {
				throw new Exception("Dectected prob < 0 !");
			}
		} catch (Exception e) {
			throw e;
		}
	}
	
	private void initializeFuzziness() {
		// Initialize frequency map
		pairFreqAroundInitialDyad = new HashMap<Integer, Double>();
		for(int i=-75; i<=75; i++) {
			pairFreqAroundInitialDyad.put(i, 0.);
		}
		
		for(Pair<String, Integer> p: initialDyad) {
			//Get strandedPair around dyad +/- 75bp
			int start = (p.cdr()-75)>0 ? (p.cdr()-75) : 0;
			int end = p.cdr() + 75;
			Region r = new Region(gconfig.getGenome(), p.car(), start, end);
			List<StrandedPair> pairs = new ArrayList<StrandedPair>();
			for(ControlledExperiment rep: cond.getReplicates()) {
				pairs.addAll(rep.getSignal().getPairsByMid(r));
			}
			
			//Add strandedPair into frequency map
			for (StrandedPair pair: pairs) {
				int distance = p.cdr() - pair.getMidpoint().getLocation();
				pairFreqAroundInitialDyad.put(distance, pairFreqAroundInitialDyad.get(distance) + pair.getWeight());
			}
		}
		
		//Compute initial fuzziness for all nucleosomes
		initialFuzziness = 0;
		double sumWeight = 0;
		for(int dis: pairFreqAroundInitialDyad.keySet()) {
			initialFuzziness += Math.pow(dis, 2) * pairFreqAroundInitialDyad.get(dis);
			sumWeight += pairFreqAroundInitialDyad.get(dis);
		}
		initialFuzziness /= sumWeight;
		
		//monitor
		System.out.println("Initialize Fuzziness: "+initialFuzziness);
	}
	
	public static void main(String[] args) {
		String folder = "D:\\Yen lab\\2017\\Analysis\\Code\\sem-test\\";
		String dyadFile = "Dyad_H3Q85C_GSE97290_top1000.txt";
		
		
	}
}

	
	

package mthesis.concurrent_graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mthesis.concurrent_graph.examples.CCDetectVertex;
import mthesis.concurrent_graph.examples.CCOutputWriter;
import mthesis.concurrent_graph.examples.EdgeListReader;
import mthesis.concurrent_graph.master.AbstractMasterOutputWriter;
import mthesis.concurrent_graph.master.MasterMachine;
import mthesis.concurrent_graph.master.input.BaseInputPartitionDistributor;
import mthesis.concurrent_graph.master.input.BaseMasterInputReader;
import mthesis.concurrent_graph.master.input.ContinousInputPartitionDistributor;
import mthesis.concurrent_graph.util.Pair;
import mthesis.concurrent_graph.worker.WorkerMachine;

public class QuickTest {

	public static void main(String[] args) throws Exception {
		//		final String inputData = "../../Data/cctest.txt";
		//		final Class<? extends AbstractMasterInputReader> inputReader = VertexEdgesInputReader.class;

		final String inputData = "../../Data/Wiki-Vote.txt";
		final Class<? extends BaseMasterInputReader> inputReader = EdgeListReader.class;
		final BaseInputPartitionDistributor inputDistributor = new ContinousInputPartitionDistributor();
		final int partitionLines = 4000;

		//Thread.sleep(10000);

		final String inputDir = "input";
		final String outputDir = "output";
		final int numWorkers = 4;
		final String host = "localhost";
		final int basePort = 23499;
		final Class<? extends AbstractMasterOutputWriter> outputWriter = CCOutputWriter.class;
		final Class<? extends AbstractVertex> vertexClass = CCDetectVertex.class;

		final Map<Integer, Pair<String, Integer>> allCfg = new HashMap<>();
		final List<Integer> allWorkerIds= new ArrayList<>();
		allCfg.put(-1, new Pair<String, Integer>(host, basePort));
		for(int i = 0; i < numWorkers; i++) {
			allWorkerIds.add(i);
			allCfg.put(i, new Pair<String, Integer>(host, basePort + 1 + i));
		}

		System.out.println("Starting");
		//final MasterNode master =
		startMaster(allCfg, -1, allWorkerIds, inputData, inputDir, outputDir, partitionLines, inputReader, inputDistributor, outputWriter);

		final List<WorkerMachine> workers = new ArrayList<>();
		for(int i = 0; i < numWorkers; i++) {
			workers.add(startWorker(allCfg, i, allWorkerIds, outputDir, vertexClass));
		}


		//		master.waitUntilStarted();
		//		worker0.waitUntilStarted();
		//		worker1.waitUntilStarted();
		//		System.out.println("All started");
		//		Thread.sleep(240000);

		//		System.out.println("Shutting down");
		//		master.stop();
		//		worker0.stop();
		//		worker1.stop();
		//		System.out.println("End");
	}

	private static WorkerMachine startWorker(Map<Integer, Pair<String, Integer>> allCfg,
			int id, List<Integer> allWorkers, String output,
			Class<? extends AbstractVertex> vertexClass) {
		final WorkerMachine node = new WorkerMachine(allCfg, id, allWorkers, -1, output, vertexClass);
		node.start();
		return node;
	}

	private static MasterMachine startMaster(Map<Integer, Pair<String, Integer>> allCfg,
			int id, List<Integer> allWorkers, String inputData, String inputDir, String outputDir, int partitionSize,
			Class<? extends BaseMasterInputReader> inputReader,
			BaseInputPartitionDistributor inputDistributor,
			Class<? extends AbstractMasterOutputWriter> outputWriter) {
		final MasterMachine node = new MasterMachine(allCfg, id, allWorkers, inputData, inputDir, outputDir, partitionSize, inputReader, inputDistributor, outputWriter);
		node.start();
		return node;
	}
}

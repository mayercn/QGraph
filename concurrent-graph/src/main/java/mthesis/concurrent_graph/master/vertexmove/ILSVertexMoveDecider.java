package mthesis.concurrent_graph.master.vertexmove;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mthesis.concurrent_graph.Configuration;
import mthesis.concurrent_graph.util.MiscUtil;

public class ILSVertexMoveDecider extends AbstractVertexMoveDecider {

	private static final Logger logger = LoggerFactory.getLogger(ILSVertexMoveDecider.class);

	private final double VerticesActiveImbalanceThreshold = Configuration.getPropertyDoubleDefault("VertexMoveActiveBalance", 0.1);
	private final double VerticesActiveImbalanceMin = 1 - VerticesActiveImbalanceThreshold;
	private final double VerticesActiveImbalanceMax = 1 + VerticesActiveImbalanceThreshold;
	private final double VerticesTotalImbalanceThreshold = Configuration.getPropertyDoubleDefault("VertexMoveTotalBalance", 0.1);
	private final double VerticesTotalImbalanceMin = 1 - VerticesTotalImbalanceThreshold;
	private final double VerticesTotalImbalanceMax = 1 + VerticesTotalImbalanceThreshold;
	private final long MaxTotalImproveTime = Configuration.MASTER_QUERY_MOVE_CALC_TIMEOUT;
	private final long MaxGreedyImproveTime = Configuration.MASTER_QUERY_MOVE_CALC_TIMEOUT / 4; // TODO Config
	// TODO Configuration
	private final long MinMoveTotalVertices = 500;
	private final int MaxImproveIterations = 30;

	private long decideStartTime;

	private List<IlsLogItem> ilsLog = new ArrayList<>();
	private double latestPertubatedDistributionCosts;
	private boolean saveIlsLog = true;



	@Override
	public VertexMoveDecision decide(Set<Integer> queryIds, Map<Integer, Map<IntSet, Integer>> workerQueryChunks,
			Map<Integer, Long> workerTotalVertices) {
		if (queryIds.size() == 0 || workerQueryChunks.size() == 0) return null;

		List<Integer> queryIdsList = new ArrayList<>(queryIds);
		List<Integer> workerIds = new ArrayList<>(workerQueryChunks.keySet());
		Map<Integer, QueryWorkerMachine> queryMachines = new HashMap<>();
		for (Entry<Integer, Map<IntSet, Integer>> worker : workerQueryChunks.entrySet()) {
			List<QueryVertexChunk> workerChunks = new ArrayList<>();
			for (Entry<IntSet, Integer> chunk : worker.getValue().entrySet()) {
				workerChunks.add(new QueryVertexChunk(chunk.getKey(), chunk.getValue(), worker.getKey()));
			}
			QueryWorkerMachine workerMachine = new QueryWorkerMachine(workerChunks, workerTotalVertices.get(worker.getKey()));
			queryMachines.put(worker.getKey(), workerMachine);
		}

		QueryDistribution originalDistribution = new QueryDistribution(queryIds, queryMachines);
		QueryDistribution bestDistribution = originalDistribution;
		System.out.println(bestDistribution.getCurrentCosts());

		logger.info("///////////////////////////////// Move decission");
		logger.debug("Move decission queries {}", queryIds);
		logger.trace("Move decission workerQueryChunks {}", workerQueryChunks);
		//		bestDistribution.printMoveDistribution();

		//		QueryDistribution test = bestDistribution.clone();
		//		test.moveVertices(0, 1, 0);
		//		System.out.println(bestDistribution.getCosts());
		//		bestDistribution.printMoveDecissions();
		//		bestDistribution.printMoveDistribution();
		//
		//		test.moveVertices(0, 2, 0);
		//		System.out.println(bestDistribution.getCosts());
		//		bestDistribution.printMoveDecissions();
		//		bestDistribution.printMoveDistribution();
		//
		//		System.out.println("/////////////////////////////////");

		int totalVerticesMoved = 0;
		ilsLog.clear();
		decideStartTime = System.currentTimeMillis();

		// Greedy improve initial distribution
		latestPertubatedDistributionCosts = bestDistribution.getCurrentCosts();
		bestDistribution = optimizeGreedy(queryIds, workerIds, bestDistribution);

		int maxIlsIteraions = 20;
		Random rd = new Random(0);

		// Do ILS with pertubations
		int i = 0;
		for (; i < maxIlsIteraions && (System.currentTimeMillis() - decideStartTime) < MaxTotalImproveTime; i++) {
			QueryDistribution ilsDistribution = pertubationQueryUnifyLargestPartition(queryIdsList, workerIds, bestDistribution, rd);
			latestPertubatedDistributionCosts = ilsDistribution.getCurrentCosts();
			logger.info("Pertubation " + ilsDistribution.getCurrentCosts());

			ilsDistribution = optimizeGreedy(queryIds, workerIds, ilsDistribution);

			boolean isGoodNew = isGoodNewDistribution(bestDistribution, ilsDistribution, workerIds);
			if (isGoodNew) {
				bestDistribution = ilsDistribution;
				logger.info("Improved pertubation " + ilsDistribution.getCurrentCosts());
			}
			if (saveIlsLog) ilsLog.add(new IlsLogItem(ilsDistribution.getCurrentCosts(), latestPertubatedDistributionCosts, isGoodNew));
		}

		int movedVertices = bestDistribution.calculateMovedVertices();
		logger.info("+++++++++++++ Stopped deciding after " + i + " ILS iterations in " + (System.currentTimeMillis() - decideStartTime) + "ms moving "
				+ movedVertices);

		//bestDistribution.printMoveDistribution();
		//		bestDistribution.printMoveDecissions();


		if (saveIlsLog) {
			try (PrintWriter writer = new PrintWriter(new FileWriter("ILS_log_" + System.currentTimeMillis() + ".csv"))) {
				writer.println("Costs;LastPertubationCosts;ValidCosts;");
				for (IlsLogItem ilsLogItem : ilsLog) {
					if(ilsLogItem.isValid)
						writer.println(ilsLogItem.costs + ";" + ilsLogItem.lastPertubationCosts + ";" + ilsLogItem.costs + ";");
					else
						writer.println(ilsLogItem.costs + ";" + ilsLogItem.lastPertubationCosts + ";;");
				}
			}
			catch (Exception e) {
				logger.error("Save ILS failed", e);
			}
		}

		if (movedVertices < MinMoveTotalVertices) {
			logger.info("Decided not move, not enough vertices: " + totalVerticesMoved);
			return null;
		}
		logger.info("Decided move, vertices: " + totalVerticesMoved);
		return bestDistribution.toMoveDecision(workerIds);
	}


	private QueryDistribution optimizeGreedy(Set<Integer> queryIds, List<Integer> workerIds, QueryDistribution baseDistribution) {
		long minActiveVertices = (long) (baseDistribution.workerActiveVertices / workerIds.size() * VerticesActiveImbalanceMin);
		long maxActiveVertices = (long) (baseDistribution.workerActiveVertices / workerIds.size() * VerticesActiveImbalanceMax);
		long minTotalVertices = (long) (baseDistribution.workerTotalVertices / workerIds.size() * VerticesTotalImbalanceMin);
		long maxTotalVertices = (long) (baseDistribution.workerTotalVertices / workerIds.size() * VerticesTotalImbalanceMax);

		QueryDistribution bestDistribution = baseDistribution;
		int i = 0;
		for (; i < MaxImproveIterations && (System.currentTimeMillis() - decideStartTime) < MaxGreedyImproveTime; i++) {
			QueryDistribution iterBestDistribution = bestDistribution.clone();
			boolean anyImproves = false;

			// TODO Move chunks instead of queries?
			for (Integer queryId : queryIds) {
				// TODO Queries not to move?

				for (Integer fromWorkerId : workerIds) {
					// Dont move from worker if worker has too few vertices afterwards
					QueryWorkerMachine fromWorker = baseDistribution.getQueryMachines().get(fromWorkerId);
					int fromWorkerQueryVertices = MiscUtil.defaultInt(fromWorker.queryVertices.get(queryId));
					if (fromWorkerQueryVertices == 0) continue;
					if (fromWorker.activeVertices - fromWorkerQueryVertices < minActiveVertices
							|| fromWorker.totalVertices - fromWorkerQueryVertices < minTotalVertices)
						continue;

					for (Integer toWorkerId : workerIds) {
						if (fromWorkerId == toWorkerId) continue;
						// Dont move to worker if has too many vertices afterwards
						QueryWorkerMachine toWorker = baseDistribution.getQueryMachines().get(toWorkerId);
						int toWorkerQueryVertices = MiscUtil.defaultInt(toWorker.queryVertices.get(queryId));
						if (toWorkerQueryVertices == 0) continue;
						if (toWorker.activeVertices + toWorkerQueryVertices > maxActiveVertices
								|| toWorker.totalVertices + toWorkerQueryVertices > maxTotalVertices)
							continue;

						// Only move smaller to larger partition
						if (fromWorkerQueryVertices > toWorkerQueryVertices) continue;

						// TODO Optimize, neglect cases.

						QueryDistribution newDistribution = bestDistribution.clone();
						int movedVerts = newDistribution.moveVertices(queryId, fromWorkerId, toWorkerId, true);

						System.out.println();
						System.out.println(newDistribution.getCurrentCosts() + " vs " + iterBestDistribution.getCurrentCosts() + " after " + movedVerts + " "
								+ fromWorker + "->" + toWorker);
						// TODO movedVerts > MinMoveWorkerVertices &&
						if (isGoodNewDistributionMove(iterBestDistribution, newDistribution, fromWorkerId, toWorkerId)) {
							if (saveIlsLog) ilsLog.add(new IlsLogItem(iterBestDistribution.getCurrentCosts(), latestPertubatedDistributionCosts,
									isGoodNewDistribution(iterBestDistribution, newDistribution, workerIds)));
							iterBestDistribution = newDistribution;
							anyImproves = true;
							//							System.out.println("## i " + i + ": " + newDistribution.getCosts());
							//							System.out
							//									.println("# " + newDistribution.getCosts() + " vs " + bestDistribution.getCosts());
						}
					}
				}
			}

			if (!anyImproves) {
				logger.info("No more improves after " + i + " " + iterBestDistribution.getCurrentCosts());
				break;
			}
			bestDistribution = iterBestDistribution;
			//totalVerticesMoved += verticesMovedIter;
		}
		logger.info("+ Stopped greedy after " + i + " iterations in " + (System.currentTimeMillis() - decideStartTime) + "ms" + " "
				+ bestDistribution.getCurrentCosts());
		return bestDistribution;
	}

	/**
	 * Checks if a new distribution after a move step is better than the old one and has sufficient workload balancing.
	 * Checks workers affected by the move.
	 */
	private boolean isGoodNewDistributionMove(QueryDistribution oldDistribution, QueryDistribution newDistribution, Integer fromWorker, Integer toWorker) {
		return newDistribution.getCurrentCosts() < oldDistribution.getCurrentCosts()
				&& checkWorkerActiveVerticesOk(oldDistribution, newDistribution, fromWorker, toWorker)
				&& checkWorkerTotalVerticesOk(oldDistribution, newDistribution, fromWorker, toWorker);
	}

	/**
	 * Checks if a new distribution is better than the old one and has sufficient workload balancing at all workers.
	 */
	private boolean isGoodNewDistribution(QueryDistribution oldDistribution, QueryDistribution newDistribution, List<Integer> workerIds) {
		if( newDistribution.getCurrentCosts() >= oldDistribution.getCurrentCosts())
			return false;
		for(Integer worker : workerIds) {
			if (!checkWorkerActiveVerticesOk(oldDistribution, newDistribution, worker) || !checkWorkerTotalVerticesOk(oldDistribution, newDistribution, worker))
				return false;
		}
		return true;
	}

	/**
	 * Pertubation by moving all partitions of a query to with least load machine
	 */
	@SuppressWarnings("unused")
	private QueryDistribution pertubationQueryUnifyLeastLoaded(List<Integer> queryIds, List<Integer> workerIds, QueryDistribution baseDistribution, Random rd) {
		QueryDistribution pertubated = baseDistribution.clone();
		int pertubationQuery = getRandomFromList(queryIds, rd);
		int bestWorkerId = 0;
		long bestWorkerSize = Integer.MAX_VALUE;
		for (Entry<Integer, QueryWorkerMachine> machine : baseDistribution.getQueryMachines().entrySet()) {
			if (machine.getValue().totalVertices < bestWorkerSize) {
				bestWorkerSize = machine.getValue().totalVertices;
				bestWorkerId = machine.getKey();
			}
		}

		for (int worker : workerIds) {
			if (worker != bestWorkerId) {
				pertubated.moveVertices(pertubationQuery, worker, bestWorkerId, true);

			}
		}

		// TODO No pertubation without workload balance

		return pertubated;
	}

	/**
	 * Pertubation by moving all partitions of a query to machine with largest partition
	 */
	private QueryDistribution pertubationQueryUnifyLargestPartition(List<Integer> queryIds, List<Integer> workerIds, QueryDistribution baseDistribution,
			Random rd) {
		QueryDistribution pertubated = baseDistribution.clone();
		int pertubationQuery = getRandomFromList(queryIds, rd);

		int bestWorkerId = 0;
		long bestWorkerPartitionSize = 0;
		for (Entry<Integer, QueryWorkerMachine> machine : baseDistribution.getQueryMachines().entrySet()) {
			int partitionSize = MiscUtil.defaultInt(machine.getValue().queryVertices.get(pertubationQuery)) ;
			if (partitionSize > bestWorkerPartitionSize) {
				bestWorkerPartitionSize = partitionSize;
				bestWorkerId = machine.getKey();
			}
		}

		for(int worker : workerIds) {
			if (worker != bestWorkerId) {
				pertubated.moveVertices(pertubationQuery, worker, bestWorkerId, true);

			}
		}

		return pertubated;
	}



	private boolean checkWorkerActiveVerticesOk(QueryDistribution oldDistribution, QueryDistribution newDistribution, int fromWorker,
			int toWorker) {
		return checkWorkerActiveVerticesOk(oldDistribution, newDistribution, fromWorker)
				&& checkWorkerActiveVerticesOk(oldDistribution, newDistribution, toWorker);
	}

	private boolean checkWorkerTotalVerticesOk(QueryDistribution oldDistribution, QueryDistribution newDistribution, int fromWorker,
			int toWorker) {
		return checkWorkerTotalVerticesOk(oldDistribution, newDistribution, fromWorker)
				&& checkWorkerTotalVerticesOk(oldDistribution, newDistribution, toWorker);
	}

	private boolean checkWorkerActiveVerticesOk(QueryDistribution oldDistribution, QueryDistribution newDistribution, int worker) {
		double oldImbalance = oldDistribution.getWorkerActiveVerticesImbalanceFactor(worker);
		double newImbalance = newDistribution.getWorkerActiveVerticesImbalanceFactor(worker);
		return (newImbalance <= oldImbalance || newImbalance <= VerticesActiveImbalanceThreshold);
	}

	private boolean checkWorkerTotalVerticesOk(QueryDistribution oldDistribution, QueryDistribution newDistribution, int worker) {
		double oldImbalance = oldDistribution.getWorkerTotalVerticesImbalanceFactor(worker);
		double newImbalance = newDistribution.getWorkerTotalVerticesImbalanceFactor(worker);
		return (newImbalance <= oldImbalance || newImbalance <= VerticesTotalImbalanceThreshold);
	}



	// Testing
	public static void main(String[] args) throws Exception {
		Map<String, String> overrideConfigs = new HashMap<>();
		overrideConfigs.put("MASTER_QUERY_MOVE_CALC_TIMEOUT", "999999999");
		Configuration.loadConfig("configs\\configuration.properties", overrideConfigs);

		test2Equal();
	}

	// 2 workers, 2 queries, equal hashed-like staring state
	private static void test2Equal() {
		Set<Integer> queryIds = new HashSet<>();
		Map<Integer, Map<IntSet, Integer>> workerQueryChunks = new HashMap<>();
		Map<Integer, Long> workerTotalVertices = new HashMap<>();

		for (int iQ = 0; iQ < 2; iQ++) {
			queryIds.add(iQ);
		}
		for (int iW = 0; iW < 2; iW++) {
			workerTotalVertices.put(iW, 10000L);
		}
		for (int iW = 0; iW < 2; iW++) {
			Map<IntSet, Integer> chunks = new HashMap<>();
			for (int iQ = 0; iQ < 2; iQ++) {
				chunks.put(new IntOpenHashSet(new int[] { iQ }), 5000);
			}
			workerQueryChunks.put(iW, chunks);
		}

		ILSVertexMoveDecider decider = new ILSVertexMoveDecider();
		VertexMoveDecision decission = decider.decide(queryIds, workerQueryChunks, workerTotalVertices);
		decission.printDecission();
	}

	private static <T> T getRandomFromList(List<T> list, Random rd) {
		return list.get(rd.nextInt(list.size()));
	}


	private class IlsLogItem {

		public final int costs;
		public final int lastPertubationCosts;
		public final boolean isValid;

		public IlsLogItem(double costs, double lastPertubationCosts, boolean isValid) {
			super();
			this.costs = (int) costs;
			this.lastPertubationCosts = (int) lastPertubationCosts;
			this.isValid = isValid;
		}
	}
}
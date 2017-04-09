package mthesis.concurrent_graph.master.vertexmove;

import java.util.List;
import java.util.Map;

import mthesis.concurrent_graph.BaseQuery;
import mthesis.concurrent_graph.Configuration;
import mthesis.concurrent_graph.master.MasterQuery;

public class GreedyNewVertexMoveDecider<Q extends BaseQuery> extends AbstractVertexMoveDecider<Q> {

	// TODO Configuration
	private static final double WorkerImbalanceThreshold = 0.3;
	private static final long MinMoveWorkerVertices = 50;
	private static final long MinMoveTotalVertices = 500;

	private long vertexBarrierMoveLastTime = System.currentTimeMillis();


	@Override
	public VertexMoveDecision decide(List<Integer> workerIds, Map<Integer, MasterQuery<Q>> activeQueries,
			Map<Integer, Map<Integer, Integer>> actQueryWorkerActiveVerts,
			Map<Integer, Map<Integer, Map<Integer, Integer>>> actQueryWorkerIntersects) {

		if (!Configuration.VERTEX_BARRIER_MOVE_ENABLED
				|| (System.currentTimeMillis() - vertexBarrierMoveLastTime) < Configuration.VERTEX_BARRIER_MOVE_INTERVAL)
			return null;
		vertexBarrierMoveLastTime = System.currentTimeMillis();


		QueryDistribution originalDistribution = new QueryDistribution(workerIds, actQueryWorkerActiveVerts,
				actQueryWorkerIntersects);
		QueryDistribution bestDistribution = originalDistribution;
		System.out.println(bestDistribution.getCostsNoImbalance());

		System.out.println("/////////////////////////////////");
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

		for (int i = 0; i < 100; i++) {
			QueryDistribution iterBestDistribution = bestDistribution.clone();
			boolean anyImproves = false;
			int verticesMovedIter = 0;

			//			System.out.println(i + " iteration");

			for (Integer queryId : activeQueries.keySet()) {
				for (Integer fromWorker : workerIds) {
					for (Integer toWorker : workerIds) {
						if (fromWorker == toWorker) continue;

						QueryDistribution newDistribution = bestDistribution.clone();
						int movedVerts = newDistribution.moveVertices(queryId, fromWorker, toWorker, true);

						double oldFmImbalance = bestDistribution.getWorkerImbalanceFactor(fromWorker);
						double oldToImbalance = bestDistribution.getWorkerImbalanceFactor(toWorker);
						double newFmImbalance = newDistribution.getWorkerImbalanceFactor(fromWorker);
						double newToImbalance = newDistribution.getWorkerImbalanceFactor(toWorker);
						boolean newFmBalanceBetter = newFmImbalance < oldFmImbalance;
						boolean newToBalanceBetter = newToImbalance < oldToImbalance;
						boolean newFmBalanceOk = newFmImbalance < WorkerImbalanceThreshold;
						boolean newToBalanceOk = newToImbalance < WorkerImbalanceThreshold;

						if (movedVerts > MinMoveWorkerVertices
								&& newDistribution.getCostsNoImbalance() < iterBestDistribution.getCostsNoImbalance()
								&& (newFmBalanceBetter || newFmBalanceOk) && (newToBalanceBetter || newToBalanceOk)) {
							iterBestDistribution = newDistribution;
							anyImproves = true;
							verticesMovedIter = movedVerts;
							//							System.out.println("## i " + i + ": " + newDistribution.getCosts());
							//							System.out
							//									.println("# " + newDistribution.getCosts() + " vs " + bestDistribution.getCosts());
						}
					}
				}
			}

			if (!anyImproves) {
				System.out.println("No more improves after " + i);
				break;
			}
			bestDistribution = iterBestDistribution;
			totalVerticesMoved += verticesMovedIter;
		}

		System.out.println("+++++++++++++");
		//		bestDistribution.printMoveDistribution();
		System.out.println(bestDistribution.getCostsNoImbalance());
		bestDistribution.printMoveDecissions();

		System.out.println("# MOVE " + totalVerticesMoved);
		if (totalVerticesMoved < MinMoveTotalVertices) return null;
		return bestDistribution.toMoveDecision(workerIds);
	}

}
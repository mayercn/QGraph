package mthesis.concurrent_graph.master.vertexmove;

import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.ints.IntSet;
import mthesis.concurrent_graph.BaseQuery;

/**
 * Abstract base class for VertexMove deciders.
 * Decides if and which vertices to move. Returns null if no move at all or a VertexMoveDecision.
 *
 * @author Jonas Grunert
 *
 */
public abstract class AbstractVertexMoveDecider<Q extends BaseQuery> {

	public abstract VertexMoveDecision decide(Set<Integer> queryIds, Map<Integer, Map<IntSet, Integer>> workerQueryChunks,
			Map<Integer, Long> workerTotalVertices);
}

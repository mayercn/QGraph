package mthesis.concurrent_graph.apps.shortestpath;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mthesis.concurrent_graph.BaseQuery;
import mthesis.concurrent_graph.Configuration;
import mthesis.concurrent_graph.QueryStats;


public class SPQuery extends BaseQuery {

	protected static final Logger logger = LoggerFactory.getLogger(SPQuery.class);

	private static final boolean SearchNextTagTestMode = Configuration.getPropertyBoolDefault("SearchNextTagTestMode", false);

	public int From;
	public int To;
	public int Tag;
	// Maximum distance. Initially set to infinite, set to target dist as soon as target discovered
	public double MaxDist;
	public boolean ReconstructionPhaseActive;
	public boolean InitializedReconstructionPhase;


	/**
	 * Public query creation constructor
	 */
	public SPQuery(int queryId, int from, int to) {
		this(queryId, from, to, -1);
	}

	/**
	 * Public query creation constructor
	 */
	public SPQuery(int queryId, int from, int to, int tag) {
		super(queryId);
		From = from;
		To = to;
		Tag = tag;
		MaxDist = Double.POSITIVE_INFINITY;
		ReconstructionPhaseActive = false;
		InitializedReconstructionPhase = false;
	}

	private SPQuery(int queryId, int activeVertices, int vertexCount, QueryStats stats,
			int from, int to, int tag, double maxDist, boolean startedReconstructionPhase, boolean initializedReconstructionPhase) {
		super(queryId, activeVertices, vertexCount, stats);
		From = from;
		To = to;
		Tag = tag;
		MaxDist = maxDist;
		ReconstructionPhaseActive = startedReconstructionPhase;
		InitializedReconstructionPhase = initializedReconstructionPhase;
	}


	/**
	 * Called by master when no more vertices are active
	 * @return Returns TRUE if the query is finished now
	 */
	@Override
	public boolean onMasterAllVerticesFinished() {
		if (!ReconstructionPhaseActive) {
			// Start reconstruction phase
			logger.debug(QueryId + " start reconstruction phase");
			ReconstructionPhaseActive = true;
			return false;
		}
		// Reconstruction finished
		logger.debug(QueryId + " finished reconstruction phase");
		return true;
	}

	/**
	 * Called by master if all workers must be forced active in the next superstep.
	 * @return TRUE if force all workers to be active.
	 */
	@Override
	public boolean masterForceAllWorkersActive(int superstepNo) {
		if (ReconstructionPhaseActive && !InitializedReconstructionPhase) {
			return true;
		}
		return superstepNo <= 0;
	}

	/**
	 * Called by worker before the computation of a new superstep is started
	 * @return Instructions how to start superstep.
	 */
	@Override
	public SuperstepInstructions onWorkerSuperstepStart(int superstepNo) {
		if (ReconstructionPhaseActive && !InitializedReconstructionPhase) {
			logger.debug(QueryId + " worker start initialize reconstruction phase");
			InitializedReconstructionPhase = true;
			Set<Integer> verts = new HashSet<>(1);
			verts.add(To);
			return new SuperstepInstructions(SuperstepInstructionsType.StartSpecific, verts);
		}
		if (superstepNo == 0) {
			Set<Integer> verts = new HashSet<>(1);
			verts.add(From);
			return new SuperstepInstructions(SuperstepInstructionsType.StartSpecific, verts);
		}
		return new SuperstepInstructions(SuperstepInstructionsType.StartActive, null);
	}


	@Override
	public void writeToBuffer(ByteBuffer buffer) {
		super.writeToBuffer(buffer);
		buffer.putInt(From);
		buffer.putInt(To);
		buffer.putInt(Tag);
		buffer.putDouble(MaxDist);
		buffer.put(ReconstructionPhaseActive ? (byte) 0 : (byte) 1);
		buffer.put(InitializedReconstructionPhase ? (byte) 0 : (byte) 1);
	}

	@Override
	public String getString() {
		return super.getString() + ":" + From + ":" + To + ":" + MaxDist + ":" + ReconstructionPhaseActive + ":"
				+ InitializedReconstructionPhase;
	}

	@Override
	public int getBytesLength() {
		return super.getBytesLength() + 3 * 4 + 1 * 8 + 2 * 1;
	}

	@Override
	public int GetQueryHash() {
		final int prime = 31;
		int result = 1;
		result = prime * result + From;
		result = prime * result + To;
		return result;
	}

	@Override
	public void combine(BaseQuery v) {
		SPQuery other = (SPQuery) v;
		if (SearchNextTagTestMode && this.To == -1 && other.To != -1)
			this.To = other.To;
		MaxDist = Math.min(MaxDist, other.MaxDist);
		ReconstructionPhaseActive |= (other).ReconstructionPhaseActive;
		InitializedReconstructionPhase |= (other).InitializedReconstructionPhase;
		super.combine(v);
	}



	public static class Factory extends BaseQueryGlobalValuesFactory<SPQuery> {

		@Override
		public SPQuery createDefault() {
			throw new RuntimeException("Not supported");
		}

		@Override
		public SPQuery createDefault(int queryId) {
			return new SPQuery(queryId, 0, 0);
		}

		@Override
		public SPQuery createFromString(String str) {
			throw new RuntimeException("createFromString not implemented for BaseQueryGlobalValues");
		}

		@Override
		public SPQuery createFromBytes(ByteBuffer bytes) {
			return new SPQuery(bytes.getInt(), bytes.getInt(), bytes.getInt(), new QueryStats(bytes),
					bytes.getInt(), bytes.getInt(), bytes.getInt(), bytes.getDouble(), bytes.get() == 0, bytes.get() == 0);
		}
	}
}

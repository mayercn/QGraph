package mthesis.concurrent_graph.apps.shortestpath;

import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mthesis.concurrent_graph.Configuration;
import mthesis.concurrent_graph.JobConfiguration;
import mthesis.concurrent_graph.vertex.AbstractVertex;
import mthesis.concurrent_graph.vertex.Edge;
import mthesis.concurrent_graph.vertex.VertexFactory;
import mthesis.concurrent_graph.worker.VertexWorkerInterface;
import mthesis.concurrent_graph.worker.WorkerQuery;
import mthesis.concurrent_graph.writable.DoubleWritable;

/**
 * Example vertex for shortest path
 *
 * @author Jonas Grunert
 *
 */
public class SPVertex extends AbstractVertex<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> {

	private static final Logger logger = LoggerFactory.getLogger(SPVertex.class);

	private static final double SPDistStepFactor = Configuration.getPropertyDoubleDefault("SPDistStepFactor", 10.0);
	private static final boolean SearchNextTagTestMode = Configuration.getPropertyBoolDefault("SearchNextTagTestMode", false);

	//	private final Map<Integer, Integer> visits = new HashMap<>(4);
	//	private static int firstVisits = 0;
	//	private static int reVisits = 0;
	//	private static int maxVisits = 0;

	public SPVertex(int id,
			VertexWorkerInterface<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> messageSender) {
		this(id, -1, messageSender);
	}

	public SPVertex(int id, int tag,
			VertexWorkerInterface<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> messageSender) {
		super(id, tag, messageSender);
	}

	public SPVertex(ByteBuffer bufferToRead,
			VertexWorkerInterface<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> worker,
			JobConfiguration<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> jobConfig) {
		super(bufferToRead, worker, jobConfig);
	}

	@Override
	protected void compute(int superstepNo, List<SPMessageWritable> messages,
			WorkerQuery<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> query) {
		// Revisit testing code
		//		int vis = MiscUtil.defaultInt(visits.get(query.QueryId));
		//		visits.put(query.QueryId, vis + 1);
		//		if (vis >= 1) {
		//			if (vis > 1) reVisits++;
		//			else firstVisits++;
		//			maxVisits = Math.max(maxVisits, vis);
		//			if (reVisits % 100000 == 1 || firstVisits % 100000 == 1)
		//				System.out.println(reVisits + "/" + firstVisits + " " + vis + " " + maxVisits);
		//		}

		//		if (messages != null) {
		//			for (SPMessageWritable msg : messages) {
		//				if (msg.SuperstepNo != superstepNo) {
		//					logger.warn(query.QueryId + ":" + superstepNo + "  Message for wrong superstep, not " + superstepNo
		//							+ " " + msg);
		//				}
		//				if (msg.DstVertex != ID) {
		//					logger.warn(query.QueryId + ":" + superstepNo + " Message for vertex dst vertex, not "
		//							+ msg.DstVertex + " " + msg);
		//				}
		//			}
		//		}

		if (superstepNo == 0) {
			if (ID != query.Query.From) {
				voteVertexHalt(query.QueryId);
				return;
			}
			else {
				logger.debug(query.QueryId + ":" + superstepNo + " start vertex compute start");
				SPVertexWritable mutableValue = new SPVertexWritable(-1, 0, false, false);
				setValue(mutableValue, query.QueryId);
				for (Edge<DoubleWritable> edge : getEdges()) {
					sendMessageToVertex(new SPMessageWritable(ID, edge.Value.Value, edge.TargetVertexId, superstepNo + 1),
							edge.TargetVertexId,
							query);
				}
				voteVertexHalt(query.QueryId);
				return;
			}
		}

		SPVertexWritable mutableValue = getValue(query.QueryId);

		// Reconstruct path over all vertices that have been visitied so far
		if (query.Query.ReconstructionPhaseActive && mutableValue != null) {
			if (!query.Query.InitializedReconstructionPhase) {
				// Start reconstruction at target vertex
				if (ID == query.Query.To) {
					if (mutableValue.Dist != Double.POSITIVE_INFINITY) {
						logger.debug(query.QueryId + ":" + superstepNo + " target vertex " + ID + " start reconstructing");
						mutableValue.OnShortestPath = true;
						sendMessageToVertex(
								new SPMessageWritable(ID, mutableValue.Dist, mutableValue.Pre, superstepNo + 1),
								mutableValue.Pre, query);
					}
					else {
						logger.error(query.QueryId + " Unable to reconstruct from target " + ID + ": No pre node. Probably no path found.");
					}
				}
			}
			else {
				// Send message to pre vertex until start vertex reached
				mutableValue.OnShortestPath = true;
				if (ID != query.Query.From) {
					if (mutableValue.Dist != Double.POSITIVE_INFINITY) {
						if (messages.size() == 1) {
							SPMessageWritable preMsg = messages.get(0);
							if (preMsg.DstVertex == ID) {
								if (preMsg.Dist >= mutableValue.Dist) {
									sendMessageToVertex(new SPMessageWritable(ID, mutableValue.Dist, mutableValue.Pre,
											superstepNo + 1), mutableValue.Pre, query);
								}
								else {
									logger.error(query.QueryId + ":" + superstepNo + " "
											+ "Reconstruct message distance smaller than own distance at node " + ID + ": "
											+ messages.get(0).Dist + "<" + mutableValue.Dist + " " + preMsg);
								}
							}
							else {
								logger.error(query.QueryId + ":" + superstepNo + " "
										+ "Reconstruct message for wrong vertex. Should be " + ID + " but is " + preMsg.DstVertex
										+ " from " + preMsg.Dist + " " + preMsg);
							}
						}
						else {
							logger.error(query.QueryId + ":" + superstepNo + " " + "Incorrect reconstruct message count at node " + ID
									+ ": " + messages);
						}
					}
					else {
						logger.error(query.QueryId + ":" + superstepNo + " " + "Unable to reconstruct from node " + ID + ": No pre node");
					}
				}
			}
			voteVertexHalt(query.QueryId);
			return;
		}

		if (mutableValue == null) {
			mutableValue = new SPVertexWritable(-1, Double.POSITIVE_INFINITY, false, false);
			setValue(mutableValue, query.QueryId);
		}

		double minDist = mutableValue.Dist;
		int minPre = mutableValue.Pre;
		if (messages != null) {
			for (SPMessageWritable msg : messages) {
				if (msg.Dist < minDist) {
					minDist = msg.Dist;
					minPre = msg.SrcVertex;
				}
			}
		}

		if (minDist > query.Query.MaxDist) {
			// Vertex is out of range
			setValue(null, query.QueryId);
			voteVertexHalt(query.QueryId);
			return;
		}

		if (minDist > superstepNo * SPDistStepFactor) {
			// Come back later
			if (minDist < mutableValue.Dist) {
				mutableValue.Dist = minDist;
				mutableValue.Pre = minPre;
				mutableValue.SendMsgsLater = true; // Send messages to neighbors later
			}
			return;
		}

		boolean sendMessages = mutableValue.SendMsgsLater;
		if (minDist < mutableValue.Dist) {
			mutableValue.Dist = minDist;
			mutableValue.Pre = minPre;
			sendMessages = true;
		}
		if (sendMessages) {
			for (Edge<DoubleWritable> edge : getEdges()) {
				sendMessageToVertex(new SPMessageWritable(ID, mutableValue.Dist + edge.Value.Value, edge.TargetVertexId, superstepNo + 1),
						edge.TargetVertexId, query);
			}
			mutableValue.SendMsgsLater = false;
		}

		voteVertexHalt(query.QueryId);

		if (isTarget(query.Query)) {
			// Target vertex found.  Now start limiting max dist to target dist.
			if (query.QueryLocal.MaxDist == Double.POSITIVE_INFINITY)
				logger.debug(query.QueryId + ":" + superstepNo + " target vertex " + ID + " found with dist " + minDist);
			if (SearchNextTagTestMode)
				query.QueryLocal.To = ID;
			query.QueryLocal.MaxDist = minDist;
		}
		//		else {
		//			// Halt vertex next superstep if not target and no messages
		//			voteVertexHalt(query.QueryId);
		//		}
	}

	private boolean isTarget(SPQuery query) {
		return ((!SearchNextTagTestMode && ID == query.To) || (SearchNextTagTestMode && Tag == query.Tag));
	}


	@Override
	public SPVertexWritable getOutputValue(int queryId) {
		SPVertexWritable value = super.getOutputValue(queryId);
		return value != null && value.OnShortestPath ? value : null;
	}


	public static class Factory extends VertexFactory<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> {

		@Override
		public AbstractVertex<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> newInstance(int id,
				VertexWorkerInterface<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> messageSender) {
			return new SPVertex(id, messageSender);
		}

		@Override
		public AbstractVertex<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> newInstance(
				ByteBuffer bufferToRead,
				VertexWorkerInterface<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> worker,
				JobConfiguration<SPVertexWritable, DoubleWritable, SPMessageWritable, SPQuery> jobConfig) {
			return new SPVertex(bufferToRead, worker, jobConfig);
		}
	}
}


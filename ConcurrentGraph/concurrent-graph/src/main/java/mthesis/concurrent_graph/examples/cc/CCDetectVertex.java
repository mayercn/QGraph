package mthesis.concurrent_graph.examples.cc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mthesis.concurrent_graph.BaseQueryGlobalValues;
import mthesis.concurrent_graph.vertex.AbstractVertex;
import mthesis.concurrent_graph.vertex.Edge;
import mthesis.concurrent_graph.vertex.VertexFactory;
import mthesis.concurrent_graph.worker.VertexWorkerInterface;
import mthesis.concurrent_graph.writable.IntWritable;
import mthesis.concurrent_graph.writable.NullWritable;

/**
 * Example vertex to detect connected components in a graph
 *
 * @author Jonas Grunert
 *
 */
public class CCDetectVertex extends AbstractVertex<IntWritable, NullWritable, CCMessageWritable, BaseQueryGlobalValues> {

	// TODO Have this in state?
	private final Set<Integer> allNeighbors = new HashSet<>();

	public CCDetectVertex(int id,
			VertexWorkerInterface<IntWritable, NullWritable, CCMessageWritable, BaseQueryGlobalValues> messageSender) {
		super(id, messageSender);
	}

	@Override
	protected void compute(int superstepNo, List<CCMessageWritable> messages, BaseQueryGlobalValues query) {
		if (superstepNo == 0) {
			IntWritable value = new IntWritable(ID);
			setValue(value, query.QueryId);

			final List<Edge<NullWritable>> edges = getEdges();
			for (final Edge<NullWritable> edge : edges) {
				allNeighbors.add(edge.TargetVertexId);
			}

			sendMessageToAllOutgoingEdges(new CCMessageWritable(ID, ID));
			voteVertexHalt();
			return;
		}

		IntWritable mutableValue = getValue(query.QueryId);
		int min = mutableValue.Value;
		final int knownNeighborsBefore = allNeighbors.size();
		for (final CCMessageWritable msg : messages) {
			allNeighbors.add(msg.SrcVertex);
			final int msgValue = msg.Value;
			//System.out.println("Get " + msgValue + " on " + ID + " from " + msg.SrcVertex);
			min = Math.min(min, msgValue);
		}

		if (min < mutableValue.Value) {
			mutableValue.Value = min;
			sendMessageToVertices(new CCMessageWritable(ID, mutableValue.Value), allNeighbors);
		}
		else {
			if (knownNeighborsBefore < allNeighbors.size())
				sendMessageToVertices(new CCMessageWritable(ID, mutableValue.Value), allNeighbors);
			//System.out.println("Vote halt on " + ID + " with " + value);
			voteVertexHalt();
		}
	}


	public static class Factory extends VertexFactory<IntWritable, NullWritable, CCMessageWritable, BaseQueryGlobalValues> {

		@Override
		public AbstractVertex<IntWritable, NullWritable, CCMessageWritable, BaseQueryGlobalValues> newInstance(int id,
				VertexWorkerInterface<IntWritable, NullWritable, CCMessageWritable, BaseQueryGlobalValues> messageSender) {
			return new CCDetectVertex(id, messageSender);
		}
	}
}

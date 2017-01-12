package mthesis.concurrent_graph.examples.cc;

import java.util.List;

import mthesis.concurrent_graph.BaseQueryGlobalValues;
import mthesis.concurrent_graph.vertex.AbstractVertex;
import mthesis.concurrent_graph.vertex.VertexFactory;
import mthesis.concurrent_graph.worker.VertexWorkerInterface;
import mthesis.concurrent_graph.writable.IntWritable;
import mthesis.concurrent_graph.writable.NullWritable;

/**
 * Example vertex to detect strongly connected components in a graph
 * 
 * @author Jonas Grunert
 *
 */
public class SCCDetectVertex extends AbstractVertex<IntWritable, NullWritable, IntWritable, BaseQueryGlobalValues> {

	public SCCDetectVertex(int id, VertexWorkerInterface<IntWritable, BaseQueryGlobalValues> messageSender) {
		super(id, messageSender);
		setValue(new IntWritable(id));
	}

	@Override
	protected void compute(List<IntWritable> messages, BaseQueryGlobalValues query) {
		if (superstepNo == 0) {
			sendMessageToAllOutgoingEdges(getValue());
			voteVertexInactive();
			return;
		}

		int min = getValue().Value;
		for (final IntWritable msg : messages) {
			final int msgValue = msg.Value;
			//System.out.println("Get " + msgValue + " on " + ID + " from " + msg.SrcVertex);
			min = Math.min(min, msgValue);
		}

		if (min < getValue().Value) {
			getValue().Value = min;
			sendMessageToAllOutgoingEdges(getValue());
		}
		else {
			//System.out.println("Vote halt on " + ID + " with " + value);
			voteVertexInactive();
		}
	}


	public static class Factory extends VertexFactory<IntWritable, NullWritable, IntWritable, BaseQueryGlobalValues> {

		@Override
		public AbstractVertex<IntWritable, NullWritable, IntWritable, BaseQueryGlobalValues> newInstance(int id,
				VertexWorkerInterface<IntWritable, BaseQueryGlobalValues> messageSender) {
			return new SCCDetectVertex(id, messageSender);
		}
	}
}

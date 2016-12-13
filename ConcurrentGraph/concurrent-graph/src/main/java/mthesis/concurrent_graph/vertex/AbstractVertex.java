package mthesis.concurrent_graph.vertex;

import java.util.Collection;
import java.util.List;

import mthesis.concurrent_graph.worker.WorkerMachine;
import mthesis.concurrent_graph.writable.BaseWritable;


public abstract class AbstractVertex<V extends BaseWritable, M extends BaseWritable> {
	public final int id;
	protected final List<Integer> outgoingNeighbors;
	protected int superstepNo = 0;
	private final WorkerMachine<V, M> workerManager;
	private boolean active = true;


	public AbstractVertex(List<Integer> neighbors, int id, WorkerMachine<V, M> workerManager) {
		super();
		this.id = id;
		this.outgoingNeighbors = neighbors;
		this.workerManager = workerManager;
	}


	public void superstep(List<VertexMessage<M>> messages, int superstep) {
		if (!messages.isEmpty())
			active = true;
		if(active) {
			this.superstepNo = superstep;
			compute(messages);
		}
	}

	protected abstract void compute(List<VertexMessage<M>> messages);


	protected void sendMessageToAllOutgoing(M message) {
		for (final Integer nb : outgoingNeighbors) {
			workerManager.sendVertexMessage(id, nb, message);
		}
	}

	protected void sendMessageToVertex(M message, int sendTo) {
		workerManager.sendVertexMessage(id, sendTo, message);
	}

	protected void sendMessageToVertices(M message, Collection<Integer> sendTo) {
		for (final Integer st : sendTo) {
			workerManager.sendVertexMessage(id, st, message);
		}
	}


	protected void voteHalt() {
		active = false;
	}

	public boolean isActive() {
		return active;
	}

	public abstract String getOutput();
}
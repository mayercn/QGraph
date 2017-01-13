package mthesis.concurrent_graph.worker;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import mthesis.concurrent_graph.BaseQueryGlobalValues;
import mthesis.concurrent_graph.BaseQueryGlobalValues.BaseQueryGlobalValuesFactory;
import mthesis.concurrent_graph.writable.BaseWritable;

public class WorkerQuery<M extends BaseWritable, Q extends BaseQueryGlobalValues> {

	public Q Query;
	public Q QueryLocal;
	//private final BaseQueryGlobalValuesFactory<Q> globalValueFactory;

	public int SuperstepNo = 0;
	public Set<Integer> ChannelBarrierWaitSet = new HashSet<>();
	public Int2ObjectMap<List<M>> InVertexMessages = new Int2ObjectOpenHashMap<>();
	public boolean Master = false;
	public boolean Finished = false;


	public WorkerQuery(Q globalQueryValues, BaseQueryGlobalValuesFactory<Q> globalValueFactory) {
		//this.globalValueFactory = globalValueFactory;
		Query = globalQueryValues;
		QueryLocal = globalValueFactory.createClone(globalQueryValues);
	}

	public void nextSuperstep() {
		if (!ChannelBarrierWaitSet.isEmpty()) throw new RuntimeException("ChannelBarrierWaitSet not empty when nextSuperstep");
		SuperstepNo++;
	}

	public void finish() {
		Finished = true;
		ChannelBarrierWaitSet.clear();
	}
}

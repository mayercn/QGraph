package mthesis.concurrent_graph.worker;

import java.util.List;

import mthesis.concurrent_graph.BaseQuery;
import mthesis.concurrent_graph.vertex.AbstractVertex;
import mthesis.concurrent_graph.writable.BaseWritable;

public abstract class WorkerOutputWriter<V extends BaseWritable, E extends BaseWritable, M extends BaseWritable, G extends BaseQuery> {

	public abstract void getVertices(List<AbstractVertex<V, E, M, G>> vertices, String output);
}

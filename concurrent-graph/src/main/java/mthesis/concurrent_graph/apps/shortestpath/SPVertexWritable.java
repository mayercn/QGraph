package mthesis.concurrent_graph.apps.shortestpath;

import java.nio.ByteBuffer;

import mthesis.concurrent_graph.writable.BaseWritable;

public class SPVertexWritable extends BaseWritable {

	public int Pre;
	public double Dist;
	// Indicates if a superstep was skipped because of distance limit and messages should be sent later.
	public boolean SendMsgsLater;
	// Indicates if this vertex is on the shortest path
	public boolean OnShortestPath;


	public SPVertexWritable() {
		super();
	}

	public SPVertexWritable(int pre, double dist, boolean sendMsgsLater, boolean onShortestPath) {
		super();
		Pre = pre;
		Dist = dist;
		SendMsgsLater = sendMsgsLater;
		OnShortestPath = onShortestPath;
	}

	@Override
	public void readFromBuffer(ByteBuffer buffer) {
		Pre = buffer.getInt();
		Dist = buffer.getDouble();
		SendMsgsLater = (buffer.get() == 0);
		OnShortestPath = (buffer.get() == 0);
	}


	@Override
	public void writeToBuffer(ByteBuffer buffer) {
		buffer.putInt(Pre);
		buffer.putDouble(Dist);
		buffer.put(SendMsgsLater ? (byte) 0 : (byte) 1);
		buffer.put(OnShortestPath ? (byte) 0 : (byte) 1);
	}

	@Override
	public String getString() {
		return Pre + ":" + Dist + ":" + SendMsgsLater + ":" + OnShortestPath;
	}

	@Override
	public int getBytesLength() {
		return 4 + 8 + 2;
	}


	public static class Factory extends BaseWritable.BaseWritableFactory<SPVertexWritable> {

		@Override
		public SPVertexWritable createDefault() {
			return new SPVertexWritable();
		}

		@Override
		public SPVertexWritable createFromString(String str) {
			final String[] sSplit = str.split(":");
			return new SPVertexWritable(Integer.parseInt(sSplit[0]), Double.parseDouble(sSplit[1]), Boolean.parseBoolean(sSplit[2]),
					Boolean.parseBoolean(sSplit[3]));
		}

		@Override
		public SPVertexWritable createClone(SPVertexWritable toClone) {
			return new SPVertexWritable(toClone.Pre, toClone.Dist, toClone.SendMsgsLater, toClone.OnShortestPath);
		}
	}
}

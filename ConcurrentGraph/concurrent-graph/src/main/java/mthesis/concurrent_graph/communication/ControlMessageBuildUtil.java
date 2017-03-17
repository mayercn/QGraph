package mthesis.concurrent_graph.communication;

import java.util.List;
import java.util.Map;

import com.google.protobuf.ByteString;

import mthesis.concurrent_graph.BaseQueryGlobalValues;
import mthesis.concurrent_graph.communication.Messages.ControlMessage;
import mthesis.concurrent_graph.communication.Messages.ControlMessage.AssignPartitionsMessage;
import mthesis.concurrent_graph.communication.Messages.ControlMessage.QueryIntersectionsMessage;
import mthesis.concurrent_graph.communication.Messages.ControlMessage.ReceiveQueryVerticesMessage;
import mthesis.concurrent_graph.communication.Messages.ControlMessage.SendQueryVerticesMessage;
import mthesis.concurrent_graph.communication.Messages.ControlMessage.StartBarrierMessage;
import mthesis.concurrent_graph.communication.Messages.ControlMessage.WorkerInitializedMessage;
import mthesis.concurrent_graph.communication.Messages.ControlMessageType;
import mthesis.concurrent_graph.communication.Messages.MessageEnvelope;


/**
 * Utilities for ControlMessage building
 *
 * @author Joans Grunert
 *
 */
public class ControlMessageBuildUtil {

	public static MessageEnvelope Build_Master_WorkerInitialize(int srcMachineId, List<String> partitions) {
		final AssignPartitionsMessage assignPartitionsMsg = AssignPartitionsMessage.newBuilder().addAllPartitionFiles(partitions).build();
		return MessageEnvelope.newBuilder()
				.setControlMessage(ControlMessage.newBuilder()
						.setType(ControlMessageType.Master_Worker_Initialize)
						.setSrcMachine(srcMachineId)
						.setAssignPartitions(assignPartitionsMsg).build())
				.build();
	}

	public static MessageEnvelope Build_Master_QueryStart(int srcMachineId, BaseQueryGlobalValues query) {
		return MessageEnvelope.newBuilder()
				.setControlMessage(ControlMessage.newBuilder()
						.setType(ControlMessageType.Master_Query_Start)
						.setSuperstepNo(0)
						.setQueryValues(ByteString.copyFrom(query.getBytes()))
						.setSrcMachine(srcMachineId).build())
				.build();
	}


	// Normal next superstep message, no vertex transfer
	public static MessageEnvelope Build_Master_QueryNextSuperstep_NoVertMove(int superstepNo, int srcMachineId,
			BaseQueryGlobalValues query) {
		return MessageEnvelope.newBuilder()
				.setControlMessage(ControlMessage.newBuilder()
						.setType(ControlMessageType.Master_Query_Next_Superstep)
						.setQueryValues(ByteString.copyFrom(query.getBytes()))
						.setSuperstepNo(superstepNo)
						.setSrcMachine(srcMachineId).setQueryValues(ByteString.copyFrom(query.getBytes())).build())
				.build();
	}

	// Transfer Vertices: Send to other worker
	public static MessageEnvelope Build_Master_QueryNextSuperstep_VertSend(int superstepNo, int srcMachineId, BaseQueryGlobalValues query,
			int sendTo) {
		SendQueryVerticesMessage.Builder sendVertMsg = SendQueryVerticesMessage.newBuilder().setSendToMachine(sendTo);
		return MessageEnvelope.newBuilder()
				.setControlMessage(ControlMessage.newBuilder()
						.setType(ControlMessageType.Master_Query_Next_Superstep)
						.setQueryValues(ByteString.copyFrom(query.getBytes()))
						.setSuperstepNo(superstepNo)
						.setSrcMachine(srcMachineId).setQueryValues(ByteString.copyFrom(query.getBytes()))
						.setSendQueryVertices(sendVertMsg)
						.build())
				.build();
	}

	// Transfer vertices: Receive vertices from one ore more workers before next superstep
	public static MessageEnvelope Build_Master_QueryNextSuperstep_VertReceive(int superstepNo, int srcMachineId,
			BaseQueryGlobalValues query,
			List<Integer> receiveFrom) {
		ReceiveQueryVerticesMessage.Builder recvVertMsg = ReceiveQueryVerticesMessage.newBuilder().addAllRecvFromMachine(receiveFrom);
		return MessageEnvelope.newBuilder()
				.setControlMessage(ControlMessage.newBuilder()
						.setType(ControlMessageType.Master_Query_Next_Superstep)
						.setQueryValues(ByteString.copyFrom(query.getBytes()))
						.setSuperstepNo(superstepNo)
						.setSrcMachine(srcMachineId)
						.setQueryValues(ByteString.copyFrom(query.getBytes()))
						.setReceiveQueryVertices(recvVertMsg)
						.build())
				.build();
	}

	public static MessageEnvelope Build_Master_QueryFinish(int srcMachineId, BaseQueryGlobalValues query) {
		return MessageEnvelope.newBuilder().setControlMessage(ControlMessage.newBuilder()
				.setType(ControlMessageType.Master_Query_Finished)
				.setQueryValues(ByteString.copyFrom(query.getBytes()))
				.setSrcMachine(srcMachineId).build()).build();
	}

	public static MessageEnvelope Build_Master_Shutdown(int srcMachineId) {
		return MessageEnvelope.newBuilder().setControlMessage(ControlMessage.newBuilder()
				.setType(ControlMessageType.Master_Shutdown)
				.setSrcMachine(srcMachineId).build()).build();
	}

	public static MessageEnvelope Build_Master_StartBarrier_VertexMove(int srcMachineId,
			List<Messages.ControlMessage.StartBarrierMessage.SendQueryVerticesMessage> sendVerts,
			List<Messages.ControlMessage.StartBarrierMessage.ReceiveQueryVerticesMessage> recvVerts) {
		return MessageEnvelope.newBuilder().setControlMessage(ControlMessage.newBuilder()
				.setType(ControlMessageType.Master_Start_Barrier)
				.setStartBarrier(StartBarrierMessage.newBuilder().addAllSendQueryVertices(sendVerts).addAllReceiveQueryVertices(recvVerts))
				.setSrcMachine(srcMachineId).build()).build();
	}

	public static MessageEnvelope Build_Worker_QuerySuperstepBarrier(int superstepNo, int srcMachineId, BaseQueryGlobalValues query) {
		return MessageEnvelope.newBuilder().setControlMessage(ControlMessage.newBuilder()
				.setType(ControlMessageType.Worker_Query_Superstep_Barrier)
				.setQueryValues(ByteString.copyFrom(query.getBytes()))
				.setSuperstepNo(superstepNo).setSrcMachine(srcMachineId).build())
				.build();
	}

	public static MessageEnvelope Build_Worker_Initialized(int srcMachineId, int vertexCount) {
		final WorkerInitializedMessage workerInit = WorkerInitializedMessage.newBuilder()
				.setVertexCount(vertexCount).build();
		return MessageEnvelope.newBuilder()
				.setControlMessage(ControlMessage.newBuilder().setType(ControlMessageType.Worker_Initialized)
						.setWorkerInitialized(workerInit)
						.setSrcMachine(srcMachineId).build())
				.build();
	}

	public static MessageEnvelope Build_Worker_QuerySuperstepFinished(int superstepNo, int srcMachineId, //SuperstepStats stats,
			BaseQueryGlobalValues localQuery, Map<Integer, Integer> queryIntersects) {
		final QueryIntersectionsMessage intersectMsg = QueryIntersectionsMessage.newBuilder().putAllIntersections(queryIntersects).build();
		return MessageEnvelope.newBuilder()
				.setControlMessage(ControlMessage.newBuilder().setType(ControlMessageType.Worker_Query_Superstep_Finished)
						.setQueryValues(ByteString.copyFrom(localQuery.getBytes()))
						.setSuperstepNo(superstepNo)
						.setSrcMachine(srcMachineId)
						.setQueryIntersections(intersectMsg)
						.build())
				.build();
	}

	public static MessageEnvelope Build_Worker_QueryFinished(int superstepNo, int srcMachineId, BaseQueryGlobalValues localQuery) {
		return MessageEnvelope.newBuilder()
				.setControlMessage(ControlMessage.newBuilder()
						.setQueryValues(ByteString.copyFrom(localQuery.getBytes()))
						.setType(ControlMessageType.Worker_Query_Finished)
						.setSuperstepNo(superstepNo).setSrcMachine(srcMachineId).build())
				.build();
	}
}

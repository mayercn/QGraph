package mthesis.concurrent_graph.communication;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import mthesis.concurrent_graph.communication.Messages.ControlMessage;
import mthesis.concurrent_graph.communication.Messages.ControlMessageType;
import mthesis.concurrent_graph.communication.Messages.MessageEnvelope;


// TODO Everything should be more robust, error handling etc.
public class MessageHandler extends SimpleChannelInboundHandler<MessageEnvelope> {
	private final Logger logger;
	private final int ownId;
	private final MessageSenderAndReceiver messageListner;
	private final ConcurrentHashMap<Integer, Channel> activeChannels;

	private enum ChannelState { Inactive, Handshake, Active }
	private ChannelState channelState = ChannelState.Inactive;
	private int connectedMachine;


	public MessageHandler(ConcurrentHashMap<Integer, Channel> activeChannels, int ownId,
			MessageSenderAndReceiver messageListner) {
		super();
		this.ownId = ownId;
		this.logger = LoggerFactory.getLogger(MessageHandler.class.getCanonicalName() + "[" + ownId + "]");
		this.activeChannels = activeChannels;
		this.messageListner = messageListner;
	}


	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		// ctx.writeAndFlush(firstMessage);
		logger.debug("Channel active: " + ctx.channel().id());
		if (channelState == ChannelState.Inactive) {
			channelState = ChannelState.Handshake;
			ctx.writeAndFlush(
					MessageEnvelope.newBuilder().setControlMessage(
							ControlMessage.newBuilder().setType(ControlMessageType.Channel_Handshake).setSrcMachine(ownId).build()).build());
			// TODO AndFlush
		} else {
			logger.warn("Channel not inactive ignoring active channel " + ctx.channel().id());
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		//ctx.fireChannelInactive();
		channelState = ChannelState.Inactive;
		logger.debug("Channel inactive: " + ctx.channel().id());
	}


	@Override
	protected void channelRead0(ChannelHandlerContext ctx, MessageEnvelope msg) throws Exception {
		if(msg.hasVertexMessage()) {
			messageListner.onIncomingVertexMessage(msg.getVertexMessage());
			//System.out.println(msg);
		}
		else if(msg.hasControlMessage()) {
			switch (channelState) {
				case Handshake:
					connectedMachine = msg.getControlMessage().getSrcMachine();
					activeChannels.put(connectedMachine, ctx.channel());
					channelState = ChannelState.Active;
					logger.debug("Channel handshake finished. Connected " + connectedMachine + " via " + ctx.channel().id());
					break;
				case Active:
					messageListner.onIncomingControlMessage(msg.getControlMessage());
					//System.out.println("1 it is " + inBuf.isReadable());

				default:
					break;
			}
		}
		else {
			logger.warn("Received message envelope without valid inner message: " + msg);
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		// Close the connection when an exception is raised.
		logger.error("exceptionCaught", cause);
		ctx.close();
	}
}
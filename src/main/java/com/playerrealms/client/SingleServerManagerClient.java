package com.playerrealms.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.playerrealms.common.PacketDecoder;
import com.playerrealms.common.PacketEncoder;
import com.playerrealms.common.PacketUtil;
import com.playerrealms.common.ResponseCodes;
import com.playerrealms.common.ServerInformation;
import com.playerrealms.common.ServerStatus;
import com.playerrealms.common.file.FileTransferClient;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

@Sharable
public class SingleServerManagerClient extends ChannelInboundHandlerAdapter implements ServerManagerClient {

	public static final byte[] PASSWORD = new byte[] {
			(byte)213,(byte)97,(byte)132,(byte)208,(byte)11,(byte)112,(byte)144,(byte)208,(byte)246,(byte)93,(byte)99,(byte)250,(byte)161,(byte)2,(byte)43,(byte)171,
			(byte)51,(byte)180,(byte)169,(byte)36,(byte)204,(byte)58,(byte)237,(byte)175,(byte)216,(byte)176,(byte)131,(byte)126,(byte)108,(byte)164,(byte)27,(byte)140,
			(byte)15,(byte)68,(byte)109,(byte)236,(byte)170,(byte)84,(byte)251,(byte)229,(byte)25,(byte)8,(byte)49,(byte)138,(byte)81,(byte)228,(byte)243,(byte)234,
			(byte)47,(byte)52,(byte)252,(byte)235,(byte)239,(byte)213,(byte)52,(byte)130,(byte)166,(byte)186,(byte)199,(byte)254,(byte)168,(byte)106,(byte)245,(byte)176,
			(byte)12,(byte)210,(byte)134,(byte)199,(byte)63,(byte)173,(byte)134,(byte)37,(byte)229,(byte)196,(byte)48,(byte)2,(byte)219,(byte)111,(byte)54,(byte)70,
			(byte)40,(byte)68,(byte)244,(byte)150,(byte)118,(byte)253,(byte)215,(byte)177,(byte)66,(byte)74,(byte)39,(byte)78,(byte)68,(byte)63,(byte)239,(byte)188,
			(byte)89,(byte)224,(byte)230,(byte)16
	};
	
	private Channel channel;
	
	private EventLoopGroup worker;
	
	private Map<String, ServerInformation> serverData;
	
	private List<ServerUpdateListener> listeners;
	
	private Random random;
	
	private boolean gotList;
	
	private String id;
	
	private long maxMemory;
	private long availableDiskSpace;
	private int maxServers;
	
	private String host;
	
	private int ftsPort;
	
	private boolean shutdown;
	
	public SingleServerManagerClient(String id, EventLoopGroup worker) {
		this(id);
		this.worker = worker;
		shutdown = false;
	}
	
	public SingleServerManagerClient(String id) {
		serverData = Collections.synchronizedMap(new TreeMap<String, ServerInformation>(String.CASE_INSENSITIVE_ORDER));
		
		listeners = new ArrayList<ServerUpdateListener>();
		random = new Random();
		gotList = false;
		this.id = id;
	}
	
	@Override
	public long getAvailableDiskSpace() {
		return availableDiskSpace;
	}
	
	@Override
	public boolean isConnected() {
		return channel != null;
	}
	
	@Override
	public String toString() {
		return "SingleClient {"+id+" ftsPort="+ftsPort+" "+getServers(server -> server.getStatus() != ServerStatus.OFFLINE).size()+" / "+maxServers+"}";
	}
	
	@Override
	public int getFileTransferPort() {
		return ftsPort;
	}
	
	@Override
	public String getHost() {
		return host;
	}
	
	public long getMaxMemory() {
		return maxMemory;
	}
	
	public int getMaxServers(){
		return maxServers;
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		channel = null;
		if(!shutdown) {
			listeners.forEach(l -> l.onDisconnectFromServerManager());
			connect(cIp, cPort);			
		}

	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
	}
	
	public void addListener(ServerUpdateListener listener) {
		if(listener == null){
			throw new IllegalArgumentException("Listener cannot be null");
		}
		listeners.add(listener);
	}
	
	private String cIp;
	private int cPort;
	
	public void connect(String ip, int port){
		cIp = ip;
		cPort = port;
		if(channel != null){
			throw new IllegalStateException("Already connected");
		}
		host = ip;
		Bootstrap bs = new Bootstrap();
		
		if(worker == null)
			worker = new NioEventLoopGroup();
		
		try{
			bs.channel(NioSocketChannel.class)
			.group(worker)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.handler(new ChannelInitializer<SocketChannel>() {
				@Override
				protected void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast(new PacketDecoder(), new PacketEncoder(), SingleServerManagerClient.this);
				}
			});
			
			channel = bs.connect(ip, port).sync().channel();
			ByteBuf password = ByteBufAllocator.DEFAULT.directBuffer(PASSWORD.length, PASSWORD.length);
			password.writeBytes(PASSWORD);
			sendPacket(password);
			if(id != null)
				sendIdentification(id);
			while(!gotList){
				Thread.sleep(1);
			}
		}catch(Exception e){
			System.err.println("Failed to connect to ServerManager "+e.getClass().getName()+": "+e.getMessage());
		}
		
	}
	
	@Override
	public ServerInformation getServerByCode(String code) {
		for(ServerInformation info : getServers()){
			if(info.isThirdParty()){
				if(info.getThirdPartyCode().equals(code)){
					return info;
				}
			}
		}
		return null;
	}
	
	public long sendUpdateServerPlayers(String serverName, int players){
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		
		buf.writeByte(5);
		buf.writeLong(uid);
		PacketUtil.writeString(serverName, buf);
		buf.writeInt(players);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		
		return uid;
	}
	

	@Override
	public long setPlayers(String server, int players, int max) {
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		
		buf.writeByte(5);
		buf.writeLong(uid);
		PacketUtil.writeString(server, buf);
		buf.writeInt(players);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		
		return uid;
	}


	public long sendDeleteServer(String name) {
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		
		buf.writeByte(6);
		buf.writeLong(uid);
		PacketUtil.writeString(name, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		
		return uid;
	}
	
	public long sendStopServer(String name, boolean force){
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		
		buf.writeByte(1);
		buf.writeLong(uid);
		PacketUtil.writeString(name, buf);
		buf.writeBoolean(force);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
	}

	@Override
	public long ftpServerInfo(int port, String username, String password) {
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(14);
		buf.writeLong(uid);
		buf.writeChar(port);
		PacketUtil.writeString(username, buf);
		PacketUtil.writeString(password, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
	}
	
	public long sendCreateServer(String name, String type){
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(2);
		buf.writeLong(uid);
		PacketUtil.writeString(name, buf);
		PacketUtil.writeString(type, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
	}
	
	public long sendStartServer(String name, boolean priority){
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(3);
		buf.writeLong(uid);
		PacketUtil.writeString(name, buf);
		buf.writeBoolean(priority);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
	}
	
	public long sendCommandToServer(String name, String command){
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(4);
		buf.writeLong(uid);
		PacketUtil.writeString(name, buf);
		PacketUtil.writeString(command, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
	}
	
	public long createConsoleReadContract(String name){
		
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(7);
		buf.writeLong(uid);
		buf.writeByte(0);
		PacketUtil.writeString(name, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
		
	}
	
	public long destroyConsoleContract(long id){
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(7);
		buf.writeLong(id);
		buf.writeByte(1);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return id;
	}
	
	public long setMetadata(String server, String key, String value){
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(8);
		buf.writeLong(uid);
		PacketUtil.writeString(server, buf);
		PacketUtil.writeString(key, buf);
		PacketUtil.writeString(value, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
	}
	
	public long saveMetadata(String server){
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(9);
		buf.writeLong(uid);
		PacketUtil.writeString(server, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
	}
	
	public void shutdownServerManager(){
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(9);
		buf.writeByte(0);
		buf.writeLong(0);
		
		sendPacket(buf);
		
	}
	
	public long sendRestartServer(String name) {
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(10);
		buf.writeLong(uid);
		PacketUtil.writeString(name, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
	}


	public long renameRealm(String oldName, String newName) {
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(11);
		buf.writeLong(uid);
		PacketUtil.writeString(oldName, buf);
		PacketUtil.writeString(newName, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
	}
	
	public long refreshServer(String name){
		long uid = random.nextLong();
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		buf.writeByte(12);
		buf.writeLong(uid);
		PacketUtil.writeString(name, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
		
		return uid;
	}
	
	public void sendIdentification(String id){
		ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
		
		buf.writeByte(13);
		buf.writeLong(random.nextLong());
		PacketUtil.writeString(id, buf);
		
		buf = buf.capacity(buf.writerIndex());
		
		sendPacket(buf);
	}
	
	public List<ServerInformation> getServers(){
		synchronized (serverData) {
			return new LinkedList<ServerInformation>(serverData.values());
		}
	}
	
	@Override
	public List<ServerInformation> getServers(Predicate<ServerInformation> filter) {
		return getServers().stream().filter(filter).collect(Collectors.toList());
	}
	
	public ServerInformation getServerByName(String name){
		synchronized (serverData) {
			return serverData.get(name);
		}
	}
	
	public void shutdown(){
		shutdown = true;
		channel.close();
		worker.shutdownGracefully();
	}
	
	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public void moveServer(String server, ServerManagerClient target) {
		FileTransferClient ftc = new FileTransferClient(target.getHost(), target.getFileTransferPort());
		try {
			ftc.requestDownloadServer(server, host, ftsPort);
			sendDeleteServer(server);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void updateServerData(ServerInformation info){

		ServerInformation old = serverData.put(info.getName(), info);
		
		if(old != null){
			if(old.getStatus() != info.getStatus()){
				listeners.forEach(l -> l.onServerStatusChange(info, old.getStatus()));
			}
		}
		
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		ByteBuf buf = (ByteBuf) msg;
		
		byte code = buf.readByte();
		
		synchronized (serverData) {
			if(code == 0){
				short servers = buf.readShort();
				
				List<ServerInformation> updated = new ArrayList<ServerInformation>(servers);
				
				for(int i = 0; i < servers;i++){
					ServerInformation decoded = ServerInformation.decode(buf);
					
					updateServerData(decoded);
					updated.add(decoded);
				}
				listeners.forEach(l -> l.onServerListReceive(updated));
				gotList = true;
			}else if(code == 1){
				ServerInformation decoded = ServerInformation.decode(buf);
				
				updateServerData(decoded);
				listeners.forEach(l -> l.onServerReceive(decoded));
			}else if(code == 2){
				String name = PacketUtil.readString(buf);
				
				serverData.remove(name);
				listeners.forEach(l -> l.onServerDeleted(name));
			}else if(code == 3){
				maxMemory = buf.readLong();
				maxServers = buf.readInt();
				ftsPort = buf.readUnsignedShort();
				availableDiskSpace = buf.readLong();
			}else if(code == -1){
				
				long responseId = buf.readLong();
				byte responseType = buf.readByte();
				
				listeners.forEach(l -> l.onReply(responseId, ResponseCodes.getById(responseType)));
				
			}else if(code == -2){
				long rid = buf.readLong();
				
				String line = PacketUtil.readString(buf);
				
				listeners.forEach(l -> l.onConsoleRead(rid, line));
			}
		}

		buf.release();
		
	}
	
	
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		
	}

	protected void sendPacket(ByteBuf buf) {
		synchronized (channel) {
			channel.writeAndFlush(buf);
		}
	}

	@Override
	public long sendDeleteServerData(String server) {
		throw new NotImplementedException();
	}

	
}

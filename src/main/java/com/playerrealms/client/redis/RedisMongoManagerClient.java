package com.playerrealms.client.redis;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.playerrealms.client.NoAvailableServerException;
import com.playerrealms.client.ServerManagerClient;
import com.playerrealms.client.ServerUpdateListener;
import com.playerrealms.common.RedisConstants;
import com.playerrealms.common.ResponseCodes;
import com.playerrealms.common.ServerInformation;
import com.playerrealms.common.ServerStatus;
import org.bson.Document;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RedisMongoManagerClient implements ServerManagerClient, JedisListener {

	private RedisInterface redis;
	
	private MongoDatabase database;
	
	private Map<String, ServerInformation> serverInfo;
	
	private Random random;
	
	private List<ServerUpdateListener> listeners;
	
	public RedisMongoManagerClient(RedisInterface redis, MongoDatabase database) {
		this.redis = redis;
		this.database = database;
		serverInfo = Collections.synchronizedMap(new TreeMap<String, ServerInformation>(String.CASE_INSENSITIVE_ORDER));
		random = new Random();
		listeners = new ArrayList<>();
	}
	
	@Override
	public String getHost() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getFileTransferPort() {
		throw new UnsupportedOperationException();
	}

	@Override
	public long getAvailableDiskSpace() {
		return Long.MAX_VALUE;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public boolean isConnected() {
		return true;
	}

	@Override
	public void connect(String ip, int port) {
		redis.subscribe(this);
		
		grabAllServers();
	
	}
	
	private Document grabServerDocument(String name) {
		MongoCollection<Document> servers = getServerCollection();
		
		Document doc = servers.find(Filters.eq("server_name_lower", name.toLowerCase())).first();
		
		return doc;
	}
	
	private void grabServer(String name) {
		
		Document doc = grabServerDocument(name);
		
		if(doc == null) {
			return;
		}
		
		ServerStatus old = null;
		
		if(serverInfo.containsKey(name)) {
			old = getServerByName(name).getStatus();
		}
		
		ServerInformation info = generateInfo(doc);
		
		serverInfo.put(info.getName(), info);
		
		listeners.forEach(l -> l.onServerReceive(info));
		if(old != info.getStatus()) {
			if(old == null)
				old = ServerStatus.OFFLINE;
			final ServerStatus f_old = old;
			listeners.forEach(l -> l.onServerStatusChange(info, f_old));
		}
	}
	
	private void grabAllServers() {
		MongoCollection<Document> servers = getServerCollection();
		serverInfo.clear();
		for(Document doc : servers.find(Filters.exists("server_name"))) {
			
			ServerInformation info = generateInfo(doc);
			
			serverInfo.put(info.getName(), info);
			
		}
		listeners.forEach(l -> l.onServerListReceive(getServers()));
	}

	private long createRequestId() {
		long id = random.nextLong();
		if(id == 0L) {
			return 1L;
		}
		return id;
	}

	@Override
	public void shutdown() {
		redis.shutdown();
	}
	
	public MongoCollection<Document> getServerCollection(){
		MongoCollection<Document> col = database.getCollection("servers");
		
		return col;
	}
	
	public MongoCollection<Document> getManagerCollection(){
		MongoCollection<Document> col = database.getCollection("managers");
		
		return col;
	}
	
	public boolean isManagerExisting(String ip) {
		MongoCollection<Document> managers = getManagerCollection();
		
		return managers.find(Filters.eq("ip", ip)).first() != null;
	}
	
	public String getBestManager() {
		MongoCollection<Document> managers = getManagerCollection();

		long bestFree = 0;
		String bestIp = "";

		for(Document doc : managers.find()) {

			String ip = doc.getString("ip");
			long free = doc.getLong("free");
			long time = doc.getLong("time");
			boolean valid = doc.getBoolean("accept", true);
			String sort = doc.getString("type");

			if ("ultra".equals(sort)){
				continue;
			}

			if(!valid) {
				continue;
			}

			if(System.currentTimeMillis() - time > 15000) {
				continue;//It must have turned off because it's not updating its time!
			}

			if(bestIp.isEmpty()) {
				bestIp = ip;
				bestFree = free;
			}else if(bestFree < free){
				bestIp = ip;
				bestFree = free;
			}

		}

		if(bestIp.isEmpty()) {
			throw new IllegalStateException("No available managers");
		}

		return bestIp;
	}

	public String getUltraManager() {
		MongoCollection<Document> managers = getManagerCollection();

		long bestFree = 0;
		String bestIp = "";

		for(Document doc : managers.find()) {

			String ip = doc.getString("ip");
			long free = doc.getLong("free");
			long time = doc.getLong("time");
			boolean valid = doc.getBoolean("accept", true);
			String sort = doc.getString("type");

			if (!"ultra".equals(sort)){
				continue;
			}

			if(!valid) {
				continue;
			}

			if(System.currentTimeMillis() - time > 15000) {
				continue;//It must have turned off because it's not updating its time!
			}

			if(bestIp.isEmpty()) {
				bestIp = ip;
				bestFree = free;
			}else if(bestFree < free){
				bestIp = ip;
				bestFree = free;
			}

		}

		if(bestIp.isEmpty()) {
			throw new IllegalStateException("No available managers");
		}

		return bestIp;
	}

	@Override
	public long sendUpdateServerPlayers(String server, int players) {
		return setMetadata(server, "PLAYERS", String.valueOf(players));
	}

	@Override
	public long sendDeleteServer(String server) {
		long id = createRequestId();
		
		ServerInformation info = serverInfo.get(server);
		
		if(info == null) {
			return 0L;
		}
		
		if(info.getPort() == 0 || !isManagerExisting(info.getIp())) {
			redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+getBestManager(), RedisConstants.DELETE_SERVER+" "+id+" "+info.getName());
		}else{
			redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+info.getIp(), RedisConstants.DELETE_SERVER+" "+id+" "+info.getName());
		}
		
		return id;
	}
	
	@Override
	public long sendDeleteServerData(String server) {
		long id = createRequestId();
		
		ServerInformation info = serverInfo.get(server);
		
		
		
		if(info == null) {
			return 0L;
		}
		
		if(info.getPort() == 0 || !isManagerExisting(info.getIp())) {
			redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+getBestManager(), RedisConstants.DELETE_DATA+" "+id+" "+info.getName());
		}else {
			redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+info.getIp(), RedisConstants.DELETE_DATA+" "+id+" "+info.getName());
		}
		
		return id;
	}

	@Override
	public long sendStopServer(String server, boolean force) {
		
		long id = createRequestId();
		
		ServerInformation info = serverInfo.get(server);
		
		if(info == null) {
			return 0L;
		}

		redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+info.getIp(), RedisConstants.STOP_SERVER+" "+id+" "+info.getName()+" "+force);
		
		return id;
	}

	@Override
	public long sendCreateServer(String server, String type) {
		
		long id = createRequestId();
		
		String bestFree = getBestManager();

		redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+bestFree, RedisConstants.CREATE_SERVER+" "+id+" "+server+" "+type);
		
		return id;
	}

	@Override
	public long sendStartServer(String server, boolean priority) throws NoAvailableServerException {
		
		long id = createRequestId();
		
		ServerInformation info = serverInfo.get(server);

		if(info.isUltraPremium()){
			try{
				String best = getUltraManager();
				redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+best, RedisConstants.START_SERVER+" "+id+" "+server);
			}catch (IllegalStateException e){
				redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+getBestManager(), RedisConstants.START_SERVER+" "+id+" "+server);
			}
			return id;
		}

		if(info.getPort() == 0 || !isManagerExisting(info.getIp())) {
			redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+getBestManager(), RedisConstants.START_SERVER+" "+id+" "+server);
		}else {
			redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+info.getIp(), RedisConstants.START_SERVER+" "+id+" "+server);
		}

		
		
		return id;
	}

	@Override
	public long sendCommandToServer(String server, String command) {
		
		long id = createRequestId();
		
		ServerInformation info = serverInfo.get(server);
		
		if(info == null) {
			return 0L;
		}

		redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+info.getIp(), RedisConstants.COMMAND_SERVER+" "+id+" "+server+" "+command);
		
		return id;
	}

	@Override
	public long createConsoleReadContract(String server) {
		return 0;
	}

	@Override
	public long destroyConsoleContract(long id) {
		return 0;
	}

	@Override
	public long setMetadata(String server, String key, String value) {
		
		if(key == null || value == null) {
			throw new IllegalArgumentException("key or value cannot be null ("+key+") ("+value+")");
		}
		
		ServerInformation info = serverInfo.get(server);
		if(info != null) {
			if(info.getMetadata().containsKey(key)) {
				if(info.getMetadata().get(key).equals(value)) {
					return 0L;//no need
				}
			}else {
				if(value.equals("")) {
					return 0L;//no need
				}
			}
		}
		
		Document found = grabServerDocument(server);
		
		if(found == null) {
			return 0L;
		}
		
		
		MongoCollection<Document> col = getServerCollection();
		
		if(value.isEmpty()) {
			col.findOneAndUpdate(Filters.eq(found.getObjectId("_id")), new Document("$unset", new Document("metadata."+key, "")));
		}else {
			col.findOneAndUpdate(Filters.eq(found.getObjectId("_id")), new Document("$set", new Document("metadata."+key, value)));	
		}
		
		grabServer(server);
		
		redis.publish(RedisConstants.MANAGER_UPDATE_CHANNEL, server+" "+RedisConstants.UPDATE);
		
		return 0L;
	}

	@Override
	public long saveMetadata(String server) {
		return 0;
	}

	@Override
	public void shutdownServerManager() {

	}

	@Override
	public long sendRestartServer(String server) {
		long id = createRequestId();
		
		ServerInformation info = serverInfo.get(server);
		
		if(info == null) {
			return 0L;
		}

		redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+info.getIp(), RedisConstants.RESTART_SERVER+" "+id+" "+server);
		
		return id;
	}

	@Override
	public long renameRealm(String oldName, String newName) {
		long id = createRequestId();
		
		redis.publish(RedisConstants.MANAGER_REQUEST_CHANNEL+getBestManager(), RedisConstants.RENAME_SERVER+" "+id+" "+oldName+" "+newName);
		
		return id;
	}

	@Override
	public long refreshServer(String server) {
		grabServer(server);
		return 0;
	}

	@Override
	public long setPlayers(String server, int players, int max) {
		return setMetadata(server, "PLAYERS", String.valueOf(players));
	}

	@Override
	public void addListener(ServerUpdateListener listener) {
		listeners.add(listener);
	}

	@Override
	public List<ServerInformation> getServers() {
		synchronized (serverInfo) {
			return new ArrayList<>(serverInfo.values());	
		}
	}

	@Override
	public List<ServerInformation> getServers(Predicate<ServerInformation> filter) {
		return getServers().stream().filter(filter).collect(Collectors.toList());
	}

	@Override
	public ServerInformation getServerByName(String server) {
		return serverInfo.get(server);
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

	@Override
	public void moveServer(String server, ServerManagerClient target) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long ftpServerInfo(int port, String username, String password) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String[] getChannel() {
		return new String[] {RedisConstants.MANAGER_RESPONSE_CHANNEL, RedisConstants.MANAGER_UPDATE_CHANNEL};
	}

	@Override
	public void onMessage(String channel, String message) {
		if(channel.equals(RedisConstants.MANAGER_RESPONSE_CHANNEL)) {
			if(!message.equals("")) {
				try{
					String[] splits = message.split(" ");
					long reqId = Long.parseLong(splits[0]);
					ResponseCodes code = ResponseCodes.valueOf(splits[1]);

					listeners.forEach(l -> l.onReply(reqId, code));
				}catch(NumberFormatException e){ }
			}
		}else if(channel.equals(RedisConstants.MANAGER_UPDATE_CHANNEL)){
			String[] splits = message.split(" ");
			String server = splits[0];
			String op = splits[1];
			
			if(op.equals(RedisConstants.UPDATE)) {
				grabServer(server);
			}else if (op.equals(RedisConstants.DELETE)){
				serverInfo.remove(server);
			}
			
		}
	}
	
	public static ServerInformation generateInfo(Document doc) {
		
		String name = doc.getString("server_name");
		
		Document meta = (Document) doc.get("metadata");
		
		Map<String, String> metadata = new HashMap<>();
		
		int playersOnline = 0;
		ServerStatus status = ServerStatus.OFFLINE;
		String ip = "0.0.0.0";
		int port = 0;
		
		for(String key : meta.keySet()) {
			String val = meta.get(key).toString();
			
			if(key.equals("PLAYERS")) {
				playersOnline = Integer.parseInt(val);
			}else if(key.equals("STATUS")) {
				status = ServerStatus.valueOf(val);
			}else if(key.equalsIgnoreCase("SOURCE")) {
				String[] splits = val.split(":");
				
				ip = splits[0];
				port = Integer.parseInt(splits[1]);
			}else {
				metadata.put(key, val);
			}
			
		}
		
		ServerInformation info = new ServerInformation(ip, port, name, playersOnline, status, metadata);
		
		return info;
	}
	
}

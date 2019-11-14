package com.playerrealms.client.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.playerrealms.client.NoAvailableServerException;
import com.playerrealms.client.ServerManagerClient;
import com.playerrealms.client.ServerUpdateListener;
import com.playerrealms.client.SingleServerManagerClient;
import com.playerrealms.common.ResponseCodes;
import com.playerrealms.common.ServerInformation;
import com.playerrealms.common.ServerStatus;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

public class ServerManagerCluster implements ServerManagerClient, ServerUpdateListener {

	private List<ServerUpdateListener> listeners;
	
	private Set<ServerClientInfo> remoteAddresses;
	
	private Map<Integer, ServerManagerClient> clients;
	
	private Map<Long, Integer> contracts;
	
	private boolean connected;
	
	private String id;
	
	public ServerManagerCluster(String id) {
		this.id = id;
		listeners = new ArrayList<>();
		remoteAddresses = new HashSet<>();
		clients = new HashMap<>();
		contracts = new HashMap<>();
		connected = false;
	}
	
	@Override
	public long getAvailableDiskSpace() {
		return clients.values().stream().mapToLong(client -> client.getAvailableDiskSpace()).sum();
	}
	
	@Override
	public boolean isConnected() {
		for(ServerManagerClient client : clients.values()){
			if(client.isConnected()){
				return true;
			}
		}
		return false;
	}
	
	@Override
	public String toString() {
		return "ServerManagerCluster {"+clients.values().toString()+"}";
	}
	
	@Override
	public String getHost() {
		throw new UnsupportedOperationException("Cluster contains many hosts");
	}
	
	@Override
	public int getFileTransferPort() {
		throw new UnsupportedOperationException("Cluster contains many fts ports");
	}
	
	@Override
	public boolean isAvailable() {
		for(ServerManagerClient client : clients.values()){
			if(client.isAvailable()){
				return true;
			}
		}
		return false;
	}
	
	public void addConnection(String ip, int port){
		if(connected){
			throw new IllegalStateException("Already connected!!!");
		}
		remoteAddresses.add(new ServerClientInfo(ip, port));
	}
	
	public void connect(){
		if(remoteAddresses.size() == 0){
			throw new IllegalStateException("No given addresses...");
		}
		
		int nextId = 0;
		
		EventLoopGroup worker = new NioEventLoopGroup();
		
		for(ServerClientInfo pair : remoteAddresses){
			
			SingleServerManagerClient client = new SingleServerManagerClient(id, worker);
			
			client.addListener(this);
			
			client.connect(pair.getIp(), pair.getPort());
			
			clients.put(nextId++, client);
			
		}
	}
	
	private int getClientId(ServerManagerClient client){
		for(int i : clients.keySet()){
			if(clients.get(i).equals(client)){
				return i;
			}
		}
		return -1;
	}
	
	private ServerManagerClient getClientByContract(long id){
		if(contracts.containsKey(id)){
			return clients.get(contracts.get(id));
		}
		
		return null;
	}
	
	private ServerManagerClient getClient(String server){
		for(ServerManagerClient client : clients.values()){
			ServerInformation info = client.getServerByName(server);
			
			if(info != null){
				return client;
			}
		}
		return null;
	}
	
	
	private ServerInformation getServer(String server){
		
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			return client.getServerByName(server);
		}
		
		return null;
	}
	
	@Override
	public ServerInformation getServerByCode(String code) {
		for(ServerManagerClient client : clients.values()){
			ServerInformation info = client.getServerByCode(code);
			
			if(info != null){
				return info;
			}
		}
		return null;
	}
	
	private ServerManagerClient getBestForNewServer(){
		
		long most = Long.MIN_VALUE;
		ServerManagerClient best = null;
		
		for(ServerManagerClient client : clients.values()){
			
			if(best == null){
				best = client;
			}else{
				if(client.getAvailableDiskSpace() > most){
					most = client.getAvailableDiskSpace();
					best = client;
				}
			}
			
		}
		
		return best;
	}

	private ServerManagerClient getAvailableClient() {
		for(ServerManagerClient client : clients.values()){
			if(client.isAvailable()){
				return client;
			}
		}
		return null;
	}

	@Override
	public void connect(String ip, int port) {
		addConnection(ip, port);
	}

	@Override
	public void shutdown() {
		for(ServerManagerClient client : clients.values()){
			client.shutdown();
		}
	}

	@Override
	public long sendUpdateServerPlayers(String server, int players) {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			return client.sendUpdateServerPlayers(server, players);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}

	@Override
	public long sendDeleteServer(String server) {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			return client.sendDeleteServer(server);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}

	@Override
	public long sendStopServer(String server, boolean force) {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			return client.sendStopServer(server, force);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}

	@Override
	public long sendCreateServer(String server, String type) {
		ServerManagerClient client = getBestForNewServer();
		
		if(client != null){
			return client.sendCreateServer(server, type);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}

	@Override
	public long sendStartServer(String server, boolean priority) throws NoAvailableServerException {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			if(!client.isAvailable()){
				
				ServerManagerClient available = getAvailableClient();
				
				if(available == null){
					throw new NoAvailableServerException();
				}
				
				client.moveServer(server, available);
				
				return available.sendStartServer(server, priority);
				
			}
			
			return client.sendStartServer(server, priority);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}


	@Override
	public long sendCommandToServer(String server, String command) {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			return client.sendCommandToServer(server, command);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}

	@Override
	public long createConsoleReadContract(String server) {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			long id = client.createConsoleReadContract(server);
			
			contracts.put(id, getClientId(client));
			
			return id;
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}

	@Override
	public long destroyConsoleContract(long id) {
		ServerManagerClient client = getClientByContract(id);
		
		if(client != null){
			contracts.remove(id);
			return client.destroyConsoleContract(id);
		}
		
		return 0;
	}

	@Override
	public long setMetadata(String server, String key, String value) {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			return client.setMetadata(server, key, value);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}

	@Override
	public long saveMetadata(String server) {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			return client.saveMetadata(server);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}

	@Override
	public void shutdownServerManager() {
		for(ServerManagerClient client : clients.values()){
			client.shutdownServerManager();
		}
	}

	@Override
	public long sendRestartServer(String server) {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			return client.sendRestartServer(server);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}

	@Override
	public long renameRealm(String oldName, String newName) {
		ServerManagerClient client = getClient(oldName);
		
		if(client != null){
			return client.renameRealm(oldName, newName);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+oldName);
	}

	@Override
	public long refreshServer(String server) {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			return client.refreshServer(server);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}
	
	@Override
	public long setPlayers(String server, int players, int max) {
		ServerManagerClient client = getClient(server);
		
		if(client != null){
			return client.setPlayers(server, players, max);
		}
		
		throw new IllegalArgumentException("No server could be found with name "+server);
	}

	@Override
	public void addListener(ServerUpdateListener listener) {
		listeners.add(listener);
	}

	@Override
	public List<ServerInformation> getServers() {
		
		List<ServerInformation> together = new LinkedList<>();
		
		for(ServerManagerClient client : clients.values()){
			together.addAll(client.getServers());
		}
		
		return together;
	}

	@Override
	public ServerInformation getServerByName(String name) {
		return getServer(name);
	}

	@Override
	public void onServerListReceive(Collection<ServerInformation> info) {
		listeners.forEach(listener -> listener.onServerListReceive(info));
	}

	@Override
	public void onServerReceive(ServerInformation info) {
		listeners.forEach(listener -> listener.onServerReceive(info));
	}

	@Override
	public void onServerDeleted(String name) {
		listeners.forEach(listener -> listener.onServerDeleted(name));
	}

	@Override
	public void onReply(long responseId, ResponseCodes code) {
		listeners.forEach(listener -> listener.onReply(responseId, code));
	}

	@Override
	public void onConsoleRead(long responseId, String line) {
		listeners.forEach(listener -> listener.onConsoleRead(responseId, line));
	}

	@Override
	public void onDisconnectFromServerManager() {
		if(isConnected()){
			return;
		}
		listeners.forEach(listener -> listener.onDisconnectFromServerManager());
	}

	@Override
	public void onServerStatusChange(ServerInformation info, ServerStatus old) {
		listeners.forEach(listener -> listener.onServerStatusChange(info, old));
	}

	@Override
	public List<ServerInformation> getServers(Predicate<ServerInformation> filter) {
		List<ServerInformation> servers = new ArrayList<>();
		
		for(ServerManagerClient client : clients.values()){
			servers.addAll(client.getServers(filter));
		}
		
		return servers;
	}

	@Override
	public void moveServer(String server, ServerManagerClient target) {
		ServerManagerClient client = getClient(server);
		
		if(client == target){
			return;
		}
		
		client.moveServer(server, target);
	}

	@Override
	public long ftpServerInfo(int port, String username, String password) {
		return 0L;
	}

	@Override
	public long sendDeleteServerData(String server) {
		// TODO Auto-generated method stub
		return 0;
	}
	
}

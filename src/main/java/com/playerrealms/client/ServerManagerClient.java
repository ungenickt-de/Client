package com.playerrealms.client;

import java.util.List;
import java.util.function.Predicate;

import com.playerrealms.common.ServerInformation;

public interface ServerManagerClient {

	public String getHost();
	
	public int getFileTransferPort();
	
	public long getAvailableDiskSpace();
	
	public boolean isAvailable();
	
	public boolean isConnected();
	
	public void connect(String ip, int port);
	
	public void shutdown();
	
	public long sendUpdateServerPlayers(String server, int players);
	
	public long sendDeleteServer(String server);
	
	public long sendDeleteServerData(String server);
	
	public long sendStopServer(String server, boolean force);
	
	public long sendCreateServer(String server, String type);
	
	public long sendStartServer(String server, boolean priority) throws NoAvailableServerException;
	
	public long sendCommandToServer(String server, String command);

	public long createConsoleReadContract(String server);
	
	public long destroyConsoleContract(long id);
	
	public long setMetadata(String server, String key, String value);
	
	public long saveMetadata(String server);
	
	public void shutdownServerManager();
	
	public long sendRestartServer(String server);
	
	public long renameRealm(String oldName, String newName);
	
	public long refreshServer(String server);
	
	public long setPlayers(String server, int players, int max);
	
	public void addListener(ServerUpdateListener listener);
	
	public List<ServerInformation> getServers();
	
	public List<ServerInformation> getServers(Predicate<ServerInformation> filter);
	
	public ServerInformation getServerByName(String server);

	public ServerInformation getServerByCode(String code);

	public void moveServer(String server, ServerManagerClient target);
	
	public long ftpServerInfo(int port, String username, String password);
	
}

package com.playerrealms.client;

import java.util.Collection;

import com.playerrealms.common.ResponseCodes;
import com.playerrealms.common.ServerInformation;
import com.playerrealms.common.ServerStatus;

public interface ServerUpdateListener {

	public void onServerListReceive(Collection<ServerInformation> info);
	
	public void onServerReceive(ServerInformation info);
	
	public void onServerDeleted(String name);
	
	public void onReply(long responseId, ResponseCodes code);
	
	public void onConsoleRead(long responseId, String line);
	
	public void onDisconnectFromServerManager();
	
	public void onServerStatusChange(ServerInformation info, ServerStatus old);
	
}

package com.playerrealms.client;

import java.util.Collection;

import com.playerrealms.common.ResponseCodes;
import com.playerrealms.common.ServerInformation;
import com.playerrealms.common.ServerStatus;

public abstract class ServerUpdateAdapter implements ServerUpdateListener {

	public void onServerListReceive(Collection<ServerInformation> info) {
		
	}

	public void onServerReceive(ServerInformation info) {
		
	}

	public void onServerDeleted(String name) {
		
	}
	
	public void onReply(long responseId, ResponseCodes code) {
		
	}
	
	@Override
	public void onConsoleRead(long responseId, String line) {
		
	}
	
	@Override
	public void onDisconnectFromServerManager() {
		
	}

	@Override
	public void onServerStatusChange(ServerInformation info, ServerStatus old) {
		
	}
	
	
}

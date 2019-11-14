package com.playerrealms.client.cluster;

public class ServerClientInfo {

	private final String ip;
	
	private final int port;
	
	public ServerClientInfo(String ip, int port) {
		this.ip = ip;
		this.port = port;
	}
	
	public String getIp() {
		return ip;
	}
	
	public int getPort() {
		return port;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof ServerClientInfo){
			ServerClientInfo other = (ServerClientInfo) obj;
			
			if(other.ip.equals(ip) && other.port == port){
				return true;
			}
		}
		return super.equals(obj);
	}
	
	@Override
	public int hashCode() {
		return ip.hashCode() * 31 + port;
	}
	
}

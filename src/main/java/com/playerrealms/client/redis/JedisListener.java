package com.playerrealms.client.redis;

public interface JedisListener {

	public String[] getChannel();
	
	public void onMessage(String channel, String message);
	
}

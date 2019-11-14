package com.playerrealms.client.redis;

public interface RedisInterface {
	
	public void shutdown();
	
	public void subscribe(JedisListener listener);
	
	public void publish(String ch, String msg);
	
}

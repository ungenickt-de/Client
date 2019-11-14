package com.playerrealms.client.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.playerrealms.client.ServerUpdateListener;
import com.playerrealms.client.SingleServerManagerClient;
import com.playerrealms.client.redis.JedisListener;
import com.playerrealms.client.redis.RedisInterface;
import com.playerrealms.client.redis.RedisMongoManagerClient;
import com.playerrealms.common.ResponseCodes;
import com.playerrealms.common.ServerInformation;
import com.playerrealms.common.ServerStatus;
import com.playerrealms.common.ServerType;

public class ConnectionTest {

	public static void main(String[] args2) {
		MongoClient mongoClient = new MongoClient("playerrealms.com", 27017);
		MongoDatabase database = mongoClient.getDatabase("playerrealms");
		RedisMongoManagerClient client = new RedisMongoManagerClient(new RedisInterface() {
			
			@Override
			public void subscribe(JedisListener listener) {
				System.out.println("Subscribe");
			}
			
			@Override
			public void shutdown() {
				System.out.println("Shutdown");
			}
			
			@Override
			public void publish(String ch, String msg) {
				System.out.println("Publish "+ch+" "+msg);
			}
		}, database);
		
		
		System.out.println(client.getBestManager());
	}
	
}

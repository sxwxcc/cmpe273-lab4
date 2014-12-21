package edu.sjsu.cmpe273.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;

public class CRDTClient {
	private List<DistributedCacheService> servers;

	public CRDTClient() {
		DistributedCacheService cache1 = new DistributedCacheService("http://localhost:3000");
		DistributedCacheService cache2 = new DistributedCacheService("http://localhost:3001");
		DistributedCacheService cache3 = new DistributedCacheService("http://localhost:3002");
		List<DistributedCacheService> servers = new ArrayList<DistributedCacheService>();
		servers.add(cache1);
		servers.add(cache2);
		servers.add(cache3);
	}

	private CountDownLatch countDownLatch;
	
	public boolean put(long key, String value) throws InterruptedException, IOException {
		final AtomicInteger completedCount = new AtomicInteger(0);
		this.countDownLatch = new CountDownLatch(servers.size());
		final ArrayList<DistributedCacheService> writtenServerList = new ArrayList<DistributedCacheService>(3);
		for (final DistributedCacheService cacheServer : servers) {
			Future<HttpResponse<JsonNode>> future = Unirest.put(cacheServer.getCacheServerUrl()+ "/cache/{key}/{value}")
					.header("accept", "application/json")
					.routeParam("key", Long.toString(key))
					.routeParam("value", value)
					.asJsonAsync(new Callback<JsonNode>() {

						public void failed(UnirestException e) {
							System.out.println("The request has failed ");
							countDownLatch.countDown();
						}

						public void completed(HttpResponse<JsonNode> response) {
							int count = completedCount.incrementAndGet();
							writtenServerList.add(cacheServer);
							System.out.println("The request is successful "+cacheServer.getCacheServerUrl());
							countDownLatch.countDown();
						}

						public void cancelled() {
							System.out.println("The request has been cancelled");
							countDownLatch.countDown();
						}

					});
		}
		this.countDownLatch.await();
		if (completedCount.intValue() > 1) {
			return true;
		} else {
			System.out.println("Deleting...");
			this.countDownLatch = new CountDownLatch(writtenServerList.size());
			for (final DistributedCacheService cacheServer : writtenServerList) {
				Future<HttpResponse<JsonNode>> future = Unirest.get(cacheServer.getCacheServerUrl() + "/cache/{key}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(key))
						.asJsonAsync(new Callback<JsonNode>() {

							public void failed(UnirestException e) {
								System.out.println("Delete failed..."+cacheServer.getCacheServerUrl());
								countDownLatch.countDown();
							}

							public void completed(HttpResponse<JsonNode> response) {
								System.out.println("Delete is successful "+cacheServer.getCacheServerUrl());
								countDownLatch.countDown();
							}

							public void cancelled() {
								System.out.println("The request has been cancelled");
								countDownLatch.countDown();
							}
					});
			}
			this.countDownLatch.await(3, TimeUnit.SECONDS);
			Unirest.shutdown();
			return false;
		}
	}

	public String get(long key) throws InterruptedException, UnirestException, IOException {
		this.countDownLatch = new CountDownLatch(servers.size());
		final Map<DistributedCacheService, String> resultMap = new HashMap<DistributedCacheService, String>();
		for (final DistributedCacheService cacheServer : servers) {
			Future<HttpResponse<JsonNode>> future = Unirest.get(cacheServer.getCacheServerUrl() + "/cache/{key}")
					.header("accept", "application/json")
					.routeParam("key", Long.toString(key))
					.asJsonAsync(new Callback<JsonNode>() {

						public void failed(UnirestException e) {
							System.out.println("The request has failed");
							countDownLatch.countDown();
						}

						public void completed(HttpResponse<JsonNode> response) {
							resultMap.put(cacheServer, response.getBody().getObject().getString("value"));
							System.out.println("The request is successful "+cacheServer.getCacheServerUrl());
							countDownLatch.countDown();
						}

						public void cancelled() {
							System.out.println("The request has been cancelled");
							countDownLatch.countDown();
						}
				});
		}
		this.countDownLatch.await(3, TimeUnit.SECONDS);
		final Map<String, Integer> countMap = new HashMap<String, Integer>();
		int maxCount = 0;
		for (String value : resultMap.values()) {
			int count = 1;
			if (countMap.containsKey(value)) {
				count = countMap.get(value);
				count++;
			}
			if (maxCount < count)
				maxCount = count;
			countMap.put(value, count);
		}
		System.out.println("maxCount "+maxCount);
		String value = this.getKeyByValue(countMap, maxCount);
		System.out.println("maxCount value "+value);
		if (maxCount != this.servers.size()) {
			for (Entry<DistributedCacheService, String> cacheServerData : resultMap.entrySet()) {
				if (!value.equals(cacheServerData.getValue())) {
					System.out.println("Repairing "+cacheServerData.getKey());
					HttpResponse<JsonNode> response = Unirest.put(cacheServerData.getKey() + "/cache/{key}/{value}")
							.header("accept", "application/json")
							.routeParam("key", Long.toString(key))
							.routeParam("value", value)
							.asJson();
				}
			}
			for (DistributedCacheService cacheServer : this.servers) {
				if (resultMap.containsKey(cacheServer)) continue;
				System.out.println("Repairing "+cacheServer.getCacheServerUrl());
				HttpResponse<JsonNode> response = Unirest.put(cacheServer.getCacheServerUrl() + "/cache/{key}/{value}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(key))
						.routeParam("value", value)
						.asJson();
			}
		} else {
			System.out.println("Repair not required");
		}
		Unirest.shutdown();
		return value;
	}

	public String getKeyByValue(Map<String, Integer> map, int value) {
		for (Entry<String, Integer> entry : map.entrySet()) {
			if (value == entry.getValue()) return entry.getKey();
		}
		return null;
	}
}
package edu.sjsu.cmpe273.client;

/**
 * Cache Service Interface
 * 
 */
public interface CacheServiceInterface {
    public String get(long key);

    public void put(long key, String value);
}

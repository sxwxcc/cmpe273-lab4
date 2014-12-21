package edu.sjsu.cmpe273.client;

public class Client {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Cache Client...");
        
        CRDTClient crdtClient = new CRDTClient();
        boolean request = crdtClient.put(1, "a");
        if (request) {
        	
        	System.out.println("Wait for 30secs.");
        	Thread.sleep(30000);
        	request = crdtClient.put(1, "b");
        	if (request) {
        		System.out.println("Wait for 30secs.");
            	Thread.sleep(30000);
            	String value = crdtClient.get(1);
            	System.out.println("GET value "+value);
        	} else {
            	System.out.println("Writing failed.");
        	}
        } else {
        	
        	System.out.println("Writing failed.");
        }	  
    }
}

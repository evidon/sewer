package net.pixelcop.sewer;

import java.io.IOException;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;

import java.io.FileInputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.pixelcop.sewer.sink.durable.TransactionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendRabbitMQ extends Thread {

    private String propInput = "config.properties";
    private static final Logger LOG = LoggerFactory.getLogger(SendRabbitMQ.class);

    private Properties prop = new Properties();
    private ConnectionFactory factory;
    private Channel channel;
    private Connection connection;
    private String EXCHANGE_NAME;
    private String EXCHANGE_TYPE;
    private String HOST_NAME;
    private int PORT_NUMBER;
    private String ROUTING_KEY;
    private String USERNAME;
    private String PASSWORD;
    private String VIRTUAL_HOST;
    private String QUEUE_NAME;
    private String QUEUE_CONFIRM_NAME;
    private boolean CONFIRMS=false;
        
    public static BlockingQueue<BlockingQueue<String>> batchQueue;

    public SendRabbitMQ() {        
    	loadProperties();
    	EXCHANGE_NAME = prop.getProperty("rmq.exchange.name");
    	EXCHANGE_TYPE = prop.getProperty("rmq.exchange.type");
    	HOST_NAME = prop.getProperty("rmq.host.name");
        PORT_NUMBER = Integer.parseInt( prop.getProperty("rmq.port.number") );
        ROUTING_KEY = prop.getProperty("rmq.routing.key");
        USERNAME = prop.getProperty("rmq.username");
        PASSWORD = prop.getProperty("rmq.password");
        VIRTUAL_HOST = prop.getProperty("rmq.virtual.host");
        QUEUE_NAME = prop.getProperty("rmq.queue.name");
        QUEUE_CONFIRM_NAME = prop.getProperty("rmq.queue.confirm.name");
        CONFIRMS = Boolean.parseBoolean( prop.getProperty("rmq.queue.is.confirm") );

        createFactory();        
        start();
    }
    
    public void sendMessage() {
    	if( batchQueue.size() > 0 ) {
    		BlockingQueue<String> batch = batchQueue.peek();
    		boolean connectionError=false;
    		if( batch.size() > 0) {
    			try{
    				if( channel==null || !channel.isOpen()) {
    					open();
    				}
    	            //send message
	                channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, MessageProperties.PERSISTENT_TEXT_PLAIN, batch.toString().getBytes());
	                if( CONFIRMS) {
	                    boolean test = channel.waitForConfirms();
	                    if( test ) {
	                    	batchQueue.take();
	    	                LOG.info("RABBITMQ: Sent to Rabbit Servers, batch of Size: "+batch.size());
	                    }
	                    else {
	    	                LOG.info("RABBITMQ: NACKED, will try resending it, left in queue.");
	                    }	                    	
	                }
	                else {
	                	LOG.error("RABBITMQ: Confirms is off! Fix!");
	                }
	                
	            }  catch( InterruptedException e) {
	                 e.printStackTrace();
	                 connectionError=true;
	            } catch( IOException e) {
	                 e.printStackTrace();
	                 connectionError=true;
	            } catch( NullPointerException e) {
	                 e.printStackTrace();
	                 connectionError=true;
	            }
    			if(connectionError){
 	                 LOG.error("RABBITMQ: Connection Error, Reseting Connection...");
 	                 resetConnection();
 	                 createFactory();
    			}
    			
    		}
    		else {
    			LOG.info("RABBITMQ: Batch is empty, removing from queue.");
    			try {
    				batchQueue.take();
				} catch (InterruptedException e) {
	                LOG.error("RABBITMQ: Error removing black batch from queue.");
					e.printStackTrace();
				}
    		}
    	}
    }
    
    public void createFactory() {
        LOG.info("RABBITMQ: Reinitializing Connection Factory...");
    	factory = new ConnectionFactory();
        factory.setHost(HOST_NAME);
        factory.setPort(PORT_NUMBER);
        factory.setUsername(USERNAME);
        factory.setPassword(PASSWORD);
        factory.setVirtualHost(VIRTUAL_HOST);
    }
    
    public void resetConnection() {
    	//close connection
        LOG.info("RABBITMQ: Closing connection with Rabbit Servers...");
        try {
        	if(channel != null) {
				channel.close();
			}
	        if(connection != null) {
	        	connection.close();
	        }
	    }catch (IOException e) {
	        LOG.error("RABBITMQ: Error Closing connection");
			e.printStackTrace();
		}
    }
    
    public void open() throws IOException {
    	LOG.info("RABBITMQ: Opening connection with Rabbit Servers...");
		connection = factory.newConnection();
        channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, true); // true so its durable

        if(CONFIRMS)
            createQueueConfirm();
        else
            createQueue();
    }

    public void createQueueConfirm() {
        try {
        	LOG.info("RABBITMQ: Declaring confirms queue...");
            channel.queueDeclare(QUEUE_CONFIRM_NAME, true, false, false, null);
            channel.confirmSelect();
            channel.queueBind(QUEUE_CONFIRM_NAME, EXCHANGE_NAME, ROUTING_KEY);
        } catch (IOException e) {
        	LOG.info("RABBITMQ: Error declaring confirms queue.");
            e.printStackTrace();
        }
    }

    public void createQueue() {
        try {
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close(){
        try {
        	LOG.info("RABBITMQ: Closing connection with Rabbit Servers...");
        	if( channel != null )
        		if( channel.isOpen() )
        			channel.close();
        		else
        			LOG.error("RABBITMQ: Channel is already closed.");
        	else
        		LOG.error("RABBITMQ: Channel is null.");
        	if( connection != null)
        		if( connection.isOpen() )
        			connection.close();
        		else
        			LOG.error("RABBITMQ: Connection is already closed.");
        	else
        		LOG.error("RABBITMQ: Connection is null.");
        } catch (IOException e) {
        	LOG.info("RABBITMQ: Error Closing connection with Rabbit Servers.");
            e.printStackTrace();
        } 
    }

    public void putBatch(BlockingQueue<String> queue) {
    	try {
    		batchQueue.put(queue);
    		LOG.info("RABBITMQ: Batch Queue Size: "+batchQueue.size());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }
    
	private void loadProperties() {
		prop = new Properties();	 
		try {
            File file = new File(SendRabbitMQ.class.getClassLoader().getResource( propInput ).toURI());
            prop.load( new FileInputStream( file ) );
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
            e.printStackTrace();
        }
	}
	
	public void run() {
		if(batchQueue == null) {
        	LOG.info("RABBITMQ: Initializing batchQueue...");
			batchQueue = new LinkedBlockingQueue<BlockingQueue<String>>();
		}
		while(true) {
			sendMessage();
		}
	}
	
}
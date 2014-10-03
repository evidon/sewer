package net.pixelcop.sewer.sink;

import java.io.IOException;
import java.util.ArrayList;
import net.pixelcop.sewer.DrainSink;
import net.pixelcop.sewer.Event;
import net.pixelcop.sewer.SendRabbitMQTopic.RabbitMessageBatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.pixelcop.sewer.SendRabbitMQTopic;
import net.pixelcop.sewer.sink.durable.TransactionManager;

import com.evidon.nerf.AccessLogWritable;

/**
 * @author richard craparotta
 */
@DrainSink
public class SequenceFileWithRabbitMQSink extends SequenceFileSink {

  private static final Logger LOG = LoggerFactory.getLogger(SequenceFileWithRabbitMQSink.class);
  private ArrayList<RabbitMessageBatch> batches = new ArrayList<RabbitMessageBatch>(); 
  
  public SequenceFileWithRabbitMQSink(String[] args) {
	super(args);
  }

  @Override
  public void close() throws IOException {
	  //sends each batch (different hosts) to SendRabbit
	  if( !TransactionManager.sendRabbit.isAlive() )
		  TransactionManager.restartRabbit();
	  
	  for(RabbitMessageBatch batch : batches ) {
		  LOG.info("RABBITMQ: ::::::::::::::::::::::::::Putting batch of host: "+batch.getHost());
		  TransactionManager.sendRabbit.putBatch(batch);
	  }
	  super.close();
  }
  
  @Override
  public void open() throws IOException {
	  super.open();
  }
  
  int count = 1;
  @Override
  public void append(Event event) throws IOException {
    super.append(event);
    //adding to Rabbit message,adds to the RabbitMessageBatch object that has the same host, if no matches creates one with that host and adds.
    boolean done = false;
    
    //APPEND IS BEING CALLED THE SAME NUMBER OF TIMES PATRICK SAYS IT SENT A REQUEST
    
    for(RabbitMessageBatch rmb : batches) {
//    	done = rmb.checkHostAndAddMessage(event.toString(), ((AccessLogWritable)event).getHost());
    	done = rmb.checkHostAndAddMessage(""+count+" , "+((AccessLogWritable)event).getHost(), ((AccessLogWritable)event).getHost());
    	if(done)
    		break;
    }
    if(!done) {
    	RabbitMessageBatch newBatch = TransactionManager.sendRabbit.new RabbitMessageBatch(((AccessLogWritable)event).getHost());
//    	done = newBatch.checkHostAndAddMessage(event.toString(), ((AccessLogWritable)event).getHost());
    	done = newBatch.checkHostAndAddMessage(""+count+" , "+((AccessLogWritable)event).getHost(), ((AccessLogWritable)event).getHost());
    	batches.add(newBatch);
//    	LOG.info("RABBITMQ: Created batch and added message to host: "+newBatch.getHost());
    }
//    LOG.info("TRACKER: END.\n\n");
    count++;
    //using if( done==true )  same number of calls as Patricks program is sending
  }

}

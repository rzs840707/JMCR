package buffertest;

/****************************MAIN**************************/
import org.junit.Test;
import org.junit.runner.RunWith;

import edu.tamu.aser.reexecution.JUnit4MCRRunner;

@RunWith(JUnit4MCRRunner.class)
public class BufferTest {
  static int Ws = 5; /* parameter */
  public int[] stop;
  int counter = 0;
  int sum = 0;
  Object w;
  @Test
  public void testBoundedBuffer() throws Exception {
	     
	  w = new Object();	
	
     final Worker[] workers = new Worker[Ws];
     for(int i = 0; i < Ws; i++){
    	 workers[i] = new Worker(this,i+1,Ws);
    	 workers[i].start();
     }
     synchronized(w){
    	 while(this.counter < Ws) 
    		 w.wait();
     }
     System.out.println("Total sum is: " + this.sum);
  }
  
  public synchronized void updateCounter(){
	  synchronized(w){
		  this.counter++;
		  w.notify();
	  }
  }

public void updateSum(int id) {
		if(this.sum <= 40) 
			this.sum+= id;
		else 
			this.sum /=2;
}
}
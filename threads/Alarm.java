package nachos.threads;
import java.util.*;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	class Pair {
		KThread thread;
		long wakeUpTime;
		public Pair(KThread thread, long wakeUpTime) {
			this.thread = thread;
			this.wakeUpTime = wakeUpTime;
		}
	}
	// This PriorityQueue is to store sleeping threads
	PriorityQueue <Pair> wakeUpQueue = null;
	public Alarm() {
		// initialize the PQ for storing sleeping threads
		this.wakeUpQueue = new PriorityQueue<>(10000, new Comparator<Pair>(){ 
			@Override
			// sort by wake-up time
			public int compare(Pair p1, Pair p2) {
				if (p1.wakeUpTime > p2.wakeUpTime) {
					return 1;
				}
				else if (p1.wakeUpTime == p2.wakeUpTime) {
					return 0;
				}
				else {
					return -1;
				}
			}
		});

		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}
    	// Add Alarm testing code to the Alarm class
    
    	public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;

		for (int d : durations) {
	    	t0 = Machine.timer().getTime();
	    	ThreadedKernel.alarm.waitUntil (d);
	    	t1 = Machine.timer().getTime();
	   	System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
    	}

   	 // Implement more test methods here ...

   	 // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    	public static void selfTest() {
		alarmTest1();
	
		// Invoke your other test methods here ...
   	}
	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		// when there's sleeping thread in the queue
		if (!this.wakeUpQueue.isEmpty()) {
			// if the head of the queue reaches its wake-up time
			if (Machine.timer().getTime() >= this.wakeUpQueue.peek().wakeUpTime) {
				// take the sleeping thread out
				Pair curThread = wakeUpQueue.poll();
				Machine.interrupt().disable();
				// put the sleeping thread into the ready queue, i.e. waking it up
				curThread.thread.ready();
				Machine.interrupt().enable();
			}
		}
		// context switch
		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		if (x <= 0) {
			return;
		}
		// disable the Machine.interrupt()
		if (Machine.interrupt().disabled() == false) {
			Machine.interrupt().disable();
		}
		long wakeUpTime = Machine.timer().getTime() + x;
		// put the sleeping thread into the priority queue
		this.wakeUpQueue.offer(new Pair(KThread.currentThread(), wakeUpTime));
		KThread.sleep();
	}
}

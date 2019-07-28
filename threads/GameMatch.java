
package nachos.threads;
import java.util.*;
import nachos.machine.*;

/**
 * A <i>GameMatch</i> groups together player threads of the same
 * ability into fixed-sized groups to play matches with each other.
 * Implement the class <i>GameMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into groups.
 */
public class GameMatch {
    private Condition2 beginnerCV;
    private Condition2 intermediateCV;
    private Condition2 expertCV;
    private static int matchIndex;
    private static int numPlayersInMatch;
    private static int beginnerSize;
    private static int intermediateSize;
    private static int expertSize;
    private static Lock beginnerLock;
    private static Lock intermediateLock;
    private static Lock expertLock;
    private static HashMap<KThread, Integer> matchMap;
    /* Three levels of player ability. */
    public static final int abilityBeginner = 1,
	abilityIntermediate = 2,
	abilityExpert = 3;

    /**
     * Allocate a new GameMatch specifying the number of player
     * threads of the same ability required to form a match.  Your
     * implementation may assume this number is always greater than zero.
     */
    public GameMatch (int numPlayersInMatch) {
	beginnerLock = new Lock();
	intermediateLock = new Lock();
	expertLock = new Lock();
	beginnerCV = new Condition2(beginnerLock);
	intermediateCV = new Condition2(intermediateLock);
	expertCV = new Condition2(expertLock);
	this.beginnerSize = 0;
	this.intermediateSize = 0;
	this.expertSize = 0;
	this.matchIndex = 1;
	this.numPlayersInMatch = numPlayersInMatch;
	this.matchMap = new HashMap<>();
    }

    /**
     * Wait for the required number of player threads of the same
     * ability to form a game match, and only return when a game match
     * is formed.  Many matches may be formed over time, but any one
     * player thread can be assigned to only one match.
     *
     * Returns the match number of the formed match.  The first match
     * returned has match number 1, and every subsequent match
     * increments the match number by one, independent of ability.  No
     * two matches should have the same match number, match numbers
     * should be strictly monotonically increasing, and there should
     * be no gaps between match numbers.
     *
     * @param ability should be one of abilityBeginner, abilityIntermediate,
     * or abilityExpert; return -1 otherwise.
     */
    public int play (int ability) {
    	// block for beginner ability
	if (ability == 1) {
		// for each thread, acquire the lock first
		beginnerLock.acquire();
		// increment the value of the queue size
		beginnerSize++;
		// remember the match number for each thread
		matchMap.put(KThread.currentThread(), matchIndex);
		// if the queue.size() equals to the required number of players
		if (beginnerSize == numPlayersInMatch) {
			// increment the match number
			matchIndex++;
			System.out.println(KThread.currentThread().getName() + " Thread is calling wakeAll()");
			// wake up all the previous thread
			beginnerCV.wakeAll();
			// reset the queue size to zero
			beginnerSize = 0;
		}
		// if the queue.size() does not equal to the number of players
		else {
			System.out.println(KThread.currentThread().getName() + " Thread is sleeping");		
			// put the current thread to sleep
			beginnerCV.sleep();
		}
		// release the lock
		beginnerLock.release();
	}

	// block for intermediate ability
	if (ability == 2) {
		intermediateLock.acquire();
		intermediateSize++;
		matchMap.put(KThread.currentThread(), matchIndex);
		if (intermediateSize == numPlayersInMatch) {
			matchIndex++;
			System.out.println(KThread.currentThread().getName() + " Thread is calling wakeAll()");
			intermediateCV.wakeAll();
			intermediateSize = 0;
		}

		else {
			System.out.println(KThread.currentThread().getName() + " Thread is sleeping");		
			intermediateCV.sleep();
		}
		intermediateLock.release();
	}

	// block for expert ability
	if (ability == 3) {
		expertLock.acquire();
		expertSize++;
		matchMap.put(KThread.currentThread(), matchIndex);
		if (expertSize == numPlayersInMatch) {
			matchIndex++;
			System.out.println(KThread.currentThread().getName() + " Thread is calling wakeAll()");
			expertCV.wakeAll();
			expertSize = 0;
		}
		else {
			System.out.println(KThread.currentThread().getName() + " Thread is sleeping");		
			expertCV.sleep();
		}
		expertLock.release();
	}
	return matchMap.get(KThread.currentThread());
    }

    // Place GameMatch test code inside of the GameMatch class.

    public static void matchTest4 () {
	final GameMatch match = new GameMatch(4);

	// Instantiate the threads
	KThread beg1 = new KThread( new Runnable () {
		public void run() {
		    int r = match.play(GameMatch.abilityBeginner);
		    System.out.println ("beg1 matched"+ ", match number: " + r);
		    // beginners should match with a match number of 1
		   // Lib.assertTrue(r == 1, "expected match number of 1");
		}
	    });
	beg1.setName("B1");

	KThread beg2 = new KThread( new Runnable () {
		public void run() {
		    int r = match.play(GameMatch.abilityBeginner);
		    System.out.println ("beg2 matched" +", match number: " + r);
		    // beginners should match with a match number of 1
		   // Lib.assertTrue(r == 1, "expected match number of 1");
		}
	    });
	beg2.setName("B2");

	KThread beg3 = new KThread( new Runnable () {
		public void run() {
		    int r = match.play(GameMatch.abilityBeginner);
		    System.out.println ("beg3 matched" + ", match number: " + r);
		    // beginners should match with a match number of 2
		   // Lib.assertTrue(r == 2, "expected match number of 2");
		   
		}
	    });
	beg3.setName("B3");

	KThread beg4 = new KThread( new Runnable () {
		public void run() {
		    int r = match.play(GameMatch.abilityBeginner);
		    System.out.println ("beg4 matched"+ ", match number: " + r);
		    // beginners should match with a match number of 2
		   // Lib.assertTrue(r == 2, "expected match number of 2");
		}
	    });
	beg4.setName("B4");

	KThread beg5 = new KThread( new Runnable () {
		public void run() {
		    int r = match.play(GameMatch.abilityExpert);
		    System.out.println ("beg5 matched"+ ", match number: " + r);
		    // beginners should match with a match number of 2
		   // Lib.assertTrue(r == 2, "expected match number of 2");
		}
	    });
	beg5.setName("B5");

	KThread beg6 = new KThread( new Runnable () {
		public void run() {
		    int r = match.play(GameMatch.abilityIntermediate);
		    System.out.println ("beg6 matched"+ ", match number: " + r);
		    // beginners should match with a match number of 2
		   // Lib.assertTrue(r == 2, "expected match number of 2");
		}
	    });
	beg6.setName("B6");

	
/*	KThread int1 = new KThread( new Runnable () {
		public void run() {
		    int r = match.play(GameMatch.abilityIntermediate);
		    Lib.assertNotReached("int1 should not have matched!");
		}
	    });
	int1.setName("I1");

	KThread exp1 = new KThread( new Runnable () {
		public void run() {
		    int r = match.play(GameMatch.abilityExpert);
		    Lib.assertNotReached("exp1 should not have matched!");
		}
	    });
	exp1.setName("E1");*/

	// Run the threads.  The beginner threads should successfully
	// form a match, the other threads should not.  The outcome
	// should be the same independent of the order in which threads
	// are forked.
	beg1.fork();  // match 1
//	int1.fork();
//	exp1.fork();
	beg2.fork();  // match 1
	beg3.fork();  // match 2
	beg4.fork();  // match 2
	beg5.fork();
	beg6.fork();
	
	// Assume join is not implemented, use yield to allow other
	// threads to run
	for (int i = 0; i < 10000; i++) {
	    KThread.currentThread().yield();
	}
    }
    
    public static void selfTest() {
	matchTest4();
    }
}

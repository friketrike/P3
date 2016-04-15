import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

/**
 * Class Monitor
 * To synchronize dining philosophers.
 *
 * @author Serguei A. Mokhov, mokhov@cs.concordia.ca  
 */
public class Monitor  
{
	/*
	 * ------------   
	 * Data members 
	 * ------------
	 */
	private enum State {THINKING, HUNGRY, EATING, STARVING}
	private static final int WAITING_BOUND = 2;
		
	private Lock lock = new ReentrantLock();
	private Condition[] self;
	private Condition talkBlock = lock.newCondition();
	
	private State state[];
	private Integer[] turnsWaiting;
	private int nThinkers; // keep a record of how many philosophers attended dinner
	private boolean isSomeoneTalking = false;
	/**
	 * Constructor
	 */
	public Monitor(int piNumberOfPhilosophers)
	{
		try{
			lock.lock();
			nThinkers = piNumberOfPhilosophers; // this is also the number of chopsticks between them
			self = new Condition[piNumberOfPhilosophers];
			state = new State[piNumberOfPhilosophers];
			turnsWaiting = new Integer[piNumberOfPhilosophers];
			for(int i = 0; i < nThinkers; i++){
				self[i] = lock.newCondition();
				state[i] = State.THINKING;
				turnsWaiting[i] = 0;
			}
		} finally {
			lock.unlock();
		}
	}

	/*
	 * -------------------------------
	 * User-defined monitor procedures
	 * -------------------------------
	 */
	
	/**
	 * Check whether philosopher with thread id piTID can eat.
	 * The rules:
	 * 1) Eat only if you' re hungry, someone might have told you 
	 * to check if you can eat and you weren't actually hungry.
	 * 2) if anyone of your neighbors is eating, you're short
	 * of chopsticks, wait. 
	 * If someone besides you is starving, let them eat first
	 * but let them know that you're beginning to starve.
	 * @param piTID Process id to check, id s start at 1, 
	 * so we always index arrays with id-1 
	 */
	public void test(final int piTID) {
		int i = piTID; // arrays are 0-index-based philosophers count themselves... happy
		boolean canGo = false;
		try {
			lock.lock();	
			if ( ( (state[toLeft(i)-1] != State.EATING) 
			 			&& (state[toLeft(i)-1] != State.STARVING) ) 
			 			&& ( (state[i-1] == State.HUNGRY) 
			 					|| (state[i-1] == State.STARVING) )
			 			&& ( (state[toRight(i)-1] != State.EATING) 
			 			&& (state[toRight(i)-1] != State.STARVING) ) ) {
				canGo = true;
			}
			
			/* 
			 * if I wasn't able to go because someone is starving
			 * then I should check that I'm not starving too
			 * if that's the case, whomever has waited longest should go
			 */
			
			// Case 1: my left neighbor is starving, have I waited more? 
			else if ( (state[toLeft(i)-1] == State.STARVING) 
						&& (state[i-1] == State.STARVING) 
						&& ( (state[toRight(i)-1] != State.EATING) 
	 					&& (state[toRight(i)-1] != State.STARVING) ) ) {
				// break the tie
				if(turnsWaiting[toLeft(i)-1] <= turnsWaiting[i-1]) {
					canGo = true;
				}
			}
			
			// Case 2: my right neighbor is starving, have I waited more?
			else if ( (state[toRight(i)-1] == State.STARVING) 
		 				&& (state[i-1] == State.STARVING) 
		 				&& ( (state[toLeft(i)-1] != State.EATING)
		 				&& (state[toLeft(i)-1] != State.STARVING) ) ) {
					// break the tie
				if(turnsWaiting[toRight(i)-1] <= turnsWaiting[i-1]) {
					canGo = true;
				}
			}
			
			// Case 3: both my neighbors are starving, have I waited more than both?
			else if ( (state[toRight(i)-1] == State.STARVING) 
						&& (state[toLeft(i)-1] == State.STARVING) ) {
				if(turnsWaiting[toLeft(i)-1] <= turnsWaiting[i-1]
						&& turnsWaiting[toRight(i)-1] <= turnsWaiting[i-1]) {
					canGo = true;
				}
			}
			
			//
			if (canGo) {
				state[i-1] = State.EATING;
				turnsWaiting[i-1] = 0;
				self[i-1].signal();
			}
			
			// if we can't go but we're hungry, keep track of our waiting time
			else if ( (state[i-1] == State.HUNGRY) 
 					|| (state[i-1] == State.STARVING) ) {
				turnsWaiting[i-1]++;
				if(turnsWaiting[i-1] >= WAITING_BOUND) { // aim to get served at WAITING_BOUND 
					// I've had it, I'm starving! Let them all know, grumble grumble.
					state[i-1] = State.STARVING;
				}
			}
		} finally {
			lock.unlock();
		}
		
	}

	/**
	 * Grants request (returns) to eat when both chopsticks/forks are available.
	 * Else forces the philosopher to wait()
	 * @param piTID Process id requesting cutlery, id s start at 1, 
	 * so we always index arrays with id-1 
	 */
	public void pickUp(final int piTID) {
		lock.lock();
		try {			
			state[piTID-1] = State.HUNGRY;
			test(piTID);
			if (state[piTID-1] != State.EATING){
				try {
					self[piTID-1].await();
				} catch(InterruptedException e) {
					System.err.println(e.getMessage());
				}
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * When a given philosopher's done eating, they put the chopsticks/forks down
	 * and let others know they are available.
	 * @param piTID Process id putting down cutlery, id s start at 1, 
	 * so we always index arrays with id-1 
	 */
	public void putDown(final int piTID) {
		lock.lock();
		try {	
			// arrays are 0-indexed while philosophers count themselves happy
			// Ehem, I mean from 1. change piTID to piTID when indexing
			state[piTID-1] = State.THINKING;
			test(toLeft(piTID));
			test(toRight(piTID));
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Only one philosopher at a time is allowed to share wisdom
	 * (while she is not eating).
	 */
	public void requestTalk() {
		try {
			lock.lock();
			while (isSomeoneTalking) {
				try{
					talkBlock.await();
				} catch(InterruptedException e) {
					System.err.println(e.getMessage());
				}
			}
			isSomeoneTalking = true;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * When one philosopher is done talking stuff, others
	 * can feel free to start talking.
	 */
	public void endTalk() {
		try {
			lock.lock();
			isSomeoneTalking = false;
			
			/* there's only one of those waiting that gets to preach,
			 * and whomever is waiting on talkBlock should get to talk. 
			 * don't need to signal them all.
			 */
			talkBlock.signal(); 
		} finally {
			lock.unlock();
		}
	}
	
	/**
	 * Helper function to get my right neighbor's ids
	 * We're interfacing 1-based numbering with 0-based indexing
	 * Translate down here to remove visual pollution from the
	 * monitor and make it look cleaner where it counts.
	 * @param piTID of process seeking its neighbor
	 */
	private int toRight(int piTID) {
		// subtract 1, manipulate including modulo and add 1 again
		int right = ( ((piTID-1) + 1) % nThinkers ) + 1;
		return right;
	}
	
	/**
	 * Helper function to get my left neighbor's ids
	 * We're interfacing 1-based numbering with 0-based indexing
	 * Translate down here to remove visual pollution from the
	 * monitor and make it look cleaner where it counts.
	 * @param piTID of process seeking its neighbor
	 */
	private int toLeft(int piTID) {
		int left = ( ((piTID-1) + nThinkers - 1) % nThinkers ) + 1;
		return left;
	}
}

// EOF

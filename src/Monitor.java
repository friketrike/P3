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
	private static final int WAITING_BOUND = 2;
	
	private final Lock lock = new ReentrantLock();
	private final Condition[] self;
	private enum State {THINKING, HUNGRY, EATING, STARVING}
	private State state[];
	private Integer[] turnsWaiting;
	private int nThinkers; // keep a record of how many philosophers attended dinner

	/**
	 * Constructor
	 */
	public Monitor(int piNumberOfPhilosophers)
	{
		nThinkers = piNumberOfPhilosophers; // this is also the number of chopsticks between them
		self = new Condition[piNumberOfPhilosophers];
		state = new State[piNumberOfPhilosophers];
		turnsWaiting = new Integer[piNumberOfPhilosophers];
		for(int i = 0; i < nThinkers; i++){
			turnsWaiting[i] = 0;
		}
	}

	/*
	 * -------------------------------
	 * User-defined monitor procedures
	 * -------------------------------
	 */
	public void test(final int piTID){
		int n = nThinkers; // abbreviate for ease of reading
		int i = piTID;
		try{
			lock.lock();		
			if ( ( (state[(i + n-1) % n] != State.EATING) 
			 			|| (state[(i + n-1) % n] != State.STARVING) ) 
			 			&& (state[i] == State.HUNGRY) 
			 			&& ( (state[(i + 1) % n] != State.EATING) 
			 			|| (state[(i + 1) % n] != State.STARVING) ) ) {
				state[i] = State.EATING;
				turnsWaiting[i] = 0;
				self[i].signal();
			}
			else{
				turnsWaiting[i]++;
				if(turnsWaiting[i] > WAITING_BOUND){ // I've had it, I'm starving!
					state[i] = State.STARVING;
				}
			}
		} // TODO should we catch?
		finally{
			lock.unlock();
		}
		
	}

	/**
	 * Grants request (returns) to eat when both chopsticks/forks are available.
	 * Else forces the philosopher to wait()
	 */
	public void pickUp(final int piTID)
	{
		lock.lock();
		try{			
			state[piTID] = State.HUNGRY;
			test(piTID);
			if (state[piTID] != State.EATING){
				try{
					self[piTID].await();
				}catch(InterruptedException e){
					System.err.println(e.getMessage());
				}
			}
		} // TODO should we catch?
		finally{
			lock.unlock();
		}
	}

	/**
	 * When a given philosopher's done eating, they put the chopsticks/forks down
	 * and let others know they are available.
	 */
	public void putDown(final int piTID)
	{
		lock.lock();
		try{			
			state[piTID] = State.THINKING;
			test((piTID+1)%nThinkers);
			test((piTID+(nThinkers-1))%nThinkers);
			
		} // TODO should we catch?
		finally{
			lock.unlock();
		}
	}

	/**
	 * Only one philopher at a time is allowed to philosophy
	 * (while she is not eating).
	 */
	public void requestTalk()
	{
		// ...
	}

	/**
	 * When one philosopher is done talking stuff, others
	 * can feel free to start talking.
	 */
	public void endTalk()
	{
		// ...
	}
}

// EOF

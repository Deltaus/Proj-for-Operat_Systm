package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * A <i>GameMatch</i> groups together player threads of the same
 * ability into fixed-sized groups to play matches with each other.
 * Implement the class <i>GameMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into groups.
 */
public class GameMatch {
    
    /* Three levels of player ability. */
    public static final int abilityBeginner = 1,
	abilityIntermediate = 2,
	abilityExpert = 3;

    private Lock lock;
    private int numPlayersInMatch;
    private ArrayList<Condition> conds;
    private ArrayList<Stack<Integer>> currPlayers;
    private HashMap<Integer, Integer> thread2MatchId;
    private int id = 1;

    /**
     * Allocate a new GameMatch specifying the number of player
     * threads of the same ability required to form a match.  Your
     * implementation may assume this number is always greater than zero.
     */
    public GameMatch (int numPlayersInMatch) {
        this.numPlayersInMatch = numPlayersInMatch;
        lock = new Lock();
        conds = new ArrayList<>();
        currPlayers = new ArrayList<>();
        thread2MatchId = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            currPlayers.add(new Stack<>());
        }
        for (int i = 0; i < 3; i++) {
            conds.add(new Condition(lock));
        }
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
        lock.acquire();
        System.out.println("lock successfully acquired");
        if (ability != abilityBeginner && ability != abilityIntermediate && ability != abilityExpert) {
            lock.release();
            return -1;
        }
        Stack<Integer> stack = currPlayers.get(ability - 1);
	    stack.push(KThread.currentThread().getId());
	    if (stack.size() < numPlayersInMatch) {
	        System.out.println("sleep! ");
	        conds.get(ability - 1).sleep();
        } else {
            boolean status1 = Machine.interrupt().disable();
            System.out.println("wake! ");
	        while (!stack.isEmpty()) {
	            thread2MatchId.put(stack.pop(), id);
            }
	        id++;
            Machine.interrupt().restore(status1);
	        conds.get(ability - 1).wakeAll();
        }
        lock.release();
        return thread2MatchId.get(KThread.currentThread().getId());
    }

    public static void matchTest4 () {
        final GameMatch match = new GameMatch(2);

        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run beg1");
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg1 matched and id is " + r);
                // beginners should match with a match number of 1
                //Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg1.setName("B1");

        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run beg2");
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched and id is " + r);
                // beginners should match with a match number of 1
                //Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg2.setName("B2");

        KThread int1 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run int1");
                int r = match.play(GameMatch.abilityIntermediate);
                System.out.println ("int1 matched and id is " + r);
            }
        });
        int1.setName("I1");

        KThread int2 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run int2");
                int r = match.play(GameMatch.abilityIntermediate);
                System.out.println ("int2 matched and id is " + r);
            }
        });
        int2.setName("I2");

        KThread int3 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run int3");
                int r = match.play(GameMatch.abilityIntermediate);
                System.out.println ("int3 matched and id is " + r);
            }
        });
        int3.setName("I3");

        KThread int4 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run int4");
                int r = match.play(GameMatch.abilityIntermediate);
                System.out.println ("int4 matched and id is " + r);
            }
        });
        int4.setName("I4");

        KThread exp1 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run exp1");
                int r = match.play(GameMatch.abilityExpert);
                System.out.println ("exp1 matched and id is " + r);
            }
        });
        exp1.setName("E1");

        KThread exp2 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run exp2");
                int r = match.play(GameMatch.abilityExpert);
                System.out.println ("exp2 matched and id is " + r);
            }
        });
        exp2.setName("E2");

        KThread beg3 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run beg3");
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg3 matched and id is " + r);
                // beginners should match with a match number of 1
                //Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg3.setName("B3");

        KThread beg4 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run beg4");
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg4 matched and id is " + r);
                // beginners should match with a match number of 1
                //Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg4.setName("B4");

        KThread beg5 = new KThread( new Runnable () {
            public void run() {
                System.out.println("starting run beg5");
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg5 matched and id is " + r);
                // beginners should match with a match number of 1
                //Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg5.setName("B5");


        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        int1.fork();
        exp1.fork();
        beg2.fork();
        int2.fork();
        exp2.fork();
        int3.fork();
        beg3.fork();
        beg4.fork();
        beg5.fork();
        //int4.fork();

//        beg4.join();
        // Assume join is not implemented, use yield to allow other
        // threads to run
        for (int i = 0; i < 100; i++) {
            KThread.currentThread().yield();
        }
    }

    public static void selfTest() {
        matchTest4();
    }
}

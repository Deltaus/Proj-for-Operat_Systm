package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {

    public class IPTEntry {

        public int pid;
        public int vpn;
        //public int spn;
        public boolean used;
        public boolean ispinned;

        public IPTEntry() {
            this.ispinned = false;
            this.pid = -1;
            this.vpn = -1;
            //this.spn = -1;
            this.used = false;
        }

        public IPTEntry(IPTEntry entry) {
            this.pid = entry.pid;
            this.vpn = entry.vpn;
            this.ispinned = entry.ispinned;
            //this.spn = entry.spn;
            this.used = entry.used;
        }

        public IPTEntry(int pid, int vpn, int spn, boolean ispinned, boolean used) {
            this.vpn = vpn;
            this.pid = pid;
            //this.spn = spn;
            this.ispinned = ispinned;
            this.used = used;
        }

    }

	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
		maxSPN = 0;
		freeSwapPages = new LinkedList<>();
        swapLock = new Lock();
		invertedPageTable = new IPTEntry[Machine.processor().getNumPhysPages()];
		for(int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
		    invertedPageTable[i] = new IPTEntry();
        }
		IPTLock = new Lock();
		pinLock = new Lock();
		//clockLock = new Lock();
        //getPageLock = new Lock();
        procLock = new Lock();
        VMLock = new Lock();
        //vmProcLock = new Lock();
        pinCond = new Condition(pinLock);

		processes = new HashMap<>();
		clockHand = 0;
//		circularBuffer = new int[bufferSize];
//		for (int i = 0; i < circularBuffer.length; i++) {
//			circularBuffer[i] = 0;
//		}
//		bufferIndex = 0;
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swapFile.close();
		ThreadedKernel.fileSystem.remove("swapFile");
		super.terminate();
	}

	//Inverted Page Table
//	public static void updateIPT(int ppn, int pid) {
//		invertedPageTable.put(ppn, pid);
//	}
//
//	public static int getIPTPid(int ppn) {
//		return invertedPageTable.get(ppn);
//	}
//
//	public static void delIPTPid(int ppn) {
//		invertedPageTable.remove(ppn);
//	}

	//handle processes
	public static void addProcess(int pid, VMProcess proc) {
	    procLock.acquire();
		processes.put(pid, proc);
		procLock.release();
	}

	public static void rmvProcess(int pid) {
	    procLock.acquire();
	    processes.remove(pid);
	    procLock.release();
    }

	public static VMProcess getProcess(int ppn) {
	    IPTLock.acquire();
	    int pid = invertedPageTable[ppn].pid;
	    IPTLock.release();
	    procLock.acquire();
	    VMProcess vp = processes.get(pid);
	    procLock.release();
		return vp;
	}

//	public static void delProcess(int pid) {
//		processes.remove(pid);
//	}

    //Handle IPT
    public static boolean isUsed(int ppn) {
	    IPTLock.acquire();
	    boolean status =  invertedPageTable[ppn].used;
	    IPTLock.release();
	    return status;
    }

    public static boolean isPinned(int ppn) {
	    IPTLock.acquire();
	    boolean isPn = invertedPageTable[ppn].ispinned;
	    IPTLock.release();
	    return isPn;
    }

    public static int ppn2vpn(int ppn) {
	    IPTLock.acquire();
	    int vpn =  invertedPageTable[ppn].vpn;
	    IPTLock.release();
	    return vpn;
    }

    public static void updateVPNIPT(int ppn, int vpn) {
	    IPTLock.acquire();
	    invertedPageTable[ppn].vpn = vpn;
	    IPTLock.release();
    }

    public static void updatePIDIPT(int ppn, int pid) {
	    IPTLock.acquire();
	    invertedPageTable[ppn].pid = pid;
	    IPTLock.release();
    }

    public static void pinPage(int ppn) {
	    //IPTLock.acquire();
	    //pinLock.acquire();
	    invertedPageTable[ppn].ispinned = true;
	    //pinLock.release();
        //IPTLock.release();
    }

    public static void unpinPage(int ppn) {
	    //IPTLock.acquire();
	    invertedPageTable[ppn].ispinned = false;
        //IPTLock.release();
        //pinLock.acquire();
	    //pinCond.wakeAll();
	    //pinLock.release();
    }

    public static void setIPTUsed(int ppn) {
	    IPTLock.acquire();
	    invertedPageTable[ppn].used = true;
	    IPTLock.release();
    }

    public static void resetIPTUsed(int ppn) {
	    IPTLock.acquire();
	    invertedPageTable[ppn].used = false;
	    IPTLock.release();
    }

	//TODO ClockAlgorithm
//	public void setCircularBuffer(int ppn) {
//		circularBuffer[ppn] = 1;
//	}
//
//	public void resetCircularBuffer(int ppn) {
//		circularBuffer[ppn] = 0;
//	}

	public static int getVictim() {
	    //pinLock.acquire();
	    clockHand = (clockHand + 1) % Machine.processor().getNumPhysPages();
        int initPos = clockHand;
        //IPTLock.acquire();
	    while (isPinned(clockHand)) {
	        clockHand = (clockHand + 1) % Machine.processor().getNumPhysPages();
	        if (clockHand == initPos) {
	            pinCond.sleep();
            }
        }
        //IPTLock.release();
        //pinLock.release();
	    return clockHand;
	}


	//Handle swap file
    private static int allocateFreeSwapPage() {

	    int spn;

        //swapLock.acquire();
	    if (!freeSwapPages.isEmpty()) {
	        spn = freeSwapPages.pop();
        }
        else {
	        spn = maxSPN++;
        }
        //swapLock.release();

        return spn;
    }

    public static boolean recycleSwapPage(int spn) {

	    //swapLock.acquire();
	    if (spn < 0) {
	        //swapLock.release();
	        return false;
        }
        freeSwapPages.addLast(spn);
	    //swapLock.release();

	    return true;
    }

	private static boolean writeSwapFile(int ppn, int spn) {

	    //swapLock.acquire();
        byte[] memory = Machine.processor().getMemory();
        int writeByte = 0;
        int phyAddr = ppn * pageSize;
        int swpAddr = spn * pageSize;

        swapFile.seek(swpAddr);
        writeByte = swapFile.write(memory, phyAddr, pageSize);
        if (writeByte != pageSize) {
            //swapLock.release();
            return false;
        }
        //swapLock.release();

        return true;
	}

	private static boolean readSwapFile(int ppn, int spn) {

	    //swapLock.acquire();
        byte[] memory = Machine.processor().getMemory();
        int phyAddr = ppn * pageSize;
        int swpAddr = spn * pageSize;
        int readByte = 0;
        swapFile.seek(swpAddr);

        readByte = swapFile.read(memory, phyAddr, pageSize);
        if (readByte != pageSize) {
            //swapLock.release();
            return false;
        }
        //swapLock.release();

        return true;
    }

    public static boolean pageIn(int ppn, int spn) {

	    swapLock.acquire();
	    boolean readSW = readSwapFile(ppn, spn);

	    if (!readSW) {
            Lib.debug(dbgVM,"\tReading from swap file failed!\n");
            swapLock.release();
            return false;
        }
	    recycleSwapPage(spn);
	    swapLock.release();

	    return true;
    }

    public static int pageOut(int ppn) {

	    swapLock.acquire();
        int spn = allocateFreeSwapPage();
        boolean writeSW = writeSwapFile(ppn, spn);

        if (!writeSW) {
            Lib.debug(dbgVM,"\tWriting to swap file failed!\n");
            swapLock.release();
            return -1;
        }
        swapLock.release();

        return spn;
    }

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

    private static OpenFile swapFile;

    private static int maxSPN;

    private static LinkedList<Integer> freeSwapPages;

    private static Lock swapLock;

	private static IPTEntry[] invertedPageTable;

	private static Lock IPTLock;

	public static Lock pinLock;

	//public static Lock clockLock;

	//public static Lock getPageLock;

	private static Lock procLock;

	public static Lock VMLock;

	public static Condition pinCond;

	private static HashMap<Integer, VMProcess> processes;

    private static final int pageSize = Processor.pageSize;

	private static int clockHand;
}

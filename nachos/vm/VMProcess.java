package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.HashMap;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		VMKernel.addProcess(PID, this);
		vpn2spn = new HashMap<>();
		//pageTableLock = new Lock();
		//v2sLock = new Lock();
	}

	@Override
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
	    VMKernel.VMLock.acquire();
        Lib.assertTrue(offset >= 0 && length >= 0
                && offset + length <= data.length);

        int amountRead = 0;
        byte[] memory = Machine.processor().getMemory();
        int totalByteNum = numPages * pageSize;

        if (vaddr < 0 || vaddr >= totalByteNum) {
            VMKernel.VMLock.release();
            return 0;
        }

        int vpn = Processor.pageFromAddress(vaddr);
        //VMKernel.getPageLock.acquire();
        if (!isValid(vpn)) {
            handlePageFault(vaddr);
        }
        int ofs = Processor.offsetFromAddress(vaddr);
        int ppn = getPPN(vpn);
        int paddr = ppn * pageSize + ofs;
        int amount = Math.min(length, pageSize - ofs);

        if (paddr < 0 || paddr >= memory.length) {
            //VMKernel.getPageLock.release();
            VMKernel.VMLock.release();
            return 0;
        }

        VMKernel.pinLock.acquire();
        VMKernel.pinPage(ppn);
        VMKernel.pinLock.release();
        //VMKernel.getPageLock.release();
        setUsed(vpn, true);
        VMKernel.setIPTUsed(ppn);
        System.arraycopy(memory, paddr, data, offset, amount);
        amountRead += amount;
        length -= amount;
        VMKernel.pinLock.acquire();
        VMKernel.unpinPage(ppn);
        VMKernel.pinCond.wake();
        VMKernel.pinLock.release();

        if (length > 0) {
            int numPageInNeed = (length % pageSize == 0 ) ? (length / pageSize) : (length / pageSize + 1);
            while (numPageInNeed > 0) {
                vpn++;
                if (vpn >= numPages) {
                    VMKernel.VMLock.release();
                    return -1;
                }
                //VMKernel.getPageLock.acquire();
                if (!isValid(vpn)) {
                    handlePageFault(vpn * pageSize);
                }
                offset += amount;
                ppn = getPPN(vpn);
                VMKernel.pinLock.acquire();
                VMKernel.pinPage(ppn);
                VMKernel.pinLock.release();
                //VMKernel.getPageLock.release();
                setUsed(vpn,true);
                VMKernel.setIPTUsed(ppn);
                paddr = ppn * pageSize;
                amount = Math.min(length, pageSize);
                System.arraycopy(memory, paddr, data, offset, amount);
                amountRead += amount;
                numPageInNeed--;
                VMKernel.pinLock.acquire();
                VMKernel.unpinPage(ppn);
                VMKernel.pinCond.wake();
                VMKernel.pinLock.release();
            }
        }
        VMKernel.VMLock.release();

        return amountRead;
    }


    @Override
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
	    VMKernel.VMLock.acquire();
        Lib.assertTrue(offset >= 0 && length >= 0
                && offset + length <= data.length);

        int amountWritten = 0;
        byte[] memory = Machine.processor().getMemory();
        int totalByteNum = numPages * pageSize;

        if (vaddr < 0 || vaddr >= totalByteNum) {
            VMKernel.VMLock.release();
            return 0;
        }

        int vpn = Processor.pageFromAddress(vaddr);

        //VMKernel.getPageLock.acquire();
        if (!isValid(vpn)) {
            handlePageFault(vaddr);
        }

        if (isRdOly(vpn)) {
            //VMKernel.getPageLock.release();
            VMKernel.VMLock.release();
            return -1;
        }

        int ofs = Processor.offsetFromAddress(vaddr);
        int ppn = getPPN(vpn);
        int paddr = ppn * pageSize + ofs;
        int amount = Math.min(length, pageSize - ofs);

        if (paddr < 0 || paddr >= memory.length) {
            //VMKernel.getPageLock.release();
            VMKernel.VMLock.release();
            return 0;
        }

        VMKernel.pinLock.acquire();
        VMKernel.pinPage(ppn);
        VMKernel.pinLock.release();
        //VMKernel.getPageLock.release();
        setDty(vpn,true);
        setUsed(vpn,true);
        VMKernel.setIPTUsed(ppn);
        System.arraycopy(data, offset, memory, paddr, amount);
        amountWritten += amount;
        length -= amount;
        VMKernel.pinLock.acquire();
        VMKernel.unpinPage(ppn);
        VMKernel.pinCond.wake();
        VMKernel.pinLock.release();

        if (length > 0) {
            int numPageInNeed = (length % pageSize == 0 ) ? (length / pageSize) : (length / pageSize + 1);
            while (numPageInNeed > 0) {
                vpn++;
                //VMKernel.getPageLock.acquire();
                if (!isValid(vpn)) {
                    handlePageFault(vpn * pageSize);
                }
                if (vpn >= numPages || isRdOly(vpn)) {
                    //VMKernel.getPageLock.release();
                    VMKernel.VMLock.release();
                    return -1;
                }
                offset += amount;
                ppn = getPPN(vpn);
                VMKernel.pinLock.acquire();
                VMKernel.pinPage(ppn);
                VMKernel.pinLock.release();
                //VMKernel.getPageLock.release();
                paddr = ppn * pageSize;
                amount = Math.min(length, pageSize);
                System.arraycopy(data, offset, memory, paddr, amount);
                setDty(vpn,true);
                setUsed(vpn,true);
                VMKernel.setIPTUsed(ppn);
                amountWritten += amount;
                numPageInNeed--;
                VMKernel.pinLock.acquire();
                VMKernel.unpinPage(ppn);
                VMKernel.pinCond.wake();
                VMKernel.pinLock.release();
            }
        }
        VMKernel.VMLock.release();
        return amountWritten;
    }

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	@Override
	protected boolean loadSections() {
	    // TODO: check it is right or not
        VMKernel.VMLock.acquire();
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		VMKernel.VMLock.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
	    //UserKernel.lock.acquire();
        for(int i = 0; i < numPages; i++) {
            int ppn = getPPN(i);
            if (ppn == -1) {
                UserKernel.recyclePages(ppn);
                continue;
            }
            VMKernel.updatePIDIPT(ppn, -1);
            VMKernel.updateVPNIPT(ppn, -1);
            VMKernel.resetIPTUsed(ppn);
            VMKernel.pinLock.acquire();
            VMKernel.unpinPage(ppn);
            VMKernel.pinCond.wake();
            VMKernel.pinLock.release();
            if (v2sContain(i)) {
                VMKernel.recycleSwapPage(getSPN(i));
                rmvSPN(i);
            }
            UserKernel.recyclePages(ppn);
        }
        pageTable = null;
        System.out.println("Process " + this.PID + " unloaded.");
        VMKernel.rmvProcess(PID);
        //UserKernel.lock.release();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
            case Processor.exceptionPageFault:
                handlePageFault(processor.readRegister(Processor.regBadVAddr));
                break;
		    default:
			    super.handleException(cause);
			    break;
		}
	}

	private void handlePageFault(int vaddr) {
	    //VMKernel.VMLock.acquire();
	    //VMKernel.vmProcLock.acquire();
        //UserKernel.lock.acquire();
        //VMKernel.faultLock.acquire();
        int vpnNeed = Processor.pageFromAddress(vaddr);
        int ppn = -1;
        byte[] memory = Machine.processor().getMemory();
        //VMKernel.clockLock.acquire();
        if (!UserKernel.isFreePagelistEmpty()) {
            int[] allocatedPage = UserKernel.allocatePages(1);
            ppn = allocatedPage[0];
        }
        else {
            while (true) {
                int victim = VMKernel.getVictim();
                VMProcess victimProc = VMKernel.getProcess(victim);
                int procVpn = VMKernel.ppn2vpn(victim);
                if (VMKernel.isUsed(victim)) {
                    VMKernel.resetIPTUsed(victim);
                    victimProc.setUsed(procVpn,false);
                }
                else if (victimProc.isDty(procVpn)) {
                    int spn = VMKernel.pageOut(victim);
                    if (spn == -1) {
                        Lib.debug(dbgVM,"\tClock failed!\n");
                        ppn = -1;
                        break;
                    }
                    victimProc.addSpn(procVpn, spn);
                    victimProc.setValid(procVpn,false);
                    VMKernel.updatePIDIPT(victim, -1);
                    VMKernel.updateVPNIPT(victim, -1);
                    ppn = victim;
                    break;
                }
                else {
                    victimProc.setValid(procVpn,false);
                    VMKernel.updatePIDIPT(victim, -1);
                    VMKernel.updateVPNIPT(victim, -1);
                    ppn = victim;
                    break;
                }
            }
        }
        //VMKernel.clockLock.release();
        if (ppn == -1) {
            Lib.debug(dbgVM,"\tPage fault failed!\n");
            //VMKernel.VMLock.release();
            //VMKernel.vmProcLock.release();
            //UserKernel.lock.release();
            //VMKernel.faultLock.release();
            return;
        }

        int vpn0 = Processor.pageFromAddress(vaddr);
        if (v2sContain(vpn0)) {
            int spn0 = getSPN(vpn0);
            VMKernel.pageIn(ppn, spn0);
            VMKernel.updateVPNIPT(ppn, vpn0);
            VMKernel.updatePIDIPT(ppn, PID);
            setPPN(vpn0, ppn);
            setValid(vpn0, true);
            rmvSPN(vpn0);
            //VMKernel.VMLock.release();
            //VMKernel.vmProcLock.release();
            //UserKernel.lock.release();
            //VMKernel.faultLock.release();
            return;
        }

        // Page fault from the coff file
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                    + " section (" + section.getLength() + " pages)");

            boolean isread = section.isReadOnly();

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                if (vpn != vpnNeed) {
                    continue;
                }
                setPPN(vpn, ppn);
                setRdOly(vpn, isread);
                section.loadPage(i, getPPN(vpn));
                setValid(vpn, true);
                VMKernel.updateVPNIPT(ppn, vpn);
                VMKernel.updatePIDIPT(ppn, PID);
                //VMKernel.VMLock.release();
                //VMKernel.vmProcLock.release();
                //UserKernel.lock.release();
                //VMKernel.faultLock.release();
                return;
            }
        }
        // Page fault on a stack page
        // Zero fill
        setPPN(vpnNeed, ppn);
        int paddr = ppn * pageSize;
        for (int i = 0; i < pageSize; i++) {
            memory[paddr] = (byte)0;
            paddr++;
        }
        setValid(vpnNeed, true);
        VMKernel.updateVPNIPT(ppn, vpnNeed);
        VMKernel.updatePIDIPT(ppn, PID);
        //VMKernel.VMLock.release();
        //VMKernel.vmProcLock.release();
        //UserKernel.lock.release();
        //VMKernel.faultLock.release();
    }

    //Handle vpn2spn
    public void addSpn(int vpn, int spn) {
	    //v2sLock.acquire();
	    vpn2spn.put(vpn, spn);
	    //v2sLock.release();
    }

    public boolean v2sContain(int vpn) {
	    //v2sLock.acquire();
	    boolean cont = vpn2spn.containsKey(vpn);
	    //v2sLock.release();
	    return cont;
    }

    public int getSPN(int vpn) {
	    //v2sLock.acquire();
	    int spn = vpn2spn.get(vpn);
	    //v2sLock.release();
	    return spn;
    }

    public void rmvSPN(int vpn) {
	    //v2sLock.acquire();
	    vpn2spn.remove(vpn);
	    //v2sLock.release();
    }

    //Handle PageTable
    ///read
    public int getPPN(int vpn) {
	    //pageTableLock.acquire();
	    int ppn = pageTable[vpn].ppn;
	    //pageTableLock.release();
	    return ppn;
    }

    public boolean isValid(int vpn) {
        //pageTableLock.acquire();
        boolean isVld = pageTable[vpn].valid;
        //pageTableLock.release();
        return isVld;
    }

    public boolean isRdOly(int vpn) {
        //pageTableLock.acquire();
        boolean ro = pageTable[vpn].readOnly;
        //pageTableLock.release();
        return ro;
    }

    public boolean isUsed(int vpn) {
        //pageTableLock.acquire();
        boolean isUsd = pageTable[vpn].used;
        //pageTableLock.release();
        return isUsd;
    }

    public boolean isDty(int vpn) {
        //pageTableLock.acquire();
        boolean isDy = pageTable[vpn].dirty;
        //pageTableLock.release();
        return isDy;
    }
    ///set
    public void setPPN(int vpn, int ppn) {
        //pageTableLock.acquire();
        pageTable[vpn].ppn = ppn;
        //pageTableLock.release();
    }

    public void setValid(int vpn, boolean val) {
        //pageTableLock.acquire();
        pageTable[vpn].valid = val;
        //pageTableLock.release();
    }

    public void setRdOly(int vpn, boolean val) {
        //pageTableLock.acquire();
        pageTable[vpn].readOnly = val;
        //pageTableLock.release();
    }

    public void setUsed(int vpn, boolean val) {
        //pageTableLock.acquire();
        pageTable[vpn].used = val;
        //pageTableLock.release();
    }

    public void setDty(int vpn, boolean val) {
        //pageTableLock.acquire();
        pageTable[vpn].dirty = val;
        //pageTableLock.release();
    }

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	//private Lock pageTableLock;

	private HashMap<Integer, Integer> vpn2spn;

	//private String name;

	//private Lock v2sLock;
}

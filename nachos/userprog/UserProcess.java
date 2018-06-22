package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.vm.*;

import javax.jws.soap.SOAPBinding;
import java.io.EOFException;

import java.util.*;


/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */

public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		childrenExitState = new HashMap<>();
		childrenMap = new HashMap<>();
		lock = new Lock();
		cond = new Condition(lock);
		this.PID = UserKernel.allocatePID();
		UserKernel.increNumProcess();
		idleFd = new Stack<>();
		for (int i = 2; i < maxFileNumber; i++) {
		    idleFd.push(i);
		}
        openFiles = new OpenFile[maxFileNumber];
		openFiles[0] = UserKernel.console.openForReading();
		openFiles[1] = UserKernel.console.openForWriting();
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		new UThread(this).setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		int amountRead = 0;
		byte[] memory = Machine.processor().getMemory();
		int totalByteNum = numPages * pageSize;

		if (vaddr < 0 || vaddr >= totalByteNum)
			return 0;

		int vpn = Processor.pageFromAddress(vaddr);
		int ofs = Processor.offsetFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int paddr = ppn * pageSize + ofs;
		int amount = Math.min(length, pageSize - ofs);

		if (paddr < 0 || paddr >= memory.length) {
			return 0;
		}

		System.arraycopy(memory, paddr, data, offset, amount);
		amountRead += amount;
		length -= amount;

		if (length > 0) {
			int numPageInNeed = (length % pageSize == 0 ) ? (length / pageSize) : (length / pageSize + 1);
			while (numPageInNeed > 0) {
				vpn++;
				if (vpn >= numPages) {
					return -1;
				}
				offset += amount;
				ppn = pageTable[vpn].ppn;
				paddr = ppn * pageSize;
				amount = Math.min(length, pageSize);
				System.arraycopy(memory, paddr, data, offset, amount);
				amountRead += amount;
				numPageInNeed--;
			}
		}
		return amountRead;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		int amountWritten = 0;
		byte[] memory = Machine.processor().getMemory();
		int totalByteNum = numPages * pageSize;

		if (vaddr < 0 || vaddr >= totalByteNum)
			return 0;

		int vpn = Processor.pageFromAddress(vaddr);
		if (pageTable[vpn].readOnly) {
		    return -1;
        }
		int ofs = Processor.offsetFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int paddr = ppn * pageSize + ofs;
		int amount = Math.min(length, pageSize - ofs);

		if (paddr < 0 || paddr >= memory.length) {
			return 0;
		}

		System.arraycopy(data, offset, memory, paddr, amount);
		amountWritten += amount;
		length -= amount;

		if (length > 0) {
			int numPageInNeed = (length % pageSize == 0 ) ? (length / pageSize) : (length / pageSize + 1);
			while (numPageInNeed > 0) {
				vpn++;
				if (vpn >= numPages || pageTable[vpn].readOnly) {
					return -1;
				}
				offset += amount;
				ppn = pageTable[vpn].ppn;
				paddr = ppn * pageSize;
				amount = Math.min(length, pageSize);
				System.arraycopy(data, offset, memory, paddr, amount);
				amountWritten += amount;
				numPageInNeed--;
			}
		}
		return amountWritten;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		UserKernel.lock.acquire();
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			UserKernel.lock.release();
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			UserKernel.lock.release();
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				UserKernel.lock.release();
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			UserKernel.lock.release();
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections()) {
			UserKernel.lock.release();
			return false;
		}

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		UserKernel.lock.release();
		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		//UserKernel.lock.acquire();
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			//UserKernel.lock.release();
			return false;
		}

		// get allocated pages and construct page table
		int[] allocatedPages = UserKernel.allocatePages(numPages);
		if (allocatedPages == null) {
			//UserKernel.lock.release();
			return false;
		}
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
			pageTable[i] = new TranslationEntry(i, allocatedPages[i], true, false, false, false);

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			boolean isread = section.isReadOnly();

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
                pageTable[vpn].readOnly = isread;
				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}
		//UserKernel.lock.release();

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.lock.acquire();
		for(int i = 0; i < numPages; i++) {
			UserKernel.recyclePages(pageTable[i].ppn);
		}
		pageTable = null;
		UserKernel.lock.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
        if (this.PID != 0) {
            // attention please
            return -1;
        }
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.
		System.out.println(status);

        // Close all the files associated with the process
        for (int i = 0; i < maxFileNumber; i++) {
            OpenFile file = openFiles[i];
            if (file == null) {
                continue;
            }
            file.close();
            openFiles[i] = null;
            idleFd.push(i);
        }
        // Give back the physical pages you allocated to the user process
        unloadSections();
        coff.close();

        // set all children's parent to be null
        for (Integer childPID : childrenMap.keySet()) {
            childrenMap.get(childPID).parent = null;
        }

        // Write to the parents data structure about the argument a0 and wake up
        // the parent if it has called join on the child that is exiting
        if (parent != null) {
            UserKernel.processLock.acquire();
            if (normalExit) {
                parent.childrenExitState.put(PID, status);
            } else {
                parent.childrenExitState.put(PID, null);
            }
            UserKernel.processLock.release();
            lock.acquire();
            cond.wake();
            lock.release();
        }

        // If its the last process call Kernel.kernel.terminate()
        UserKernel.decreNumProcess();
        if (UserKernel.numRunProcess == 0) {
            Kernel.kernel.terminate();
        } else { // KThread.finish() if its not the last process so it chooses something else to run
            KThread.finish();
        }

		return 0;
	}

	private int handleExec(int fileAddr, int argc, int argvAddr) {
	    String fileName = readVirtualMemoryString(fileAddr, 256);
	    if (fileName == null || !fileName.contains(".coff")) {
	        return -1;
        }
        if (argc < 0) {
	        return -1;
        }
        String[] args = new String[argc];
        for (int i = 0; i < argc; i++) {
            byte[] strPtr = new byte[4];
            if (readVirtualMemory(argvAddr, strPtr) != 4) {
                return -1;
            }
            argvAddr += 4;
            int vaddr = Lib.bytesToInt(strPtr, 0);
	        String arg = readVirtualMemoryString(vaddr, 256);
	        if (arg == null) {
	            return -1;
            }
	        args[i] = arg;
        }
        UserProcess child = newUserProcess();
	    boolean res = child.execute(fileName, args);
	    if (res == false) {
	        return -1;
        }
        child.parent = this;
        childrenMap.put(child.PID, child);
        return child.PID;
    }

    private int handleJoin(int PID, int statusPtr) {
	    if (!childrenMap.containsKey(PID)) {
	        return -1;
        }

        if (childrenExitState.containsKey(PID)) {
            Integer state = childrenExitState.get(PID);
            childrenMap.remove(PID);
            if (state == null) {
                return 0;
            }
            byte[] data = Lib.bytesFromInt(state);
            writeVirtualMemory(statusPtr, data);
            return 1;
        }

        UserProcess child = childrenMap.get(PID);
	    child.lock.acquire();
	    child.cond.sleep();
	    child.lock.release();
	    if (!childrenExitState.containsKey(PID)) {
	        return -1;
        }
        childrenMap.remove(PID);
        Integer state = childrenExitState.get(PID);
        if (state == null) {
            return 0;
        }
        byte[] data = Lib.bytesFromInt(state);
        writeVirtualMemory(statusPtr, data);
        return 1;
    }

	private int handleOpen(int vaddr) {
	    String name = readVirtualMemoryString(vaddr, 256);
	    if (name == null) {
	        return -1;
        }
	    OpenFile file = ThreadedKernel.fileSystem.open(name, false);
        if (file == null) {
            return -1;
        }
        if (idleFd.isEmpty()) {
            return -1;
        }
        int fileDescriptor = idleFd.pop();
        openFiles[fileDescriptor] = file;
		return fileDescriptor;
	}


    private int handleCreate(int vaddr) {
        String name = readVirtualMemoryString(vaddr, 256);
        if (name == null) {
            return -1;
        }
        OpenFile file = ThreadedKernel.fileSystem.open(name, false);
        if (file != null) {
            if (idleFd.isEmpty()) {
                return -1;
            }
            int fileDescriptor = idleFd.pop();
            openFiles[fileDescriptor] = file;
            return fileDescriptor;
        }
        file = ThreadedKernel.fileSystem.open(name, true);
        if (idleFd.isEmpty()) {
            return -1;
        }
        int fileDescriptor = idleFd.pop();
        openFiles[fileDescriptor] = file;
        return fileDescriptor;
    }

    private int handleRead(int fd, int buffer, int count) {
	    if (fd < 0 || fd >= maxFileNumber) {
	        return -1;
        }
        if (buffer <= 0 || buffer >= numPages * pageSize) {
            return -1;
        }
        if (count < 0) {
	        return -1;
        }
        if (buffer + count >= numPages * pageSize) {
            return -1;
        }

        OpenFile file = openFiles[fd];
	    if (file == null) {
	        return -1;
        }
        int num = 0;
        while (count > 0) {
	        int length = Math.min(count, pageSize);
	        int readBytes = file.read(dummy, 0, length);
	        if (readBytes < 0) {
                return -1;
            }
            int writeBytes = writeVirtualMemory(buffer, dummy, 0, readBytes);
	        if (writeBytes == -1) {
	            return -1;
            }
            buffer += writeBytes;
	        num += writeBytes;
	        count -= writeBytes;
	        if (writeBytes < pageSize || writeBytes == 0) {
	            break;
            }
        }
	    return num;
    }

    private int handleWrite(int fd, int buffer, int count) {
        if (fd < 0 || fd >= maxFileNumber) {
            return -1;
        }
        if (buffer <= 0 || buffer >= numPages * pageSize) {
            return -1;
        }
        if (count < 0) {
            return -1;
        }
        if (buffer + count >= numPages * pageSize) {
            return -1;
        }

        OpenFile file = openFiles[fd];
        if (file == null) {
            return -1;
        }

        int num = 0;
        while (count > 0) {
            int length = Math.min(count, pageSize);
            int readBytes = readVirtualMemory(buffer, dummy, 0, length);
            if (readBytes < 0) {
                return -1;
            }
            int writeBytes = file.write(dummy, 0, readBytes);
            if (writeBytes == -1) {
                return -1;
            }
            buffer += writeBytes;
            num += writeBytes;
            count -= writeBytes;
            if (writeBytes < length) {
                return -1;
            }
        }
        return num;
    }

    private int handleClose(int fd) {
	    if (fd < 0 || fd >= maxFileNumber) {
	        return -1;
        }
        OpenFile file = openFiles[fd];
	    if (file == null) {
	        return -1;
        }
        file.close();
	    openFiles[fd] = null;
        idleFd.push(fd);
	    return 0;
    }

    private int handleUnlink(int vaddr) {
        String name = readVirtualMemoryString(vaddr, 256);
        if (name == null) {
            return -1;
        }
        if (!ThreadedKernel.fileSystem.remove(name)) {
            return -1;
        }
        return 0;
    }


	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallExit:
                return handleExit(a0);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallJoin:
                return handleJoin(a0, a1);
            case syscallOpen:
                return handleOpen(a0);
            case syscallCreate:
                return handleCreate(a0);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
		    normalExit = false;
		    handleExit(0);
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private OpenFile[] openFiles;

	private static final int maxFileNumber = 16;

	private Stack<Integer> idleFd;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private byte[] dummy = new byte[pageSize];

	private static final char dbgProcess = 'a';

	public int PID;

	private HashMap<Integer, UserProcess> childrenMap;

	public HashMap<Integer, Integer> childrenExitState;

	public Condition cond;

	public Lock lock;

	public UserProcess parent = null;

    private boolean normalExit = true;
}

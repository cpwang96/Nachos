package nachos.userprog;
import java.util.*;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;

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
		fileTable = new OpenFile[16]; // filetable to store 16 different file descriptors
		for (int i = 0; i < fileTable.length; i++) {
			fileTable[i] = null;
		}
		fileTable[0] = UserKernel.console.openForReading(); // stdin
		fileTable[1] = UserKernel.console.openForWriting(); // stdout	
		// acquire the lock for process id
		UserKernel.idLock.acquire();
		this.processID = UserKernel.processID; // assign the process id to current process
		UserKernel.processID++; // increment the global process id
		UserKernel.runningProcessMap.put(this.processID, this); // put current process into the running map
		UserKernel.idLock.release();
		// release the lock for process id
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

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= pageSize * numPages)
			return 0;

		int remain = length; // to store the remaining size, update inside while loop
		int result = 0; // to store how many bytes transferred
		int cur = offset; // to store the current offset
		int curVaddr = vaddr; // to store the current virtual address
		//System.out.println("Start to read data from virtual memory!");
		while (remain > 0) {
			// get the virtual page number
			int vpn = Processor.pageFromAddress(curVaddr);
			int page_offset = Processor.offsetFromAddress(curVaddr);

			// make sure vpn is valid
			if (vpn < 0 || vpn >= pageTable.length) {
				return result;
			}
			// make sure pageTable entry is valid
			if (pageTable[vpn] == null) {
				return result;
			}
			// get the offset of the page
			int ppn = pageTable[vpn].ppn;
			//System.out.println("Physical Page number is: " + ppn);

			int paddr = ppn * pageSize + page_offset;
			if (paddr < 0 || paddr >= memory.length) {
				return result;
			}
			int amount = Math.min(remain, pageSize - page_offset);
			System.arraycopy(memory, paddr, data, cur, amount);
			remain = remain - amount;
			result = result + amount;
			cur = cur + amount;
			curVaddr = curVaddr + amount;
		}
		return result;
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

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= numPages * pageSize)
			return 0;
		int remain = length;
		int result = 0;
		int cur = offset;
		int curVaddr = vaddr;
		while (remain > 0) {
			// get the virtual page number
			int vpn = Processor.pageFromAddress(curVaddr);
			// get the offset of the page
			int page_offset = Processor.offsetFromAddress(curVaddr);
			if (vpn < 0 || vpn >= pageTable.length) {
				return result;
			}
			if (pageTable[vpn] == null) {
				return result;
			}
			if (pageTable[vpn].readOnly) {
				return result;
			}
			int ppn = pageTable[vpn].ppn;
			//System.out.println("Physical Page Number is: " + ppn);

			int paddr = ppn * pageSize + page_offset;
			if (paddr < 0 || paddr >= memory.length) {
				return result;
			}
			int amount = Math.min(remain, pageSize - page_offset);
			System.arraycopy(data, cur, memory, paddr, amount);
			remain = remain - amount;
			result = result + amount;
			cur = cur + amount;
			curVaddr = curVaddr + amount;
		}

		return result;
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
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
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
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

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
		// if the number of pages needed is larger than free pages
		// return
		System.out.println("Calling loadSections inside UserProcess!");
		if (numPages > UserKernel.freePages.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		UserKernel.lock.acquire();
		// allocate pageTable
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			int ppn = UserKernel.freePages.remove();
			pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false);
		}
		UserKernel.lock.release();

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				if (vpn < 0 || vpn >= pageTable.length) {
					return false;
				}
				// find the correct vpn inside the pageTable and get the mapped ppn
				int ppn = pageTable[vpn].ppn;
				pageTable[vpn].readOnly = section.isReadOnly();
				section.loadPage(i, ppn);
			}
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		if (pageTable == null || pageTable.length == 0) {
			return;
		}
		// get the free page lock
		UserKernel.lock.acquire();
		for (int i = 0; i < pageTable.length; i++) {
			if (pageTable[i] != null) {
				UserKernel.freePages.add(pageTable[i].ppn);
				pageTable[i] = null;
			}
		}
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
		if (this.processID != 1) {
			return -1;
		}
		// call halt() when the root process
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}


	private int handleCreate(int fileName) {
		// filename validity check handled by readVitualMemoryString
		String filename = readVirtualMemoryString(fileName, 256);
		if (filename == null || filename.length() == 0) {
			return -1;
		}
		// try to open the file first, if its not null, it means already exist
		OpenFile file = ThreadedKernel.fileSystem.open(filename, false);
		// Open the file operation, not create
		if (file != null) {
			for (int i = 0; i < fileTable.length; i++) {
				if (fileTable[i] == null) {
					fileTable[i] = file;
					return i;
				}
			}
			return -1;
		}
		// the file is null, need to create a new file
		file = ThreadedKernel.fileSystem.open(filename, true);
		System.out.println("<---------Creating new File: " + filename + "----------->");
		if (file == null) {
			return -1;
		}
		for (int i = 0; i < fileTable.length; i++) {
			if (fileTable[i] == null) {
				fileTable[i] = file;
				return i;
			}
		}
		return -1;
	}
	/**
	 * Attempt to write up to count bytes from buffer to the file or stream
	 * referred to by fileDescriptor. write() can return before the bytes are
	 * actually flushed to the file or stream. A write to a stream can block,
	 * however, if kernel queues are temporarily full.
	 *
	 * On success, the number of bytes written is returned (zero indicates nothing
	 * was written), and the file position is advanced by this number. It IS an
	 * error if this number is smaller than the number of bytes requested. For
	 * disk files, this indicates that the disk is full. For streams, this
	 * indicates the stream was terminated by the remote host before all the data
	 * was transferred.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
	 * if a network stream has already been terminated by the remote host.
	 */
	private int handleWrite(int fileDescriptor, int buffer, int size) {
		// invalid fileDescriptor
		if (fileDescriptor < 0 || fileDescriptor >= fileTable.length) {
			return -1;
		}
		// invalid size
		if (size < 0) {
			return -1;
		}
		if (buffer < 0) {
			return -1;
		}
		OpenFile file = fileTable[fileDescriptor];
		if (file == null) {
			return -1;
		}

		byte[] local = new byte[pageSize];
		int curPosition = buffer;
		int curSize = size;
		int result = 0;
		while (curSize > 0) {

			int amount = Math.min(curSize, local.length);
			//System.out.println("Remaning size is " + curSize + ", will read " + amount + " from the vitrtual memory");
			int byteRead = readVirtualMemory(curPosition, local, 0, amount);
			//System.out.println("Byte read from the virtual memory: " + byteRead);
			if (byteRead != amount) {
				return -1;
			}
			int byteWrite = file.write(local, 0, byteRead);
			//System.out.println(byteWrite + " bytes wrtten succesfully!");
			if (byteWrite == -1) {
				return -1;
			}
			curPosition = curPosition + byteRead;
			curSize = curSize - byteWrite;
			result = result + byteWrite;
			//System.out.println("Remaining size to write: " + curSize);
		}
		//System.out.println("Reaches the outside of the while loop!!!!");
		return result;
	}
	/**
	 * Attempt to read up to count bytes into buffer from the file or stream
	 * referred to by fileDescriptor.
	 *
	 * On success, the number of bytes read is returned. If the file descriptor
	 * refers to a file on disk, the file position is advanced by this number.
	 *
	 * It is not necessarily an error if this number is smaller than the number of
	 * bytes requested. If the file descriptor refers to a file on disk, this
	 * indicates that the end of the file has been reached. If the file descriptor
	 * refers to a stream, this indicates that the fewer bytes are actually
	 * available right now than were requested, but more bytes may become available
	 * in the future. Note that read() never waits for a stream to have more data;
	 * it always returns as much as possible immediately.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is read-only or
	 * invalid, or if a network stream has been terminated by the remote host and
	 * no more data is available.
	 */
	private int handleRead(int fileDescriptor, int buffer, int size) {
		if (fileDescriptor < 0 || fileDescriptor >= fileTable.length) {
			return -1;
		}
		if (size < 0) {
			return -1;
		}
		if (buffer < 0) {
			return -1;
		}
		OpenFile file = fileTable[fileDescriptor];
		if (file == null) {
			return -1;
		}
		byte[] local = new byte[pageSize]; // local buffer to store the data
		int curPosition = buffer; // current position 
		int curSize = size; // left reading size
		int result = 0; // return value, how many bytes transferred
		
		while (curSize > 0) {
			int amount = Math.min(curSize, local.length); // determine how many bytes to read
			int byteRead = file.read(local, 0, amount);
			// some errors happened
			if (byteRead == -1) {
				return -1;
			}

			// write to virtual memory from the current position
			int byteWrite = writeVirtualMemory(curPosition, local, 0, byteRead);
			if (byteWrite != byteRead) {
				return -1;
			}
			// increment the current position of the file pointer
			curPosition = curPosition + byteRead;
			// increment the current size 
			curSize = curSize - byteWrite;
			// increment the transferred total amount
			result = result + byteWrite;
		}
		return result;
	}
	/**
	 * Attempt to open the named file and return a file descriptor.
	 *
	 * Note that open() can only be used to open files on disk; open() will never
	 * return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 */
	private int handleOpen(int fileName) {
		String filename = readVirtualMemoryString(fileName, 256);
		//check if the name is valid
		if (filename == null || filename.length() == 0) {
			return -1;
		}
		//open file
		System.out.println("****** Opening the file: " + filename + " ********");
		OpenFile file = ThreadedKernel.fileSystem.open(filename, false);
		// if file is null, some errors happened
		if (file == null) {
			return -1;
		}
		for (int i = 0; i < fileTable.length; i++) {
			if (fileTable[i] == null) {
				fileTable[i] = file;
				return i;
			}
		}
		return -1;
	}

	private int handleClose(int fileDescriptor) {
		if (fileDescriptor < 0 || fileDescriptor >= fileTable.length) {
			return -1;
		}
		if (fileTable[fileDescriptor] == null) {
			return -1;
		}
		fileTable[fileDescriptor].close();
		fileTable[fileDescriptor] = null;
		return 0;
	}
	/**
	 * Close a file descriptor, so that it no longer refers to any file or
	 * stream and may be reused. The resources associated with the file
	 * descriptor are released.
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 */
	private int handleUnlink(int fileName) {
		String filename = readVirtualMemoryString(fileName, 256);
		if (filename == null || filename.length() == 0) {
			return -1;
		}
		// remove file
		boolean status = ThreadedKernel.fileSystem.remove(filename);
		// if failed to remove return -1
		if (status == false) {
			return -1;
		}
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
		this.statusLock.acquire();
		// if status == 1 and the flag is true, called from handleException
		if (status == 1 && this.flag == true) {
			this.exitStatus = 1;
		}
		else {
			this.exitStatus = 0;
			this.statusValue = status; // store the status value for parent
		}
		this.statusLock.release();

		System.out.println("Exiting!!!! The exiting process's id is: " + this.processID);
		// delete all memories
		unloadSections();
		
		// close all file descriptors
		for (int i = 0; i < fileTable.length; i++) {
			handleClose(i);
		}
		// close the coff
		coff.close();
		UserProcess p = this.parent;
		// set the parent process to null if exists
		if (p != null) {
			System.out.println("Parent is not null!!!");
			if (p.sleeping == true) {
				// get the parent process and wake it up
				UserKernel.cvLock.acquire();
				System.out.println("Waking up parent process: " + p.processID);
				UserKernel.cv.wake();
				UserKernel.cvLock.release();
			}
			this.parent = null;
		}
		UserKernel.idLock.acquire();
		UserKernel.runningProcessMap.remove(this.processID);
		UserKernel.idLock.release();
		// last process exiting, call terminate()
		if (UserKernel.runningProcessMap.size() == 0) {
			System.out.println("Last process exiting!!!");
			Kernel.kernel.terminate();
		}
		System.out.println("Finishing thread!!!");
		KThread.finish();
		return 0;
	}
	
/**
 * Execute the program stored in the specified file, with the specified
 * arguments, in a new child process. The child process has a new unique
 * process ID, and starts with stdin opened as file descriptor 0, and stdout
 * opened as file descriptor 1.
 *
 * file is a null-terminated string that specifies the name of the file
 * containing the executable. Note that this string must include the ".coff"
 * extension.
 *
 * argc specifies the number of arguments to pass to the child process. This
 * number must be non-negative.
 *
 * argv is an array of pointers to null-terminated strings that represent the
 * arguments to pass to the child process. argv[0] points to the first
 * argument, and argv[argc-1] points to the last argument.
 *
 * exec() returns the child process's process ID, which can be passed to
 * join(). On error, returns -1.
 */
	private int handleExec(int fileName, int argc, int argv) {
		if (argc < 0) {
			return -1;
		}
	//	if (argv < 0 || argv >= numPages * pageSize) {
	//		return -1;
	//	}
		String filename = readVirtualMemoryString(fileName, 256);
		if (filename == null || filename.length() <= 5) {
			return -1;
		}
		// check if the file name ends with ".coff"
		if (!filename.substring(filename.length() - 5, filename.length()).equals(".coff")) {
			return -1;
		}
		
		// each pointer inside argv[] is 4 bytes
		String[] arguments = new String[argc];
		for (int i = 0; i < argc; i++) {
			byte[] bytes = new byte[4];
			int byteRead = readVirtualMemory(argv + i * 4, bytes);
			// if not 4 bytes
			if (byteRead != bytes.length) {
				return -1;
			}
			arguments[i] = readVirtualMemoryString(Lib.bytesToInt(bytes, 0), 256);
		}

		System.out.println("Creating child process...");
		UserProcess child = newUserProcess();
		System.out.println("Parent process id is: " + this.processID);

		// if could be executed successfully
		if (child.execute(filename, arguments)) {
			System.out.println("Child's process id is: " + child.processID);
			this.childMap.put(child.processID, child);
			child.parent = this;
			System.out.println("Child executing!!!");
			return child.processID;
		}
		System.out.println("Error happening while executing!");
		return -1;
	}
	
/**
 * Suspend execution of the current process until the child process specified
 * by the processID argument has exited. If the child has already exited by the
 * time of the call, returns immediately. When the current process resumes, it
 * disowns the child process, so that join() cannot be used on that process
 * again.
 *
 * processID is the process ID of the child process, returned by exec().
 *
 * status points to an integer where the exit status of the child process will
 * be stored. This is the value the child passed to exit(). If the child exited
 * because of an unhandled exception, the value stored is not defined.
 *
 * If the child exited normally, returns 1. If the child exited as a result of
 * an unhandled exception, returns 0. If processID does not refer to a child
 * process of the current process, returns -1.
 */
	private int handleJoin(int id, int status) {
		// child id does not exist, return
		if (!childMap.containsKey(id)) {
			System.out.println("Parent process " + this.processID + " does not have child process " + id);
			return -1;
		}
		UserProcess childProcess = childMap.get(id);
		// check the exit status of the child
		System.out.println("Child process exit status is: " + childProcess.exitStatus);
		// if 0, means the child process already executed, just return successful
		childProcess.statusLock.acquire();
		
		// the child already exited
		if (childProcess.exitStatus != null && childProcess.exitStatus == 0) {
			childMap.remove(id);
			writeVirtualMemory(status, Lib.bytesFromInt(childProcess.statusValue));
			this.sleeping = false;
			childProcess.statusLock.release();
			return 1;
		}
		
		if (childProcess.exitStatus != null && childProcess.exitStatus == 1) {
			childMap.remove(id);
			this.sleeping = false;
			childProcess.statusLock.release();
			return 0;
		}

		// suspend the parent process if child not exit
		childProcess.statusLock.release();

		UserKernel.cvLock.acquire();
		System.out.println("Sleeping process: " + this.processID);
		this.sleeping = true; // set the sleeping flag to true;
		while(childProcess.exitStatus == null) {
			UserKernel.cv.sleep(); // make the parent process sleep
		}
		UserKernel.cvLock.release(); // when it wakes up, release the lock firstly
		// when wakes up, reset the flag and remove the child
		// the child already exited
		this.sleeping = false; // reset the sleeping flag to false;
		childProcess.statusLock.acquire();
		if (childProcess.exitStatus == 0) {
			childMap.remove(id);
			writeVirtualMemory(status, Lib.bytesFromInt(childProcess.statusValue));
			childProcess.statusLock.release();
			return 1;
		}
		// abnormal exit happens
		childMap.remove(id);
		// if status code is 1, join() would return 0
		// otherwise, normally return 1
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
		case syscallCreate:
			return handleCreate(a0);
		case syscallClose:
			return handleClose(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
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
			this.flag = true;
			handleExit(1); 
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
			break;
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	protected OpenFile[] fileTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private int processID;
	public static boolean sleeping = false;
	private static Lock statusLock = new Lock();
	private static HashMap<Integer, UserProcess> childMap = new HashMap<>();
	public static Integer exitStatus = null; // to store the exit status for the current process
	public static UserProcess parent = null; // store the parent of the current process
	public static Integer statusValue = null;
	public static boolean flag = false;

}

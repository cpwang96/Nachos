package nachos.vm;
import java.util.*;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
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
	protected boolean loadSections() {
		System.out.println("Calling loadSections in VMProcess");
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(-1, -1, false, false, false, false);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		VMKernel.lock.acquire();
		VMKernel.swapLock.acquire();
		VMKernel.frameLock.acquire();
		for (int i = 0; i < pageTable.length; i++) {
			if (pageTable[i] != null && pageTable[i].ppn != -1) {
				VMKernel.freePages.add(pageTable[i].ppn);
				VMKernel.invertedPageTable[pageTable[i].ppn] = null;
			}
			if (pageTable[i] != null && pageTable[i].vpn != -1) {
				VMKernel.freeSwapPages.add(pageTable[i].vpn);
			}
			pageTable[i] = null;
		}
		VMKernel.frameLock.release();
		VMKernel.swapLock.release();
		VMKernel.lock.release();
	}
	
	protected void handlePageFault(int vaddr) {
		System.out.println("Handling page fault!!!");
		System.out.println("Vaddr is: "  + vaddr);
		int vpn = Processor.pageFromAddress(vaddr); // get the vpn
		System.out.println("VPN is" + " " + vpn);
		System.out.println("Page Table size is " + pageTable.length);
		if (vpn < 0 || vpn >= pageTable.length) {
			return;
		}
		boolean stackFlag = true;
		// load free physical pages when the list is not empty (task 1)
		VMKernel.lock.acquire();
		VMKernel.frameLock.acquire();
		VMKernel.positionLock.acquire();
		VMKernel.swapLock.acquire();
		System.out.println("Free pages size is: " + VMKernel.freePages.size());
		// when the free page list is not empty
		if (VMKernel.freePages.size() != 0) {
			System.out.println("There is free page that could be used");
			int ppn = VMKernel.freePages.remove();
			pageTable[vpn].ppn = ppn;
			// UPDATE Physical Page Pool condition
			if (VMKernel.invertedPageTable[ppn] == null) {
				VMKernel.invertedPageTable[ppn] = new PageFrame(pageTable[vpn], this, false);
			}
			else {
				VMKernel.invertedPageTable[ppn].process = this;
				VMKernel.invertedPageTable[ppn].entry = pageTable[vpn];
			}
		}
		// when the free page list is empty
		// evict the page from inverted page table
		// make sure the selected victim is not the current process's pages!!!
		else {
			System.out.println("No free page could be used. Need to do PAGE REPLACEMENT!!!");
			// find a victim using Clock Replacement Algorithm
			int victim = VMKernel.clockReplacement();
			System.out.println("Victim selected-------------------->" + victim);
			// dirty is true, has been modified
			if (VMKernel.invertedPageTable[victim].entry.dirty == true) {
				// do some swap out
				if (VMKernel.freeSwapPages.size() == 0) {
					//int spn = VMKernel.swapFile.tell(); // first position of the swap file
					int spn = VMKernel.lastPosition;
					System.out.println("*******************Writing to the last index of the swapFile: " + spn);
					VMKernel.invertedPageTable[victim].entry.vpn = spn;
					byte[] buffer = new byte[pageSize];
					byte[] memory = Machine.processor().getMemory();
					System.arraycopy(memory, victim * pageSize, buffer, 0, pageSize); // copy physical pages from memory
					VMKernel.swapFile.write(spn * pageSize, buffer, 0, pageSize); // write to swap file
					VMKernel.lastPosition += 1;
				}
				else {
					int spn = VMKernel.freeSwapPages.remove();
					System.out.println("WRITING TO SPN: " + spn + " ..................................");
					VMKernel.invertedPageTable[victim].entry.vpn = spn;
					byte[] buffer = new byte[pageSize];
					byte[] memory = Machine.processor().getMemory();
					System.arraycopy(memory, victim * pageSize, buffer, 0, pageSize); // copy physical pages from memory
					VMKernel.swapFile.write(spn * pageSize, buffer, 0, pageSize); // write to swap file
				}
			}

			// it has not been modified, don't need to swap out, access directly through coff files
			VMKernel.invertedPageTable[victim].entry.valid = false;
			VMKernel.invertedPageTable[victim].entry.ppn = -1;
			pageTable[vpn].ppn = victim;
			VMKernel.invertedPageTable[victim].entry = pageTable[vpn]; // update the ppn <----> TranslationEntry
		}
		VMKernel.lock.release();
		VMKernel.swapLock.release();
		VMKernel.frameLock.release();
		VMKernel.positionLock.release();
		pageTable[vpn].valid = true;

		// page fault loading: dirty bit is false
		if (pageTable[vpn].dirty == false) {
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);
				Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");
				for (int i = 0; i < section.getLength(); i++) {
					int curVPN = section.getFirstVPN() + i;
					if (curVPN == vpn) {
						System.out.println("Found corresponding vpn inside coff file!!!");
						stackFlag = false;
						pageTable[vpn].readOnly = section.isReadOnly();
						section.loadPage(i, pageTable[vpn].ppn);	
					}
				}	
			}

			// other stack/argument page
			if (stackFlag) {
				System.out.println("Zero filling stack/argument pages!!!!");
				int paddr = pageSize * pageTable[vpn].ppn;
				byte[] memory = Machine.processor().getMemory();
				byte[] buffer = new byte[pageSize];
				System.arraycopy(buffer, 0, memory, paddr, buffer.length);
			}
		}
		// load page from the swap file (swap in)
		else {
			VMKernel.swapLock.acquire();
			int spn = pageTable[vpn].vpn; // get spn
			System.out.println("SWAPPING IN FROM SPN: " + spn + "..................................");
			byte[] buffer = new byte[pageSize];
			VMKernel.swapFile.read(spn * pageSize, buffer, 0, pageSize);
			VMKernel.swapLock.release();
			VMKernel.frameLock.acquire();
			VMKernel.freeSwapPages.add(spn);
			VMKernel.frameLock.release();
			byte[] memory = Machine.processor().getMemory();
			int paddr = pageTable[vpn].ppn * pageSize;
			System.arraycopy(buffer, 0, memory, paddr, buffer.length); //?????/IndexOutOfBoundException
		}

		System.out.println("");
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

			// see if it's a pagefault
			if (!pageTable[vpn].valid) {
				handlePageFault(curVaddr);
			}

			if (pageTable[vpn].readOnly) {
				return result;
			}
			int ppn = pageTable[vpn].ppn;
			VMKernel.frameLock.acquire();
			//System.out.println("Physical Page Number is: " + ppn);
			VMKernel.invertedPageTable[ppn].pinned = true;
			int paddr = ppn * pageSize + page_offset;
			if (paddr < 0 || paddr >= memory.length) {
				return result;
			}
			int amount = Math.min(remain, pageSize - page_offset);
			System.arraycopy(data, cur, memory, paddr, amount);
			VMKernel.invertedPageTable[ppn].pinned = false;
			System.out.println("*************UNPINNED ACTIVATED!!!***********");
			VMKernel.cvLock.acquire();
			VMKernel.unpinnedPage.wake();
			VMKernel.cvLock.release();
			VMKernel.frameLock.release();
			remain = remain - amount;
			result = result + amount;
			cur = cur + amount;
			curVaddr = curVaddr + amount;
			pageTable[vpn].dirty = true;
			pageTable[vpn].used = true;
		}

		return result;
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
			if (!pageTable[vpn].valid) {
				handlePageFault(curVaddr);
			}
			// get the offset of the page
			int ppn = pageTable[vpn].ppn;
			VMKernel.frameLock.acquire();
			//System.out.println("Physical Page number is: " + ppn);
			VMKernel.invertedPageTable[ppn].pinned = true;
			int paddr = ppn * pageSize + page_offset;
			if (paddr < 0 || paddr >= memory.length) {
				return result;
			}
			int amount = Math.min(remain, pageSize - page_offset);
			System.arraycopy(memory, paddr, data, cur, amount);
			VMKernel.invertedPageTable[ppn].pinned = false;
			System.out.println("*********************UNPINNED FROM READVIRTUALMEMORY!!!---------->");
			VMKernel.cvLock.acquire();
			VMKernel.unpinnedPage.wake();
			VMKernel.cvLock.release();
			VMKernel.frameLock.release();
			remain = remain - amount;
			result = result + amount;
			cur = cur + amount;
			curVaddr = curVaddr + amount;
			pageTable[vpn].used = true;
		}
		return result;
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
			// how to pass the vpn here
			int vaddr = processor.readRegister(Processor.regBadVAddr); // bad address register
			int vpn = Processor.pageFromAddress(vaddr);
			handlePageFault(vaddr);
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';	
}

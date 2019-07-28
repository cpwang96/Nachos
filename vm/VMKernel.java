package nachos.vm;
import java.util.*;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
class PageFrame{
	public TranslationEntry entry;
	public VMProcess process;
	public Boolean pinned;
	public PageFrame(TranslationEntry entry, VMProcess process, Boolean pinned) {
		this.entry = entry;
		this.process = process;
		this.pinned = pinned;
	}
}
/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
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
		this.invertedPageTable = new PageFrame[Machine.processor().getNumPhysPages()];
		for (int i = 0; i < invertedPageTable.length; i++) {
			invertedPageTable[i] = null;
		}
		this.victimIndex = 0;
		victimLock = new Lock();
		frameLock = new Lock();
		positionLock = new Lock();
		swapLock = new Lock();
		cvLock = new Lock();
		unpinnedPage = new Condition(cvLock);
		swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
		freeSwapPages = new LinkedList<Integer>();
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
		super.terminate();
		ThreadedKernel.fileSystem.remove("swapFile");
	}
	
	public static int clockReplacement() {
		int numPinned = 0;
		while (invertedPageTable[victimIndex].entry.used == true) {
			if (numPinned == invertedPageTable.length) {
				System.out.println("NO FREE PAGES FOR NOW! ALL PINNED!!! GO TO SLEEP...............");
				cvLock.acquire();
				unpinnedPage.sleep();
				cvLock.release();
			}
			if (invertedPageTable[victimIndex].pinned == true) {
				numPinned++;
				continue;
			}
			System.out.println("Checking index " + victimIndex + " in invertedPageTable");
			invertedPageTable[victimIndex].entry.used = false;
			victimIndex = (victimIndex + 1) % invertedPageTable.length;
		}
		int toEvict = victimIndex;
		System.out.println("To-be-evicted: " + toEvict);
		victimIndex = (victimIndex + 1) % invertedPageTable.length;
		return toEvict;
	}
	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	public static PageFrame[] invertedPageTable = null;
	public static HashMap<TranslationEntry, VMProcess> processPagesTracker = new HashMap<>();
	public static int victimIndex;
	public static OpenFile swapFile = null;
	public static LinkedList<Integer> freeSwapPages = null;
	public static int lastPosition = 0;
	public static Lock frameLock = null;
	public static Lock positionLock = null;
	public static Lock victimLock = null;
	public static Lock swapLock = null;
	public static Condition unpinnedPage = null;
	public static Lock cvLock = null;
}

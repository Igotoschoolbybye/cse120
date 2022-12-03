package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;
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
		invertedPageTable = new info[Machine.processor().getNumPhysPages()];
		for(int i = 0; i < Machine.processor().getNumPhysPages(); i++){
			invertedPageTable[i] = new info(null, null, false);
		  }
		victimPage = 0;
		swapNums = 0;
		counter = 0;
		swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
		freePages = new LinkedList<Integer>();
		VMmutex = new Lock();
		conditionVariable = new Condition(VMmutex);
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
		swapFile.close();
        ThreadedKernel.fileSystem.remove("swapFile");
	}

	// dummy variables to make javac smarter
	public class info{
		public info(VMProcess process, TranslationEntry entry, boolean pinning){
			this.process = process;
			this.entry = entry;
			this.pinning = pinning;
		}
		public VMProcess process;
		public TranslationEntry entry;
		public boolean pinning;
	}
	public static int counter;
	private static VMProcess dummy1 = null;
	private static final char dbgVM = 'v';
	public static info invertedPageTable[];
	public static LinkedList<Integer> freePages;
	public static OpenFile swapFile;
	public static int swapNums;
	public static Lock VMmutex;
	public static Condition conditionVariable;
	public static int victimPage;
	
}

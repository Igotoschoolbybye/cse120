package nachos.vm;

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
		pageTable = new TranslationEntry[numPages];
		for(int i = 0; i < numPages; i++){
			pageTable[i] = new TranslationEntry(i, i, false, false, false, false);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	public int evicPageHelper(){
		int ppn = 0;
		while(true){
			if(VMKernel.invertedPageTable[VMKernel.victimPage].pinning == true){
				if(VMKernel.counter == Machine.processor().getNumPhysPages()){
					VMKernel.conditionVariable.sleep();
				}
				VMKernel.victimPage = (VMKernel.victimPage + 1) % Machine.processor().getNumPhysPages();
				continue;
			} if(VMKernel.invertedPageTable[VMKernel.victimPage].entry.used == false){
				break;
			}
			VMKernel.invertedPageTable[VMKernel.victimPage].entry.used = false;
			VMKernel.victimPage = (VMKernel.victimPage + 1) % Machine.processor().getNumPhysPages();
		}
		VMKernel.victimPage = (VMKernel.victimPage + 1) % Machine.processor().getNumPhysPages();
		if(VMKernel.invertedPageTable[VMKernel.victimPage].entry.dirty){
			int swapPageNum = 0;
			if(!VMKernel.freePages.isEmpty()){
				swapPageNum = VMKernel.freePages.removeLast();
			} else{
				swapPageNum = VMKernel.swapNums;
				VMKernel.swapNums++;
			}
			VMKernel.swapFile.write(Processor.pageSize * swapPageNum, Machine.processor().getMemory(), Processor.makeAddress(VMKernel.invertedPageTable[VMKernel.victimPage].entry.ppn, 0), Processor.pageSize);
			VMKernel.invertedPageTable[VMKernel.victimPage].entry.ppn = swapPageNum;
		}
		VMKernel.invertedPageTable[VMKernel.victimPage].process.usedPage.remove(new Integer(VMKernel.invertedPageTable[VMKernel.victimPage].entry.ppn));
		VMKernel.invertedPageTable[VMKernel.victimPage].entry.valid = false;
		ppn = VMKernel.invertedPageTable[VMKernel.victimPage].entry.ppn;
		return ppn;
	}


	protected void handlePageFault(int badVaddr){
		UserKernel.locking.acquire();
		int badVirtualPageNum = Processor.pageFromAddress(badVaddr);
		int coffVirtualPageNum = 0;
		int i = 0;
		int n = coffVirtualPageNum + 1;

		do {
			CoffSection section = coff.getSection(i);
			Lib.debug(dbgProcess, "\tinitializing " + section.getName()+ " section (" + section.getLength() + " pages)");
			for (int j = 0; j < section.getLength(); j++){
				int vpn = section.getFirstVPN() + j;
				coffVirtualPageNum = vpn;
				if (vpn == badVirtualPageNum){
					int ppn = 0;
					if (!UserKernel.physicalPages.isEmpty()){
						ppn = UserKernel.physicalPages.removeLast();
					} else{
						ppn = evicPageHelper();
					}
					usedPage.add(ppn);
					if(!pageTable[vpn].dirty){
						section.loadPage(j, ppn);
						if(section.isReadOnly()){
							pageTable[vpn] = new TranslationEntry(vpn, ppn, true, true, true, false);
						} else{
							pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, false);
						}
					} else{
						VMKernel.swapFile.read(pageTable[vpn].vpn * Processor.pageSize, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), Processor.pageSize);
						VMKernel.freePages.add(pageTable[vpn].vpn);
						pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, true);
					}
					VMKernel.invertedPageTable[ppn].process = this;
					VMKernel.invertedPageTable[ppn].entry = pageTable[vpn];
				}
			}
			i++;
		} while (i < coff.getNumSections());

		do {
			int vpn = n;
			if(vpn == badVirtualPageNum){
				int ppn = 0;
				if(!UserKernel.physicalPages.isEmpty()){
					ppn = UserKernel.physicalPages.removeLast();
				} else{
					ppn = evicPageHelper();
				}
				usedPage.add(ppn);
				if(!pageTable[vpn].dirty){
					byte[] data = new byte[Processor.pageSize];
					for(int j = 0; j < data.length; j++){
						data[j] = 0;
					}
					System.arraycopy(data, 0, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), Processor.pageSize);
					pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, false);
				} else{
					VMKernel.swapFile.read(pageTable[vpn].vpn * Processor.pageSize, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), Processor.pageSize);
					VMKernel.freePages.add(pageTable[vpn].vpn);
					pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, true);
				}
				VMKernel.invertedPageTable[ppn].process = this;
				VMKernel.invertedPageTable[ppn].entry = pageTable[vpn];
			}
			n++;
		} while (n < numPages);
		UserKernel.locking.release();
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
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    //get lock
    VMKernel.VMmutex.acquire();
    
    Lib.assertTrue(offset >= 0 && length >= 0
        && offset + length <= data.length);
    
    int stat_ = length;
    int total = 0;
    int read_size = 0;
    byte[] mm_space = Machine.processor().getMemory();

    if(vaddr_check(vaddr) == 0){
        return 0;
    };

    int offset_ = offset;
    int phy_addr = -1;
    int paddr_offset = Processor.offsetFromAddress(vaddr);
    int vpn = Processor.pageFromAddress(vaddr);
    boolean vpn_flag = pageTable[vpn].valid;
    boolean paddr_flag = (phy_addr < 0 || phy_addr >= mm_space.length);
    boolean vpnlength = (vpn >= pageTable.length || vpn < 0);
               
    //vpn invalid check         
    if(vpnlength){
        lockrel();
        return read_size;
    }
    if(vpn_flag){
        vpn_valid(vpn);
        phy_addr = pageTable[vpn].ppn * pageSize;
        phy_addr = phy_addr + paddr_offset;
    }
    else{
        handlePageFault(vaddr); 
        if(vpn_flag){
            vpn_valid(vpn);
            phy_addr = pageTable[vpn].ppn * pageSize;
            phy_addr = phy_addr + paddr_offset;
        }
        else{
            lockrel();
            return read_size;
        }
    }

    if (paddr_flag){
        paddr_error(vpn);
        return 0;
    }

    total = Math.min(stat_, (pageSize - paddr_offset));
    System.arraycopy(mm_space, phy_addr, data, offset, total);

    page_update(vpn,2);
    read_size = read_size + total;
    offset_ = offset_ + total;
    stat_ = stat_ - total;
    
    while(stat_ > 0){
        vpn++;
        if(vpnlength){
            lockrel();
            return read_size;
        }
        if(vpn_flag){
            vpn_valid(vpn);
            phy_addr = pageTable[vpn].ppn * pageSize;
        }
        else{
            vaddr = Processor.makeAddress(vpn, 0);
            handlePageFault(vaddr); 
            if(vpn_flag){  
                vpn_valid(vpn);
                phy_addr = pageTable[vpn].ppn * pageSize;
            }
            else{
                lockrel();
                return read_size;
            }
        }
        
        if(paddr_flag){
            paddr_error(vpn);
            return read_size;
        }
        total = Math.min(stat_, pageSize);
        System.arraycopy(mm_space, phy_addr, data, offset_, total);

        page_update(vpn,2);
        read_size += total;
        offset_ += total;
        stat_ -= total;
    }
    lockrel();
	return read_size;
}


public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    VMKernel.VMmutex.acquire();
    Lib.assertTrue(offset >= 0 && length >= 0
            && offset + length <= data.length);
    int stat_ = length;
    int total = 0;
    int write_size = 0;
	byte[] mm_space = Machine.processor().getMemory();
    int vpn = Processor.pageFromAddress(vaddr);
    boolean page_read = pageTable[vpn].readOnly;
    boolean vpnlength = (vpn >= pageTable.length || vpn < 0);
    if(vaddr_check(vaddr) == 0){
        return 0;
    };
    if(vpnlength){
        lockrel();
        return write_size;
    }
    int offset_ = offset;
    int phy_addr = -1;
    int paddr_offset = Processor.offsetFromAddress(vaddr);
    boolean vpn_flag = pageTable[vpn].valid;
    boolean paddr_flag = (phy_addr < 0 || phy_addr >= mm_space.length);
    
    if(vpn_flag){
        page_update(vpn,1);
        if(page_read == false){
            phy_addr = pageTable[vpn].ppn * pageSize + paddr_offset;
            pageTable[vpn].used = true;
        }
    }
    else{
        handlePageFault(vaddr);
        if(vpn_flag && (page_read == false)){
            page_update(vpn,1);
            phy_addr = pageTable[vpn].ppn * pageSize + paddr_offset;
            pageTable[vpn].used = true;
        }
        else{
            lockrel();
            return write_size;
        }
    }
    
    if (paddr_flag){
        paddr_error(vpn);
        return 0;
    }

    total = Math.min(stat_, (pageSize - paddr_offset));
    System.arraycopy(data, offset, mm_space, phy_addr, total);
    if(total > 0){
        pageTable[vpn].dirty = true;
    }
    page_update(vpn,2);
    write_size += total;
    offset_ += total;
    stat_ -= total;

    while(stat_ > 0){
        vpn++;
        if(vpnlength){
            lockrel();
            return write_size;
        }
        if(vpn_flag){
            if(page_read == false){
                page_update(vpn,1);
                phy_addr = pageTable[vpn].ppn * pageSize;
                pageTable[vpn].used = true; 
            }
            else{
                lockrel();
                return write_size;
            }
        }
        else{
            vaddr = Processor.makeAddress(vpn, 0);
            handlePageFault(vaddr); 
            if(vpn_flag && (page_read == false)){
                page_update(vpn,1);
                phy_addr = pageTable[vpn].ppn * pageSize;
                pageTable[vpn].used = true; 
            }
            else{
                lockrel();
                return write_size;
            }
        }
        if(paddr_flag){
            paddr_error(vpn);
            return write_size;
        }
        total = Math.min(stat_, pageSize);
        System.arraycopy(data, offset_, mm_space, phy_addr, total);
        if(total > 0){
            pageTable[vpn].dirty = true;
        }
        page_update(vpn,2);
        write_size += total;
        offset_ += total;
        stat_ -= total;
    }
    lockrel();
	return write_size;
}

public void lockrel(){
    VMKernel.VMmutex.release();
}
public void page_true(int vpn){
    VMKernel.invertedPageTable[pageTable[vpn].ppn].pinning = true;
    VMKernel.counter++;
}
public int vaddr_check(int vaddr){
    if(vaddr < 0){
        VMKernel.VMmutex.release();
        return 0;
    }
    return 1;
}
public void page_update(int vpn,int i){
    if(i == 1){
        VMKernel.invertedPageTable[pageTable[vpn].ppn].pinning = true;
        VMKernel.counter++;
    }
    if(i==2){
        VMKernel.invertedPageTable[pageTable[vpn].ppn].pinning = false;
        VMKernel.counter--;
        VMKernel.conditionVariable.wake();
    }
}

public void vpn_valid(int vpn){
    VMKernel.invertedPageTable[pageTable[vpn].ppn].pinning = true;
    VMKernel.counter++;
    pageTable[vpn].used = true;
}

public void paddr_error(int vpn){
    VMKernel.invertedPageTable[pageTable[vpn].ppn].pinning = false;
    VMKernel.counter--;
    VMKernel.conditionVariable.wake();
    VMKernel.VMmutex.release();
}
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}

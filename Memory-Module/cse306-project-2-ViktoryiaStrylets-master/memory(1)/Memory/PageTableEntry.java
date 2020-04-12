package osp.Memory;

/**
 * * Viktoryia Strylets 111748510
 * I pledge my honor that all parts of this project were done by me individually,
 * without collaboration with anyone, and without consulting external sources 
 * that help with similar projects.
 */
import osp.Hardware.*;
import osp.Tasks.*;
import osp.Threads.*;
import osp.Devices.*;
import osp.Utilities.*;
import osp.IFLModules.*;
/**
   The PageTableEntry object contains information about a specific virtual
   page in memory, including the page frame in which it resides.

   @OSPProject Memory

 */

public class PageTableEntry extends IflPageTableEntry
{
	/**
       The constructor. Must call

       	   super(ownerPageTable,pageNumber);

       as its first statement.

       @OSPProject Memory
	 */
	public PageTableEntry(PageTable ownerPageTable, int pageNumber)
	{
		super(ownerPageTable,pageNumber);

	}

	/**
       This method increases the lock count on the page by one. 

	The method must FIRST increment lockCount, THEN  
	check if the page is valid, and if it is not and no 
	page validation event is present for the page, start page fault 
	by calling PageFaultHandler.handlePageFault().

	@return SUCCESS or FAILURE
	FAILURE happens when the pagefault due to locking fails or the 
	that created the IORB thread gets killed.

	@OSPProject Memory
	 */
	public int do_lock(IORB iorb)
	{  

		/*Thread requested the IORB*/
		ThreadCB thread=iorb.getThread();

		/*Thread assigned by the PageFaultHandler*/
		ThreadCB validatingThread=this.getValidatingThread();

		/* FIRST increment lockCount*/
		if(this.getFrame()!=null) {
			this.getFrame().incrementLockCount();
			return SUCCESS;
			}


		/*
		 * check if the page is valid, and if it is not and no 
	      page validation event is present for the page, start page fault 
	      by calling PageFaultHandler.handlePageFault().
		 */
		if(!this.isValid()) {
			/*If validating Thread equals null 
			 *  no validation event is present for the page, initiate the PageFaultHandler
			 */
			if(validatingThread==null) {
				if(PageFaultHandler.handlePageFault(thread, MemoryLock, this)==FAILURE) {
					return FAILURE;
				}
			}
			/*If validation event is present for the page and thread requested I/O!=ValidThread
			 * suspend the Thread for on the Page event*/
			else if(thread!=validatingThread) {
				thread.suspend(this);	
			} 

		}

		/*check if the thread that created the IORB killed if yes return failure*/
		if(thread.getStatus()==ThreadKill) {
			return FAILURE;
		}
		/* increment lockCount*/
		this.getFrame().incrementLockCount();

		return SUCCESS;
	}

	/** This method decreases the lock count on the page by one. 

	This method must decrement lockCount, but not below zero.

	@OSPProject Memory
	 */
	public void do_unlock()
	{
		/*decrement lockCount, but not below zero*/
		if(this.getFrame().getLockCount()!=0) {
			this.getFrame().decrementLockCount(); }
		else {
			MyOut.print(this, "the lockcount of "+ this +"can not be decremented "
					+ "it will become negative");
		}

	}




}



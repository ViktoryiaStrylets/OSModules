package osp.Memory;

import java.util.*;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Interrupts.*;

/**
 * * Viktoryia Strylets 111748510
 * I pledge my honor that all parts of this project were done by me individually,
 * without collaboration with anyone, and without consulting external sources 
 * that help with similar projects.
 */

/**
    The MMU class contains the student code that performs the work of
    handling a memory reference.  It is responsible for calling the
    interrupt handler if a page fault is required.

    @OSPProject Memory
 */
public class MMU extends IflMMU
{
	/** 
        This method is called once before the simulation starts. 
     	Can be used to initialize the frame table and other static variables.


        @OSPProject Memory
	 */
	public static void init()
	{
		/* initialize the frame table*/

		for(int frameID=0; frameID<MMU.getFrameTableSize();frameID++) {
			FrameTableEntry frameEntry= new FrameTableEntry(frameID);
			MMU.setFrame(frameID, frameEntry);

		}

		PageFaultHandler.init();
	}

	/**
       This method handles memory references. The method must 
       calculate, which memory page contains the memoryAddress,
       determine, whether the page is valid, start page fault 
       by making an interrupt if the page is invalid, finally, 
       if the page is still valid, i.e., not swapped out by another 
       thread while this thread was suspended, set its frame
       as referenced and then set it as dirty if necessary.
       (After pagefault, the thread will be placed on the ready queue, 
       and it is possible that some other thread will take away the frame.)

       @param memoryAddress A virtual memory address
       @param referenceType The type of memory reference to perform 
       @param thread that does the memory access
       (e.g., MemoryRead or MemoryWrite).
       @return The referenced page.

       @OSPProject Memory
	 */
	static public PageTableEntry do_refer(int memoryAddress,
			int referenceType, ThreadCB thread)
	{
		/*Calculate page number*/
		int pageNumber=calcPageNumber(memoryAddress);

		/*find the page table of the thread*/
		PageTable pageTable=MMU.getPTBR();
		/*find the page entry*/
		PageTableEntry pageEntry=pageTable.pages[pageNumber];


		/*
		 * Check if the page is valid
		 * if not initiate pagefault 
		 * make sure do not call the pagefault if the page has outgoing pagefault operation
		 * */

		if(!pageEntry.isValid()) {
			ThreadCB validatingThread= pageEntry.getValidatingThread();

			/*if no PageFault event for this thread
			 * initiate the PageFault
			 * and also interrupt
			 */
			if(validatingThread==null) {
				/*set all static fields of the Class InterruptVector
				 * and initiate the system interrupt*/
				InterruptVector.setPage(pageEntry);
				InterruptVector.setReferenceType(referenceType);
				InterruptVector.setThread(thread);
				CPU.interrupt(PageFault);
				ThreadCB.dispatch();
			}
			/*If validation event is present for the page and thread requested I/O!=ValidThread
			 * suspend the Thread for on the Page event*/
			else if(thread!=validatingThread) {
				thread.suspend(pageEntry);

			}

		}

		/*check the status of thread is not ThreadKill
		 * and the page is valid */
		if(thread.getStatus()!=ThreadKill&&pageEntry.isValid()) {
			/*set reference bit and dirty bit*/
			pageEntry.getFrame().setReferenced(true);

			/*if the referenceType is MemoryWrite set the Dirty bit to true*/
			if(referenceType==MemoryWrite) {
				pageEntry.getFrame().setDirty(true);
			}


		}

		return  pageEntry; 

	}

	/** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.

	@OSPProject Memory
	 */
	public static void atError()
	{

	}

	/** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.

      @OSPProject Memory
	 */
	public static void atWarning()
	{
		// your code goes here

	}

	/*Function calculate PageNumber portion of the logical Address
	 * @param logicalAddress : A virtual memory address
	 * @return pageNumber
	 */

	public static int calcPageNumber(int logicalAddress){
		int offsetBits=MMU.getVirtualAddressBits()-MMU.getPageAddressBits();
		/*Calculate PageNumber*/
		int pageNumber=logicalAddress>>offsetBits;
		return pageNumber;
	}


	/*
       Feel free to add methods/fields to improve the readability of your code
	 */

}

/*
      Feel free to add local classes to improve the readability of your code
 */

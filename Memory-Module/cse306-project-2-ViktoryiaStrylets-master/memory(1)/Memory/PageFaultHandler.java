package osp.Memory;
import java.util.*;
import osp.Hardware.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.FileSys.FileSys;
import osp.FileSys.OpenFile;
import osp.IFLModules.*;
import osp.Interrupts.*;
import osp.Utilities.*;
import osp.IFLModules.*;


/**
 * * Viktoryia Strylets 111748510
 * I pledge my honor that all parts of this project were done by me individually,
 * without collaboration with anyone, and without consulting external sources 
 * that help with similar projects.
 */

/**
    The page fault handler is responsible for handling a page
    fault.  If a swap in or swap out operation is required, the page fault
    handler must request the operation.

    @OSPProject Memory
 */
public class PageFaultHandler extends IflPageFaultHandler
{

	public static void init()
	{
		Daemon.create("Cleaner",new Cleaner(),4000);
	}

	/**
        This method handles a page fault. 

        It must check and return if the page is valid, 

        It must check if the page is already being brought in by some other
	thread, i.e., if the page's has already pagefaulted
	(for instance, using getValidatingThread()).
        If that is the case, the thread must be suspended on that page.

        If none of the above is true, a new frame must be chosen 
        and reserved until the swap in of the requested 
        page into this frame is complete. 

	Note that you have to make sure that the validating thread of
	a page is set correctly. To this end, you must set the page's
	validating thread using setValidatingThread() when a pagefault
	happens and you must set it back to null when the pagefault is over.

        If a swap-out is necessary (because the chosen frame is
        dirty), the victim page must be dissassociated 
        from the frame and marked invalid. After the swap-in, the 
        frame must be marked clean. The swap-ins and swap-outs 
        must are performed using regular calls read() and write().

        The student implementation should define additional methods, e.g, 
        a method to search for an available frame.

	Note: multiple threads might be waiting for completion of the
	page fault. The thread that initiated the pagefault would be
	waiting on the IORBs that are tasked to bring the page in (and
	to free the frame during the swapout). However, while
	pagefault is in progress, other threads might request the same
	page. Those threads won't cause another pagefault, of course,
	but they would enqueue themselves on the page (a page is also
	an Event!), waiting for the completion of the original
	pagefault. It is thus important to call notifyThreads() on the
	page at the end -- regardless of whether the pagefault
	succeeded in bringing the page in or not.

        @param thread the thread that requested a page fault
        @param referenceType whether it is memory read or write
        @param page the memory page 

	@return SUCCESS is everything is fine; FAILURE if the thread
	dies while waiting for swap in or swap out or if the page is
	already in memory and no page fault was necessary (well, this
	shouldn't happen, but...). In addition, if there is no frame
	that can be allocated to satisfy the page fault, then it
	should return NotEnoughMemory

        @OSPProject Memory
	 */
	public static int do_handlePageFault(ThreadCB thread, 
			int referenceType,
			PageTableEntry page)
	{


		/*Check if the page passed as a parameter is valid candidate for
		 * the pagefault if not than return Failure
		 */
		if(page.isValid()) {
			/*resume all the threads waiting for the page to get valid*/
			page.notifyThreads();
			ThreadCB.dispatch();
			return FAILURE;
		}



		/*Check if the Memory is full if it is the case return NotEnoughMemory*/

		if(isMemoryFull()) {
			/*resume all the threads waiting for the page to get valid*/
			page.notifyThreads();
			ThreadCB.dispatch();
			return NotEnoughMemory;
		}

		/*Set the Validating thread of the page to the proper thread */
		page.setValidatingThread(thread);


		/* Suspend the thread that caused the interrupt until
		 * situation that gave rise to the pagefault is rectified
		 */
		SystemEvent pfEvent=new SystemEvent("PageFaultEvent");
		thread.suspend(pfEvent);


		/* Find a suitable frame to assign to page*/
		FrameTableEntry frame=findFreeFrame();



		/* Case 1 the free frame is found
		 * if there is a free frame  update page's frame attributes
		 * and perform swap in function*/

		if(frame!=null) {
			/* reserved the page to protect it from theft
			 * by other invocations of page handler
			 */

			frame.setReserved(thread.getTask());
			/*set the frame*/
			page.setFrame(frame);
			/*perform the swap-in operation*/
			swapin(thread,page);


		}

		/* Case 2 the frame is occupied
		 * if frame is null , means no free frame is found 
		 * initiate page replacement action
		 */
		else{
			frame=pageReplacement(page);

			/* reserved the page to protect it from theft
			 * by other invocations of page handler
			 */
			frame.setReserved(thread.getTask());

			PageTableEntry victimPage=frame.getPage();

			/* Case 2.a Frame contains not a clean page
			 * swap out the content of the frame to the corresponded 
			 * swap file before evicting the page*/

			if(frame.isDirty()) {

				swapout(thread,victimPage);

				/*check if the thread is not killed */
				if(thread.getStatus()==ThreadKill) {


					if(frame.getPage() != null){

						if(frame.getPage().getTask() == thread.getTask()){

							frame.setPage(null);

						}

					}
					frame.setReferenced(false);

					/*Unreserved a frame if necessary */
					if(frame.getReserved()==thread.getTask()) {
						frame.setUnreserved(thread.getTask());
					}

					/*resume all the threads waiting for the page to get valid*/
					page.notifyThreads();
					pfEvent.notifyThreads();
					page.setValidatingThread(null);
					ThreadCB.dispatch();
					return FAILURE;
				}




				frame.setDirty(false);

			}

			/*Case 2.b Frame contains a clean page
			 * the frame should be freed */


			/*free the frame*/
			frame.setPage(null);
			frame.setReferenced(false);

			/*update the attributes of the page to indicate 
			 * that it is not valid
			 */

			victimPage.setValid(false);
			victimPage.setFrame(null);


			/*swap-in*/
			page.setFrame(frame);
			swapin(thread,page);

		}





		/*if the thread get killed while 
		 * swap in was taking place*/
		if(thread.getStatus()==ThreadKill) {


			/*set the frame of the page to null if its contain page*/


			if(frame.getPage() != null){

				if(frame.getPage().getTask() == thread.getTask()){

					frame.setPage(null);

				}

			}

			/*Unreserved a frame if necessary */
			if(frame.getReserved()==thread.getTask()) {
				frame.setUnreserved(thread.getTask());
			}


			/*resume all the threads waiting for the page to get valid*/
			page.notifyThreads();
			/*resume the thread*/
			pfEvent.notifyThreads();
			/*set the validating Thread of page to null*/
			page.setValidatingThread(null);
			/*dispatch another thread*/
			ThreadCB.dispatch();
			return FAILURE;
		}

		/*after swap-in if the thread is not killed set frame to clean*/
		frame.setDirty(false);

		/*if all is well and the thread was not killed while waiting for the I/O operations
		 * update the page table
		 */
		frame.setPage(page);
		/* Set the validating thread to true*/
		page.setValid(true);

		/*Unreserves a frame*/
		if(frame.getReserved()==thread.getTask()) {
			frame.setUnreserved(thread.getTask());
		}


		/*resume all the threads waiting for the page to get valid*/
		page.notifyThreads();

		/*resume the thread*/
		pfEvent.notifyThreads();

		/*setValidationThread to null*/
		page.setValidatingThread(null);

		/*dispatch() Thread*/
		ThreadCB.dispatch();

		return SUCCESS;
	}

	/*Function that performs swap-in operation
	 * @param thread : thread  on behalf which the read is performed
	 * @param memoryPage : page where to read the requested block from the file
	 */

	public static void swapin(ThreadCB thread, PageTableEntry memoryPage) {
		TaskCB task=memoryPage.getTask();

		/*get the SwapDevice of the task*/
		OpenFile swapFile=task.getSwapFile();

		/*initiate the read to the frame*/
		swapFile.read(memoryPage.getID(), memoryPage, thread);
	}

	/*Function that performs swap-out operation
	 * @param thread : thread  on behalf which the write is performed
	 * @param memoryPage : page where to write the requested block from the file
	 */
	public static void swapout(ThreadCB thread, PageTableEntry memoryPage) {
		TaskCB task=memoryPage.getTask();

		/*get the SwapDevice of the task*/
		OpenFile swapFile=task.getSwapFile();
		/*initiate the read to the frame*/
		swapFile.write(memoryPage.getID(), memoryPage, thread);

	}

	/*Function Check if there enough memory, other words 
	 * not all the frames locked or reserved 
	 * if it is a case return true otherwise return false*/


	public static boolean isMemoryFull(){

		int available_frames=MMU.getFrameTableSize();
		for(int frameID=0; frameID<MMU.getFrameTableSize();frameID++) {
			FrameTableEntry frameEntry=MMU.getFrame(frameID);
			if(frameEntry.isReserved()||frameEntry.getLockCount()>0) {
				available_frames--;
			}

		}
		/*if number of available frames is zero , than all frames 
		 * either locked or reserved return NotEnoughMemory
		 */
		if(available_frames==0) {
			return true;
		}
		else return false;

	}

	/*Function looks for a free frame 
	 * return free frame or null if there is no free frame
	 */

	public static FrameTableEntry findFreeFrame() {
		FrameTableEntry freeFrame=null;
		for(int frameID=0; frameID<MMU.getFrameTableSize();frameID++) {
			FrameTableEntry frameEntry=MMU.getFrame(frameID);
			if(frameEntry.getPage()==null&&!frameEntry.isReserved()&&frameEntry.getLockCount()==0) {
				freeFrame=frameEntry;
				break;
			}

		}
		return freeFrame;
	}

	/*Function performs page replacement algorithm
	 * return free frame with assigned  page to it
	 * sweeps the eligible frames and selects one with the use bit of 0 for replacement
	 * if no such frame exists, choose some eligible frame at will
	 */
	public static FrameTableEntry pageReplacement(PageTableEntry page) {
		FrameTableEntry frame=null;
		FrameTableEntry eligibleFrame=null;
		for(int frameID=0; frameID<MMU.getFrameTableSize();frameID++) {
			FrameTableEntry frameEntry=MMU.getFrame(frameID);
			if(!frameEntry.isReserved()&&frameEntry.getLockCount()==0) {
				eligibleFrame=frameEntry;
				if(!frameEntry.isReferenced()) {
					frame=frameEntry;
					break;
				}
			}

		}
		/*no frame exists choose any eligible frame*/
		if(frame==null) {
			frame=eligibleFrame;
		}
		return frame;
	}
}
/* Class represents the first hand in the two handed clock policy algorithm
 *  Sweeps all eligible (for replacement) frames and 
 * sets the reference bits of these frames to 0
 * swaps out every 10th dirty page it finds
 */

class Cleaner implements DaemonInterface {

	
	private static final int ThreadKill = 0;
	
	public void unleash(ThreadCB thread) {
		 
		int dirtyPagecount=0;

		for(int frameID=0; frameID<MMU.getFrameTableSize();frameID++) {
			FrameTableEntry frameEntry=MMU.getFrame(frameID);

			if(!frameEntry.isReserved()&&frameEntry.getLockCount()==0) {
				frameEntry.setReferenced(false);

				if(frameEntry.isDirty()&&(++dirtyPagecount%10)==0) {


					PageTableEntry page=frameEntry.getPage();
					TaskCB task=page.getTask();
					/*get the SwapDevice of the task*/
					OpenFile swapFile=task.getSwapFile();
					/*initiate the read to the frame*/
					swapFile.write(page.getID(), page, thread);


					if(thread.getStatus()!=ThreadKill) {
						frameEntry.setDirty(false);
					}

				}
			}

		}
	}
}


/*
      Feel free to add local classes to improve the readability of your code
 */

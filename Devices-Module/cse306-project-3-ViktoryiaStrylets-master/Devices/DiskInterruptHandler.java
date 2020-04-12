package osp.Devices;
import java.util.*;
import osp.IFLModules.*;
import osp.Hardware.*;
import osp.Interrupts.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.FileSys.*;

/**
    The disk interrupt handler.  When a disk I/O interrupt occurs,
    this class is called upon the handle the interrupt.

    @OSPProject Devices
*/

/**
 * Viktoryia Strylets 111748510
 * I pledge my honor that all parts of this project were done by me individually,
 * without collaboration with anyone,
 * and without consulting any external sources that could help with similar projects.
 */
public class DiskInterruptHandler extends IflDiskInterruptHandler
{
    /** 
        Handles disk interrupts. 
        
        This method obtains the interrupt parameters from the 
        interrupt vector. The parameters are IORB that caused the 
        interrupt: (IORB)InterruptVector.getEvent(), 
        and thread that initiated the I/O operation: 
        InterruptVector.getThread().
        The IORB object contains references to the memory page 
        and open file object that participated in the I/O.
        
        The method must unlock the page, set its IORB field to null,
        and decrement the file's IORB count.
        
        The method must set the frame as dirty if it was memory write 
        (but not, if it was a swap-in, check whether the device was 
        SwapDevice)

        As the last thing, all threads that were waiting for this 
        event to finish, must be resumed.

        @OSPProject Devices 
    */
    public void do_handleInterrupt()
    {
     /*obtain the information about the interrupt
      * IORB,Thread, page, OpenFile , open file handle  */
    	IORB iorb=(IORB)InterruptVector.getEvent();
    	ThreadCB thread=iorb.getThread();
    	OpenFile openFile = iorb.getOpenFile();
    	PageTableEntry page = iorb.getPage();
    	boolean handle=openFile.closePending;
    	
    	/*decrement the count of the iorb*/
    	openFile.decrementIORBCount();
    	
    	/*if there no more pending i/o request for the file close it*/
    	if(openFile.closePending&&openFile.getIORBCount()==0 ) {
			openFile.close();

		}
    	
    	/*unlock the page*/
    	page.unlock();
    	
    	
    	/*if the task is alive  REferenced and Dirty bit properly if needed */
    	if(thread.getTask().getStatus()!=TaskTerm) {
    	 
    		/*if the operation not a page swap in or swap-out ad thread is alive set Referenced bit*/
    		if(iorb.getDeviceID() != SwapDeviceID && thread.getStatus() != ThreadCB.ThreadKill ) {
    			
    			page.getFrame().setReferenced(true);
    			
    			/*if the interrupt type is FileRead set the page's frame to Dirty*/
    			if(iorb.getIOType()==FileRead) {
    				page.getFrame().setDirty(true);
    			}
    			
    			
    		}

    	   else {
    		   page.getFrame().setDirty(false);
    		    }
    		
    	}
    	 /* if the task that owns the IORB is dead and the frame associated with the IORB
    	  * was reserved by the task unreserve it
    	  */
    	else if(page.getFrame().getReserved()==thread.getTask()) {
    		page.getFrame().setUnreserved(thread.getTask());
    	}
    	
    	/*notify threads*/
    	iorb.notifyThreads();
    	
    	/*set the Device to busy*/
    	
       Device.get(iorb.getDeviceID()).setBusy(false);
       
       IORB device = Device.get(iorb.getDeviceID()).dequeueIORB();
       
       /* start new I/O request */

       if (device != null) 

   	{

   		Device.get(iorb.getDeviceID()).startIO(device);

   	}

    ThreadCB.dispatch();
    }
 
    
    

    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/

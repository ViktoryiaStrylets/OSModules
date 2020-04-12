package osp.Devices;
/**
 * Viktoryia Strylets 111748510
 * I pledge my honor that all parts of this project were done by me individually,
 * without collaboration with anyone,
 * and without consulting any external sources that could help with similar projects.
 */

/**
    This class stores all pertinent information about a device in
    the device table.  This class should be sub-classed by all
    device classes, such as the Disk class.

    @OSPProject Devices
 */

import osp.IFLModules.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Memory.*;
import osp.FileSys.*;
import osp.Tasks.*;
import java.util.*;

public class Device extends IflDevice
{
	/**
        This constructor initializes a device with the provided parameters.
	    As a first statement it must have the following:

	    super(id,numberOfBlocks);

	    @param numberOfBlocks -- number of blocks on device

        @OSPProject Devices
   */

	GenericList backUpQueue;
	GenericList currentQueuePtr;

	public Device(int id, int numberOfBlocks)
	{

		super(id,numberOfBlocks);

		backUpQueue=new GenericList();
		iorbQueue=new GenericList();
		currentQueuePtr=null;


	}

	/**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Devices
	 */
	public static void init()
	{
     

	}

	/**
       Enqueues the IORB to the IORB queue for this device
       according to some kind of scheduling algorithm.

       This method must lock the page (which may trigger a page fault),
       check the device's state and call startIO() if the 
       device is idle, otherwise append the IORB to the IORB queue.

       @return SUCCESS or FAILURE.
       FAILURE is returned if the IORB wasn't enqueued 
       (for instance, locking the page fails or thread is killed).
       SUCCESS is returned if the IORB is fine and either the page was 
       valid and device started on the IORB immediately or the IORB
       was successfully enqueued (possibly after causing pagefault pagefault)

       @OSPProject Devices
	 */
	public int do_enqueueIORB(IORB iorb)
	{

		/*lock the page*/
		iorb.getPage().lock(iorb);

		/* increment iorb count*/
		if(iorb.getThread().getStatus()!=ThreadKill)
			iorb.getOpenFile().incrementIORBCount();

		/*set the cylinder*/
		int cylinder=this.calculateCylinder(iorb);
		iorb.setCylinder(cylinder);

		/*check if the thread is still alive*/
		if(iorb.getThread().getStatus()==ThreadKill) {
			return FAILURE;
		}

		/*if the Device is idle start i/o*/

		if(!this.isBusy()) {

			this.startIO(iorb); 
			return SUCCESS;
		}

		else {

			if(currentQueuePtr==null||currentQueuePtr.equals( backUpQueue)) {

				((GenericList) iorbQueue).insert(iorb);

			}


			else {



				((GenericList) backUpQueue).insert(iorb);

			}

		}

		return SUCCESS;
	}


	/**
       Selects an IORB (according to some scheduling strategy)
       and dequeues it from the IORB queue.

       @OSPProject Devices
	 */
	public IORB do_dequeueIORB()
	{

		if(this.iorbQueue.isEmpty()&&this.backUpQueue.isEmpty()) {
			return null;
		}


		IORB iorbCandidate=null;

		/*start dequeuing from the iorb queue*/
		if(currentQueuePtr==null&&!iorbQueue.isEmpty()) {

			iorbCandidate=SSTF((GenericList) iorbQueue);
			((GenericList)iorbQueue).remove(iorbCandidate);


			if(!(((GenericList)iorbQueue).isEmpty())) {

				currentQueuePtr=(GenericList)iorbQueue ;

			}



			return iorbCandidate;

		}
       
		/*if current process queue is not empty continue dequeue from it if empty means
		 * means device is done with it , switch it to the backup queue and process the iorb from that queue
		 * make the iorb queue open for new request and close backup queue
		 */
		
		else if(currentQueuePtr.equals(iorbQueue)) {
			if(!iorbQueue.isEmpty()) {

				iorbCandidate=SSTF((GenericList) iorbQueue);
				((GenericList)iorbQueue).remove(iorbCandidate);



				if(iorbQueue.isEmpty()) {

					currentQueuePtr=(GenericList) backUpQueue;

				}
				return iorbCandidate;
			}
			else {
				iorbCandidate=SSTF((GenericList) backUpQueue);
				((GenericList)backUpQueue).remove(iorbCandidate);
				if(backUpQueue.isEmpty()) {

					currentQueuePtr=(GenericList) iorbQueue ;

				}

			}

		}

		else if(currentQueuePtr.equals(backUpQueue)) {
			if(!backUpQueue.isEmpty()) {

				iorbCandidate=SSTF((GenericList) backUpQueue);
				((GenericList)backUpQueue).remove(iorbCandidate);
				if(backUpQueue.isEmpty()) {

					currentQueuePtr=(GenericList) iorbQueue;

				}
				return iorbCandidate;
			}

			else {

				iorbCandidate=SSTF((GenericList) iorbQueue);
				((GenericList)iorbQueue).remove(iorbCandidate);
				if(iorbQueue.isEmpty()) {

					currentQueuePtr=(GenericList) backUpQueue;

				}
			}
		}


		return iorbCandidate;

	}

	/**
        Remove all IORBs that belong to the given ThreadCB from 
	this device's IORB queue

        The method is called when the thread dies and the I/O 
        operations it requested are no longer necessary. The memory 
        page used by the IORB must be unlocked and the IORB count for 
	the IORB's file must be decremented.

	@param thread thread whose I/O is being canceled

        @OSPProject Devices
	 */
	public void do_cancelPendingIO(ThreadCB thread)
	{
		if(!iorbQueue.isEmpty()) {
			cancelIO(thread,( GenericList)iorbQueue);
		}
		else return ;

		if(!backUpQueue.isEmpty()) {
			cancelIO(thread,( GenericList)backUpQueue);  
		}
		else return;
	}



	/*helping method of do_cancelIORB 
	 * to avoid redundancy of the code*/

	public void cancelIO(ThreadCB thread, GenericList queue) {
		Enumeration iterator=null;

		iterator= ((GenericList)queue).forwardIterator();

		while(iterator.hasMoreElements()) {
			IORB chosenIORB=(IORB)iterator.nextElement();

			if(chosenIORB.getThread()==thread) {

				/*unlock the page*/
				chosenIORB.getPage().unlock();
				/*decrement the IORB count on of the open file*/
				chosenIORB.getOpenFile().decrementIORBCount();
				/*close the open file handle*/
				if(chosenIORB.getOpenFile().closePending&&chosenIORB.getOpenFile().getIORBCount()==0 ) {
					chosenIORB.getOpenFile().close();

				}

				/*remove the iorb*/
				((GenericList) queue).remove(chosenIORB);

			}


		}   
	}

	/** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.

	@OSPProject Devices
	 */
	public static void atError()
	{
		// your code goes here

	}

	/** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.

	@OSPProject Devices
	 */
	public static void atWarning()
	{
		// your code goes here

	}

	/** The function calculates the cylinder from the Block number
	 * 
	 * @return the cylinder number
	 */

	public int calculateCylinder(IORB iorb) {

		int blockSize= (int) Math.pow(2, MMU.getVirtualAddressBits() - MMU.getPageAddressBits()); 
		int blocksPertrack=(((Disk) this).getSectorsPerTrack()*((Disk) this).getBytesPerSector())/blockSize;
		int cylinder= iorb.getBlockNumber()/ (blocksPertrack * ((Disk) this).getPlatters());

		return cylinder;
	}

	/** the function looks for the min seek time IORB , SSTF algorithm
	 * 
	 * @param queue the queue of all outstanding requests
	 * @return the IORB with the min seek time
	 */

	public IORB SSTF(GenericList queue ){


		int ptr= queue.length() - 1;

		IORB chosenIORB=(IORB)queue.getAt(ptr);
		int minDiffer=Math.abs(chosenIORB.getCylinder() - ((Disk)this).getHeadPosition());

		for(int i = ptr; i >= 0; i--)  {
			IORB iorb=(IORB) queue.getAt(i);
			int distance=Math.abs(iorb.getCylinder() - ((Disk)this).getHeadPosition());
			if(distance<minDiffer) {
				minDiffer=distance;
				chosenIORB=iorb;
			}

		}

		return chosenIORB;
	}	
}

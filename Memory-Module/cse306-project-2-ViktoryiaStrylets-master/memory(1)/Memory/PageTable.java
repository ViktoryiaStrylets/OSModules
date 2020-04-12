package osp.Memory;

/**
    The PageTable class represents the page table for a given task.
    A PageTable consists of an array of PageTableEntry objects.  This
    page table is of the non-inverted type.

    @OSPProject Memory
 */
import java.lang.Math;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Hardware.*;
/**
 * * Viktoryia Strylets 111748510
 * I pledge my honor that all parts of this project were done by me individually,
 * without collaboration with anyone, and without consulting external sources 
 * that help with similar projects.
 */
public class PageTable extends IflPageTable
{
	/** 
	The page table constructor. Must call

	    super(ownerTask)

	as its first statement.

	@OSPProject Memory
	 */
	public PageTable(TaskCB ownerTask)
	{
		/*call the Page table constructor*/
		super(ownerTask);

		/* get number of bits devoted to the page address*/
		int bits=MMU.getPageAddressBits();
		/*calculate number of entries in the Page Table*/
		int num_pages=(int)Math.pow(2, bits);
		/*initialized the pages variable to the new array */
		pages=new PageTableEntry[num_pages];
		/*Initialized each page with suitable PageTableEntry object*/
		for(int id=0;id<num_pages;id++) {
			pages[id]=new PageTableEntry(this,id);
		}
	}

	/**
       Frees up main memory occupied by the task.
       Then unreserved the freed pages, if necessary.

       @OSPProject Memory
	 */


	public void do_deallocateMemory()

	{

		for(int id = 0; id < MMU.getFrameTableSize(); id++){
			FrameTableEntry frame = MMU.getFrame(id);
			PageTableEntry page= frame.getPage();
			/*make sure the page belongs to the killed task*/
			if(page!= null && page.getTask() == getTask())

			{
				/*set the page assigned to the frame to null, indicating that the frame is free*/
				frame.setPage(null);
				/*set Dirty bit to false indicating that frame is clean*/
				frame.setDirty(false);
				/*set referenced bit to the false to unreferenced the frame*/
				frame.setReferenced(false);

          

				/* Unreserve  each frame that was reserved by that task   
				 * make sure that the frame reserved by that task 
				 */

				if(frame.getReserved() == getTask())
					frame.setUnreserved(getTask());

			}

		}

	}




	/*
       Feel free to add methods/fields to improve the readability of your code
	 */

}

/*
      Feel free to add local classes to improve the readability of your code
 */

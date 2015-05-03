/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2015
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.runtime.controlprogram.parfor;

import java.util.LinkedList;
import java.util.List;

import com.ibm.bi.dml.runtime.DMLRuntimeException;
import com.ibm.bi.dml.runtime.controlprogram.ParForProgramBlock;
import com.ibm.bi.dml.runtime.controlprogram.parfor.Task.TaskType;
import com.ibm.bi.dml.runtime.instructions.cp.IntObject;

/**
 * This naive task partitioner virtually iterates over the given FOR loop (from, to, incr),
 * creates iterations and group them to tasks according to the given task size. There, all
 * tasks are equally sized.
 * 
 */
public class TaskPartitionerFixedsize extends TaskPartitioner
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2015\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	protected int _firstnPlus1 = 0; //add one to these firstn tasks
 	
	public TaskPartitionerFixedsize( long taskSize, String iterVarName, IntObject fromVal, IntObject toVal, IntObject incrVal ) 
	{
		super(taskSize, iterVarName, fromVal, toVal, incrVal);
	}

	@Override
	public List<Task> createTasks() 
		throws DMLRuntimeException 
	{
		LinkedList<Task> tasks = new LinkedList<Task>();
		
		//range tasks (similar to run-length encoding) make only sense if taskSize>3
		TaskType type = (ParForProgramBlock.USE_RANGE_TASKS_IF_USEFUL && _taskSize>3 ) ? 
				           TaskType.RANGE : TaskType.SET;
		
		long lFrom  = _fromVal.getLongValue();
		long lTo    = _toVal.getLongValue();
		long lIncr  = _incrVal.getLongValue();
		long lfnp1  = _firstnPlus1;
		
		for( long i = lFrom; i<=lTo;  )
		{
			//create new task and add to list of tasks
			Task lTask = new Task( type );
			tasks.addLast(lTask);
			
			int corr = (lfnp1-- > 0)? 1:0; //correction for static partitioner
			
			// add <tasksize> iterations to task 
			// (last task might have less)
			if( type == TaskType.SET ) 
			{
				//value based tasks
				for( long j=0; j<_taskSize+corr && i<=lTo; j++, i+=lIncr )
				{
					lTask.addIteration(new IntObject(_iterVarName, i));				
				}				
			}
			else 
			{
				//determine end of task
				long to = Math.min( i+(_taskSize-1+corr)*lIncr, lTo );
				
				//range based tasks
				lTask.addIteration(new IntObject(_iterVarName, i));	    //from
				lTask.addIteration(new IntObject(_iterVarName, to));    //to
				lTask.addIteration(new IntObject(_iterVarName, lIncr));	//increment
				
				i = to + lIncr;
			}
		}

		return tasks;
	}

	@Override
	public long createTasks(LocalTaskQueue<Task> queue) 
		throws DMLRuntimeException 
	{
		long numCreatedTasks=0;
		
		//range tasks (similar to run-length encoding) make only sense if taskSize>3
		TaskType type = (ParForProgramBlock.USE_RANGE_TASKS_IF_USEFUL && _taskSize>3 ) ? 
				              TaskType.RANGE : TaskType.SET;
		
		long lFrom  = _fromVal.getLongValue();
		long lTo    = _toVal.getLongValue();
		long lIncr  = _incrVal.getLongValue();
		long lfnp1  = _firstnPlus1;
		
		try
		{
			for( long i = lFrom; i<=lTo;  )
			{
				//create new task and add to list of tasks
				Task lTask = new Task( type );
				
				int corr = (lfnp1-- > 0)? 1:0; //correction for static partitioner
				
				// add <tasksize> iterations to task 
				// (last task might have less)
				if( type == TaskType.SET ) 
				{
					//value based tasks
					for( long j=0; j<_taskSize+corr && i<=lTo; j++, i+=lIncr )
					{
						lTask.addIteration(new IntObject(_iterVarName, i));				
					}				
				}
				else 
				{
					//determine end of task
					long to = Math.min( i+(_taskSize-1+corr)*lIncr, lTo );
					
					//range based tasks
					lTask.addIteration(new IntObject(_iterVarName, i));	    //from
					lTask.addIteration(new IntObject(_iterVarName, to));    //to
					lTask.addIteration(new IntObject(_iterVarName, lIncr));	//increment
					
					i = to + lIncr;
				}
				
				//add task to queue (after all iteration added for preventing raise conditions)
				queue.enqueueTask( lTask );
				numCreatedTasks++;
			}
			
			// mark end of task input stream
			queue.closeInput();	
		}
		catch(Exception ex)
		{
			throw new DMLRuntimeException(ex);
		}
		
		return numCreatedTasks;
	}
	
	
}

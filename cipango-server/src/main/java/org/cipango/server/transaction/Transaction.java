// ========================================================================
// Copyright 2008-2012 NEXCOM Systems
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.cipango.server.transaction;

import java.util.EnumMap;
import java.util.Map;

import org.cipango.server.SipConnection;
import org.cipango.server.SipRequest;
import org.cipango.server.SipServer;
import org.cipango.server.labs.transaction.TransactionManager;
import org.cipango.server.labs.transaction.TransactionManager.TimerTask;
import org.cipango.server.session.CallSession;
//import org.cipango.util.TimerTask;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public abstract class Transaction 
{
	private static final Logger LOG = Log.getLogger(Transaction.class);
			
	enum State 
	{
		UNDEFINED,
		CALLING,
		TRYING,
		PROCEEDING,
		COMPLETED,
		CONFIRMED,
		ACCEPTED,
		TERMINATED
	}
	
	enum Timer { A, B, D, E, F, G, H, I, J, K, L, M }
	
	public static final int DEFAULT_T1 = 500;
	public static final int DEFAULT_T2 = 4000;
	public static final int DEFAULT_T4 = 5000;
	public static final int DEFAULT_TD = 32000;
		
	public static final int __T1 = DEFAULT_T1;
	public static final int __T2 = DEFAULT_T2;
	public static final int __T4 = DEFAULT_T4;
	public static final int __TD = DEFAULT_TD;	
	
	protected State _state = State.UNDEFINED;
	
	private String _branch;
	
	public TransactionManager _transactionManager;
	
	protected SipRequest _request;
		
	public abstract boolean isServer();
	public abstract SipConnection getConnection();
	protected abstract void timeout(Timer timer);
	
	private Map<Timer, TimerTask> _timerTasks = new EnumMap<Timer, TimerTask>(Timer.class);

	public Transaction(SipRequest request, String branch)
	{
		_request = request;
		_branch = branch;
		
		request.setTransaction(this);
	}
	
	public boolean isTransportReliable()
	{
		return getConnection().getTransport().isReliable();
	}
	
	public State getState()
	{
		return _state;
	}
	
	protected void setState(State state)
	{
		_state = state;
	}
	
	public boolean isInvite()
	{
		return _request.isInvite();
	}
	
	public boolean isCancel()
	{
		return _request.isCancel();
	}
	
	public String getBranch()
	{
		return _branch;
	}
	
	protected SipServer getServer()
	{
		//return _request.getCallSession().getServer();
		return _transactionManager.getServer();
	}
	
	public void setTransactionManager(TransactionManager manager)
	{
		_transactionManager = manager;
	}
	
	protected CallSession getCallSession()
	{
		return _request.getCallSession();
	}
	
	protected void startTimer(Timer timer, long delay)
	{
		TimerTask task = _timerTasks.get(timer);
		if (task != null)
			task.cancel();
		//_timerTasks.put(timer, getCallSession().schedule(new Timeout(timer), delay));
		_timerTasks.put(timer, _transactionManager.schedule(new Timeout(timer), delay));
	}
	
	protected void cancelTimer(Timer timer)
	{
		TimerTask task = _timerTasks.get(timer);
		if (task != null)
			//getCallSession().cancel(task);
			task.cancel();
		_timerTasks.remove(timer);
	}
	
	/*protected void startTimer(Timer timer, long delay)
	{
		TimerTask task = _timerTasks.get(timer);
		if (task != null)
			//getCallSession().cancel(task);
			_transactionManager.cancel(task);
		_timerTasks.put(timer, getCallSession().schedule(new Timeout(timer), delay));
	}
	
	protected void cancelTimer(Timer timer)
	{
		TimerTask task = _timerTasks.get(timer);
		if (task != null)
			getCallSession().cancel(task);
		_timerTasks.remove(timer);
	}*/
	
	@Override
	public String toString()
	{
		return String.format("%s{%s,%s}", isServer() ? "ST" : "CT", getBranch(), _state);
	}
	
	class Timeout implements Runnable
	{
		private Timer _timer;
		public Timeout(Timer timer) { _timer = timer; }
		public void run() { try { timeout(_timer); } catch (Throwable t) { LOG.debug(t); } } 
		@Override public String toString() { return "Timer" + _timer; }
	}
}
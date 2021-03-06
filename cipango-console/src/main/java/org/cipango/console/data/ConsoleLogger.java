package org.cipango.console.data;

import java.io.IOException;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.servlet.http.HttpServletRequest;

import org.cipango.console.Action;
import org.cipango.console.Manager;
import org.cipango.console.Action.StartAction;
import org.cipango.console.Action.StopAction;
import org.cipango.console.menu.Page;
import org.cipango.console.util.Parameters;

public class ConsoleLogger
{

	public static final int DEFAULT_MAX_MESSAGES = 20;
	
	private MBeanServerConnection _mbsc;
	private ObjectName _objectName;
	private Page _page;
	private Map<String, String> _filters;
	private int _maxMessages;
	private String _messageFilter = "";
	
	
	public static final String 
		CALL_ID_FILTER = "message.callId",
		BRANCH_FILTER = "message.topVia.branch",
		TO_FILTER = "message.to.uRI.toString()",
		FROM_FILTER = "message.from.uRI.toString()",
		REMOTE_FILTER = "remote",
		REQUEST_URI_FILTER = "message.requestURI != null and message.requestURI.toString()";

		
	public ConsoleLogger(MBeanServerConnection mbsc, Page page, ObjectName objectName, Map<String, String> filters) throws Exception
	{
		_mbsc = mbsc;
		_page = page;
		_objectName = objectName;
		_filters = filters;
	}
	
	public boolean isEnabled() throws Exception
	{
		return Manager.isRunning(_mbsc, _objectName);
	}
	
	public boolean isRegistered() throws IOException
	{
		return _mbsc.isRegistered(_objectName);
	}

	public Map<String, String> getFilters()
	{
		return _filters;
	}
	
	
	public String getFilterTitle()
	{
		String filterValue = _messageFilter.substring(_messageFilter.lastIndexOf('(') + 1);
		filterValue = filterValue.substring(0, filterValue.length() - 1);
		if (_messageFilter.startsWith(CALL_ID_FILTER))
			 return "Call-ID is " + filterValue;
		else if (_messageFilter.startsWith(BRANCH_FILTER))
			 return "Branch is " + filterValue;
		else if (_messageFilter.startsWith(TO_FILTER))
			 return "To URI is " + filterValue;
		else if (_messageFilter.startsWith(FROM_FILTER))
			 return "From URI is " + filterValue;
		else if (_messageFilter.startsWith(REQUEST_URI_FILTER))
			 return "Request-URI is " + filterValue;
		else if (_messageFilter.startsWith(REMOTE_FILTER))
			 return "Remote host is " + filterValue;
		else
			 return _messageFilter;
	}

	public int getMaxMessages()
	{
		return _maxMessages;
	}

	public void setMaxMessages(int maxMessages)
	{
		_maxMessages = maxMessages;
	}

	public String getMessageFilter()
	{
		return _messageFilter;
	}

	public void setMessageFilter(String messageFilter)
	{
		if (messageFilter == null)
			_messageFilter = "";
		else
			_messageFilter = messageFilter;
	}
	
	public Action getStartAction()
	{
		return new StartConsoleLoggerAction(_page, _objectName);
	}
	
	public Action getStopAction()
	{
		return new StopConsoleLoggerAction(_page, _objectName);
	}
	
	public Action getClearAction()
	{
		return new ClearConsoleLoggerAction(_page);
	}
	
	public Integer getMaxSavedMessages() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException
	{
		return (Integer) _mbsc.getAttribute(_objectName, "maxMessages");
	}

	public static class StartConsoleLoggerAction extends StartAction
	{
		public StartConsoleLoggerAction(Page page, ObjectName objectName)
		{
			super(page, "activate-console-message-log", objectName);
		}
	}
	
	public static class StopConsoleLoggerAction extends StopAction
	{
		public StopConsoleLoggerAction(Page page, ObjectName objectName)
		{
			super(page, "deactivate-console-message-log", objectName);
		}
	}
	
	public static class ClearConsoleLoggerAction extends Action
	{
		public ClearConsoleLoggerAction(Page page)
		{
			super(page, "clear-logs");
		}
		
		@Override
		protected void doProcess(HttpServletRequest request, MBeanServerConnection mbsc) throws Exception
		{
			throw new UnsupportedOperationException();
		}
	}
	
	public static class MessageInMemoryAction extends Action
	{
		private ObjectName _objectName;
		
		public MessageInMemoryAction(Page page, ObjectName objectName)
		{
			super(page, "msg-in-memory");
			_objectName = objectName;
		}

		@Override
		protected void doProcess(HttpServletRequest request, MBeanServerConnection mbsc) throws Exception
		{
			String maxMsg = request.getParameter(Parameters.MAX_SAVED_MESSAGES);
			if (maxMsg != null)
			{
				mbsc.setAttribute(_objectName, new Attribute("maxMessages", Integer.parseInt(maxMsg)));
			}
		}
	}	
}

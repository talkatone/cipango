// ========================================================================
// Copyright 2006-2013 NEXCOM Systems
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
package org.cipango.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import javax.servlet.sip.Address;
import javax.servlet.sip.Parameterable;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipSession;

import org.cipango.server.servlet.SipServletHolder;
import org.cipango.server.session.ApplicationSession;
import org.cipango.server.session.Session;
import org.cipango.server.session.scoped.ScopedAppSession;
import org.cipango.server.session.scoped.ScopedSession;
import org.cipango.server.transaction.Transaction;
import org.cipango.server.util.ContactAddress;
import org.cipango.server.util.ListIteratorProxy;
import org.cipango.server.util.ReadOnlyAddress;
import org.cipango.server.util.ReadOnlyParameterable;
import org.cipango.sip.AddressImpl;
import org.cipango.sip.CSeq;
import org.cipango.sip.SipFields;
import org.cipango.sip.SipFields.Field;
import org.cipango.sip.SipHeader;
import org.cipango.sip.SipHeader.Type;
import org.cipango.sip.SipMethod;
import org.cipango.sip.SipVersion;
import org.cipango.sip.Via;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.AttributesMap;
import org.eclipse.jetty.util.LazyList;
import org.eclipse.jetty.util.QuotedStringTokenizer;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public abstract class SipMessage implements SipServletMessage
{
	private static final Logger LOG = Log.getLogger(SipMessage.class);
	private static final Collection<Locale> __defaultLocale = Collections.singleton(Locale.getDefault());
	
	protected final SipFields _fields;
	
	private long _timeStamp;
	private SipConnection _connection;
	
	private boolean _committed = false;
	
	private HeaderForm _headerForm = HeaderForm.DEFAULT;
	protected String _characterEncoding;
	
	protected Session _session;
	
	protected SipMethod _sipMethod;
	protected String _method;
	
	private byte[] _content;
	
	private Attributes _attributes;
	
	private SipServletHolder _handler;
	
	private String _initialRemoteAddr;
	private int _initialRemotePort;
	private String _initialTransport;

	private UserIdentity _userIdentity;
	
	public SipMessage()
	{
		_fields = new SipFields();
	}
	
	public SipMessage(SipMessage other)
	{
		_fields = new SipFields(other._fields);
		_headerForm = other._headerForm;
		_characterEncoding = other._characterEncoding;
		_session = other._session;
		_sipMethod = other._sipMethod;
		_method = other._method;
		_content = other._content;
	}
	
	public boolean isRegister()
	{
		return getSipMethod() == SipMethod.REGISTER;
	}
	
	public boolean isInvite()
	{
		return getSipMethod() == SipMethod.INVITE;
	}
	
	public boolean isAck()
	{
		return getSipMethod() == SipMethod.ACK;
	}
	
	public boolean isCancel()
	{
		return getSipMethod() == SipMethod.CANCEL;
	}
	
	public boolean isBye()
	{
		return getSipMethod() == SipMethod.BYE;
	}
	
    public boolean isPrack()
    {
        return getSipMethod() == SipMethod.PRACK;
    }
    
	public boolean isSubscribe()
	{
		return getSipMethod() == SipMethod.SUBSCRIBE;
	}
	
	public boolean isNotify()
	{
		return getSipMethod() == SipMethod.NOTIFY;
	}
	
	public boolean isUpdate()
	{
		return getSipMethod() == SipMethod.UPDATE;
	}
	
	public boolean isMethod(SipMethod method)
	{
		return getSipMethod() == method;
	}
	
	public SipMethod getSipMethod()
	{
		return _sipMethod;
	}
		
	protected boolean isSystemHeader(SipHeader header)
	{
		return header != null && (header.isSystem() || header == SipHeader.CONTACT && !canSetContact());
	}
	
	public SipFields getFields()
	{
		return _fields;
	}
	
	public void setTimeStamp(long ts)
	{
		_timeStamp = ts;
	}
	
	public long getTimeStamp()
	{
		return _timeStamp;
	}
	
	public void setConnection(SipConnection connection)
	{
		_connection = connection;
	}
	
	public SipConnection getConnection()
	{
		return _connection;
	}
	
	public void setSession(Session session)
	{
		_session = session;
	}
	
	public abstract boolean isRequest();

	protected abstract boolean canSetContact();
	public abstract boolean needsContact();
	public abstract String toStringCompact();
	
	public abstract Transaction getTransaction(); 
	
	public Via getTopVia()
	{
		return (Via) _fields.get(SipHeader.VIA);
	}
	
	public CSeq getCSeq() 
    {
		try 
		{
			String cseq = _fields.getString(SipHeader.CSEQ);
	    	if (cseq != null)
	    		return new CSeq(cseq);
	    	return null;
		} 
		catch (ServletParseException e) 
		{
			LOG.ignore(e);
			return null;
		}
    	
    }
	
	public String getToTag()
	{
		return to().getTag();
	}
	
	public AddressImpl from()
	{
		return (AddressImpl) _fields.get(SipHeader.FROM);
	}
	
	public AddressImpl to()
	{
		return (AddressImpl) _fields.get(SipHeader.TO);
	}
	
	public Session session()
	{
		return _session;
	}
	
	public ApplicationSession appSession()
	{
		return _session.appSession();
	}
	

	public void addAcceptLanguage(Locale locale) 
	{
		addHeader(SipHeader.ACCEPT_LANGUAGE.asString(), locale.toString().replace('_','-'));
	}

	public void addAddressHeader(String name, Address address, boolean first) 
	{
		if (isCommitted())
			throw new IllegalStateException("Message is committed");
		
		SipHeader header = SipHeader.CACHE.get(name);
		if (header != null)
		{
			if (header.getType() != SipHeader.Type.ADDRESS && header.getType() != SipHeader.Type.STRING )
				throw new IllegalArgumentException("Header: " + name + " is not of address type");
	
			if (isSystemHeader(header))
				throw new IllegalArgumentException(name + " is a system header");
			
			name = header.asString();
		}
		
		if (address == null || name == null) 
			throw new NullPointerException("name or address is null");
		
		_fields.add(name, address, first);
	}
		
	/**
	 * @see SipServletMessage#addHeader(String, String)
	 */
	public void addHeader(String name, String value) 
	{
		if (isCommitted())
			throw new IllegalStateException("Message is committed");
		
		SipHeader header = SipHeader.CACHE.get(name);
		if (isSystemHeader(header))
			throw new IllegalArgumentException(name + " is a system header");
		
		if (value == null || name == null)
			throw new NullPointerException("name or value is null");
		
		_fields.add(name, value);
	}

	@Override
	public void addParameterableHeader(String name, Parameterable parameterable,
			boolean first) 
	{
		if (isCommitted())
			throw new IllegalStateException("Message is committed");
		
		SipHeader header = SipHeader.CACHE.get(name);
		if (header != null)
		{
			if (header.getType() != Type.PARAMETERABLE 
					&& header.getType() != Type.ADDRESS
					&& header.getType() != Type.VIA)
				throw new IllegalArgumentException("Header " + name + " is not of parameterable type");
			
			if (isSystemHeader(header))
				throw new IllegalArgumentException(name + " is a system header");
			
			name = header.asString();
		}
		
		if (parameterable == null || name == null) 
			throw new NullPointerException("name or address is null");
		
		_fields.add(name, parameterable, first);
	}

	@Override
	public Locale getAcceptLanguage() {
		Iterator<Locale> it = getAcceptLanguages();
		if (it.hasNext())
			return it.next();
		return null;
	}

	@Override
	public Iterator<Locale> getAcceptLanguages() 
	{
        final Iterator<String> it = getFields().getValues(SipHeader.ACCEPT_LANGUAGE.asString());

        if (!it.hasNext())
            return __defaultLocale.iterator();

        List<String> acceptLanguage = HttpFields.qualityList(
        		new Enumeration<String>() 
    			{
					public boolean hasMoreElements()
					{
						return it.hasNext();
					}

					public String nextElement()
					{
						return it.next();
					}
    			});
        
        if (acceptLanguage.size() == 0)
            return __defaultLocale.iterator();
        
        Object langs = null;
        int size = acceptLanguage.size();
        
        for (int i = 0; i < size; i++)
        {
            String language = acceptLanguage.get(i);
            language = HttpFields.valueParameters(language, null);
            String country = "";
            int dash = language.indexOf('-');
            if (dash > -1)
            {
                country = language.substring(dash + 1).trim();
                language = language.substring(0, dash).trim();
            }
            langs = LazyList.ensureSize(langs, size);
            langs = LazyList.add(langs, new Locale(language, country));
        }
        
        if (LazyList.size(langs) == 0)
            return __defaultLocale.iterator();
        
        List<Locale> l = LazyList.getList(langs);
        return l.iterator();
	}

	@Override
	public Address getAddressHeader(String name) throws ServletParseException
	{
		SipHeader header = SipHeader.CACHE.get(name);
		
		if (header != null && (header.getType() != SipHeader.Type.ADDRESS && header.getType() != SipHeader.Type.STRING))
			throw new ServletParseException("Header: " + name + " is not of address type");
		
		Field field;
		if (header != null)
			field = _fields.getField(header);
		else
			field = _fields.getField(name);
		
		if (field == null)
			return null;
		
		Address address = field.asAddress();
		if (header == SipHeader.CONTACT && isSystemHeader(header) && !isCommitted())
			return new ContactAddress(address);
		return isSystemHeader(header) || isCommitted() ? new ReadOnlyAddress(address) : address;
	}

	@Override
	public ListIterator<Address> getAddressHeaders(String name)
			throws ServletParseException 
	{
		SipHeader header = SipHeader.CACHE.get(name);
				
		if (header != null && (header.getType() != SipHeader.Type.ADDRESS && header.getType() != SipHeader.Type.STRING))
			throw new ServletParseException("Header: " + name + " is not of address type");
		
		ListIterator<Address> it = _fields.getAddressValues(header, name);
		
		if (isSystemHeader(header) || isCommitted())
		{
			return new ListIteratorProxy<Address>(it)
			{
				@Override
				public Address next() { return new ReadOnlyAddress(super.next()); }
				@Override
				public Address previous() { return new ReadOnlyAddress(super.previous()); }
			};
		}
		return it;
	}

	@Override
	public SipApplicationSession getApplicationSession()
	{
		if (_session == null)
			return null; 
		return new ScopedAppSession(_session.appSession());
	}

	@Override
	public SipApplicationSession getApplicationSession(boolean create)
	{
		return getApplicationSession();
	}

	@Override
	public Object getAttribute(String name) 
	{
		return _attributes == null ? null : _attributes.getAttribute(name);
	}

	@Override
	public Enumeration<String> getAttributeNames() 
	{
		if (_attributes == null) 
			return Collections.emptyEnumeration();
		
		return AttributesMap.getAttributeNamesCopy(_attributes);
	}

	/**
	 * @see SipServletMessage#getCallId()
	 */
	public String getCallId() 
	{
		return _fields.getString(SipHeader.CALL_ID);
	}

	@Override
	public String getCharacterEncoding() 
	{
		if (_characterEncoding != null)
            return _characterEncoding;
        String contentType = getContentType();
        
        if (contentType != null)
        {
            int i0 = contentType.indexOf(';');
            if (i0 > 0)
            {
                int i1 = contentType.indexOf("charset=", i0 + 1);
                if (i1 >= 0)
                {
                    int i8 = i1+8;
                    int i2 = contentType.indexOf(';',i8);
                    
                    if (i2 > 0)
                        _characterEncoding = QuotedStringTokenizer.unquote(contentType.substring(i8, i2));
                    else 
                        _characterEncoding = QuotedStringTokenizer.unquote(contentType.substring(i8));
                }
            }
        }
        return _characterEncoding; // TODO contentlanguage ?
	}

	@Override
	public Object getContent() throws IOException, UnsupportedEncodingException
	{
		String contentType = getContentType();
		if (_content != null
				&& contentType != null
				&& (StringUtil.startsWithIgnoreCase(contentType, "text") || contentType
						.equalsIgnoreCase("application/sdp")))
		{
			String charset = getCharacterEncoding();
			if (charset == null)
				charset = StringUtil.__UTF8;

			return new String(_content, charset);
		}
		else
		{
			return _content;
		}
	}

	@Override
	public Locale getContentLanguage() 
	{
		String s = getHeader(SipHeader.CONTENT_LANGUAGE.asString());
		if (s == null) 
			return null;
		
		return new Locale(s);
	}

	@Override
	public int getContentLength() 
	{
		int length = (int) _fields.getLong(SipHeader.CONTENT_LENGTH);
		if (length == -1) 
		{
			if (_content == null) 
				return 0;
			else 
				return _content.length;
		} 
		else 
		{
			return length;
		}
	}

	@Override
	public String getContentType() 
	{
		return  getHeader(SipHeader.CONTENT_TYPE.asString()); // TODO parse;
	}

	@Override
	public int getExpires() 
	{
		return (int) _fields.getLong(SipHeader.EXPIRES);
	}

	/**
	 * @see SipServletMessage#getFrom()
	 */
	public Address getFrom() 
	{
		return  new ReadOnlyAddress((Address) _fields.get(SipHeader.FROM));
	}

	/**
	 * @see SipServletMessage#getHeader(String)
	 */
	public String getHeader(String name) 
	{
		if (name == null)
			throw new NullPointerException("name is null");
		return _fields.getString(name);
	}

	@Override
	public HeaderForm getHeaderForm() 
	{
		return _headerForm;
	}

	@Override
	public Iterator<String> getHeaderNames() 
	{
		return _fields.getNames();
	}

	@Override
	public ListIterator<String> getHeaders(String name)
	{
		return _fields.getValues(name);
	}

	@Override
	public String getInitialRemoteAddr()
	{
		if (_initialRemoteAddr == null)
			return getRemoteAddr();
		return _initialRemoteAddr;
	}

	@Override
	public int getInitialRemotePort()
	{
		if (_initialRemoteAddr == null)
			return getRemotePort();
		return _initialRemotePort;
	}

	@Override
	public String getInitialTransport()
	{
		if (_initialTransport == null)
			return getTransport();
		return _initialTransport;
	}

	@Override
	public String getLocalAddr() 
	{
		return _connection != null ?_connection.getLocalAddress().getHostAddress() : null;
	}

	@Override
	public int getLocalPort() 
	{
		return _connection != null ? _connection.getLocalPort() : -1;
	}


	@Override
	public Parameterable getParameterableHeader(String name) throws ServletParseException
	{
		SipHeader header = SipHeader.CACHE.get(name);
				
		Field field;
		if (header != null)
		{
			if (header.getType() != Type.PARAMETERABLE 
					&& header.getType() != Type.ADDRESS
					&& header.getType() != Type.VIA)
				throw new ServletParseException("Header " + name + " is not of parameterable type");
			
			field = _fields.getField(header);
		}
		else
			field = _fields.getField(name);
		
		if (field == null)
			return null;
		
		Parameterable p = field.asParameterable();
		return isSystemHeader(header) ? new ReadOnlyParameterable(p) : p;
	}

	@Override
	public ListIterator<? extends Parameterable> getParameterableHeaders(String name) throws ServletParseException 
	{
		SipHeader header = SipHeader.CACHE.get(name);
		
		if (header != null)
		{
			if (header.getType() != Type.PARAMETERABLE 
					&& header.getType() != Type.ADDRESS
					&& header.getType() != Type.VIA)
				throw new ServletParseException("Header " + name + " is not of parameterable type");
		}
		
		ListIterator<Parameterable> it = _fields.getParameterableValues(header, name);
		
		if (isSystemHeader(header) || isCommitted())
		{
			return new ListIteratorProxy<Parameterable>(it)
			{
				@Override
				public Parameterable next() { return new ReadOnlyParameterable(super.next()); }
				@Override
				public Parameterable previous() { return new ReadOnlyParameterable(super.previous()); }
			};
		}
		return it;
	}

	@Override
	public String getProtocol() 
	{
		return SipVersion.SIP_2_0.asString();
	}

	@Override
	public byte[] getRawContent() 
	{
		return _content;
	}

	/**
	 * @see SipServletMessage#getRemoteAddr()
	 */
	public String getRemoteAddr() 
	{
		return _connection != null ? _connection.getRemoteAddress().getHostAddress() : null;
	}

	/**
	 * @see SipServletMessage#getRemotePort()
	 */
	public int getRemotePort() 
	{
		return _connection != null ? _connection.getRemotePort() : -1;
	}

	@Override
	public String getRemoteUser() 
	{
		if (_userIdentity != null)
			return _userIdentity.getUserPrincipal().getName(); // TODO check that this returns every time the right value
		return null;
	}

	/**
	 * @see SipServletMessage#getSession()
	 */
	public SipSession getSession() 
	{
		return new ScopedSession(_session);
	}

	/**
	 * 
	 */
	public SipSession getSession(boolean create) 
	{
		return getSession();
	}

	@Override
	public Address getTo() 
	{
		return  new ReadOnlyAddress((Address) _fields.get(SipHeader.TO));
	}

	/**
	 * @see SipServletMessage#getTransport()
	 */
	public String getTransport() 
	{
		if (_connection == null)
			return null;
		return _connection.getTransport().getName();
	}

	@Override
	public Principal getUserPrincipal() 
	{
		if (_userIdentity != null)
			return _userIdentity.getUserPrincipal();
		return null;
	}

	/**
	 * @see SipServletMessage#isCommitted()
	 */
	public boolean isCommitted() 
	{
		return _committed;
	}
	
	public void setCommitted(boolean b)
	{
		_committed = b;
	}

	@Override
	public boolean isSecure() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isUserInRole(String role) 
	{
		if (_userIdentity != null)
			return _userIdentity.isUserInRole(role, getHandler());
		return false;
	}

	@Override
	public void removeAttribute(String name)
	{
		if (_attributes != null)
			_attributes.removeAttribute(name);

	}

	@Override
	public void removeHeader(String name)
	{
		if (isCommitted())
			throw new IllegalStateException("Message is committed");
		
		SipHeader header = SipHeader.CACHE.get(name);
		if (header != null)
		{
			if (isSystemHeader(header))
				throw new IllegalArgumentException(name + " is a system header");
			
			name = header.asString();
		}
		
		if (name == null) 
			throw new NullPointerException("name is null");
		
		_fields.remove(name);
	}


	@Override
	public void setAcceptLanguage(Locale locale) 
	{
		setHeader(SipHeader.ACCEPT_LANGUAGE.asString(), locale.toString().replace('_', '-'));
	}

	@Override
	public void setAddressHeader(String name, Address address)
	{
		if (isCommitted())
			throw new IllegalStateException("Message is committed");
		
		SipHeader header = SipHeader.CACHE.get(name);
		if (header != null)
		{
			if (header.getType() != SipHeader.Type.ADDRESS && header.getType() != SipHeader.Type.STRING )
				throw new IllegalArgumentException("Header: " + name + " is not of address type");
			
			if (isSystemHeader(header))
				throw new IllegalArgumentException(name + " is a system header");
			
			name = header.asString();
		}
		
		if (name == null) 
			throw new NullPointerException("name is null");
		
		_fields.set(name, address);
	}

	@Override
	public void setAttribute(String name, Object value) 
	{
		if (value == null || name == null) 
			throw new NullPointerException("name or value is null");
		
		if (_attributes == null) 
			_attributes = new AttributesMap();
		_attributes.setAttribute(name, value);
	}

	@Override
	public void setCharacterEncoding(String encoding)
			throws UnsupportedEncodingException
	{
		"".getBytes(encoding);
		_characterEncoding = encoding;
	}

	@Override
	public void setContent(Object o, String type) throws UnsupportedEncodingException
	{
		// TODO add sdp, ...
		if (isCommitted())
			throw new IllegalStateException("Is committed");

		if (o == null)
		{
			_content = null;
			setContentLength(0);
			setContentType(type);
		}
		else if (o instanceof byte[])
		{
			_content = (byte[]) o;
			setContentLength(_content.length);
			setContentType(type);
		}
		else if (o instanceof String)
		{
			String s = (String) o;
			setContentType(type);
			String charset = getCharacterEncoding();
			if (charset == null)
				charset = StringUtil.__UTF8;

			_content = s.getBytes(charset);
			setContentLength(_content.length);
		}
		else
		{
			throw new IllegalArgumentException("Unsupported object type");
		}
	}

	@Override
	public void setContentLanguage(Locale locale) 
	{
		if (locale == null)
        {
            removeHeader(SipHeader.CONTENT_LANGUAGE.asString());
        }
        else 
        {
            setHeader(SipHeader.CONTENT_LANGUAGE.asString(), locale.toString().replace('_','-'));
// FIXME         if (_characterEncoding == null)
//                _characterEncoding = _session.appSession().getContext().getLocaleEncoding(locale);
        }
	}

	@Override
	public void setContentLength(int length) 
	{
		if (isCommitted())
			throw new IllegalStateException("Message is committed");
		
		setHeader(SipHeader.CONTENT_LENGTH.asString(), Integer.toString(length));
	}

	@Override
	public void setContentType(String contentType) 
	{
		if (isCommitted())
			throw new IllegalStateException("Message is committed");
        if (contentType == null)
            getFields().remove(SipHeader.CONTENT_TYPE.asString());      
        else
        {
            int i0 = contentType.indexOf(';');
            if (i0 > 0)
            {
                int i1 = contentType.indexOf("charset=", i0 + 1);
                if (i1 >= 0)
                {
                    int i8 = i1+8;
                    int i2 = contentType.indexOf(';',i8);
                    
                    if (i2 > 0)
                        _characterEncoding = QuotedStringTokenizer.unquote(contentType.substring(i8, i2));
                    else 
                        _characterEncoding = QuotedStringTokenizer.unquote(contentType.substring(i8));
                }
            }
            getFields().set(SipHeader.CONTENT_TYPE, contentType);	
        }
	}

	@Override
	public void setExpires(int seconds) 
	{
		if (seconds < 0) 
			removeHeader(SipHeader.EXPIRES.asString());
		else 
			setHeader(SipHeader.EXPIRES.asString(), Long.toString(seconds));
	}

	@Override
	public void setHeader(String name, String value) 
	{
		if (isCommitted())
			throw new IllegalStateException("Message is committed");
		
		SipHeader header = SipHeader.CACHE.get(name);
		if (isSystemHeader(header))
			throw new IllegalArgumentException(name + " is a system header");
		
		if (value == null || name == null)
			throw new NullPointerException("name or value is null");
		
		_fields.set(name, value);
	}

	@Override
	public void setHeaderForm(HeaderForm form)
	{
		if (form == null)
			throw new NullPointerException("Null form");
		_headerForm = form;
	}

	@Override
	public void setParameterableHeader(String name, Parameterable parameterable)
	{
		if (isCommitted())
			throw new IllegalStateException("Message is committed");
		
		SipHeader header = SipHeader.CACHE.get(name);
		if (header != null)
		{
			if (header.getType() != Type.PARAMETERABLE 
					&& header.getType() != Type.ADDRESS
					&& header.getType() != Type.VIA)
				throw new IllegalArgumentException("Header " + name + " is not of parameterable type");
			
			if (isSystemHeader(header))
				throw new IllegalArgumentException(name + " is a system header");
			
			name = header.asString();
		}
		
		if (name == null) 
			throw new NullPointerException("name is null");
		
		_fields.set(name, parameterable);
	}

	public SipServletHolder getHandler()
	{
		if (_handler != null)
			return _handler;
		SipServletHolder holder = session().getHandler();
		if (holder == null && isRequest())
		{
			holder = appSession().getContext().getServletHandler().getHolder((SipRequest) this);
		}
		return holder;
	}

	public void setHandler(SipServletHolder handler)
	{
		_handler = handler;
	}

	public void setInitialRemoteAddr(String initialRemoteAddr)
	{
		_initialRemoteAddr = initialRemoteAddr;
	}

	public void setInitialRemotePort(int initialRemotePort)
	{
		_initialRemotePort = initialRemotePort;
	}

	public void setInitialTransport(String initialTransport)
	{
		_initialTransport = initialTransport;
	}
	
	public UserIdentity getUserIdentity()
	{
		return _userIdentity;
	}

	public void setUserIdentity(UserIdentity userIdentity)
	{
		_userIdentity = userIdentity;
	}
	
	public Via removeTopVia() 
	{
		Via via = (Via) _fields.removeFirst(SipHeader.VIA);
		return via;
	}

}

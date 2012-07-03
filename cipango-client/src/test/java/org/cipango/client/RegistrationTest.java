// ========================================================================
// Copyright 2011 NEXCOM Systems
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

package org.cipango.client;

import static org.cipango.client.Constants.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.text.ParseException;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;

import org.cipango.sip.SipHeader;
import org.cipango.sip.SipMethod;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

public class RegistrationTest
{
    Mockery _context = new JUnit4Mockery();
	SipClient _client;
	SipURI _uri = ALICE_URI;
	
	@Test
	public void testConstructor() throws ParseException
	{
		Registration r = new Registration(_uri);
		assertThat(r.getFactory(), is(nullValue()));
		assertThat(r.getCredentials(), is(nullValue()));
		assertThat(r.getURI(), is(sameInstance(_uri)));
	}
	
	@Test
	public void testConstructorWithNoURI()
	{
		Registration r = new Registration(null);
		assertThat(r.getFactory(), is(nullValue()));
		assertThat(r.getCredentials(), is(nullValue()));
		assertThat(r.getURI(), is(nullValue()));
	}
	
	@Test
	public void testSetFactory() throws ParseException
	{
		final SipFactory factory = _context.mock(SipFactory.class);
		Registration r = new Registration(_uri);
		r.setFactory(factory);
		assertThat(r.getFactory(), is(sameInstance(factory)));
	}

	@Test
	public void testSetCredentials() throws ParseException
	{
		final Credentials credentials = _context.mock(Credentials.class);
		Registration r = new Registration(_uri);
		r.setCredentials(credentials);
		assertThat(r.getCredentials(), is(sameInstance(credentials)));
	}

	@Test
	public void testCreateRegister() throws ServletParseException
	{
		final SipApplicationSession appSession = _context.mock(SipApplicationSession.class);
		final SipFactory factory = _context.mock(SipFactory.class);
		final SipServletRequest request = _context.mock(SipServletRequest.class);
		
		_context.checking(new Expectations() {{
			allowing(request).getSession(); will(returnValue(null));
			oneOf(request).setRequestURI(with(any(URI.class)));
			oneOf(request).setAddressHeader(with(equal(SipHeader.CONTACT.asString())), with(any(Address.class)));
			// Can't get this matching...
			//oneOf(request).setAddressHeader(with(equal(SipHeader.CONTACT.asString())), with(equal(new AddressImpl(BOB_URI))));
			oneOf(request).setExpires(1800);

			allowing(factory).createSipURI(with(any(String.class)), with(any(String.class))); will(returnValue(EXAMPLE_URI));
	        oneOf(factory).createApplicationSession(); will(returnValue(appSession));
	        oneOf(factory).createRequest(appSession, SipMethod.REGISTER.asString(), ALICE_URI, ALICE_URI); will(returnValue(request));
	    }});
		
		Registration r = new Registration(_uri);
		r.setFactory(factory);
		SipServletRequest req = r.createRegister(BOB_URI, 1800);

		assertThat(req, is(sameInstance(request)));
		
		// TODO: test a second invocation (the session now exists).
	}

	@Test
	public void testCreateRegisterWithAuthentication() throws ServletParseException
	{
		// TODO
	}
	
	@Test(expected=NullPointerException.class) 
	public void testCreateRegisterWithNoFactory()
	{
		final SipURI contact = _context.mock(SipURI.class, "contact");
		Registration r = new Registration(_uri);
		SipServletRequest request = r.createRegister(contact, 3600);
		
		// TODO
	}
	
	//  The following is to be moved in separate project.
	
//	@Before
//	public void start() throws Exception
//	{
//		_client = new SipClient("127.0.0.1", 5060);
//		_client.start();
//	}
//	
//	@After
//	public void stop() throws Exception
//	{
//		_client.stop();
//	}
//	
//	@Test
//	public void testRegistration() throws Exception
//	{
//		Registration registration = new Registration(new SipURIImpl("sip:alice@127.0.0.1"));
//		
//		SipServletRequest register = registration.createRegister(_client.getContact(), 3600);
//		register.addHeader("X-biduke", "");
//		
//		SipServletResponse response = _client.waitforResponse(register);
//		assertThat(response, isSuccess());
//		
//	}
}

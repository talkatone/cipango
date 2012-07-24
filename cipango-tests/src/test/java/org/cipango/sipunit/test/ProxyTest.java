// ========================================================================
// Copyright 2010 NEXCOM Systems
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
package org.cipango.sipunit.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.cipango.sipunit.test.matcher.SipMatchers.*;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import org.cipango.client.Call;
import org.cipango.client.MessageHandler;
import org.cipango.client.SipMethods;
import org.cipango.sipunit.UaRunnable;
import org.cipango.sipunit.UaTestCase;
import org.cipango.sipunit.UasScript;
import org.junit.Test;

public class ProxyTest extends UaTestCase
{
	/**
	 * <pre>
	 *  Alice        Proxy       Bob        Carol        HSS
	 * 1  | INVITE     |          |           |           |
	 *    |----------->|          |           |           |
	 * 2  |            | INVITE   |           |           |
	 *    |            |--------->|           |           |
	 * 3  |            |      404 |           |           |
	 *    |            |<---------|           |           |
	 * 4  |            | ACK      |           |           |
	 *    |            |--------->|           |           |
	 * 5  |            | UDR      |           |           |
	 *    |            |--------------------------------->|
	 * 6  |            |          |           |       UDA |
	 *    |            |<---------------------------------|
	 * 7  |            | INVITE   |           |           |
	 *    |            |--------------------->|           |
	 * 8  |            |          |       200 |           |
	 *    |            |<---------------------|           |
	 * 9  |        200 |          |           |           |
	 *    |<-----------|          |           |           |
	 * 10 | ACK        |          |           |           |
	 *    |----------->|          |           |           |
	 * 11 |            | ACK      |           |           |
	 *    |            |--------------------->|           |
	 * 12 | BYE        |          |           |           |
	 *    |----------->|          |           |           |
	 * 13 |            | BYE      |           |           |
	 *    |            |--------------------->|           |
	 * 14 |            |          |       200 |           |
	 *    |            |<---------------------|           |
	 * 15 |        200 |          |           |           |
	 *    |<-----------|          |           |           |
	 * </pre>
	 */
	@Test
	public void testProxyDiameter() throws Throwable
	{
		Call callA;
		UaRunnable callB = new UasScript.NotFound(getBobUserAgent());
		UaRunnable callC = new UasScript.OkBye(getCarolUserAgent());

		callB.start();
		callC.start();
		
		SipServletRequest request = _ua.createRequest(SipMethods.INVITE, getBobUri());
		request.setRequestURI(getBobContact().getURI());
		request.addHeader("proxy", getCarolContact().toString());
		callA = _ua.createCall(request);

		try 
		{			
	        assertThat(callA.waitForResponse(), isSuccess());
	        callA.createAck().send();	        
	        Thread.sleep(200);
	        callA.createBye().send();
	        assertThat(callA.waitForResponse(), isSuccess());
	        callB.assertDone();
			callC.assertDone();
		}
		finally
		{
			checkForFailure();
		}
	}
	
	/**
	 * <pre>
	 *  Alice        Proxy         Bob
	 * 1  | INVITE     |            |
	 *    |----------->|            |
	 * 2  |            | INVITE     |
	 *    |            |----------->|
	 * 3  |            |        180 |
	 *    |            |<-----------|
	 * 4  |        180 |            |
	 *    |<-----------|            |
	 * 5  |            | CANCEL     |
	 *    |            |----------->|
	 * 6  |        408 |            |
	 *    |<-----------|            |
	 * 7  |            | 200/CANCEL |
	 *    |            |<-----------|
	 * 8  |            | 487/INVITE |
	 *    |            |<-----------|
	 * 9  |            | ACK        |
	 *    |            |----------->|
	 * 10 | ACK        |            |
	 *    |----------->|            |
	 * </pre>
	 */
	@Test
	public void testVirtualBranch() throws Exception
	{
		Call callA;
		UaRunnable callB = new UasScript.RingingCanceled(getBobUserAgent());
		
		callB.start();
		
		SipServletRequest request = _ua.createRequest(SipMethods.INVITE, getBobUri());
		request.setRequestURI(getBobContact().getURI());
		callA = _ua.createCall(request);

		try 
		{
	        assertThat(callA.waitForResponse(), hasStatus(SipServletResponse.SC_REQUEST_TIMEOUT));
		}
		finally
		{
			checkForFailure();
		}
	}
	
	/**
	 * Ensure that the servlet is not invoked if session is invalidated.
	 * <p> 
	 * See http://jira.cipango.org/browse/CIPANGO-121
	 * 
	 * <pre>
	 *  Alice        Proxy       Bob
	 * 1  | INVITE     |          |
	 *    |----------->|          |
	 * 2  |            | INVITE   |
	 *    |            |--------->|
	 * 3  |            |      200 |
	 *    |            |<---------|
	 * 4  |        200 |          |
	 *    |<-----------|          |
	 * 5  | ACK        |          |
	 *    |----------->|          |
	 * 6  |            | ACK      |
	 *    |            |--------->|
	 * 7  | BYE        |          |
	 *    |----------->|          |
	 * 8  |            | BYE      |
	 *    |            |--------->|
	 * 9  |            |      200 |
	 *    |            |<---------|
	 * 10 |        200 |          |
	 *    |<-----------|          |
	 * </pre>
	 */
	@Test
	public void testInvalidateBefore200() throws Exception
	{
		Call callA;
		UaRunnable callB = new UasScript.OkBye(getBobUserAgent());

		callB.start();
		
		SipServletRequest request = _ua.createRequest(SipMethods.INVITE, getBobUri());
		request.setRequestURI(getBobContact().getURI());
		callA = _ua.createCall(request);
        assertThat(callA.waitForResponse(), isSuccess());
		callA.createAck().send();
        callA.createBye().send();
        
        SipServletResponse response = callA.waitForResponse();
        assertThat(response, isSuccess());
        assertThat(response.getHeader("error"), is(nullValue()));
	}
	
	/**
	 * <pre>
	 *  Alice         Proxy             Proxy       Bob
	 * 1  | INVITE      |                 |          |
	 *    |------------>|                 |          |
	 * 2  |             |INVITE tel:1234  |          |
	 *    |             |---------------->|          |
	 * 3  |             |                 | INVITE   |
	 *    |             |                 |--------->|
	 * 4  |             |                 |      603 |
	 *    |             |                 |<---------|
	 * 5  |             |             603 |          |
	 *    |             |<----------------|          |
	 * 6  |         603 |                 |          |
	 *    |<------------|                 |          |
	 * 7  | ACK         |                 |          |
	 *    |------------>|                 |          |
	 * 8  |             |ACK              |          |
	 *    |             |---------------->|          |
	 * 9  |             |                 | ACK      |
	 *    |             |                 |--------->|
	 * </pre>
	 */
	@Test
	public void testTelUri() throws Exception
	{
		Call call;
		
		getBobUserAgent().setDefaultHandler(new MessageHandler() {
			public void handleRequest(SipServletRequest request)
					throws IOException, ServletException
			{
				assertThat(request.getHeader("req-uri"), containsString("tel:1234"));
				getBobUserAgent().createResponse(request,
						SipServletResponse.SC_DECLINE).send();
			}

			public void handleResponse(SipServletResponse response)
					throws IOException, ServletException
			{
			}
		});
		
		SipServletRequest request = _ua.createRequest(SipMethods.INVITE, getBobUri());
		request.setRequestURI(getBobContact().getURI());
		call = _ua.createCall(request);
		assertThat(call.waitForResponse(), hasStatus(SipServletResponse.SC_DECLINE));
	}
}

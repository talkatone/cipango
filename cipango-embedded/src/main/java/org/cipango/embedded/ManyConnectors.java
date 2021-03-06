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

package org.cipango.embedded;

import org.cipango.server.SipConnector;
import org.cipango.server.SipServer;
import org.cipango.server.log.FileMessageLog;
import org.cipango.server.nio.SelectChannelConnector;
import org.cipango.server.nio.UdpConnector;
import org.cipango.server.sipapp.SipAppContext;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.ArrayUtil;
import org.eclipse.jetty.webapp.WebAppContext;

public class ManyConnectors 
{
	public static void main(String[] args) throws Exception
    {
		SipServer sipServer = new SipServer();
		Server server = new Server();
		sipServer.setServer(server);
				
		UdpConnector udp = new UdpConnector(sipServer);
		SelectChannelConnector tcp = new SelectChannelConnector(sipServer);
		
		int port = Integer.getInteger("sip.port", 5060);
		udp.setPort(port);
		tcp.setPort(port);
		
		sipServer.setConnectors(new SipConnector[] { udp, tcp });
		sipServer.setAccessLog(new FileMessageLog());
		
		SipAppContext sipapp = new SipAppContext();
		
		WebAppContext webapp = new WebAppContext();
		sipapp.setWebAppContext(webapp);
		webapp.setContextPath("/");
		
		String[] classes = ArrayUtil.addToArray(WebAppContext.getDefaultConfigurationClasses(), 
				"org.cipango.server.sipapp.SipXmlConfiguration",
				String.class);
		webapp.setConfigurationClasses(classes);
		
		String war = (args.length == 0) ? "../cipango-example-sipapp/target/cipango-example-sipapp.war" : args[0];
		
		webapp.setWar(war);
		
		sipServer.setHandler(sipapp);
		server.setHandler(webapp);
		
		server.start();
		server.join();
    }
}

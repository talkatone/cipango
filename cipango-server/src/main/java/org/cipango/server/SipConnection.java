package org.cipango.server;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public interface SipConnection 
{
	Transport getTransport();
	
	InetAddress getLocalAddress();
	int getLocalPort();
	
	InetAddress getRemoteAddress();
	int getRemotePort();
	
	void send(SipMessage message);
	void write(ByteBuffer buffer) throws IOException;
}
package org.cipango.sip;

import java.io.IOException;
import java.text.ParseException;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.cipango.util.StringScanner;
import org.cipango.util.StringUtil;

public class Parameters 
{
	private final static BitSet END_PNAME = StringUtil.toBitSet(" \t;=");
	private final static BitSet END_PVALUE = StringUtil.toBitSet(" \t;");
	
	private Map<String, String> _parameters;

	public String getParameter(String name) 
	{
		return _parameters != null ? _parameters.get(name) : null;
	}

	public Iterator<String> getParameterNames() 
	{
		return Collections.unmodifiableSet(_parameters.keySet()).iterator();
	}

	public Set<Entry<String, String>> getParameters() 
	{
		return Collections.unmodifiableSet(_parameters.entrySet());
	}

	public void removeParameter(String name) 
	{
		if (_parameters != null)
			_parameters.remove(name);		
	}

	public void setParameter(String name, String value) 
	{
		if (_parameters == null)
			_parameters = new HashMap<String, String>();
		_parameters.put(name, value);		
	}
	
	public void parameterParsed(String name, String value)
	{
		setParameter(name, value);
	}
	
	public void parseParameters(StringScanner scanner) throws ParseException
	{
		while (!scanner.eof())
		{
			int start = scanner.position();
			scanner.skipWhitespace();
			if (!scanner.eof() && scanner.peekChar() == ';')
			{
				scanner.skipChar().skipWhitespace();
				String key = scanner.readTo(END_PNAME);
				
				if (key.length() > 0)
				{
					String value = "";
					
					scanner.skipWhitespace();
					if (!scanner.eof() && scanner.peekChar() == '=')
					{
						scanner.skipChar().skipWhitespace(); 
						
						if (!scanner.eof() && scanner.peekChar() == '"')
							value = StringUtil.unquote(scanner.readQuoted());
						else
							value = scanner.readTo(END_PVALUE);
					}
					
					parameterParsed(key, value);
				}
			}
			else
			{
				scanner.position(start);
				return;
			}
		}
	}
	
	public void appendParameters(Appendable appendable) throws IOException
	{
		if (_parameters != null)
		{
			for (String name : _parameters.keySet())
			{
				String value = _parameters.get(name);
				if (value != null)
				{
					appendable.append(';');
					appendable.append(name);
					appendable.append('=');
					appendable.append(value);
				}
			}
		}
	}
}
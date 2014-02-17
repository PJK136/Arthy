package org.pjk136.Arthy;

public final class SMSDecoder {
	private static DatabaseSMS m_database; 
	
	static
	{
		m_database = DatabaseSMS.getInstance();
	}
	
	private SMSDecoder()
	{
		
	}
	
	static public String decoderPhrase(String phrase)
	{
		String decode = phrase.replace("&", " et ").replace("+", " plus ").replace("@", "a").replace("’", "'")
				.replace("œ", "oe").replace("Œ", "OE")
				.replace("æ", "ae").replace("Æ", "AE");
		for (String sms : Sanitizer.split(decode))
		{
			String token = Sanitizer.sanitize(sms);
			if (token.equalsIgnoreCase("sms")|| token.equalsIgnoreCase("mot"))
				continue;
			String mot = m_database.decoderMot(token);
			if (!mot.equals(token))
			{
				if (token.length() > 1)
					decode = decode.replaceFirst("\\b"+token+"\\b", mot);
				else
					decode = decode.replaceFirst(" "+token+" ", " "+mot+" ");
			}
		}
		return decode;
	}
	
}

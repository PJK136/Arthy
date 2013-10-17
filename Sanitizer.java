import java.util.StringTokenizer;
import java.util.regex.Pattern;


public final class Sanitizer {
	private Sanitizer()
	{
		
	}
	
	static public String removeEmoticone(String message)
	{
		String sansEmoticone = new String(message);
		StringTokenizer tokens = new StringTokenizer((":-) :) (: :o) :] :3 :c) :> =] 8) =) :} :^) "
				+ ":-D :D 8-D 8D x-D xD =-D =D =-3 =3 B^D ;D ;-D:-)) "
				+ " >:[ :-( :( ): :-c :c :-< :< :-[ :[ :{ "
				+ ":-|| :@ >:( :'-( :'( :'-) :') QQ D:< D: D8 D; D= DX v.v D-': "
				+ ">:O :-O :O °o° °O° :O o_O o_0 o.O Oo 8-0 :* :^* "
				+ ";-) ;) *-) *) ;-] ;] ;D ;^) :-, ^^ ^_^ "
				+ ">:P :-P :P X-P x-p xp XP :-p :p =p :-b :b ;P ;b "
				+ ">:\\ >:/ :-/ :-. :/ :\\ =/ =\\ :L =L :S >.< >< "
				+ ":| :-| :$ :-X :X :-# :# O:-) 0:-3 0:3 0:-) 0:) 0;^) "
				+ ">:) >;) >:-) }:-) }:) 3:-) 3:) o/\\o ^5 >_> <_< "
				+ "._. -_- =_= ==' =.= --' --\" ,,|,, \\m/ x) *v*"
				+ "|;-) |-O :-& :& #-) %-) %) <:-| \\o/ *\\0/* <3 </3").toLowerCase());
		while (tokens.hasMoreTokens())
		{
			String token = tokens.nextToken();
			sansEmoticone = sansEmoticone.replaceAll("(?i) " + Pattern.quote(token), " ");
			sansEmoticone = sansEmoticone.replaceAll("(?i)" + Pattern.quote(token) + " ", " ");
			sansEmoticone = sansEmoticone.replaceAll("(?i)" + Pattern.quote(token) + "$", " ");
		}
		return sansEmoticone;
	}
	
	static public String punctuation_to_whitespace(String phrase)
	{
		return removeEmoticone(phrase).replaceAll("[()\\[\\],.:;!?|_]…", " ");
	}
	
	static public String sanitize(String phrase)
	{
		return punctuation_to_whitespace(phrase).replaceAll("(?iu)[^a-z0-9 éèêëâàôûùîïçœ€\\-']", "");
	}

	static public String format(String original)
	{
		String phrase = original.trim();
		if (phrase.isEmpty())
			return "";
		
		if (phrase.endsWith(".") || phrase.endsWith("?") || phrase.endsWith("!") || phrase.endsWith(";") || phrase.endsWith(",") ||
			phrase.endsWith(":") || phrase.endsWith("…")) 
			return Character.toUpperCase(phrase.charAt(0)) + phrase.substring(1).replace("( )+", " ");
		else
			return Character.toUpperCase(phrase.charAt(0)) + phrase.substring(1, phrase.length()).replace("( )+", " ") + ".";
	}
}

package org.pjk136.Arthy;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

public final class Sanitizer {
	private Sanitizer()
	{

	}

	static private String[][] emoticones = {
	{":-)", ">:)",":)", "*:))", "(:", ":o)", ":]","=]", "=)", ":}", ":^)",":c)",">:-)", "}:-)", "}:)","0:-)", "0:)","O:-)","#-)", ":>", "^^", "^_^"},
	{":-D", ":D", "x-D", "xD", "=-D", "=D", "x)", "B^D", ":'-)", ":')", "|-O"},
	{"8-D", "8D", "8)"},
	{";D", ";-D", "|;-)",";-)", ";)", "*-)", "*)", ";-]", ";]", ";D", ";^)",">;)", ":-,"},
	{"._.", "-_-", "=_=", "=='", "=.=", "--'", "--\"", "u_u","v.v", ">_>", "<_<"},
	{">:P", ":-P", ":P", "X-P", "x-p", "XP", "=P", ":-b", ":b", ";P", ";b", ":-&", ":&"},
	{">:O", ":-O", ":O", "°o°", "°O°", ":O", "o_O", "o_0", "o.O", "Oo", "8-0"},
	{":-(", ":(", "):", ":-C", ":C", ":-<", ":<", ":-[", ":[", ":{", ":'-(",":'(", "QQ", "D:<", "D:", "D;", "D=", "DX","D-':"},
	{":|", ":-|", ":-/",":-||","<:-|", ":/"},
	{":S", ":-S", "=S", ":$", "=$" ,":-#", ":#", ":-X",":X"},
	{"0:-3", "0:3", ":3", "x3", "=-3", "=3","0:-3", "0:3"},
	{"3:-)", "3:)"},
	{"<3", "</3"},
	{":@", ">:[", ">:\\", ">:/", ">:("},
	{":*", ":^*"},
	{"%-)", "%)"},
	{"\\o/", "*\\0/*"}};

	static public String removeEmoticones(String message)
	{
		for (String[] groupes_emoticones : emoticones)
		{
			for (String emoticone : groupes_emoticones)
			{
				emoticone = Pattern.quote(emoticone.substring(0, emoticone.length()-1)) + "(" + Pattern.quote(emoticone.substring(emoticone.length()-1)) + ")+";
				message = message.replaceAll("(?i)^" + emoticone, " ");
				message = message.replaceAll("(?i) " + emoticone, " ");
				/*message = message.replaceAll("(?i)" + emoticone + "[ .,]", " ");
				message = message.replaceAll("(?i)" + emoticone + "$", " ");*/
			}
		}
		return message;
	}

	static public List<Integer> identifyEmoticones(String message)
	{
		List<Integer> ids = new LinkedList<Integer>();
		for (int i = 0; i < emoticones.length; i++)
		{
			for (String emoticone : emoticones[i])
			{
				emoticone = Pattern.quote(emoticone.substring(0, emoticone.length()-1)) + "(" + Pattern.quote(emoticone.substring(emoticone.length()-1)) + ")+";
				if (message.matches("(?i)^" + emoticone) || message.matches("(?i) " + emoticone))
				{
					ids.add(i);
					break;
				}
			}
		}
		return ids;
	}

	static public String getEmoticones(List<Integer> ids)
	{
		String message = new String();
		for (Integer id : ids)
		{
			int choix = new Random().nextInt(emoticones[id].length);
			message += emoticones[id][choix];
			message += " ";
		}
		return message.trim();
	}

	static public String ponctuation_to_whitespace(String phrase)
	{
		return removeEmoticones(phrase).replaceAll("[()\\[\\],.:;!?|_…«»\"“”<>]", " ");
	}

	static public String[] split(String phrase)
	{
		return ponctuation_to_whitespace(phrase).split(" ");
	}

	static public String sanitize(String phrase)
	{
		return ponctuation_to_whitespace(phrase).replaceAll("(?iu)[^a-z0-9⁰¹²-⁹ éèêëâàôûùîïçœ€\\$%\\-']", "").trim();
	}

	static private String fix_double_ponctuation(String original, String ponctuation)
	{
		return original.replaceAll(Pattern.quote(ponctuation)+"+", ponctuation);
	}

	static private String fix_ponctuation_gauche(String original, String ponctuation)
	{
		return fix_double_ponctuation(original, ponctuation).replaceAll(Pattern.quote(ponctuation) + "\\s+", ponctuation).replace(ponctuation, " " + ponctuation);
	}

	static private String fix_ponctuation_droite(String original, String ponctuation)
	{
		return fix_double_ponctuation(original, ponctuation).replaceAll("\\s+" + Pattern.quote(ponctuation), ponctuation).replace(ponctuation, ponctuation + " ");
	}

	static private String fix_ponctuation_double(String original, String ponctuation)
	{
		return fix_double_ponctuation(original, ponctuation).replace(ponctuation, " " + ponctuation + " ");
	}

	static public String fix_typography(String original)
	{
		String phrase = fix_ponctuation_droite(original, "…");

		phrase = fix_ponctuation_double(phrase, ";");
		phrase = fix_ponctuation_double(phrase, ":");
		phrase = fix_ponctuation_double(phrase, "!");
		phrase = fix_ponctuation_double(phrase, "?");
		phrase = fix_ponctuation_double(phrase, "«");
		phrase = fix_ponctuation_double(phrase, "»");

		phrase = fix_ponctuation_gauche(phrase, "(");
		phrase = fix_ponctuation_droite(phrase, ")");
		phrase = fix_ponctuation_gauche(phrase, "[");
		phrase = fix_ponctuation_droite(phrase, "]");
		phrase = fix_ponctuation_gauche(phrase, "{");
		phrase = fix_ponctuation_droite(phrase, "}");

		phrase = phrase.replace("..", "…");
		phrase = phrase.replace("...", "…");
	    phrase = fix_ponctuation_droite(phrase, ".");
		phrase = fix_ponctuation_droite(phrase, ",");
		return phrase.replaceAll(" +", " ").trim();
	}

	static public String format(String phrase)
	{
		phrase = phrase.replaceAll("(.)\\1{3,}+", "$1$1$1");
		phrase = phrase.replaceAll("[^\\x00-\\xFF€…]", "");
		phrase = fix_typography(phrase);
		if (phrase.isEmpty())
			return "";

		if (phrase.endsWith(".") || phrase.endsWith("?") || phrase.endsWith("!") || phrase.endsWith(";") || phrase.endsWith(",") ||
			phrase.endsWith(":") || phrase.endsWith("…"))
			return Character.toUpperCase(phrase.charAt(0)) + phrase.substring(1);
		else
			return Character.toUpperCase(phrase.charAt(0)) + phrase.substring(1, phrase.length()) + ".";
	}

	static public Boolean isUnicode(String message)
	{
		return message.matches("[^\\x00-\\xFF€]");
	}
}

import java.util.Scanner;

import org.neo4j.graphdb.Node;

public class IABot {

	public static void main(String[] args) {
		Bot bot = new Bot();
		System.out.println("IABot :");
		Scanner input = new Scanner(System.in);
		String phrase = new String("");
		System.out.print("Moi : ");
		while (!(phrase = input.nextLine()).contains("exit"))
		{
			if (phrase.contains("load messages"))
				bot.charger("messages");
			else if (phrase.contains("load sms"))
				SMSDecoder.charger("sms");
			/*else if (phrase.contains("trouver"))
			{
				Node node = bot.trouverMessage(phrase.split(" ", 2)[1]);
				if (node != null)
					System.out.println(node.getProperty("phrase"));
			}
			else if (phrase.contains("sanitize"))
				System.out.println("Sanitizer : " + Sanitizer.sanitize(phrase.split(" ", 2)[1]));
			else if (phrase.contains("format"))
				System.out.println("Formatter : " + Sanitizer.format(phrase.split(" ", 2)[1]));*/
			else
			{
				System.out.println("Bot : " + bot.reponse(phrase));
			}
		}
		input.close();
	}

}

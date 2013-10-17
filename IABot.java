import java.util.Scanner;

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
			else
			{
				System.out.println("Moi : " + Sanitizer.format(Sanitizer.removeEmoticone(SMSDecoder.decoderPhrase(phrase))));
				//System.out.println("Bot : " + bot.reponse(Sanitizer.format(SMSDecoder.decoderPhrase(phrase))));
			}
		}
		input.close();
	}

}

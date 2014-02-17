package org.pjk136.Arthy;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Scanner;

import org.apache.lucene.queryParser.ParseException;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.impl.JsonObjectMessage;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.EventBusBridgeHook;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;
import org.vertx.java.platform.PlatformLocator;
import org.vertx.java.platform.PlatformManager;

public final class Arthy {

	public static class ServerHook implements EventBusBridgeHook
	{
		@Override
		public boolean handlePreRegister(SockJSSocket arg0, String arg1) {
			return true;
		}
		
		@Override
		public void handlePostRegister(SockJSSocket arg0, String arg1) {
		}

		@Override
		public boolean handleUnregister(SockJSSocket arg0, String arg1) {
			return true;
		}

		@Override
		public boolean handleSocketCreated(SockJSSocket arg0) {
			System.out.println("Sockjs " + arg0.writeHandlerID() + " créé !");
			return true;
		}
		
		@Override
		public boolean handleAuthorise(JsonObject arg0, String arg1,
				Handler<AsyncResult<Boolean>> arg2) {
			return true;
		}
		
		@Override
		public boolean handleSendOrPub(SockJSSocket sock, boolean send, JsonObject msg, String address)
		{
			msg.getObject("body").putString("uuid", sock.writeHandlerID());
			return true;
		}

		@Override
		public void handleSocketClosed(SockJSSocket sock) {
			Bot.removeBot(sock.writeHandlerID());
			System.out.println("Sockjs " + sock.writeHandlerID() + " fermé !");
		}
	};
	
	public static void init()
	{
		PlatformManager pm = PlatformLocator.factory.createPlatformManager();
		DatabaseSMS.getInstance();
		DatabaseBot.getInstance();
		
		HttpServer httpServer = pm.vertx().createHttpServer();
		httpServer.requestHandler(new Handler<HttpServerRequest>() {
			public void handle(HttpServerRequest request) {
				String file = "";
				if (request.path().equals("/"))
					file = "index.html";
				else if (!request.path().contains(".."))
					file = request.path();
				request.response().sendFile("web/" + file, "web/404.html");
			};
		});

		SockJSServer sockJSServer = pm.vertx().createSockJSServer(httpServer);
		JsonObject config = new JsonObject().putString("prefix", "/chat");
		JsonArray inboundPermitted = new JsonArray().add(new JsonObject().putString("address", "message"));
		sockJSServer.setHook(new ServerHook());
		sockJSServer.bridge(config, inboundPermitted, new JsonArray().add(new JsonObject()));
		pm.vertx().eventBus().registerHandler("message", new Handler<JsonObjectMessage>() {
			public void handle(JsonObjectMessage message) {
				JsonObject reponse = new JsonObject();
				System.out.println("Message reçu : " + message.body().toString());
				try
				{
					SimpleEntry<String, String> phrase_reponse = Bot.getBot(message.body().getString("uuid")).reponse(message.body().getString("phrase"));
					reponse.putString("phrase", phrase_reponse.getKey());
					reponse.putString("reponse", phrase_reponse.getValue());
				}
				catch (Exception e)
				{
					e.printStackTrace();
					reponse = new JsonObject();
					reponse.putString("phrase", message.body().toString());
					reponse.putString("reponse", "Woops ! Ton message était tellement philosophique que mon programme interne a planté...");
					
				}
				System.out.println("Réponse : " + reponse.getString("reponse"));
				message.reply(reponse);
			}
		});

		httpServer.listen(8080);
	}
	
	public static void main(String[] args) throws IOException, ParseException
	{
		init();
		Scanner input = new Scanner(System.in);
		String phrase = new String("");
		do
		{
			try
			{
				if (phrase.equals("charger messages"))
					DatabaseBot.getInstance().charger("messages");
				else if (phrase.equals("charger sms"))
					DatabaseSMS.getInstance().charger("sms");
				else if (phrase.equals("verifier"))
					DatabaseBot.getInstance().verifier(false);
				else if (phrase.equals("corriger"))
					DatabaseBot.getInstance().verifier(true);
				else if (phrase.equals("hotfix"))
					DatabaseBot.getInstance().hotfix(false);
				else if (phrase.equals("hotfix true"))
					DatabaseBot.getInstance().hotfix(true);
				else if (phrase.equals("reconstruire index"))
					DatabaseBot.getInstance().reconstruireIndex();
				else if (phrase.equals("relations boucle"))
				{
					for (SimpleEntry<Long, String> result : DatabaseBot.getInstance().trouverRelationsBoucle())
						System.out.println(result);
				}
				else if (phrase.startsWith("chercher exact "))
				{
					System.out.println("Recherche : " + phrase.split(" ", 3)[2]);
					for (SimpleEntry<Long, String> result : DatabaseBot.getInstance().chercherExact(phrase.split(" ", 3)[2]))
						System.out.println(result);
				}
				else if (phrase.startsWith("chercher "))
				{
					for (SimpleEntry<Long, String> result : DatabaseBot.getInstance().chercherMots(phrase.split(" ", 2)[1]))
						System.out.println(result);
				}
				else if (phrase.startsWith("get "))
				{
					System.out.println(DatabaseBot.getInstance().getNodeById(Long.valueOf(phrase.split(" ", 2)[1])).getPhrase());
				}
				else if (phrase.startsWith("set "))
				{
					String arguments[] = phrase.split(" ", 3);
					DatabaseBot.getInstance().setNode(Long.valueOf(arguments[1]), arguments[2]);
				}
				else if (phrase.startsWith("supprimer node "))
				{
					DatabaseBot.getInstance().supprimerNode(Long.valueOf(phrase.split(" ", 3)[2]));
					System.out.println("Supprimé !");
				}
				else if (phrase.startsWith("supprimer relation "))
				{
					DatabaseBot.getInstance().supprimerRelation(Long.valueOf(phrase.split(" ", 3)[2]));
					System.out.println("Supprimé !");
				}
				else if (phrase.startsWith("supprimer nodes "))
				{
					long start = Long.valueOf(phrase.split(" ", 4)[2]);
					long stop = Long.valueOf(phrase.split(" ", 4)[3]);
					
					for (long i = start; i <= stop ; i++)
						DatabaseBot.getInstance().supprimerNode(i);

					System.out.println(String.valueOf(stop-start+1) + " nodes supprimés !");
				}
				else if (phrase.startsWith("supprimer relations "))
				{
					long start = Long.valueOf(phrase.split(" ", 4)[2]);
					long stop = Long.valueOf(phrase.split(" ", 4)[3]);
					
					for (long i = start; i <= stop ; i++)
						DatabaseBot.getInstance().supprimerRelation(i);
					
					System.out.println(String.valueOf(stop-start+1) + " relations supprimés !");
				}
				else if (phrase.startsWith("sms "))
					System.out.println(SMSDecoder.decoderPhrase(phrase.split(" ", 2)[1]));
				else if (phrase.startsWith("format "))
					System.out.println(Sanitizer.format(phrase.split(" ", 2)[1]));
				else if (phrase.equals("flush"))
					Bot.flushFiles();
				else if (!phrase.isEmpty())
					System.out.println("Unknown command.");
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
			System.out.print(">>> ");
		} while (!(phrase = input.nextLine()).startsWith("exit"));
		input.close();
		
		System.exit(0);
	}
}

package org.pjk136.Arthy;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.Random;
import java.util.Vector;

import org.pjk136.Arthy.DatabaseBot.NodePhrase;
import org.pjk136.Arthy.DatabaseBot.RelationPhrase;

import java.io.File;
import java.io.PrintWriter;

public class Bot {
	private static HashMap<String, Bot> m_instances;
	private static DatabaseBot m_database;
	private NodePhrase m_previous;
	private int m_repetition;
	private Boolean m_repetition_mode;
	private PrintWriter m_fichier;

    static {
    	m_instances = new HashMap<String, Bot>();
    	m_database = DatabaseBot.getInstance();
    	
    	Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                for (Bot bot : m_instances.values())
                	bot.finalize();
            }
        } );
    	
    	new File("logs").mkdir();
    }
    
    static public Bot getBot(String uuid)
    {
    	if (m_instances.get(uuid) != null)
    		return m_instances.get(uuid);
    	else
    	{
    		return new Bot(uuid);
    	}
    }
    
    static public void removeBot(String uuid)
    {
    	if (m_instances.get(uuid) != null)
    		m_instances.get(uuid).flushFile();
    	m_instances.remove(uuid);
    }
    
    static public void flushFiles()
    {
    	for (Bot bot : m_instances.values())
        	bot.flushFile();
    }
    
	private Bot(String uuid) {
		m_previous = null;
		m_repetition = 0;
		m_repetition_mode = false;
		m_instances.put(uuid, this);
		try {
			m_fichier = new PrintWriter("logs/"+uuid+".txt");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void finalize()
	{
		try {
			if (m_fichier != null)
			{
				m_fichier.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void writeFile(String message)
	{
		if (m_fichier != null)
			m_fichier.println(message);
	}

	public void flushFile()
	{
		if (m_fichier != null)
			m_fichier.flush();
	}
	
	private static String reponses_repetitions[] = {"Répètera bien qui répètera le dernier !",
		"Si tu continues, je pourrais faire de même et on verra qui tiendra le plus longtemps !",
		"En effet, c'est ce que j'ai dit !", "Tiens, tu répètes ce que je dis :-D.", "Bravo, tu sais copier une phrase !",
		"Oh ! J'énonce des propos tellement intelligents que tu ne sais que les répéter !",
		"Pourquoi tu me répètes ?", "Si ça t'amuse de répéter ce que j'ai dit ...", "Tu n'as rien d'autre à faire que d'imiter un perroquet ?",
		"Me dire ce que je t'ai dit ne t'apportera rien de nouveau ...", };

	public SimpleEntry<String, String> reponse(String message)
	{
		writeFile("Utilisateur : " + message);
		if (Sanitizer.isUnicode(message))
		{
			m_previous = null;
			m_repetition = 0;
			if (m_repetition_mode)
				writeFile("Mode de répétition désactivé ...");
			
			m_repetition_mode = false;
			writeFile("Il y a des caractères Unicode ...");
			writeFile("Bot : Malheureusement, je ne supporte pas encore les caractères Unicodes :-(.");
			return new SimpleEntry<String, String>(message, "Malheureusement, je ne supporte pas encore les caractères Unicode :-(.");
		}
		
		String message_format = Sanitizer.format(Sanitizer.removeEmoticones(SMSDecoder.decoderPhrase(message)));
		writeFile("Décodé : " + message_format);
		if (message_format.isEmpty())
		{
			if (m_repetition_mode)
			{
				m_repetition_mode = false;
				m_repetition = 0;
				writeFile("Désactivation du mode de répétition ...");
				writeFile("Bot : Bah ... Tu ne me répètes plus ? :-D");
				return new SimpleEntry<String, String>(message, "Bah ... Tu ne me répètes plus ? :-D");
			}	
			
			List<Integer> ids_emoticones = Sanitizer.identifyEmoticones(message);
			if (ids_emoticones.size() == 0)
			{
				writeFile("Le message après décodage est vide ... Ignoré !");
				writeFile("Bot : ");
				return new SimpleEntry<String, String>("", "");
			}
			else
			{
				m_previous = null;
				writeFile("Présence d'émoticones ... Réponse en émoticones :)");
				String reponse = Sanitizer.getEmoticones(ids_emoticones);
				writeFile("Bot : " + reponse);
				return new SimpleEntry<String, String>(message, reponse);
			}
		}

		message = message_format;
		
		if (m_previous != null)
		{
			if (Sanitizer.sanitize(m_previous.getPhrase()).equals(Sanitizer.sanitize(message)))
			{
				String reponse = reponseRepetition();
				if (reponse != null)
					return new SimpleEntry<String, String>(message, reponse);
			}
			else
				m_repetition = 0;
				
			NodePhrase phraseExacte = m_database.trouverMessageExact(message);
			if (phraseExacte == null)
				phraseExacte = m_database.ajouterPhrase(message);
			m_database.ajouterRelation(m_previous, phraseExacte);
		}
		else
		{
			if (m_repetition > 0)
			{
				for (String s : reponses_repetitions)
				{
					if (Sanitizer.sanitize(s).equals(Sanitizer.sanitize(message)))
					{
						if (m_repetition_mode == false)
						{
							String reponse = reponseRepetition();
							if (reponse != null)
								return new SimpleEntry<String, String>(message, reponse);
						}
						else
							return new SimpleEntry<String, String>(message, message);
					}
				}
				
				if (m_repetition_mode)
				{
					m_repetition_mode = false;
					m_repetition = 0;
					return new SimpleEntry<String, String>(message, "Oh ... C'est déjà fini ? :-D Tant pis !");
				}
			}
		}

		NodePhrase meilleureReponse = recherchePrecise(message);
		
		if (meilleureReponse == null)
			meilleureReponse = rechercheApproximative(message);

		if (meilleureReponse == null)
		{
			Vector<Long> phrases = m_database.questionsSansSuite();
			if (!phrases.isEmpty())
			{
				meilleureReponse = m_database.getNodeById(phrases.elementAt(new Random().nextInt(phrases.size())));
				writeFile("Rien à dire : Utilisation d'une question comme réponse.");
			}
		}
		
		String reponse = "";
		m_previous = meilleureReponse;
		if (meilleureReponse != null)
			reponse = meilleureReponse.getPhrase();
		else
			reponse = "Je ne sais pas quoi répondre :/";
		
		writeFile("Bot : " + reponse);
		return new SimpleEntry<String, String>(message, reponse);
	}
	
	private String reponseRepetition()
	{
		m_repetition++;
		if (m_repetition >= 2)
		{
			writeFile("Répétition détectée ...");
			int reponse = new Random().nextInt(reponses_repetitions.length);
			if (reponse < 2)
			{
				m_repetition_mode = true;
				writeFile("Mode de répétition actif ...");
			}
			m_previous = null;
			writeFile("Bot : " + reponse);
			return reponses_repetitions[reponse];
		}
		return null;
	}
	
	private NodePhrase recherchePrecise(String message)
	{
		writeFile("Recherche précise ...");
		List<NodePhrase> phrasesPrecises = m_database.recherchePrecise(message);
		if (!phrasesPrecises.isEmpty())
		{
			LinkedList<RelationPhrase> relationsReponses = new LinkedList<>();
			for (NodePhrase phrase : phrasesPrecises)
			{
				writeFile("Trouvé : " + phrase.getPhrase() + " id : " + phrase.getId());
				for (RelationPhrase relation : phrase.getRelations())
					relationsReponses.add(relation);
			}
			
			if (phrasesPrecises.size() == 1 && relationsReponses.size() == 1)
			{
				writeFile("Il y a seulement 1 phrase correspondante avec seulement 1 réponse ... Ne pas envoyer la réponse pour protéger le fil de discussion originale !");
				return null;
			}
			
			if (!relationsReponses.isEmpty())
			{
				PriorityQueue<RelationPhrase> relationsSorted = new PriorityQueue<RelationPhrase>(relationsReponses.size(), Collections.reverseOrder());
				relationsSorted.addAll(relationsReponses);
				
				List<SimpleEntry<NodePhrase,Integer>> topReponses = new LinkedList<SimpleEntry<NodePhrase,Integer>>();
				int count = 0;
				int last_score = 0;
				while (!relationsSorted.isEmpty())
				{
					int score = relationsSorted.peek().getCount();
					if (score != last_score)
					{
						last_score = score;
						if (topReponses.size() > 5)
							break;
					}
					topReponses.add(new SimpleEntry<>(relationsSorted.poll().getEndNode(), score));
					count += score;
				}
				
				return randomReponse(topReponses, count);
			}
		}
		
		return null;
	}
	
	private NodePhrase rechercheApproximative(String message)
	{
		writeFile("Recherche approximative ...");
		List<SimpleEntry<NodePhrase,Double>> phrasesApproximatives = m_database.rechercheApproximative(message);
		if (!phrasesApproximatives.isEmpty())
		{
			LinkedList<SimpleEntry<NodePhrase, Integer>> reponses = new LinkedList<>();
			for (SimpleEntry<NodePhrase, Double> phrase : phrasesApproximatives)
			{
				writeFile("Trouvé : " + phrase.getKey().getPhrase() + " id :" + phrase.getKey().getId() + " score : " + phrase.getValue());
				for (RelationPhrase relation : phrase.getKey().getRelations())
					reponses.add(new SimpleEntry<NodePhrase, Integer>(relation.getEndNode(), (int) Math.ceil((phrase.getValue()*(double)relation.getCount()))));
			}
			
			if (phrasesApproximatives.size() == 1 && reponses.size() == 1)
			{
				writeFile("Il y a seulement 1 phrase correspondante avec seulement 1 réponse ... Ne pas envoyer la réponse pour protéger le fil de discussion originale !");
				return null;
			}
			
			if (!reponses.isEmpty())
			{
				PriorityQueue<SimpleEntry<NodePhrase, Integer>> reponsesSorted = new PriorityQueue<SimpleEntry<NodePhrase, Integer>>(reponses.size(),
						Collections.reverseOrder(new Comparator<SimpleEntry<NodePhrase, Integer>>() {
							@Override
							public int compare(SimpleEntry<NodePhrase, Integer> arg0,
									SimpleEntry<NodePhrase, Integer> arg1) {
								return Integer.compare(arg0.getValue(),arg1.getValue());
							}
						}));
				
				reponsesSorted.addAll(reponses);
				
				List<SimpleEntry<NodePhrase, Integer>> topReponses = new LinkedList<SimpleEntry<NodePhrase, Integer>>();
				int count = 0;
				int last_score = 0;
				while (!reponsesSorted.isEmpty())
				{
					int score = reponsesSorted.peek().getValue();
					if (score != last_score)
					{
						last_score = score;
						if (topReponses.size() > 5)
							break;
					}
					topReponses.add(reponsesSorted.poll());
					count += score;
				}
				return randomReponse(topReponses, count);
			}
		}
		return null;
	}
	
	private NodePhrase randomReponse(List<SimpleEntry<NodePhrase, Integer>> topReponses, int max)
	{
		writeFile("Réponses possibles :");
		for (SimpleEntry<NodePhrase, Integer> reponse : topReponses)
		{
			if (reponse.getValue() != 1)
				writeFile(reponse.getKey().getPhrase() + " " + reponse.getValue());
			else
			{
				writeFile("Et d'autre réponses une seule fois utilisées ...");
				break;
			}
		}
		
		int choix = new Random().nextInt(max);
		int count = 0;
		for (SimpleEntry<NodePhrase, Integer> reponse : topReponses)
		{
			count += reponse.getValue();
			if (choix < count)
				return reponse.getKey();
		}
		
		System.err.println("WTF ! Le random a choisi aucune réponse ! Le choix était : " + choix + " et le maximum était : " + max);
		return null;
	}
}

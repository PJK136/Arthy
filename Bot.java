import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.util.Version;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.helpers.collection.MapUtil;


public class Bot {
	private static GraphDatabaseService m_database;
	private static Index<Node> m_index_exact;
	private static Index<Node> m_index_french_analyzer;
	private Node m_previous;

    private static enum RelTypes implements RelationshipType
    {
        TO
    }
    
    static public class FrAnalyzer extends Analyzer
    {
    	private FrenchAnalyzer m_analyzer;
    	public FrAnalyzer()
    	{
    		m_analyzer = new FrenchAnalyzer(Version.LUCENE_36);
    	}
		@Override
		public TokenStream tokenStream(String fieldName, Reader reader) {
			return m_analyzer.tokenStream(fieldName, reader);
		}
    }
   
    static {
    	m_database = new GraphDatabaseFactory().newEmbeddedDatabase("conversation.db");
    	try (Transaction tx = m_database.beginTx())
    	{
    		m_index_exact = m_database.index().forNodes("phrase-exact");
    		m_index_french_analyzer = m_database.index().forNodes("phrase-french-analyzer", MapUtil.stringMap(IndexManager.PROVIDER, "lucene", "analyzer", FrAnalyzer.class.getName()));
    		tx.success();
    	}
    }
    
	public Bot() {
		m_previous = null;
	}
	
	public void finalize()
	{
		m_database.shutdown();
	}
	
	public Node trouverMessage(String message)
	{
		message = Sanitizer.sanitize(message).toLowerCase();
		if (message.isEmpty())
			return null;
		
		try (Transaction tx = m_database.beginTx())
		{
			IndexHits<Node> results = m_index_exact.get("phrase", message);
			if (results.size() > 1)
			{
				for (Node result : results)
				{
					if (message.equals(Sanitizer.sanitize((String) result.getProperty("phrase")).toLowerCase()))
						return result;
				}
			}
			else if (results.size() == 1)
				return results.next();
			tx.success();
		}
		return null;
	}
	
	public Node meilleurePhrase(String message)
	{
		message = Sanitizer.sanitize(message);
		if (message.isEmpty())
			return null;
		
		try (Transaction tx = m_database.beginTx())
		{
			IndexHits<Node> results = m_index_french_analyzer.query(new QueryParser(Version.LUCENE_36, "phrase", new FrenchAnalyzer(Version.LUCENE_36)).parse(message));
			if (results.hasNext())
			{
				if (results.size() > 1)
				{
					double maxScore = -1;
					Node meilleurePhrase = null;
					for ( Node phrase : results )
					{
						if (!phrase.hasRelationship(Direction.OUTGOING, RelTypes.TO))
							continue;
						
						double score = results.currentScore()*1/Math.sqrt((double)StringUtils.getLevenshteinDistance(message, Sanitizer.sanitize((String) phrase.getProperty("phrase"))));
						if (maxScore < score)
						{
							maxScore = score;
							meilleurePhrase = phrase;
						}
					}
					return meilleurePhrase;
				}
				else
					return results.next();
			}
			else
				return null;
		} catch (Exception e) {
			return null;
		}
	}
	
	private Node ajouterPhrase(String message)
	{
		try (Transaction tx = m_database.beginTx())
		{
			Node node = m_database.createNode();
			node.setProperty("phrase", message);
			m_index_exact.add(node, "phrase", Sanitizer.sanitize(message).toLowerCase());
			m_index_french_analyzer.add(node, "phrase", Sanitizer.sanitize(message));
			tx.success();
			return node;
		}
	}
	
	private void createRelation(Node a, Node b)
	{
		try (Transaction tx = m_database.beginTx())
		{
			boolean found = false;
			
			if (a.hasRelationship(Direction.OUTGOING))
			{
				for (Relationship relation : a.getRelationships(Direction.OUTGOING, RelTypes.TO))
				{
					if (relation.getEndNode().equals(b))
					{
						relation.setProperty("count", (int) relation.getProperty("count") + 1);
						found = true;
						break;
					}
				}
			}
			
			if (!found)
			{
				Relationship to = a.createRelationshipTo(b, RelTypes.TO);
				to.setProperty("count", 1);
			}
			tx.success();
		}
	}
	
	public String reponse(String message)
	{
		message = Sanitizer.format(Sanitizer.removeEmoticone(SMSDecoder.decoderPhrase(message)));
		System.err.println("Décodé : " + message);
		if (message.isEmpty())
			return "";

		Node meilleurePhrase = null;
		Node phraseExacte = meilleurePhrase = trouverMessage(message);
		
		try (Transaction tx = m_database.beginTx()) {
			if (phraseExacte != null)
				System.err.println("Trouvé exact : " + phraseExacte.getProperty("phrase") + " ID : " + phraseExacte.getId());
			tx.success();
		}
		
		if (phraseExacte != null && m_previous != null)
			createRelation(m_previous, phraseExacte);
		
		Boolean exacte = true;
		try (Transaction tx = m_database.beginTx())
		{
			if (phraseExacte == null || !phraseExacte.hasRelationship())
				exacte = false;
			tx.success();
		}
		
		if (!exacte)
		{
			meilleurePhrase = meilleurePhrase(message);
			
			try (Transaction tx = m_database.beginTx()) {
				if (meilleurePhrase != null)
					System.err.println("Trouvé moyen : " + meilleurePhrase.getProperty("phrase") + " ID : " + meilleurePhrase.getId());
				tx.success();
			}
		}
	
		if (phraseExacte == null && m_previous != null)
		{
			Node reponse = ajouterPhrase(message);
			createRelation(m_previous, reponse);
		}
		
		try (Transaction tx = m_database.beginTx())
		{
			Node meilleureReponse = null;
			if (meilleurePhrase != null)
			{
				int maxCount = 0;
				for (Relationship relationReponse : meilleurePhrase.getRelationships(Direction.OUTGOING, RelTypes.TO))
				{
					System.err.println(relationReponse.getEndNode().getProperty("phrase") + " " + relationReponse.getProperty("count").toString() + " " + relationReponse.getEndNode().getId());
					maxCount += Math.pow((int) relationReponse.getProperty("count"), 2);
				}
				
				int choix = new Random().nextInt(maxCount);
				int count = 0;
				for (Relationship relationReponse : meilleurePhrase.getRelationships(Direction.OUTGOING, RelTypes.TO))
				{
					count += Math.pow((int) relationReponse.getProperty("count"), 2);
					if (count >= choix)
					{
						meilleureReponse = relationReponse.getEndNode();
						System.out.println(relationReponse.getProperty("count"));
						break;
					}
				}
			}
			
			String reponse = "";
	
			if (meilleureReponse == null)
			{
				m_previous = null;
				reponse = "Je ne sais pas quoi répondre :/";
			}
			else
			{
				m_previous = meilleureReponse;
				reponse = (String) meilleureReponse.getProperty("phrase");
			}
			tx.success();
			return reponse;
		}
	}
	
	public void charger(String fichier)
	{
		int count = 0;
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(fichier));
			String ligne = "";
			Node previous = null;
			while ((ligne = br.readLine()) != null)
			{
				count++;
				if (count % 100 == 0)
					System.err.println(count);

				if (ligne.isEmpty())
					continue;
					
				if (ligne.contentEquals("###") || ligne.contentEquals("---") ||
					(ligne = Sanitizer.format(Sanitizer.removeEmoticone(SMSDecoder.decoderPhrase(ligne)))).isEmpty())
				{
					previous = null;
					continue;
				}

				Node meilleure = trouverMessage(ligne);

				if (meilleure == null)
					meilleure = ajouterPhrase(ligne);

				if (previous != null)
					createRelation(previous, meilleure);

				previous = meilleure;
			}
			br.close();
		} catch (Exception e) {
			System.err.println("Fichier introuvable ou problème de lecture ...");
			e.printStackTrace();
		}
	}
}

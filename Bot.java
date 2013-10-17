import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.QueryParser.Operator;
import org.apache.lucene.util.Version;
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
	private static Index<Node> m_index;
	private Node m_previous;

    private static enum RelTypes implements RelationshipType
    {
        TO
    }
    
    static public class FrAnalyzer extends Analyzer
    {
    	private final Analyzer analyzer;
    	
    	public FrAnalyzer()
    	{
    		analyzer = new FrenchAnalyzer(Version.LUCENE_36);
    	}
    	
		@Override
		public TokenStream tokenStream(String arg0, Reader arg1) {
			return analyzer.tokenStream(arg0, arg1);
		}
    }
    
    static {
    	m_database = new GraphDatabaseFactory().newEmbeddedDatabase("conversation.db");
    	m_index = m_database.index().forNodes("phrase-fulltext", MapUtil.stringMap(IndexManager.PROVIDER, "lucene", "type", "fulltext", "analyzer", FrAnalyzer.class.getName()));
    }
    
	public Bot() {
		m_previous = null;
	}
	
	public void finalize()
	{
		m_database.shutdown();
	}
	
	public Node meilleurePhrase(String message)
	{
		if (message.isEmpty())
			return null;
		
		try {
			IndexHits<Node> results = m_index.query(new QueryParser(Version.LUCENE_36, "phrase", new FrenchAnalyzer(Version.LUCENE_36)).parse(Sanitizer.sanitize(message)));
			if (results.hasNext())
			{
				if (results.size() > 1)
				{
					double maxScore = -1;
					Node meilleurePhrase = null;
					for ( Node phrase : results )
					{
						if (!phrase.hasRelationship(RelTypes.TO))
							continue;
						
						double score = results.currentScore()*1/Math.sqrt((double)StringUtils.getLevenshteinDistance(message, (String) phrase.getProperty("phrase")));
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
		} catch (ParseException e) {
			return null;
		}
	}

	public Node trouverMessage(String message)
	{
		if (message.isEmpty())
			return null;
		
		QueryParser parser = new QueryParser(Version.LUCENE_36, "phrase", new FrenchAnalyzer(Version.LUCENE_36));
		parser.setDefaultOperator(Operator.AND);
		try {
			IndexHits<Node> results = m_index.query(parser.parse(Sanitizer.sanitize(message)));
			if (results.hasNext())
			{
				for ( Node phrase : results )
				{
					if (((String) phrase.getProperty("phrase")).equalsIgnoreCase(message))
						return phrase;
				}
			}
			return null;
		} catch (ParseException e) {
			return null;
		}
	}
	
	private Node ajouterPhrase(String message)
	{
		Transaction tx = m_database.beginTx();
		try
		{
			Node node = m_database.createNode();
			node.setProperty("phrase", message);
			m_index.add(node, "phrase", message);
			tx.success();
			return node;
		}
		finally
		{
			tx.finish();
		}
	}
	
	private void createRelation(Node a, Node b)
	{
		Transaction tx = m_database.beginTx();
		try
		{
			boolean found = false;
			
			if (a.hasRelationship())
			{
				for (Relationship relation : a.getRelationships(RelTypes.TO))
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
		finally
		{
			tx.finish();
		}
	}
	
	public String reponse(String message)
	{
		if (message.isEmpty())
			return "";

		if (m_previous != null)
		{
			Node reponse = trouverMessage(message);
			if (reponse == null)
				reponse = ajouterPhrase(message);
			createRelation(m_previous, reponse);
		}
		
		Node meilleurePhrase = meilleurePhrase(message);
		Node meilleureReponse = null;
		if (meilleurePhrase != null)
		{
			int maxCount = 0;
			for (Relationship relationReponse : meilleurePhrase.getRelationships(RelTypes.TO))
			{
				System.out.println(relationReponse.getEndNode().getProperty("phrase") + relationReponse.getProperty("count").toString());
				maxCount += (int) relationReponse.getProperty("count");
			}
			
			int choix = new Random().nextInt(maxCount);
			int count = 0;
			for (Relationship relationReponse : meilleurePhrase.getRelationships(RelTypes.TO))
			{
				count += (int) relationReponse.getProperty("count");
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

		return reponse;
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

				if (ligne.contentEquals("###") || ligne.contentEquals("---"))
				{
					previous = null;
					continue;
				}

				String message = Sanitizer.format(Sanitizer.removeEmoticone(SMSDecoder.decoderPhrase(ligne)));
				if (message.isEmpty())
				{
					previous = null;
					continue;
				}
				
				Node meilleure = trouverMessage(message);

				if (meilleure == null)
					meilleure = ajouterPhrase(message);

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

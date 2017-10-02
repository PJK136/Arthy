package org.pjk136.Arthy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.DefaultSimilarity;
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
import org.neo4j.tooling.GlobalGraphOperations;

public final class DatabaseBot {
	private static volatile DatabaseBot instance = null;

	private GraphDatabaseService m_database;
	private Index<Node> m_index_exact;
	private Index<Node> m_index_simple_analyzer;
	private Index<Node> m_index_french_analyzer;
	private Vector<Long> m_questions_sans_suite;

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

    static public class SimilarityBot extends DefaultSimilarity
    {
		private static final long serialVersionUID = 1L;

		@Override
		public float tf(int freq) {
			return 1.0f;
		}

		@Override
		public float tf(float freq) {
			return 1.0f;
		}
    }

    public class NodePhrase
    {
    	private Node m_node;
    	private NodePhrase(Node node)
    	{
    		m_node = node;
    	}

    	public long getId()
    	{
    		try (Transaction tx = m_database.beginTx())
    		{
    			return m_node.getId();
    		}
    	}

    	public String getPhrase()
    	{
    		try (Transaction tx = m_database.beginTx())
    		{
    			return (String) m_node.getProperty("phrase");
    		}
    	}

    	public Boolean hasRelations()
    	{
    		try (Transaction tx = m_database.beginTx())
    		{
	    		return m_node.hasRelationship(Direction.OUTGOING, RelTypes.TO);
    		}
    	}
    	public List<RelationPhrase> getRelations()
    	{
    		try (Transaction tx = m_database.beginTx())
    		{
    			LinkedList<RelationPhrase> relations = new LinkedList<>();
	    		for (Relationship relation : m_node.getRelationships(Direction.OUTGOING, RelTypes.TO))
	    			relations.add(new RelationPhrase(relation));
	    		return relations;
    		}
    	}
    }

    public class RelationPhrase implements Comparable<RelationPhrase>
    {
    	private Relationship m_relation;

    	private RelationPhrase(Relationship relation)
    	{
    		m_relation = relation;
    	}

    	public int getCount()
    	{
    		int count = 0;
    		try (Transaction tx = m_database.beginTx())
    		{
    			count = (int) m_relation.getProperty("count");
    			tx.success();
    		}
    		return count;
    	}

    	public NodePhrase getEndNode()
    	{
    		try (Transaction tx = m_database.beginTx())
    		{
    			return new NodePhrase(m_relation.getEndNode());
    		}
    	}

		@Override
		public int compareTo(RelationPhrase b) {
			return ((Integer) getCount()).compareTo(b.getCount());
		}
    }

	public final static DatabaseBot getInstance()
	{
		if (DatabaseBot.instance == null)
		{
			synchronized(DatabaseBot.class)
			{
				if (DatabaseBot.instance == null)
				{
					try
					{
						DatabaseBot.instance = new DatabaseBot();
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}
		}
		return DatabaseBot.instance;
	}

	private DatabaseBot()
	{
    	m_database = new GraphDatabaseFactory().newEmbeddedDatabase(new File("conversation.db"));
    	try (Transaction tx = m_database.beginTx())
    	{
    		m_index_exact = m_database.index().forNodes("phrase-exact");
    		m_index_simple_analyzer = m_database.index().forNodes("phrase-simple-analyzer", MapUtil.stringMap(IndexManager.PROVIDER, "lucene", "analyzer", SimpleAnalyzer.class.getName(), "similarity", SimilarityBot.class.getName()));
    		m_index_french_analyzer = m_database.index().forNodes("phrase-french-analyzer", MapUtil.stringMap(IndexManager.PROVIDER, "lucene", "analyzer", FrAnalyzer.class.getName(), "similarity", SimilarityBot.class.getName()));
    		m_questions_sans_suite = new Vector<>();
    		tx.success();
    	}

    	Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                m_database.shutdown();
            }
        });

    	updateQuestionsSansSuite();
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				updateQuestionsSansSuite();
			}}, 5*60*1000, 5*60*1000);
	}

	public NodePhrase getNodeById(long id)
	{
		try (Transaction tx = m_database.beginTx())
		{
			return new NodePhrase(m_database.getNodeById(id));
		}
	}

	public NodePhrase trouverMessageExact(String message)
	{
		Boolean estQuestion = message.contains("?");
		if (message.isEmpty())
			return null;

		try (Transaction tx = m_database.beginTx())
		{
			IndexHits<Node> results = m_index_exact.get(estQuestion ? "question" : "phrase", message);
			for (Node result : results)
			{
				if (message.equals(result.getProperty("phrase")))
					return new NodePhrase(result);
			}
		}
		return null;
	}

	public List<NodePhrase> recherchePrecise(String message)
	{
		Boolean estQuestion = message.contains("?");
		message = Sanitizer.sanitize(message).toLowerCase();
		if (message.isEmpty())
			return new LinkedList<>();

		List<NodePhrase> phrases = new LinkedList<>();
		try (Transaction tx = m_database.beginTx())
		{
			QueryParser queryParser = new QueryParser(Version.LUCENE_36, estQuestion ? "question" : "phrase", new SimpleAnalyzer(Version.LUCENE_36));
			queryParser.setDefaultOperator(QueryParser.AND_OPERATOR);
			IndexHits<Node> results = m_index_simple_analyzer.query(queryParser.parse(message));
			//System.out.println("Query Précise : " + new QueryParser(Version.LUCENE_36, estQuestion ? "question" : "phrase", new SimpleAnalyzer(Version.LUCENE_36)).parse(message));
			for (Node phrase : results)
			{
				if (!phrase.hasRelationship(Direction.OUTGOING, RelTypes.TO))
					continue;

				if (message.equals(Sanitizer.sanitize(((String) phrase.getProperty("phrase")).toLowerCase())))
					phrases.add(new NodePhrase(phrase));
			}
		}
		catch (Exception e) {
			return phrases;
		}

		return phrases;
	}

	public List<SimpleEntry<NodePhrase, Double>> rechercheApproximative(String message)
	{
		Boolean estQuestion = message.contains("?");
		message = Sanitizer.sanitize(message);
		if (message.isEmpty())
			return null;

		int nb_token = new StringTokenizer(message).countTokens();

		List<SimpleEntry<NodePhrase, Double>> best = new LinkedList<>();
		PriorityQueue<SimpleEntry<NodePhrase, Double>> phrases = new PriorityQueue<>(5, Collections.reverseOrder(new ComparatorNodePhraseDouble()));
		try (Transaction tx = m_database.beginTx())
		{
			IndexHits<Node> results = m_index_french_analyzer.query(new QueryParser(Version.LUCENE_36, estQuestion ? "question" : "phrase", new FrenchAnalyzer(Version.LUCENE_36)).parse(message));
			//System.out.println("Query Approximative : " + new QueryParser(Version.LUCENE_36, estQuestion ? "question" : "phrase", new FrenchAnalyzer(Version.LUCENE_36)).parse(message));
			int count = 0;
			double lastScore = 0;
			for (Node phrase : results)
			{
				if (!phrase.hasRelationship(Direction.OUTGOING, RelTypes.TO))
					continue;

				double score = results.currentScore();
				if (score != lastScore)
				{
					lastScore = score;
					count++;
					if (count > 3 || phrases.size() > 5)
						break;
				}

				phrases.add(new SimpleEntry<NodePhrase, Double>(new NodePhrase(phrase),
						score*1.f/Math.sqrt(Math.abs((double)new StringTokenizer(Sanitizer.sanitize((String) phrase.getProperty("phrase"))).countTokens()-nb_token)+1)));
			}

			count = 0;
			lastScore = 0;
			double first = 0;
			for (SimpleEntry<NodePhrase, Double> phrase : phrases)
			{
				double score = phrase.getValue();
				if (first == 0)
					first = 1.f/score;

				phrase.setValue(phrase.getValue()*first);
				if (score != lastScore)
				{
					lastScore = score;
					count++;
					if (count > 5 || best.size() > 5)
						break;
				}
				best.add(phrase);
			}

		} catch (Exception e) {
			return best;
		}

		return best;
	}

	class ComparatorNodePhraseDouble implements Comparator<SimpleEntry<NodePhrase, Double>>
	{
		@Override
		public int compare(SimpleEntry<NodePhrase, Double> arg0,
				SimpleEntry<NodePhrase, Double> arg1) {
			return Double.compare(arg0.getValue(),arg1.getValue());
		}
	}

	public Vector<Long> questionsSansSuite()
	{
		return m_questions_sans_suite;
	}

	private void updateQuestionsSansSuite()
	{
		Vector<Long> questions_sans_suite = new Vector<>();

		try (Transaction tx = m_database.beginTx())
		{
			Iterable<Node> nodes = GlobalGraphOperations.at(m_database).getAllNodes();

			for (Node node : nodes)
			{
				Long relationCount = (long) 0;
				for (@SuppressWarnings("unused") Relationship relation : node.getRelationships(Direction.OUTGOING))
					relationCount++;

				if (relationCount < 3 && ((String)node.getProperty("phrase")).contains("?"))
					questions_sans_suite.add(node.getId());
			}
		}

		synchronized(DatabaseBot.class)
		{
			m_questions_sans_suite = questions_sans_suite;
		}
	}

	public NodePhrase ajouterPhrase(String message)
	{
		try (Transaction tx = m_database.beginTx())
		{
			Node node = m_database.createNode();
			node.setProperty("phrase", message);
			indexer(node, message);
			tx.success();
			return new NodePhrase(node);
		}
	}

	private void indexer(Node node, String message)
	{
		if (message.contains("?"))
		{
			m_index_exact.add(node, "question", message);
			m_index_simple_analyzer.add(node, "question", Sanitizer.sanitize(message).toLowerCase());
			m_index_french_analyzer.add(node, "question", Sanitizer.sanitize(message));
		}
		else
		{
			m_index_exact.add(node, "phrase", message);
			m_index_simple_analyzer.add(node, "phrase", Sanitizer.sanitize(message).toLowerCase());
			m_index_french_analyzer.add(node, "phrase", Sanitizer.sanitize(message));
		}
	}

	public void ajouterRelation(NodePhrase a, NodePhrase b)
	{
		try (Transaction tx = m_database.beginTx())
		{
			boolean found = false;

			if (a.m_node.hasRelationship(Direction.OUTGOING))
			{
				for (Relationship relation : a.m_node.getRelationships(Direction.OUTGOING, RelTypes.TO))
				{
					if (relation.getEndNode().equals(b.m_node))
					{
						relation.setProperty("count", (int) relation.getProperty("count") + 1);
						found = true;
						break;
					}
				}
			}

			if (!found)
			{
				Relationship to = a.m_node.createRelationshipTo(b.m_node, RelTypes.TO);
				to.setProperty("count", 1);
			}
			tx.success();
		}
	}

	public void charger(String fichier)
	{
		int count = 0;
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(fichier));
			String ligne = "";
			NodePhrase previous = null;
			while ((ligne = br.readLine()) != null)
			{
				count++;
				if (count % 100 == 0)
					System.err.println(count);

				if (ligne.isEmpty())
					continue;

				if (ligne.contentEquals("###") || ligne.contentEquals("---") ||
					(ligne = Sanitizer.format(Sanitizer.removeEmoticones(SMSDecoder.decoderPhrase(ligne)))).isEmpty())
				{
					previous = null;
					continue;
				}

				NodePhrase meilleure = trouverMessageExact(ligne);

				if (meilleure == null)
					meilleure = ajouterPhrase(ligne);

				if (previous != null)
					ajouterRelation(previous, meilleure);

				previous = meilleure;
			}
			br.close();
			updateQuestionsSansSuite();
		} catch (Exception e) {
			System.err.println("Fichier introuvable ou problème de lecture ...");
			e.printStackTrace();
			return;
		}
	}

	public void reconstruireIndex() {
		synchronized(DatabaseBot.class)
		{
			try (Transaction tx = m_database.beginTx())
			{
				m_index_exact.delete();
				m_index_simple_analyzer.delete();
				m_index_french_analyzer.delete();
				tx.success();
			}

			try (Transaction tx = m_database.beginTx())
			{
				m_index_exact = m_database.index().forNodes("phrase-exact");
				m_index_simple_analyzer = m_database.index().forNodes("phrase-simple-analyzer", MapUtil.stringMap(IndexManager.PROVIDER, "lucene", "analyzer", SimpleAnalyzer.class.getName(), "similarity", SimilarityBot.class.getName()));
				m_index_french_analyzer = m_database.index().forNodes("phrase-french-analyzer", MapUtil.stringMap(IndexManager.PROVIDER, "lucene", "analyzer", FrAnalyzer.class.getName(), "similarity", SimilarityBot.class.getName()));

				Iterable<Node> nodes = GlobalGraphOperations.at(m_database).getAllNodes();

				for (Node node : nodes)
				{
					indexer(node, (String)node.getProperty("phrase"));
				}
				tx.success();
			}
		}
	}

	public List<SimpleEntry<Long, String>> trouverRelationsBoucle()
	{
		List<SimpleEntry<Long, String>> result = new LinkedList<SimpleEntry<Long, String>>();
		try (Transaction tx = m_database.beginTx())
		{
			Iterable<Relationship> relationShips = GlobalGraphOperations.at(m_database).getAllRelationships();

			for (Relationship relation : relationShips)
			{
				if (relation.getStartNode().equals(relation.getEndNode()))
					result.add(new SimpleEntry<Long, String>(relation.getId(), (String) relation.getStartNode().getProperty("phrase")));
			}
		}

		return result;
	}

	List<SimpleEntry<Long, String>> chercherMots(String mots)
	{
		Boolean estQuestion = mots.contains("?");
		mots = Sanitizer.sanitize(mots).toLowerCase();
		if (mots.isEmpty())
			return new LinkedList<>();

		List<SimpleEntry<Long, String>> phrases = new LinkedList<>();
		try (Transaction tx = m_database.beginTx())
		{
			QueryParser queryParser = new QueryParser(Version.LUCENE_36, estQuestion ? "question" : "phrase", new SimpleAnalyzer(Version.LUCENE_36));
			queryParser.setDefaultOperator(QueryParser.AND_OPERATOR);
			IndexHits<Node> results = m_index_simple_analyzer.query(queryParser.parse(mots));
			for (Node phrase : results)
				phrases.add(new SimpleEntry<Long, String>(phrase.getId(), (String)phrase.getProperty("phrase")));
		}
		catch (Exception e) {
			e.printStackTrace();
			return phrases;
		}

		return phrases;
	}

	List<SimpleEntry<Long, String>> chercherExact(String morceau)
	{
		morceau = morceau.toLowerCase();
		if (morceau.isEmpty())
			return new LinkedList<>();

		List<SimpleEntry<Long, String>> phrases = new LinkedList<>();

		try (Transaction tx = m_database.beginTx())
		{
			Iterable<Node> nodes = GlobalGraphOperations.at(m_database).getAllNodes();

			for (Node node : nodes)
			{
				String message = (String)node.getProperty("phrase");
				if (message.toLowerCase().contains(morceau))
					phrases.add(new SimpleEntry<Long, String>(node.getId(), message));
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			return phrases;
		}

		return phrases;
	}

	void setNode(Long id, String message)
	{
		try (Transaction tx = m_database.beginTx())
		{
			Node node = m_database.getNodeById(id);
			node.setProperty("phrase", message);
			tx.success();
		}
	}

	void supprimerNode(Long id)
	{
		try (Transaction tx = m_database.beginTx())
		{
			Node node = m_database.getNodeById(id);
			for (Relationship relation : node.getRelationships())
				relation.delete();

			m_index_exact.remove(node);
			m_index_simple_analyzer.remove(node);
			m_index_french_analyzer.remove(node);
			node.delete();
			tx.success();
		}
	}

	void supprimerRelation(Long id)
	{
		try (Transaction tx = m_database.beginTx())
		{
			Relationship relation = m_database.getRelationshipById(id);
			relation.delete();
			tx.success();
		}
	}

	public void verifier(Boolean corriger) {
		try (Transaction tx = m_database.beginTx())
		{
			Iterable<Node> nodes = GlobalGraphOperations.at(m_database).getAllNodes();

			for (Node node : nodes)
			{
				String before = (String)node.getProperty("phrase");
				String after = Sanitizer.format(Sanitizer.removeEmoticones(SMSDecoder.decoderPhrase(before)));
				if (!before.equals(after))
				{
					System.out.println(before);
					System.out.println(after);
					if (corriger)
						node.setProperty("phrase", after);
				}
			}

			if (corriger)
				tx.success();
		}
	}

	public void hotfix(Boolean corriger) {
		Iterable<SimpleEntry<Long, String>> nodes = chercherExact("est'est");

		try (Transaction tx = m_database.beginTx())
		{
			for (SimpleEntry<Long, String> node : nodes)
			{
				String before = node.getValue();
				String after = before.replace("est'est", "est");
				System.out.println(before);
				System.out.println(after);
				if (corriger)
					m_database.getNodeById(node.getKey()).setProperty("phrase", after);
			}

			if (corriger)
				tx.success();
		}

		nodes = chercherExact("es'es");

		try (Transaction tx = m_database.beginTx())
		{
			for (SimpleEntry<Long, String> node : nodes)
			{
				String before = node.getValue();
				String after = before.replace("es'es", "es");
				System.out.println(before);
				System.out.println(after);
				if (corriger)
					m_database.getNodeById(node.getKey()).setProperty("phrase", after);
			}

			if (corriger)
				tx.success();
		}
	}
}

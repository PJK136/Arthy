package org.pjk136.Arthy;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseSMS {
	private static volatile DatabaseSMS instance = null;

	private Connection m_connexion;

	private DatabaseSMS() throws ClassNotFoundException, SQLException
	{
		Class.forName("org.sqlite.JDBC");
		m_connexion = DriverManager.getConnection("jdbc:sqlite:sms.db");
		executeStatementUpdate("CREATE TABLE IF NOT EXISTS MOTS " +
				"(ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
				" MOT            TEXT    NOT NULL COLLATE NOCASE)");
		executeStatementUpdate("CREATE TABLE IF NOT EXISTS SMS " +
				"(SMS            TEXT    NOT NULL COLLATE NOCASE," +
				" MOT_ID         INT     NOT NULL," +
				" FOREIGN KEY(MOT_ID) REFERENCES MOTS(ID))");
		executeStatementUpdate("CREATE INDEX IF NOT EXISTS SMS_INDEX ON SMS(SMS)");

		Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                try {
					m_connexion.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
            }
        } );
	}

	public final static DatabaseSMS getInstance()
	{
		if (DatabaseSMS.instance == null)
		{
			synchronized(DatabaseSMS.class)
			{
				if (DatabaseSMS.instance == null)
				{
					try
					{
						DatabaseSMS.instance = new DatabaseSMS();
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}
		}
		return DatabaseSMS.instance;
	}

	public String decoderMot(String sms)
	{
		Statement statement = null;
		ResultSet result = null;
		try
		{
			statement = m_connexion.createStatement();
			result = statement.executeQuery(String.format("SELECT MOT FROM MOTS WHERE ID=(SELECT MOT_ID FROM SMS WHERE SMS=\"%s\")", sms));
			if (result.next())
			{
				String mot = result.getString("MOT");
				statement.close();
				result.close();
				return mot;
			}
			statement.close();
			result.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return sms;
		}
		return sms;
	}

	public void charger(String fichier)
	{
		int count = 0;
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(fichier));
			String ligne = "";
			int id_mot = -1;
			while ((ligne = br.readLine()) != null)
			{
				count++;
				if (count % 100 == 0)
					System.err.println(count);

				if (ligne.isEmpty())
					continue;

				if (ligne.contentEquals("---"))
				{
					id_mot = -1;
					continue;
				}

				if (id_mot == -1)
				{
					id_mot = getIdMot(ligne);
					if (id_mot == -1)
						id_mot = ajouterMot(ligne);
					continue;
				}
				else
					ajouterSMS(ligne, id_mot);
			}
			br.close();
		} catch (Exception e) {
			System.err.println("Fichier introuvable ou problème de lecture ...");
			e.printStackTrace();
			return;
		}
	}

	private void executeStatementUpdate(String sql) throws SQLException
	{
		Statement statement = m_connexion.createStatement();
		statement.executeUpdate(sql);
		statement.close();
	}

	private int getIdMot(String mot)
	{
		try
		{
			int id_mot = -1;
			Statement statement = m_connexion.createStatement();
			ResultSet results = statement.executeQuery(String.format("SELECT ID FROM MOTS WHERE MOT=\"%s\"", mot));
			if (results.next())
				id_mot = results.getInt("ID");
			results.close();
			statement.close();
			return id_mot;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return -1;
		}
	}

	private int ajouterMot(String mot)
	{
		try
		{
			int id_mot = 0;
			Statement statement = m_connexion.createStatement();
			statement.executeUpdate(String.format("INSERT INTO MOTS (MOT) VALUES(\"%s\")", mot));
			id_mot = statement.getGeneratedKeys().getInt(1);
			statement.close();
			return id_mot;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return -1;
		}
	}

	private void ajouterSMS(String sms, int id_mot)
	{
		try {
			Statement statement = m_connexion.createStatement();
			ResultSet results = statement.executeQuery(String.format("SELECT MOT_ID FROM SMS WHERE SMS=\"%s\"", sms));
			if (results.next())
			{
				results.close();
				statement.close();
				return;
			}
			results.close();
			statement.close();

			executeStatementUpdate(String.format("INSERT INTO SMS (SMS, MOT_ID) VALUES(\"%s\", %d)", sms, id_mot));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return;
		}
	}
}

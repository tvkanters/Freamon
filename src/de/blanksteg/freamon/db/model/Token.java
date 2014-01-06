package de.blanksteg.freamon.db.model;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;

import de.blanksteg.freamon.db.Database;

/**
 * A token value used in quads.
 */
public class Token {

    /** The max length of tokens */
    public final static int MAX_LENGTH = 20;

    /** The log4j logger to print log output to */
    private static final Logger l = Logger.getLogger("de.blanksteg.freamon.db");

    /** The ID of the quad in the DB */
    private final int mId;

    /** The tokens in this quad */
    private final String mToken;

    /**
     * Saves the token specified by its value. Will first attempt to retrieve a token from the database.
     * 
     * @param database
     *            The database to put the token in
     * @param value
     *            The token value
     * 
     * @return The retrieved or created token
     */
    public static Token put(final Database database, final String value) {
        // Try to retrieve an existing quad
        final Token token = get(database, value);
        if (token != null) {
            return token;
        } else {
            // If no token exists, insert a new one
            try {
                final int id = database.insert("INSERT INTO TOKENS (TOKEN) VALUES ('" + sqlToken(value) + "')");
                return new Token(id, value);
            } catch (SQLException ex) {
                l.error("Can't insert token", ex);
                return null;
            }
        }
    }

    /**
     * Retrieves a token based on the given value.
     * 
     * @param database
     *            The database to search
     * @param keyword
     *            The keyword to search for
     * 
     * @return The matching token or null if it doesn't exist
     */
    public static Token get(final Database database, final String keyword) {
        if (keyword == null) {
            return null;
        }

        try {
            ResultSet r = database.query("SELECT * FROM TOKENS WHERE TOKEN='" + sqlToken(keyword) + "'");

            if (!r.first()) {
                return null;
            }

            return new Token(r.getInt("ID"), r.getString("TOKEN"));

        } catch (SQLException ex) {
            l.error("Couldn't fetch token", ex);
            return null;
        }
    }

    /**
     * Retrieves a token based on the given value.
     * 
     * @param database
     *            The database to search
     * @param keyword
     *            The keyword ID to search for
     * 
     * @return The matching token or null if it doesn't exist
     */
    public static Token get(final Database database, final int keyword) {
        try {
            ResultSet r = database.query("SELECT * FROM TOKENS WHERE ID=" + keyword);

            if (!r.first()) {
                return null;
            }

            return new Token(r.getInt("ID"), r.getString("TOKEN"));

        } catch (SQLException ex) {
            l.error("Couldn't fetch token", ex);
            return null;
        }
    }

    /**
     * Retrieves a token based on the given value.
     * 
     * @param database
     *            The database to search
     * @param keyword
     *            The keyword to search for
     * 
     * @return A random token or null if it doesn't exist
     */
    public static Token getRandom(final Database database) {
        try {
            ResultSet r = database.query("SELECT * FROM TOKENS ORDER BY RAND() LIMIT 1");

            if (!r.first()) {
                return null;
            }

            return new Token(r.getInt("ID"), r.getString("TOKEN"));

        } catch (SQLException ex) {
            l.error("Couldn't fetch token", ex);
            return null;
        }
    }

    /**
     * Prepares the token for SQL usage by limiting length and replacing dangerous characters.
     * 
     * @param token
     *            The token to use in SQL
     * 
     * @return The safe token
     */
    private static String sqlToken(final String token) {
        return StringEscapeUtils.escapeSql(limitToken(token));
    }

    /**
     * Prepares the tokens by limiting length.
     * 
     * @param token
     *            The token to limit
     * 
     * @return The limited token
     */
    private static String limitToken(final String token) {
        if (token != null && token.length() > 20) {
            return token.substring(0, 20);
        }
        return token;
    }

    /**
     * Prepares a new token model.
     * 
     * @param database
     *            The DB to the token is in
     * @param id
     *            The token's DB ID
     * @param token
     *            The token value
     */
    private Token(final int id, final String token) {
        mId = id;
        mToken = limitToken(token);
    }

    /**
     * Retrieves a token ID.
     * 
     * @return The token ID
     */
    public int getId() {
        return mId;
    }

    /**
     * Retrieves a token value.
     * 
     * @return The token value
     */
    public String getToken() {
        return mToken;
    }

    @Override
    public String toString() {
        return mToken;
    }

    @Override
    public int hashCode() {
        return mToken.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Token)) {
            return false;
        }
        final Token other = (Token) o;
        return other.mToken.equals(mToken);
    }

}
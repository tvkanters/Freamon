package de.blanksteg.freamon.db.model;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.log4j.Logger;

import de.blanksteg.freamon.db.Database;

/**
 * A part of a sentence containing four tokens where tokens can be either words or punctuation pieces.
 */
public class Quad {

    /** The log4j logger to print log output to */
    private static final Logger l = Logger.getLogger("de.blanksteg.freamon.db");

    /** The DB that the quad is stored in */
    private final Database mDatabase;

    /** The ID of the quad in the DB */
    private final int mId;

    /** The tokens in this quad */
    private final Token[] mTokens;

    /** Whether or not this quad can start a sentence */
    private boolean mCanStart = false;

    /** Whether or not this quad can end a sentence */
    private boolean mCanEnd = false;

    /**
     * Saves the quad specified by its parameters. Will first attempt to retrieve a quad from the database and update
     * its start and end options if needed.
     * 
     * @param database
     *            The database to put the quad in
     * @param keyword1
     *            The first token
     * @param keyword2
     *            The second token
     * @param keyword3
     *            The third token
     * @param keyword4
     *            The fourth token
     * @param canStart
     *            Whether or not this quad can start a sentence
     * @param canEnd
     *            Whether or not this quad can end a sentence
     * 
     * @return The retrieved or created quad
     */
    public static Quad put(final Database database, final String keyword1, final String keyword2,
            final String keyword3, final String keyword4, final boolean canStart, final boolean canEnd) {

        // Try to retrieve an existing quad
        final Quad quad = get(database, keyword1, keyword2, keyword3, keyword4);
        if (quad != null) {
            try {
                // Allow start and end values if now made possible
                if (!quad.mCanStart && canStart) {
                    quad.mCanStart = true;
                    database.update("UPDATE QUADS SET CANSTART=TRUE WHERE ID=" + quad.mId);
                }
                if (!quad.mCanEnd && canEnd) {
                    quad.mCanEnd = true;
                    database.update("UPDATE QUADS SET CANEND=TRUE WHERE ID=" + quad.mId);
                }
            } catch (SQLException ex) {
                l.error("Can't update quad", ex);
            }
            return quad;
        } else {
            // If no quad exists, insert a new one
            try {
                final Token token1 = Token.put(database, keyword1);
                final Token token2 = Token.put(database, keyword2);
                final Token token3 = Token.put(database, keyword3);
                final Token token4 = Token.put(database, keyword4);

                final int id = database
                        .insert("INSERT INTO QUADS (TOKEN1, TOKEN2, TOKEN3, TOKEN4, CANSTART, CANEND) VALUES ("
                                + token1.getId() + ", " + token2.getId() + ", " + token3.getId() + ", "
                                + token4.getId() + ", " + canStart + ", " + canEnd + ")");
                return new Quad(database, id, token1, token2, token3, token4, canStart, canEnd);
            } catch (SQLException ex) {
                l.error("Can't insert quad", ex);
                return null;
            }
        }
    }

    /**
     * Retrieves a quad based on the given tokens.
     * 
     * @param database
     *            The database to search
     * @param token1
     *            The first token or null for any
     * @param token2
     *            The second token or null for any
     * @param token3
     *            The third token or null for any
     * @param token4
     *            The fourth token or null for any
     * 
     * @return The matching quad or null if no quad with the specified tokens was found
     */
    public static Quad get(final Database database, final Token token1, final Token token2, final Token token3,
            final Token token4) {
        String where = "SELECT * FROM QUADS";

        boolean tokenAdded = false;
        final Token[] tokens = { token1, token2, token3, token4 };
        for (int i = 0; i < 4; ++i) {
            if (tokens[i] == null) {
                continue;
            }

            where += (tokenAdded ? " AND" : " WHERE");
            where += " TOKEN" + (i + 1) + "=" + tokens[i].getId();

            tokenAdded = true;
        }

        where += " ORDER BY RAND() LIMIT 1";

        try {
            final ResultSet r = database.query(where);

            if (!r.first()) {
                return null;
            }

            return new Quad(database, r.getInt("ID"), r.getInt("TOKEN1"), r.getInt("TOKEN2"), r.getInt("TOKEN3"),
                    r.getInt("TOKEN4"), r.getBoolean("CANSTART"), r.getBoolean("CANEND"));

        } catch (SQLException ex) {
            l.error("Couldn't fetch quad", ex);
            return null;
        }
    }

    /**
     * Retrieves a quad based on the given tokens.
     * 
     * @param database
     *            The database to search
     * @param token1
     *            The first token or null for any
     * @param token2
     *            The second token or null for any
     * @param token3
     *            The third token or null for any
     * @param token4
     *            The fourth token or null for any
     * 
     * @return The matching quad or null if no quad with the specified tokens was found
     */
    public static Quad get(final Database database, final String token1, final String token2, final String token3,
            final String token4) {
        return get(database, Token.get(database, token1), Token.get(database, token2), Token.get(database, token3),
                Token.get(database, token4));
    }

    /**
     * Retrieves a quad based on the given keyword.
     * 
     * @param database
     *            The database to search
     * @param keyword
     *            The keyword that must occur within the quad
     * 
     * @return The matching quad, a random quad if there is no match or null if there are no quads
     */
    public static Quad get(final Database database, final String keyword) {
        Token token = Token.get(database, keyword);
        if (token == null) {
            token = Token.getRandom(database);
            if (token == null) {
                return null;
            }
        }

        final int tokenId = token.getId();
        String where = "SELECT * FROM QUADS WHERE TOKEN1=" + tokenId + " OR TOKEN2=" + tokenId + " OR TOKEN3="
                + tokenId + " OR TOKEN4=" + tokenId + " ORDER BY RAND() LIMIT 1";
        try {
            ResultSet r = database.query(where);

            // If no matching quad is found, generate a random one
            if (!r.first()) {
                r = database.query(where);
                if (!r.first()) {
                    return null;
                }
            }

            return new Quad(database, r.getInt("ID"), r.getInt("TOKEN1"), r.getInt("TOKEN2"), r.getInt("TOKEN3"),
                    r.getInt("TOKEN4"), r.getBoolean("CANSTART"), r.getBoolean("CANEND"));

        } catch (SQLException ex) {
            l.error("Couldn't fetch quad", ex);
            return null;
        }
    }

    /**
     * Prepares a new quad model.
     * 
     * @param database
     *            The DB to the quad is in
     * @param id
     *            The quad's DB ID
     * @param token1
     *            The first token
     * @param token2
     *            The second token
     * @param token3
     *            The third token
     * @param token4
     *            The fourth token
     * @param canStart
     *            Whether or not this quad can start a sentence
     * @param canEnd
     *            Whether or not this quad can end a sentence
     */
    private Quad(final Database database, final int id, final int token1, final int token2, final int token3,
            final int token4, final boolean canStart, final boolean canEnd) {
        this(database, id, Token.get(database, token1), Token.get(database, token2), Token.get(database, token3), Token
                .get(database, token4), canStart, canEnd);
    }

    /**
     * Prepares a new quad model.
     * 
     * @param database
     *            The DB to the quad is in
     * @param id
     *            The quad's DB ID
     * @param token1
     *            The first token
     * @param token2
     *            The second token
     * @param token3
     *            The third token
     * @param token4
     *            The fourth token
     * @param canStart
     *            Whether or not this quad can start a sentence
     * @param canEnd
     *            Whether or not this quad can end a sentence
     */
    private Quad(final Database database, final int id, final Token token1, final Token token2, final Token token3,
            final Token token4, final boolean canStart, final boolean canEnd) {
        mDatabase = database;
        mId = id;
        mTokens = new Token[] { token1, token2, token3, token4 };
        mCanStart = canStart;
        mCanEnd = canEnd;
    }

    /**
     * Retrieves a token from the quad.
     * 
     * @param index
     *            The index of the token, may range from 0 to 3
     * 
     * @return The token at the specified index
     */
    public Token getToken(final int index) {
        return mTokens[index];
    }

    /**
     * Whether or not a sentence can start with this quad.
     * 
     * @return True iff the sentence may start with this quad
     */
    public boolean canStart() {
        return mCanStart;
    }

    /**
     * Whether or not a sentence can end with this quad.
     * 
     * @return True iff the sentence may end with this quad
     */
    public boolean canEnd() {
        return mCanEnd;
    }

    /**
     * Retrieves a random quad that may follow this one or null if none is available.
     * 
     * @return A quad that can follow this one
     */
    public Quad getNext() {
        return get(mDatabase, mTokens[1], mTokens[2], mTokens[3], null);
    }

    /**
     * Retrieves a random quad that may preceed this one or null if none is available.
     * 
     * @return A quad that can preceed this one
     */
    public Quad getPrev() {
        return get(mDatabase, null, mTokens[0], mTokens[1], mTokens[2]);
    }

    @Override
    public String toString() {
        return mTokens[0].toString() + mTokens[1].toString() + mTokens[2].toString() + mTokens[3].toString();
    }

    @Override
    public int hashCode() {
        return mTokens[0].hashCode() + mTokens[1].hashCode() + mTokens[2].hashCode() + mTokens[3].hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Quad)) {
            return false;
        }
        final Quad other = (Quad) o;
        return other.mTokens[0].equals(mTokens[0]) && other.mTokens[1].equals(mTokens[1])
                && other.mTokens[2].equals(mTokens[2]) && other.mTokens[3].equals(mTokens[3]);
    }

}
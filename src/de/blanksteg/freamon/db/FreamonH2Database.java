package de.blanksteg.freamon.db;

import java.sql.SQLException;

import de.blanksteg.freamon.db.model.Token;

/**
 * A helper class for H2 database connections, specifically for the Freamon environment.
 * 
 * @author Timon Kanters
 */
public class FreamonH2Database extends H2Database {

    /**
     * Creates a new database connection to a temporary clone of the target database.
     * 
     * @param dbLoc
     *            The database to connect to
     * 
     * @throws SQLException
     */
    public FreamonH2Database(final String dbLoc) throws SQLException {
        super(dbLoc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepareDatabase() throws SQLException {
        // Add the table for know users
        update("CREATE TABLE PEOPLENAMES (ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(15), UNIQUE(NAME))");

        // Add the table containing all known tokens
        update("CREATE TABLE TOKENS (ID INT PRIMARY KEY AUTO_INCREMENT, TOKEN VARCHAR(" + Token.MAX_LENGTH + "), "
                + "UNIQUE(TOKEN))");

        // Add the table containing the quads with tokens
        String quadTable = "CREATE TABLE QUADS (ID INT PRIMARY KEY AUTO_INCREMENT";
        String uniqueConstraint = "";
        String keyConstraint = "";
        for (int i = 1; i <= 4; ++i) {
            quadTable += ", TOKEN" + i + " INT";
            uniqueConstraint += (uniqueConstraint.length() > 0 ? ", " : "") + "TOKEN" + i;
            keyConstraint += ", FOREIGN KEY(TOKEN" + i + ") REFERENCES TOKENS(ID)";
        }
        quadTable += ", CANSTART BOOLEAN, CANEND BOOLEAN, UNIQUE(" + uniqueConstraint + ")" + keyConstraint + ")";
        update(quadTable);
    }
}

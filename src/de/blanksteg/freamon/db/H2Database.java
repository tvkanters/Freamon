package de.blanksteg.freamon.db;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

/**
 * A helper class for H2 database connections.
 * 
 * @author Timon Kanters
 */
public abstract class H2Database implements Database {
    /** The log4j logger to print log output to */
    private static final Logger l = Logger.getLogger("de.blanksteg.freamon.db");

    /** The protocol for H2 database files */
    private static final String DB_PREFIX = "jdbc:h2:file:";

    /** The extension for H2 database files */
    private static final String DB_SUFFIX = ".h2.db";

    /** Maximum amount of time we try to get a database connection, also limits the amount of parallel instances */
    private static final int MAX_CONNECTION_ATTEMPTS = 20;

    /** The database that we're connected to */
    private Connection mConn;

    /**
     * Creates a new database connection to a temporary clone of the target database.
     * 
     * @param dbLoc
     *            The database to connect to
     * 
     * @throws SQLException
     */
    public H2Database(final String dbLoc) throws SQLException {
        Connection conn = null;
        final File dbFile = new File(dbLoc + DB_SUFFIX);

        // If the database doesn't exist, prepare it
        if (!dbFile.exists()) {
            mConn = DriverManager.getConnection(DB_PREFIX + dbLoc, "sa", "");
            prepareDatabase();
            mConn.close();
        }

        // Since we cannot use one DB file for multiple instances, copy it and use that file instead
        for (int attempt = 0; attempt < MAX_CONNECTION_ATTEMPTS; ++attempt) {
            final String dbLocTemp = dbLoc + "-" + attempt;
            final File dbFileTemp = new File(dbLocTemp + DB_SUFFIX);

            // If the temp DB already seems to be there, check if it can be freed
            if (dbFileTemp.exists()) {
                if (inUse(dbLocTemp)) {
                    // If the DB is in use, skip it and try another
                    continue;

                } else {
                    // Delete the temp DB if it isn't used
                    l.debug("Deleting temp DB " + dbLocTemp);
                    dbFileTemp.delete();
                }
            }

            // Try to copy the database
            try {
                FileUtils.copyFile(dbFile, dbFileTemp);
            } catch (final IOException ex) {
                // Can't copy database file
                continue;
            }

            conn = connect(dbLocTemp);
            break;
        }

        if (conn == null) {
            throw new RuntimeException("Could not connect to DB");
        }

        mConn = conn;

        l.info("Connected to DB: " + mConn.getMetaData().getURL());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultSet query(final String query) throws SQLException {
        return query(mConn, query);
    }

    /**
     * Executes the given query on the connection.
     * 
     * @param conn
     *            The connection to execute the query on
     * @param query
     *            The query to execute
     * 
     * @return The result set of the query
     * 
     * @throws SQLException
     */
    public static ResultSet query(final Connection conn, final String query) throws SQLException {
        return conn.prepareStatement(query).executeQuery();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int update(final String update) throws SQLException {
        return update(mConn, update);
    }

    /**
     * Executes the given update on the connection.
     * 
     * @param conn
     *            The connection to execute the update on
     * @param update
     *            The update to execute
     * 
     * @return The amount of affected rows
     * 
     * @throws SQLException
     */
    public static int update(final Connection conn, final String update) throws SQLException {
        return conn.prepareStatement(update).executeUpdate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int insert(final String update) throws SQLException {
        return insert(mConn, update);
    }

    /**
     * Executes the given insert action on the connection.
     * 
     * @param conn
     *            The connection to execute the insert on
     * @param insert
     *            The insert to execute
     * 
     * @return The new row's ID or -1 if no ID could be retrieved
     * 
     * @throws SQLException
     */
    public static int insert(final Connection conn, final String insert) throws SQLException {
        final PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
        ps.executeUpdate();
        ResultSet r = ps.getGeneratedKeys();
        if (r.first()) {
            return r.getInt(1);
        } else {
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean save() {
        try {
            final String dbLocTemp = mConn.getMetaData().getURL().substring(DB_PREFIX.length());
            final String dbLoc = dbLocTemp.substring(0, dbLocTemp.lastIndexOf("-"));

            // Closing the connection will make sure all updates are in the file
            mConn.close();

            // Move the file to the original DB
            FileUtils.copyFile(new File(dbLocTemp + DB_SUFFIX), new File(dbLoc + DB_SUFFIX));
            l.info("Saved temp DB [" + dbLocTemp + "] to [" + dbLoc + "]");

            // Now that the DB is saved, connect again
            mConn = connect(dbLocTemp);

            return true;

        } catch (final SQLException ex) {
            l.error("Can't save database", ex);
            return false;

        } catch (final IOException ex) {
            l.error("Can't save database", ex);
            return false;
        }
    }

    /**
     * Connects to a database and returns the connection.
     * 
     * @param dbLoc
     *            The database location to connect to
     * 
     * @return The connection or null if the connection failed
     */
    private static Connection connect(final String dbLoc) {
        try {
            Class.forName("org.h2.Driver");
        } catch (final ClassNotFoundException ex) {
            l.error("H2 driver not found", ex);
            return null;
        }

        try {
            return DriverManager.getConnection(DB_PREFIX + dbLoc, "sa", "");
        } catch (final SQLException ex) {
            return null;
        }
    }

    /**
     * Checks if the database is in use.
     * 
     * @param dbLoc
     *            The location of the H2 database
     * 
     * @return True iff the database is currently in use
     */
    public static boolean inUse(final String dbLoc) {
        final Connection connection = connect(dbLoc);
        if (connection != null) {
            try {
                connection.close();
            } catch (final SQLException ex) {}
            return false;
        } else {
            return true;
        }
    }

    /**
     * Prepares a new database with the required structure.
     * 
     * @throws SQLException
     */
    public abstract void prepareDatabase() throws SQLException;
}

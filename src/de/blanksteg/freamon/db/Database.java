package de.blanksteg.freamon.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Interface for database related functionality.
 * 
 * @author Timon Kanters
 */
public interface Database {

    /**
     * Executes the given query on the connection.
     * 
     * @param query
     *            The query to execute
     * 
     * @return The result set of the query
     * 
     * @throws SQLException
     */
    public ResultSet query(String query) throws SQLException;

    /**
     * Executes the given update on the connection.
     * 
     * @param update
     *            The update to execute
     * 
     * @return The amount of affected rows
     * 
     * @throws SQLException
     */
    public int update(String update) throws SQLException;

    /**
     * Executes the given insert action on the connection.
     * 
     * @param insert
     *            The insert to execute
     * 
     * @return The new row's ID
     * 
     * @throws SQLException
     */
    public int insert(String insert) throws SQLException;

    /**
     * Saves the temporary database to the original one.
     * 
     * @return True iff the save action was successful
     */
    public boolean save();

}
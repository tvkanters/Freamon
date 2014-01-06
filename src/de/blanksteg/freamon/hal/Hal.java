package de.blanksteg.freamon.hal;

import java.io.IOException;

import de.blanksteg.freamon.db.Database;

/**
 * Inferface for a HAL class that parses sentences to remember them and to generate new content.
 */
public interface Hal {

    /**
     * Adds an entire documents to the brain. Useful for feeding in stray theses.
     * 
     * @param uri
     *            The location of the document
     */
    public abstract void addDocument(String uri) throws IOException;

    /**
     * Adds a new sentence to the brain.
     * 
     * @param sentence
     *            The sentence to learn
     */
    public abstract void addSentence(String sentence);

    /**
     * Generate a random sentence from the brain.
     * 
     * @return The generated random sentence
     */
    public abstract String getSentence();

    /**
     * Generate a sentence that includes (if possible) the specified word.
     * 
     * @param keyword
     *            The word to base the sentence on
     * 
     * @return The sentence relevant to the given word
     */
    public abstract String getSentence(String keyword);

    /**
     * Saves this instance to a permanent solution.
     * 
     * @return True iff the save action was successful
     */
    public abstract boolean save();

    /**
     * Retrieves the database this instance is linked to.
     * 
     * @return The database for this instance
     */
    public abstract Database getDatabase();
}
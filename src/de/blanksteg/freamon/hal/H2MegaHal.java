/*
Copyright Paul James Mutton, 2001-2004, http://www.jibble.org/

This file is part of JMegaHal.

This software is dual-licensed, allowing you to choose between the GNU
General Public License (GPL) and the www.jibble.org Commercial License.
Since the GPL may be too restrictive for use in a proprietary application,
a commercial license is also provided. Full license information can be
found at http://www.jibble.org/licenses/

$Author: pjm2 $
$Id: JMegaHal.java,v 1.4 2004/02/01 13:24:06 pjm2 Exp $

 */

package de.blanksteg.freamon.hal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Pattern;

import de.blanksteg.freamon.db.Database;
import de.blanksteg.freamon.db.model.Quad;

/**
 * Implementation of MegaHal that stores its content in a database.
 */
public class H2MegaHal implements Hal {

    /** Valid characters in regex format, non-matching chars are seen as punctuation */
    public static final Pattern WORD_PATTERN = Pattern.compile("[\\p{IsAlphabetic}0-9-_']");

    /** Characters indicating the end of a sentence */
    public static final String END_CHARS = ".!?";

    /** The database containing the language information */
    private final Database mDatabase;

    /**
     * Construct an instance of MegaHal with the specified database as brain.
     *
     * @param database
     *            The database to read and write sentences in
     */
    public H2MegaHal(final Database database) {
        mDatabase = database;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDocument(final String uri) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(uri).openStream()));
        StringBuffer buffer = new StringBuffer();
        int ch = 0;
        while ((ch = reader.read()) != -1) {
            buffer.append((char) ch);
            if (END_CHARS.indexOf((char) ch) >= 0) {
                addSentence(buffer.toString().replaceAll("[\r\n ]+", " "));
                buffer = new StringBuffer();
            }
        }
        addSentence(buffer.toString());
        reader.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSentence(final String sentence) {
        final ArrayList<String> tokens = new ArrayList<String>();
        final char[] chars = sentence.trim().toCharArray();

        // Split sentences into parts of words and punctuation/space
        boolean punctuation = false;
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < chars.length; ++i) {
            final char ch = chars[i];

            // Check if this character switches the token type
            if (WORD_PATTERN.matcher(new Character(ch).toString()).matches() == punctuation) {
                // Add the token if it's filled
                final String token = buffer.toString();
                if (token.length() > 0) {
                    tokens.add(token);
                }

                // Prepare for the next token
                punctuation = !punctuation;
                buffer = new StringBuffer();
            }

            buffer.append(ch);
        }

        // Add the last leftover buffer as token to the parts
        tokens.add(buffer.toString());

        // Store each quad of tokens in the DB
        Quad prevQuad = null;
        for (int i = 0; i < tokens.size() - 3; ++i) {
            final Quad quad = Quad.put(mDatabase, tokens.get(i), tokens.get(i + 1), tokens.get(i + 2),
                    tokens.get(i + 3), i == 0, i == tokens.size() - 4);

            if (prevQuad != null) {
                prevQuad.linkToRight(mDatabase, quad);
            }
            prevQuad = quad;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSentence() {
        return getSentence(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSentence(final String keyword) {
        final LinkedList<String> tokens = new LinkedList<String>();

        final Quad middleQuad = Quad.get(mDatabase, keyword);
        if (middleQuad == null) {
            return "";
        }

        // Add the quad as base of the sentence
        for (int i = 0; i < 4; i++) {
            tokens.add(middleQuad.getToken(i).getToken());
        }

        // Add possible following tokens to the sentence
        Quad quad = middleQuad;
        while (!quad.canEnd() && (quad = quad.getNext()) != null) {
            tokens.add(quad.getToken(3).getToken());
        }

        // Add possible preceding tokens to the sentence
        quad = middleQuad;
        while (!quad.canStart() && (quad = quad.getPrev()) != null) {
            tokens.addFirst(quad.getToken(0).getToken());
        }

        // Combine tokens to form a sentence
        final StringBuffer sentence = new StringBuffer();
        for (final String token : tokens) {
            sentence.append(token);
        }

        return sentence.toString();
    }

    /**
     * Saves the temporary database to the original one.
     *
     * @return True iff the save action was successful
     */
    @Override
    public boolean save() {
        return mDatabase.save();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Database getDatabase() {
        return mDatabase;
    }

}
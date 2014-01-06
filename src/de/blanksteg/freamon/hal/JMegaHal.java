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

import java.util.*;
import java.net.*;
import java.io.*;

import org.jibble.jmegahal.Quad;

import de.blanksteg.freamon.db.Database;

/**
 * Original implementation of MegaHal by Paul Mutton. Should not be used due to extreme memory requirements.
 */
public class JMegaHal implements Serializable, Hal {
    private static final long serialVersionUID = 5387014457168517161L;

    // These are valid chars for words. Anything else is treated as punctuation.
    public static final String WORD_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static final String END_CHARS = ".!?";

    /**
     * Construct an instance of JMegaHal with an empty brain.
     */
    public JMegaHal() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDocument(String uri) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(uri).openStream()));
        StringBuffer buffer = new StringBuffer();
        int ch = 0;
        while ((ch = reader.read()) != -1) {
            buffer.append((char) ch);
            if (END_CHARS.indexOf((char) ch) >= 0) {
                String sentence = buffer.toString();
                sentence = sentence.replace('\r', ' ');
                sentence = sentence.replace('\n', ' ');
                addSentence(sentence);
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
    public void addSentence(String sentence) {
        sentence = sentence.trim();
        ArrayList<String> parts = new ArrayList<String>();
        char[] chars = sentence.toCharArray();
        int i = 0;
        boolean punctuation = false;
        StringBuffer buffer = new StringBuffer();
        while (i < chars.length) {
            char ch = chars[i];
            if ((WORD_CHARS.indexOf(ch) >= 0) == punctuation) {
                punctuation = !punctuation;
                String token = buffer.toString();
                if (token.length() > 0) {
                    parts.add(token);
                }
                buffer = new StringBuffer();
                // i++;
                continue;
            }
            buffer.append(ch);
            i++;
        }
        String lastToken = buffer.toString();
        if (lastToken.length() > 0) {
            parts.add(lastToken);
        }

        if (parts.size() >= 4) {
            for (i = 0; i < parts.size() - 3; i++) {
                // System.out.println("\"" + parts.get(i) + "\"");
                Quad quad = new Quad((String) parts.get(i), (String) parts.get(i + 1), (String) parts.get(i + 2),
                        (String) parts.get(i + 3));
                if (quads.containsKey(quad)) {
                    quad = (Quad) quads.get(quad);
                } else {
                    quads.put(quad, quad);
                }

                if (i == 0) {
                    quad.setCanStart(true);
                }
                // else if (i == parts.size() - 4) {
                if (i == parts.size() - 4) {
                    quad.setCanEnd(true);
                }

                for (int n = 0; n < 4; n++) {
                    String token = (String) parts.get(i + n);
                    if (!words.containsKey(token)) {
                        words.put(token, new HashSet<Quad>(1));
                    }
                    HashSet<Quad> set = (HashSet<Quad>) words.get(token);
                    set.add(quad);
                }

                if (i > 0) {
                    String previousToken = (String) parts.get(i - 1);
                    if (!previous.containsKey(quad)) {
                        previous.put(quad, new HashSet<String>(1));
                    }
                    HashSet<String> set = (HashSet<String>) previous.get(quad);
                    set.add(previousToken);
                }

                if (i < parts.size() - 4) {
                    String nextToken = (String) parts.get(i + 4);
                    if (!next.containsKey(quad)) {
                        next.put(quad, new HashSet<String>(1));
                    }
                    HashSet<String> set = (HashSet<String>) next.get(quad);
                    set.add(nextToken);
                }

            }
        } else {
            // Didn't learn anything.
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
    public String getSentence(String word) {
        LinkedList<String> parts = new LinkedList<String>();

        Quad[] quads;
        if (words.containsKey(word)) {
            quads = (Quad[]) ((HashSet<?>) words.get(word)).toArray(new Quad[0]);
        } else {
            quads = (Quad[]) this.quads.keySet().toArray(new Quad[0]);
        }

        if (quads.length == 0) {
            return "";
        }

        Quad middleQuad = quads[rand.nextInt(quads.length)];
        Quad quad = middleQuad;

        for (int i = 0; i < 4; i++) {
            parts.add(quad.getToken(i));
        }

        while (quad.canEnd() == false) {
            String[] nextTokens = (String[]) ((HashSet<?>) next.get(quad)).toArray(new String[0]);
            String nextToken = nextTokens[rand.nextInt(nextTokens.length)];
            quad = (Quad) this.quads.get(new Quad(quad.getToken(1), quad.getToken(2), quad.getToken(3), nextToken));
            parts.add(nextToken);
        }

        quad = middleQuad;
        while (quad.canStart() == false) {
            String[] previousTokens = (String[]) ((HashSet<?>) previous.get(quad)).toArray(new String[0]);
            String previousToken = previousTokens[rand.nextInt(previousTokens.length)];
            quad = (Quad) this.quads.get(new Quad(previousToken, quad.getToken(0), quad.getToken(1), quad.getToken(2)));
            parts.addFirst(previousToken);
        }

        StringBuffer sentence = new StringBuffer();
        Iterator<String> it = parts.iterator();
        while (it.hasNext()) {
            String token = (String) it.next();
            sentence.append(token);
        }

        return sentence.toString();
    }

    // This maps a single word to a HashSet of all the Quads it is in.
    private HashMap<String, HashSet<Quad>> words = new HashMap<String, HashSet<Quad>>();

    // A self-referential HashMap of Quads.
    private HashMap<Quad, Quad> quads = new HashMap<Quad, Quad>();

    // This maps a Quad onto a Set of Strings that may come next.
    private HashMap<Quad, HashSet<String>> next = new HashMap<Quad, HashSet<String>>();

    // This maps a Quad onto a Set of Strings that may come before it.
    private HashMap<Quad, HashSet<String>> previous = new HashMap<Quad, HashSet<String>>();

    private Random rand = new Random();

    public boolean save() {
        // Saving is handled elsewhere
        return false;
    }

    public Database getDatabase() {
        // No database available
        return null;
    }

}
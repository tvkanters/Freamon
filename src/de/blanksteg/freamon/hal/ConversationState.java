package de.blanksteg.freamon.hal;

import java.util.TreeSet;

import de.blanksteg.freamon.LimitedTreeSet;
import de.blanksteg.freamon.nlp.Word;

/**
 * A ConversationState consists of a maximum amount of recent talkers and a maximum amount of recent words uttered in
 * that conversation. Additionally, a conversation has a name it can be identified with, although uniqueness of the name
 * is not guaranteed by this class.
 *
 * Conversation participants and words are stored in sorted sets, where participants are ordered according to
 * {@link String#CASE_INSENSITIVE_ORDER} and words as specified in {@link Word#COMPARATOR}.
 *
 * @author Marc Müller
 */
public class ConversationState {
    /** Maximum amount of recent talkers to remember. */
    private static final int TALKER_LIMIT = 6;
    /** Maximum amount of uttered words to store. */
    private static final int WORD_LIMIT = 12;

    /** This conversation's name. */
    private final String name;
    /** The set of recent conversation participants. */
    private final LimitedTreeSet<String> talkers = new LimitedTreeSet<String>(TALKER_LIMIT,
            String.CASE_INSENSITIVE_ORDER);
    /** The set of recently used words. */
    private final LimitedTreeSet<Word> words = new LimitedTreeSet<Word>(WORD_LIMIT, Word.COMPARATOR);
    /** The last person to speak */
    private String lastTalker;

    /**
     * Creates a new conversation instance by the given name with empty sets of recent talkers and words.
     *
     * @param newName
     *            The supposed name of the conversation.
     */
    public ConversationState(final String newName) {
        name = newName;
    }

    /**
     * Get his conversation's name.
     *
     * @return The name of this conversation.
     */
    public String getName() {
        return name;
    }

    /**
     * Add a person that recently participated in this conversation.
     *
     * @param talker
     *            The recent participant.
     */
    public void addTalker(final String talker) {
        lastTalker = talker;
        talkers.add(talker);
    }

    /**
     * Get the set of recent participants.
     *
     * @return The talkers.
     */
    public TreeSet<String> getTalkers() {
        return talkers.asTreeSet();
    }

    /**
     * @return The person to last speak
     */
    public String getLastTalker() {
        return lastTalker;
    }

    /**
     * Add a word recently uttered in this conversation.
     *
     * @param word
     *            The word to add.
     */
    public void addWord(final Word word) {
        words.add(word);
    }

    /**
     * Get the set of recently uttered words.
     *
     * @return The set of words.
     */
    public TreeSet<Word> getWords() {
        return words.asTreeSet();
    }
}

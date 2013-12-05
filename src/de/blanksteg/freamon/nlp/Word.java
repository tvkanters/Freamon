package de.blanksteg.freamon.nlp;

import java.util.Comparator;

/**
 * The Word class represents a simple association of a string with a certain {@link WordType}. It also
 * implements a basic {@link Comparator} that compares words according to their priority defined in
 * {@link WordType#getPriority()}.
 * 
 * @author Marc Müller 
 */
public class Word
{
  /** A publicly available instance of the comparator implemented in this class. */
  public static final PhraseComparator COMPARATOR = new PhraseComparator();
  
  /**
   * A comparator that orders words according to their priority in {@link WordType#getPriority()}.
   * Words that have the same priority are compared by their length.
   * 
   * @author Marc Müller
   */
  private static class PhraseComparator implements Comparator<Word>
  {
    @Override
    public int compare(Word arg0, Word arg1)
    {
      int ret = Integer.compare(arg0.type.getPriority(), arg1.type.getPriority());
      return ret != 0 ? ret : Integer.compare(arg0.word.length(), arg0.word.length());
    }
  }
  
  /** The actual string this word represents. */
  private final String    word;
  /** What type of word this is supposed to be. */
  private final WordType  type;
  
  /**
   * Creates a new word with the given string as content and type as the supposed word type.
   * 
   * @param newWord The string representation of the word.
   * @param newType The word's supposed type.
   * @throws IllegalArgumentException If null is given as the word's string.
   */
  public Word(String newWord, WordType newType)
  {
    if (newWord == null)
    {
      throw new IllegalArgumentException("The given word string is null.");
    }
    
    this.word = newWord;
    this.type = newType;
  }
  
  /**
   * Creates a new word with the given string as content and infers the type using the
   * {@link WordType#typeFromTag(String)} method.
   * 
   * @param newWord The word's string contents.
   * @param posTag  The tag to infer from.
   */
  public Word(String newWord, String posTag)
  {
    this(newWord, WordType.typeFromTag(posTag));
  }

  public String getWord()
  {
    return word;
  }

  public WordType getType()
  {
    return type;
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    result = prime * result + ((word == null) ? 0 : word.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Word other = (Word) obj;
    if (type != other.type)
      return false;
    if (word == null)
    {
      if (other.word != null)
        return false;
    } else if (!word.equals(other.word))
      return false;
    return true;
  }

  @Override
  public String toString()
  {
    return "Word [word=" + word + ", type=" + type + "]";
  }
}

package de.blanksteg.freamon.nlp;

import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * This enumeration defines certain types of words and a numerical priority for each word. The types
 * used here are more broad than most other NLP libraries but should suffice for the application.
 * 
 * It also offers methods to convert a string-tag to a word type. This conversion is performed
 * according to the classification found here:
 * {@link http://nlp.stanford.edu/pubs/LREC06_dependencies.pdf}
 * 
 * Priorities are subject to change heavily in each version.
 * 
 * @author Marc Müller
 */
public enum WordType
{
  OTHER(0),
  ADJECTIVE(30),
  VERB(40),
  NOUN(80),
  ROOT(70),
  OBJECT(50),
  SUBJECT(55);
  
  /** A mapping of string tags to their supposed word type in this application. */
  private static final Map<String, WordType> POSMAP = new HashMap<String, WordType>();
  
  static
  {
    /**
     * Populate the {@link WordType#POSMAP} with the appropriate mappings.
     */
    
    POSMAP.put("arg",   SUBJECT);
    POSMAP.put("subj",  SUBJECT);
    POSMAP.put("nsubj", SUBJECT);
    POSMAP.put("csubj", SUBJECT);
    
    POSMAP.put("obj",   OBJECT);
    POSMAP.put("dobj",  OBJECT);
    POSMAP.put("pobj",  OBJECT);
    POSMAP.put("ccomp", OBJECT);
    POSMAP.put("xcomp", OBJECT);
    
    POSMAP.put("amod",  ADJECTIVE);
    POSMAP.put("nn",    NOUN);
    POSMAP.put("prt",   VERB);
    
    POSMAP.put("root",  ROOT);
  }
  
  /**
   * Convert the given string tag to the appropriate word type.
   * 
   * @param posTag The tag to convert.
   * @return The type associated with it or {@link WordType#OTHER} if none was found.
   * @throws IllegalArgumentException If the tag is null.
   */
  public static WordType typeFromTag(String posTag)
  {
    if (posTag == null)
    {
      throw new IllegalArgumentException("The given tag was null.");
    }
    
    WordType ret = POSMAP.get(posTag);
    return ret == null ? OTHER : ret;
  }
  
  /**
   * Convert the given {@link GrammaticalRelation} to its appropriate word type.
   * 
   * @param gr The {@link GrammaticalRelation} to convert.
   * @return The supposed type.
   * @throws IllegalArgumentException If the relation is null.
   */
  public static WordType typeFromRelation(GrammaticalRelation gr)
  {
    if (gr == null)
    {
      throw new IllegalArgumentException("The given relation is null.");
    }
    
    String grString = gr.getShortName();
    return typeFromTag(grString);
  }
  
  /** A word type's priority. */
  private final int priority;
  
  /**
   * Create a new word type with the given priority.
   * 
   * @param newPriority The supposed priority.
   */
  WordType(int newPriority)
  {
    this.priority = newPriority;
  }
  
  /**
   * Get a word's priority.
   * 
   * @return The word's priority.
   */
  public int getPriority()
  {
    return this.priority;
  }
}

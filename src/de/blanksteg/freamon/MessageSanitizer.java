package de.blanksteg.freamon;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The MessageSanitizer is supposed to deal with the various idiosyncrasies inherent in online
 * communication and  random response generation. Its main functions are to make incoming messages
 * more usable for statistical analysis and outgoing messages more pleasing to the eye.
 * 
 * Generally, any URL is worthless for a statistical chatbot, because any mutation in a URL will
 * likely result in an invalid one. Hence, URLs are filtered from incoming messages. Certain special
 * characters used in online communication such as < and @ tend to spoil generated messages with
 * seemingly random usage of said characters, so these are filtered as well. Additionally, any
 * leading !word segments of a message are deleted because these are mainly chat commands. Leading
 * and trailing whitespace will be trimmed. 
 * 
 * When considering outgoing messages, this class mainly ensures capitalization at the beginning of
 * the response, removing redundant punctuation and spaces, and ensuring some common sentence
 * delimiter.
 * 
 * @author Marc Müller 
 */
public abstract class MessageSanitizer
{
  /** Regular expression used to match URLs. */
  private static final Pattern  URL     = Pattern.compile("\\b(([\\w-]+://?|www[.])[^\\s()<>]+(?:\\([\\w\\d]+\\)|([^[:punct:]\\s]|/?)))", Pattern.CASE_INSENSITIVE);
  /** Simple pattern to identify strings containing only whitespace. */
  private static final Pattern  SPACE   = Pattern.compile("^\\s*$");
  /** An array of characters to be filtered from messaged. */
  private static final String[] IGNORED = new String[]
  {
//    "<", "@", "*", "(", ")", "/", "\\", "[", "]", "\"", "^", ",", ".", "!"
    "<", "@", "*",  "\"", "^"
  };
  
  /** An array of characters JMegaHAL commonly misuses by giving them too much space. */
  private static final String[] REDUCE = new String[]
  {
    " , ", " . ", " ' ", " g ", "  "
  };
  
  /**
   * This method will filter the given message accordingly:
   * <ul>
   *  <li>Replace leading !word segments (where word is anything matching \w*).</li>
   *  <li>Remove any URL.</li>
   *  <li>Remove leading and trailing whitespace.</li>
   *  <li>Remove any occurrence of the characters in {@link MessageSanitizer#IGNORED}.</li>
   *  <li>Replace any double spaces with a single one until no double spaces are present.</li>
   *  <li>Clear the message if it only contains a number.</li>
   * </ul>
   * 
   * Said filtered message will then be returned.
   * 
   * @param message The message to filter.
   * @return The filtered message.
   */
  public static String filterMessage(String message)
  {
    message = message.replaceAll("!\\w*", "");
    
    message = URL.matcher(message).replaceAll("");
    message = message.replaceAll("^\\s*", "");
    message = message.replaceAll("\\s*$", "");
    
    for (String ignore : IGNORED)
    {
      message = message.replace(ignore, "");
    }
    
    while (message.indexOf("  ") != -1)
    {
      message = message.replace("  ", "");
    }
    
    if (message.matches("^\\d*$"))
    {
      return null;
    }
    
    return message;
  }
  
  /**
   * This method will take the given message and make it more appropriate for output. It will do:
   * <ul>
   *   <li>Replace any leading non-word characters.</li>
   *   <li>Capitalize the first letter.</li>
   *   <li>Remove any string from {@link MessageSanitizer#REDUCE} and replace it with it's compacted counterpart.</li>
   *   <li>Replace erroneous g's being placed at either start or end of the message.</li>
   *   <li>Insert a space to every . ! ? or , that directly leads into a word.</li>
   *   <li>Add a . if neither . nor ? nor ! is present at the end of the message.</li>
   * </ul>
   * 
   * The beautified message is then returned.
   * 
   * @param message The message to improve.
   * @return The improved version.
   */
  public static String beautifyMessage(String message)
  {
    String b = message;
    b = b.replaceAll("^[^a-zA-Z]*", "");
    b = b.substring(0, 1).toUpperCase() + b.substring(1);
    
    for (String reduction : REDUCE)
    {
      while (b.contains(reduction))
      {
        b = b.replace(reduction, reduction.substring(1));
      }
    }
    
    b = b.replaceAll("^g ", "");
    b = b.replaceAll(" g$", "");
    b = b.replaceAll(".[.]\\w", ". ");
    b = b.replaceAll(".[!]\\w", "! ");
    b = b.replaceAll(".[?]\\w", "? ");
    b = b.replaceAll(".[,]\\w", ", ");
    
    return b;
  }
  
  /**
   * This method simply checks if the given word contains only whitespace.
   * 
   * @param word The word to check.
   * @return True iff there are only whitespace characters in the given word.
   */
  public static boolean emptyString(String word)
  {
    Matcher matcher = SPACE.matcher(word);
    return matcher.matches();
  }
}

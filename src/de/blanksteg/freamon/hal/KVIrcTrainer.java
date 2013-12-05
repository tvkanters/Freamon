package de.blanksteg.freamon.hal;

import java.io.File;

/**
 * The KVIrcTrainer extends the common {@link FileTrainer} to train sentences and people names from
 * a KVIrc log file. It also supports color coded logs, but will remove any colors so as not to
 * cause the chat bot to color his messages as well.
 * 
 * @author Marc Müller
 */
public class KVIrcTrainer extends FileTrainer
{
  /** A regex for what is considered a non-colored line. */
  private static final String REGULAR_LINE = "^.*\\[.*\\] <.*>.*$";
  
  /**
   * Small anonymous factory used to create these trainers.
   */
  public static final TrainerFactory FACTORY = new TrainerFactory()
  {
    @Override
    public Trainer createTrainerFor(File file)
    {
      return new KVIrcTrainer(file);
    }
  };
  
  /**
   * Create a new trainer that learns from the given file.
   * 
   * @param newFile
   */
  public KVIrcTrainer(File newFile)
  {
    super(newFile);
  }
  
  @Override
  public void handleLine(FreamonHal hal, String message)
  {
    if (!message.matches(REGULAR_LINE))
    {
      message = message.replaceAll("\r!\\w*", "");
      message = message.replace("\02", "");
      message = message.replace("\r", "");
    }
    
    if (message.matches(REGULAR_LINE))
    {
      int start = message.indexOf("<");
      if (start == -1)
      {
        return;
      }
      
      message = message.substring(start);
      if (message.startsWith("<"))
      {
        message = message.substring(1);
        if (message.startsWith("@") || message.startsWith("+"))
        {
          message = message.substring(1);
        }
        int nameEnd = message.indexOf(">");
        String name = message.substring(0, nameEnd);
        message = message.substring(nameEnd+2);
        
        hal.addPeopleName(name);
        hal.addSentence(message);
      }
    }
  }
}

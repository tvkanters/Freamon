package de.blanksteg.freamon.hal;

import java.io.File;

/**
 * The IRSSITrainer behaves much like the file trainer with the difference that it reads lines
 * from IRSSI-formatted log files and teaches the respective {@link FreamonHal} both the person
 * talking and its sentence.
 * 
 * @author Marc Müller
 */
public class IRSSITrainer extends FileTrainer
{
  /**
   * Small anonymous factory used to create these trainers.
   */
  public static final TrainerFactory FACTORY = new TrainerFactory()
  {
    @Override
    public Trainer createTrainerFor(File file)
    {
      return new IRSSITrainer(file);
    }
  };
  
  /**
   * Creates a new instance training from the given IRSSI log file.
   * 
   * @param newSource The IRSSI log.
   */
  public IRSSITrainer(File newSource)
  {
    super(newSource);
  }
  
  @Override
  public void handleLine(FreamonHal hal, String message)
  {
    int start = message.indexOf("<");
    if (start == -1)
    {
      return;
    }
    
    message = message.substring(start);
    if (message.startsWith("<"))
    {
      message = message.substring(2);
      int nameEnd = message.indexOf(">");
      String name = message.substring(0, nameEnd);
      message = message.substring(nameEnd+2);
      
      hal.addPeopleName(name);
      hal.addSentence(message);
    }
  }
}

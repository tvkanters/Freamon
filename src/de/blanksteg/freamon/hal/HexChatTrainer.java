package de.blanksteg.freamon.hal;

import java.io.File;

/**
 * The HexChatTrainer behaves much like the file trainer with the difference that it reads lines
 * from HexChat-formatted log files and teaches the respective {@link FreamonHal} both the person
 * talking and its sentence.
 * 
 * @author Marc Müller
 */
public class HexChatTrainer extends FileTrainer
{
  /**
   * Small anonymous factory used to create these trainers.
   */
  public static final TrainerFactory FACTORY = new TrainerFactory()
  {
    @Override
    public Trainer createTrainerFor(File file)
    {
      return new HexChatTrainer(file);
    }
  };
  
  /**
   * Creates a new instance training from the given HexChat log file.
   * 
   * @param newFile The HexChat log file.
   */
  public HexChatTrainer(File newFile)
  {
    super(newFile);
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
      message = message.substring(1);
      int nameEnd = message.indexOf(">");
      String name = message.substring(0, nameEnd);
      message = message.substring(nameEnd+2);
      
      hal.addPeopleName(name);
      hal.addSentence(message);
    }
  }
}

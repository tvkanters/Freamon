package de.blanksteg.freamon.hal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.log4j.Logger;

/**
 * A FileTrainer implements the {@link Trainer} interface to create a trainer that reads a file
 * line by line and teaches a given {@link FreamonHal} instance that line. This suffices for most
 * text files, but if a line needs special treatment the {@link FileTrainer#handleLine(FreamonHal, String)}
 * method can be overwritten by extending classes.
 * 
 * This class is optimized for large files. Meaning it doesn't use the naive implementation of file
 * reading that causes GarbageCollector execution to skyrocket.
 * 
 * @author Marc Müller
 */
public class FileTrainer implements Trainer
{
  /** The log4j logger to generate output for. */
  private static final Logger l = Logger.getLogger("de.blanksteg.freamon.hal");
  
  /**
   * Internal anonymous factory used to create instances of the FileTrainer.
   */
  public static final TrainerFactory FACTORY = new TrainerFactory()
  {
    @Override
    public Trainer createTrainerFor(File file)
    {
      return new FileTrainer(file);
    }
  };
  
  /** Char buffer size. */
  private static final int BUFFER_SIZE = 512;
  
  /** The base file to train from. */
  private final File file;
  
  /**
   * Creates a new FileTrainer training from the given file.
   * 
   * @param newFile The training file.
   */
  public FileTrainer(File newFile)
  {
    this.file = newFile;
  }

  @Override
  public void trainAll(FreamonHal hal)
  {
    /**
     * The intuitive and most common approach of reading a file in Java as used in
     * {@link FileIO#readClassPathFile} tends to allocate too many single-use string instances since
     * each line of the file requires one string. Said instances accumulate too quickly for the
     * garbage collector so he can't keep up with the obsolete lines being stored in memory all the
     * time. This causes the garbage collector to spend large amounts of time gathering unreachable
     * references and clearing their memory, which makes the Java virtual machine abort the
     * execution. Due to this, we have to use the alternative method of buffering char values in
     * a one-time allocated array which does not suffer from the issue previously illustrated since
     * primitive values are allocated on the stack.
     */
    
    l.info("Starting training session for the file: " + this.file);
    FileReader in = null;
    try
    {
      in = new FileReader(this.file);
    }
    catch (FileNotFoundException e1)
    {
      l.error("Error while creating file reader during training.", e1);
      return;
    }
    
    BufferedReader  bin     = new BufferedReader(in);
    char[]          buffer  = new char[BUFFER_SIZE];
    StringBuilder   sb      = new StringBuilder();
    
    try
    {
      while (bin.ready())
      {
        int read = bin.read(buffer);
        sb.append(buffer, 0, read);
        
        int newline = sb.indexOf("\n");
        if (newline != -1)
        {
          String line = sb.substring(0, newline);
          this.handleLine(hal, line);
          sb.delete(0, newline + 1);
        }
      }
    }
    catch (Exception e)
    {
      l.error("Error while training file: " + this.file + ".", e);
    }
    finally
    {
      try
      {
        in.close();
        bin.close();
      }
      catch (IOException e)
      {
        l.error("Oh boy. Exception while closing readers.", e);
      }
    } 
  }
  
  /**
   * This method is called to teach the given {@link FreamonHal} instance the given line. This
   * implementation just calls {@link FreamonHal#addSentence(String)}, but extending classes are
   * welcome to overwrite this for special behavior.
   * 
   * @param hal The target instance to train.
   * @param line The line to train.
   */
  public void handleLine(FreamonHal hal, String line)
  {
    hal.addSentence(line);
  }
}

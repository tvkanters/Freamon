package de.blanksteg.freamon.hal;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import de.blanksteg.freamon.FileIO;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

/**
 * The ContinuousTextTrainer reads some file and splits its continuous text using the {@link
 * StanfordCoreNLP} class. Each sentence is then taught to some {@link FreamonHal} instance.
 * 
 * @author Marc Müller
 */
public class ContinuousTextTrainer implements Trainer
{
  /** The log4j logger to log to. */
  private static final Logger l = Logger.getLogger("de.blanksteg.freamon.hal");
  /** A global StanfordCoreNLP everyone of these trainers uses. */
  private static final StanfordCoreNLP NLP;
  
  static
  {
    Properties props = new Properties();
    props.put("annotators", "tokenize, ssplit");
    
    NLP = new StanfordCoreNLP(props);
  }
  
  /**
   * Small anonymous factory used to create these trainers.
   */
  public static final TrainerFactory FACTORY = new TrainerFactory()
  {
    @Override
    public Trainer createTrainerFor(File file)
    {
      return new ContinuousTextTrainer(file);
    }
  };
  
  /** The source file to read from. */
  private final File source;
  
  /**
   * Create a new trainer reading from the given path.
   * 
   * @param newSource The source path.
   * @throws IllegalArgumentException If the path is null.
   */
  public ContinuousTextTrainer(File newSource)
  {
    if (newSource == null)
    {
      throw new IllegalArgumentException("The given source path is null.");
    }
    
    this.source = newSource;
  }

  @Override
  public void trainAll(FreamonHal hal)
  {
    l.info("Starting trainging of continuous text file: " + this.source);
    try
    {
      String      content   = FileIO.readFile(this.source).replace("\n", " ");
      Annotation  document  = new Annotation(content);
      NLP.annotate(document);
      l.debug("Loaded and annotated the document from " + this.source + ".");
      
      List<CoreMap> sentenceMaps = document.get(CoreAnnotations.SentencesAnnotation.class);
      if (sentenceMaps != null)
      {
        for (CoreMap sentenceMap : sentenceMaps)
        {
          String line = sentenceMap.toString();
          l.info("Teaching the sentence: " + line);
          hal.addSentence(line);
        }
      }
      l.info("Done with the training of the file " + this.source + ".");
    }
    catch (IOException e)
    {
      l.error("Error while reading source file " + this.source + ".", e);
    }
  }
}

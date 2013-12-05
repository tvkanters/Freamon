package de.blanksteg.freamon.nlp;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * The PhraseAnalyzer class utilizes the {@link GrammaticalStructure} from Stanford's NLP Parser to
 * determine words used in a sentence. Its main use is to extract relevant parts from a sentence
 * for increased response accuracy.
 * 
 * @author Marc Müller
 */
public class PhraseAnalyzer
{
  /** The tokenizer factory used across the lifetime of the object. */
  private final TokenizerFactory<CoreLabel> tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
  
  /** The parser used to analyze text with. This needs special deserialization. */
  private final LexicalizedParser lp;
  /** A grammatical structure factory used to generate GrammaticalStructure's when extracting words. */
  private final GrammaticalStructureFactory gsf;
  
  /**
   * Create a new instance that initializes the {@link LexicalizedParser} with the given model file.
   * 
   * Check out the documentation for {@link LexicalizedParser} to get more info on the file needed
   * by this class.
   * 
   * @param model The model file to use.
   */
  public PhraseAnalyzer(String model)
  {
    this.lp = LexicalizedParser.loadModel(model);
    
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    this.gsf = tlp.grammaticalStructureFactory();
  }
  
  /**
   * Extract a sorted list of words from the given phrase. The words are split up according to the
   * {@link GrammaticalStructure} used and then sorted according to their priority in {@link Word#COMPARATOR}.
   * 
   * @param phrase
   * @return
   */
  public List<Word> analyzePhrase(String phrase)
  {
    if (phrase == null)
    {
      throw new IllegalArgumentException("The given phrase was null.");
    }
    
    List<CoreLabel> rawWords  = this.tokenizerFactory.getTokenizer(new StringReader(phrase)).tokenize();
    Tree            parsed    = lp.apply(rawWords);
    
    GrammaticalStructure  gs  = gsf.newGrammaticalStructure(parsed);
    List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
    
    ArrayList<Word> ret = new ArrayList<Word>(tdl.size());
    for (TypedDependency t : tdl)
    {
      GrammaticalRelation relation  = t.reln();
      WordType            type      = WordType.typeFromRelation(relation);
      
      String content = t.dep().nodeString();
      
      Word word = new Word(content, type);
      ret.add(word);
    }
    Collections.sort(ret, Word.COMPARATOR);
    Collections.reverse(ret);
    
    return ret;
  }
}

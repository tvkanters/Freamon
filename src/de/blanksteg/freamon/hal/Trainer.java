package de.blanksteg.freamon.hal;

/**
 * A Trainer is used to teach a {@link FreamonHal} instance a set of sentences.
 * 
 * @author Marc Müller
 */
public interface Trainer
{
  /**
   * Teach the given {@link FreamonHal} this trainer's sentences.
   * 
   * @param hal The target instance.
   */
  public void trainAll(FreamonHal hal);
}

package de.blanksteg.freamon.irc;

import org.pircbotx.hooks.events.JoinEvent;

import de.blanksteg.freamon.Configuration;

/**
 * This implementation of the GreetingsGenerator relies on another {@link GreetingsGenerator} to
 * generate its greetings. However, it does modulate the behavior to seem more human by adding
 * a random delay before returning a message, having a cooldown period in which no greeting is sent
 * and deciding whether or not to greet based on a roll with {@link Configuration#rollGreeting()}.
 * 
 * @author Marc Müller 
 */
public class GenericAnthroGreetingsGenerator implements GreetingsGenerator
{
  /** The generator to get greetings from. */
  private final GreetingsGenerator base;
  /** The system time we set the last greeting. */
  private long lastGreeting;
  
  /**
   * Create a new instance using the given generator.
   * 
   * @param newBase The base generator.
   */
  public GenericAnthroGreetingsGenerator(GreetingsGenerator newBase)
  {
    this.base = newBase;
  }

  @Override
  public boolean shouldGreet(JoinEvent<Network> event)
  {
    return this.hasCooledDown() && Configuration.rollGreeting() && this.base.shouldGreet(event);
  }

  @Override
  public String generateGreeting(JoinEvent<Network> event)
  {
    Configuration.simulateDelay();
    this.lastGreeting = System.currentTimeMillis();
    return this.base.generateGreeting(event);
  }
  
  /**
   * Check whether enough time has passed since the last greeting was sent.
   * 
   * @return true if we've been quiet for more than {@link Configuration#getCooldown()}.
   */
  private boolean hasCooledDown()
  {
    return (System.currentTimeMillis() - this.lastGreeting) > Configuration.getCooldown() * 1000;
  }
}

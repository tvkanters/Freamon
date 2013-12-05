package de.blanksteg.freamon.irc;

import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;

import de.blanksteg.freamon.hal.FreamonHal;

/**
 * This class uses an underlying {@link FreamonHal} instance to generate responses to both private
 * and public messages using {@link FreamonHal#generateRelevantPrivateMessage(PrivateMessageEvent)}
 * and {@link FreamonHal#generateRelevantPublicMessage(MessageEvent)} respectively.
 * 
 * A notable feature is that the instance used for responses is changeable throughout the lifetime
 * of this object, meaning one can switch {@link FreamonHal} instances whilst the application is
 * running. 
 * 
 * @author Marc Müller
 */
public class FreamonHalResponseGenerator implements ResponseGenerator
{
  /** The FreamonHal instance to get responses from. */
  private FreamonHal hal;
  
  /**
   * Create a new instance using the given {@link FreamonHal} as its base.
   * 
   * @param newHal The base Freamon.
   */
  public FreamonHalResponseGenerator(FreamonHal newHal)
  {
    this.hal = newHal;
  }
  
  public synchronized FreamonHal getFreamonHal()
  {
    return this.hal;
  }
  
  public synchronized void setFreamonHal(FreamonHal newHal)
  {
    this.hal = newHal;
  }

  @Override
  public synchronized String respondPublic(MessageEvent<Network> event)
  {
    if (event.getMessage().startsWith("!"))
    {
      return null;
    }
    
    return this.hal.generateRelevantPublicMessage(event).replaceAll("(?i)" + event.getBot().getNick(), event.getUser().getNick());
  }

  @Override
  public synchronized String respondPrivate(PrivateMessageEvent<Network> event)
  {
    return this.hal.generateRelevantPrivateMessage(event).replaceAll("(?i)" + event.getBot().getNick(), event.getUser().getNick());
  }
}

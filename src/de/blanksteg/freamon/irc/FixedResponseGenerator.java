package de.blanksteg.freamon.irc;

import java.util.HashMap;
import java.util.Map;

import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;

import de.blanksteg.freamon.Configuration;

/**
 * The FixedResponseGenerator responds only to specific triggers with specific reactions. If a message
 * matches a string <b>completely</b> it will be considered a trigger and the associated string will
 * be posted as the response. Too mediate response frequency, the decision to reply also relies on
 * an additional call to {@link Configuration#rollChance(int)} with {@link Configuration#FIXED_CHANCE}
 * as the chance. 
 * 
 * @author Marc Müller
 */
public class FixedResponseGenerator implements ResponseGenerator
{
  /** The mapping of triggers to their supposed responses. */
  private final Map<String, String> responses = new HashMap<String, String>();
  
  /**
   * Remember a trigger and its response.
   * 
   * @param cause The trigger to react to
   * @param response The response to post
   */
  public void putResponse(String cause, String response)
  {
    this.responses.put(cause, response);
  }
  
  /**
   * Remove the given trigger and its response.
   * 
   * @param cause The trigger to no longer react to.
   */
  public void deleteResponse(String cause)
  {
    this.responses.remove(cause);
  }

  @Override
  public String respondPublic(MessageEvent<Network> event)
  {
    String message  = event.getMessage();
    
    if (Configuration.rollChance(Configuration.FIXED_CHANCE) && this.responses.containsKey(message))
    {
      return this.responses.get(message);
    }
    else
    {
      return null;
    }
  }

  @Override
  public String respondPrivate(PrivateMessageEvent<Network> event)
  {
    String message  = event.getMessage();
    
    if (Configuration.rollChance(Configuration.FIXED_CHANCE) && this.responses.containsKey(message))
    {
      return this.responses.get(message);
    }
    else
    {
      return null;
    }
  }
}

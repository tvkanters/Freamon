package de.blanksteg.freamon.irc;

import java.util.LinkedList;

import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;

/**
 * The ComplexResponseGenerator is a decorative response generator that relies on other generators
 * to create the actual responses. However, this particular implementation of a response generator
 * will try out a sequence of possible other generators and return response of the first one that
 * actually generates a reply.
 * 
 * Generators are tried in the order they are added to this generator.
 * 
 * @author Marc Müller
 */
public class ComplexResponseGenerator implements ResponseGenerator
{
  /** The other responders to attempt a response with. */
  private final LinkedList<ResponseGenerator> responders = new LinkedList<ResponseGenerator>();
  
  /**
   * Register a {@link ResponseGenerator} as a possible responder for this instance.
   * 
   * @param responder The responder to try.
   */
  public void addResponder(ResponseGenerator responder)
  {
    this.responders.offerLast(responder);
  }
  
  /**
   * Unregister a {@link ResponseGenerator} as a possible responder for this instance.
   * 
   * @param responder The responder to no longer try.
   */
  public void removeResponder(ResponseGenerator responder)
  {
    this.responders.remove(responder);
  }

  @Override
  public String respondPublic(MessageEvent<Network> event)
  {
    for (ResponseGenerator responder : this.responders)
    {
      String response = responder.respondPublic(event);
      if (response != null)
      {
        return response;
      }
    }
    
    return null;
  }

  @Override
  public String respondPrivate(PrivateMessageEvent<Network> event)
  {
    for (ResponseGenerator responder : this.responders)
    {
      String response = responder.respondPrivate(event);
      if (response != null)
      {
        return response;
      }
    }
    
    return null;
  }
}

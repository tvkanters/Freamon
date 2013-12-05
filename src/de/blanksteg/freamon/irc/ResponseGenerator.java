package de.blanksteg.freamon.irc;

import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;

/**
 * The ResponseGenerator interface is used to defer generation of responses to public and private
 * messages to other implementing classes. It is mainly used by the {@link IRCClient} to handle its
 * incoming messages.
 * 
 * Note that classes implementing this interface are supposed to return their response, and not
 * send them.
 * 
 * @author Marc Müller
 */
public interface ResponseGenerator
{
  /**
   * Attempt to generate a response to a publicly received message that caused the {@link MessageEvent}.
   * 
   * @param event The event to react to.
   * @return The generated message, null if no response could be determined.
   */
  public String respondPublic(MessageEvent<Network> event);
  
  /**
   * Attempt to generate a response to a privately received message that caused the {@link
   * PrivateMessageEvent}.
   * 
   * @param event The event to react to.
   * @return The generated response, null if no response could be determined.
   */
  public String respondPrivate(PrivateMessageEvent<Network> event);
}

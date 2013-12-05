package de.blanksteg.freamon.irc;

import org.pircbotx.hooks.events.JoinEvent;

/**
 * The GreetingsGenerator interface is used to defer greetings generation of the {@link IRCClient}
 * to various other classes. 
 * 
 * @author Marc Müller
 */
public interface GreetingsGenerator
{
  /**
   * Checks whether or not this generator wants to do a greeting for the given event.
   * 
   * @param event The event
   * @return True if a greeting should be generated.
   */
  public boolean shouldGreet(JoinEvent<Network> event);
  
  /**
   * Generates a greeting appropriate for the given event.
   * 
   * @param event The event.
   * @return The generated greeting.
   */
  public String generateGreeting(JoinEvent<Network> event);
}

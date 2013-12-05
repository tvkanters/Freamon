package de.blanksteg.freamon.irc;

import java.util.ArrayList;
import java.util.List;

import org.pircbotx.Channel;
import org.pircbotx.hooks.events.JoinEvent;

import de.blanksteg.freamon.Configuration;

/**
 * The FixedGreetingsGenerator will generate greetings based on a fixed list of strings for either
 * greetings to other people joining or the bot itself joining. These greetings can use
 * {@link Configuration#USER_MASK} and {@link Configuration#CHANCE_MASK} in place of the user or
 * channel the event occurred in.
 * 
 * Possible greetings can be added after the object's creation. "Fixed" refers to each greeting
 * staying the same throughout the lifetime of the object.
 * 
 * @author Marc Müller
 */
public class FixedGreetingsGenerator implements GreetingsGenerator
{
  /** The list of possible greetings to use after joining a channel. */
  private final List<String>  joinMessages  = new ArrayList<String>();
  /** The list of possible messages to greet a user joining a channel with. */
  private final List<String>  greetMessages = new ArrayList<String>();
  
  /**
   * Add a possible message for when the bot enters a channel.
   * 
   * @param message The message to add
   */
  public void addJoinMessage(String message)
  {
    this.joinMessages.add(message);
  }
  
  /**
   * Add a possible message for when another user enters a channel.
   * 
   * @param message The message to add
   */
  public void addGreetMessage(String message)
  {
    this.greetMessages.add(message);
  }

  @Override
  public boolean shouldGreet(JoinEvent<Network> event)
  {
    Network target = event.getBot();
    Channel channel = event.getChannel();
    
    return target.isActiveChannel(channel);
  }

  @Override
  public String generateGreeting(JoinEvent<Network> event)
  {
    Network target = event.getBot();
    String message = null;
    if (target.getNick().equals(event.getUser().getNick()))
    {
      message = this.select(this.joinMessages);
    }
    else
    {
      message = this.select(this.greetMessages);
    }
    
    message = message.replace(Configuration.CHANNEL_MASK, event.getChannel().getName());
    message = message.replace(Configuration.USER_MASK, event.getUser().getNick());
    return message;
  }
  
  /**
   * Randomly select on element from the given list.
   * 
   * @param choices The list of possible selections.
   * @return The selected element.
   */
  private String select(List<String> choices)
  {
    return choices.get(Configuration.RNG.nextInt(choices.size()));
  }
}

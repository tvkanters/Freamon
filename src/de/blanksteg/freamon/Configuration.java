package de.blanksteg.freamon;

import java.util.Random;

import net.sf.json.JSONObject;

import org.apache.commons.cli.CommandLine;
import org.apache.log4j.Logger;

import de.blanksteg.freamon.nlp.PhraseAnalyzer;

/**
 * The configuration class is meant to be a globally accessible storage of values affecting various
 * aspects of the Freamon application. Additionally, small helper methods that aren't significant
 * enough to warrant a class of their own are implemented in here. Included in this class are:
 * <ul>
 *   <li>Current public, pinged greeting response chances.</li>
 *   <li>Current cooldown between messages.</li>
 *   <li>Current range of random delay before responding.</li>
 *   <li>The administration password for remote control.</li>
 *   <li>IRC properties such as client name, user name and real name.</li>
 *   <li>Methods for weighted random decision.</li>
 *   <li>A method for artificial random delay.</li>
 *   <li>A method for configuration based on command line arguments.</li>
 *   <li>Global random number generator.</li>
 * </ul>
 * 
 * Any setter-method() that is publicly visible can be assumed to be safe to use throughout the
 * execution of the application. The {@link Configuration#configure(CommandLine)} method should only
 * be called during the initialization of the program.
 * 
 * This class checks its sanity only inside the {@link Configuration#configure(CommandLine) method,
 * the values passed to the various setter functions need to be checked for consistency by the
 * respective user.
 * 
 * @author Marc Müller
 */
public abstract class Configuration
{
  /** The log4j logger we use to give status output. */
  private static final Logger l   = Logger.getLogger("de.blanksteg.freamon.main");
  /** The random number generator free to use by everybody who needs it. */
  public  static final Random RNG = new Random();
  
  /** Path to the model we use to initialize Stanford NLP Parser's model in {@link PhraseAnalyzer}. */
  public static final String SNLP_MODEL     = "NLPModels/englishPCFG.ser.gz";
  /** Path to a line-separated list of possible greetings. */
  public static final String GREETINGS_PATH = "greetings.list";
  /** Path to a line-separated list of possible join messages. */
  public static final String JOIN_PATH      = "join.list";
  
  /** Path to a file containing a JSON string defining triggered responses. */
  public static final String FIXED_PATH     = "fixed.json";
  
  /** The string to be replaced with the respective user in a greeting. */
  public static final String USER_MASK      = "%user%";
  /** The string to be replaced with the respective channel in a greeting. */
  public static final String CHANNEL_MASK   = "%channel%";
  
  /** A regex to match channel names. */
  public static final String CHANNEL_MATCH  = "#[a-zA-Z_\\-]*";
  
  /** Default IRC port to use when none other is specified. */
  public static final int DEFAULT_PORT      = 6667;
  
  /** Minimum cooldown phase in seconds. */
  public static final int MIN_COOLDOWN      = 1;
  /** Maximum cooldown phase in seconds. */
  public static final int MAX_COOLDOWN      = Integer.MAX_VALUE;
  
  /** Lower bound for the minimum response delay. */
  public static final int MIN_MIN_DELAY     = 100;
  /** Upper bound for the minimum response delay. */
  public static final int MAX_MIN_DELAY     = 60000;
  
  /** Lower bound for the maximum response delay. */
  public static final int MIN_MAX_DELAY     = 200;
  /** Upper bound for the maximum response delay. */
  public static final int MAX_MAX_DELAY     = Integer.MAX_VALUE;
  
  /** Just the upper bound on what can be considered a percentage. */
  public static final int CHANCE_MAX        = 100;
  
  /** The chance of actually using a fixed reply. */
  public static final int FIXED_CHANCE      = 30;
  
  /** Amount of times to retry response generation if JMegaHal returns null. */
  public static final int JMEGAHAL_ATTEMPTS = 32;
  
  /** The default password to use. */
  public static final String DEFAULT_PASS   = null;
  
  /** Current minimum response delay in ms. */
  private static int    minDelay            = 2000;
  /** Current maximum response delay in ms. */
  private static int    maxDelay            = 4000;
  /** Current cooldown period in seconds. */
  private static long   cooldown            = 5;
  /** Current tire period in seconds. */
  private static long   tirePeriod          = 15 * 60;
  
  /** Current public response chance in percent. */
  private static int    pubResponseChance   = 10;
  /** Current pinged response chance in percent. */
  private static int    pingResponseChance  = 100;
  /** Current greeting chance in percent. */
  private static int    greetChance         = 80;
  
  /** Whether or not to learn nicknames. */
  private static boolean learnNames = true;
  
  /** Current administration password. */
  private static String password            = "temehiisfaggot";
  /** Current IRC client user name. */
  private static String userName            = "BotSteg";
  /** Current IRC client real name. */
  private static String realName            = "BotSteg";
  /** Current IRC client name. */
  private static String clientName          = "BotSteg";
  
  /**
   * This method will traverse possible command line arguments and initialize the respective values
   * as specified in the given {@link CommandLine} instance. Values are checked for their sanity
   * and upon inconsistent values configuration is aborted and the failure indicated in the return
   * value of the method. If no error is encountered, true is returned.
   * 
   * For more information on configuration options, consult the manual.
   * 
   * @param base The command line arguments to configure from.
   * @return Whether configuration succeeded without errors.
   */
  protected static boolean configure(CommandLine base)
  {
    l.debug("Starting configuration from command line.");
    
    if (base.hasOption("cd"))
    {
      try
      {
        long cooldownOpt = Long.parseLong(base.getOptionValue("cd"));
        cooldown = cooldownOpt;
        l.info("Set cooldown to " + cooldown);
      }
      catch (Exception e)
      {
        System.err.println("Malformed cooldown value: " + base.getOptionValue("cd"));
        return false;
      }
    }
    
    if (base.hasOption("tp"))
    {
      try
      {
        long tirePeriodOpt = Long.parseLong(base.getOptionValue("tp"));
        tirePeriod = tirePeriodOpt;
        l.info("Set tire period to " + tirePeriod);
      }
      catch (Exception e)
      {
        System.err.println("Malformed tire period value: " + base.getOptionValue("tp"));
        return false;
      }
    }
    
    if (base.hasOption("pc"))
    {
      try
      {
        int pubChanceOpt = Integer.parseInt(base.getOptionValue("pc"));
        pubResponseChance = pubChanceOpt;
        if (pubResponseChance < 0 || pubResponseChance > CHANCE_MAX)
        {
          System.err.println("Public response chance out of range: " + pubResponseChance);
          return false;
        }
        
        l.info("Set public response chance to " + pubResponseChance);
      }
      catch (Exception e)
      {
        System.err.println("Malformed public chance value: " + base.getOptionValue("pc"));
        return false;
      }
    }
    
    if (base.hasOption("dc"))
    {
      try
      {
        int pubChanceOpt = Integer.parseInt(base.getOptionValue("dc"));
        pingResponseChance = pubChanceOpt;
        if (pingResponseChance < 0 || pingResponseChance > CHANCE_MAX)
        {
          System.err.println("Pinged response chance out of range: " + pingResponseChance);
          return false;
        }
        
        l.info("Set pinged response chance to " + pingResponseChance);
      }
      catch (Exception e)
      {
        System.err.println("Malformed pinged chance value: " + base.getOptionValue("gc"));
        return false;
      }
    }
    
    if (base.hasOption("gc"))
    {
      try
      {
        int pubChanceOpt = Integer.parseInt(base.getOptionValue("gc"));
        greetChance = pubChanceOpt;
        if (greetChance < 0 || greetChance > CHANCE_MAX)
        {
          System.err.println("Greet chance out of range: " + greetChance);
          return false;
        }
        
        l.info("Set greet chance to " + greetChance);
      }
      catch (Exception e)
      {
        System.err.println("Malformed greet chance value: " + base.getOptionValue("gc"));
        return false;
      }
    }
    
    if (base.hasOption("mid"))
    {
      try
      {
        int minDelayOpt = Integer.parseInt(base.getOptionValue("mid"));
        minDelay = minDelayOpt;
        if (minDelay < MIN_MIN_DELAY || minDelay > MAX_MIN_DELAY)
        {
          System.err.println("Min delay out of range: " + minDelay);
          return false;
        }
        
        l.info("Set minimum delay to " + minDelay);
      }
      catch (Exception e)
      {
        System.err.println("Malformed min delay value: " + base.getOptionValue("mid"));
        return false;
      }
    }
    
    if (base.hasOption("mad"))
    {
      try
      {
        int minDelayOpt = Integer.parseInt(base.getOptionValue("mad"));
        maxDelay = minDelayOpt;
        if (maxDelay < MIN_MAX_DELAY || maxDelay > MAX_MAX_DELAY)
        {
          System.err.println("Min delay out of range: " + minDelay);
          return false;
        }
        
        if (maxDelay < minDelay)
        {
          System.err.println("Max delay is greater than min delay.");
          return false;
        }
        
        l.info("Set maximum delay to " + minDelay);
      }
      catch (Exception e)
      {
        System.err.println("Malformed min delay value: " + base.getOptionValue("mad"));
        return false;
      }
    }
    
    if (base.hasOption("p"))
    {
      password = base.getOptionValue("p");
      l.info("Set the password to " + password);
    }
    
    if (base.hasOption("c"))
    {
      clientName = base.getOptionValue("c");
      l.info("Set the client name to " + clientName);
    }
    
    if (base.hasOption("r"))
    {
      realName = base.getOptionValue("r");
      l.info("Set the real name to " + realName);
    }
    
    if (base.hasOption("u"))
    {
      userName = base.getOptionValue("u");
      l.info("Set the user name to " + userName);
    }
    
    return true;
  }
  
  protected static boolean configure(JSONObject base)
  {
    l.debug("Starting configuration from config file.");
    
    if (base.containsKey("cooldown"))
    {
      try
      {
        long cooldownOpt = base.getLong("cooldown");
        cooldown = cooldownOpt;
        l.info("Set cooldown to " + cooldown);
      }
      catch (Exception e)
      {
        System.err.println("Malformed cooldown value: " + base.get("cd"));
        return false;
      }
    }
    
    if (base.containsKey("tireperiod"))
    {
      try
      {
        long tirePeriodOpt = base.getLong("tireperiod");
        tirePeriod = tirePeriodOpt;
        l.info("Set tire period to " + tirePeriod);
      }
      catch (Exception e)
      {
        System.err.println("Malformed tire period value: " + base.get("tp"));
        return false;
      }
    }
    
    if (base.containsKey("publicchance"))
    {
      try
      {
        int pubChanceOpt = base.getInt("publicchance");
        pubResponseChance = pubChanceOpt;
        if (pubResponseChance < 0 || pubResponseChance > CHANCE_MAX)
        {
          System.err.println("Public response chance out of range: " + pubResponseChance);
          return false;
        }
        
        l.info("Set public response chance to " + pubResponseChance);
      }
      catch (Exception e)
      {
        System.err.println("Malformed public chance value: " + base.get("pc"));
        return false;
      }
    }
    
    if (base.containsKey("pingchance"))
    {
      try
      {
        int pubChanceOpt = base.getInt("pingchance");
        pingResponseChance = pubChanceOpt;
        if (pingResponseChance < 0 || pingResponseChance > CHANCE_MAX)
        {
          System.err.println("Pinged response chance out of range: " + pingResponseChance);
          return false;
        }
        
        l.info("Set pinged response chance to " + pingResponseChance);
      }
      catch (Exception e)
      {
        System.err.println("Malformed pinged chance value: " + base.get("gc"));
        return false;
      }
    }
    
    if (base.containsKey("greetchance"))
    {
      try
      {
        int pubChanceOpt = base.getInt("greetchance");
        greetChance = pubChanceOpt;
        if (greetChance < 0 || greetChance > CHANCE_MAX)
        {
          System.err.println("Greet chance out of range: " + greetChance);
          return false;
        }
        
        l.info("Set greet chance to " + greetChance);
      }
      catch (Exception e)
      {
        System.err.println("Malformed greet chance value: " + base.get("gc"));
        return false;
      }
    }
    
    if (base.containsKey("mindelay"))
    {
      try
      {
        int minDelayOpt = base.getInt("mindelay");
        minDelay = minDelayOpt;
        if (minDelay < MIN_MIN_DELAY || minDelay > MAX_MIN_DELAY)
        {
          System.err.println("Min delay out of range: " + minDelay);
          return false;
        }
        
        l.info("Set minimum delay to " + minDelay);
      }
      catch (Exception e)
      {
        System.err.println("Malformed min delay value: " + base.get("mid"));
        return false;
      }
    }
    
    if (base.containsKey("maxdelay"))
    {
      try
      {
        int minDelayOpt = base.getInt("maxdelay");
        maxDelay = minDelayOpt;
        if (maxDelay < MIN_MAX_DELAY || maxDelay > MAX_MAX_DELAY)
        {
          System.err.println("Min delay out of range: " + minDelay);
          return false;
        }
        
        if (maxDelay < minDelay)
        {
          System.err.println("Max delay is greater than min delay.");
          return false;
        }
        
        l.info("Set maximum delay to " + minDelay);
      }
      catch (Exception e)
      {
        System.err.println("Malformed min delay value: " + base.get("mad"));
        return false;
      }
    }
    
    if (base.containsKey("password"))
    {
      password = base.getString("password");
      l.info("Set the password to " + password);
    }
    
    if (base.containsKey("client"))
    {
      clientName = base.getString("client");
      l.info("Set the client name to " + clientName);
    }
    
    if (base.containsKey("realname"))
    {
      realName = base.getString("realname");
      l.info("Set the real name to " + realName);
    }
    
    if (base.containsKey("username"))
    {
      userName = base.getString("username");
      l.info("Set the user name to " + userName);
    }
    
    return true;
  }
  
  /**
   * This method will sleep for a random amount of time between the current values of
   * {@link Configuration#minDelay} and {@link Configuration#maxDelay}.
   */
  public static void simulateDelay()
  {
    try
    {
      Thread.sleep(RNG.nextInt(maxDelay) + minDelay);
    }
    catch (Exception e)
    {
      l.error("Error while simulating a delay.", e);
    }
  }
  
  /**
   * Randomly decide true chance% of the time.
   * 
   * @param chance The percentage with which to return true.
   * @return chance% of the time true, (100 - chance)% of the time false. 
   */
  public static boolean rollChance(int chance)
  {
    if (chance == CHANCE_MAX)
    {
      return true;
    }
    
    if (chance == 0)
    {
      return false;
    }
    
    return chance > RNG.nextInt(CHANCE_MAX);
  }
  
  /**
   * Randomly decide if a greeting is due based on the current value of {@link Configuration#greetChance}.
   * 
   * @return true if a greeting is randomly considered due.
   */
  public static boolean rollGreeting()
  {
    return rollChance(greetChance);
  }
  
  /**
   * Randomly decide if a public response is due based on the current value of {@link Configuration#pubResponseChance}.
   * 
   * @return true if a public response is randomly considered due.
   */
  public static boolean rollPublicResponse()
  {
    return rollChance(pubResponseChance);
  }
  
  /**
   * Randomly decide if a pinged response is due based on the current value of {@link Configuration#pingResponseChance}.
   * 
   * @return true if a pinged response is randomly considered due.
   */
  public static boolean rollPingResponse()
  {
    return rollChance(pingResponseChance);
  }
  
  /**
   * Below are trivial getters and setters.
   */
  
  public static String getUserName()
  {
    return userName;
  }

  public static void setUserName(String userName)
  {
    Configuration.userName = userName;
  }

  public static String getRealName()
  {
    return realName;
  }

  public static void setRealName(String realName)
  {
    Configuration.realName = realName;
  }

  public static String getClientName()
  {
    return clientName;
  }

  public static void setClientName(String clientName)
  {
    Configuration.clientName = clientName;
  }

  public static String getPassword()
  {
    return password;
  }

  public static void setPassword(String admin)
  {
    Configuration.password = admin;
  }
  
  public static long getMinDelay()
  {
    return minDelay;
  }
  
  public static void setMinDelay(int minDelay)
  {
    Configuration.minDelay = minDelay;
  }
  
  public static long getMaxDelay()
  {
    return maxDelay;
  }
  
  public static void setMaxDelay(int maxDelay)
  {
    Configuration.maxDelay = maxDelay;
  }
  
  public static long getCooldown()
  {
    return cooldown;
  }
  
  public static void setCooldown(long cooldown)
  {
    Configuration.cooldown = cooldown;
  }
  
  public static long getTirePeriod()
  {
    return tirePeriod;
  }
  
  public static void setTirePeriod(long tirePeriod)
  {
    Configuration.tirePeriod = tirePeriod;
  }
  
  public static int getPubResponseChance()
  {
    return pubResponseChance;
  }
  
  public static void setPubResponseChance(int pubResponseChance)
  {
    Configuration.pubResponseChance = pubResponseChance;
  }
  
  public static int getPingResponseChance()
  {
    return pingResponseChance;
  }
  
  public static void setPingResponseChance(int pingResponseChance)
  {
    Configuration.pingResponseChance = pingResponseChance;
  }

  public static int getGreetChance()
  {
    return greetChance;
  }

  public static void setGreetChance(int greetChance)
  {
    Configuration.greetChance = greetChance;
  }
  
  public static boolean getLearnNames()
  {
    return Configuration.learnNames;
  }
  
  public static void setLearnNames(boolean newLearnNames)
  {
    Configuration.learnNames = newLearnNames;
  }
}

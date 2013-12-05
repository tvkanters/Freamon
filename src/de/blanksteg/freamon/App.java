package de.blanksteg.freamon;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.pircbotx.exception.IrcException;

import de.blanksteg.freamon.hal.ContinuousTextTrainer;
import de.blanksteg.freamon.hal.FileTrainer;
import de.blanksteg.freamon.hal.FreamonHal;
import de.blanksteg.freamon.hal.HexChatTrainer;
import de.blanksteg.freamon.hal.IRSSITrainer;
import de.blanksteg.freamon.hal.KVIrcTrainer;
import de.blanksteg.freamon.hal.SerializedFreamonHalTools;
import de.blanksteg.freamon.hal.Trainer;
import de.blanksteg.freamon.hal.TrainerFactory;
import de.blanksteg.freamon.irc.CommandResponseGenerator;
import de.blanksteg.freamon.irc.ComplexResponseGenerator;
import de.blanksteg.freamon.irc.FixedGreetingsGenerator;
import de.blanksteg.freamon.irc.FixedResponseGenerator;
import de.blanksteg.freamon.irc.FreamonHalResponseGenerator;
import de.blanksteg.freamon.irc.GenericAnthroGreetingsGenerator;
import de.blanksteg.freamon.irc.GenericAnthroResponseGenerator;
import de.blanksteg.freamon.irc.GreetingsGenerator;
import de.blanksteg.freamon.irc.IRCClient;
import de.blanksteg.freamon.irc.Network;
import de.blanksteg.freamon.irc.ResponseGenerator;

public class App
{
  private static final Logger l = Logger.getLogger("de.blanksteg.freamon.main");
  
  private static final String[] SINGLE_TRAINERS = new String[]
  {
    "tf", "ct", "il", "hl", "kl"
  };
  
  private static final String[] DIRECTORY_TRAINERS = new String[]
  {
    "tfd", "ctd", "ild", "hld", "kld"
  };
  
  private static final Map<String, TrainerFactory> TRAINER_FACTORIES = new HashMap<String, TrainerFactory>();
  
  static
  {
    TRAINER_FACTORIES.put("il",   IRSSITrainer.FACTORY);
    TRAINER_FACTORIES.put("ild",  IRSSITrainer.FACTORY);
    TRAINER_FACTORIES.put("hl",   HexChatTrainer.FACTORY);
    TRAINER_FACTORIES.put("hld",  HexChatTrainer.FACTORY);
    TRAINER_FACTORIES.put("kl",   KVIrcTrainer.FACTORY);
    TRAINER_FACTORIES.put("kld",  KVIrcTrainer.FACTORY);
    TRAINER_FACTORIES.put("tf",   FileTrainer.FACTORY);
    TRAINER_FACTORIES.put("tfd",  FileTrainer.FACTORY);
    TRAINER_FACTORIES.put("ct",   ContinuousTextTrainer.FACTORY);
    TRAINER_FACTORIES.put("ctd",  ContinuousTextTrainer.FACTORY);
  }
  
  private static final int WRITEOUT_INTERVAL = 40;
  
  public static void main(String[] args) throws IOException, IrcException, ParseException, ClassNotFoundException
  {
    Options options = getOptions();
    CommandLineParser parser = new GnuParser();
    CommandLine line = parser.parse(options, args);
    
    try
    {
      File xml = new File("log4j.xml");
      if (xml.exists() && xml.canRead())
      {
        DOMConfigurator.configure("log4j.xml");
      }
      else
      {
        DOMConfigurator.configure(new URL("log4j.xml"));
      }
    }
    catch (Exception e)
    {
      
    }
    
    if (line.hasOption("h"))
    {
      printHelp(options);
      return;
    }
    
    l.trace("Handling command line arguments.");
    if (line.hasOption("i"))
    {
      l.info("Initialization flag is set. Starting to train a new brain.");
      train(line);
    }
    else
    {
      if (line.hasOption("cf"))
      {
        l.info("Starting the IRC client from config file.");
        startClientConfigFile(line.getOptionValue("cf"));
      }
      else
      {
        l.info("Starting the IRC client from command line.");
        startClientCommandLine(line);
      }
    }
  }
  
  private static void printHelp(Options options)
  {
    HelpFormatter formatter = new HelpFormatter();
    formatter.setLeftPadding(2);
    formatter.setDescPadding(4);
    formatter.printHelp(120,
        "<jar>",
        "\nWhere [...] indicates and optional parameter and -p or --paramter can be:\n",
        options,
        "(c) Marc Müller",
        true
    );
  }
  
  @SuppressWarnings("static-access")
  private static Options getOptions()
  {
    Options gnuOptions = new Options();
    
    Option ircServer = OptionBuilder.withLongOpt("server")
                                    .withDescription("The initial IRC server(s) to join. Other ones can be specified later. Use <host>:<port> to specify a port other than the default 6667.")
                                    .hasArgs()
                                    .create("s");
    
    Option ircName = OptionBuilder.withLongOpt("name")
                                  .withDescription("The initial nickname choices of the bot.")
                                  .hasArgs()
                                  .create("n");
    
    Option cooldown = OptionBuilder.withLongOpt("cooldown")
                                    .withDescription("The initial cooldown time in seconds.")
                                    .hasArg()
                                    .create("cd");
    
    Option pubChance = OptionBuilder.withLongOpt("public-chance")
                                    .withDescription("The initial public response chance.")
                                    .hasArg()
                                    .create("pc");
    
    Option pingChance = OptionBuilder.withLongOpt("direct-chance")
                                      .withDescription("The initial response to messages the bot is pinged in.")
                                      .hasArg()
                                      .create("dc");
    
    Option greetChance = OptionBuilder.withLongOpt("greeting-chance")
                                      .withDescription("The initial chance of greeting.")
                                      .hasArg()
                                      .create("gc");
    
    Option minDelay = OptionBuilder.withLongOpt("min-delay")
                                    .withDescription("The initial minimum response delay in milliseconds.")
                                    .hasArg()
                                    .create("mid");
    
    Option maxDelay = OptionBuilder.withLongOpt("max-delay")
                                    .withDescription("The initial maximum response delay in milliseconds.")
                                    .hasArg()
                                    .create();
    
    Option init = OptionBuilder.withLongOpt("initialize")
                                .withDescription("Setting this flag will create a FreamonHal brain file instead of joining any servers. You should specify trainers to fill the generated brain. The output file will be the brain file specified with -bf.")
                                .create("i");
    
    Option brain = OptionBuilder.withLongOpt("brain-file")
                                .withDescription("The initial brain file to use for FreamonHal.")
                                .hasArg()
                                .create("bf");
    
    Option irssi = OptionBuilder.withLongOpt("irssi-log")
                                .withDescription("An IRSSI log file to train.")
                                .hasArgs()
                                .create("il");
    
    Option irssiD = OptionBuilder.withArgName("irssi-log-dir")
                                  .withDescription("An IRSSI log directory to scan through.")
                                  .hasArgs()
                                  .create("ild");
    
    Option txt = OptionBuilder.withLongOpt("text-file")
                              .withDescription("Some text file to train. The file will be trained line by line.")
                              .hasArgs()
                              .create("tf");
    
    Option txtD = OptionBuilder.withLongOpt("text-file-dir")
                                .withDescription("A directory containing text files to scan through.")
                                .hasArgs()
                                .create("tfd");
    
    Option hex = OptionBuilder.withLongOpt("hexchat-log")
                              .withDescription("A Hexchat log file to train.")
                              .hasArgs()
                              .create("hl");
    
    Option hexD = OptionBuilder.withLongOpt("hexchat-log-dir")
                                .withDescription("A HexChat log directory to scan through.")
                                .hasArgs()
                                .create("hld");
    
    Option cont = OptionBuilder.withLongOpt("continuous-text")
                                .withDescription("A file with continuous text to train.")
                                .hasArgs()
                                .create("ct");
    
    Option contD = OptionBuilder.withLongOpt("continuous-text-dir")
                                .withDescription("A directory with continuous text files to scan through.")
                                .hasArgs()
                                .create("ctd");
    
    Option help = OptionBuilder.withLongOpt("help")
                              .withDescription("Display this help.")
                              .create("h");
    
    Option admin = OptionBuilder.withLongOpt("password")
                                .withDescription("The password needed to administrate the bot. Default is temehiisfaggot.")
                                .hasArg()
                                .create("p");
    
    Option client = OptionBuilder.withLongOpt("client-name")
                                  .withDescription("The client name for the irc bot.")
                                  .hasArg()
                                  .create("c");
    
    Option real = OptionBuilder.withLongOpt("real-name")
                                .withDescription("The real name for the irc bot.")
                                .hasArg()
                                .create("r");
    
    Option user = OptionBuilder.withLongOpt("user-name")
                              .withDescription("The user name for the irc bot.")
                              .hasArg()
                              .create("u");
    
    Option kvirc = OptionBuilder.withLongOpt("kvirc-log")
                                .withDescription("A KVIrc log file to parse.")
                                .hasArgs()
                                .create("kl");
    
    Option kvircD = OptionBuilder.withLongOpt("kvirc-log-dir")
                                  .withDescription("A KVIrc log directory to scan through.")
                                  .hasArgs()
                                  .create("kld");
    
    Option cf = OptionBuilder.withLongOpt("config-file")
                             .withDescription("A configuration file to be used.")
                             .hasArg()
                             .create("cf");
    
    gnuOptions.addOption(ircServer);
    gnuOptions.addOption(cf);
    gnuOptions.addOption(ircName);
    gnuOptions.addOption(admin);
    gnuOptions.addOption(help);
    gnuOptions.addOption(hex);
    gnuOptions.addOption(hexD);
    gnuOptions.addOption(txt);
    gnuOptions.addOption(txtD);
    gnuOptions.addOption(cont);
    gnuOptions.addOption(contD);
    gnuOptions.addOption(irssi);
    gnuOptions.addOption(irssiD);
    gnuOptions.addOption(kvirc);
    gnuOptions.addOption(kvircD);
    gnuOptions.addOption(brain);
    gnuOptions.addOption(init);
    gnuOptions.addOption(maxDelay);
    gnuOptions.addOption(minDelay);
    gnuOptions.addOption(greetChance);
    gnuOptions.addOption(pingChance);
    gnuOptions.addOption(pubChance);
    gnuOptions.addOption(cooldown);
    gnuOptions.addOption(client);
    gnuOptions.addOption(real);
    gnuOptions.addOption(user);
    
    return gnuOptions;
  }
  
  private static Collection<Trainer> gatherSupposedTrainers(CommandLine args)
  {
    LinkedList<Trainer> trainers = new LinkedList<Trainer>();
    
    l.trace("Starting to gather single trainers.");
    for (String singleTrainer : SINGLE_TRAINERS)
    {
      if (args.hasOption(singleTrainer))
      {
        l.debug("Getting all trainers for command line option " + singleTrainer + ".");
        TrainerFactory factory = TRAINER_FACTORIES.get(singleTrainer);
        
        if (factory != null)
        {
          String[] files = args.getOptionValues(singleTrainer);
          for (String file : files)
          {
            Trainer trainer = gatherTrainer(factory, file);
            
            if (trainer != null)
            {
              trainers.add(trainer);
            }
          }
        }
      }
    }
    
    for (String directoryTrainer : DIRECTORY_TRAINERS)
    {
      if (args.hasOption(directoryTrainer))
      {
        l.debug("Getting all trainers for command line option " + directoryTrainer + ".");
        TrainerFactory factory = TRAINER_FACTORIES.get(directoryTrainer);
        
        if (factory != null)
        {
          String[] directories = args.getOptionValues(directoryTrainer);
          for (String directory : directories)
          {
            Collection<Trainer> gathered = gatherTrainers(factory, directory);
            trainers.addAll(gathered);
          }
        }
      }
    }
    
    return trainers;
  }
  
  private static Collection<Trainer> gatherTrainers(TrainerFactory factory, String arg)
  {
    l.trace("Gathering trainers for directory at " + arg);
    LinkedList<Trainer> trainers = new LinkedList<Trainer>();
    
    File dir = new File(arg);
    if (dir.exists() && dir.canRead())
    {
      if (dir.isDirectory())
      {
        File[] children = dir.listFiles();
        for (File child : children)
        {
          if (child.exists() && child.canRead())
          {
            if (child.isDirectory())
            {
              Collection<Trainer> gathered = gatherTrainers(factory, child.getAbsolutePath());   
              trainers.addAll(gathered);
            }
            else
            {
              Trainer trainer = factory.createTrainerFor(child);
              if (trainer != null)
              {
                trainers.add(trainer);
              }
            }
          }
          else
          {
            System.err.println("The file at " + arg + " does not exist or can not be read. Skipping.");
          }
        }
      }
      else
      {
        System.err.println(arg + " is not a directory. Skipping.");
      }
    }
    else
    {
      System.err.println("The directory at " + arg + " does not exist or can not be read. Skipping.");
    }
    
    return trainers;
  }
  
  private static Trainer gatherTrainer(TrainerFactory factory, String arg)
  {
    l.trace("Gathering trainer for the file at " + arg);
    File file = new File(arg);
    if (file.exists() && file.canRead())
    {
      return factory.createTrainerFor(file);
    }
    else
    {
      System.err.println("The file at " + arg + " does not exist or can not be read. Skipping.");
    }
    
    return null;
  }
  
  private static void train(CommandLine args) throws IOException, ClassNotFoundException
  {
    if (!args.hasOption("bf"))
    {
      System.err.println("No brain file specified. Aborting.");
      return;
    }
    
    Collection<Trainer> trainers = gatherSupposedTrainers(args);
    l.debug("Gathered " + trainers.size() + " trainers.");
    
    String brain = args.getOptionValue("bf");
    File brainFile = new File(brain);
    FreamonHal hal = new FreamonHal(brainFile);
    l.info("Starting training.");
    long start = System.currentTimeMillis();
    
    int trainCount = 0;
    for (Trainer trainer : trainers)
    {
      trainCount++;
      trainer.trainAll(hal);
      
      if (trainCount % WRITEOUT_INTERVAL == 0)
      {
        l.info("Flusing current hal instance.");
        start = System.currentTimeMillis();
        SerializedFreamonHalTools.write(brainFile, hal);
        System.gc();
        l.info("Wrote and read hal instance in " + ((System.currentTimeMillis() - start) / 1000 ) + "s.");
        hal = SerializedFreamonHalTools.read(brainFile);
      }
    }
    
    hal.trainAll(trainers);
    long end = System.currentTimeMillis();
    l.info("Done with training. It took " + ((end - start) / 1000) + " seconds.");
    
    l.info("Writing the file to " + brain);
    start = System.currentTimeMillis();
    SerializedFreamonHalTools.write(brainFile, hal);
    end = System.currentTimeMillis();
    l.info("Done writing the file. Writing took " + ((end - start) / 1000) + " seconds.");
  }
  
  private static void startClientCommandLine(CommandLine line) throws IOException, IrcException
  {
    Configuration.configure(line);
    
    if (!line.hasOption("s"))
    {
      System.err.println("No servers specified. Aborting.");
      return;
    }
    
    if (!line.hasOption("n"))
    {
      System.err.println("No nicknames specified. Aborting.");
      return;
    }
    
    if (!line.hasOption("bf"))
    {
      System.err.println("No brain file specified. Aborting.");
      return;
    }
    
    String brain = line.getOptionValue("bf");
    File brainFile = new File(brain);
    if (!brainFile.exists() || !brainFile.canRead())
    {
      System.err.println("Brain file at " + brainFile + " does not exist or is otherwise not readable. Aborting.");
      return;
    }
    
    FreamonHal hal = null;
    try
    {
      l.debug("Reading brain file from " + brainFile);
      hal = SerializedFreamonHalTools.read(brainFile);
    } catch (ClassNotFoundException e1)
    {
      System.err.println("Error while reading brain file: ");
      e1.printStackTrace();
      return;
    }
    
    l.debug("Starting network configuration.");
    IRCClient client = new IRCClient();
    client.addSubscriber(hal);
    
    ResponseGenerator responder = createDefaultResponseGenerator(client, hal);
    client.setResponder(responder);
    
    GreetingsGenerator greeter = createDefaultGreetingsGenerator(client);
    client.setGreeter(greeter);
    
    for (String server : line.getOptionValues("s"))
    {
      int port = Configuration.DEFAULT_PORT;
      if (server.contains(":"))
      {
        String[] parts = server.split(":");
        String host = parts[0];
        try
        {
          port = Integer.parseInt(parts[1]);
        }
        catch (Exception e)
        {
          System.err.println("Malformed port in " + server + ". Aborting.");
          return;
        }
        server = host;
      }
      
      l.debug("Creating network instance for " + server);
      Network network = new Network(server, port, line.getOptionValues("n"), Configuration.getUserName(), Configuration.getRealName(), Configuration.getClientName());
      client.addNetwork(network);
    }
    
    l.info("Connecting...");
    client.doConnect();
  }
  
  private static ResponseGenerator createDefaultResponseGenerator(IRCClient client, FreamonHal hal) throws IOException
  {
    FreamonHalResponseGenerator halResponder = new FreamonHalResponseGenerator(hal);
    
    CommandResponseGenerator cmd = new CommandResponseGenerator(client, halResponder);
    client.addSubscriber(cmd);
    
    FixedResponseGenerator fixed = loadFixedResponses();
    
    ComplexResponseGenerator complex = new ComplexResponseGenerator();
    complex.addResponder(fixed);
    complex.addResponder(halResponder);
    
    GenericAnthroResponseGenerator anthro = new GenericAnthroResponseGenerator(complex);
    
    ComplexResponseGenerator base = new ComplexResponseGenerator();
    base.addResponder(cmd);
    base.addResponder(anthro);
    
    return base;
  }
  
  private static FixedResponseGenerator loadFixedResponses() throws IOException
  {
    FixedResponseGenerator fixed = new FixedResponseGenerator();
    
    l.trace("Loading fixed responses.");
    String jsonString = FileIO.readClassPathFile(Configuration.FIXED_PATH);
    JSONObject json = JSONObject.fromObject(jsonString);
    
    for (Object key : json.keySet())
    {
      String cause = key.toString();
      String response = json.get(key).toString();
      fixed.putResponse(cause, response);
      l.trace("Got fixed response " + cause + " => " + response);
    }
    return fixed;
  }
  
  private static GreetingsGenerator createDefaultGreetingsGenerator(IRCClient client) throws IOException
  {
    FixedGreetingsGenerator fixed = new FixedGreetingsGenerator();
    
    String joinMessages = FileIO.readClassPathFile(Configuration.JOIN_PATH);
    for (String line : joinMessages.split("\n"))
    {
      fixed.addJoinMessage(line);
    }
    
    String greetMessages = FileIO.readClassPathFile(Configuration.GREETINGS_PATH);
    for (String line : greetMessages.split("\n"))
    {
      fixed.addGreetMessage(line);
    }
    
    GenericAnthroGreetingsGenerator anthro = new GenericAnthroGreetingsGenerator(fixed);
    return anthro;
  }
  
  private static void startClientConfigFile(String cf) throws IOException, ClassNotFoundException, IrcException
  {
    String      configString  = FileIO.readFile(cf);
    JSONObject  config        = JSONObject.fromObject(configString);
    
    Configuration.configure(config);
    
    FreamonHal hal = null;
    if (config.containsKey("brain"))
    {
      hal = SerializedFreamonHalTools.read(new File(config.getString("brain")));
    }
    else
    {
      System.err.println("No brain specified. Aborting.");
      return;
    }
    
    l.debug("Starting network configuration.");
    IRCClient client = new IRCClient();
    client.addSubscriber(hal);
    
    ResponseGenerator responder = createDefaultResponseGenerator(client, hal);
    client.setResponder(responder);
    
    GreetingsGenerator greeter = createDefaultGreetingsGenerator(client);
    client.setGreeter(greeter);
    
    if (config.containsKey("networks"))
    {
      JSONArray networksArray = config.getJSONArray("networks");
      for (int i = 0; i < networksArray.size(); i++)
      {
        JSONObject  networkObj  = networksArray.getJSONObject(i);
        Network     network     = fromJSONObject(networkObj);
        
        client.addNetwork(network);
      }
    }
    
    client.doConnect();
  }
  
  private static Network fromJSONObject(JSONObject network)
  {
    String url = null;
    int port = Configuration.DEFAULT_PORT;
    String[] nicknames = null;
    String username = Configuration.getUserName();
    String realname = Configuration.getRealName();
    String client = Configuration.getClientName();
    String serverpass = Configuration.DEFAULT_PASS;
    
    JSONArray nicks = network.getJSONArray("nicks");
    nicknames = new String[nicks.size()];
    for (int j = 0; j < nicks.size(); j++)
    {
      nicknames[j] = nicks.getString(j);
    }
    
    url = network.getString("url");
    
    if (network.containsKey("port"))
    {
      port = network.getInt("port");
    }
    
    if (network.containsKey("username"))
    {
      username = network.getString("username");
    }
    
    if (network.containsKey("client"))
    {
      client = network.getString("client");
    }
    
    if (network.containsKey("serverpass"))
    {
      serverpass = network.getString("serverpass");
    }
    
    if (network.containsKey("realname"))
    {
      realname = network.getString("realname");
    }
    
    Network networkObj = new Network(url, port, nicknames, username, realname, client, serverpass);
    
    if (network.containsKey("active"))
    {
      JSONArray activeChannels = network.getJSONArray("active");
      for (int i = 0; i < activeChannels.size(); i++)
      {
        String channel = activeChannels.getString(i);
        networkObj.addActiveChannel(channel);
      }
    }
    
    if (network.containsKey("passive"))
    {
      JSONArray passiveChannels = network.getJSONArray("passive");
      for (int i = 0; i < passiveChannels.size(); i++)
      {
        String channel = passiveChannels.getString(i);
        networkObj.addPassiveChannel(channel);
      }
    }
    
    if (network.containsKey("ignore"))
    {
      JSONArray ignores = network.getJSONArray("ignore");
      for (int i = 0; i < ignores.size(); i++)
      {
        String ignored = ignores.getString(i);
        networkObj.addIgnored(ignored);
      }
    }
    
    return networkObj; 
  }
}

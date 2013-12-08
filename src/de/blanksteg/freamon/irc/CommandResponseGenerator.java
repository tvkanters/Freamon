package de.blanksteg.freamon.irc;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.NickChangeEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.events.QuitEvent;

import de.blanksteg.freamon.Configuration;
import de.blanksteg.freamon.hal.FreamonHal;
import de.blanksteg.freamon.hal.SerializedFreamonHalTools;

/**
 * The CommandResponseGenerator implements a private message based administration backend to manage the following
 * aspects of the application:
 * <ul>
 * <li>Join a channel either active, passive or polite.</li>
 * <li>Leave a channel.</li>
 * <li>Connect to an IRC network.</li>
 * <li>Disconnect from a network.</li>
 * <li>Change public, pinged and greet chances.</li>
 * <li>Change cooldown period.</li>
 * <li>Change message delay intervals.</li>
 * <li>Change the brain base used for {@link FreamonHal}.
 * <li>Quit the application.</li>
 * </ul>
 * 
 * Because the functionality described above can easily be abused by some adversary, most commands require a user to be
 * authenticated. Authenticated means the respective user has sent the bot a private message containing the
 * authorization command and the password specified in {@link Configuration#getPassword()}. A user is considered authed
 * as long as he doesn't change his name or disconnect from the network he authenticated from.
 * 
 * For an detailed explanation of the possible commands and their parameters, consult the manual.
 * 
 * @author Marc Müller
 */
public class CommandResponseGenerator extends ListenerAdapter<Network> implements ResponseGenerator {
    /** The log4j instance to output messages to. */
    private static final Logger l = Logger.getLogger("de.blanksteg.freamon.cmd");

    /**
     * An internal interface used to delegate actual command handling to small, mostly anonymous classes that can be
     * stored in a map.
     * 
     * @author Marc Müller
     */
    private static interface PrivateCommandHandler {
        /**
         * Handle the command stored in the given event.
         * 
         * @param event
         *            The event the command is in.
         * @return The result of the execution.
         */
        public String handleCommand(PrivateMessageEvent<Network> event);
    }

    /**
     * An internal interface used to delegate actual command handling to small, mostly anonymous classes that can be
     * stored in a map.
     */
    private static interface PublicCommandHandler {
        /**
         * Handle the command stored in the given event.
         * 
         * @param event
         *            The event the command is in.
         * @return The result of the execution.
         */
        public String handleCommand(MessageEvent<Network> event);
    }

    /**
     * A general class used for commands that require an additional parameter.
     * 
     * @author Marc Müller
     */
    private abstract class PrivateParameterCommandHandler implements PrivateCommandHandler {
        @Override
        public String handleCommand(PrivateMessageEvent<Network> event) {
            String message = event.getMessage();
            String[] parts = message.split(" ");
            if (parts.length < 2) {
                return "No parameter specified. Add a parameter after the command seperated by space.";
            } else {
                return this.handleCommand(event, parts[1]);
            }
        }

        /**
         * Handle the command stored in the given event that has the given parameter.
         * 
         * @param event
         *            The event the command was contained in.
         * @param param
         *            The parameter passed to the command.
         * @return The result of the execution.
         */
        public abstract String handleCommand(PrivateMessageEvent<Network> event, String param);
    }

    /**
     * A general class used for commands that required a user to be authenticated.
     * 
     * @author Marc Müller
     */
    private abstract class PriviledgedCommandHandler extends PrivateParameterCommandHandler {
        @Override
        public String handleCommand(PrivateMessageEvent<Network> event, String param) {
            if (authed.contains(getAuthID(event.getUser().getNick(), event.getBot()))) {
                return this.handleAuthedCommand(event, param);
            } else {
                return "You are not authenticated.";
            }
        }

        /**
         * Safely handle the command contained in the given event that was passed the given parameter.
         * 
         * @param event
         *            The event the command has caused.
         * @param param
         *            The parameter given.
         * @return The result of the execution.
         */
        public abstract String handleAuthedCommand(PrivateMessageEvent<Network> event, String param);
    }

    /**
     * A general class used for commands that required a user to be ops.
     */
    private abstract class OpsCommandHandler implements PublicCommandHandler {
        @Override
        public String handleCommand(MessageEvent<Network> event) {
            if (event.getChannel().getOps().contains(event.getUser())) {
                return handleOpsCommand(event);
            } else {
                return handleNonOpsCommand(event);
            }
        }

        /**
         * Safely handle the command contained in the given event.
         * 
         * @param event
         *            The event the command has caused.
         * 
         * @return The result of the execution.
         */
        public abstract String handleOpsCommand(MessageEvent<Network> event);

        /**
         * Handle the command contained in the given event when requested by non-ops.
         * 
         * @param event
         *            The event the command has caused.
         * 
         * @return The result of the execution.
         */
        public abstract String handleNonOpsCommand(MessageEvent<Network> event);
    }

    /**
     * A general command handler used for privileged commands that require a number within a certain range as their
     * parameter.
     * 
     * @author Marc Müller
     */
    private abstract class PriviledgedNumberCommandHandler extends PriviledgedCommandHandler {
        /** The minimum parameter value. */
        private final int min;
        /** The maximum parameter value. */
        private final int max;

        /**
         * Create a new instance accepting values from newMin to newMax.
         * 
         * @param newMin
         * @param newMax
         */
        public PriviledgedNumberCommandHandler(int newMin, int newMax) {
            this.min = newMin;
            this.max = newMax;
        }

        @Override
        public String handleAuthedCommand(PrivateMessageEvent<Network> event, String param) {
            int value = Integer.MIN_VALUE;

            try {
                value = Integer.parseInt(param);
            } catch (Exception e) {
                return "Malformed number " + param;
            }

            if (value < this.min || value > this.max) {
                return "Value must be between " + this.min + " and " + this.max + ".";
            }

            return this.handleAuthedCommand(event, value);
        }

        /**
         * Handle the privileged command with the parameter within the legal range.
         * 
         * @param event
         *            The event the command was in.
         * @param param
         *            The numerical parameter.
         * @return The result of the execution.
         */
        public abstract String handleAuthedCommand(PrivateMessageEvent<Network> event, int param);
    }

    /**
     * A generic handler used for commands requiring a channel as their parameter.
     * 
     * @author Marc Müller
     */
    private abstract class ChannelCommandHandler extends PriviledgedCommandHandler {
        @Override
        public String handleAuthedCommand(PrivateMessageEvent<Network> event, String channel) {
            if (!channel.matches(Configuration.CHANNEL_MATCH)) {
                return "Malformed channel: " + channel;
            } else {
                return this.handleChannelCommand(event, channel);
            }
        }

        /**
         * Handle the command contained in the given event with the given channel name as parameter.
         * 
         * @param event
         *            The event caused by the command.
         * @param channel
         *            The channel name passed as a parameter.
         * @return The result of the execution.
         */
        public abstract String handleChannelCommand(PrivateMessageEvent<Network> event, String channel);
    }

    /**
     * A general handler used for join requests. It ensures the bot supposed to join is not already in the requested
     * channel.
     * 
     * @author Marc Müller
     */
    private abstract class JoinCommandHandler extends ChannelCommandHandler {
        @Override
        public String handleChannelCommand(PrivateMessageEvent<Network> event, String channel) {
            Network target = event.getBot();
            if (target.channelKnown(channel)) {
                return "Already in channel " + channel;
            }

            return this.joinChannel(target, channel);
        }

        /**
         * Join the given channel on the given network.
         * 
         * @param network
         *            The network the channel is in.
         * @param channel
         *            The channel to join.
         * @return The result of the execution.
         */
        public abstract String joinChannel(Network network, String channel);
    }

    /** The IRC client to modify networks and listen for nick changes / quits on of. */
    private final IRCClient client;
    /** A responder to switch the {@link FreamonHal} instance of when brain switching. */
    private final FreamonHalResponseGenerator halResponder;
    /** Set of currently authenticated users. */
    private final Set<String> authed = new HashSet<String>();

    /** Mapping of known private commands to their respective handlers. */
    private final Map<String, PrivateCommandHandler> privateHandlers = new HashMap<String, PrivateCommandHandler>();
    /** Mapping of known public commands to their respective handlers. */
    private final Map<String, PublicCommandHandler> publicHandlers = new HashMap<String, PublicCommandHandler>();

    /**
     * Creates a new command backend for the given {@link IRCClient} and {@link FreamonHalResponseGenerator}.
     * 
     * @param newClient
     * @param newHalResponder
     */
    public CommandResponseGenerator(IRCClient newClient, FreamonHalResponseGenerator newHalResponder) {
        this.client = newClient;
        this.halResponder = newHalResponder;

        PrivateCommandHandler authHandler = new PrivateCommandHandler() {
            public String handleCommand(PrivateMessageEvent<Network> event) {
                User user = event.getUser();
                if (authed.contains(user)) {
                    return "Already authed.";
                } else {
                    String[] parts = event.getMessage().split(" ");
                    if (parts.length < 2) {
                        return "You must specify a password after the command.";
                    } else {
                        if (parts[1].equals(Configuration.getPassword())) {
                            String id = getAuthID(user.getNick(), event.getBot());
                            auth(id);
                            return "Successfully authenticated as an admin.";
                        } else {
                            return "Wrong password.";
                        }
                    }
                }
            }
        };

        PrivateCommandHandler activeJoinHandler = new JoinCommandHandler() {
            @Override
            public String joinChannel(Network network, String channel) {
                network.addActiveChannel(channel);
                return "Joined as an active user.";
            }
        };

        PrivateCommandHandler lurkHandler = new JoinCommandHandler() {
            @Override
            public String joinChannel(Network network, String channel) {
                network.addPassiveChannel(channel);
                return "Joined as a lurker.";
            }
        };

        PrivateCommandHandler politeHandler = new JoinCommandHandler() {
            @Override
            public String joinChannel(Network network, String channel) {
                network.addPoliteChannel(channel);
                return "Joined as a polite user.";
            }
        };

        PrivateCommandHandler partHandler = new ChannelCommandHandler() {
            @Override
            public String handleChannelCommand(PrivateMessageEvent<Network> event, String channel) {
                Network target = event.getBot();
                if (!target.channelKnown(channel)) {
                    return "Not in channel " + channel;
                }

                target.partChannel(channel);
                return "Left channel " + channel;
            }
        };

        PrivateCommandHandler networkAdditionHandler = new PriviledgedCommandHandler() {
            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, String network) {
                String host = network;
                int port = Configuration.DEFAULT_PORT;

                if (network.contains(":")) {
                    String[] parts = network.split(":");
                    host = parts[0];
                    try {
                        port = Integer.parseInt(parts[1]);
                    } catch (Exception e) {
                        return "Malformed port: " + network;
                    }
                }

                Network created = new Network(host, port, event.getBot().getNickNames(), Configuration.getUserName(),
                        Configuration.getRealName(), Configuration.getClientName());
                try {
                    client.addNetwork(created);
                } catch (Exception e) {
                    return "Could not connect: " + e.getMessage();
                }

                return "Joined " + network;
            }
        };

        PrivateCommandHandler networkRemovalHandler = new PriviledgedCommandHandler() {
            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, String param) {
                if (event.getBot().getUrl().equals(param)) {
                    return "Can't delete the network you are talking to me on.";
                }

                if (client.removeNetwork(param)) {
                    return "Disconnected from " + param;
                } else {
                    return "Not connected to " + param;
                }
            }
        };

        PrivateCommandHandler nickChangeHandler = new PriviledgedCommandHandler() {
            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, String param) {
                if (param.length() < 2 || !param.matches("[a-zA-Z_\\-]*")) {
                    return "Please specify a name that is longer than two characters and matches [a-zA-Z_\\-]*.";
                }

                event.getBot().changeNick(param);
                return "Attempted to change nick to " + param;
            }
        };

        PrivateCommandHandler quitHandler = new PrivateCommandHandler() {
            @Override
            public String handleCommand(PrivateMessageEvent<Network> event) {
                String id = getAuthID(event.getUser().getNick(), event.getBot());
                if (authed.contains(id)) {
                    event.getBot().sendMessage(event.getUser(), "Bye!");
                    client.doDisconnect();

                    l.info("Storing the current brain state.");
                    FreamonHal hal = halResponder.getFreamonHal();
                    l.trace("Attempting lock on the Freamon instance.");
                    synchronized (hal) {
                        l.debug("Writing the brain to the disk at " + hal.getBaseFile() + ".");
                        SerializedFreamonHalTools.writeThreaded(hal.getBaseFile(), hal);
                        l.debug("Done writing the brain to " + hal.getBaseFile() + ".");
                    }
                    return "Bye!";
                } else {
                    return "You are not authenticated.";
                }
            }
        };

        PrivateCommandHandler greetChance = new PriviledgedNumberCommandHandler(0, Configuration.CHANCE_MAX) {
            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, int param) {
                Configuration.setGreetChance(param);
                return "Set the new greeting chance to " + param + "%.";
            }
        };

        PrivateCommandHandler publicChance = new PriviledgedNumberCommandHandler(0, Configuration.CHANCE_MAX) {
            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, int param) {
                Configuration.setPubResponseChance(param);
                return "Set the new public response chance to " + param + "%.";
            }
        };

        PrivateCommandHandler pingChance = new PriviledgedNumberCommandHandler(0, Configuration.CHANCE_MAX) {
            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, int param) {
                Configuration.setPubResponseChance(param);
                return "Set the new pinged response chance to " + param + "%.";
            }
        };

        PrivateCommandHandler cooldown = new PriviledgedNumberCommandHandler(Configuration.MIN_COOLDOWN,
                Configuration.MAX_COOLDOWN) {
            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, int param) {
                Configuration.setCooldown(param);
                return "Set the new cooldown to " + param + "s.";
            }
        };

        PrivateCommandHandler minDelay = new PriviledgedNumberCommandHandler(Configuration.MIN_MIN_DELAY,
                Configuration.MAX_MIN_DELAY) {
            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, int param) {
                Configuration.setMinDelay(param);
                return "Set the new minimum delay to " + param + "ms.";
            }
        };

        PrivateCommandHandler maxDelay = new PriviledgedNumberCommandHandler(Configuration.MIN_MAX_DELAY,
                Configuration.MAX_MAX_DELAY) {
            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, int param) {
                if (Configuration.getMinDelay() < param) {
                    Configuration.setMaxDelay(param);
                    return "Set the new maximum delay to " + param + "ms.";
                } else {
                    return "The maximum delay needs to be larger than the current minimum delay: "
                            + Configuration.getMinDelay() + "ms.";
                }
            }
        };

        PrivateCommandHandler brainSwitch = new PriviledgedCommandHandler() {

            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, String param) {
                File brain = new File(param);
                if (!brain.exists() || !brain.canRead()) {
                    return "The brain file either doesn't exist or is not readable: " + brain;
                }

                event.getBot()
                        .sendMessage(
                                event.getUser(),
                                "Switching the brain to " + brain
                                        + ". It might take a while. I will notify you when I'm done.");
                FreamonHal hal = halResponder.getFreamonHal();
                FreamonHal newHal = null;
                l.trace("Attempting lock on the Freamon instance.");
                synchronized (hal) {
                    l.trace("Got lock on the Freamon instance.");
                    try {
                        SerializedFreamonHalTools.write(hal.getBaseFile(), hal);
                    } catch (IOException e) {
                        return "Error while writing previous brain: " + e.getMessage();
                    }

                    try {
                        newHal = SerializedFreamonHalTools.read(brain);
                    } catch (Exception e) {
                        return "Error while reading new brain " + e.getMessage();
                    }

                    halResponder.setFreamonHal(newHal);
                }

                client.removeSubscriber(hal);
                client.addSubscriber(newHal);

                return "Done switching to the new brain " + brain;
            }
        };

        PublicCommandHandler tireHandler = new OpsCommandHandler() {
            @Override
            public String handleOpsCommand(MessageEvent<Network> event) {
                client.becomeTired(event.getChannel());
                return event.getUser().getNick() + ": ok i go now";
            }

            @Override
            public String handleNonOpsCommand(MessageEvent<Network> event) {
                return event.getUser().getNick() + ": no";
            }
        };

        PrivateCommandHandler untireHandler = new PriviledgedCommandHandler() {
            @Override
            public String handleAuthedCommand(PrivateMessageEvent<Network> event, String param) {
                client.becomeTired(param, -1);
                return ":D";
            }
        };

        this.privateHandlers.put("!auth", authHandler);
        this.privateHandlers.put("!nick", nickChangeHandler);
        this.privateHandlers.put("!join", activeJoinHandler);
        this.privateHandlers.put("!lurk", lurkHandler);
        this.privateHandlers.put("!polite", politeHandler);
        this.privateHandlers.put("!networkadd", networkAdditionHandler);
        this.privateHandlers.put("!networkdel", networkRemovalHandler);
        this.privateHandlers.put("!quit", quitHandler);
        this.privateHandlers.put("!part", partHandler);
        this.privateHandlers.put("!pubchance", publicChance);
        this.privateHandlers.put("!pingchance", pingChance);
        this.privateHandlers.put("!greetchance", greetChance);
        this.privateHandlers.put("!cooldown", cooldown);
        this.privateHandlers.put("!mindelay", minDelay);
        this.privateHandlers.put("!maxdelay", maxDelay);
        this.privateHandlers.put("!brainswitch", brainSwitch);
        this.privateHandlers.put("!plscome", untireHandler);

        this.publicHandlers.put("!plsgo", tireHandler);
    }

    @Override
    public String respondPublic(MessageEvent<Network> event) {
        String command = this.extractCommand(event.getMessage());
        if (command == null) {
            return null;
        }

        l.trace("Attempting to handle public command: " + command);
        if (this.publicHandlers.containsKey(command)) {
            l.debug("Handling public command: " + command + " by user " + event.getUser());
            return publicHandlers.get(command).handleCommand(event);
        } else {
            return null;
        }
    }

    @Override
    public synchronized String respondPrivate(PrivateMessageEvent<Network> event) {
        String command = this.extractCommand(event.getMessage());
        if (command == null) {
            return null;
        }

        l.trace("Attempting to handle private command: " + command);
        if (this.privateHandlers.containsKey(command)) {
            l.debug("Handling private command: " + command + " by user " + event.getUser());
            return privateHandlers.get(command).handleCommand(event);
        } else {
            return null;
        }
    }

    /**
     * Remember the user by the given auth ID to be authed.
     * 
     * @param id
     *            The auth ID of the user.
     */
    private void auth(String id) {
        this.authed.add(id);
        l.debug("Authed user: " + id);
    }

    /**
     * Remember the user by the given auth ID to not be authed anymore.
     * 
     * @param id
     *            The auth ID of the user.
     */
    private void deauth(String id) {
        this.authed.remove(id);
        l.debug("Deauthed user: " + id);
    }

    /**
     * If an authenticated user changes nicknames, we will consider him no longer authenticated.
     */
    public synchronized void onNickChange(NickChangeEvent<Network> event) {
        String id = this.getAuthID(event.getOldNick(), event.getBot());
        l.info(event.getUser() + " changed from " + event.getOldNick() + " to " + event.getNewNick()
                + ". Deauthing him.");
        this.deauth(id);
    }

    /**
     * If an authenticated user quits, we will consider him no longer authenticated.
     */
    public synchronized void onQuit(QuitEvent<Network> event) {
        String id = this.getAuthID(event.getUser().getNick(), event.getBot());
        l.info(id + " has quit. Deauthing him.");
        this.deauth(id);
    }

    /**
     * If an authenticated user parts, we will consider him no longer authenticated.
     */
    public synchronized void onPart(PartEvent<Network> event) {
        String id = this.getAuthID(event.getUser().getNick(), event.getBot());
        l.info(id + " has quit. Deauthing him.");
        this.deauth(id);
    }

    /**
     * Creates the auth ID for the given user on the given network.
     * 
     * @param name
     *            The user's name.
     * @param network
     *            The network he's on.
     * @return The respective auth ID
     */
    private String getAuthID(String name, Network network) {
        return name + "@" + network.getUrl();
    }

    /**
     * Find the command requested in the given message. A command is considered a word that follows a leading !.
     * 
     * @param message
     *            The message to scan through.
     * @return The command found, including the !. null if none was found.
     */
    private String extractCommand(String message) {
        if (message.startsWith("!")) {
            String[] parts = message.split(" ");
            if (parts.length > 0) {
                return parts[0];
            }
        }

        return null;
    }
}

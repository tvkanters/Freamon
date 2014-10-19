package de.blanksteg.freamon.irc;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.pircbotx.Colors;
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
        public String handleCommand(final PrivateMessageEvent<Network> event) {
            final String message = event.getMessage();
            final String[] parts = message.split(" ");
            if (parts.length < 2) {
                return "No parameter specified. Add a parameter after the command seperated by space.";
            } else {
                String param = "";
                for (int i = 1; i < parts.length; ++i) {
                    param += (i > 1 ? " " : "") + parts[i];
                }
                return this.handleCommand(event, param);
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
        public String handleCommand(final PrivateMessageEvent<Network> event, final String param) {
            if (authed.contains(getAuthID(event.getUser().getNick(), event.getBot()))) {
                return handleAuthedCommand(event, param);
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
        public String handleCommand(final MessageEvent<Network> event) {
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
        public PriviledgedNumberCommandHandler(final int newMin, final int newMax) {
            min = newMin;
            max = newMax;
        }

        @Override
        public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final String param) {
            int value = Integer.MIN_VALUE;

            try {
                value = Integer.parseInt(param);
            } catch (final Exception e) {
                return "Malformed number " + param;
            }

            if (value < min || value > max) {
                return "Value must be between " + min + " and " + max + ".";
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
        public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final String channel) {
            if (!channel.matches(Configuration.CHANNEL_MATCH)) {
                return "Malformed channel: " + channel;
            } else {
                return handleChannelCommand(event, channel);
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
        public String handleChannelCommand(final PrivateMessageEvent<Network> event, final String channel) {
            final Network target = event.getBot();
            if (target.channelKnown(channel)) {
                return "Already in channel " + channel;
            }

            return joinChannel(target, channel);
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
    public CommandResponseGenerator(final IRCClient newClient, final FreamonHalResponseGenerator newHalResponder) {
        client = newClient;
        halResponder = newHalResponder;

        final PrivateCommandHandler authHandler = new PrivateCommandHandler() {
            @Override
            public String handleCommand(final PrivateMessageEvent<Network> event) {
                final User user = event.getUser();
                if (authed.contains(user)) {
                    return "Already authed.";
                } else {
                    final String[] parts = event.getMessage().split(" ");
                    if (parts.length < 2) {
                        return "You must specify a password after the command.";
                    } else {
                        if (parts[1].equals(Configuration.getPassword())) {
                            final String id = getAuthID(user.getNick(), event.getBot());
                            auth(id);
                            return "Successfully authenticated as an admin.";
                        } else {
                            return "Wrong password.";
                        }
                    }
                }
            }
        };

        final PrivateCommandHandler activeJoinHandler = new JoinCommandHandler() {
            @Override
            public String joinChannel(final Network network, final String channel) {
                network.addActiveChannel(channel);
                return "Joined as an active user.";
            }
        };

        final PrivateCommandHandler lurkHandler = new JoinCommandHandler() {
            @Override
            public String joinChannel(final Network network, final String channel) {
                network.addPassiveChannel(channel);
                return "Joined as a lurker.";
            }
        };

        final PrivateCommandHandler politeHandler = new JoinCommandHandler() {
            @Override
            public String joinChannel(final Network network, final String channel) {
                network.addPoliteChannel(channel);
                return "Joined as a polite user.";
            }
        };

        final PrivateCommandHandler partHandler = new ChannelCommandHandler() {
            @Override
            public String handleChannelCommand(final PrivateMessageEvent<Network> event, final String channel) {
                final Network target = event.getBot();
                if (!target.channelKnown(channel)) {
                    return "Not in channel " + channel;
                }

                target.partChannel(channel);
                return "Left channel " + channel;
            }
        };

        final PrivateCommandHandler networkAdditionHandler = new PriviledgedCommandHandler() {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final String network) {
                String host = network;
                int port = Configuration.DEFAULT_PORT;

                if (network.contains(":")) {
                    final String[] parts = network.split(":");
                    host = parts[0];
                    try {
                        port = Integer.parseInt(parts[1]);
                    } catch (final Exception e) {
                        return "Malformed port: " + network;
                    }
                }

                final Network created = new Network(host, port, event.getBot().getNickNames(),
                        Configuration.getUserName(), Configuration.getRealName(), Configuration.getClientName());
                try {
                    client.addNetwork(created);
                } catch (final Exception e) {
                    return "Could not connect: " + e.getMessage();
                }

                return "Joined " + network;
            }
        };

        final PrivateCommandHandler networkRemovalHandler = new PriviledgedCommandHandler() {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final String param) {
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

        final PrivateCommandHandler nickChangeHandler = new PriviledgedCommandHandler() {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final String param) {
                if (param.length() < 2 || !param.matches("[a-zA-Z_\\-]*")) {
                    return "Please specify a name that is longer than two characters and matches [a-zA-Z_\\-]*.";
                }

                event.getBot().changeNick(param);
                return "Attempted to change nick to " + param;
            }
        };

        final PrivateCommandHandler quitHandler = new PrivateCommandHandler() {
            @Override
            public String handleCommand(final PrivateMessageEvent<Network> event) {
                final String id = getAuthID(event.getUser().getNick(), event.getBot());
                if (authed.contains(id)) {
                    event.getBot().sendMessage(event.getUser(), "Hold up... saving brain");

                    l.info("Storing the current brain state.");
                    final FreamonHal hal = halResponder.getFreamonHal();
                    l.trace("Attempting lock on the Freamon instance.");
                    synchronized (hal) {
                        hal.save();
                    }

                    event.getBot().sendMessage(event.getUser(), "Bye!");
                    client.doDisconnect();
                    return "Bye!";
                } else {
                    return "You are not authenticated.";
                }
            }
        };

        final PrivateCommandHandler greetChance = new PriviledgedNumberCommandHandler(0, Configuration.CHANCE_MAX) {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final int param) {
                Configuration.setGreetChance(param);
                return "Set the new greeting chance to " + param + "%.";
            }
        };

        final PrivateCommandHandler publicChance = new PriviledgedNumberCommandHandler(0, Configuration.CHANCE_MAX) {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final int param) {
                Configuration.setPubResponseChance(param);
                return "Set the new public response chance to " + param + "%.";
            }
        };

        final PrivateCommandHandler pingChance = new PriviledgedNumberCommandHandler(0, Configuration.CHANCE_MAX) {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final int param) {
                Configuration.setPubResponseChance(param);
                return "Set the new pinged response chance to " + param + "%.";
            }
        };

        final PrivateCommandHandler cooldown = new PriviledgedNumberCommandHandler(Configuration.MIN_COOLDOWN,
                Configuration.MAX_COOLDOWN) {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final int param) {
                Configuration.setCooldown(param);
                return "Set the new cooldown to " + param + "s.";
            }
        };

        final PrivateCommandHandler minDelay = new PriviledgedNumberCommandHandler(Configuration.MIN_MIN_DELAY,
                Configuration.MAX_MIN_DELAY) {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final int param) {
                Configuration.setMinDelay(param);
                return "Set the new minimum delay to " + param + "ms.";
            }
        };

        final PrivateCommandHandler maxDelay = new PriviledgedNumberCommandHandler(Configuration.MIN_MAX_DELAY,
                Configuration.MAX_MAX_DELAY) {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final int param) {
                if (Configuration.getMinDelay() < param) {
                    Configuration.setMaxDelay(param);
                    return "Set the new maximum delay to " + param + "ms.";
                } else {
                    return "The maximum delay needs to be larger than the current minimum delay: "
                            + Configuration.getMinDelay() + "ms.";
                }
            }
        };

        final PrivateCommandHandler brainSwitch = new PriviledgedCommandHandler() {

            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final String param) {
                final File brain = new File(param);
                if (!brain.exists() || !brain.canRead()) {
                    return "The brain file either doesn't exist or is not readable: " + brain;
                }

                event.getBot()
                        .sendMessage(
                                event.getUser(),
                                "Switching the brain to " + brain
                                        + ". It might take a while. I will notify you when I'm done.");
                final FreamonHal hal = halResponder.getFreamonHal();
                FreamonHal newHal = null;
                l.trace("Attempting lock on the Freamon instance.");
                synchronized (hal) {
                    l.trace("Got lock on the Freamon instance.");
                    hal.save();

                    try {
                        newHal = SerializedFreamonHalTools.read(brain);
                    } catch (final Exception e) {
                        return "Error while reading new brain " + e.getMessage();
                    }

                    halResponder.setFreamonHal(newHal);
                }

                client.removeSubscriber(hal);
                client.addSubscriber(newHal);

                return "Done switching to the new brain " + brain;
            }
        };

        final PublicCommandHandler tireHandler = new OpsCommandHandler() {
            @Override
            public String handleOpsCommand(final MessageEvent<Network> event) {
                client.becomeTired(event.getChannel());
                return event.getUser().getNick() + ": ok i go now";
            }

            @Override
            public String handleNonOpsCommand(final MessageEvent<Network> event) {
                return event.getUser().getNick() + ": no";
            }
        };

        final PublicCommandHandler untireHandler = new OpsCommandHandler() {
            @Override
            public String handleOpsCommand(final MessageEvent<Network> event) {
                client.becomeTired(event.getChannel(), -1);
                return ":D";
            }

            @Override
            public String handleNonOpsCommand(final MessageEvent<Network> event) {
                return null;
            }
        };

        final PrivateCommandHandler privateUntireHandler = new PriviledgedCommandHandler() {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final String param) {
                client.becomeTired(param, -1);
                return ":D";
            }
        };

        final PublicCommandHandler wisdomHandler = new PublicCommandHandler() {
            @Override
            public String handleCommand(final MessageEvent<Network> event) {
                return Colors.BOLD
                        + halResponder.respondPublic(new MessageEvent<Network>(event.getBot(), event.getChannel(),
                                event.getUser(), "are"));
            }
        };

        final PrivateCommandHandler messageInjectHandler = new PriviledgedCommandHandler() {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final String param) {
                final int firstSpace = param.indexOf(' ');
                if (firstSpace == -1) {
                    return "Invalid parameters";
                } else {
                    final String channel = param.substring(0, firstSpace);
                    final String message = param.substring(firstSpace + 1);
                    if (event.getBot().channelKnown(channel)) {
                        event.getBot().getChannel(channel).sendMessage(message);
                        return "Message sent to " + channel;
                    } else {
                        return "Not in channel " + channel;
                    }
                }
            }
        };

        final PrivateCommandHandler qAuthHandler = new PriviledgedCommandHandler() {
            @Override
            public String handleAuthedCommand(final PrivateMessageEvent<Network> event, final String param) {
                event.getBot().getUser("Q@CServe.quakenet.org").sendMessage("AUTH " + param);
                return "Q auth requested";
            }
        };

        privateHandlers.put("!auth", authHandler);
        privateHandlers.put("!nick", nickChangeHandler);
        privateHandlers.put("!join", activeJoinHandler);
        privateHandlers.put("!lurk", lurkHandler);
        privateHandlers.put("!polite", politeHandler);
        privateHandlers.put("!networkadd", networkAdditionHandler);
        privateHandlers.put("!networkdel", networkRemovalHandler);
        privateHandlers.put("!quit", quitHandler);
        privateHandlers.put("!part", partHandler);
        privateHandlers.put("!pubchance", publicChance);
        privateHandlers.put("!pingchance", pingChance);
        privateHandlers.put("!greetchance", greetChance);
        privateHandlers.put("!cooldown", cooldown);
        privateHandlers.put("!mindelay", minDelay);
        privateHandlers.put("!maxdelay", maxDelay);
        privateHandlers.put("!brainswitch", brainSwitch);
        privateHandlers.put("!plscome", privateUntireHandler);
        privateHandlers.put("!inject", messageInjectHandler);
        privateHandlers.put("!qauth", qAuthHandler);

        publicHandlers.put("!plsgo", tireHandler);
        publicHandlers.put("!plscome", untireHandler);
        publicHandlers.put("!freamonwisdom", wisdomHandler);
    }

    @Override
    public String respondPublic(final MessageEvent<Network> event) {
        final String command = extractCommand(event.getMessage());
        if (command == null) {
            return null;
        }

        l.trace("Attempting to handle public command: " + command);
        if (publicHandlers.containsKey(command)) {
            l.debug("Handling public command: " + command + " by user " + event.getUser());
            return publicHandlers.get(command).handleCommand(event);
        } else {
            return null;
        }
    }

    @Override
    public synchronized String respondPrivate(final PrivateMessageEvent<Network> event) {
        final String command = extractCommand(event.getMessage());
        if (command == null) {
            return null;
        }

        l.trace("Attempting to handle private command: " + command);
        if (privateHandlers.containsKey(command)) {
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
    private void auth(final String id) {
        authed.add(id);
        l.debug("Authed user: " + id);
    }

    /**
     * Remember the user by the given auth ID to not be authed anymore.
     *
     * @param id
     *            The auth ID of the user.
     *
     * @return True iff the ID was present and is now removed
     */
    private boolean deauth(final String id) {
        if (authed.remove(id)) {
            l.debug("Deauthed user: " + id);
            return true;
        } else {
            return false;
        }
    }

    /**
     * If an authenticated user changes nicknames, we will consider him no longer authenticated.
     */
    @Override
    public synchronized void onNickChange(final NickChangeEvent<Network> event) {
        final String id = getAuthID(event.getOldNick(), event.getBot());
        if (deauth(id)) {
            l.info(event.getUser() + " changed from " + event.getOldNick() + " to " + event.getNewNick()
                    + ". Deauthing him.");
        }
    }

    /**
     * If an authenticated user quits, we will consider him no longer authenticated.
     */
    @Override
    public synchronized void onQuit(final QuitEvent<Network> event) {
        final String id = getAuthID(event.getUser().getNick(), event.getBot());
        if (deauth(id)) {
            l.info(id + " has quit. Deauthing him.");
        }
    }

    /**
     * If an authenticated user parts, we will consider him no longer authenticated.
     */
    @Override
    public synchronized void onPart(final PartEvent<Network> event) {
        final String id = getAuthID(event.getUser().getNick(), event.getBot());
        if (deauth(id)) {
            l.info(id + " has quit. Deauthing him.");
        }
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
    private String getAuthID(final String name, final Network network) {
        return name + "@" + network.getUrl();
    }

    /**
     * Find the command requested in the given message. A command is considered a word that follows a leading !.
     *
     * @param message
     *            The message to scan through.
     * @return The command found, including the !. null if none was found.
     */
    private String extractCommand(final String message) {
        if (message.startsWith("!")) {
            final String[] parts = message.split(" ");
            if (parts.length > 0) {
                return parts[0];
            }
        }

        return null;
    }
}

package de.blanksteg.freamon.irc;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.Listener;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PingEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.managers.ListenerManager;

import de.blanksteg.freamon.Configuration;

/**
 * The IRCClient class offers semi-advanced functionality to manage connections to multiple IRC networks in which
 * multiple channels can be joined either as an active or passive member. When a channel is joined or another user joins
 * a channel the IRCClient queries a stored instance of an {@link GreetingsGenerator} to possibly generate a greeting
 * appropriate for the event. Similarly, it checks a {@link ResponseGenerator} for received messages and, if possible,
 * responds using them.
 *
 * Network management is done using {@link Network} instances that are collected in this class. Networks can be added
 * and removed after the IRCClient instance has been created. If the client is already connected and a network is added
 * / removed, the client will connect / disconnect accordingly.
 *
 * For additional monitoring, classes implementing {@link Listener} can register themselves as subscribers to this
 * client. Subscribers will be registered as event listeners to every network managed by this client.
 *
 * @author Marc Müller
 */
public class IRCClient extends ListenerAdapter<Network> {
    /** The log4j logger to print log output to. */
    private static final Logger l = Logger.getLogger("de.blanksteg.freamon.irc");

    /** Mapping of hostnames to networks this instance manages. */
    private final Map<String, Network> networks = new HashMap<String, Network>();
    /** List of listeners that want to listen on any network managed by this client. */
    private final List<Listener<Network>> subscribers = new LinkedList<Listener<Network>>();
    /** List of listeners that want to listen on any network managed by this client. */
    private final Map<String, Long> tiredChannels = new HashMap<String, Long>();

    /** Whether or not we are currently connected to the networks. */
    private boolean connected;
    /** The greeter to use for possible greeting. */
    private GreetingsGenerator greeter;
    /** The responder to use for possible respondes. */
    private ResponseGenerator responder;

    /**
     * Adds a subscriber to this client.
     *
     * @param subscriber
     *            The subscriber to add.
     */
    public synchronized void addSubscriber(final Listener<Network> subscriber) {
        l.trace("Adding subscriber: " + subscriber);
        subscribers.add(subscriber);
    }

    /**
     * Removes a subscriber from this client.
     *
     * @param subscriber
     *            The subscriber to remove.
     */
    public synchronized void removeSubscriber(final Listener<Network> subscriber) {
        l.trace("Removing subscriber: " + subscriber);
        subscribers.remove(subscriber);
    }

    /**
     * Connect to the networks managed by this client.
     *
     * @throws IOException
     * @throws IrcException
     * @throws UnsupportedOperationException
     *             If the client is already connected.
     */
    public synchronized void doConnect() throws IOException, IrcException {
        if (connected) {
            throw new UnsupportedOperationException("You are already connected.");
        }
        l.debug("Starting connection.");

        for (final String host : networks.keySet()) {
            l.info("Connecting to " + host);
            final Network network = networks.get(host);
            network.doConnect();
        }

        l.debug("Done with connection start.");
        connected = true;
    }

    /**
     * Disconnect from the networks managed by this client.
     *
     * @throws UnsupportedOperationException
     *             If the client is not connected.
     */
    public synchronized void doDisconnect() {
        if (!connected) {
            throw new UnsupportedOperationException("You are not even connected.");
        }
        l.debug("Starting disconnection.");

        for (final String host : networks.keySet()) {
            l.info("Disconnecting from " + host);
            final Network network = networks.get(host);
            network.doDisconnect();
        }

        l.debug("Done with disconnection.");
        connected = false;
    }

    /**
     * Adds a new IRC network to be managed by this client. The network is only added if it is not known by this client.
     * If the network is added and this client is considered connected, it will automatically connect to the network.
     *
     * @param newNetwork
     *            The network to manage.
     * @return Whether or not the network was added.
     * @throws IOException
     * @throws IrcException
     * @throws IllegalArgumentException
     *             If the network is null or doesn't have a host set.
     */
    public synchronized boolean addNetwork(final Network newNetwork) throws IOException, IrcException {
        if (newNetwork == null || newNetwork.getUrl() == null) {
            throw new IllegalArgumentException("Network or its host was null.");
        }

        final String host = newNetwork.getUrl();
        l.trace("Adding a network for " + host + ".");
        if (networks.containsKey(host)) {
            l.trace("Network " + host + " already known. Skipping.");
            return false;
        } else {
            l.trace("Adding subscribers and this client as listeners to the network.");
            final ListenerManager<? extends PircBotX> manager = newNetwork.getListenerManager();
            for (final Listener<Network> subscriber : subscribers) {
                manager.addListener(subscriber);
            }

            manager.addListener(this);
            networks.put(host, newNetwork);

            if (connected) {
                l.trace("Client is online so connection is established to " + host);
                newNetwork.doConnect();
            } else {
                l.trace("Client is offline so no connection is made to " + host);
            }

            return true;
        }
    }

    /**
     * Removes an IRC network managed by this client. The network is only removed if it is known by this client. If the
     * network is removed and this client is considered connected, it will automatically disconnect from the network.
     *
     * @param newNetwork
     *            The network to remove's host.
     * @return Whether or not the network was removed.
     * @throws IOException
     * @throws IrcException
     * @throws IllegalArgumentException
     *             If the network host is null or empty
     */
    public synchronized boolean removeNetwork(final String toRemove) {
        if (toRemove == null || toRemove.length() < 1) {
            throw new IllegalArgumentException("Network host was null or empty.");
        }
        l.trace("Deleting the network " + toRemove);

        if (networks.containsKey(toRemove)) {
            l.trace("Got an instance for " + toRemove + ". Removing it.");
            return this.removeNetwork(networks.get(toRemove));
        } else {
            l.trace("Unknown network " + toRemove + ". Skipping.");
            return false;
        }
    }

    /**
     * Removes an IRC network managed by this client. The network is only removed if it is known by this client. If the
     * network is removed and this client is considered connected, it will automatically disconnect from the network.
     *
     * @param newNetwork
     *            The network to remove
     * @return Whether or not the network was removed.
     * @throws IOException
     * @throws IrcException
     * @throws IllegalArgumentException
     *             If the network host is null
     */
    public synchronized boolean removeNetwork(final Network toRemove) {
        if (toRemove == null) {
            throw new IllegalArgumentException("Network was null.");
        }
        l.trace("Deleting the network " + toRemove);

        if (networks.containsKey(toRemove.getUrl())) {
            if (connected) {
                l.trace("Since IRC client is online we're disconnecting from " + toRemove.getUrl());
                toRemove.doDisconnect();
            }

            networks.remove(toRemove);
            l.trace("Removing subscribers and the client as listeners on " + toRemove.getUrl());
            for (final Listener<Network> subscriber : subscribers) {
                toRemove.getListenerManager().removeListener(subscriber);
            }
            toRemove.getListenerManager().removeListener(this);
            return true;
        } else {
            l.trace("Network " + toRemove.getUrl() + " is unknown. Skipping.");
            return false;
        }
    }

    /**
     * Get this client's current greeter.
     *
     * @return The greeter used to generate greetings.
     */
    public synchronized GreetingsGenerator getGreeter() {
        return greeter;
    }

    /**
     * Set this client's current greeter.
     *
     * @param greeter
     *            The new greeter.
     */
    public synchronized void setGreeter(final GreetingsGenerator greeter) {
        if (greeter == null) {
            throw new IllegalArgumentException("Given greeter was null.");
        }

        this.greeter = greeter;
    }

    /**
     * Get this client's response generator.
     *
     * @return The responder used to generate respondes.
     */
    public synchronized ResponseGenerator getResponder() {
        return responder;
    }

    /**
     * Set this client's current responder.
     *
     * @param responder
     *            The new responder to use.
     */
    public synchronized void setResponder(final ResponseGenerator responder) {
        if (responder == null) {
            throw new IllegalArgumentException("Given responder was null.");
        }

        this.responder = responder;
    }

    // Various IRC event handlers
    @Override
    public synchronized void onJoin(final JoinEvent<Network> event) {
        if (event == null) {
            return;
        }

        l.trace("Got a join event: " + event);
        final Channel channel = event.getChannel();
        if (greeter.shouldGreet(event) && !isTired(channel)) {
            l.debug("Greeter gave the go-ahead on greeting. Generating a greeting now.");
            final Network target = event.getBot();
            final String greeting = greeter.generateGreeting(event);
            l.debug("Sending greeting in " + channel.getName() + ": " + greeting);
            target.sendMessage(channel, greeting);
        }
    }

    @Override
    public synchronized void onMessage(final MessageEvent<Network> event) {
        if (event == null) return;

        final Network target = event.getBot();
        final Channel channel = event.getChannel();
        final String senderNick = event.getUser().getNick();
        final boolean botMentioned = StringUtils.containsIgnoreCase(event.getMessage(), target.getNick());
        boolean politeBlock = false;
        boolean wasTired = false;

        l.trace("Got a public message in " + channel.getName());

        if (target.isIgnored(senderNick)) {
            l.debug("Ignoring user " + senderNick);
            return;
        }

        // We may only respond if we're in an active or polite channel
        if (target.isPassiveChannel(channel)) {
            l.debug(channel.getName() + " is not an active channel. Not responding.");
            return;
        }

        if (target.isPoliteChannel(channel)) {
            if (!botMentioned) {
                // When we're polite we should be mentioned to reply
                politeBlock = true;
                l.debug(channel.getName() + " is not a polite channel but we're not mentioned.");

            } else if (isStreamLive(channel)) {
                // When we're polite and someone is streaming, don't reply
                politeBlock = true;
                l.debug(channel.getName() + " currently has a live stream.");
            }
        }

        if (isTired(channel)) {
            // When we're tired of a channel, we don't reply but can be woken up
            wasTired = true;
            l.debug(channel.getName() + " is currently tiring me.");
        }

        // When the event is a command, we might not cancel responding
        if ((politeBlock || wasTired) && !event.getMessage().startsWith("!")) {
            return;
        }

        try {
            final String message = responder.respondPublic(event);

            // When we were and still are tired of a channel, don't reply
            if (wasTired && isTired(channel)) {
                l.debug(channel.getName() + " is currently tiring me. Not responding.");
                return;
            }

            // Check if a reply was given
            if (message == null) {
                l.debug("Message was null.");
                return;
            }

            l.debug("Responding in " + channel.getName() + " with: " + message);
            target.sendMessage(channel, message);

        } catch (final Exception ex) {
            // Manually catch exceptions as pircbotx ignores them all
            l.warn("Exception while responding", ex);
        }
    }

    @Override
    public synchronized void onPing(final PingEvent<Network> event) {
        l.trace("Got a ping request: " + event);
        final Network target = event.getBot();
        final User source = event.getUser();
        target.sendMessage(source,
                "I'm a brainless grunt -- don't trust me! My serial number is: " + Math.abs(source.hashCode()));
    }

    @Override
    public synchronized void onPrivateMessage(final PrivateMessageEvent<Network> event) {
        if (event == null) {
            return;
        }

        if (event.getBot().isIgnored(event.getUser().getNick())) {
            return;
        }

        l.trace("Got a private message from " + event.getUser());
        l.trace("Responder gave the go-ahead on responding to " + event.getUser() + ". Generating a response now.");
        final Network target = event.getBot();
        final User user = event.getUser();
        final String message = responder.respondPrivate(event);
        if (message != null) {
            l.trace("Sending response to " + event.getUser().getNick() + ": " + message);
            target.sendMessage(user, message);
        } else {
            l.trace("Message was null.");
        }
    }

    @Override
    public synchronized void onDisconnect(final DisconnectEvent<Network> event) {
        if (connected) {
            l.info("Lost connection to: " + event.getBot().getUrl() + ". Attempting reconnection.");
            final Network network = event.getBot();
            final Thread reconnector = new Thread() {
                @Override
                public void run() {
                    for (int i = 0; i < 32; i++) {
                        try {
                            network.doConnect();
                            return;
                        } catch (final Exception e) {}
                    }
                }
            };

            reconnector.setDaemon(true);
            reconnector.start();
        }
    }

    /**
     * Checks if a stream is currently playing in the channel based on the topic. This method supports dopelives.com
     * topic formats only.
     *
     * @param channel
     *            The channel to check for
     *
     * @return True if a stream is playing, false otherwise
     */
    private boolean isStreamLive(final Channel channel) {
        return channel.getTopic().matches("\\s*Streamer:\\s*[^\\s|].*");
    }

    /**
     * Become tired of a channel so that we won't talk in it for a while.
     *
     * @param channel
     *            The channel to become tired of
     */
    public void becomeTired(final Channel channel) {
        becomeTired(channel.getName());
    }

    /**
     * Become tired of a channel so that we won't talk in it for a while.
     *
     * @param channel
     *            The channel to become tired of
     */
    public void becomeTired(final String channel) {
        becomeTired(channel, Configuration.getTirePeriod());
    }

    /**
     * Become tired of a channel so that we won't talk in it for a while.
     *
     * @param channel
     *            The channel to become tired of
     * @param tirePeriod
     *            How long we should become tired of a channel in seconds
     */
    public void becomeTired(final Channel channel, final long tirePeriod) {
        becomeTired(channel.getName(), tirePeriod);
    }

    /**
     * Become tired of a channel so that we won't talk in it for a while.
     *
     * @param channel
     *            The channel to become tired of
     * @param tirePeriod
     *            How long we should become tired of a channel in seconds
     */
    public void becomeTired(final String channel, final long tirePeriod) {
        tiredChannels.put(channel, System.currentTimeMillis() + tirePeriod * 1000);
    }

    /**
     * Checks if we're currently tired of talking in a channel and shouldn't do so.
     *
     * @param channel
     *            The channel to check tire for
     *
     * @return True iff we shouldn't talk in the given channel
     */
    public boolean isTired(final Channel channel) {
        return isTired(channel.getName());
    }

    /**
     * Checks if we're currently tired of talking in a channel and shouldn't do so.
     *
     * @param channel
     *            The channel to check tire for
     *
     * @return True iff we shouldn't talk in the given channel
     */
    public boolean isTired(final String channel) {
        final Long tireLimit = tiredChannels.get(channel);
        if (tireLimit != null) {
            if (tireLimit > System.currentTimeMillis()) {
                return true;
            } else {
                tiredChannels.remove(channel);
            }
        }

        return false;
    }
}

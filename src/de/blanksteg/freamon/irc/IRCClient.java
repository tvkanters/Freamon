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
    private List<Listener<Network>> subscribers = new LinkedList<Listener<Network>>();

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
    public synchronized void addSubscriber(Listener<Network> subscriber) {
        l.trace("Adding subscriber: " + subscriber);
        this.subscribers.add(subscriber);
    }

    /**
     * Removes a subscriber from this client.
     * 
     * @param subscriber
     *            The subscriber to remove.
     */
    public synchronized void removeSubscriber(Listener<Network> subscriber) {
        l.trace("Removing subscriber: " + subscriber);
        this.subscribers.remove(subscriber);
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
        if (this.connected) {
            throw new UnsupportedOperationException("You are already connected.");
        }
        l.debug("Starting connection.");

        for (String host : this.networks.keySet()) {
            l.info("Connecting to " + host);
            Network network = this.networks.get(host);
            network.doConnect();
        }

        l.debug("Done with connection start.");
        this.connected = true;
    }

    /**
     * Disconnect from the networks managed by this client.
     * 
     * @throws UnsupportedOperationException
     *             If the client is not connected.
     */
    public synchronized void doDisconnect() {
        if (!this.connected) {
            throw new UnsupportedOperationException("You are not even connected.");
        }
        l.debug("Starting disconnection.");

        for (String host : this.networks.keySet()) {
            l.info("Disconnecting from " + host);
            Network network = this.networks.get(host);
            network.doDisconnect();
        }

        l.debug("Done with disconnection.");
        this.connected = false;
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
    public synchronized boolean addNetwork(Network newNetwork) throws IOException, IrcException {
        if (newNetwork == null || newNetwork.getUrl() == null) {
            throw new IllegalArgumentException("Network or its host was null.");
        }

        String host = newNetwork.getUrl();
        l.trace("Adding a network for " + host + ".");
        if (this.networks.containsKey(host)) {
            l.trace("Network " + host + " already known. Skipping.");
            return false;
        } else {
            l.trace("Adding subscribers and this client as listeners to the network.");
            ListenerManager<? extends PircBotX> manager = newNetwork.getListenerManager();
            for (Listener<Network> subscriber : this.subscribers) {
                manager.addListener(subscriber);
            }

            manager.addListener(this);
            this.networks.put(host, newNetwork);

            if (this.connected) {
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
    public synchronized boolean removeNetwork(String toRemove) {
        if (toRemove == null || toRemove.length() < 1) {
            throw new IllegalArgumentException("Network host was null or empty.");
        }
        l.trace("Deleting the network " + toRemove);

        if (this.networks.containsKey(toRemove)) {
            l.trace("Got an instance for " + toRemove + ". Removing it.");
            return this.removeNetwork(this.networks.get(toRemove));
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
    public synchronized boolean removeNetwork(Network toRemove) {
        if (toRemove == null) {
            throw new IllegalArgumentException("Network was null.");
        }
        l.trace("Deleting the network " + toRemove);

        if (this.networks.containsKey(toRemove.getUrl())) {
            if (this.connected) {
                l.trace("Since IRC client is online we're disconnecting from " + toRemove.getUrl());
                toRemove.doDisconnect();
            }

            this.networks.remove(toRemove);
            l.trace("Removing subscribers and the client as listeners on " + toRemove.getUrl());
            for (Listener<Network> subscriber : this.subscribers) {
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
    public synchronized void setGreeter(GreetingsGenerator greeter) {
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
    public synchronized void setResponder(ResponseGenerator responder) {
        if (responder == null) {
            throw new IllegalArgumentException("Given responder was null.");
        }

        this.responder = responder;
    }

    // Various IRC event handlers
    public synchronized void onJoin(JoinEvent<Network> event) {
        if (event == null) {
            return;
        }

        l.trace("Got a join event: " + event);
        if (this.greeter.shouldGreet(event)) {
            l.debug("Greeter gave the go-ahead on greeting. Generating a greeting now.");
            Network target = event.getBot();
            Channel channel = event.getChannel();
            String greeting = this.greeter.generateGreeting(event);
            l.debug("Sending greeting in " + channel.getName() + ": " + greeting);
            target.sendMessage(channel, greeting);
        }
    }

    public synchronized void onMessage(MessageEvent<Network> event) {
        if (event == null) {
            return;
        }

        if (event.getBot().isIgnored(event.getUser().getNick())) {
            return;
        }

        l.trace("Got a public message in " + event.getChannel().getName());
        Network target = event.getBot();
        Channel channel = event.getChannel();
        final boolean botMentioned = StringUtils.containsIgnoreCase(event.getMessage(), target.getNick());
        final boolean isPolite = target.isPoliteChannel(channel);

        // We may chat if we're in an active channel or when we're polite and mentioned
        if (target.isActiveChannel(channel) || (botMentioned && isPolite)) {

            // Though when we're polite and someone is streaming, don't reply
            if (isPolite && isStreamLive(channel)) {
                l.debug(channel.getName() + " currently has a live stream. Not responding.");

            } else {
                try {
                    String message = this.responder.respondPublic(event);
                    if (message != null) {
                        l.debug("Responding in " + channel.getName() + " with: " + message);
                        target.sendMessage(channel, message);
                    } else {
                        l.debug("Message was null.");
                    }
                } catch(final Exception ex) {
                    // Manually catch exceptions as pircbotx ignores them all
                    l.warn("Exception while responding", ex);
                }
            }
        } else {
            l.debug(channel.getName() + " is not an active channel. Not responding.");
        }
    }

    public synchronized void onPing(PingEvent<Network> event) {
        l.trace("Got a ping request: " + event);
        Network target = event.getBot();
        User source = event.getUser();
        target.sendMessage(source,
                "I'm a brainless grunt -- don't trust me! My serial number is: " + Math.abs(source.hashCode()));
    }

    public synchronized void onPrivateMessage(PrivateMessageEvent<Network> event) {
        if (event == null) {
            return;
        }

        if (event.getBot().isIgnored(event.getUser().getNick())) {
            return;
        }

        l.trace("Got a private message from " + event.getUser());
        l.trace("Responder gave the go-ahead on responding to " + event.getUser() + ". Generating a response now.");
        Network target = event.getBot();
        User user = event.getUser();
        String message = this.responder.respondPrivate(event);
        if (message != null) {
            l.trace("Sending response to " + event.getUser().getNick() + ": " + message);
            target.sendMessage(user, message);
        } else {
            l.trace("Message was null.");
        }
    }

    public synchronized void onDisconnect(DisconnectEvent<Network> event) {
        if (this.connected) {
            l.info("Lost connection to: " + event.getBot().getUrl() + ". Attempting reconnection.");
            final Network network = event.getBot();
            Thread reconnector = new Thread() {
                public void run() {
                    for (int i = 0; i < 32; i++) {
                        try {
                            network.doConnect();
                            return;
                        } catch (Exception e) {}
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
}

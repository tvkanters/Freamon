package de.blanksteg.freamon.irc;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.exception.IrcException;

import de.blanksteg.freamon.Configuration;

/**
 * A network stores the basic information needed to connect to an IRC network. Additionally, it maintains two sets of
 * active and passive channels. Active channels are ones the bot is allowed to talk in, while passive channels are ones
 * he can only learn from. The distinction between them makes little difference to this class and is mainly used by the
 * {@link IRCClient}.
 * 
 * @author Marc Müller
 */
public class Network extends PircBotX {
    /** The log4j logger to output logging info to. */
    private final Logger l = Logger.getLogger("de.blanksteg.freamon.networks");

    /** This network's target host. */
    private final String url;
    /** The port to connect on. */
    private final int port;
    /** The server password. */
    private final String serverPass;

    /** The possible nickname choices to iterate through. The first that is available is taken. */
    private final String[] nickNames;
    /** The client's user name on this network. */
    private String userName;
    /** The client's real name on this network. */
    private String realName;
    /** The client's name on this network. */
    private String clientName;

    /** Message to post upon leaving channels or the server. */
    private final String quitMessage = ">Freamon leaves";

    /** Set of channels we are currently active in. */
    private final Set<String> activeChannels = new HashSet<String>();
    /** Set of channels we are currently lurking in. */
    private final Set<String> passiveChannels = new HashSet<String>();
    /** Set of people/channels to ignore. */
    private final Set<String> ignore = new HashSet<String>();

    /**
     * Instantiate a new network for the given IRC properties and server password.
     * 
     * @param newUrl
     *            The host to connect to.
     * @param newPort
     *            Target port to connect on.
     * @param newNickNames
     *            Array of nickname preferences.
     * @param newUserName
     *            The supposed user name on the network.
     * @param newRealName
     *            The supposed real name on the network.
     * @param newClientName
     *            The supposed client's name.
     * @param newServerPass
     *            The supposed server password name.
     * @throws IllegalArgumentException
     *             If either of these values is null or the port is outside of legal range.
     */
    public Network(String newUrl, int newPort, String[] newNickNames, String newUserName, String newRealName,
            String newClientName, String newServerPass) {
        if (newUrl == null || newNickNames == null || newUserName == null || newRealName == null
                || newClientName == null) {
            throw new IllegalArgumentException("Null was passed as a property to the network: (host = " + newUrl
                    + ", nickNames = " + newNickNames + ", user name = " + newUserName + ", real name = " + newRealName
                    + ", client = " + newClientName + ")");
        }

        if (newPort < 1 || newPort > 65535) {
            throw new IllegalArgumentException("Port was outside of the range of 1 to 65535: " + newPort);
        }

        this.url = newUrl;
        this.port = newPort;
        this.nickNames = newNickNames;
        this.userName = newUserName;
        this.realName = newRealName;
        this.clientName = newClientName;
        this.serverPass = newServerPass;
    }

    /**
     * Instantiate a new network for the given IRC properties.
     * 
     * @param newUrl
     *            The host to connect to.
     * @param newPort
     *            Target port to connect on.
     * @param newNickNames
     *            Array of nickname preferences.
     * @param newUserName
     *            The supposed user name on the network.
     * @param newRealName
     *            The supposed real name on the network.
     * @param newClientName
     *            The supposed client's name.
     * @throws IllegalArgumentException
     *             If either of these values is null or the port is outside of legal range.
     */
    public Network(String newUrl, int newPort, String[] newNickNames, String newUserName, String newRealName,
            String newClientName) {
        this(newUrl, newPort, newNickNames, newUserName, newRealName, newClientName, Configuration.DEFAULT_PASS);
    }

    /**
     * Adds a channel to actively participate in to this network. If the channel is already joined either actively or
     * passively, this request is ignored. If we are already connected the channel is entered immediately, otherwise it
     * will be remembered for when we do connect.
     * 
     * @param toAdd
     *            The channel's name.
     * @return Whether or not the channel was added to the set of active channels.
     * @throws IllegalArgumentException
     *             If the channel name is null, shorter than two or does not match {@link Configuration#CHANNEL_MATCH}.
     */
    public synchronized boolean addActiveChannel(String toAdd) {
        if (toAdd == null || toAdd.length() < 2) {
            throw new IllegalArgumentException("Channel name was null or empty.");
        }

        if (!toAdd.matches(Configuration.CHANNEL_MATCH)) {
            throw new IllegalArgumentException("Not a proper channel name: " + toAdd);
        }

        l.trace("Adding an active channel to " + this.url + ": " + toAdd);
        if (this.channelKnown(toAdd)) {
            l.trace("Channel " + toAdd + " was already known in " + this.url + ". Not adding.");
            return false;
        } else {
            this.activeChannels.add(toAdd);

            if (this.isConnected()) {
                l.trace("We're connected to " + this.url + " so we'll join the new channel " + toAdd);
                this.joinChannel(toAdd);
            }

            return true;
        }
    }

    /**
     * Removes a channel from the set of channels to be active in. If we were never in that channel, nothing is done. If
     * we are connected to the network, the client parts from the channel with {@link Network#quitMessage} as the
     * reason.
     * 
     * @param channelName
     *            The name of the channel to remove.
     * @return True iff the channel was removed.
     * @throws IllegalArgumentException
     *             If null is passed as the channel name.
     */
    public synchronized boolean removeActiveChannel(String channelName) {
        if (channelName == null) {
            throw new IllegalArgumentException("Channel name was null or empty.");
        }

        l.trace("Removing an active channel from " + this.url + ": " + channelName);
        boolean ret = this.activeChannels.remove(channelName);

        if (ret && this.isConnected()) {
            l.trace("We were connected to " + this.url + ". Leaving channel: " + channelName);
            this.leaveChannel(channelName);
        }

        return ret;
    }

    /**
     * Adds a channel to lurk in to this network. If the channel is already joined either actively or passively, this
     * request is ignored. If we are already connected the channel is entered immediately, otherwise it will be
     * remembered for when we do connect.
     * 
     * @param channelName
     *            The channel's name.
     * @return Whether or not the channel was added to the set of passive channels.
     * @throws IllegalArgumentException
     *             If the channel name is null, shorter than two or does not match {@link Configuration#CHANNEL_MATCH}.
     */
    public synchronized boolean addPassiveChannel(String channelName) {
        if (channelName == null) {
            throw new IllegalArgumentException("Channel name was null.");
        }
        l.trace("Adding a passive channel to " + this.url + ": " + channelName);

        if (this.channelKnown(channelName)) {
            l.trace("Channel " + channelName + " was already known in " + this.url + ". Not adding.");
            return false;
        } else {
            this.passiveChannels.add(channelName);

            if (this.isConnected()) {
                l.trace("We're connected to " + this.url + " so we'll join the new channel " + channelName);
                this.joinChannel(channelName);
            }

            return true;
        }
    }

    /**
     * Removes a channel from the set of channels to be passive in. If we were never in that channel, nothing is done.
     * If we are connected to the network, the client parts from the channel with {@link Network#quitMessage} as the
     * reason.
     * 
     * @param channelName
     *            The name of the channel to remove.
     * @return True iff the channel was removed.
     * @throws IllegalArgumentException
     *             If null is passed as the channel name.
     */
    public synchronized boolean removePassiveChannel(String channelName) {
        if (channelName == null) {
            throw new IllegalArgumentException("Channel name was null.");
        }
        l.trace("Removing a passive channel from " + this.url + ": " + channelName);

        if (this.channelKnown(channelName)) {
            this.passiveChannels.remove(channelName);

            if (this.isConnected()) {
                l.trace("We were connected to " + this.url + ". Leaving channel: " + channelName);
                this.leaveChannel(channelName);
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * Connect to this network and join all previously known active and passive channel.
     * 
     * @throws IOException
     * @throws IrcException
     * @throws UnsupportedOperationException
     *             When we already are connected.
     */
    public synchronized void doConnect() throws IOException, IrcException {
        if (this.isConnected()) {
            throw new UnsupportedOperationException("Already connected to this network.");
        }

        l.info("Establishing connection to: " + this.url);
        this.setVersion(this.clientName);
        l.trace("Set version to: " + this.clientName + " on " + this.url);
        this.setUserName(this.userName);
        this.setLogin(this.userName);
        l.trace("Set user name and login to: " + this.userName + " on " + this.url);
        this.setRealName(this.realName);
        l.trace("Set real name to: " + this.realName + " on " + this.url);

        for (int i = 0; i < this.nickNames.length; i++) {
            String nickName = this.nickNames[i];
            try {
                this.setName(nickName);
                l.debug("Attempting to establish connection to " + this.url + " with nickname: " + nickName);
                if (this.serverPass != null) {
                    this.connect(this.url, this.port, this.serverPass);
                } else {
                    this.connect(this.url, this.port);
                }
                break;
            } catch (IOException e) {
                if (i == this.nickNames.length - 1) {
                    l.trace("Nickname " + nickName + " is already used on " + this.url + ". Reattempting.");
                    throw e;
                } else {
                    continue;
                }
            }
        }

        for (String channel : this.passiveChannels) {
            l.info("Joining passive channel on " + this.url + ": " + channel);
            this.joinChannel(channel);
        }

        for (String channel : this.activeChannels) {
            l.info("Joining active channel on " + this.url + ": " + channel);
            this.joinChannel(channel);
        }
    }

    /**
     * Disconnect from the network giving {@link Network#quitMessage} as the reason.
     */
    public synchronized void doDisconnect() {
        l.info("Disconnecting from " + this.url);
        if (this.isConnected()) {
            this.quitServer(this.quitMessage);
        } else {
            throw new UnsupportedOperationException("You are not connected to the network.");
        }
    }

    /**
     * Become passive in the given channel. It's illegal to become passive in a channel not even joined.
     * 
     * @param channelName
     *            The channel to become passive in.
     * @return Whether or not we switched to be passive.
     * @throws IllegalArgumentException
     *             If the given channel name is null.
     * @throws UnsupportedOperationException
     *             If we are not even in the given channel.
     */
    public synchronized boolean switchToPassive(String channelName) {
        if (channelName == null) {
            throw new IllegalArgumentException("Given channel name was null or empty.");
        }

        if (!this.channelKnown(channelName)) {
            throw new UnsupportedOperationException("You can't join channels using this method.");
        }

        l.info("Becoming a lurker on " + this.url + " in channel " + channelName + ".");
        if (this.activeChannels.contains(channelName)) {
            this.activeChannels.remove(channelName);
            this.passiveChannels.add(channelName);
            l.debug("Became a lurker on " + this.url + " in " + channelName);
            return true;
        } else {
            l.debug("We were not active in the channel on " + this.url + ": " + channelName);
            return false;
        }
    }

    /**
     * Become active in the given channel. It's illegal to become active in a channel not even joined.
     * 
     * @param channelName
     *            The channel to become active in.
     * @return Whether or not we switched to be active.
     * @throws IllegalArgumentException
     *             If the given channel name is null.
     * @throws UnsupportedOperationException
     *             If we are not even in the given channel.
     */
    public synchronized boolean switchToActive(String channel) {
        if (channel == null || channel.length() < 1) {
            throw new IllegalArgumentException("The given channel name was null or empty.");
        }

        if (this.activeChannels.contains(channel)) {
            throw new UnsupportedOperationException("You can't switch to be active in an active channel.");
        }

        l.info("Becoming active on " + this.url + " in channel " + channel + ".");
        if (this.passiveChannels.contains(channel)) {
            this.passiveChannels.remove(channel);
            this.activeChannels.add(channel);
            l.debug("Became active on " + this.url + " in channel " + channel + ".");
            return true;
        } else {
            l.debug("We were not lurking in the channel on " + this.url + ": " + channel);
            return false;
        }
    }

    /**
     * Return whether or not the given channel is considered one to be active in.
     * 
     * @param channelName
     *            The channel to check.
     * @return True iff the channel is an active one.
     * @throws IllegalArgumentException
     *             If the channel name is null.
     */
    public synchronized boolean isActiveChannel(String channelName) {
        if (channelName == null) {
            throw new IllegalArgumentException("Given channel name was null or empty.");
        }

        return this.activeChannels.contains(channelName);
    }

    /**
     * Return whether or not the given {@link Channel} is considered one to be active in.
     * 
     * @param channel
     *            The channel to check.
     * @return True iff the channel is an active one.
     * @throws IllegalArgumentException
     *             If the channel is null.
     */
    public synchronized boolean isActiveChannel(Channel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel was null.");
        }

        return this.isActiveChannel(channel.getName());
    }

    /**
     * Return whether or not the given channel is one to be passive in.
     * 
     * @param channelName
     *            The channel to check.
     * @return True iff the channel is a passive one.
     * @throws IllegalArgumentException
     *             If the channel name is null.
     */
    public synchronized boolean isPassiveChannel(String channelName) {
        if (channelName == null) {
            throw new IllegalArgumentException("Given channel name was null or empty.");
        }

        return this.passiveChannels.contains(channelName);
    }

    /**
     * Return whether or not the given {@link Channel} is one to be passive in.
     * 
     * @param channel
     *            The channel to check.
     * @return True iff the channel is a passive one.
     * @throws IllegalArgumentException
     *             If the channel is null.
     */
    public synchronized boolean isPassiveChannel(Channel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel was null.");
        }

        return this.isPassiveChannel(channel.getName());
    }

    /**
     * Leave the channel by the given name regardless of whether we're active or passive in it. The channel is removed
     * from its respective set.
     * 
     * @param channel
     *            The channel to leave.
     * @throws IllegalArgumentException
     *             If the given channel name is null.
     * @throws IllegalStateException
     *             If we are not in the channel.
     */
    public synchronized void partChannel(String channel) {
        if (channel == null) {
            throw new IllegalArgumentException("The given channel name was null.");
        }

        if (!this.channelKnown(channel)) {
            throw new IllegalStateException("Can't part from a channel you aren't in.");
        }

        l.info("Parting from channel on " + this.url + ": " + channel);
        if (this.isActiveChannel(channel)) {
            this.removeActiveChannel(channel);
        }

        if (this.isPassiveChannel(channel)) {
            this.removePassiveChannel(channel);
        }
    }

    /**
     * Leaves the given channel. This is only a small helper method needed to resolve a channel's name to it's
     * {@link Channel} instance needed for {@link PircBotX#partChannel(Channel, String)}. The part message will be
     * {@link Network#quitMessage}.
     * 
     * @param channelName
     */
    private void leaveChannel(String channelName) {
        Channel channel = this.getChannel(channelName);
        assert channel != null;
        this.partChannel(channel, this.quitMessage);
    }

    /**
     * Return whether or not the given channel is known as either an active or passive channel.
     * 
     * @param channelName
     *            The channel to check.
     * @return True iff the channel is considered active or passive.
     * @throws IllegalArgumentException
     *             If the given channel is null.
     */
    public synchronized boolean channelKnown(String channelName) {
        if (channelName == null) {
            throw new IllegalArgumentException("The given channel name is null.");
        }

        return this.passiveChannels.contains(channelName) || this.activeChannels.contains(channelName);
    }

    /**
     * Add a message source to be ignored in this network.
     * 
     * @param ignored
     *            The message source to be ignored.
     */
    public synchronized void addIgnored(String ignored) {
        this.ignore.add(ignored);
    }

    /**
     * Check if the given message source should be ignored.
     * 
     * @param ignored
     *            The message source to check.
     * @return True iff the message source should be ignored.
     */
    public boolean isIgnored(String ignored) {
        return this.ignore.contains(ignored);
    }

    public synchronized String getUserName() {
        return userName;
    }

    public synchronized void setUserName(String userName) {
        this.userName = userName;
    }

    public synchronized String getRealName() {
        return realName;
    }

    public synchronized void setRealName(String realName) {
        this.realName = realName;
    }

    public synchronized String getUrl() {
        return url;
    }

    public synchronized String[] getNickNames() {
        return nickNames;
    }

    public synchronized String getQuitMessage() {
        return quitMessage;
    }

    @Override
    public synchronized int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        return result;
    }

    @Override
    public synchronized boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        Network other = (Network) obj;
        if (url == null) {
            if (other.url != null) return false;
        } else if (!url.equals(other.url)) return false;
        return true;
    }
}

package de.blanksteg.freamon.irc;

import org.pircbotx.Channel;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;

import de.blanksteg.freamon.Configuration;

/**
 * This ResponseGenerator uses another {@link ResponseGenerator} to generate responses to private and public messages.
 * Before sending anything, he simulates the user typing using {@link Configuration#simulateDelay()} and then enters a
 * cooldown period during which he won't respond. Whether or not he actually does respond is also determined by a roll
 * using {@link Configuration#rollPublicResponse()} or {@link Configuration#rollPingResponse()}.
 * 
 * @author Marc Müller
 */
public class GenericAnthroResponseGenerator implements ResponseGenerator {
    /** The base responder to use. */
    private final ResponseGenerator base;

    /** The system time we last sent a message. */
    private long lastMessage;

    /**
     * Create a new instance using the given {@link ResponseGenerator} as a base.
     * 
     * @param newBase
     *            The base generator.
     */
    public GenericAnthroResponseGenerator(ResponseGenerator newBase) {
        this.base = newBase;
    }

    @Override
    public String respondPublic(MessageEvent<Network> event) {
        Network target = event.getBot();
        Channel channel = event.getChannel();
        String channelName = channel.getName();

        String response = null;
        if (this.hasCooledDown() && target.isActiveChannel(channelName)) {
            String nickName = target.getName().toLowerCase();

            if (event.getMessage().toLowerCase().contains(nickName)) {
                if (Configuration.rollPingResponse()) {
                    response = this.base.respondPublic(event);
                }
            } else {
                if (Configuration.rollPublicResponse()) {
                    response = this.base.respondPublic(event);
                }
            }
        }

        if (response != null) {
            String senderNick = event.getUser().getNick();

            this.handleMessage();
            String lowerResponse = response.toLowerCase();
            String message = event.getMessage().toLowerCase();

            String could = senderNick.toLowerCase();

            if (message.contains(event.getBot().getNick().toLowerCase()) && !lowerResponse.contains(could)) {
                response = senderNick + ": " + response;
            }
        }

        return response;
    }

    @Override
    public String respondPrivate(PrivateMessageEvent<Network> event) {
        if (this.hasCooledDown()) {
            String response = this.base.respondPrivate(event);
            if (response != null) {
                this.handleMessage();
            }
            return response;
        }

        return null;
    }

    /**
     * Check whether or not we've been silent for {@link Configuration#getCooldown()}.
     * 
     * @return true if we've waited long enough.
     */
    private boolean hasCooledDown() {
        return (System.currentTimeMillis() - this.lastMessage) > Configuration.getCooldown() * 1000;
    }

    /**
     * Sleep for a random amount and remember when we sent a message.
     */
    private void handleMessage() {
        Configuration.simulateDelay();
        this.lastMessage = System.currentTimeMillis();
    }

}

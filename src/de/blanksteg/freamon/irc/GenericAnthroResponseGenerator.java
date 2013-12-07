package de.blanksteg.freamon.irc;

import org.apache.commons.lang.StringUtils;
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
    public String respondPublic(final MessageEvent<Network> event) {
        final Network target = event.getBot();
        final String botName = target.getName();
        final String message = event.getMessage();
        final boolean botMentioned = StringUtils.containsIgnoreCase(message, botName);

        String response = null;

        // When we're mentioned, ignore the cooldown
        if (botMentioned) {
            if (Configuration.rollPingResponse()) {
                response = base.respondPublic(event);
            }
        } else if (hasCooledDown()) {
            if (Configuration.rollPublicResponse()) {
                response = base.respondPublic(event);
            }
        }

        if (response != null) {
            handleMessage();

            // If the sender mentioned us but isn't mentioned yet, make sure we do
            final String senderNick = event.getUser().getNick();
            if (botMentioned && !StringUtils.containsIgnoreCase(response, senderNick)) {
                response = senderNick + ": " + response;
            }
        }

        return response;
    }

    @Override
    public String respondPrivate(PrivateMessageEvent<Network> event) {
        if (hasCooledDown()) {
            String response = base.respondPrivate(event);
            if (response != null) {
                handleMessage();
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
        return (System.currentTimeMillis() - lastMessage) > Configuration.getCooldown() * 1000;
    }

    /**
     * Sleep for a random amount and remember when we sent a message.
     */
    private void handleMessage() {
        Configuration.simulateDelay();
        lastMessage = System.currentTimeMillis();
    }

}

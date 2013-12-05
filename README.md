# 0. Pre-note


This project was originally developed by Mark Müller. As it seems to have been
discontinued, I (Timon Kanters) have set up this project again to further
develop it and to allow others to contribute. Until more knowledge about the
project is gained, the below notes might not be accurate at the moment. (Mainly
the Maven instructions.)

# 1. About


The Freamon project is a simple IRC chatbot that very specifically does not try
to actually have sensible conversations, but rather be a somewhat stupified
participant. Freamon learns how he should respond to certain messages by
constantly observing the ongoing conversations and remembering commonly used
phrases. When challenged for a response he then attempts to find some reply that
he previous related to the message challenge with. While this rarely produces
meaningful results, the mutations caused by the statistical model of a language
he falls back on have proven to be an amusing side effect.

Freamon is not meant to be used in a destructive way. The pointless banter he
spams normal conversations with are generally irritating to people unaware of the
fact that he is a chatbot and he should therefore not be present during actual
discussions but remain in an isolated channel where people can play with him.

The name "Freamon" is a reference to [Lester Freamon](https://en.wikipedia.org/wiki/Lester_Freamon).

# 2. Building the project


Freamon itself is implemented in Java. [Maven](https://maven.apache.org/) is
used to manage the build process and most libraries it depends on. However, it
also requires the [JMegaHal](http://www.jibble.org/jmegahal/) project which
is not available in any Maven repository known to me. To actually build the project
one therefore has to manually install the respective `JMegaHal.jar` into their
local Maven repository. To do this, navigate to the directory containing your
`JMegaHal.jar` and execute the following:

    mvn install:install-file -Dfile=JMegaHal.jar -DgroupId=org.jibble -DartifactId=jmegahal -Dversion=1.0 -Dpackaging=jar

This will install the `JMegaHal.jar` in such a manner that Maven finds it when
building Freamon.

When all this is done, the project can be built by issuing the follwoing command
in the directory Freamon's [pom.xml](pom.xml) resides in:

    mvn assembly:assembly

Which will make Maven place a runnable `freamon.jar` inside the `target/assemly`
subdirectory.

# 3. Using Freamon


To actually get the bot chatting, several other steps are required in preperation.
The process of setting him up is described here.

## 3.1 Generating brains


Since Freamon is a learning chatbot, it's ideal to have him actually learn some
things before getting him into a channel. To start training, one needs to launch
`freamon.jar` with the `-i` flag. Additionally, where to store the brain needs
to be specified with the `-bf <brainfile>` parameter. Freamon can learn from the
following kinds of files:

  - Plain text files where each line is taught as a sentence.
  - Plain text files where continuous text is taught with sentences split
    according to [Stanford CoreNLP](http://nlp.stanford.edu/software/corenlp.shtml).
  - [IRSSI](http://irssi.org/) log files where each line represents a user's message.
  - [HexChat](http://hexchat.org/) log files where each line represents a user's message.
  - [KVIrc](http://www.kvirc.de/) log files where each line represents a user's mesage. Color coded logs are allowed in this case.
  - Diretories to scan through containing files of one of the types named above.

Specifying which files to learn from is also done via command line arguments.
Further info on these are available by launching Freamon with the `-h` flag, but
command line parameters for the files above are:

  - `-tf <txtfile>` for plain text files.
  - `-tfd <txtdir>` for plain text file directories.
  - `-ct <conttxt>` for continuous text files.
  - `-ctd <contdir>` for continuous text directories.
  - `-il <irssilog>` for IRSSI logs.
  - `-ild <irssidir>` for IRSSI log directories.
  - `-hl <hexchatlog>` for HexChat logs.
  - `-hld <hexchatdir>` for HexChat log directories.
  - `-kl <kvirclog>` for KVIrc logs.
  - `-kld <kvircdir>` for KVIrc log directories.

Directories will be recursively traversed and will generally open every file
inside, so having files of varying types in one directory is not recommended.

An example command to train Freamon would be:

    java -jar freamon.jar -i -bf example.brain \
      -tf common_sayings.txt                   \
      -tfd files/texts                         \
      -ct metamorphosis.txt                    \
      -ctd files/kafka                         \
      -il irssi.log                            \
      -ild ~/.irssi/logs                       \
      -hl hexchat.log                          \
      -hld ~/.hexchat/logs                     \
      -kl kvirc.log                            \
      -kld ~/.kvirc/logs

This would create a brain file `example.brain` and teach Freamon plain text from
the file `common_sayings.txt` and the directory `files/texts`, the complete works
of Kafka from the file `metamorphosis.txt` and the directory `files/kafka` and
the several log file options for the clients currently supported. The resulting
brain will be saved to `example.brain` can and then then be used as Freamon's
brain while chatting.

Note that whenever `-i` is used, Freamon will start training and terminate
afterwards, *without* joining any IRC networks.

## 3.2 Lauching Freamon


Apart from learning the language, Freamon needs additional info to join an IRC
network. These are also given as command line arguments. When he is connected to
a network, configuration is done via private messaging, but initially
at least one network needs to be specified with the `-s <host>` parameter. It
also requires a previously generated brain file using the `-bf <brainfile>` option
and a sequence of nicknames that are attempted when connecting with `-n <name1> <name2>`.

So an example for starting `freamon.jar` would be:

    java -jar freamon.jar -s irc.example.org -bf example.brain -n Freamon Freamon- Freamon1

Which would make him connect to `irc.example.org` using the nickname Freamon, falling
back on either Freamon- or Freamon1 if these turn out to be taken. His brain during
this would be `example.brain`.

Consult the output of `java -jar freamon.jar -h` for many more configuration
parameters.

## 3.3 Managing Freamon


As you may have noticed, there was no channel the bot should join specified in
the command line arguments from #3.2. This is because after connecting, Freamon
should be managed via a series of commands that are sent to him as private
messages. Since such a management backend would be easily abused, most commands
require a user to be authenticated as an admin. To do so, the user needs to PM
the bot with `!auth <password>`. The password expected here is given as a parameter
when Freamon is started using `-p <password>`. Freamon will respond whether or
not the user succesfully authenticated with him.

The commands available after authentication are:

  - `!lurk <channelname>`: Join a channel but don't talk in it.
  - `!join <channelname>`: Join a channel and talk in it.
  - `!part <channelname>`: Leave a channel.
  - `!networkadd <host>`: Join another network, just like the command line parameter `-s`.
  - `!networkdel <host>`: Disconnect from *another* network.
  - `!quit`: Disconnect from all networks, save the current brain and quit the application.
  - `!nick`: Change the nickname. Will not change if already in use.
  - `!brainswitch <brainfile>`: Save the current brain and and switch to the given one.
  - `!pubchance <n>`: Set response chance to normal public messages.
  - `!pingchance <n>`: Set response chance to messages containing his nickname.
  - `!greetchance <n>`: Set greeting chance for someone joining a channel.
  - `!cooldown <n>`: Set the minimum cooldown between messages in seconds.
  - `!mindelay <n>`: Set the minimum delay before sending a message in milliseconds.
  - `!maxdelay <n>`: Set the maximum delay before sending a message in milliseconds.

Freamon will react to most of these commands instantly, but the `!brainswitch`
command might take some time during which Freamon will not respond to any queries
in any channel.

To avoid people hijacking Freamon after an authenticated user quits or changes
his nickname, any previously authenticated user will require reauthentication with
Freamon after either quitting or changing his nickname.

Keep in mind that this system is obviously not very safe since there's only a
single password any administrator needs to share and it might be sent over an
unencrypted connection. The private message mechanism, while comfortable for an
IRC bot, might also lead to people accidentally posting `!auth <password>` in
a channel, leaking the password.

Beware that he will not react to any command being sent publicly, even if the user
is authenticated.

# 4. Additional Configuration and Information


Information described in chapter 3 should suffice for most normal users. Advanced
topics are discussed here.

## 4.1 Logging configuration


During Freamon's normal execution he produces very verbose log output. Since such
output is mostly irrelevant to normal users, one might want to customize what
component of Freamon is logging to which level. Because the application utilizes
the [Apache Log4j 2](https://logging.apache.org/log4j/2.x/) library, the log
output can be customized after the compilation. To do this, one can place a
custom `log4j.xml` inside the directory the `freamon.jar` is executed from and
the respective logging classes will be configured accordingly. The basic `log4j.xml`
Freamon uses when the user does not specify a custom one can be found in
[src/main/resources/log4j.xml](src/main/resources/log4j.xml). Modifying this one
to suit your needs is the recommended way.

You should only omit output considered too verbose from the standard
output, but still have it kept in the log files. In case of a crash the likely
cause can then be deduced from the log files.

## 4.2 Custom greetings


Greetings are not something a chat bot can heuristically learn using the Markov
chaining algorithm implemented in JMegaHal. To still have the possibility of a
bot that greets people, the application simply maintains a list of possible
greetings to be sent and picks one of them randomly in case of a greeting. While
currently there is no option to redefine these after compilation, one can simply
modify the files [src/main/resources/greetings.list](src/main/resources/greetings.list)
and [src/main/resources/join.list](/src/main/resources/join.list) to customize
greetings. The `greetings.list` contains one line for each possible greeting
*to* a user when he joins a channel and `join.list` contains greetings *from*
the bot after entering a channel.

Each greeting line can also contain the special string `%user%` and `%channel%`.
These will be replace by the respective channel an user before being sent. So for
example:

> Hello people of %channel%!

> Hello %user%! How are you doing?

When the application is then recompiled, the `freamon.jar` will contain these
files instead of the normal ones.

## 4.3 Custom triggers

Certain responses can be enforced by the developer. This is also a workaround for
the limited learning possible with Markov chaining. Defining these triggers is
also done before compilation by modifying the file in [src/main/resources/fixed.json](src/main/resources/fixed.json).
As the name of the file implies, the expected contents are a [JSON](http://www.json.org/)
object in which each key is considered a trigger and its mapped value the fixed
response. Values are always treated as strings. An example JSON object would be:

    {
      ":)" : ":)",
      "Haha" : "That's not funny."
    }

Which would register a smiley as a response to a smiley and "That's not funny."
to a user saying "Haha".

Keep in mind that a trigger needs to fit the *entire* message, so someone saying
"Hahah, that's funny." would not cause the fixed reply. Fixed replies are ignored
a certain percent of the time to not make the bot predictable.

## 4.4 Freamon's tells

Obviously, Freamon could be used to spam IRC channels. If someone is ever doubtful
about a user's "humanity", he can check if it's a Freamon bot by simly pinging him.
When Freamon receives a `/ping <nickname>` request, he will message the user that
pinged him and tell him he's a bot. Sending him either of the commands in chapter
 3.3 would also blow a bot's cover, because he will respond according to the
command.

The user, client and real name available in IRC are not reliable tells as they
are easily customized via command line arguments.

# Credits

Freamon relies on the following great libraries and would probably not possible
without them:

  - [Apache Commons CLI](https://commons.apache.org/proper/commons-cli/)
  - [Apache Log4j 2](https://logging.apache.org/log4j/2.x/)
  - [PircBotX](https://code.google.com/p/pircbotx/)
  - [JMegaHal](http://www.jibble.org/jmegahal/)
  - [Stanford Parser](http://nlp.stanford.edu/software/lex-parser.shtml)
  - [Stanford CoreNLP](http://nlp.stanford.edu/software/corenlp.shtml)
  - [JSON-lib](http://json-lib.sourceforge.net/)

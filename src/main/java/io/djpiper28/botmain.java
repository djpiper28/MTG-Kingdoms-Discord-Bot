package io.djpiper28;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import okhttp3.OkHttpClient;

import javax.security.auth.login.LoginException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class botmain extends ListenerAdapter {

    static final String command = "!kingdoms";

    static final String assassinWinCon = "You win if you are the last standing";
    static final String kingWinCon = "You win if you are the last standing or if you and your knight are the last standing. " +
            "You **go first** and start on **1.5x life** (60 in commander), you must reveal your role, the " +
            "bot does this for you.";

    static final String[] roles = {"King", "Knight", "Bandit", " Assassin", "Bandit", "Usurper"};
    static final String[] rolesDescriptions = {
            kingWinCon,
            "You win if the king wins.",
            "You win if the king loses.",
            assassinWinCon, assassinWinCon,
            "If you kill the king you become the king revealing your role at the same time. The bandit does not win " +
                    "immediately. ```King rules:\n" + kingWinCon + "```"};

    static String[] bannedChannelIds;

    public static void main(String[] args) {
        // Read parameters

        if (args.length < 1) {
            throw new RuntimeException("Illegal number of parameters. Expected parameters are: <token> <secret> <banned " +
                    "channel ids (space separated))>");
        }

        String token = args[0];
        bannedChannelIds = new String[args.length - 1];

        for (int i = 1; i < args.length; i++) {
            bannedChannelIds[i - 1] = args[i];
        }

        // Start bot
        try {
            JDABuilder builder = JDABuilder.createDefault(token);
            builder.enableCache(CacheFlag.VOICE_STATE);
            builder.setActivity(Activity.playing(String.format("Type %s whilst in VC to start a game.", command)));
            builder.addEventListeners(new botmain());
            builder.enableIntents(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.DIRECT_MESSAGES,
                    GatewayIntent.GUILD_VOICE_STATES
            );

            JDA jda = builder.build();


            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    jda.shutdown();

                    OkHttpClient client = jda.getHttpClient();
                    client.connectionPool().evictAll();
                    client.dispatcher().executorService().shutdown();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.exit(0);
            }));
        } catch (LoginException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Message msg = event.getMessage();

        if (msg.getContentRaw().equals(command) && contains(bannedChannelIds, msg.getChannel().getId()) && !msg.getAuthor().isBot()) {
            MessageChannel channel = event.getChannel();

            StringBuilder playersList = new StringBuilder();
            boolean userInVC = false;
            boolean enoughPlayers = false;

            if (msg.getMember().getVoiceState().inVoiceChannel()) {
                List<Member> notBots = new LinkedList<>();
                VoiceChannel voiceChannel = msg.getMember().getVoiceState().getChannel();
                userInVC = true;

                // Get not bots
                voiceChannel.getMembers().forEach(member -> {
                    if (!member.getUser().isBot()) {
                        notBots.add(member);
                    }
                });

                enoughPlayers = notBots.size() > 3 && notBots.size() <= roles.length;

                if (enoughPlayers) {

                    for (int i = 0; i < notBots.size(); i++) {
                        if (i == notBots.size() - 1) {
                            playersList.append(" and " + notBots.get(i).getAsMention());
                        } else if (i != 0) {
                            playersList.append(" , " + notBots.get(i).getAsMention());
                        } else {
                            playersList.append(notBots.get(i).getAsMention());
                        }
                    }
                }

                if (enoughPlayers && userInVC) {
                    channel.sendMessage("Assigning roles to " + playersList.toString()).queue();
                    Random random = new Random();

                    // Pull random members
                    int i = 0;
                    while (!notBots.isEmpty()) {
                        Member randomMember = notBots.remove(random.nextInt(notBots.size()));

                        if (i == 0) {
                            channel.sendMessage(randomMember.getAsMention() + " Is the king").queue();
                        }

                        randomMember.getUser().openPrivateChannel().complete().sendMessage(String.format("Dear `%s`,\n" +
                                        "You have been assigned the role **%s** the rules for it are```%s```",
                                randomMember.getUser().getName(), roles[i], rolesDescriptions[i])).queue();

                        i++;
                    }

                } else if (enoughPlayers) {
                    channel.sendMessage("Get your self in the vc you numpty @" + msg.getMember().getNickname()).queue();
                } else {
                    channel.sendMessage(String.format("Get your self enough players in the vc you numpty (4 ≤ x ≤ %s) ", roles.length) +
                            msg.getMember().getAsMention() + " (non bots: " + notBots.size() + " detected).").queue();
                }
            }
        }
    }

    private boolean contains(String[] strings, String id) {
        for (String str : strings) {
            if (str == id) {
                return true;
            }
        }

        return false;
    }

}

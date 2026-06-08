package com.agape;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessagePollBuilder;
import net.dv8tion.jda.api.utils.messages.MessagePollData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class LetsChatManager {

    // Master switch — flip to true to enable posting
    static final boolean ENABLED = true;

    // Server timezone (America/New_York — handles EST/EDT automatically)
    private static final ZoneId SERVER_ZONE = ZoneId.of("America/New_York");

    // Local hour at which the daily question is posted (9 AM ET)
    private static final int POST_HOUR = 9;

    private static final String QUESTIONS_FILE = "assets/lets_chat_questions.json";
    private static final String STATE_FILE     = "user_content/lets_chat_state.json";

    // Channel names searched in priority order
    private static final String[] CHANNEL_NAMES = {
        "general", "gen-chat", "general-chat",
        "lets-chat", "letschat", "lets_chat",
        "daily-chat", "daily-question", "dailies",
        "christian-chat", "christian-talk", "discussion"
    };

    static class Question {
        String category;
        String format;   // "QA" or "Poll"
        String question;
        List<String> options; // only present for Poll entries
    }

    static class State {
        String lastPostedDate = "";
    }

    public static void checkAndPost(JDA jda) {
        if (!ENABLED) return;

        ZonedDateTime now = ZonedDateTime.now(SERVER_ZONE);
        if (now.getHour() < POST_HOUR) return;

        LocalDate today = LocalDate.now(SERVER_ZONE);

        // Only post on even days of the year
        int dayOfYear = today.getDayOfYear();
        if (dayOfYear % 2 != 0) return;

        String todayStr = today.toString();
        State state = loadState();
        if (todayStr.equals(state.lastPostedDate)) return;

        List<Question> questions = loadQuestions();
        if (questions == null || questions.isEmpty()) {
            System.err.println("LetsChatManager: No questions loaded from " + QUESTIONS_FILE);
            return;
        }

        // 4-year cycle: year%4 → offset 0,1,366,367 maps 183 posting days to a unique slice of 732
        int year = today.getYear();
        int yearMod4 = year % 4;
        int offset = (yearMod4 / 2) * 366 + (yearMod4 % 2);
        int idx = (dayOfYear + offset) % questions.size();

        Question q = questions.get(idx);

        boolean postedAny = false;
        for (Guild guild : jda.getGuilds()) {
            if (!EnvironmentManager.isGuildAllowed(guild.getId())) continue;

            TextChannel ch = findChannel(guild);
            if (ch == null) {
                System.err.println("LetsChatManager: No suitable channel found in guild " + guild.getName()
                    + ". Create a channel named 'lets-chat' to enable daily posts.");
                continue;
            }

            Role letsRole = guild.getRolesByName("Let's Chat!", false).stream().findFirst().orElse(null);
            String mention = letsRole != null ? letsRole.getAsMention() : null;

            if ("Poll".equals(q.format) && q.options != null && q.options.size() >= 2) {
                MessagePollBuilder pollBuilder = MessagePollData.builder(q.question)
                    .setMultiAnswer(false)
                    .setDuration(Duration.ofHours(24));
                for (String opt : q.options) pollBuilder.addAnswer(opt);
                ch.sendMessagePoll(pollBuilder.build()).setContent(mention).queue(
                    s -> System.out.println("LetsChatManager: Posted daily poll to #" + ch.getName() + " in " + guild.getName()),
                    e -> System.err.println("LetsChatManager: Failed to post poll to " + guild.getName() + ": " + e.getMessage())
                );
            } else {
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("☕ Let's Chat!")
                    .setDescription("**" + q.question + "**\n\n-# Share your thoughts below!")
                    .setColor(0xFF9966)
                    .setFooter("📖 " + q.category + "  ·  Agape Matchmaking")
                    .setTimestamp(java.time.Instant.now());
                ch.sendMessageEmbeds(embed.build()).setContent(mention).queue(
                    s -> System.out.println("LetsChatManager: Posted daily question to #" + ch.getName() + " in " + guild.getName()),
                    e -> System.err.println("LetsChatManager: Failed to post to " + guild.getName() + ": " + e.getMessage())
                );
            }
            postedAny = true;
        }

        if (postedAny) {
            state.lastPostedDate = todayStr;
            saveState(state);
        }
    }

    private static List<Question> loadQuestions() {
        File f = new File(QUESTIONS_FILE);
        if (!f.exists()) {
            System.err.println("LetsChatManager: Questions file not found: " + QUESTIONS_FILE);
            return null;
        }
        try (FileReader r = new FileReader(f)) {
            Type listType = new TypeToken<List<Question>>(){}.getType();
            return new Gson().fromJson(r, listType);
        } catch (Exception e) {
            System.err.println("LetsChatManager: Failed to load questions: " + e.getMessage());
            return null;
        }
    }

    private static TextChannel findChannel(Guild guild) {
        for (String name : CHANNEL_NAMES) {
            List<TextChannel> chs = guild.getTextChannelsByName(name, true);
            if (!chs.isEmpty()) return chs.get(0);
        }
        return null;
    }

    private static State loadState() {
        File f = new File(STATE_FILE);
        if (!f.exists()) return new State();
        try (FileReader r = new FileReader(f)) {
            State s = new Gson().fromJson(r, State.class);
            return s != null ? s : new State();
        } catch (Exception e) {
            System.err.println("LetsChatManager: Failed to load state: " + e.getMessage());
            return new State();
        }
    }

    private static void saveState(State state) {
        try {
            new File("user_content").mkdirs();
            try (FileWriter w = new FileWriter(STATE_FILE)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(state, w);
            }
        } catch (Exception e) {
            System.err.println("LetsChatManager: Failed to save state: " + e.getMessage());
        }
    }
}

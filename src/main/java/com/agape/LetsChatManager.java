package com.agape;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class LetsChatManager {

    // Master switch — flip to true to enable posting
    static final boolean ENABLED = false;

    // UTC hour at which the daily question is posted (noon)
    private static final int POST_HOUR_UTC = 12;

    // Channel names searched in priority order
    private static final String[] CHANNEL_NAMES = {
        "lets-chat", "letschat", "lets_chat",
        "daily-chat", "daily-question", "dailies",
        "christian-chat", "christian-talk", "discussion"
    };

    private static final String STATE_FILE = "user_content/lets_chat_state.json";

    // {category, question}
    private static final String[][] QUESTIONS = {
        // Dating & Marriage
        { "Dating & Marriage", "What do you believe is the single most important quality to look for in a future spouse?" },
        { "Dating & Marriage", "At what stage of a relationship do you think it's appropriate to discuss marriage?" },
        { "Dating & Marriage", "What role should prayer play in a Christian dating relationship?" },
        { "Dating & Marriage", "What is one thing you believe separates a healthy relationship from an unhealthy one?" },
        { "Dating & Marriage", "How do you think finances should be handled in a Christian marriage?" },
        { "Dating & Marriage", "What's one non-negotiable character trait you would need in a potential spouse?" },
        { "Dating & Marriage", "How do you believe a husband and wife should handle disagreements in a godly way?" },
        { "Dating & Marriage", "What does 'marrying your best friend' mean to you?" },
        { "Dating & Marriage", "How do you feel about long-distance relationships — can they truly thrive?" },
        { "Dating & Marriage", "How do you envision spiritual leadership working in a future marriage?" },
        { "Dating & Marriage", "Do you think couples need to share the same hobbies and interests, or is it okay to have separate passions?" },
        { "Dating & Marriage", "What does submission in marriage mean to you, as described in Ephesians 5?" },
        { "Dating & Marriage", "At what point in dating do you think it's appropriate to discuss children and family planning?" },
        { "Dating & Marriage", "What role should a couple's church community play in their relationship?" },
        { "Dating & Marriage", "How should physical boundaries look in a Christian dating relationship?" },
        { "Dating & Marriage", "Do you believe spouses need to share the exact same denomination, or is shared faith in Christ enough?" },
        { "Dating & Marriage", "How do you feel about premarital counseling — do you think every couple should go through it?" },
        { "Dating & Marriage", "What's one thing you want to make sure is firmly established *before* getting engaged?" },
        { "Dating & Marriage", "What's the difference between loving someone and being *in love* with someone?" },
        { "Dating & Marriage", "What does a 'date' look like to you — what kinds of activities do you find most meaningful for getting to know someone?" },

        // Protestant Christianity
        { "Christian Faith", "What is one Bible verse that has deeply shaped how you approach love or relationships?" },
        { "Christian Faith", "How has your faith journey shaped the kind of partner you are — or want to be?" },
        { "Christian Faith", "What does it mean practically to 'put God at the center' of a relationship?" },
        { "Christian Faith", "How do you handle seasons where your prayer life or church attendance feels dry?" },
        { "Christian Faith", "Which book of the Bible has impacted your walk with God the most, and why?" },
        { "Christian Faith", "What does 'bearing one another's burdens' (Galatians 6:2) look like practically in a relationship?" },
        { "Christian Faith", "Do you have a Bible reading routine or method you'd recommend to others?" },
        { "Christian Faith", "What is one spiritual discipline (prayer, fasting, journaling, etc.) that has had the biggest impact on your faith?" },
        { "Christian Faith", "How do you believe a Christian should handle forgiveness in a romantic relationship after being hurt?" },
        { "Christian Faith", "What does being 'equally yoked' (2 Corinthians 6:14) mean to you in the context of dating?" },
        { "Christian Faith", "How do you think Christians should navigate social media and its effect on relationships?" },
        { "Christian Faith", "Do you have a favorite Christian book, sermon, or devotional that shaped how you think about love and marriage?" },
        { "Christian Faith", "What does agape love — unconditional, self-giving love — look like practically in a dating relationship?" },
        { "Christian Faith", "How important is it to you that your future spouse serves in some capacity at church?" },
        { "Christian Faith", "What's one area of your faith you feel God is currently calling you to grow in?" },
        { "Christian Faith", "How do you navigate friendships with members of the opposite sex while pursuing holiness?" },
        { "Christian Faith", "What does a healthy prayer life look like for a couple, in your opinion?" },
        { "Christian Faith", "How has your relationship with God shaped the way you handle conflict?" },
        { "Christian Faith", "What's one thing you think the Church tends to get wrong about dating and relationships?" },
        { "Christian Faith", "What does 'waiting on God' mean to you when it comes to finding a spouse — and how do you practice it?" },
    };

    public static void checkAndPost(JDA jda) {
        if (!ENABLED) return;

        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        if (nowUtc.getHour() < POST_HOUR_UTC) return;

        String today = LocalDate.now(ZoneOffset.UTC).toString();

        State state = loadState();
        if (today.equals(state.lastPostedDate)) return;

        if (state.usedIndices.size() >= QUESTIONS.length) {
            state.usedIndices.clear();
        }

        int idx = pickUnused(state.usedIndices);
        String category = QUESTIONS[idx][0];
        String question = QUESTIONS[idx][1];

        boolean postedAny = false;
        for (Guild guild : jda.getGuilds()) {
            if (!EnvironmentManager.isGuildAllowed(guild.getId())) continue;

            TextChannel ch = findChannel(guild);
            if (ch == null) {
                System.err.println("LetsChatManager: No suitable channel found in guild " + guild.getName()
                    + ". Create a channel named 'lets-chat' to enable daily posts.");
                continue;
            }

            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("☕ Let's Chat!")
                .setDescription("**" + question + "**\n\n-# Share your thoughts below!")
                .setColor(0xFF9966)
                .setFooter("📖 " + category + "  ·  Agape Matchmaking")
                .setTimestamp(java.time.Instant.now());

            ch.sendMessageEmbeds(embed.build()).queue(
                s -> System.out.println("LetsChatManager: Posted daily question to #" + ch.getName() + " in " + guild.getName()),
                e -> System.err.println("LetsChatManager: Failed to post to " + guild.getName() + ": " + e.getMessage())
            );
            postedAny = true;
        }

        if (postedAny) {
            state.lastPostedDate = today;
            state.usedIndices.add(idx);
            saveState(state);
        }
    }

    private static TextChannel findChannel(Guild guild) {
        for (String name : CHANNEL_NAMES) {
            List<TextChannel> chs = guild.getTextChannelsByName(name, true);
            if (!chs.isEmpty()) return chs.get(0);
        }
        return null;
    }

    private static int pickUnused(List<Integer> used) {
        for (int i = 0; i < QUESTIONS.length; i++) {
            if (!used.contains(i)) return i;
        }
        return 0;
    }

    static class State {
        String lastPostedDate = "";
        List<Integer> usedIndices = new ArrayList<>();
    }

    private static State loadState() {
        File f = new File(STATE_FILE);
        if (!f.exists()) return new State();
        try (FileReader r = new FileReader(f)) {
            State s = new com.google.gson.Gson().fromJson(r, State.class);
            if (s == null) return new State();
            if (s.usedIndices == null) s.usedIndices = new ArrayList<>();
            return s;
        } catch (Exception e) {
            System.err.println("LetsChatManager: Failed to load state: " + e.getMessage());
            return new State();
        }
    }

    private static void saveState(State state) {
        try {
            File dir = new File("user_content");
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter w = new FileWriter(STATE_FILE)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(state, w);
            }
        } catch (Exception e) {
            System.err.println("LetsChatManager: Failed to save state: " + e.getMessage());
        }
    }
}

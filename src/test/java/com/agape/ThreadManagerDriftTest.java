package com.agape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.agape.ThreadManager.TopicSignal;

/**
 * Characterizes the deal-breakers-only drift heuristic ({@link ThreadManager#classifyMessage})
 * using real message shapes drawn from a production manual-match thread.
 */
public class ThreadManagerDriftTest {

    @Test
    public void dealbreakerPostsAreOnTopic() {
        // Structured deal-breaker lists from the real log — all contain cue vocabulary.
        assertEquals(TopicSignal.ON_TOPIC, ThreadManager.classifyMessage("Ok 3 red flags"));
        assertEquals(TopicSignal.ON_TOPIC, ThreadManager.classifyMessage(
            "Long distance* - if it is only a few states away, then that may be doable"));
        assertEquals(TopicSignal.ON_TOPIC, ThreadManager.classifyMessage(
            "Manipulation- You actively avoid me or use my love and care against me"));
        assertEquals(TopicSignal.ON_TOPIC, ThreadManager.classifyMessage(
            "Lack of Commitment and Communication- I am not ok with a man who doesn't communicate"));
        assertEquals(TopicSignal.ON_TOPIC, ThreadManager.classifyMessage(
            "i am capable of being a master class manipulator, i find it evil and detestable"));
    }

    @Test
    public void gamingAndHobbyChatterIsOffTopic() {
        // The drift that actually happened in the log — long, substantive, no deal-breaker cue.
        assertEquals(TopicSignal.OFF_TOPIC_SUBSTANTIVE, ThreadManager.classifyMessage(
            "But yes! I love talking and gaming for most of my part. Going out and chilling outside is something I enjoy"));
        assertEquals(TopicSignal.OFF_TOPIC_SUBSTANTIVE, ThreadManager.classifyMessage(
            "Same, i am a bit of a fake gamer though, i prefer sandbox games because i like to create"));
        assertEquals(TopicSignal.OFF_TOPIC_SUBSTANTIVE, ThreadManager.classifyMessage(
            "Oh wow! I'm getting tested for ADHD soon, tho it's possible I have more than that haha"));
    }

    @Test
    public void shortReactionsAreTrivial() {
        assertEquals(TopicSignal.TRIVIAL, ThreadManager.classifyMessage(":0"));
        assertEquals(TopicSignal.TRIVIAL, ThreadManager.classifyMessage("Helloooo"));
        assertEquals(TopicSignal.TRIVIAL, ThreadManager.classifyMessage("Ok I'm back :D"));
        assertEquals(TopicSignal.TRIVIAL, ThreadManager.classifyMessage("YES PLEEEAAASSSSEEEE"));
        assertEquals(TopicSignal.TRIVIAL, ThreadManager.classifyMessage("<:WOAH:1385341763547304137>"));
    }

    @Test
    public void mentionsAndEmojiAreStrippedBeforeLengthCheck() {
        // A mention + custom emoji with little real text should read as trivial, not substantive.
        assertEquals(TopicSignal.TRIVIAL,
            ThreadManager.classifyMessage("<@756880689567367299> <:heh_ok:1077649621788139640>"));
    }

    /**
     * Walks the real conversation in timestamp order and confirms the nudge would
     * fire only after the chat genuinely drifts into hobbies — never during the
     * deal-breaker exchange.
     */
    @Test
    public void nudgeFiresOnlyAfterGenuineDrift() {
        String[] convoInOrder = {
            "No this is just another match I'm in lol",                    // off-topic 1
            "Well how are you? Pleased to speak to you again in a formal setting", // off-topic 2
            "Long distance* - faithless - one who holds a differing faith",// ON TOPIC → reset
            "Oh my gosh someone actually responds",                        // off-topic 1
            "Ok awesome! I'll send mine in a bit. I'm currently at the vet",// off-topic 2
            "Ok 3 red flags",                                              // ON TOPIC → reset
            "Cowardice- Manipulation- Lack of Commitment and Communication",// ON TOPIC → reset
            "Sorry for responding so late btw, i was supposed to be off work",// off-topic 1
            "I'm always up late anyways so it's totally fine with me",      // off-topic 2
            "Oh wow! I'm getting tested for ADHD soon, more than that haha",// off-topic 3
            "But yes! I love talking and gaming for most of my part here",  // off-topic 4 → FIRE
            "Same, i am a bit of a fake gamer, i prefer sandbox games"      // would-be off-topic 5
        };

        int streak = 0;
        int firedAtIndex = -1;
        for (int i = 0; i < convoInOrder.length; i++) {
            TopicSignal s = ThreadManager.classifyMessage(convoInOrder[i]);
            if (s == TopicSignal.ON_TOPIC) { streak = 0; continue; }
            if (s == TopicSignal.TRIVIAL) continue;
            streak++;
            if (streak >= 4) { firedAtIndex = i; break; }
        }

        assertTrue("nudge should have fired once drift set in", firedAtIndex >= 0);
        assertEquals("nudge should fire on the gaming message, after deal-breakers were done", 10, firedAtIndex);
    }
}

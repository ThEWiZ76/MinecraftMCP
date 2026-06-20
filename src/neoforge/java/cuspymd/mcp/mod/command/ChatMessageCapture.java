package cuspymd.mcp.mod.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class ChatMessageCapture {
    private static final ChatMessageCapture INSTANCE = new ChatMessageCapture();
    private static final int MAX_RECENT_MESSAGES = 200;
    private final BlockingQueue<CapturedMessage> messageQueue = new LinkedBlockingQueue<>();
    private final List<CapturedMessage> recentMessages = new ArrayList<>();

    public static ChatMessageCapture getInstance() {
        return INSTANCE;
    }

    public void captureMessage(String message, MessageSource source) {
        if (message == null) return;
        CapturedMessage captured = new CapturedMessage(message, System.currentTimeMillis(), source);
        synchronized (recentMessages) {
            recentMessages.add(captured);
            if (recentMessages.size() > MAX_RECENT_MESSAGES) recentMessages.remove(0);
            recentMessages.notifyAll();
        }
        messageQueue.offer(captured);
    }

    public CapturedMessage waitForRecentMessage(long timeoutMs, Predicate<String> filter) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (recentMessages) {
            while (true) {
                for (CapturedMessage message : recentMessages) {
                    if (filter.test(message.text())) return message;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return null;
                recentMessages.wait(Math.min(remaining, 100));
            }
        }
    }

    public List<CapturedMessage> drainAvailableCapturedMessages() {
        List<CapturedMessage> drained = new ArrayList<>();
        messageQueue.drainTo(drained);
        return drained;
    }

    public CapturedMessage waitForCapturedMessage(long timeoutMs) throws InterruptedException {
        return messageQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public enum MessageSource {
        SYSTEM,
        PLAYER_CHAT,
        UNKNOWN
    }

    public record CapturedMessage(String text, long timestampMs, MessageSource source) {
    }
}

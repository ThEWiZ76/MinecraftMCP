package cuspymd.mcp.mod.command;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageCapture {
    private static final ChatMessageCapture INSTANCE = new ChatMessageCapture();
    private static final int MAX_RECENT_MESSAGES = 200;
    private final BlockingQueue<CapturedMessage> messageQueue = new LinkedBlockingQueue<>();
    private final List<CapturedMessage> recentMessages = new ArrayList<>();
    private volatile boolean capturing = false;
    
    public static ChatMessageCapture getInstance() {
        return INSTANCE;
    }
    
    public void captureMessage(String message) {
        captureMessage(message, MessageSource.UNKNOWN);
    }

    public void captureMessage(String message, MessageSource source) {
        if (message == null) {
            return;
        }

        CapturedMessage captured = new CapturedMessage(message, System.currentTimeMillis(), source);
        synchronized (recentMessages) {
            recentMessages.add(captured);
            if (recentMessages.size() > MAX_RECENT_MESSAGES) {
                recentMessages.remove(0);
            }
            recentMessages.notifyAll();
        }

        if (capturing) {
            messageQueue.offer(captured);
        }
    }
    
    public void startCapturing() {
        capturing = true;
        messageQueue.clear();
    }
    
    public void stopCapturing() {
        capturing = false;
    }
    
    public String waitForMessage(long timeoutMs) throws InterruptedException {
        CapturedMessage message = waitForCapturedMessage(timeoutMs);
        return message == null ? null : message.text();
    }
    
    public String waitForMessage(long timeoutMs, Predicate<String> filter) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            CapturedMessage message = messageQueue.poll(100, TimeUnit.MILLISECONDS);
            if (message != null && filter.test(message.text())) {
                return message.text();
            }
        }
        return null;
    }

    public CapturedMessage waitForCapturedMessage(long timeoutMs) throws InterruptedException {
        return messageQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public List<String> drainAvailableMessages() {
        List<String> messages = new ArrayList<>();
        for (CapturedMessage captured : drainAvailableCapturedMessages()) {
            messages.add(captured.text());
        }
        return messages;
    }

    public List<CapturedMessage> drainAvailableCapturedMessages() {
        List<CapturedMessage> drained = new ArrayList<>();
        messageQueue.drainTo(drained);
        return drained;
    }

    public CapturedMessage waitForRecentMessage(long timeoutMs, Predicate<String> filter) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (recentMessages) {
            while (true) {
                for (CapturedMessage message : recentMessages) {
                    if (filter.test(message.text())) {
                        return message;
                    }
                }

                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                recentMessages.wait(Math.min(remaining, 100));
            }
        }
    }

    public List<CapturedMessage> getRecentMessages(long sinceEpochMs) {
        synchronized (recentMessages) {
            return recentMessages.stream()
                .filter(message -> message.timestampMs() >= sinceEpochMs)
                .toList();
        }
    }

    public enum MessageSource {
        SYSTEM,
        PLAYER_CHAT,
        UNKNOWN
    }

    public record CapturedMessage(String text, long timestampMs, MessageSource source) {
    }
}

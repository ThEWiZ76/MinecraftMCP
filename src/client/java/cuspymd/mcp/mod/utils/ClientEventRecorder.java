package cuspymd.mcp.mod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

import java.util.ArrayList;
import java.util.List;

public final class ClientEventRecorder {
    private static final int MAX_EVENTS = 400;
    private static final List<JsonObject> SOUNDS = new ArrayList<>();
    private static final List<JsonObject> PARTICLES = new ArrayList<>();

    private ClientEventRecorder() {
    }

    public static void recordSound(double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        JsonObject event = new JsonObject();
        event.addProperty("timestampMs", System.currentTimeMillis());
        event.addProperty("sound", Registries.SOUND_EVENT.getId(sound).toString());
        event.addProperty("category", category == null ? "unknown" : category.getName());
        event.addProperty("volume", volume);
        event.addProperty("pitch", pitch);
        addPosition(event, x, y, z);
        addEvent(SOUNDS, event);
    }

    public static void recordParticle(ParticleEffect particle, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        JsonObject event = new JsonObject();
        event.addProperty("timestampMs", System.currentTimeMillis());
        event.addProperty("particle", particle == null ? "unknown" : Registries.PARTICLE_TYPE.getId(particle.getType()).toString());
        addPosition(event, x, y, z);
        JsonObject velocity = new JsonObject();
        velocity.addProperty("x", velocityX);
        velocity.addProperty("y", velocityY);
        velocity.addProperty("z", velocityZ);
        event.add("velocity", velocity);
        addEvent(PARTICLES, event);
    }

    public static JsonObject getRecentEvents(double seconds) {
        long since = System.currentTimeMillis() - Math.round(Math.max(0.0, Math.min(seconds, 60.0)) * 1000.0);
        JsonObject result = new JsonObject();
        result.addProperty("sinceEpochMs", since);
        result.add("sounds", copySince(SOUNDS, since));
        result.add("particles", copySince(PARTICLES, since));
        return result;
    }

    private static void addEvent(List<JsonObject> events, JsonObject event) {
        synchronized (events) {
            events.add(event);
            if (events.size() > MAX_EVENTS) {
                events.remove(0);
            }
        }
    }

    private static JsonArray copySince(List<JsonObject> events, long since) {
        JsonArray result = new JsonArray();
        synchronized (events) {
            for (JsonObject event : events) {
                if (event.get("timestampMs").getAsLong() >= since) {
                    result.add(event.deepCopy());
                }
            }
        }
        return result;
    }

    private static void addPosition(JsonObject event, double x, double y, double z) {
        JsonObject position = new JsonObject();
        position.addProperty("x", x);
        position.addProperty("y", y);
        position.addProperty("z", z);
        event.add("position", position);
    }
}

package cuspymd.mcp.mod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.command.ChatMessageCapture;
import cuspymd.mcp.mod.mixin.client.BossBarHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class ClientInteractionUtils {
    private static final int MAX_HOLD_TICKS = 200;
    private static final List<TickTask> TICK_TASKS = new ArrayList<>();

    private ClientInteractionUtils() {
    }

    public static CompletableFuture<JsonObject> attackBlock(JsonObject params) {
        MinecraftClient client = MinecraftClient.getInstance();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                requireWorld(client);
                BlockPos pos = parseBlockPos(params);
                Direction direction = parseDirection(params, "face", Direction.UP);
                String mode = getString(params, "mode", "start").toLowerCase(Locale.ROOT);
                int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);

                switch (mode) {
                    case "release" -> {
                        client.interactionManager.cancelBlockBreaking();
                        future.complete(attackResult(true, mode, pos, direction, false, 0));
                    }
                    case "hold", "break_until_done" -> {
                        client.interactionManager.attackBlock(pos, direction);
                        scheduleBlockAttack(client, pos, direction, ticks == 0 ? MAX_HOLD_TICKS : ticks, "break_until_done".equals(mode), mode, future);
                    }
                    case "start" -> future.complete(attackResult(client.interactionManager.attackBlock(pos, direction), mode, pos, direction, false, 0));
                    default -> throw new IllegalArgumentException("Unsupported attack_block mode: " + mode);
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static CompletableFuture<JsonObject> leftClickAir(JsonObject params) {
        return runOnClientThread(client -> {
            requireWorld(client);
            client.player.swingHand(Hand.MAIN_HAND);
            JsonObject result = new JsonObject();
            result.addProperty("swung", true);
            return result;
        });
    }

    public static CompletableFuture<JsonObject> rightClickBlock(JsonObject params) {
        return runOnClientThread(client -> {
            requireWorld(client);
            BlockPos pos = parseBlockPos(params);
            Direction direction = parseDirection(params, "face", Direction.UP);
            Hand hand = parseHand(params);
            Vec3d hitPos = Vec3d.ofCenter(pos).add(Vec3d.of(direction.getVector()).multiply(0.5));
            ActionResult actionResult = client.interactionManager.interactBlock(client.player, hand, new BlockHitResult(hitPos, direction, pos, false));
            if (actionResult.isAccepted()) {
                client.player.swingHand(hand);
            }

            JsonObject result = new JsonObject();
            result.addProperty("accepted", actionResult.isAccepted());
            result.addProperty("result", actionResult.toString());
            result.add("pos", serializePos(pos));
            result.addProperty("face", direction.asString());
            result.addProperty("hand", hand.name());
            return result;
        });
    }

    public static CompletableFuture<JsonObject> rightClickItem(JsonObject params) {
        return runOnClientThread(client -> {
            requireWorld(client);
            Hand hand = parseHand(params);
            ActionResult actionResult = client.interactionManager.interactItem(client.player, hand);
            if (actionResult.isAccepted()) {
                client.player.swingHand(hand);
            }
            JsonObject result = new JsonObject();
            result.addProperty("accepted", actionResult.isAccepted());
            result.addProperty("result", actionResult.toString());
            result.addProperty("hand", hand.name());
            return result;
        });
    }

    public static CompletableFuture<JsonObject> setHeldSlot(JsonObject params) {
        return runOnClientThread(client -> {
            requireWorld(client);
            int slot = getInt(params, "slot", -1);
            if (slot < 0 || slot > 8) {
                throw new IllegalArgumentException("slot must be between 0 and 8");
            }
            client.player.getInventory().setSelectedSlot(slot);
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            JsonObject result = new JsonObject();
            result.addProperty("selectedSlot", slot);
            return result;
        });
    }

    public static CompletableFuture<JsonObject> movementInput(JsonObject params) {
        return runOnClientThread(client -> {
            boolean pressed = !params.has("pressed") || params.get("pressed").getAsBoolean();
            int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);
            ParsedKeys parsedKeys = parseKeys(client, params);
            List<KeyBinding> keys = parsedKeys.keys();
            for (KeyBinding key : keys) {
                key.setPressed(pressed);
            }
            if (pressed && parsedKeys.hasJump()) {
                client.player.input.jump();
            }
            if (pressed && ticks > 0) {
                scheduleKeyRelease(keys, ticks);
            }
            JsonObject result = new JsonObject();
            result.addProperty("pressed", pressed);
            result.addProperty("ticks", ticks);
            JsonArray keyNames = new JsonArray();
            for (KeyBinding key : keys) {
                keyNames.add(key.getId());
            }
            result.add("keys", keyNames);
            return result;
        });
    }

    public static CompletableFuture<JsonObject> keybindInput(JsonObject params) {
        return runOnClientThread(client -> {
            List<KeyBinding> keyBindings = parseKeyBindings(client, params);
            String action = getString(params, "action", "tap").toLowerCase(Locale.ROOT);
            int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);
            boolean pressed = switch (action) {
                case "tap", "press", "hold" -> true;
                case "release" -> false;
                default -> throw new IllegalArgumentException("Unsupported keybind_input action: " + action);
            };

            for (KeyBinding key : keyBindings) {
                if ("tap".equals(action)) {
                    clickKeyBinding(key);
                    releaseKeyBinding(key);
                } else if (pressed) {
                    pressKeyBinding(key, "press".equals(action));
                } else {
                    releaseKeyBinding(key);
                }
            }
            if ("hold".equals(action) && ticks > 0) {
                scheduleKeyRelease(keyBindings, ticks);
            }

            JsonObject result = new JsonObject();
            result.addProperty("action", action);
            result.addProperty("pressed", pressed);
            result.addProperty("ticks", ticks);
            JsonArray names = new JsonArray();
            for (KeyBinding key : keyBindings) {
                names.add(key.getId());
            }
            result.add("keybinds", names);
            return result;
        });
    }

    public static CompletableFuture<JsonObject> keyboardInput(JsonObject params) {
        return runOnClientThread(client -> {
            if (params == null || !params.has("key")) {
                throw new IllegalArgumentException("Missing required parameter: key");
            }
            int keyCode = keyCodeForName(params.get("key").getAsString());
            int modifiers = parseModifiers(params);
            int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);
            String action = getString(params, "action", "tap").toLowerCase(Locale.ROOT);
            KeyInput input = new KeyInput(keyCode, 0, modifiers);
            Screen screen = client.currentScreen;
            if (screen != null) {
                switch (action) {
                    case "tap" -> {
                        screen.keyPressed(input);
                        screen.keyReleased(input);
                    }
                    case "press", "hold" -> screen.keyPressed(input);
                    case "release" -> screen.keyReleased(input);
                    default -> throw new IllegalArgumentException("Unsupported keyboard_input action: " + action);
                }
            } else {
                InputUtil.Key rawKey = InputUtil.fromKeyCode(input);
                switch (action) {
                    case "tap" -> {
                        KeyBinding.onKeyPressed(rawKey);
                        KeyBinding.setKeyPressed(rawKey, true);
                        KeyBinding.setKeyPressed(rawKey, false);
                    }
                    case "press", "hold" -> KeyBinding.setKeyPressed(rawKey, true);
                    case "release" -> KeyBinding.setKeyPressed(rawKey, false);
                    default -> throw new IllegalArgumentException("Unsupported keyboard_input action: " + action);
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("action", action);
            result.addProperty("keyCode", keyCode);
            result.addProperty("modifiers", modifiers);
            result.addProperty("ticks", ticks);
            result.addProperty("screenEvent", screen != null);
            return result;
        });
    }

    public static CompletableFuture<JsonObject> mouseInput(JsonObject params) {
        return runOnClientThread(client -> {
            Screen screen = client.currentScreen;
            if (screen == null) {
                throw new IllegalStateException("No current screen is open. Use attack_block/right_click_item for in-world mouse actions.");
            }
            String action = getString(params, "action", "click").toLowerCase(Locale.ROOT);
            double x = getDouble(params, "x", screen.width / 2.0);
            double y = getDouble(params, "y", screen.height / 2.0);
            int button = getInt(params, "button", 0);
            Click click = new Click(x, y, new MouseInput(button, 0));
            switch (action) {
                case "click" -> {
                    screen.mouseClicked(click, false);
                    screen.mouseReleased(click);
                }
                case "double_click" -> {
                    screen.mouseClicked(click, false);
                    screen.mouseReleased(click);
                    screen.mouseClicked(click, true);
                    screen.mouseReleased(click);
                }
                case "press" -> screen.mouseClicked(click, false);
                case "release" -> screen.mouseReleased(click);
                case "scroll" -> screen.mouseScrolled(x, y, getDouble(params, "scrollX", 0.0), getDouble(params, "scrollY", 0.0));
                case "drag" -> {
                    double toX = getDouble(params, "toX", x);
                    double toY = getDouble(params, "toY", y);
                    screen.mouseClicked(click, false);
                    screen.mouseDragged(new Click(toX, toY, new MouseInput(button, 0)), toX - x, toY - y);
                    screen.mouseReleased(new Click(toX, toY, new MouseInput(button, 0)));
                }
                default -> throw new IllegalArgumentException("Unsupported mouse_input action: " + action);
            }
            JsonObject result = new JsonObject();
            result.addProperty("action", action);
            result.addProperty("x", x);
            result.addProperty("y", y);
            result.addProperty("button", button);
            result.addProperty("screenClass", screen.getClass().getName());
            return result;
        });
    }

    public static CompletableFuture<JsonObject> lookInput(JsonObject params) {
        return runOnClientThread(client -> {
            requireWorld(client);
            float yaw = params != null && params.has("yaw") ? params.get("yaw").getAsFloat() : client.player.getYaw();
            float pitch = params != null && params.has("pitch") ? params.get("pitch").getAsFloat() : client.player.getPitch();
            yaw += params != null && params.has("deltaYaw") ? params.get("deltaYaw").getAsFloat() : 0.0F;
            pitch += params != null && params.has("deltaPitch") ? params.get("deltaPitch").getAsFloat() : 0.0F;
            pitch = Math.max(-90.0F, Math.min(90.0F, pitch));
            client.player.setYaw(yaw);
            client.player.setPitch(pitch);
            client.player.setHeadYaw(yaw);
            JsonObject result = new JsonObject();
            result.addProperty("yaw", yaw);
            result.addProperty("pitch", pitch);
            return result;
        });
    }

    public static CompletableFuture<JsonObject> openInventory(JsonObject params) {
        return runOnClientThread(client -> {
            requireWorld(client);
            client.setScreen(new InventoryScreen(client.player));
            JsonObject result = new JsonObject();
            result.addProperty("opened", client.currentScreen != null);
            result.addProperty("screenClass", client.currentScreen == null ? "" : client.currentScreen.getClass().getName());
            return result;
        });
    }

    public static CompletableFuture<JsonObject> sneak(JsonObject params) {
        return runOnClientThread(client -> {
            String mode = getString(params, "mode", "press").toLowerCase(Locale.ROOT);
            int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);
            boolean pressed = switch (mode) {
                case "release" -> false;
                case "toggle" -> !client.options.sneakKey.isPressed();
                case "hold", "press" -> true;
                default -> throw new IllegalArgumentException("Unsupported sneak mode: " + mode);
            };
            client.options.sneakKey.setPressed(pressed);
            if ("hold".equals(mode) && ticks > 0) {
                scheduleKeyRelease(List.of(client.options.sneakKey), ticks);
            }
            JsonObject result = new JsonObject();
            result.addProperty("mode", mode);
            result.addProperty("pressed", pressed);
            result.addProperty("ticks", ticks);
            return result;
        });
    }

    public static CompletableFuture<JsonObject> waitForChat(JsonObject params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int timeoutMs = clamp(getInt(params, "timeout_ms", 5000), 0, 60000);
                String literal = params != null && params.has("text") ? params.get("text").getAsString() : null;
                Pattern regex = params != null && params.has("regex") ? Pattern.compile(params.get("regex").getAsString()) : null;
                ChatMessageCapture.CapturedMessage message = ChatMessageCapture.getInstance().waitForRecentMessage(timeoutMs, text -> {
                    if (literal != null && text.contains(literal)) {
                        return true;
                    }
                    return regex != null && regex.matcher(text).find();
                });
                JsonObject result = new JsonObject();
                result.addProperty("matched", message != null);
                if (message != null) {
                    result.add("message", serializeChatMessage(message));
                }
                return result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for chat", e);
            }
        });
    }

    public static CompletableFuture<JsonObject> getScoreboard() {
        return runOnClientThread(client -> {
            requireWorld(client);
            JsonObject result = new JsonObject();
            Scoreboard scoreboard = client.world.getScoreboard();
            ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            result.addProperty("hasSidebar", objective != null);
            if (objective == null) {
                return result;
            }
            result.addProperty("title", objective.getDisplayName().getString());
            JsonArray lines = new JsonArray();
            for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
                if (entry.hidden()) {
                    continue;
                }
                JsonObject line = new JsonObject();
                Text name = entry.name();
                line.addProperty("owner", entry.owner());
                line.addProperty("display", name == null ? "" : name.getString());
                line.addProperty("score", entry.value());
                lines.add(line);
            }
            result.add("lines", lines);
            return result;
        });
    }

    public static CompletableFuture<JsonObject> getNearbyEntities(JsonObject params) {
        return runOnClientThread(client -> {
            requireWorld(client);
            double radius = Math.max(0.0, Math.min(getDouble(params, "radius", 16.0), 128.0));
            double radiusSquared = radius * radius;
            JsonArray entities = new JsonArray();
            Vec3d playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
            for (Entity entity : client.world.getEntities()) {
                if (entity == client.player || entity.squaredDistanceTo(playerPos) > radiusSquared) {
                    continue;
                }
                JsonObject entityJson = new JsonObject();
                entityJson.addProperty("id", entity.getId());
                entityJson.addProperty("uuid", entity.getUuidAsString());
                entityJson.addProperty("type", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
                entityJson.addProperty("name", entity.getName().getString());
                entityJson.addProperty("distance", Math.sqrt(entity.squaredDistanceTo(playerPos)));
                entityJson.add("position", serializeVec(new Vec3d(entity.getX(), entity.getY(), entity.getZ())));
                entities.add(entityJson);
            }
            JsonObject result = new JsonObject();
            result.addProperty("radius", radius);
            result.add("entities", entities);
            return result;
        });
    }

    public static CompletableFuture<JsonObject> getClientDisconnect() {
        return CompletableFuture.completedFuture(ClientDisconnectState.snapshot());
    }

    public static CompletableFuture<JsonObject> getBossbarActionbarTitles() {
        return runOnClientThread(client -> {
            JsonObject result = ClientHudState.snapshot();
            JsonArray bossbars = new JsonArray();
            if (client.inGameHud != null) {
                Map<UUID, BossBar> visibleBars = ((BossBarHudAccessor) client.inGameHud.getBossBarHud()).getBossBars();
                for (Map.Entry<UUID, BossBar> entry : visibleBars.entrySet()) {
                    JsonObject bar = new JsonObject();
                    bar.addProperty("uuid", entry.getKey().toString());
                    bar.addProperty("name", entry.getValue().getName().getString());
                    bar.addProperty("percent", entry.getValue().getPercent());
                    bar.addProperty("color", entry.getValue().getColor().getName());
                    bar.addProperty("style", entry.getValue().getStyle().getName());
                    bossbars.add(bar);
                }
            }
            result.add("bossbars", bossbars);
            return result;
        });
    }

    public static CompletableFuture<JsonObject> getRecentSoundsParticles(JsonObject params) {
        double seconds = getDouble(params, "seconds", 5.0);
        return CompletableFuture.completedFuture(ClientEventRecorder.getRecentEvents(seconds));
    }

    public static void onEndTick(MinecraftClient client) {
        synchronized (TICK_TASKS) {
            Iterator<TickTask> iterator = TICK_TASKS.iterator();
            while (iterator.hasNext()) {
                TickTask task = iterator.next();
                task.tick(client);
                if (task.done()) {
                    iterator.remove();
                }
            }
        }
    }

    private static void scheduleBlockAttack(MinecraftClient client, BlockPos pos, Direction direction, int ticks, boolean untilBroken, String mode, CompletableFuture<JsonObject> future) {
        synchronized (TICK_TASKS) {
            TICK_TASKS.add(new BlockAttackTask(pos, direction, ticks, untilBroken, mode, future));
        }
        client.options.attackKey.setPressed(true);
    }

    private static JsonObject attackResult(boolean accepted, String mode, BlockPos pos, Direction direction, boolean broken, int ticksHeld) {
        JsonObject result = new JsonObject();
        result.addProperty("accepted", accepted);
        result.addProperty("mode", mode);
        result.add("pos", serializePos(pos));
        result.addProperty("face", direction.asString());
        result.addProperty("broken", broken);
        result.addProperty("ticksHeld", ticksHeld);
        return result;
    }

    private static void scheduleKeyRelease(List<KeyBinding> keys, int ticks) {
        synchronized (TICK_TASKS) {
            TICK_TASKS.add(new KeyReleaseTask(keys, ticks));
        }
    }

    private static void pressKeyBinding(KeyBinding key, boolean countClick) {
        InputUtil.Key rawKey = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        if (countClick) {
            KeyBinding.onKeyPressed(rawKey);
        }
        KeyBinding.setKeyPressed(rawKey, true);
        key.setPressed(true);
    }

    private static void clickKeyBinding(KeyBinding key) {
        InputUtil.Key rawKey = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        KeyBinding.onKeyPressed(rawKey);
        KeyBinding.setKeyPressed(rawKey, true);
        key.setPressed(true);
    }

    private static void releaseKeyBinding(KeyBinding key) {
        InputUtil.Key rawKey = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        KeyBinding.setKeyPressed(rawKey, false);
        key.setPressed(false);
    }

    private static CompletableFuture<JsonObject> runOnClientThread(ClientAction action) {
        MinecraftClient client = MinecraftClient.getInstance();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                future.complete(action.run(client));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static void requireWorld(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            throw new IllegalStateException("Player/world/interaction manager not available. Make sure you are in a world.");
        }
    }

    private static BlockPos parseBlockPos(JsonObject params) {
        if (params == null || !params.has("pos")) {
            throw new IllegalArgumentException("Missing required parameter: pos");
        }
        JsonObject pos = params.getAsJsonObject("pos");
        return new BlockPos(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt());
    }

    private static Direction parseDirection(JsonObject params, String field, Direction fallback) {
        if (params == null || !params.has(field)) {
            return fallback;
        }
        try {
            return Direction.valueOf(params.get(field).getAsString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported direction: " + params.get(field).getAsString(), e);
        }
    }

    private static Hand parseHand(JsonObject params) {
        String hand = getString(params, "hand", "main_hand").toLowerCase(Locale.ROOT);
        return "off_hand".equals(hand) || "offhand".equals(hand) ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    private static ParsedKeys parseKeys(MinecraftClient client, JsonObject params) {
        if (params == null || !params.has("keys") || !params.get("keys").isJsonArray()) {
            throw new IllegalArgumentException("Missing required array parameter: keys");
        }
        List<KeyBinding> keys = new ArrayList<>();
        boolean hasJump = false;
        for (JsonElement element : params.getAsJsonArray("keys")) {
            String key = element.getAsString().toLowerCase(Locale.ROOT);
            if ("jump".equals(key) || "space".equals(key)) {
                hasJump = true;
            }
            keys.add(switch (key) {
                case "w", "forward" -> client.options.forwardKey;
                case "s", "back", "backward" -> client.options.backKey;
                case "a", "left" -> client.options.leftKey;
                case "d", "right" -> client.options.rightKey;
                case "jump", "space" -> client.options.jumpKey;
                case "sprint" -> client.options.sprintKey;
                case "sneak", "shift" -> client.options.sneakKey;
                default -> throw new IllegalArgumentException("Unsupported movement key: " + key);
            });
        }
        return new ParsedKeys(keys, hasJump);
    }

    private static List<KeyBinding> parseKeyBindings(MinecraftClient client, JsonObject params) {
        JsonArray values = new JsonArray();
        if (params != null && params.has("keybinds") && params.get("keybinds").isJsonArray()) {
            values = params.getAsJsonArray("keybinds");
        } else if (params != null && params.has("keybind")) {
            values.add(params.get("keybind").getAsString());
        } else {
            throw new IllegalArgumentException("Missing required parameter: keybind or keybinds");
        }
        List<KeyBinding> keys = new ArrayList<>();
        for (JsonElement element : values) {
            keys.add(resolveKeyBinding(client, element.getAsString()));
        }
        return keys;
    }

    private static KeyBinding resolveKeyBinding(MinecraftClient client, String rawName) {
        String name = normalizeKeyName(rawName);
        if (name.startsWith("hotbar")) {
            int slot = Integer.parseInt(name.substring("hotbar".length()));
            if (slot < 1 || slot > 9) {
                throw new IllegalArgumentException("Hotbar keybind must be hotbar1 through hotbar9");
            }
            return client.options.hotbarKeys[slot - 1];
        }
        return switch (name) {
            case "w", "forward", "up" -> client.options.forwardKey;
            case "s", "back", "backward", "down" -> client.options.backKey;
            case "a", "left" -> client.options.leftKey;
            case "d", "right" -> client.options.rightKey;
            case "jump", "space" -> client.options.jumpKey;
            case "sneak", "shift" -> client.options.sneakKey;
            case "sprint" -> client.options.sprintKey;
            case "inventory", "e" -> client.options.inventoryKey;
            case "drop", "q" -> client.options.dropKey;
            case "swap_hands", "swapoffhand", "f" -> client.options.swapHandsKey;
            case "attack", "leftmouse", "left_click" -> client.options.attackKey;
            case "use", "rightmouse", "right_click" -> client.options.useKey;
            case "pick_item", "middlemouse" -> client.options.pickItemKey;
            case "chat", "t" -> client.options.chatKey;
            case "command", "slash" -> client.options.commandKey;
            case "player_list", "tab" -> client.options.playerListKey;
            case "social" -> client.options.socialInteractionsKey;
            case "screenshot", "f2" -> client.options.screenshotKey;
            case "perspective", "toggle_perspective", "f5" -> client.options.togglePerspectiveKey;
            case "smooth_camera" -> client.options.smoothCameraKey;
            case "fullscreen", "f11" -> client.options.fullscreenKey;
            case "advancements", "l" -> client.options.advancementsKey;
            default -> throw new IllegalArgumentException("Unsupported keybind: " + rawName);
        };
    }

    static String normalizeKeyName(String rawName) {
        return rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_").replace(".", "");
    }

    static int keyCodeForName(String rawName) {
        String key = normalizeKeyName(rawName);
        if (key.length() == 1) {
            char c = key.charAt(0);
            if (c >= 'a' && c <= 'z') {
                return GLFW.GLFW_KEY_A + (c - 'a');
            }
            if (c >= '0' && c <= '9') {
                return GLFW.GLFW_KEY_0 + (c - '0');
            }
        }
        if (key.startsWith("f") && key.length() <= 3) {
            int number = Integer.parseInt(key.substring(1));
            if (number >= 1 && number <= 25) {
                return GLFW.GLFW_KEY_F1 + (number - 1);
            }
        }
        return switch (key) {
            case "space" -> GLFW.GLFW_KEY_SPACE;
            case "enter", "return" -> GLFW.GLFW_KEY_ENTER;
            case "escape", "esc" -> GLFW.GLFW_KEY_ESCAPE;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            case "backspace" -> GLFW.GLFW_KEY_BACKSPACE;
            case "delete" -> GLFW.GLFW_KEY_DELETE;
            case "insert" -> GLFW.GLFW_KEY_INSERT;
            case "home" -> GLFW.GLFW_KEY_HOME;
            case "end" -> GLFW.GLFW_KEY_END;
            case "page_up", "pageup" -> GLFW.GLFW_KEY_PAGE_UP;
            case "page_down", "pagedown" -> GLFW.GLFW_KEY_PAGE_DOWN;
            case "up" -> GLFW.GLFW_KEY_UP;
            case "down" -> GLFW.GLFW_KEY_DOWN;
            case "left" -> GLFW.GLFW_KEY_LEFT;
            case "right" -> GLFW.GLFW_KEY_RIGHT;
            case "left_shift", "shift" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "left_control", "control", "ctrl" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "left_alt", "alt" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "slash" -> GLFW.GLFW_KEY_SLASH;
            case "minus" -> GLFW.GLFW_KEY_MINUS;
            case "equal", "equals" -> GLFW.GLFW_KEY_EQUAL;
            case "comma" -> GLFW.GLFW_KEY_COMMA;
            case "period", "dot" -> GLFW.GLFW_KEY_PERIOD;
            case "semicolon" -> GLFW.GLFW_KEY_SEMICOLON;
            case "apostrophe" -> GLFW.GLFW_KEY_APOSTROPHE;
            case "grave", "backtick" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case "left_bracket" -> GLFW.GLFW_KEY_LEFT_BRACKET;
            case "right_bracket" -> GLFW.GLFW_KEY_RIGHT_BRACKET;
            case "backslash" -> GLFW.GLFW_KEY_BACKSLASH;
            default -> throw new IllegalArgumentException("Unsupported keyboard key: " + rawName);
        };
    }

    private static int parseModifiers(JsonObject params) {
        int modifiers = 0;
        if (getBoolean(params, "shift", false)) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (getBoolean(params, "ctrl", false) || getBoolean(params, "control", false)) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (getBoolean(params, "alt", false)) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }
        if (params != null && params.has("modifiers") && params.get("modifiers").isJsonArray()) {
            for (JsonElement element : params.getAsJsonArray("modifiers")) {
                switch (normalizeKeyName(element.getAsString())) {
                    case "shift" -> modifiers |= GLFW.GLFW_MOD_SHIFT;
                    case "ctrl", "control" -> modifiers |= GLFW.GLFW_MOD_CONTROL;
                    case "alt" -> modifiers |= GLFW.GLFW_MOD_ALT;
                    case "super", "meta" -> modifiers |= GLFW.GLFW_MOD_SUPER;
                    default -> throw new IllegalArgumentException("Unsupported keyboard modifier: " + element.getAsString());
                }
            }
        }
        return modifiers;
    }

    private static JsonObject serializeChatMessage(ChatMessageCapture.CapturedMessage message) {
        JsonObject result = new JsonObject();
        result.addProperty("text", message.text());
        result.addProperty("timestampMs", message.timestampMs());
        result.addProperty("source", message.source().name());
        return result;
    }

    private static JsonObject serializePos(BlockPos pos) {
        JsonObject result = new JsonObject();
        result.addProperty("x", pos.getX());
        result.addProperty("y", pos.getY());
        result.addProperty("z", pos.getZ());
        return result;
    }

    private static JsonObject serializeVec(Vec3d vec) {
        JsonObject result = new JsonObject();
        result.addProperty("x", vec.x);
        result.addProperty("y", vec.y);
        result.addProperty("z", vec.z);
        return result;
    }

    private static String getString(JsonObject params, String field, String fallback) {
        return params != null && params.has(field) ? params.get(field).getAsString() : fallback;
    }

    private static int getInt(JsonObject params, String field, int fallback) {
        return params != null && params.has(field) ? params.get(field).getAsInt() : fallback;
    }

    private static double getDouble(JsonObject params, String field, double fallback) {
        return params != null && params.has(field) ? params.get(field).getAsDouble() : fallback;
    }

    private static boolean getBoolean(JsonObject params, String field, boolean fallback) {
        return params != null && params.has(field) ? params.get(field).getAsBoolean() : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    @FunctionalInterface
    private interface ClientAction {
        JsonObject run(MinecraftClient client);
    }

    private interface TickTask {
        void tick(MinecraftClient client);
        boolean done();
    }

    private record ParsedKeys(List<KeyBinding> keys, boolean hasJump) {
    }

    private static final class BlockAttackTask implements TickTask {
        private final BlockPos pos;
        private final Direction direction;
        private final boolean untilBroken;
        private final String mode;
        private final CompletableFuture<JsonObject> future;
        private int remainingTicks;
        private int ticksHeld;
        private boolean done;

        private BlockAttackTask(BlockPos pos, Direction direction, int ticks, boolean untilBroken, String mode, CompletableFuture<JsonObject> future) {
            this.pos = pos;
            this.direction = direction;
            this.remainingTicks = ticks;
            this.untilBroken = untilBroken;
            this.mode = mode;
            this.future = future;
        }

        @Override
        public void tick(MinecraftClient client) {
            if (done || client.interactionManager == null || client.world == null) {
                done = true;
                future.completeExceptionally(new IllegalStateException("Player/world/interaction manager not available during block attack"));
                return;
            }
            client.options.attackKey.setPressed(true);
            client.interactionManager.updateBlockBreakingProgress(pos, direction);
            remainingTicks--;
            ticksHeld++;
            boolean broken = client.world.getBlockState(pos).isAir();
            if (remainingTicks <= 0 || (untilBroken && broken)) {
                client.interactionManager.cancelBlockBreaking();
                client.options.attackKey.setPressed(false);
                done = true;
                future.complete(attackResult(true, mode, pos, direction, broken, ticksHeld));
            }
        }

        @Override
        public boolean done() {
            return done;
        }
    }

    private static final class KeyReleaseTask implements TickTask {
        private final List<KeyBinding> keys;
        private int remainingTicks;
        private boolean done;

        private KeyReleaseTask(List<KeyBinding> keys, int ticks) {
            this.keys = List.copyOf(keys);
            this.remainingTicks = ticks;
        }

        @Override
        public void tick(MinecraftClient client) {
            remainingTicks--;
            if (remainingTicks <= 0) {
                for (KeyBinding key : keys) {
                    releaseKeyBinding(key);
                }
                done = true;
            }
        }

        @Override
        public boolean done() {
            return done;
        }
    }
}

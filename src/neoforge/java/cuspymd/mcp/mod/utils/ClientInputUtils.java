package cuspymd.mcp.mod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.command.ChatMessageCapture;
import cuspymd.mcp.mod.server.MCPProtocol;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class ClientInputUtils {
    private static final int MAX_HOLD_TICKS = 200;
    private static final List<TickTask> TICK_TASKS = new ArrayList<>();

    private ClientInputUtils() {
    }

    public static JsonObject attackBlock(JsonObject params) {
        return await(runOnClient(client -> {
            requireWorldUse(client);
            BlockPos pos = parseBlockPos(params);
            Direction direction = parseDirection(params, "face", Direction.UP);
            String mode = getString(params, "mode", "start").toLowerCase(Locale.ROOT);
            int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);
            return switch (mode) {
                case "release" -> {
                    client.gameMode.stopDestroyBlock();
                    yield attackResult(true, mode, pos, direction, false, 0);
                }
                case "start" -> attackResult(client.gameMode.startDestroyBlock(pos, direction), mode, pos, direction, false, 0);
                case "hold", "break_until_done" -> {
                    client.gameMode.startDestroyBlock(pos, direction);
                    scheduleBlockAttack(pos, direction, ticks == 0 ? MAX_HOLD_TICKS : ticks, "break_until_done".equals(mode), mode);
                    yield attackResult(true, mode, pos, direction, client.level.getBlockState(pos).isAir(), 0);
                }
                default -> throw new IllegalArgumentException("Unsupported attack_block mode: " + mode);
            };
        }), "attack block");
    }

    public static JsonObject leftClickAir(JsonObject params) {
        return await(runOnClient(client -> {
            requirePlayer(client);
            client.player.swing(InteractionHand.MAIN_HAND);
            JsonObject result = new JsonObject();
            result.addProperty("swung", true);
            return result;
        }), "left click air");
    }

    public static JsonObject rightClickBlock(JsonObject params) {
        return await(runOnClient(client -> {
            requireWorldUse(client);
            BlockPos pos = parseBlockPos(params);
            Direction direction = parseDirection(params, "face", Direction.UP);
            InteractionHand hand = parseHand(params);
            Vec3 hitPos = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.5));
            InteractionResult interactionResult = client.gameMode.useItemOn(client.player, hand, new BlockHitResult(hitPos, direction, pos, false));
            if (interactionResult.shouldSwing()) {
                client.player.swing(hand);
            }

            JsonObject result = new JsonObject();
            result.addProperty("accepted", interactionResult.consumesAction());
            result.addProperty("result", interactionResult.name());
            result.add("pos", serializePos(pos));
            result.addProperty("face", direction.getSerializedName());
            result.addProperty("hand", hand.name());
            return result;
        }), "right click block");
    }

    public static JsonObject rightClickItem(JsonObject params) {
        return await(runOnClient(client -> {
            requireWorldUse(client);
            InteractionHand hand = parseHand(params);
            InteractionResult interactionResult = client.gameMode.useItem(client.player, hand);
            if (interactionResult.shouldSwing()) {
                client.player.swing(hand);
            }

            JsonObject result = new JsonObject();
            result.addProperty("accepted", interactionResult.consumesAction());
            result.addProperty("result", interactionResult.name());
            result.addProperty("hand", hand.name());
            return result;
        }), "right click item");
    }

    public static JsonObject setHeldSlot(JsonObject params) {
        return await(runOnClient(client -> {
            requirePlayer(client);
            int slot = params.get("slot").getAsInt();
            if (slot < 0 || slot > 8) throw new IllegalArgumentException("slot must be between 0 and 8");
            client.player.getInventory().selected = slot;
            client.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
            JsonObject result = new JsonObject();
            result.addProperty("selectedSlot", slot);
            return result;
        }), "set held slot");
    }

    public static JsonObject movementInput(JsonObject params) {
        return await(runOnClient(client -> {
            boolean pressed = !params.has("pressed") || params.get("pressed").getAsBoolean();
            int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);
            List<KeyMapping> keys = parseKeys(client, params);
            for (KeyMapping key : keys) key.setDown(pressed);
            if (pressed && ticks > 0) scheduleKeyRelease(keys, ticks);
            JsonObject result = new JsonObject();
            result.addProperty("pressed", pressed);
            result.addProperty("ticks", ticks);
            JsonArray keyNames = new JsonArray();
            for (KeyMapping key : keys) keyNames.add(key.getName());
            result.add("keys", keyNames);
            return result;
        }), "movement input");
    }

    public static JsonObject keybindInput(JsonObject params) {
        return await(runOnClient(client -> {
            List<KeyMapping> keyMappings = parseKeyBindings(client, params);
            String action = getString(params, "action", "tap").toLowerCase(Locale.ROOT);
            int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);
            boolean pressed = switch (action) {
                case "tap", "press", "hold" -> true;
                case "release" -> false;
                default -> throw new IllegalArgumentException("Unsupported keybind_input action: " + action);
            };
            for (KeyMapping key : keyMappings) {
                if ("tap".equals(action)) {
                    clickKeyMapping(key);
                    releaseKeyMapping(key);
                } else if (pressed) {
                    pressKeyMapping(key, "press".equals(action));
                } else {
                    releaseKeyMapping(key);
                }
            }
            if ("hold".equals(action) && ticks > 0) scheduleKeyRelease(keyMappings, ticks);
            JsonObject result = new JsonObject();
            result.addProperty("action", action);
            result.addProperty("pressed", pressed);
            result.addProperty("ticks", ticks);
            JsonArray names = new JsonArray();
            for (KeyMapping key : keyMappings) names.add(key.getName());
            result.add("keybinds", names);
            return result;
        }), "keybind input");
    }

    public static JsonObject keyboardInput(JsonObject params) {
        return await(runOnClient(client -> {
            if (params == null || !params.has("key")) throw new IllegalArgumentException("Missing required parameter: key");
            int keyCode = keyCodeForName(params.get("key").getAsString());
            int modifiers = parseModifiers(params);
            String action = getString(params, "action", "tap").toLowerCase(Locale.ROOT);
            Screen screen = client.screen;
            if (screen != null) {
                switch (action) {
                    case "tap" -> {
                        screen.keyPressed(keyCode, 0, modifiers);
                        screen.keyReleased(keyCode, 0, modifiers);
                    }
                    case "press", "hold" -> screen.keyPressed(keyCode, 0, modifiers);
                    case "release" -> screen.keyReleased(keyCode, 0, modifiers);
                    default -> throw new IllegalArgumentException("Unsupported keyboard_input action: " + action);
                }
            } else {
                InputConstants.Key rawKey = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
                switch (action) {
                    case "tap" -> {
                        KeyMapping.click(rawKey);
                        KeyMapping.set(rawKey, true);
                        KeyMapping.set(rawKey, false);
                    }
                    case "press", "hold" -> KeyMapping.set(rawKey, true);
                    case "release" -> KeyMapping.set(rawKey, false);
                    default -> throw new IllegalArgumentException("Unsupported keyboard_input action: " + action);
                }
            }
            JsonObject result = new JsonObject();
            result.addProperty("action", action);
            result.addProperty("keyCode", keyCode);
            result.addProperty("modifiers", modifiers);
            result.addProperty("screenEvent", screen != null);
            return result;
        }), "keyboard input");
    }

    public static JsonObject mouseInput(JsonObject params) {
        return await(runOnClient(client -> {
            Screen screen = client.screen;
            if (screen == null) throw new IllegalStateException("No current screen is open. Use attack_block/right_click_item for in-world mouse actions.");
            String action = getString(params, "action", "click").toLowerCase(Locale.ROOT);
            double x = getDouble(params, "x", screen.width / 2.0);
            double y = getDouble(params, "y", screen.height / 2.0);
            int button = getInt(params, "button", 0);
            switch (action) {
                case "click" -> {
                    screen.mouseClicked(x, y, button);
                    screen.mouseReleased(x, y, button);
                }
                case "double_click" -> {
                    screen.mouseClicked(x, y, button);
                    screen.mouseReleased(x, y, button);
                    screen.mouseClicked(x, y, button);
                    screen.mouseReleased(x, y, button);
                }
                case "press" -> screen.mouseClicked(x, y, button);
                case "release" -> screen.mouseReleased(x, y, button);
                case "scroll" -> screen.mouseScrolled(x, y, getDouble(params, "scrollX", 0.0), getDouble(params, "scrollY", 0.0));
                case "drag" -> {
                    double toX = getDouble(params, "toX", x);
                    double toY = getDouble(params, "toY", y);
                    screen.mouseClicked(x, y, button);
                    screen.mouseDragged(toX, toY, button, toX - x, toY - y);
                    screen.mouseReleased(toX, toY, button);
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
        }), "mouse input");
    }

    public static JsonObject lookInput(JsonObject params) {
        return await(runOnClient(client -> {
            requirePlayer(client);
            float yaw = params != null && params.has("yaw") ? params.get("yaw").getAsFloat() : client.player.getYRot();
            float pitch = params != null && params.has("pitch") ? params.get("pitch").getAsFloat() : client.player.getXRot();
            yaw += params != null && params.has("deltaYaw") ? params.get("deltaYaw").getAsFloat() : 0.0F;
            pitch += params != null && params.has("deltaPitch") ? params.get("deltaPitch").getAsFloat() : 0.0F;
            pitch = Math.max(-90.0F, Math.min(90.0F, pitch));
            client.player.setYRot(yaw);
            client.player.setXRot(pitch);
            client.player.setYHeadRot(yaw);
            JsonObject result = new JsonObject();
            result.addProperty("yaw", yaw);
            result.addProperty("pitch", pitch);
            return result;
        }), "look input");
    }

    public static JsonObject openInventory(JsonObject params) {
        return await(runOnClient(client -> {
            requirePlayer(client);
            client.setScreen(new InventoryScreen(client.player));
            JsonObject result = new JsonObject();
            result.addProperty("opened", client.screen != null);
            result.addProperty("screenClass", client.screen == null ? "" : client.screen.getClass().getName());
            return result;
        }), "open inventory");
    }

    public static JsonObject sneak(JsonObject params) {
        return await(runOnClient(client -> {
            String mode = getString(params, "mode", "press").toLowerCase(Locale.ROOT);
            int ticks = clamp(getInt(params, "ticks", 1), 0, MAX_HOLD_TICKS);
            boolean pressed = switch (mode) {
                case "release" -> false;
                case "toggle" -> !client.options.keyShift.isDown();
                case "hold", "press" -> true;
                default -> throw new IllegalArgumentException("Unsupported sneak mode: " + mode);
            };
            client.options.keyShift.setDown(pressed);
            if ("hold".equals(mode) && ticks > 0) scheduleKeyRelease(List.of(client.options.keyShift), ticks);
            JsonObject result = new JsonObject();
            result.addProperty("mode", mode);
            result.addProperty("pressed", pressed);
            result.addProperty("ticks", ticks);
            return result;
        }), "sneak");
    }

    public static JsonObject waitForChat(JsonObject params) {
        try {
            int timeoutMs = clamp(getInt(params, "timeout_ms", 5000), 0, 60000);
            String literal = params != null && params.has("text") ? params.get("text").getAsString() : null;
            Pattern regex = params != null && params.has("regex") ? Pattern.compile(params.get("regex").getAsString()) : null;
            ChatMessageCapture.CapturedMessage message = ChatMessageCapture.getInstance().waitForRecentMessage(timeoutMs, text -> {
                if (literal != null && text.contains(literal)) return true;
                return regex != null && regex.matcher(text).find();
            });
            JsonObject result = new JsonObject();
            result.addProperty("matched", message != null);
            if (message != null) {
                JsonObject found = new JsonObject();
                found.addProperty("text", message.text());
                found.addProperty("timestampMs", message.timestampMs());
                found.addProperty("source", message.source().name());
                result.add("message", found);
            }
            return MCPProtocol.createSuccessResponse(result.toString());
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to wait for chat: " + e.getMessage(), null);
        }
    }

    public static JsonObject getScoreboard() {
        return await(runOnClient(client -> {
            requirePlayer(client);
            JsonObject result = new JsonObject();
            Scoreboard scoreboard = client.level.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            result.addProperty("hasSidebar", objective != null);
            if (objective == null) return result;
            result.addProperty("title", objective.getDisplayName().getString());
            JsonArray lines = new JsonArray();
            for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
                JsonObject line = new JsonObject();
                line.addProperty("owner", entry.owner());
                line.addProperty("display", entry.display() == null ? "" : entry.display().getString());
                line.addProperty("score", entry.value());
                lines.add(line);
            }
            result.add("lines", lines);
            return result;
        }), "get scoreboard");
    }

    public static JsonObject getNearbyEntities(JsonObject params) {
        return await(runOnClient(client -> {
            requirePlayer(client);
            double radius = Math.max(0.0, Math.min(getDouble(params, "radius", 16.0), 128.0));
            double radiusSquared = radius * radius;
            Vec3 playerPos = client.player.position();
            JsonArray entities = new JsonArray();
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity == client.player || entity.distanceToSqr(playerPos) > radiusSquared) continue;
                JsonObject entityJson = new JsonObject();
                entityJson.addProperty("id", entity.getId());
                entityJson.addProperty("uuid", entity.getUUID().toString());
                entityJson.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
                entityJson.addProperty("name", entity.getName().getString());
                entityJson.addProperty("distance", Math.sqrt(entity.distanceToSqr(playerPos)));
                JsonObject position = new JsonObject();
                position.addProperty("x", entity.getX());
                position.addProperty("y", entity.getY());
                position.addProperty("z", entity.getZ());
                entityJson.add("position", position);
                entities.add(entityJson);
            }
            JsonObject result = new JsonObject();
            result.addProperty("radius", radius);
            result.add("entities", entities);
            return result;
        }), "get nearby entities");
    }

    public static JsonObject getClientDisconnect() {
        JsonObject result = new JsonObject();
        result.addProperty("hasDisconnect", false);
        result.addProperty("title", "");
        result.addProperty("reason", "");
        result.addProperty("exception", "");
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    public static JsonObject getBossbarActionbarTitles() {
        JsonObject result = new JsonObject();
        result.addProperty("actionbar", "");
        result.addProperty("title", "");
        result.addProperty("subtitle", "");
        result.add("bossbars", new JsonArray());
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    public static JsonObject getRecentSoundsParticles(JsonObject params) {
        JsonObject result = new JsonObject();
        result.addProperty("seconds", getDouble(params, "seconds", 5.0));
        result.add("sounds", new JsonArray());
        result.add("particles", new JsonArray());
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    public static void onClientTick(Minecraft client) {
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

    private static void scheduleBlockAttack(BlockPos pos, Direction direction, int ticks, boolean untilBroken, String mode) {
        synchronized (TICK_TASKS) {
            TICK_TASKS.add(new BlockAttackTask(pos, direction, ticks, untilBroken, mode));
        }
        Minecraft.getInstance().options.keyAttack.setDown(true);
    }

    private static CompletableFuture<JsonObject> runOnClient(ClientAction action) {
        Minecraft client = Minecraft.getInstance();
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

    private static JsonObject await(CompletableFuture<JsonObject> future, String operationName) {
        try {
            return MCPProtocol.createSuccessResponse(future.get().toString());
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to " + operationName + ": " + e.getMessage(), null);
        }
    }

    private static void requirePlayer(Minecraft client) {
        if (client.player == null || client.level == null) throw new IllegalStateException("Player/world not available");
    }

    private static void requireWorldUse(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            throw new IllegalStateException("Player/world/game mode not available");
        }
    }

    private static JsonObject attackResult(boolean accepted, String mode, BlockPos pos, Direction direction, boolean broken, int ticksHeld) {
        JsonObject result = new JsonObject();
        result.addProperty("accepted", accepted);
        result.addProperty("mode", mode);
        result.add("pos", serializePos(pos));
        result.addProperty("face", direction.getSerializedName());
        result.addProperty("broken", broken);
        result.addProperty("ticksHeld", ticksHeld);
        return result;
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

    private static InteractionHand parseHand(JsonObject params) {
        String hand = getString(params, "hand", "main_hand").toLowerCase(Locale.ROOT);
        return "off_hand".equals(hand) || "offhand".equals(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static List<KeyMapping> parseKeys(Minecraft client, JsonObject params) {
        if (params == null || !params.has("keys") || !params.get("keys").isJsonArray()) {
            throw new IllegalArgumentException("Missing required array parameter: keys");
        }
        List<KeyMapping> keys = new ArrayList<>();
        for (JsonElement element : params.getAsJsonArray("keys")) {
            String key = element.getAsString().toLowerCase(Locale.ROOT);
            keys.add(switch (key) {
                case "w", "forward" -> client.options.keyUp;
                case "s", "back", "backward" -> client.options.keyDown;
                case "a", "left" -> client.options.keyLeft;
                case "d", "right" -> client.options.keyRight;
                case "jump", "space" -> client.options.keyJump;
                case "sprint" -> client.options.keySprint;
                case "sneak", "shift" -> client.options.keyShift;
                default -> throw new IllegalArgumentException("Unsupported movement key: " + key);
            });
        }
        return keys;
    }

    private static List<KeyMapping> parseKeyBindings(Minecraft client, JsonObject params) {
        JsonArray values = new JsonArray();
        if (params != null && params.has("keybinds") && params.get("keybinds").isJsonArray()) {
            values = params.getAsJsonArray("keybinds");
        } else if (params != null && params.has("keybind")) {
            values.add(params.get("keybind").getAsString());
        } else {
            throw new IllegalArgumentException("Missing required parameter: keybind or keybinds");
        }
        List<KeyMapping> keys = new ArrayList<>();
        for (JsonElement element : values) keys.add(resolveKeyMapping(client, element.getAsString()));
        return keys;
    }

    private static KeyMapping resolveKeyMapping(Minecraft client, String rawName) {
        String name = normalizeKeyName(rawName);
        if (name.startsWith("hotbar")) {
            int slot = Integer.parseInt(name.substring("hotbar".length()));
            if (slot < 1 || slot > 9) throw new IllegalArgumentException("Hotbar keybind must be hotbar1 through hotbar9");
            return client.options.keyHotbarSlots[slot - 1];
        }
        return switch (name) {
            case "w", "forward", "up" -> client.options.keyUp;
            case "s", "back", "backward", "down" -> client.options.keyDown;
            case "a", "left" -> client.options.keyLeft;
            case "d", "right" -> client.options.keyRight;
            case "jump", "space" -> client.options.keyJump;
            case "sneak", "shift" -> client.options.keyShift;
            case "sprint" -> client.options.keySprint;
            case "inventory", "e" -> client.options.keyInventory;
            case "drop", "q" -> client.options.keyDrop;
            case "swap_hands", "swapoffhand", "f" -> client.options.keySwapOffhand;
            case "attack", "leftmouse", "left_click" -> client.options.keyAttack;
            case "use", "rightmouse", "right_click" -> client.options.keyUse;
            case "pick_item", "middlemouse" -> client.options.keyPickItem;
            case "chat", "t" -> client.options.keyChat;
            case "command", "slash" -> client.options.keyCommand;
            case "player_list", "tab" -> client.options.keyPlayerList;
            case "social" -> client.options.keySocialInteractions;
            case "screenshot", "f2" -> client.options.keyScreenshot;
            case "perspective", "toggle_perspective", "f5" -> client.options.keyTogglePerspective;
            case "smooth_camera" -> client.options.keySmoothCamera;
            case "fullscreen", "f11" -> client.options.keyFullscreen;
            case "advancements", "l" -> client.options.keyAdvancements;
            default -> throw new IllegalArgumentException("Unsupported keybind: " + rawName);
        };
    }

    private static void pressKeyMapping(KeyMapping key, boolean countClick) {
        if (countClick) KeyMapping.click(key.getKey());
        KeyMapping.set(key.getKey(), true);
        key.setDown(true);
    }

    private static void clickKeyMapping(KeyMapping key) {
        KeyMapping.click(key.getKey());
        KeyMapping.set(key.getKey(), true);
        key.setDown(true);
    }

    private static void releaseKeyMapping(KeyMapping key) {
        KeyMapping.set(key.getKey(), false);
        key.setDown(false);
    }

    private static void scheduleKeyRelease(List<KeyMapping> keys, int ticks) {
        synchronized (TICK_TASKS) {
            TICK_TASKS.add(new KeyReleaseTask(List.copyOf(keys), ticks));
        }
    }

    static String normalizeKeyName(String rawName) {
        return rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_").replace(".", "");
    }

    static int keyCodeForName(String rawName) {
        String key = normalizeKeyName(rawName);
        if (key.length() == 1) {
            char c = key.charAt(0);
            if (c >= 'a' && c <= 'z') return GLFW.GLFW_KEY_A + (c - 'a');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }
        if (key.startsWith("f") && key.length() <= 3) {
            int number = Integer.parseInt(key.substring(1));
            if (number >= 1 && number <= 25) return GLFW.GLFW_KEY_F1 + (number - 1);
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
        if (getBoolean(params, "shift", false)) modifiers |= GLFW.GLFW_MOD_SHIFT;
        if (getBoolean(params, "ctrl", false) || getBoolean(params, "control", false)) modifiers |= GLFW.GLFW_MOD_CONTROL;
        if (getBoolean(params, "alt", false)) modifiers |= GLFW.GLFW_MOD_ALT;
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

    private static String getString(JsonObject params, String field, String fallback) {
        return params != null && params.has(field) ? params.get(field).getAsString() : fallback;
    }

    private static JsonObject serializePos(BlockPos pos) {
        JsonObject result = new JsonObject();
        result.addProperty("x", pos.getX());
        result.addProperty("y", pos.getY());
        result.addProperty("z", pos.getZ());
        return result;
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
        JsonObject run(Minecraft client);
    }

    private interface TickTask {
        void tick(Minecraft client);
        boolean done();
    }

    private static final class KeyReleaseTask implements TickTask {
        private final List<KeyMapping> keys;
        private int remainingTicks;
        private boolean done;

        private KeyReleaseTask(List<KeyMapping> keys, int remainingTicks) {
            this.keys = keys;
            this.remainingTicks = remainingTicks;
        }

        @Override
        public void tick(Minecraft client) {
            remainingTicks--;
            if (remainingTicks <= 0) {
                for (KeyMapping key : keys) releaseKeyMapping(key);
                done = true;
            }
        }

        @Override
        public boolean done() {
            return done;
        }
    }

    private static final class BlockAttackTask implements TickTask {
        private final BlockPos pos;
        private final Direction direction;
        private final boolean untilBroken;
        private final String mode;
        private int remainingTicks;
        private int ticksHeld;
        private boolean done;

        private BlockAttackTask(BlockPos pos, Direction direction, int ticks, boolean untilBroken, String mode) {
            this.pos = pos;
            this.direction = direction;
            this.remainingTicks = ticks;
            this.untilBroken = untilBroken;
            this.mode = mode;
        }

        @Override
        public void tick(Minecraft client) {
            if (client.gameMode == null || client.level == null) {
                done = true;
                return;
            }
            client.options.keyAttack.setDown(true);
            client.gameMode.continueDestroyBlock(pos, direction);
            remainingTicks--;
            ticksHeld++;
            boolean broken = client.level.getBlockState(pos).isAir();
            if (remainingTicks <= 0 || (untilBroken && broken)) {
                client.gameMode.stopDestroyBlock();
                client.options.keyAttack.setDown(false);
                done = true;
            }
        }

        @Override
        public boolean done() {
            return done;
        }
    }
}

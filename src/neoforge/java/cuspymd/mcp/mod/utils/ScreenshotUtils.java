package cuspymd.mcp.mod.utils;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import cuspymd.mcp.mod.config.MCPConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ScreenshotUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenshotUtils.class);
    static final List<DeferredTask> pendingDeferredTasks = Collections.synchronizedList(new ArrayList<>());

    static final class DeferredTask {
        private final Runnable runnable;
        private int remainingTicks;

        DeferredTask(Runnable runnable, int ticks) {
            this.runnable = runnable;
            this.remainingTicks = ticks;
        }
    }

    private ScreenshotUtils() {
    }

    public static CompletableFuture<String> takeScreenshot(JsonObject params) {
        Minecraft client = Minecraft.getInstance();
        CompletableFuture<String> future = new CompletableFuture<>();
        JsonObject safeParams = params != null ? params : new JsonObject();

        if (!client.isSameThread()) {
            client.execute(() -> takeScreenshotInternal(client, safeParams, future));
        } else {
            takeScreenshotInternal(client, safeParams, future);
        }

        return future;
    }

    private static void takeScreenshotInternal(Minecraft client, JsonObject params, CompletableFuture<String> future) {
        try {
            if (client.player == null) {
                future.completeExceptionally(new Exception("Player not found. Make sure you are in a world."));
                return;
            }

            String validationError = validateCoordinates(params);
            if (validationError != null) {
                future.completeExceptionally(new Exception(validationError));
                return;
            }

            boolean moved = false;
            boolean hasX = params.has("x");
            boolean hasY = params.has("y");
            boolean hasZ = params.has("z");

            if (hasX && hasY && hasZ) {
                double x = params.get("x").getAsDouble();
                double y = params.get("y").getAsDouble();
                double z = params.get("z").getAsDouble();
                float yaw = params.has("yaw") ? params.get("yaw").getAsFloat() : client.player.getYRot();
                float pitch = params.has("pitch") ? params.get("pitch").getAsFloat() : client.player.getXRot();

                client.player.moveTo(x, y, z, yaw, pitch);
                LOGGER.info("Moved player to {} {} {} (yaw: {}, pitch: {}) for screenshot", x, y, z, yaw, pitch);
                moved = true;
            } else {
                if (params.has("yaw")) {
                    client.player.setYRot(params.get("yaw").getAsFloat());
                    moved = true;
                }
                if (params.has("pitch")) {
                    client.player.setXRot(params.get("pitch").getAsFloat());
                    moved = true;
                }
            }

            if (moved) {
                pendingDeferredTasks.add(new DeferredTask(() -> captureNow(client, future), 2));
            } else {
                captureNow(client, future);
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error taking screenshot", e);
            future.completeExceptionally(e);
        }
    }

    static String validateCoordinates(JsonObject params) {
        boolean hasX = params.has("x");
        boolean hasY = params.has("y");
        boolean hasZ = params.has("z");

        if ((hasX || hasY || hasZ) && !(hasX && hasY && hasZ)) {
            return "Partial coordinates provided. You must provide all three: x, y, and z.";
        }
        return null;
    }

    public static void onClientTick(Minecraft client) {
        synchronized (pendingDeferredTasks) {
            Iterator<DeferredTask> iterator = pendingDeferredTasks.iterator();
            while (iterator.hasNext()) {
                DeferredTask task = iterator.next();
                task.remainingTicks--;
                if (task.remainingTicks <= 0) {
                    try {
                        task.runnable.run();
                    } catch (Exception e) {
                        LOGGER.error("Error executing deferred screenshot task", e);
                    }
                    iterator.remove();
                }
            }
        }
    }

    static String encodeBytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static void captureNow(Minecraft client, CompletableFuture<String> future) {
        NativeImage nativeImage = null;
        Path tempFile = null;
        try {
            nativeImage = Screenshot.takeScreenshot(client.getMainRenderTarget());
            tempFile = Files.createTempFile("mcp_screenshot", ".png");
            nativeImage.writeToFile(tempFile);

            byte[] bytes = Files.readAllBytes(tempFile);
            if (MCPConfig.load().getClient().isSaveScreenshotsForDebug()) {
                saveDebugScreenshot(client, tempFile);
            }
            future.complete(encodeBytesToBase64(bytes));
        } catch (IOException e) {
            LOGGER.error("IO error while processing screenshot", e);
            future.completeExceptionally(e);
        } catch (Exception e) {
            LOGGER.error("Error during screenshot capture", e);
            future.completeExceptionally(e);
        } finally {
            if (nativeImage != null) {
                nativeImage.close();
            }
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void saveDebugScreenshot(Minecraft client, Path tempFile) {
        try {
            Path debugDir = client.gameDirectory.toPath().resolve("mcp_debug_screenshots");
            Files.createDirectories(debugDir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            Path targetFile = debugDir.resolve("screenshot_" + timestamp + ".png");
            Files.copy(tempFile, targetFile);
            LOGGER.info("Saved debug screenshot to: {}", targetFile.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to save debug screenshot", e);
        }
    }
}

package cuspymd.mcp.mod.utils;

import com.google.gson.JsonObject;
import cuspymd.mcp.mod.server.MCPProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class PlayerInfoProvider {
    private PlayerInfoProvider() {
    }

    public static JsonObject getPlayerInfoResponse() {
        JsonObject playerInfo = getPlayerInfo();
        if (playerInfo.has("error")) {
            return MCPProtocol.createErrorResponse(playerInfo.get("error").getAsString(), null);
        }
        return MCPProtocol.createSuccessResponse(playerInfo.toString());
    }

    public static JsonObject getPlayerInfo() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        JsonObject result = new JsonObject();
        if (player == null) {
            result.addProperty("error", "No player found");
            return result;
        }

        JsonObject position = new JsonObject();
        position.addProperty("x", player.getX());
        position.addProperty("y", player.getY());
        position.addProperty("z", player.getZ());
        result.add("position", position);

        JsonObject blockPosition = new JsonObject();
        blockPosition.addProperty("x", player.blockPosition().getX());
        blockPosition.addProperty("y", player.blockPosition().getY());
        blockPosition.addProperty("z", player.blockPosition().getZ());
        result.add("blockPosition", blockPosition);

        JsonObject rotation = new JsonObject();
        rotation.addProperty("yaw", player.getYRot());
        rotation.addProperty("pitch", player.getXRot());
        result.add("rotation", rotation);
        result.addProperty("facingDirection", getCardinalDirection(player.getYRot()));

        Vec3 look = player.getLookAngle();
        JsonObject lookVector = new JsonObject();
        lookVector.addProperty("x", look.x);
        lookVector.addProperty("y", look.y);
        lookVector.addProperty("z", look.z);
        result.add("lookVector", lookVector);

        JsonObject frontPosition = new JsonObject();
        frontPosition.addProperty("x", (int) Math.floor(player.getX() + look.x * 3.0));
        frontPosition.addProperty("y", (int) Math.floor(player.getY() + look.y * 3.0));
        frontPosition.addProperty("z", (int) Math.floor(player.getZ() + look.z * 3.0));
        result.add("frontPosition", frontPosition);

        result.addProperty("health", player.getHealth());
        result.addProperty("maxHealth", player.getMaxHealth());
        result.addProperty("foodLevel", player.getFoodData().getFoodLevel());
        result.addProperty("saturation", player.getFoodData().getSaturationLevel());
        if (client.gameMode != null) {
            result.addProperty("gameMode", client.gameMode.getPlayerMode().getName());
        }
        if (client.level != null) {
            result.addProperty("dimension", client.level.dimension().location().toString());
            result.addProperty("timeOfDay", client.level.getDayTime());
            result.addProperty("isDay", client.level.isDay());
            result.addProperty("isNight", client.level.isNight());
        }
        result.addProperty("name", player.getName().getString());
        result.addProperty("experienceLevel", player.experienceLevel);
        result.addProperty("experienceProgress", player.experienceProgress);
        result.addProperty("totalExperience", player.totalExperience);

        JsonObject inventory = new JsonObject();
        inventory.addProperty("selectedSlot", player.getInventory().selected);
        inventory.addProperty("mainHandItem", itemName(player.getMainHandItem()));
        inventory.addProperty("offHandItem", itemName(player.getOffhandItem()));
        result.add("inventory", inventory);
        return result;
    }

    private static String itemName(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String getCardinalDirection(float yaw) {
        yaw = yaw % 360;
        if (yaw < 0) yaw += 360;
        if (yaw >= 337.5 || yaw < 22.5) return "South";
        if (yaw < 67.5) return "Southwest";
        if (yaw < 112.5) return "West";
        if (yaw < 157.5) return "Northwest";
        if (yaw < 202.5) return "North";
        if (yaw < 247.5) return "Northeast";
        if (yaw < 292.5) return "East";
        return "Southeast";
    }
}

package cuspymd.mcp.mod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cuspymd.mcp.mod.server.MCPProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.TreeMap;

public final class BlockScanner {
    private BlockScanner() {
    }

    public static JsonObject getBlocksInArea(JsonObject arguments, int maxAreaSize) {
        try {
            if (!arguments.has("from") || !arguments.has("to")) {
                return MCPProtocol.createErrorResponse("Missing required parameters: from and to", null);
            }
            JsonObject scan = scanBlocksInArea(arguments.getAsJsonObject("from"), arguments.getAsJsonObject("to"), maxAreaSize);
            if (scan.has("error")) return MCPProtocol.createErrorResponse(scan.get("error").getAsString(), null);
            return MCPProtocol.createSuccessResponse(scan.toString());
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to get blocks in area: " + e.getMessage(), null);
        }
    }

    public static JsonObject scanBlocksInArea(JsonObject fromPos, JsonObject toPos, int maxAreaSize) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return error("World not available");
        int minX = Math.min(fromPos.get("x").getAsInt(), toPos.get("x").getAsInt());
        int minY = Math.min(fromPos.get("y").getAsInt(), toPos.get("y").getAsInt());
        int minZ = Math.min(fromPos.get("z").getAsInt(), toPos.get("z").getAsInt());
        int maxX = Math.max(fromPos.get("x").getAsInt(), toPos.get("x").getAsInt());
        int maxY = Math.max(fromPos.get("y").getAsInt(), toPos.get("y").getAsInt());
        int maxZ = Math.max(fromPos.get("z").getAsInt(), toPos.get("z").getAsInt());
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        if (sizeX > maxAreaSize || sizeY > maxAreaSize || sizeZ > maxAreaSize) {
            return error("Area too large. Maximum size per axis: " + maxAreaSize + " blocks. Requested: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }

        JsonObject result = new JsonObject();
        JsonObject area = new JsonObject();
        area.add("from", pos(minX, minY, minZ));
        area.add("to", pos(maxX, maxY, maxZ));
        area.addProperty("size", sizeX + "x" + sizeY + "x" + sizeZ);
        result.add("area", area);

        Map<String, JsonArray> byType = new TreeMap<>();
        int total = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = client.level.getBlockState(new BlockPos(x, y, z));
                    if (state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR) || state.is(Blocks.VOID_AIR)) continue;
                    String type = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    byType.computeIfAbsent(type, ignored -> new JsonArray()).add(pos(x, y, z));
                    total++;
                }
            }
        }

        JsonObject blocks = new JsonObject();
        for (Map.Entry<String, JsonArray> entry : byType.entrySet()) blocks.add(entry.getKey(), entry.getValue());
        result.addProperty("total_blocks", total);
        result.add("blocks", blocks);
        return result;
    }

    private static JsonObject pos(int x, int y, int z) {
        JsonObject object = new JsonObject();
        object.addProperty("x", x);
        object.addProperty("y", y);
        object.addProperty("z", z);
        return object;
    }

    private static JsonObject error(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error;
    }
}

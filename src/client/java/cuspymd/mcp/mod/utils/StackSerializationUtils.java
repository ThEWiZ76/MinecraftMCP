package cuspymd.mcp.mod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.Component;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.Text;

public final class StackSerializationUtils {
    private StackSerializationUtils() {
    }

    public static JsonObject serializeStack(ItemStack stack) {
        JsonObject result = new JsonObject();
        boolean present = stack != null && !stack.isEmpty();
        result.addProperty("present", present);
        if (!present) {
            return result;
        }

        result.addProperty("itemId", Registries.ITEM.getId(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        result.addProperty("maxCount", stack.getMaxCount());
        result.addProperty("displayName", stack.getName().getString());
        result.addProperty("customName", textValue(stack.get(DataComponentTypes.CUSTOM_NAME)));
        result.addProperty("itemName", textValue(stack.get(DataComponentTypes.ITEM_NAME)));
        result.addProperty("damage", stack.isDamageable() ? stack.getDamage() : 0);
        result.addProperty("maxDamage", stack.isDamageable() ? stack.getMaxDamage() : 0);
        result.addProperty("isDamageable", stack.isDamageable());
        result.addProperty("isDamaged", stack.isDamaged());
        result.addProperty("hasGlint", stack.hasGlint());
        result.add("lore", loreLines(stack.get(DataComponentTypes.LORE)));

        result.addProperty("componentPatch", stack.getComponentChanges().toString());
        result.add("components", serializeComponents(stack));
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null && !customData.isEmpty()) {
            result.addProperty("customData", customData.copyNbt().toString());
        }
        encodeStack(stack, result);
        return result;
    }

    private static JsonArray serializeComponents(ItemStack stack) {
        JsonArray components = new JsonArray();
        for (Component<?> component : stack.getComponents()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", Registries.DATA_COMPONENT_TYPE.getId(component.type()).toString());
            Object value = component.value();
            item.addProperty("value", value == null ? "null" : value.toString());
            item.addProperty("valueClass", value == null ? "" : value.getClass().getName());
            item.addProperty("persistent", component.type().getCodec() != null);
            components.add(item);
        }
        return components;
    }

    private static void encodeStack(ItemStack stack, JsonObject result) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            DynamicOps<NbtElement> ops = client.world == null
                ? NbtOps.INSTANCE
                : RegistryOps.of(NbtOps.INSTANCE, client.world.getRegistryManager());
            ItemStack.CODEC.encodeStart(ops, stack)
                .resultOrPartial(error -> result.addProperty("encodedNbtError", error))
                .ifPresent(nbt -> result.addProperty("encodedNbt", nbt.toString()));
        } catch (Exception e) {
            result.addProperty("encodedNbtError", e.getMessage());
        }
    }

    private static JsonArray loreLines(LoreComponent lore) {
        JsonArray lines = new JsonArray();
        if (lore == null) {
            return lines;
        }
        for (Text line : lore.lines()) {
            lines.add(line.getString());
        }
        return lines;
    }

    private static String textValue(Text text) {
        return text == null ? "" : text.getString();
    }
}

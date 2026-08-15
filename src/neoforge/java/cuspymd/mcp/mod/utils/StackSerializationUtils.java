package cuspymd.mcp.mod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

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

        result.addProperty("itemId", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        result.addProperty("maxCount", stack.getMaxStackSize());
        result.addProperty("displayName", stack.getHoverName().getString());
        result.addProperty("customName", textValue(stack.get(DataComponents.CUSTOM_NAME)));
        result.addProperty("itemName", textValue(stack.get(DataComponents.ITEM_NAME)));
        result.addProperty("damage", stack.isDamageableItem() ? stack.getDamageValue() : 0);
        result.addProperty("maxDamage", stack.isDamageableItem() ? stack.getMaxDamage() : 0);
        result.addProperty("isDamageable", stack.isDamageableItem());
        result.addProperty("isDamaged", stack.isDamaged());
        result.addProperty("hasGlint", stack.hasFoil());
        result.add("lore", loreLines(stack.get(DataComponents.LORE)));

        result.addProperty("componentPatch", stack.getComponentsPatch().toString());
        result.add("components", serializeComponents(stack));
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && !customData.isEmpty()) {
            result.addProperty("customData", customData.copyTag().toString());
        }
        encodeStack(stack, result);
        return result;
    }

    private static JsonArray serializeComponents(ItemStack stack) {
        JsonArray components = new JsonArray();
        for (TypedDataComponent<?> component : stack.getComponents()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).toString());
            Object value = component.value();
            item.addProperty("value", value == null ? "null" : value.toString());
            item.addProperty("valueClass", value == null ? "" : value.getClass().getName());
            item.addProperty("persistent", !component.type().isTransient());
            components.add(item);
        }
        return components;
    }

    private static void encodeStack(ItemStack stack, JsonObject result) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) {
                return;
            }
            DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, client.level.registryAccess());
            ItemStack.CODEC.encodeStart(ops, stack)
                .resultOrPartial(error -> result.addProperty("encodedNbtError", error))
                .ifPresent(nbt -> result.addProperty("encodedNbt", nbt.toString()));
            result.addProperty("savedNbt", stack.saveOptional(client.level.registryAccess()).toString());
        } catch (Exception e) {
            result.addProperty("encodedNbtError", e.getMessage());
        }
    }

    private static JsonArray loreLines(ItemLore lore) {
        JsonArray lines = new JsonArray();
        if (lore == null) {
            return lines;
        }
        for (Component line : lore.lines()) {
            lines.add(line.getString());
        }
        return lines;
    }

    private static String textValue(Component text) {
        return text == null ? "" : text.getString();
    }
}

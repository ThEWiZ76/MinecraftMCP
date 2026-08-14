package cuspymd.mcp.mod.utils;

import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ScreenAutomationUtilsTest {

    @Test
    public void parseActionTypeDefaultsToPickup() {
        assertEquals(SlotActionType.PICKUP, ScreenAutomationUtils.parseActionType(null));
        assertEquals(SlotActionType.PICKUP, ScreenAutomationUtils.parseActionType("  "));
    }

    @Test
    public void parseActionTypeNormalizesCase() {
        assertEquals(SlotActionType.QUICK_MOVE, ScreenAutomationUtils.parseActionType("quick_move"));
        assertEquals(SlotActionType.THROW, ScreenAutomationUtils.parseActionType("Throw"));
    }

    @Test
    public void parseActionTypeRejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> ScreenAutomationUtils.parseActionType("nope"));
    }

    @Test
    public void keyCodeForCharacterMapsCommonTextInput() {
        assertEquals(GLFW.GLFW_KEY_A, ScreenAutomationUtils.keyCodeForCharacter('a'));
        assertEquals(GLFW.GLFW_KEY_A, ScreenAutomationUtils.keyCodeForCharacter('A'));
        assertEquals(GLFW.GLFW_KEY_1, ScreenAutomationUtils.keyCodeForCharacter('1'));
        assertEquals(GLFW.GLFW_KEY_SPACE, ScreenAutomationUtils.keyCodeForCharacter(' '));
        assertEquals(GLFW.GLFW_KEY_SLASH, ScreenAutomationUtils.keyCodeForCharacter('?'));
        assertEquals(GLFW.GLFW_KEY_UNKNOWN, ScreenAutomationUtils.keyCodeForCharacter('\u00e9'));
    }
}

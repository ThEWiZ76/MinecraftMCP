package cuspymd.mcp.mod.utils;

import net.minecraft.screen.slot.SlotActionType;
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
}

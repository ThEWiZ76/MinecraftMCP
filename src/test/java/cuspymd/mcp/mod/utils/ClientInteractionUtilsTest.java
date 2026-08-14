package cuspymd.mcp.mod.utils;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientInteractionUtilsTest {
    @Test
    public void keyCodeForNameHandlesCommonKeys() {
        assertEquals(GLFW.GLFW_KEY_E, ClientInteractionUtils.keyCodeForName("e"));
        assertEquals(GLFW.GLFW_KEY_ENTER, ClientInteractionUtils.keyCodeForName("enter"));
        assertEquals(GLFW.GLFW_KEY_TAB, ClientInteractionUtils.keyCodeForName("tab"));
        assertEquals(GLFW.GLFW_KEY_F2, ClientInteractionUtils.keyCodeForName("F2"));
        assertEquals(GLFW.GLFW_KEY_PAGE_DOWN, ClientInteractionUtils.keyCodeForName("page-down"));
    }

    @Test
    public void normalizeKeyNameAcceptsHumanNames() {
        assertEquals("toggle_perspective", ClientInteractionUtils.normalizeKeyName("Toggle Perspective"));
        assertEquals("page_down", ClientInteractionUtils.normalizeKeyName("page-down"));
    }
}

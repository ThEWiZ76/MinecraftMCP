# MinecraftMCP - NeoForge 1.21.1

MinecraftMCP is a modified NeoForge port of the original Minecraft MCP Server Mod.
It runs a local HTTP Model Context Protocol server inside the Minecraft client so AI tools can inspect the client, execute commands, automate menus, use in-world items, move the player, wait for chat, and take screenshots.

## Credits

Original project by **cuspymd**:

- GitHub: https://github.com/cuspymd/mcp-server-mod
- Modrinth: https://modrinth.com/mod/mcp-server-mod

This branch contains modified CommunityCraft/ThEWiZ76 builds, including the NeoForge 1.21.1 port. License remains CC0-1.0; see [LICENSE](LICENSE).

## Branches

- `fabric/mc1.21.10` - Fabric build for Minecraft 1.21.10
- `neoforge/mc1.21.1` - NeoForge build for Minecraft 1.21.1

## Requirements

- Minecraft 1.21.1
- Java 21 or newer
- NeoForge 21.1.233 or newer in the 1.21.1 line

## Download

Use the GitHub release for this branch:

- `neoforge-v1.1.0-mc1.21.1`
- Asset: `mcp-server-mod-neoforge-1.1.0+neoforge.mc1.21.1.jar`

## Install

1. Install NeoForge for Minecraft 1.21.1.
2. Download `mcp-server-mod-neoforge-1.1.0+neoforge.mc1.21.1.jar` from the release.
3. Put the jar in your NeoForge profile `mods` folder.
4. Launch the NeoForge profile.

The MCP server starts automatically at:

```text
http://127.0.0.1:8080/mcp
```

## MCP Client Configuration

Example MCP client entry:

```json
{
  "mcpServers": {
    "minecraft": {
      "url": "http://127.0.0.1:8080/mcp"
    }
  }
}
```

Use `127.0.0.1` if another local service intercepts `localhost`.

## Configuration

Config file:

```text
config/mcp-client-neoforge.json
```

Important options:

```json
{
  "server": {
    "port": 8080,
    "host": "localhost",
    "enableSafety": true,
    "enableUnsafeChatCommands": false,
    "enableGuiAutomationTools": false,
    "requestTimeoutMs": 30000
  },
  "client": {
    "saveScreenshotsForDebug": false
  }
}
```

Notes:

- `enableUnsafeChatCommands` exposes arbitrary chat-command execution. Use only in trusted local testing.
- `enableGuiAutomationTools` exposes menu/inventory GUI automation tools.
- `saveScreenshotsForDebug` saves screenshots to `mcp_debug_screenshots` in the Minecraft instance folder.

## Supported Tools

Core tools:

- `execute_commands` - execute allowed Minecraft commands with safety validation
- `execute_chat_commands` - execute arbitrary player chat commands when enabled
- `get_player_info` - return position, facing, dimension, health, and inventory summary
- `get_blocks_in_area` - scan non-air blocks in a rectangular area
- `take_screenshot` - capture PNG screenshot with optional `x/y/z/yaw/pitch`

GUI tools, when `enableGuiAutomationTools` is true:

- `get_current_screen`
- `click_screen_slot`
- `click_screen_button`
- `click_screen_entry`
- `click_screen_xy`
- `wait_for_screen`
- `close_current_screen`

World and input tools:

- `right_click_block` - right-click exact block face
- `right_click_item` - use held item in air
- `set_held_slot` - select hotbar slot 0-8
- `movement_input` - press/release movement keys for a number of ticks
- `sneak` - press, release, toggle, or hold sneak
- `wait_for_chat` - wait for literal chat text or regex

## NeoForge Parity Notes

The Fabric branch currently has additional client-observation tools that are not yet ported to NeoForge:

- `attack_block`
- `left_click_air`
- `get_scoreboard`
- `get_client_disconnect`
- `get_bossbar_actionbar_titles`
- `get_nearby_entities`
- `get_recent_sounds_particles`

## Safety Model

`execute_commands` validates commands before execution. By default it allows building/testing commands such as `fill`, `clone`, `setblock`, `summon`, `tp`, `give`, `gamemode`, `effect`, `enchant`, `weather`, `time`, `say`, `tell`, and `title`, while blocking risky patterns such as mass entity killing and oversized operations.

Unsafe chat commands bypass that allowlist and are disabled by default.

## Build From Source

```powershell
.\gradlew.bat -b build-neoforge.gradle build
```

Built jar:

```text
build/libs/mcp-server-mod-neoforge-1.1.0+neoforge.mc1.21.1.jar
```

Run development client:

```powershell
.\gradlew.bat -b build-neoforge.gradle runClient
```

## Release Verification

This release branch was verified with:

```powershell
.\gradlew.bat -b build-neoforge.gradle build
```

## Known Notes

- This is a client-side automation/testing mod.
- Keep the MCP endpoint bound to localhost unless you fully trust the network.
- GUI and unsafe chat tools are intentionally opt-in.
- NeoForge support is separate from the Fabric branch; use the matching release for your loader.

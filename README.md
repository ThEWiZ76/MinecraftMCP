# Minecraft MCP Server Mod

A Minecraft client mod that implements a Model Context Protocol (MCP) server, enabling AI assistants like Claude to interact with Minecraft through structured commands.

## Overview

This mod creates an HTTP server within the Minecraft client that accepts MCP protocol requests, allowing Large Language Models to execute Minecraft commands safely and efficiently. The mod includes comprehensive safety validation to prevent destructive operations.

## Features

- **MCP Protocol Support**: Full implementation of Model Context Protocol for AI interaction
- **Safety Validation**: Comprehensive command filtering and validation system
- **Asynchronous Execution**: Non-blocking command execution to maintain game performance
- **Configurable Settings**: Customizable safety limits, server settings, and command permissions
- **Real-time Feedback**: Detailed execution results including block counts and entity information

## Fabric Requirements

- **Minecraft**: 1.21.10
- **Fabric Loader**: 0.17.3 or higher
- **Fabric API**: 0.135.0+1.21.10
- **Java**: 21 or higher

## NeoForge Requirements

- **Minecraft**: 1.21.1
- **NeoForge**: 21.1.233 or higher in the 1.21.1 line
- **Java**: 21 or higher

The NeoForge build is a separate artifact and currently supports the core MCP client workflow:

- `execute_commands`
- `execute_chat_commands` when enabled in config
- `get_player_info`
- `get_blocks_in_area`
- `take_screenshot`
- `get_current_screen`, `click_screen_button`, `click_screen_entry`, `click_screen_xy`, `click_screen_slot`, `wait_for_screen`, `close_current_screen` when GUI automation is enabled
- `right_click_block`
- `right_click_item`
- `set_held_slot`
- `movement_input`
- `sneak`
- `wait_for_chat`

Fabric-only tools such as block attack, scoreboard/HUD capture, nearby entity capture, and sound/particle capture still need NeoForge-specific ports.

## Installation

### Fabric

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.10
2. Download and install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Place the mod JAR file in your `mods` folder
4. Launch Minecraft with the Fabric profile

### NeoForge

1. Install NeoForge for Minecraft 1.21.1.
2. Build the NeoForge artifact:

```bash
./gradlew -b build-neoforge.gradle build
```

3. Place `build/libs/mcp-server-mod-neoforge-1.1.0+neoforge.mc1.21.1.jar` in the NeoForge profile `mods` folder.
4. Launch Minecraft with the NeoForge profile.

## Usage

### Starting the MCP Server

The MCP server starts automatically when you launch Minecraft with the mod installed. By default, it runs on `localhost:8080`.

### Configuration

The Fabric build creates a configuration file at `config/mcp-client.json`.
The NeoForge build creates `config/mcp-client-neoforge.json`.

```json
{
  "server": {
    "transport": "http",
    "port": 8080,
    "host": "localhost",
    "enableSafety": true,
    "enableUnsafeChatCommands": false,
    "enableGuiAutomationTools": false,
    "maxAreaSize": 10,
    "allowedCommands": ["fill", "clone", "setblock", "summon", "tp", "give"],
    "requestTimeoutMs": 30000,
    "autoStart": true
  },
  "client": {
    "showNotifications": true,
    "logLevel": "INFO",
    "logCommands": false,
    "saveScreenshotsForDebug": false
  },
  "safety": {
    "maxEntitiesPerCommand": 10,
    "maxBlocksPerCommand": 125000,
    "blockCreativeForAll": true,
    "requireOpForAdminCommands": true
  }
}
```

`server.requestTimeoutMs` limits how long the server waits for tool execution (including `execute_commands`, `execute_chat_commands`, `take_screenshot`, and GUI automation calls) before returning a timeout error.

### Connecting with AI Assistants

Connect your AI assistant (like Claude) to the MCP server using the endpoint:
```
http://localhost:8080/mcp
```

If `localhost` on your machine is intercepted by another local service, use:
```
http://127.0.0.1:8080/mcp
```

The server supports these tools:
- `execute_commands` - Execute Minecraft commands with safety validation
- `execute_chat_commands` - Execute arbitrary player chat commands for explicit admin/debug/plugin testing when `server.enableUnsafeChatCommands` is enabled
- `get_player_info` - Get comprehensive player information
- `get_blocks_in_area` - Scan and retrieve blocks in a specified area
- `take_screenshot` - Capture game screen with optional camera control
- `get_current_screen` - Inspect the currently open GUI when `server.enableGuiAutomationTools` is enabled
- `click_screen_slot` - Click a slot in the current handled GUI when `server.enableGuiAutomationTools` is enabled
- `click_screen_button` - Click a visible screen button by text or index when `server.enableGuiAutomationTools` is enabled
- `click_screen_entry` - Click a detected world/server/list entry by text/name or index when `server.enableGuiAutomationTools` is enabled
- `click_screen_xy` - Click raw scaled GUI coordinates when `server.enableGuiAutomationTools` is enabled
- `wait_for_screen` - Wait for a screen title/class before continuing menu automation
- `close_current_screen` - Close the current GUI when `server.enableGuiAutomationTools` is enabled
- `right_click_block` - Right-click an exact block face in-world
- `right_click_item` - Use the held item in air

`execute_chat_commands` and the GUI automation tools are intentionally disabled by default so the safe vanilla command surface remains unchanged.

### Example Commands

The AI can execute commands like:
- `fill ~ ~ ~ ~10 ~5 ~8 oak_planks` - Fill an area with blocks
- `summon villager ~ ~ ~` - Spawn entities
- `setblock ~ ~1 ~ oak_door` - Place specific blocks
- `tp @s ~ ~10 ~` - Teleport players
- `give @s diamond_sword` - Give items

## Safety Features

### Allowed Commands
- Building: `fill`, `clone`, `setblock`
- Entities: `summon`, `tp`, `teleport`
- Items: `give`
- Game state: `gamemode`, `effect`, `enchant`, `weather`, `time`
- Communication: `say`, `tell`, `title`

### Blocked Operations
- Mass entity destruction (`kill @a`, `kill @e`)
- Excessive area operations (>50×50×50 blocks)
- Mass item generation (>100 items)
- Global creative mode assignment

## Development

### Building

Fabric:

```bash
./gradlew build
```

NeoForge:

```bash
./gradlew -b build-neoforge.gradle build
```

### Running in Development

Fabric:

```bash
./gradlew runClient
```

NeoForge:

```bash
./gradlew -b build-neoforge.gradle runClient
```

### Project Structure

```
src/
├── main/java/cuspymd/mcp/mod/
│   ├── MCPServerMod.java           # Main mod class
│   ├── MCPServerModClient.java     # Client initializer
│   ├── server/                     # MCP server implementation
│   ├── command/                    # Command execution system
│   ├── config/                     # Configuration management
│   └── utils/                      # Utility classes
└── main/resources/
    ├── fabric.mod.json             # Mod metadata
    └── *.mixins.json              # Mixin configurations
```

## API Reference

### MCP Endpoints

- `POST /mcp/initialize` - Initialize MCP session
- `POST /mcp/ping` - Health check
- `POST /mcp/tools/list` - List available tools
- `POST /mcp/tools/call` - Execute commands

### Tool: execute_commands

Execute one or more Minecraft commands sequentially with safety validation.

**Parameters:**
- `commands` (array): List of Minecraft commands (without leading slash)
- `validate_safety` (boolean): Enable safety validation (default: true)

**Response schema (text payload JSON):**
- Top-level: `totalCommands`, `acceptedCount`, `appliedCount`, `failedCount`, `results`, `chatMessages`
- Per command: `index`, `command`, `status`, `accepted`, `applied`, `summary`, `chatMessages`
- `status` values: `applied`, `rejected_by_game`, `execution_error`, `timed_out`, `rejected_by_safety`, `unknown`

**Example Request:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "execute_commands",
    "arguments": {
      "commands": [
        "fill ~ ~ ~ ~10 ~5 ~8 oak_planks",
        "setblock ~5 ~6 ~4 oak_door"
      ],
      "validate_safety": true
    }
  }
}
```

### Tool: execute_chat_commands

Execute one or more arbitrary player chat commands sequentially for plugin or admin testing.

**Parameters:**
- `commands` (array): List of commands, with or without a leading slash
- `auto_confirm_large_edits` (boolean, optional): When true, automatically sends the FAWE confirm command shortly after each submitted command
- `confirm_command` (string, optional): Confirm command used by `auto_confirm_large_edits`; defaults to `/fastasyncworldedit:/confirm`
- `auto_confirm_delay_ms` (integer, optional): Delay before auto-confirm is sent; defaults to `100`, maximum `2000`

**Notes:**
- Available only when `server.enableUnsafeChatCommands` is `true`
- Bypasses the normal `execute_commands` allowlist and safety validator
- Intended for trusted local testing only
- Useful for FastAsyncWorldEdit commands that queue a large edit and require confirmation before the pending action expires

**Example Request:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "execute_chat_commands",
    "arguments": {
      "commands": [
        "/customgear ammo",
        "/mtgrinding debug"
      ]
    }
  }
}
```

**FastAsyncWorldEdit confirmation example:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "execute_chat_commands",
    "arguments": {
      "commands": [
        "//ore 1,deepslate deepslate_diamond_ore 7 5 100 -64 16"
      ],
      "auto_confirm_large_edits": true,
      "auto_confirm_delay_ms": 100
    }
  }
}
```

### Tool: get_player_info

Get comprehensive player information including position, facing direction, health, inventory, and game state.

**Parameters:** None required

**Response includes:**
- Exact position (x, y, z coordinates) and block coordinates
- Facing direction (yaw, pitch, cardinal direction)  
- Calculated front position for building (3 blocks ahead)
- Look vector for directional calculations
- Health, food, and experience status
- Current game mode and dimension
- World time information
- Inventory details (selected slot, main/off-hand items)

**Example Request:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "get_player_info",
    "arguments": {}
  }
}
```

### Tool: get_blocks_in_area

Scan and retrieve all non-air blocks within a specified rectangular area. Useful for analyzing structures or checking build areas.

**Parameters:**
- `from` (object): Starting position with x, y, z coordinates
- `to` (object): Ending position with x, y, z coordinates

**Response includes:**
- List of all non-air blocks in the area
- Block types and positions
- Total block count
- Area dimensions and validation info

**Example Request:**
```json
{
  "method": "tools/call", 
  "params": {
    "name": "get_blocks_in_area",
    "arguments": {
      "from": {"x": 100, "y": 64, "z": 200},
      "to": {"x": 110, "y": 74, "z": 210}
    }
  }
}
```

**Note:** Maximum area size per axis is limited by server configuration (default: 50 blocks).

### Tool: take_screenshot

Capture a screenshot of the current Minecraft game screen. Optionally, you can specify coordinates and rotation to move the player and set their gaze before taking the screenshot.

**Parameters:**
- `x` (number, optional): X coordinate to teleport the player to.
- `y` (number, optional): Y coordinate to teleport the player to.
- `z` (number, optional): Z coordinate to teleport the player to.
- `yaw` (number, optional): Yaw rotation (0-360) for horizontal view.
- `pitch` (number, optional): Pitch rotation (-90 to 90) for vertical view.

**Response includes:**
- Base64 encoded PNG image data.
- MIME type (`image/png`).

**Example Request:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "take_screenshot",
    "arguments": {
      "x": 120.5,
      "y": 70,
      "z": -200.5,
      "yaw": 180,
      "pitch": 0
    }
  }
}
```

### Tool: get_current_screen

Inspect the currently open screen. The response includes the screen title, class name, size, detected buttons, and detected list entries. For handled inventory screens, it also includes sync id, cursor stack, and all slot contents.

**Parameters:** None required

### Tool: click_screen_slot

Click a slot in the currently open handled inventory screen.

**Parameters:**
- `slot` (integer, required): Slot id from `get_current_screen`
- `button` (integer, optional): Mouse button index. `0` = left, `1` = right
- `action` (string, optional): Slot action type. Defaults to `PICKUP`

**Notes:**
- Available only when `server.enableGuiAutomationTools` is `true`
- Intended for local GUI testing and inventory automation

### Tool: right_click_block

Right-click an exact block face in the world with the selected hand.

**Parameters:**
- `pos` (object, required): Target block position `{x,y,z}`
- `face` (string, required): Block face: `north`, `south`, `east`, `west`, `up`, or `down`
- `hand` (string, optional): `main_hand` or `off_hand`; defaults to `main_hand`

### Tool: right_click_item

Use the held item in air.

**Parameters:**
- `hand` (string, optional): `main_hand` or `off_hand`; defaults to `main_hand`

### Tool: click_screen_button

Click a visible button in the current screen. Useful for start menu and submenu navigation.

**Parameters:**
- `text` (string, optional): Button text to match, partial and case-insensitive by default
- `index` (integer, optional): Button index from `get_current_screen`
- `exact` (boolean, optional): Require exact text match
- `button` (integer, optional): Mouse button index. `0` = left, `1` = right
- `doubleClick` (boolean, optional): Click twice

**Example:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "click_screen_button",
    "arguments": {"text": "Multiplayer"}
  }
}
```

### Tool: click_screen_entry

Click a detected list entry, such as a singleplayer world or multiplayer server row.

**Parameters:**
- `text`, `entryText`, or `name` (string, optional): Entry text to match, partial and case-insensitive by default
- `index` (integer, optional): Entry index from `get_current_screen`
- `exact` (boolean, optional): Require exact text match
- `button` (integer, optional): Mouse button index. `0` = left, `1` = right
- `doubleClick` (boolean, optional): Click twice to open/join when the screen supports it

**Example flow:**
```json
{"method":"tools/call","params":{"name":"click_screen_button","arguments":{"text":"Singleplayer"}}}
{"method":"tools/call","params":{"name":"wait_for_screen","arguments":{"titleRegex":"Select World|World"}}}
{"method":"tools/call","params":{"name":"click_screen_entry","arguments":{"name":"Test World","doubleClick":true}}}
```

### Tool: click_screen_xy

Click raw scaled GUI coordinates on the current screen. Use this as a fallback for custom screens.

**Parameters:**
- `x` (number, required): Scaled GUI X coordinate
- `y` (number, required): Scaled GUI Y coordinate
- `button` (integer, optional): Mouse button index. `0` = left, `1` = right
- `doubleClick` (boolean, optional): Click twice

### Tool: wait_for_screen

Wait until the current screen title or class matches.

**Parameters:**
- `title` (string, optional): Exact title
- `titleContains` (string, optional): Case-insensitive title substring
- `titleRegex` (string, optional): Java regex matched against title
- `screenClass` (string, optional): Exact screen class
- `classContains` (string, optional): Class name substring
- `timeout_ms` (integer, optional): Timeout in milliseconds, default `5000`

### Tool: close_current_screen

Close the current client screen.

**Parameters:** None required

## Debugging

### Local Screenshot Storage

For debugging purposes, you can enable local saving of every screenshot captured by the MCP server.

1. Open `config/mcp-client.json`.
2. Set `"saveScreenshotsForDebug": true` in the `client` section.
3. Screenshots will be saved to the `mcp_debug_screenshots/` directory in your Minecraft instance folder.
4. Files are named using the pattern: `screenshot_YYYYMMDD_HHMMSS_SSS.png`.

## License

This project is licensed under the CC0-1.0 License.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly (See [TESTING.md](TESTING.md) for more info)
5. Submit a pull request

## Support

For issues and questions:
- Check the [Issues](https://github.com/your-repo/issues) page
- Review the configuration documentation
- Enable debug logging for detailed troubleshooting

# Unsafe Chat Command Tool Design

## Goal

Add a minimal MCP tool to the `v1.1.0+mc1.21.10` mod line that allows explicit player-chat command passthrough for plugin testing, without weakening the existing safe `execute_commands` tool.

## Context

The current mod exposes:

- `execute_commands`
- `get_player_info`
- `get_blocks_in_area`

That is sufficient for safe vanilla world manipulation, but not for testing plugin flows that depend on arbitrary slash commands such as:

- `/customgear ...`
- `/mtgrinding ...`
- Arsenal plugin commands

The existing `execute_commands` path is intentionally protected by:

- config-driven allowlist checks
- safety validation
- command outcome filtering tuned for known vanilla commands

Those properties should remain intact.

## Design

### New Tool

Add a new MCP tool:

- `execute_chat_commands`

Purpose:

- Send one or more commands through the local player chat-command path.
- Support arbitrary plugin commands for testing and admin/debug use.

### Activation Model

The tool is disabled by default and only becomes available when a new config flag is enabled:

- `server.enable_unsafe_chat_commands`

When disabled:

- the tool is not listed in `tools/list`

When enabled:

- the tool appears in `tools/list`
- requests are accepted and routed through the client player command sender

This keeps the safe default intact while making activation trivial.

### Safety Boundary

The new tool intentionally does **not** use the vanilla allowlist/safety validator. That validator remains exclusive to `execute_commands`.

The new tool still performs minimal request validation:

- `commands` must be present
- `commands` must contain at least one non-blank string
- each command is normalized to avoid malformed double-slash handling

This is a deliberate admin/debug capability, not a safe automation primitive.

### Execution Path

Reuse the existing client-side command send mechanism already used by `CommandExecutor`.

The new tool will:

1. parse the `commands` array
2. normalize each entry to a player-chat command payload
3. send commands sequentially through the existing async path
4. capture response messages with the same chat capture mechanism
5. return a response shape aligned with `execute_commands` where practical

That minimizes implementation risk and keeps MCP client handling simple.

### Command Normalization

Accepted input forms:

- `/customgear ammo`
- `customgear ammo`

Normalization rule:

- trim whitespace
- remove a single leading slash before passing to `sendChatCommand`

Rejected input:

- blank strings

## Files

### Modify

- `src/main/java/cuspymd/mcp/mod/config/MCPConfig.java`
- `src/main/java/cuspymd/mcp/mod/server/MCPProtocol.java`
- `src/client/java/cuspymd/mcp/mod/command/CommandExecutor.java`

### Add or Extend Tests

- `src/test/java/cuspymd/mcp/mod/server/MCPProtocolTest.java`
- `src/test/java/cuspymd/mcp/mod/command/CommandExecutorResponseSchemaTest.java`
- `src/test/java/cuspymd/mcp/mod/command/CommandExecutorMessageFilterTest.java`
- a dedicated new command-executor test file if cleaner than extending existing tests

## Testing Strategy

Use TDD:

1. write failing tests for tool exposure and normalization
2. verify failure
3. implement minimal code
4. rerun focused tests
5. run the repo test suite if practical

Must verify:

- tool hidden by default
- tool visible when flag enabled
- `/customgear ...` style commands are accepted by the new path
- leading slash normalization works
- existing `execute_commands` behavior stays unchanged

## Non-Goals

Not part of this change:

- console command execution
- GUI automation
- inventory introspection
- screenshot workflow changes
- changing the safety model of `execute_commands`

## Expected Outcome

After replacing the mod jar in the client, the MCP endpoint should expose a second, explicitly unsafe test tool that lets Codex trigger plugin command flows for Minetopia and Arsenal on the 1.21.10 dev client.

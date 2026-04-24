# Unsafe Chat Command Tool Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicitly enabled MCP tool for arbitrary player chat commands on the `v1.1.0+mc1.21.10` line without changing the existing safe command tool.

**Architecture:** Keep the safe `execute_commands` path untouched. Add one config-gated MCP tool that reuses the current client-side command dispatch and chat capture pipeline, with minimal request validation and slash normalization.

**Tech Stack:** Java 21, Fabric Loom, Gson, JUnit 5

---

## Chunk 1: Spec And Config Surface

### Task 1: Add failing config and protocol exposure tests

**Files:**
- Modify: `M:\development\mcp-server-mod\src\test\java\cuspymd\mcp\mod\server\MCPProtocolTest.java`

- [ ] **Step 1: Write failing tests for disabled-by-default and enabled exposure**
- [ ] **Step 2: Run focused test task and verify failure**
- [ ] **Step 3: Implement minimal config/protocol changes**
- [ ] **Step 4: Run focused tests and verify pass**
- [ ] **Step 5: Commit**

### Task 2: Add config flag

**Files:**
- Modify: `M:\development\mcp-server-mod\src\main\java\cuspymd\mcp\mod\config\MCPConfig.java`

- [ ] **Step 1: Add failing test coverage if current tests need config serialization assertions**
- [ ] **Step 2: Implement `enable_unsafe_chat_commands` with default `false`**
- [ ] **Step 3: Run relevant tests**
- [ ] **Step 4: Commit**

## Chunk 2: Command Execution Path

### Task 3: Add failing tests for unsafe chat-command normalization and bypass behavior

**Files:**
- Modify: `M:\development\mcp-server-mod\src\test\java\cuspymd\mcp\mod\command\CommandExecutorResponseSchemaTest.java`
- Modify: `M:\development\mcp-server-mod\src\test\java\cuspymd\mcp\mod\command\CommandExecutorMessageFilterTest.java`
- Create if needed: `M:\development\mcp-server-mod\src\test\java\cuspymd\mcp\mod\command\UnsafeChatCommandExecutorTest.java`

- [ ] **Step 1: Write failing tests for `/customgear ...` and `customgear ...` normalization**
- [ ] **Step 2: Write failing tests proving this path does not use the safe allowlist rejection**
- [ ] **Step 3: Run focused tests and verify failure**
- [ ] **Step 4: Commit**

### Task 4: Implement unsafe chat-command execution

**Files:**
- Modify: `M:\development\mcp-server-mod\src\client\java\cuspymd\mcp\mod\command\CommandExecutor.java`
- Modify: `M:\development\mcp-server-mod\src\main\java\cuspymd\mcp\mod\server\MCPProtocol.java`

- [ ] **Step 1: Add new `execute_chat_commands` handling path**
- [ ] **Step 2: Reuse existing async send/capture flow**
- [ ] **Step 3: Normalize commands by trimming and stripping one leading slash**
- [ ] **Step 4: Return response shape aligned with existing execute response**
- [ ] **Step 5: Run focused tests and verify pass**
- [ ] **Step 6: Commit**

## Chunk 3: Verification And Build

### Task 5: Regression verification

**Files:**
- No production files expected

- [ ] **Step 1: Run command-related test suite**
- [ ] **Step 2: Run protocol/config-related test suite**
- [ ] **Step 3: Run `gradlew.bat build` if environment permits**
- [ ] **Step 4: Verify remapped jar output for `1.21.10`**
- [ ] **Step 5: Commit only if verification required file changes**

## Execution Notes

- Keep `execute_commands` behavior unchanged.
- Do not reuse the unsafe path for existing safe commands.
- Prefer adding a new helper method over branching deeply inside the current safe execution method if the file starts to sprawl.
- If the `v1.1.0+mc1.21.10` line lacks a clean config test surface, keep coverage at the protocol and executor levels rather than inventing heavy config serialization scaffolding.

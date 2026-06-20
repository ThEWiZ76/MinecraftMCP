package cuspymd.mcp.mod.command;

import cuspymd.mcp.mod.config.MCPConfig;

import java.util.regex.Pattern;

public class SafetyValidator {
    private static final Pattern KILL_ALL_PATTERN = Pattern.compile("kill\\s+@[ae]");
    private static final Pattern CREATIVE_ALL_PATTERN = Pattern.compile("gamemode\\s+creative\\s+@a");
    private static final Pattern LARGE_COUNT_PATTERN = Pattern.compile("Count:(\\d+)");
    private static final Pattern FILL_COORDINATES_PATTERN = Pattern.compile("fill\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)");

    private final MCPConfig config;

    public SafetyValidator(MCPConfig config) {
        this.config = config;
    }

    public ValidationResult validate(String command) {
        if (!config.getServer().isEnableSafety()) return ValidationResult.success();
        String normalizedCommand = command.toLowerCase().trim();
        if (normalizedCommand.startsWith("/")) normalizedCommand = normalizedCommand.substring(1);
        String[] parts = normalizedCommand.split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) return ValidationResult.failure("Empty command");
        String commandName = parts[0];
        if (!config.getServer().getAllowedCommands().contains(commandName)) {
            return ValidationResult.failure("Command '" + commandName + "' is not allowed");
        }
        if (KILL_ALL_PATTERN.matcher(normalizedCommand).find()) return ValidationResult.failure("Potentially destructive pattern detected: mass entity killing");
        if (CREATIVE_ALL_PATTERN.matcher(normalizedCommand).find() && config.getSafety().isBlockCreativeForAll()) {
            return ValidationResult.failure("Setting creative mode for all players is not allowed");
        }
        var countMatcher = LARGE_COUNT_PATTERN.matcher(command);
        if (countMatcher.find() && Integer.parseInt(countMatcher.group(1)) > config.getSafety().getMaxEntitiesPerCommand()) {
            return ValidationResult.failure("Item/entity count exceeds maximum allowed");
        }
        if ("fill".equals(commandName)) {
            var fillMatcher = FILL_COORDINATES_PATTERN.matcher(command);
            if (fillMatcher.find()) {
                long x = Math.abs(Long.parseLong(fillMatcher.group(4)) - Long.parseLong(fillMatcher.group(1))) + 1;
                long y = Math.abs(Long.parseLong(fillMatcher.group(5)) - Long.parseLong(fillMatcher.group(2))) + 1;
                long z = Math.abs(Long.parseLong(fillMatcher.group(6)) - Long.parseLong(fillMatcher.group(3))) + 1;
                long volume = x * y * z;
                if (volume > config.getSafety().getMaxBlocksPerCommand()) {
                    return ValidationResult.failure("Fill area volume (" + volume + ") exceeds maximum allowed (" + config.getSafety().getMaxBlocksPerCommand() + ")");
                }
            }
        }
        return ValidationResult.success();
    }

    public record ValidationResult(boolean isValid, String getErrorMessage) {
        public static ValidationResult success() { return new ValidationResult(true, null); }
        public static ValidationResult failure(String message) { return new ValidationResult(false, message); }
    }
}

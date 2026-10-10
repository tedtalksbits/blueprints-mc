package dev.whizzle.blueprints;

import java.util.List;

public record BlueprintDefinition(String id, String name, List<BlueprintBlock> blocks) {}

package com.nexus.capabilities.service.dto;

import java.util.List;

/**
 * Information regarding a system service unit.
 */
public record ServiceUnitInfo(
    String name,
    String loadState,
    String activeState,
    String subState,
    String description
) {}

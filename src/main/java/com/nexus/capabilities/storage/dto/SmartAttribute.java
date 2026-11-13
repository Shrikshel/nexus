package com.nexus.capabilities.storage.dto;

/**
 * Representation of an individual SMART attribute (e.g. Reallocated_Sector_Ct).
 */
public record SmartAttribute(
    int id,
    String name,
    long rawValue,
    int normalizedValue,
    int worstValue,
    int threshold,
    boolean failingNow
) {}

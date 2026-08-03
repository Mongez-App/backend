package com.smartstudy.identity.util;

import java.util.Map;

/**
 * Bidirectional mapping between API contract values and internal storage values
 * for appearance and language fields.
 *
 * API Contract ↔ Internal:
 *   "Dark Mode"  ↔ "DARK"
 *   "Light Mode" ↔ "LIGHT"
 *   "System"     ↔ "SYSTEM"
 *   "Arabic"     ↔ "ar"
 *   "English"    ↔ "en"
 */
public final class FieldMappingUtil {

    private FieldMappingUtil() {}

    private static final Map<String, String> APPEARANCE_TO_INTERNAL = Map.of(
            "Dark Mode", "DARK",
            "Light Mode", "LIGHT",
            "System", "SYSTEM"
    );

    private static final Map<String, String> APPEARANCE_TO_CONTRACT = Map.of(
            "DARK", "Dark Mode",
            "LIGHT", "Light Mode",
            "SYSTEM", "System"
    );

    private static final Map<String, String> LANGUAGE_TO_INTERNAL = Map.of(
            "Arabic", "ar",
            "English", "en"
    );

    private static final Map<String, String> LANGUAGE_TO_CONTRACT = Map.of(
            "ar", "Arabic",
            "en", "English"
    );

    /**
     * Converts an API contract appearance value to its internal representation.
     * Accepts both contract values ("Dark Mode") and internal values ("DARK").
     * @return the internal value, or null if the input is not a valid value
     */
    public static String appearanceToInternal(String contractValue) {
        if (contractValue == null) return null;
        // First try the contract-to-internal mapping (e.g., "Dark Mode" -> "DARK")
        String mapped = APPEARANCE_TO_INTERNAL.get(contractValue);
        if (mapped != null) return mapped;
        // Then check if the input is already a valid internal value (e.g., "DARK")
        if (APPEARANCE_TO_CONTRACT.containsKey(contractValue.toUpperCase())) {
            return contractValue.toUpperCase();
        }
        return null;
    }

    /**
     * Converts an internal appearance value to its API contract representation.
     * @return the contract value, or the original value if no mapping exists
     */
    public static String appearanceToContract(String internalValue) {
        if (internalValue == null) return null;
        return APPEARANCE_TO_CONTRACT.getOrDefault(internalValue, internalValue);
    }

    /**
     * Converts an API contract language value to its internal representation.
     * Accepts both contract values ("English") and internal values ("en").
     * @return the internal value, or null if the input is not a valid value
     */
    public static String languageToInternal(String contractValue) {
        if (contractValue == null) return null;
        // First try the contract-to-internal mapping (e.g., "English" -> "en")
        String mapped = LANGUAGE_TO_INTERNAL.get(contractValue);
        if (mapped != null) return mapped;
        // Then check if the input is already a valid internal value (e.g., "en")
        if (LANGUAGE_TO_CONTRACT.containsKey(contractValue.toLowerCase())) {
            return contractValue.toLowerCase();
        }
        return null;
    }

    /**
     * Converts an internal language value to its API contract representation.
     * @return the contract value, or the original value if no mapping exists
     */
    public static String languageToContract(String internalValue) {
        if (internalValue == null) return null;
        return LANGUAGE_TO_CONTRACT.getOrDefault(internalValue, internalValue);
    }

    /**
     * @return comma-separated valid contract values for appearance
     */
    public static String validAppearanceValues() {
        return "'Dark Mode', 'Light Mode', 'System'";
    }

    /**
     * @return comma-separated valid contract values for language
     */
    public static String validLanguageValues() {
        return "'Arabic', 'English'";
    }
}

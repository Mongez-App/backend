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
     * @return the internal value, or null if the input is not a valid contract value
     */
    public static String appearanceToInternal(String contractValue) {
        if (contractValue == null) return null;
        return APPEARANCE_TO_INTERNAL.get(contractValue);
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
     * @return the internal value, or null if the input is not a valid contract value
     */
    public static String languageToInternal(String contractValue) {
        if (contractValue == null) return null;
        return LANGUAGE_TO_INTERNAL.get(contractValue);
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

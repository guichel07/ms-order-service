package org.acme.Util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhoneNormalizerTest {

    @Test
    void normalize_stripsSpacesAndDashes() {
        assertEquals("612345678", PhoneNormalizer.normalize("06 12 34 56 78"));
    }

    @Test
    void normalize_stripsCountryCode() {
        assertEquals("612345678", PhoneNormalizer.normalize("+242 612 34 56 78"));
    }

    @Test
    void normalize_countryCodeAndLocalPrefix_giveSameResult() {
        String withZero = PhoneNormalizer.normalize("0612345678");
        String withCountryCode = PhoneNormalizer.normalize("+242612345678");

        assertEquals(withZero, withCountryCode);
    }

    @Test
    void normalize_returnsEmptyString_whenInputIsNull() {
        assertEquals("", PhoneNormalizer.normalize(null));
    }

    @Test
    void normalize_leavesShortLocalNumberWithoutLeadingZero_unaffectedByCountryCodeStrip() {
        // "242" en tête d'un numéro trop court pour être un indicatif ne doit pas être amputé.
        assertEquals("242", PhoneNormalizer.normalize("242"));
    }
}

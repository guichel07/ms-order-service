package org.acme.Util;

/**
 * Normalise un numéro de téléphone en un identifiant stable pour le rapprochement
 * client/commande, indépendamment de la façon dont il a été saisi (espaces, tirets,
 * indicatif +242 présent ou non, 0 initial présent ou non).
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {}

    public static String normalize(String rawPhone) {
        if (rawPhone == null) {
            return "";
        }

        String digits = rawPhone.replaceAll("[^0-9]", "");

        if (digits.startsWith("242") && digits.length() > 9) {
            digits = digits.substring(3);
        }

        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        return digits;
    }
}

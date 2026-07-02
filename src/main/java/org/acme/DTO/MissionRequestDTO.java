package org.acme.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Saisie d'un achat unitaire sur le terrain — un seul article, un seul fournisseur (optionnel :
 * soit un existant via supplierId, soit un nouveau créé à la volée via newSupplierName/Phone).
 * name/icon/iconColor/refUnitCost ne sont jamais reçus du front : ils sont enrichis côté back
 * depuis l'article réel au moment de la création, jamais confiés tels quels par le client.
 */
public record MissionRequestDTO(
    @NotBlank String articleId,
    @Min(value = 1, message = "La quantité doit être d'au moins 1") int qty,
    @DecimalMin(value = "0", message = "Le prix payé ne peut pas être négatif") BigDecimal pricePaid,
    @Pattern(regexp = "terminee|en_attente|ecartee", message = "Statut de mission invalide")
    String status,
    String supplierId,
    String newSupplierName,
    String newSupplierPhone,
    /** Prix marché observé sur le terrain au moment de l'achat — met à jour Article.marketPrice si renseigné. */
    @DecimalMin(value = "0", message = "Le prix marché ne peut pas être négatif") BigDecimal marketPrice
) {}

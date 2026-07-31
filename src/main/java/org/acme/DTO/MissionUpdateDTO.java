package org.acme.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Correction d'une mission déjà créée (qty/prix/statut, et désormais fournisseur) — le changement
 * de fournisseur n'est appliqué que si la quête est encore en_cours (même garde que la mise à jour
 * elle-même), et n'ajoute une note d'historique que si le fournisseur change réellement, pour ne
 * pas spammer l'historique à chaque simple correction de quantité/prix.
 */
public record MissionUpdateDTO(
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

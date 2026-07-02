package org.acme.Entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Une note libre horodatée sur un fournisseur (ex: "Acheté 50kg de riz le 12/07, bonne qualité").
 * Historique manuel en attendant que ce soit alimenté automatiquement par les Missions d'achat.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierNote {

    private String text;

    private Instant createdAt;
}

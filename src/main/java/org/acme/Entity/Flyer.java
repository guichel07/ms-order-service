package org.acme.Entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Flyer complet façon carte de resto : plusieurs sections ordonnées (ex: "Petit-déjeuner",
 * "Midi", "Soir"), chacune reprenant une gamme déjà composée (voir Gamme). L'admin assemble
 * les sections lui-même — pas de suggestion IA sur les sections dans ce MVP.
 */
@MongoEntity(collection = "Flyers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Flyer extends PanacheMongoEntity {

    private String title;

    private List<FlyerSection> sections;

    private Instant createdAt;

    private Instant updatedAt;
}

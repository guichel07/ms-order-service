package org.acme.Entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Une section du flyer (ex: "Petit-déjeuner", "Midi") — un titre + la gamme qui compose cette
 * section. MVP : une seule gamme par section (pas de fusion de plusieurs gammes dans une section).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlyerSection {

    private String title;

    private String gammeId;
}

package org.acme.DTO;

import java.math.BigDecimal;
import org.acme.Entity.AgeCategory;
import org.acme.Entity.Client;
import org.acme.Entity.Gender;
import org.acme.Util.PhoneNormalizer;

import jakarta.validation.constraints.NotBlank;

/**
 * ClientDTO
 */
public record ClientDTO(

    String id,

    @NotBlank
    String firstname,

    @NotBlank
    String lastname,

    @NotBlank
    String phone,

    AgeCategory ageCategory,

    Gender gender,

    boolean anonymous,

    /** Total dépensé (toutes commandes confondues) — seul GET /clients (listAll) le calcule. */
    BigDecimal totalSpent,

    /** Nombre de commandes (toutes confondues) — seul GET /clients (listAll) le calcule. */
    int ordersCount
) {
        public ClientDTO(String firstname, String lastname, String phone) {
            this(null, firstname, lastname, phone, null, null, false, BigDecimal.ZERO, 0);
        }

        public ClientDTO(
            String id,
            String firstname,
            String lastname,
            String phone,
            AgeCategory ageCategory,
            Gender gender,
            boolean anonymous
        ) {
            this(id, firstname, lastname, phone, ageCategory, gender, anonymous, BigDecimal.ZERO, 0);
        }

        public static ClientDTO fromEntity(Client client) {
            return fromEntity(client, BigDecimal.ZERO, 0);
        }

        public static ClientDTO fromEntity(Client client, BigDecimal totalSpent, int ordersCount) {
            if (client == null) {
                return null;
            }
            return new ClientDTO(
                client.id != null ? client.id.toHexString() : null,
                client.getFirstname(),
                client.getLastname(),
                client.getPhone(),
                client.getAgeCategory(),
                client.getGender(),
                client.isAnonymous(),
                totalSpent,
                ordersCount
            );
        }

        public static Client toEntity(ClientDTO clientDTO) {
                if (clientDTO == null) {
                    return null;
                }
                Client client = new Client();
                client.setFirstname(clientDTO.firstname());
                client.setLastname(clientDTO.lastname());
                client.setPhone(clientDTO.phone());
                client.setNormalizedPhone(PhoneNormalizer.normalize(clientDTO.phone()));
                client.setAgeCategory(clientDTO.ageCategory());
                client.setGender(clientDTO.gender());
                return client;
        }
}

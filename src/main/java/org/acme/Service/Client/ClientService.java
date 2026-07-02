package org.acme.Service.Client;

import java.util.List;
import org.acme.DTO.ClientDTO;
import org.acme.Entity.AgeCategory;
import org.acme.Entity.Gender;

public interface ClientService {
    List<ClientDTO> getAll();
    ClientDTO getById(String id);
    ClientDTO getByPhone(String phone);
    List<ClientDTO> searchByName(String query);
    ClientDTO saveClient(ClientDTO clientDTO);
    List<ClientDTO> saveAll(List<ClientDTO> clients);
    ClientDTO updateClient(String phone, ClientDTO clientDTO);
    ClientDTO toggleArchiveClient(String phone);
    void deleteClient(String phone);

    /**
     * Retrouve la fiche "client anonyme" pour ce couple (catégorie d'âge, genre), ou
     * la crée si c'est la première vente sans téléphone de ce couple. gender peut être
     * null (genre inconnu). Une seule fiche partagée par couple — voir Client.anonymous.
     */
    ClientDTO getOrCreateAnonymous(AgeCategory ageCategory, Gender gender);
}

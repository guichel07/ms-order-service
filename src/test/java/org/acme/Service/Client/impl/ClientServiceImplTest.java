package org.acme.Service.Client.impl;

import java.util.List;

import org.acme.DTO.ClientDTO;
import org.acme.Entity.Client;
import org.acme.Entity.Order;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ClientRepository;
import org.acme.Repository.OrderRepository;
import org.acme.Util.PhoneNormalizer;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.quarkus.mongodb.panache.PanacheQuery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ClientServiceImpl clientService;

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Client buildClient(String phone, String firstname, String lastname, boolean archived) {
        Client client = new Client();
        client.id = new ObjectId();
        client.setPhone(phone);
        client.setFirstname(firstname);
        client.setLastname(lastname);
        client.setArchived(archived);
        return client;
    }

    // -----------------------------------------------------------------
    // getAll
    // -----------------------------------------------------------------

    @Test
    void getAll_returnsMappedNonArchivedClients() {
        Client c1 = buildClient("+243818000001", "Jean", "Kabila", false);
        Client c2 = buildClient("+243997000002", "Awa", "Diallo", false);
        when(clientRepository.findAllNotArchived()).thenReturn(List.of(c1, c2));
        when(orderRepository.listAll()).thenReturn(List.of());

        List<ClientDTO> result = clientService.getAll();

        assertEquals(2, result.size());
        assertEquals("Jean", result.get(0).firstname());
        assertEquals("Awa", result.get(1).firstname());
        verify(clientRepository, times(1)).findAllNotArchived();
    }

    @Test
    void getAll_returnsEmptyListWhenNoClients() {
        when(clientRepository.findAllNotArchived()).thenReturn(List.of());
        when(orderRepository.listAll()).thenReturn(List.of());

        List<ClientDTO> result = clientService.getAll();

        assertTrue(result.isEmpty());
    }

    @Test
    void getAll_sumsOrdersCaAndCountPerClient() {
        Client client = buildClient("+243818000001", "Jean", "Kabila", false);
        String clientId = client.id.toHexString();
        when(clientRepository.findAllNotArchived()).thenReturn(List.of(client));

        Order order1 = new Order();
        order1.setClientId(clientId);
        order1.setArticles(new java.util.ArrayList<>(
            List.of(new org.acme.Entity.OrderItem(null, "Savon noir", java.math.BigDecimal.valueOf(1200), 2))
        ));
        Order order2 = new Order();
        order2.setClientId(clientId);
        order2.setArticles(new java.util.ArrayList<>(
            List.of(new org.acme.Entity.OrderItem(null, "Crème hydratante", java.math.BigDecimal.valueOf(2500), 1))
        ));
        when(orderRepository.listAll()).thenReturn(List.of(order1, order2));

        List<ClientDTO> result = clientService.getAll();

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).ordersCount());
        assertEquals(0, java.math.BigDecimal.valueOf(4900).compareTo(result.get(0).totalSpent()));
    }

    // -----------------------------------------------------------------
    // getById
    // -----------------------------------------------------------------

    @Test
    void getById_returnsClientWhenFound() {
        Client client = buildClient("+243818000001", "Jean", "Kabila", false);
        ObjectId id = client.id;
        when(clientRepository.findById(id)).thenReturn(client);

        ClientDTO result = clientService.getById(id.toHexString());

        assertNotNull(result);
        assertEquals("Jean", result.firstname());
    }


    // -----------------------------------------------------------------
    // getByPhone
    // -----------------------------------------------------------------

    @Test
    void getByPhone_returnsClientWhenFound() {
        Client client = buildClient("+243818000001", "Jean", "Kabila", false);
        when(clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize("+243818000001")))
                .thenReturn(client);

        ClientDTO result = clientService.getByPhone("+243818000001");

        assertEquals("Kabila", result.lastname());
    }

    @Test
    void getByPhone_matchesRegardlessOfFormatting() {
        // "+242 751 112 222" et "0751112222" désignent le même numéro une fois normalisés.
        Client client = buildClient("+242 751 112 222", "Jean", "Kabila", false);
        when(clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize("0751112222")))
                .thenReturn(client);

        ClientDTO result = clientService.getByPhone("0751112222");

        assertEquals("Kabila", result.lastname());
    }



    // -----------------------------------------------------------------
    // saveClient
    // -----------------------------------------------------------------

    @Test
    void saveClient_persistsNewClientWhenPhoneNotUsed() {
        ClientDTO dto = new ClientDTO("Jean", "Kabila", "+243818000001");
        when(clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize(dto.phone())))
                .thenReturn(null);

        ClientDTO result = clientService.saveClient(dto);

        assertEquals("Jean", result.firstname());
        verify(clientRepository, times(1)).persist(any(Client.class));
    }


    // -----------------------------------------------------------------
    // updateClient
    // -----------------------------------------------------------------

    @Test
    void updateClient_updatesFirstnameAndLastname() {
        Client existing = buildClient("+243818000001", "Jean", "Kabila", false);
        ClientDTO dto = new ClientDTO("Jeannot", "Kabila-Modifie", "+243818000001");
        when(clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize("+243818000001")))
                .thenReturn(existing);

        ClientDTO result = clientService.updateClient("+243818000001", dto);

        assertEquals("Jeannot", result.firstname());
        assertEquals("Kabila-Modifie", result.lastname());
        verify(clientRepository, times(1)).update(any(Client.class));
    }

    @Test
    void updateClient_canChangeThePhoneNumber() {
        Client existing = buildClient("+243818000001", "Jean", "Kabila", false);
        ClientDTO dto = new ClientDTO("Jean", "Kabila", "+243818999999");
        when(clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize("+243818000001")))
                .thenReturn(existing);

        ClientDTO result = clientService.updateClient("+243818000001", dto);

        assertEquals("+243818999999", result.phone());
        assertEquals(PhoneNormalizer.normalize("+243818999999"), existing.getNormalizedPhone());
    }

    @Test
    void updateClient_throwsNotFoundWhenClientDoesNotExist() {
        ClientDTO dto = new ClientDTO("Jeannot", "Kabila", "+243818000001");
        when(clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize("+243818000001")))
                .thenReturn(null);

        assertThrows(BusinessException.class, () -> clientService.updateClient("+243818000001", dto));

        verify(clientRepository, never()).update(any(Client.class));
    }

    // -----------------------------------------------------------------
    // toggleArchiveClient
    // -----------------------------------------------------------------

    @Test
    void toggleArchiveClient_flipsArchivedFlagFromFalseToTrue() {
        Client client = buildClient("+243818000001", "Jean", "Kabila", false);
        when(clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize("+243818000001")))
                .thenReturn(client);

        clientService.toggleArchiveClient("+243818000001");

        assertTrue(client.isArchived());
        verify(clientRepository).update(client);
    }

    @Test
    void toggleArchiveClient_flipsArchivedFlagFromTrueToFalse() {
        Client client = buildClient("+243818000001", "Jean", "Kabila", true);
        when(clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize("+243818000001")))
                .thenReturn(client);

        clientService.toggleArchiveClient("+243818000001");

        assertFalse(client.isArchived());
    }

    @Test
    void toggleArchiveClient_throwsNotFoundWhenClientDoesNotExist() {
        when(clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize("+243818000001")))
                .thenReturn(null);

        assertThrows(BusinessException.class,
                () -> clientService.toggleArchiveClient("+243818000001"));
    }

    // -----------------------------------------------------------------
    // deleteClient
    // -----------------------------------------------------------------

    @Test
    void deleteClient_succeedsWhenClientDeleted() {
        when(clientRepository.deleteByNormalizedPhone(PhoneNormalizer.normalize("+243818000001")))
                .thenReturn(1L);

        assertDoesNotThrow(() -> clientService.deleteClient("+243818000001"));
    }

    // -----------------------------------------------------------------
    // searchByName
    // -----------------------------------------------------------------

    @Test
    void searchByName_returnsEmptyListWhenQueryIsNull() {
        List<ClientDTO> result = clientService.searchByName(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(clientRepository);
    }

    @Test
    void searchByName_returnsEmptyListWhenQueryIsBlank() {
        List<ClientDTO> result = clientService.searchByName("   ");

        assertTrue(result.isEmpty());
        verifyNoInteractions(clientRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByName_returnsMatchingClients() {
        Client client = buildClient("+243818000001", "Jean", "Kabila", false);
        PanacheQuery<Client> query = mock(PanacheQuery.class);

        when(query.list()).thenReturn(List.of(client));
        // Fix: Ensure find(...) returns the mocked PanacheQuery object properly
        when(clientRepository.find(anyString(), any(Object[].class))).thenReturn(query);

        List<ClientDTO> result = clientService.searchByName("Jean");

        assertEquals(1, result.size());
        assertEquals("Jean", result.get(0).firstname());
    }

    // -----------------------------------------------------------------
    // getOrCreateAnonymous
    // -----------------------------------------------------------------

    @Test
    void getOrCreateAnonymous_returnsExistingBucket_whenAlreadyCreated() {
        Client existing = new Client();
        existing.id = new ObjectId();
        existing.setAnonymous(true);
        existing.setAgeCategory(org.acme.Entity.AgeCategory.ENFANT);
        existing.setGender(org.acme.Entity.Gender.FEMME);
        existing.setFirstname("Client anonyme");
        existing.setLastname("ENFANT FEMME");
        when(clientRepository.findAnonymousByAgeCategoryAndGender(
                org.acme.Entity.AgeCategory.ENFANT, org.acme.Entity.Gender.FEMME))
                .thenReturn(existing);

        ClientDTO result = clientService.getOrCreateAnonymous(
                org.acme.Entity.AgeCategory.ENFANT, org.acme.Entity.Gender.FEMME);

        assertEquals(existing.id.toHexString(), result.id());
        assertTrue(result.anonymous());
        assertEquals(org.acme.Entity.Gender.FEMME, result.gender());
        verify(clientRepository, never()).persist(any(Client.class));
    }

    @Test
    void getOrCreateAnonymous_createsBucket_whenNoneExistsYet() {
        when(clientRepository.findAnonymousByAgeCategoryAndGender(
                org.acme.Entity.AgeCategory.ADULTE, org.acme.Entity.Gender.HOMME))
                .thenReturn(null);
        doAnswer(invocation -> {
            Client persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(clientRepository).persist(any(Client.class));

        ClientDTO result = clientService.getOrCreateAnonymous(
                org.acme.Entity.AgeCategory.ADULTE, org.acme.Entity.Gender.HOMME);

        assertNotNull(result.id());
        assertTrue(result.anonymous());
        assertEquals(org.acme.Entity.AgeCategory.ADULTE, result.ageCategory());
        assertEquals(org.acme.Entity.Gender.HOMME, result.gender());
        verify(clientRepository, times(1)).persist(any(Client.class));
    }

    @Test
    void getOrCreateAnonymous_createsBucket_whenGenderIsUnknown() {
        when(clientRepository.findAnonymousByAgeCategoryAndGender(
                org.acme.Entity.AgeCategory.ADO, null))
                .thenReturn(null);
        doAnswer(invocation -> {
            Client persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(clientRepository).persist(any(Client.class));

        ClientDTO result = clientService.getOrCreateAnonymous(org.acme.Entity.AgeCategory.ADO, null);

        assertNotNull(result.id());
        assertNull(result.gender());
        verify(clientRepository, times(1)).persist(any(Client.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByName_escapesRegexSpecialCharacters() {
        PanacheQuery<Client> query = mock(PanacheQuery.class);
        when(query.list()).thenReturn(List.of());
        when(clientRepository.find(anyString(), any(Object[].class))).thenReturn(query);

        clientService.searchByName("Jean(.*)");

        verify(clientRepository).find(
            anyString(),
            eq(".*\\QJean(.*)\\E.*"),
            eq(".*\\QJean(.*)\\E.*")
        );
    }
}
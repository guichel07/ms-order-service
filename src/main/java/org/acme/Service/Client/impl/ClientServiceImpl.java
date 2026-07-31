package org.acme.Service.Client.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.DTO.ClientDTO;
import org.acme.Entity.AgeCategory;
import org.acme.Entity.Client;
import org.acme.Entity.Gender;
import org.acme.Entity.Order;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ClientRepository;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Client.ClientService;
import org.acme.Util.OrderMath;
import org.acme.Util.PhoneNormalizer;
import org.bson.types.ObjectId;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

/**
 * ClientServiceImpl
 */
@ApplicationScoped
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;

    public ClientServiceImpl(ClientRepository _clientRepository, OrderRepository _orderRepository){
        this.clientRepository = _clientRepository;
        this.orderRepository = _orderRepository;
    }

    @Override
    public List<ClientDTO> getAll() {
        List<Client> clients = this.clientRepository.findAllNotArchived();

        Map<String, BigDecimal> caByClient = new HashMap<>();
        Map<String, Integer> ordersCountByClient = new HashMap<>();
        for (Order order : this.orderRepository.listAll()) {
            if (order.getClientId() == null) {
                continue;
            }
            caByClient.merge(order.getClientId(), OrderMath.computeOrderCa(order), BigDecimal::add);
            ordersCountByClient.merge(order.getClientId(), 1, Integer::sum);
        }

        return clients.stream()
                    .map(client -> {
                        String id = client.id.toHexString();
                        return ClientDTO.fromEntity(
                            client,
                            caByClient.getOrDefault(id, BigDecimal.ZERO),
                            ordersCountByClient.getOrDefault(id, 0)
                        );
                    })
                    .toList();
    }

    @Override
    public ClientDTO getById(String id){
        Client client = Optional.ofNullable(this.clientRepository.findById(new ObjectId(id))).orElseThrow(
            () -> new BusinessException(
                    Response.Status.NOT_FOUND,
                    "Client not found"
            )
        );

        return ClientDTO.fromEntity(client);
    }

    @Override
    public ClientDTO getByPhone(String phone){
        Client client = Optional.ofNullable(
            this.clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize(phone))
        ).orElseThrow(() -> new BusinessException(
                Response.Status.NOT_FOUND,
                "Client not found"
        ));

        return ClientDTO.fromEntity(client);
    }


    @Transactional
    @Override
    public ClientDTO saveClient(ClientDTO clientDTO) {

        Client client = this.clientRepository.findByNormalizedPhone(
            PhoneNormalizer.normalize(clientDTO.phone())
        );

        Optional.ofNullable(client).ifPresent((c) -> {
            throw new BusinessException(
                Response.Status.CONFLICT,
                "Already created"
            );
        });

        Client clientToSave = ClientDTO.toEntity(clientDTO);

        this.clientRepository.persist(clientToSave);

        return ClientDTO.fromEntity(clientToSave);
    }

    @Transactional
    @Override
    public List<ClientDTO> saveAll(List<ClientDTO> clients) {
        return clients.stream().map(this::saveClient).toList();
    }

    @Transactional
    @Override
    public ClientDTO updateClient(String phone, ClientDTO clientDTO) {

        Client existingClient = this.clientRepository.findByNormalizedPhone(
            PhoneNormalizer.normalize(phone)
        );

        Optional.ofNullable(existingClient).orElseThrow(() -> new BusinessException(
                Response.Status.NOT_FOUND,
                "Client not found"
        ));

        existingClient.setFirstname(clientDTO.firstname());
        existingClient.setLastname(clientDTO.lastname());
        existingClient.setPhone(clientDTO.phone());
        existingClient.setNormalizedPhone(PhoneNormalizer.normalize(clientDTO.phone()));

        this.clientRepository.update(existingClient);

        return ClientDTO.fromEntity(existingClient);
    }


    @Transactional
    @Override
    public ClientDTO toggleArchiveClient(String phone) {
        Client client = Optional.ofNullable(
            this.clientRepository.findByNormalizedPhone(PhoneNormalizer.normalize(phone))
        ).orElseThrow(() -> new BusinessException(
                    Response.Status.NOT_FOUND,
                    "Client not found"
        ));

            client.setArchived(!client.isArchived());
            this.clientRepository.update(client);

            return ClientDTO.fromEntity(client);
    }

    @Transactional
    @Override
    public void deleteClient(String phone) {
        long deleted = this.clientRepository.deleteByNormalizedPhone(PhoneNormalizer.normalize(phone));

        if (deleted == 0) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Client not found"
            );
        }
    }


    @Override
    public List<ClientDTO> searchByName(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String searchRegex = ".*" + Pattern.quote(query) + ".*";

        List<Client> clients = this.clientRepository.find(
            "firstname like ?1 or lastname like ?2",
            searchRegex,
            searchRegex
        ).list();

        return clients.stream()
                .map(ClientDTO::fromEntity)
                .toList();
    }

    @Transactional
    @Override
    public ClientDTO getOrCreateAnonymous(AgeCategory ageCategory, Gender gender) {
        Client client = this.clientRepository.findAnonymousByAgeCategoryAndGender(ageCategory, gender);

        if (client == null) {
            client = new Client();
            client.setFirstname("Client anonyme");
            client.setLastname(gender != null ? ageCategory.name() + " " + gender.name() : ageCategory.name());
            client.setAnonymous(true);
            client.setAgeCategory(ageCategory);
            client.setGender(gender);
            client.setArchived(false);
            this.clientRepository.persist(client);
        }

        return ClientDTO.fromEntity(client);
    }

}

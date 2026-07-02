package org.acme.Service.Client.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.acme.DTO.ClientsOverviewDTO;
import org.acme.Entity.Client;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Repository.ClientRepository;
import org.acme.Repository.OrderRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link ClientsOverviewServiceImpl}.
 * ClientRepository/OrderRepository sont mockés : aucune base de données n'est nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class ClientsOverviewServiceImplTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ClientsOverviewServiceImpl clientsOverviewService;

    private Client buildClient(String firstname, String lastname) {
        Client client = new Client();
        client.id = new ObjectId();
        client.setFirstname(firstname);
        client.setLastname(lastname);
        return client;
    }

    private Order buildOrder(String clientId, Instant saleDate, BigDecimal delta, OrderItem... items) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setClientId(clientId);
        order.setSaleDate(saleDate);
        order.setDelta(delta);
        order.setArticles(new ArrayList<>(List.of(items)));
        return order;
    }

    private OrderItem item(String name, long price, int qty) {
        return new OrderItem(null, name, BigDecimal.valueOf(price), qty);
    }

    @Test
    void getOverview_countsCurrentMonthOrderInHourlyTraffic() {
        Client client = buildClient("Awa", "Traoré");
        String clientId = client.id.toHexString();
        Instant now = Instant.now();

        Order order = buildOrder(clientId, now, BigDecimal.valueOf(500), item("Crème hydratante", 2500, 1));
        when(clientRepository.listAll()).thenReturn(List.of(client));
        when(orderRepository.listAll()).thenReturn(List.of(order));

        ClientsOverviewDTO overview = clientsOverviewService.getOverview();

        assertEquals(24, overview.hourlyTraffic().size());
        int totalOrdersAcrossHours = overview.hourlyTraffic().stream()
            .mapToInt(ClientsOverviewDTO.HourlyTrafficPointDTO::ordersCount)
            .sum();
        assertEquals(1, totalOrdersAcrossHours);
    }

    @Test
    void getOverview_ranksTopClientsByLifetimeCa() {
        Client bigSpender = buildClient("Paul", "Mbemba");
        Client smallSpender = buildClient("Moussa", "Diallo");
        Instant now = Instant.now();

        Order bigOrder = buildOrder(
            bigSpender.id.toHexString(), now, BigDecimal.valueOf(40000), item("Parfum bio", 150000, 1)
        );
        Order smallOrder = buildOrder(
            smallSpender.id.toHexString(), now, BigDecimal.valueOf(500), item("Savon noir", 1200, 1)
        );
        when(clientRepository.listAll()).thenReturn(List.of(bigSpender, smallSpender));
        when(orderRepository.listAll()).thenReturn(List.of(bigOrder, smallOrder));

        ClientsOverviewDTO overview = clientsOverviewService.getOverview();

        assertEquals(2, overview.topClients().size());
        assertEquals("Paul", overview.topClients().get(0).firstname());
        assertEquals("vip", overview.topClients().get(0).segment());
    }

    @Test
    void getOverview_flagsAtRiskClient_whenLastOrderIsOldEnough() {
        Client vipClient = buildClient("Paul", "Mbemba");
        String clientId = vipClient.id.toHexString();
        Instant staleOrderDate = Instant.now().minus(45, ChronoUnit.DAYS);

        Order staleOrder = buildOrder(clientId, staleOrderDate, BigDecimal.valueOf(40000), item("Parfum bio", 150000, 1));
        when(clientRepository.listAll()).thenReturn(List.of(vipClient));
        when(orderRepository.listAll()).thenReturn(List.of(staleOrder));

        ClientsOverviewDTO overview = clientsOverviewService.getOverview();

        assertEquals(1, overview.atRiskClients().size());
        assertEquals(clientId, overview.atRiskClients().get(0).id());
        assertTrue(overview.atRiskClients().get(0).daysSinceLastOrder() >= 45);
    }

    @Test
    void getOverview_splitsNewVsReturningByFirstOrderDate() {
        Client returningClient = buildClient("Grace", "Loemba");
        Client newClient = buildClient("Sarah", "Bemba");
        Instant now = Instant.now();

        Order oldOrderForReturning = buildOrder(
            returningClient.id.toHexString(), now.minus(90, ChronoUnit.DAYS), BigDecimal.valueOf(500), item("Savon noir", 1200, 1)
        );
        Order thisMonthOrderForReturning = buildOrder(
            returningClient.id.toHexString(), now, BigDecimal.valueOf(500), item("Savon noir", 1200, 1)
        );
        Order firstOrderForNewClient = buildOrder(
            newClient.id.toHexString(), now, BigDecimal.valueOf(500), item("Baume à lèvres", 700, 1)
        );

        when(clientRepository.listAll()).thenReturn(List.of(returningClient, newClient));
        when(orderRepository.listAll()).thenReturn(
            List.of(oldOrderForReturning, thisMonthOrderForReturning, firstOrderForNewClient)
        );

        ClientsOverviewDTO overview = clientsOverviewService.getOverview();

        assertEquals(1, overview.newVsReturning().newClientsCount());
        assertEquals(1, overview.newVsReturning().returningClientsCount());
    }
}

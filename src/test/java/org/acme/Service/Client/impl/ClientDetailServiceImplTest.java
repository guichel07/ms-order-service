package org.acme.Service.Client.impl;

import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.acme.DTO.ClientDetailDTO;
import org.acme.Entity.Client;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ClientRepository;
import org.acme.Repository.OrderRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link ClientDetailServiceImpl}.
 * ClientRepository/OrderRepository sont mockés : aucune base de données n'est nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class ClientDetailServiceImplTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ClientDetailServiceImpl clientDetailService;

    private Client buildClient(String firstname, String lastname) {
        Client client = new Client();
        client.id = new ObjectId();
        client.setFirstname(firstname);
        client.setLastname(lastname);
        client.setPhone("+243812345678");
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
    void getDetail_throwsNotFound_whenClientMissing() {
        String id = new ObjectId().toHexString();
        when(clientRepository.findById(any(ObjectId.class))).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> clientDetailService.getDetail(id)
        );

        assertEquals(Response.Status.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getDetail_returnsFourPeriods_withTotalsScopedToEachWindow() {
        Client client = buildClient("Awa", "Traoré");
        String clientId = client.id.toHexString();
        when(clientRepository.findById(any(ObjectId.class))).thenReturn(client);

        Order recentOrder = buildOrder(
            clientId,
            Instant.now().minus(2, ChronoUnit.DAYS),
            BigDecimal.valueOf(1000),
            item("Crème hydratante", 2500, 2)
        );
        Order oldOrder = buildOrder(
            clientId,
            Instant.now().minus(200, ChronoUnit.DAYS),
            BigDecimal.valueOf(500),
            item("Savon noir", 1200, 1)
        );
        when(orderRepository.findByClientId(clientId)).thenReturn(List.of(recentOrder, oldOrder));

        ClientDetailDTO detail = clientDetailService.getDetail(clientId);

        assertEquals(clientId, detail.id());
        assertEquals(4, detail.periods().size());

        ClientDetailDTO.PeriodDTO period7j = periodByKey(detail, "7j");
        assertEquals(1, period7j.ordersCount());
        assertEquals(0, new BigDecimal("5000.00").compareTo(period7j.totalCa()));

        ClientDetailDTO.PeriodDTO period2ans = periodByKey(detail, "2ans");
        assertEquals(2, period2ans.ordersCount());
        assertEquals(0, new BigDecimal("6200.00").compareTo(period2ans.totalCa()));
    }

    @Test
    void getDetail_marksVipAtRisk_whenHighSpendButNoRecentOrder() {
        Client client = buildClient("Paul", "Mbemba");
        String clientId = client.id.toHexString();
        when(clientRepository.findById(any(ObjectId.class))).thenReturn(client);

        Order bigOldOrder = buildOrder(
            clientId,
            Instant.now().minus(40, ChronoUnit.DAYS),
            BigDecimal.valueOf(40000),
            item("Parfum bio", 120000, 1)
        );
        when(orderRepository.findByClientId(clientId)).thenReturn(List.of(bigOldOrder));

        ClientDetailDTO detail = clientDetailService.getDetail(clientId);

        assertEquals("vip", detail.segment());
        assertEquals(40, detail.daysSinceLastOrder());
        assertTrue(detail.isAtRisk());
    }

    @Test
    void getDetail_marksNouveau_whenFirstOrderIsRecentAndSmall() {
        Client client = buildClient("Moussa", "Diallo");
        String clientId = client.id.toHexString();
        when(clientRepository.findById(any(ObjectId.class))).thenReturn(client);

        Order order = buildOrder(
            clientId,
            Instant.now().minus(3, ChronoUnit.DAYS),
            BigDecimal.valueOf(500),
            item("Baume à lèvres", 700, 1)
        );
        when(orderRepository.findByClientId(clientId)).thenReturn(List.of(order));

        ClientDetailDTO detail = clientDetailService.getDetail(clientId);

        assertEquals("nouveau", detail.segment());
        assertFalse(detail.isAtRisk());
    }

    @Test
    void getDetail_returnsDashRhythm_whenFewerThanTwoOrdersInPeriod() {
        Client client = buildClient("Fatou", "Sarr");
        String clientId = client.id.toHexString();
        when(clientRepository.findById(any(ObjectId.class))).thenReturn(client);
        when(orderRepository.findByClientId(clientId)).thenReturn(List.of());

        ClientDetailDTO detail = clientDetailService.getDetail(clientId);

        for (ClientDetailDTO.PeriodDTO period : detail.periods()) {
            assertEquals("—", period.purchaseRhythmLabel());
            assertEquals(0, period.ordersCount());
        }
    }

    private ClientDetailDTO.PeriodDTO periodByKey(ClientDetailDTO detail, String key) {
        return detail.periods().stream()
            .filter(p -> p.key().equals(key))
            .findFirst()
            .orElseThrow();
    }
}

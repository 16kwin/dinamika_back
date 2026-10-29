package com.example.dinamika_back.features;

import com.example.dinamika_back.config.RestTemplateConfig;
import com.example.dinamika_back.features.net.OutboundGuard;
import com.example.dinamika_back.repository.OrderStatusRepository;
import com.example.dinamika_back.repository.OrderTrackingRepository;
import com.example.dinamika_back.repository.OrdersFullRepository;
import com.example.dinamika_back.repository.OrdersListRepository;
import com.example.dinamika_back.repository.RegAnalogRepository;
import com.example.dinamika_back.repository.RegAttributesRepository;
import com.example.dinamika_back.repository.SprMaterialBlueprintRepository;
import com.example.dinamika_back.repository.SprMaterialCodeRepository;
import com.example.dinamika_back.repository.SprMaterialImageRepository;
import com.example.dinamika_back.repository.SprMaterialRepository;
import com.example.dinamika_back.service.LiveUpdates;
import com.example.dinamika_back.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Сторож исходящих HTTP (пакет B3): запрос не уходит, если функция клиента выключена, список
 * {@code awms.network.outbound.allowed} задан и хоста:порта в нём нет, или клиент не привязан к функции. Пустой
 * список (умолчание) адреса не ограничивает — как до выключателей; схема — только http и https. Разрешённый запрос пишет
 * {@code OUTBOUND_SENT} без строки запроса и учётных данных; редиректы не выполняются.
 * Клиент — тот же, что в службе ({@link RestTemplateConfig}); MockRestServiceServer подменяет только фабрику
 * соединений, перехватчики остаются.
 */
class OutboundGuardTest {

    private static final String SAAS = "http://localhost:8085";

    /** Журнал доступа, который запоминает события */
    private static final class RecordingAudit implements AccessAudit {
        final List<Object[]> events = new ArrayList<>();
        /** Записи OUTBOUND_BLOCKED в журнал профиля: функция, адрес, причина */
        final List<Object[]> blocked = new ArrayList<>();

        @Override
        public void record(Event event, Feature feature, String objectRef, Map<String, ?> details) {
            events.add(new Object[]{event, feature, objectRef, details});
        }

        @Override
        public void outboundBlocked(Feature feature, String target, String reason) {
            blocked.add(new Object[]{feature, target, reason});
        }
    }

    private static FeatureService features(String... properties) {
        Map<String, Object> map = new HashMap<>();
        for (String property : properties) {
            int eq = property.indexOf('=');
            map.put(property.substring(0, eq), property.substring(eq + 1));
        }
        return new FeatureService(FeatureResolver.fromProperties(map));
    }

    private static RestTemplate ordersClient(FeatureService features, AccessAudit audit) {
        return new RestTemplateConfig().restTemplate(new RestTemplateBuilder(), features, audit);
    }

    // ==================== Разрешено: функция включена и хост в списке ====================

    @Test
    void allowedRequestGoesOutAndIsAudited() {
        RecordingAudit audit = new RecordingAudit();
        RestTemplate client = ordersClient(features("awms.features.preset=full",
                "awms.network.outbound.allowed=localhost:8085"), audit);
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo(SAAS + "/v1/orders/u1?token=secret")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.postForEntity(SAAS + "/v1/orders/u1?token=secret", Map.of("a", 1), String.class);

        server.verify();
        assertThat(audit.events).hasSize(1);
        Object[] event = audit.events.get(0);
        assertThat(event[0]).isEqualTo(AccessAudit.Event.OUTBOUND_SENT);
        assertThat(event[1]).isEqualTo(Feature.ORDERS);
        // Хост и путь — без строки запроса
        assertThat(event[2]).isEqualTo("http://localhost:8085/v1/orders/u1");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) event[3];
        assertThat(details).containsEntry("status", 200).containsEntry("method", "POST").containsKey("size");
    }

    @Test
    void emptyAllowListDoesNotLimitOutboundWithOrdersOn() {
        RecordingAudit audit = new RecordingAudit();
        // Умолчание потолка: awms.network.outbound.allowed пуст — исходящие не ограничены, как до выключателей
        RestTemplate client = ordersClient(features("awms.features.preset=full"), audit);
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo("http://saas.example.org/v1/orders/u1")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.postForEntity("http://saas.example.org/v1/orders/u1", Map.of(), String.class);

        server.verify();
        // Журнал исходящих пишется и без списка
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event[0]).isEqualTo(AccessAudit.Event.OUTBOUND_SENT);
            assertThat(event[2]).isEqualTo("http://saas.example.org/v1/orders/u1");
        });
    }

    // ==================== Заблокировано: запрос не уходит ====================

    @Test
    void explicitAllowListBlocksOtherHosts() {
        RecordingAudit audit = new RecordingAudit();
        // Список задан явно — чужой адрес не уходит
        RestTemplate client = ordersClient(features("awms.features.preset=full",
                "awms.network.outbound.allowed=saas.example.org:443"), audit);
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();

        assertThatThrownBy(() -> client.postForEntity(SAAS + "/v1/orders/u1", Map.of(), String.class))
                .isInstanceOf(ResourceAccessException.class)
                .hasCauseInstanceOf(OutboundGuard.OutboundBlockedException.class)
                .hasMessageContaining("localhost:8085").hasMessageContaining("awms.network.outbound.allowed");
        server.verify();   // ожиданий нет: дошедший до сервера запрос провалил бы тест
        assertThat(audit.events).isEmpty();
        // Блокировка — в журнал профиля (OUTBOUND_BLOCKED): адрес без строки запроса, причина
        assertThat(audit.blocked).singleElement().satisfies(b -> {
            assertThat(b[0]).isEqualTo(Feature.ORDERS);
            assertThat(b[1]).isEqualTo("http://localhost:8085/v1/orders/u1");
            assertThat((String) b[2]).contains("awms.network.outbound.allowed");
        });
    }

    @Test
    void noneForbidsAllOutboundAndIsJournaled() {
        // none — запрещены все исходящие (пустой список их не ограничивает): строка шаблонов standard и isolated
        RecordingAudit audit = new RecordingAudit();
        RestTemplate client = ordersClient(features("awms.features.preset=full",
                "awms.network.outbound.allowed=none"), audit);
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        for (String url : List.of(SAAS + "/v1/orders/u1", "https://none/v1", "http://127.0.0.1:80/")) {
            assertThatThrownBy(() -> client.postForEntity(url, Map.of(), String.class)).as(url)
                    .hasCauseInstanceOf(OutboundGuard.OutboundBlockedException.class)
                    .hasMessageContaining("запрещены потолком");
        }
        server.verify();
        assertThat(audit.events).isEmpty();
        assertThat(audit.blocked).hasSize(3);
        assertThat(OutboundGuard.check(URI.create("http://NONE/"), List.of("NONE"))).contains("запрещены");
    }

    @Test
    void hostOrPortNotInListIsBlocked() {
        RestTemplate client = ordersClient(features("awms.features.preset=full",
                "awms.network.outbound.allowed=localhost:8085"), new RecordingAudit());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        // Другой порт, тот же хост по IP (список — по имени), чужой хост
        for (String url : List.of("http://localhost:8086/v1/orders/u1", "http://127.0.0.1:8085/v1/orders/u1",
                "http://evil.example/v1/orders/u1")) {
            assertThatThrownBy(() -> client.postForEntity(url, Map.of(), String.class)).as(url)
                    .hasCauseInstanceOf(OutboundGuard.OutboundBlockedException.class);
        }
        server.verify();
    }

    @Test
    void ordersOffAnswers404AndSendsNothing() {
        // standard: заказов нет, даже если адрес в списке или список не задан (исходящие не ограничены)
        for (FeatureService features : List.of(
                features("awms.features.preset=standard", "awms.network.outbound.allowed=localhost:8085"),
                features("awms.features.preset=standard"))) {
            RecordingAudit audit = new RecordingAudit();
            RestTemplate client = ordersClient(features, audit);
            MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();

            assertThatThrownBy(() -> client.postForEntity(SAAS + "/v1/orders/u1", Map.of(), String.class))
                    .isInstanceOf(FeatureDisabledException.class);
            server.verify();
            assertThat(audit.events).isEmpty();
        }
    }

    @Test
    void unboundClientIsAlwaysBlocked() {
        RestTemplate client = new RestTemplateBuilder().additionalInterceptors(OutboundGuard.unbound()).build();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        assertThatThrownBy(() -> client.getForEntity(SAAS + "/", String.class))
                .hasCauseInstanceOf(OutboundGuard.OutboundBlockedException.class)
                .hasMessageContaining("не привязан к функции");
        server.verify();
    }

    // ==================== Сверка адреса со списком ====================

    @Test
    void allowListMatchesHostCaseInsensitiveAndDefaultPorts() {
        List<String> list = List.of("localhost:8085", "Saas.Example.org", "[::1]:9000");
        assertThat(check("http://LOCALHOST:8085/v1")).isNull();
        assertThat(check("http://localhost/v1")).isNotNull();                       // порт 80, а в списке 8085
        // Элемент без порта — порт схемы запроса: 80 для http, 443 для https
        assertThat(OutboundGuard.check(URI.create("https://saas.example.org/v1"), list)).isNull();
        assertThat(OutboundGuard.check(URI.create("http://saas.example.org/v1"), list)).isNull();
        assertThat(OutboundGuard.check(URI.create("http://saas.example.org:8080/v1"), list)).isNotNull();
        assertThat(OutboundGuard.check(URI.create("http://[::1]:9000/v1"), list)).isNull();
        assertThat(OutboundGuard.check(URI.create("http://[::1]:9001/v1"), list)).isNotNull();
        // Только http и https
        assertThat(OutboundGuard.check(URI.create("ftp://localhost:8085/x"), list)).contains("схема");
        assertThat(OutboundGuard.check(URI.create("file:///etc/passwd"), list)).isNotNull();
        // Похожие имена не проходят
        assertThat(OutboundGuard.check(URI.create("http://localhost.evil.example:8085/"), list)).isNotNull();
        assertThat(OutboundGuard.check(URI.create("http://saas.example.org.evil:80/"), list)).isNotNull();
        // Пустой список — адреса не ограничены (как до выключателей), но схема — по-прежнему только http и https
        assertThat(OutboundGuard.check(URI.create("http://localhost:8085/"), List.of())).isNull();
        assertThat(OutboundGuard.check(URI.create("https://saas.example.org/v1"), List.of())).isNull();
        assertThat(OutboundGuard.check(URI.create("ftp://localhost:8085/x"), List.of())).contains("схема");
        assertThat(OutboundGuard.check(URI.create("file:///etc/passwd"), List.of())).isNotNull();
    }

    private static String check(String url) {
        return OutboundGuard.check(URI.create(url), List.of("localhost:8085"));
    }

    @Test
    void describeDropsCredentialsQueryAndControlCharacters() {
        assertThat(OutboundGuard.describe(URI.create("http://user:pass@Host.example:8085/v1/a%0Ab?key=secret#f")))
                .isEqualTo("http://host.example:8085/v1/a%0Ab");
    }

    // ==================== Редиректы ====================

    @Test
    void redirectIsNotFollowedToAnotherHost() throws Exception {
        AtomicInteger secretHits = new AtomicInteger();
        HttpServer target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        target.createContext("/", exchange -> {
            secretHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        HttpServer allowed = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int targetPort = target.getAddress().getPort();
        allowed.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + targetPort + "/secret");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        target.start();
        allowed.start();
        try {
            int allowedPort = allowed.getAddress().getPort();
            RestTemplate client = ordersClient(features("awms.features.preset=full",
                    "awms.network.outbound.allowed=127.0.0.1:" + allowedPort), new RecordingAudit());
            ResponseEntity<String> response = client.getForEntity("http://127.0.0.1:" + allowedPort + "/start",
                    String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(secretHits).hasValue(0);
        } finally {
            allowed.stop(0);
            target.stop(0);
        }
    }

    // ==================== Закрыто по умолчанию: клиенты из построителей контекста ====================

    @Test
    void contextBuildersGetUnboundGuardUnlessFeatureGuardIsPresent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.scan("com.example.dinamika_back.features.net");
            context.refresh();
            RestTemplateCustomizer templates = context.getBean(RestTemplateCustomizer.class);
            RestClientCustomizer clients = context.getBean(RestClientCustomizer.class);

            // Клиент без сторожа функции получает сторож «функция неизвестна»
            RestTemplate bare = new RestTemplateBuilder(templates).build();
            assertThat(bare.getInterceptors()).singleElement().isInstanceOfSatisfying(OutboundGuard.class,
                    guard -> assertThat(guard.feature()).isNull());

            // Клиент функции (как в RestTemplateConfig) остаётся со своим сторожем
            RestTemplate orders = new RestTemplateConfig().restTemplate(new RestTemplateBuilder(templates),
                    features("awms.features.preset=full"), new RecordingAudit());
            assertThat(orders.getInterceptors()).singleElement().isInstanceOfSatisfying(OutboundGuard.class,
                    guard -> assertThat(guard.feature()).isEqualTo(Feature.ORDERS));

            // RestClient из построителя контекста закрыт
            RestClient.Builder builder = RestClient.builder();
            clients.customize(builder);
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            builder.requestInterceptors(interceptors::addAll);
            assertThat(interceptors).singleElement().isInstanceOf(OutboundGuard.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient client = builder.build();
            assertThatThrownBy(() -> client.get().uri(SAAS + "/").retrieve().toBodilessEntity())
                    .hasRootCauseInstanceOf(OutboundGuard.OutboundBlockedException.class);
            server.verify();
        }
    }

    // ==================== Заказы через сторож ====================

    private static OrderService orderService(RestTemplate client, LiveUpdates live) {
        OrderService service = new OrderService(mock(OrdersListRepository.class), mock(OrdersFullRepository.class),
                mock(OrderStatusRepository.class), mock(OrderTrackingRepository.class), mock(SprMaterialRepository.class),
                mock(SprMaterialImageRepository.class), mock(SprMaterialBlueprintRepository.class),
                mock(SprMaterialCodeRepository.class), mock(RegAttributesRepository.class),
                mock(RegAnalogRepository.class), client, live, new ObjectMapper());
        ReflectionTestUtils.setField(service, "syncServiceUrl", SAAS);
        ReflectionTestUtils.setField(service, "apiKey", "test-callback-key");
        return service;
    }

    @Test
    void orderGoesOutWithKeyAndNotifiesThroughLiveUpdates() {
        RestTemplate client = ordersClient(features("awms.features.preset=full",
                "awms.network.outbound.allowed=localhost:8085"), new RecordingAudit());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo(SAAS + "/v1/orders/u1")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-callback-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        LiveUpdates live = mock(LiveUpdates.class);

        Map<String, Object> result = orderService(client, live).createOrder("u1", Map.of("products", List.of()));

        server.verify();
        assertThat(result).containsEntry("order_uid", "u1").doesNotContainKey("error");
        verify(live).publish(eq("/topic/orders/refresh"), any());
    }

    @Test
    void blockedOrderIsNotSavedAndNotPublished() {
        // Список задан явно, адреса системы заказов в нём нет
        RestTemplate client = ordersClient(features("awms.features.preset=full",
                "awms.network.outbound.allowed=saas.example.org:443"), new RecordingAudit());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        LiveUpdates live = mock(LiveUpdates.class);
        OrderService service = orderService(client, live);

        Map<String, Object> result = service.createOrder("u1", Map.of("products", List.of()));

        server.verify();
        assertThat(result).containsEntry("error", "internal_error");
        verify(live, never()).publish(anyString(), any());
        verifyNoInteractions((OrdersListRepository) ReflectionTestUtils.getField(service, "ordersListRepository"));
    }
}

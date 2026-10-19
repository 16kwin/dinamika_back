package com.example.dinamika_back.features;

import com.example.dinamika_back.config.OrdersCallbackKeyFilter;
import com.example.dinamika_back.config.RestTemplateConfig;
import com.example.dinamika_back.config.WebSocketConfig;
import com.example.dinamika_back.controller.OrderController;
import com.example.dinamika_back.controller.StationChangeEvent;
import com.example.dinamika_back.controller.StationWebSocketController;
import com.example.dinamika_back.controller.SyncReceiverController;
import com.example.dinamika_back.controller.TestDocumentController;
import com.example.dinamika_back.controller.TkpController;
import com.example.dinamika_back.dto.StationDynamicDto;
import com.example.dinamika_back.dto.StationStaticDto;
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
import com.example.dinamika_back.repository.TestDocumentRepository;
import com.example.dinamika_back.repository.TkpFullRepository;
import com.example.dinamika_back.repository.TkpListRepository;
import com.example.dinamika_back.repository.TkpStatusRepository;
import com.example.dinamika_back.service.LiveUpdates;
import com.example.dinamika_back.service.NoopLiveUpdates;
import com.example.dinamika_back.service.OrderService;
import com.example.dinamika_back.service.StationService;
import com.example.dinamika_back.service.StompLiveUpdates;
import com.example.dinamika_back.service.SyncService;
import com.example.dinamika_back.service.TestDocumentService;
import com.example.dinamika_back.service.TkpService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.sockjs.support.AbstractSockJsService;
import org.springframework.web.socket.sockjs.support.SockJsHttpRequestHandler;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WebSocket, заказы и тестовая синхронизация под выключателями (пакет B3): без функций в потолке нет брокера
 * с его пулами, HTTP-клиентов, сервисов и контроллеров (L3), публикации идут в заглушку; заказы работают и без
 * WebSocket; при включённом WebSocket — локальный клиент SockJS, источники из потолка, без префикса /app;
 * HTTP-клиенты заказов и тестовой синхронизации — только со сторожем своей функции.
 */
class NetworkFeaturesContextTest {

    /** Бины модулей пакета B3 */
    private static final List<Class<?>> ORDERS_BEANS = List.of(OrderController.class, TkpController.class,
            OrderService.class, TkpService.class, RestTemplateConfig.class);
    private static final List<Class<?>> TESTSYNC_BEANS = List.of(SyncService.class, TestDocumentService.class,
            TestDocumentController.class, SyncReceiverController.class);
    private static final List<Class<?>> WEBSOCKET_BEANS = List.of(WebSocketConfig.class,
            StationWebSocketController.class, StompLiveUpdates.class);

    private static final List<Class<?>> REPOSITORIES = List.of(OrdersListRepository.class, OrdersFullRepository.class,
            OrderStatusRepository.class, OrderTrackingRepository.class, SprMaterialRepository.class,
            SprMaterialImageRepository.class, SprMaterialBlueprintRepository.class, SprMaterialCodeRepository.class,
            RegAttributesRepository.class, RegAnalogRepository.class, TkpListRepository.class, TkpFullRepository.class,
            TkpStatusRepository.class, TestDocumentRepository.class);

    /** Контекст модулей B3; окружение процесса подменяется пустым, как в OnFeatureConditionTest */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private WebApplicationContextRunner runner() {
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of())))
                .withPropertyValues("sync.service.url=http://localhost:8085", "api.key.awms=test-callback-key")
                .withBean(StationService.class, () -> mock(StationService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
                .withUserConfiguration(FeatureService.class, AccessAudit.Noop.class)
                .withUserConfiguration(NoopLiveUpdates.class, OrdersCallbackKeyFilter.Registration.class)
                .withUserConfiguration(ORDERS_BEANS.toArray(Class<?>[]::new))
                .withUserConfiguration(TESTSYNC_BEANS.toArray(Class<?>[]::new))
                .withUserConfiguration(WEBSOCKET_BEANS.toArray(Class<?>[]::new));
        for (Class type : REPOSITORIES) {
            runner = runner.withBean(type, () -> mock(type));
        }
        return runner;
    }

    private static void assertNoNetworkModules(AssertableWebApplicationContext context, String profile) {
        assertThat(context).hasNotFailed();
        for (Class<?> bean : ORDERS_BEANS) assertThat(context).as(profile).doesNotHaveBean(bean);
        for (Class<?> bean : TESTSYNC_BEANS) assertThat(context).as(profile).doesNotHaveBean(bean);
        for (Class<?> bean : WEBSOCKET_BEANS) assertThat(context).as(profile).doesNotHaveBean(bean);
        assertThat(context).as(profile).doesNotHaveBean(RestTemplate.class);
        assertThat(context).as(profile).doesNotHaveBean(SimpMessagingTemplate.class);
        assertThat(context).as(profile).doesNotHaveBean(WebSocketMessageBrokerConfigurer.class);
        assertThat(context).as(profile).doesNotHaveBean(SimpleBrokerMessageHandler.class);
        assertThat(context).as(profile).doesNotHaveBean("clientInboundChannel");
        assertThat(context).as(profile).doesNotHaveBean("clientOutboundChannel");
        assertThat(context).as(profile).doesNotHaveBean("brokerChannel");
        assertThat(context).as(profile).doesNotHaveBean("ordersCallbackKeyFilter");
        assertThat(context.getBean(LiveUpdates.class)).as(profile).isInstanceOf(NoopLiveUpdates.class);
    }

    // ==================== L3: без функций нет ни брокера, ни клиентов ====================

    @Test
    void networkFeaturesOffCreateNoBrokerNoClientsNoModules() {
        for (String[] profile : List.<String[]>of(
                new String[]{"awms.features.preset=isolated"},
                new String[]{"awms.features.preset=standard"},
                new String[]{"awms.features.preset=full", "awms.features.orders.enabled=false",
                        "awms.features.websocket.enabled=false"})) {
            runner().withPropertyValues(profile).run(context -> assertNoNetworkModules(context, String.join(" ", profile)));
        }
    }

    @Test
    void ordersWorkWithoutWebSocket() {
        runner().withPropertyValues("awms.features.preset=full", "awms.features.websocket.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    for (Class<?> bean : ORDERS_BEANS) assertThat(context).hasSingleBean(bean);
                    assertThat(context).doesNotHaveBean(SimpMessagingTemplate.class);
                    assertThat(context).doesNotHaveBean(WebSocketMessageBrokerConfigurer.class);
                    assertThat(context).doesNotHaveBean(StationWebSocketController.class);
                    assertThat(context.getBean(LiveUpdates.class)).isInstanceOf(NoopLiveUpdates.class);
                    // Клиент заказов — только со сторожем функции orders
                    RestTemplate client = context.getBean(RestTemplate.class);
                    assertThat(client.getInterceptors()).singleElement().isInstanceOfSatisfying(OutboundGuard.class,
                            guard -> assertThat(guard.feature()).isEqualTo(Feature.ORDERS));
                    // Фильтр ключа колбэков — после Spring Security (порядок по умолчанию)
                    FilterRegistrationBean<?> filter = context.getBean("ordersCallbackKeyFilter",
                            FilterRegistrationBean.class);
                    assertThat(filter.getFilter()).isInstanceOf(OrdersCallbackKeyFilter.class);
                    assertThat(((OrdersCallbackKeyFilter) filter.getFilter()).hasKey()).isTrue();
                    assertThat(filter.getOrder()).isGreaterThan(SecurityProperties.DEFAULT_FILTER_ORDER);
                    // Тестовая синхронизация выключена и в full
                    for (Class<?> bean : TESTSYNC_BEANS) assertThat(context).doesNotHaveBean(bean);
                });
    }

    @Test
    void webSocketOnUsesLocalSockJsOriginsFromCeilingAndNoAppPrefix() {
        runner().withPropertyValues("awms.features.preset=full").run(context -> {
            assertThat(context).hasNotFailed();
            for (Class<?> bean : WEBSOCKET_BEANS) assertThat(context).hasSingleBean(bean);
            assertThat(context.getBean(LiveUpdates.class)).isInstanceOf(StompLiveUpdates.class);
            assertThat(context).hasSingleBean(SimpMessagingTemplate.class);
            assertThat(context).doesNotHaveBean(NoopLiveUpdates.class);

            AbstractSockJsService sockJs = sockJsService(context);
            assertThat(sockJs.getSockJsClientLibraryUrl()).isEqualTo("/vendor/sockjs/sockjs.min.js");
            // awms.websocket.allowed-origins не задан — список CORS (умолчание кода), без «*»
            assertThat(sockJs.getAllowedOriginPatterns()).containsExactlyInAnyOrder("http://localhost",
                    "http://localhost:3000", "http://127.0.0.1:3000");
            assertThat(sockJs.getAllowedOrigins()).isEmpty();
            // Префикса /app нет: обработчиков @MessageMapping в коде нет
            assertThat(context.getBean(SimpAnnotationMethodMessageHandler.class).getDestinationPrefixes()).isEmpty();
        });
        runner().withPropertyValues("awms.features.preset=full",
                "awms.websocket.allowed-origins=https://awms.zavod.local").run(context ->
                assertThat(sockJsService(context).getAllowedOriginPatterns())
                        .containsExactly("https://awms.zavod.local"));
    }

    private static AbstractSockJsService sockJsService(AssertableWebApplicationContext context) {
        HandlerMapping mapping = context.getBean("stompWebSocketHandlerMapping", HandlerMapping.class);
        Object handler = ((AbstractUrlHandlerMapping) mapping).getHandlerMap().get("/ws-stations/**");
        assertThat(handler).isInstanceOf(SockJsHttpRequestHandler.class);
        return (AbstractSockJsService) ((SockJsHttpRequestHandler) handler).getSockJsService();
    }

    @Test
    void testsyncOnlyByExplicitCeilingKeyAndWithGuardedClient() {
        runner().withPropertyValues("awms.features.preset=full", "awms.features.testsync.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    for (Class<?> bean : TESTSYNC_BEANS) assertThat(context).hasSingleBean(bean);
                    RestTemplate client = (RestTemplate) ReflectionTestUtils.getField(
                            context.getBean(SyncService.class), "restTemplate");
                    assertThat(client).isNotNull();
                    assertThat(client.getInterceptors()).singleElement().isInstanceOfSatisfying(OutboundGuard.class,
                            guard -> assertThat(guard.feature()).isEqualTo(Feature.TESTSYNC));
                    // Общий клиент заказов и клиент тестовой синхронизации — разные
                    assertThat(client).isNotSameAs(context.getBean(RestTemplate.class));
                });
    }

    // ==================== Адресаты живых обновлений не меняются ====================

    @Test
    void stationChangeIsPublishedToSameDestinations() {
        StationService stations = mock(StationService.class);
        LiveUpdates live = mock(LiveUpdates.class);
        List<StationStaticDto> allStatic = List.of();
        List<StationDynamicDto> allDynamic = List.of();
        StationStaticDto oneStatic = new StationStaticDto();
        StationDynamicDto oneDynamic = new StationDynamicDto();
        when(stations.getAllStaticStations()).thenReturn(allStatic);
        when(stations.getAllDynamicStations()).thenReturn(allDynamic);
        when(stations.getStaticByUid("u1")).thenReturn(oneStatic);
        when(stations.getDynamicByUid("u1")).thenReturn(oneDynamic);

        new StationWebSocketController(live, stations).handleStationChange(new StationChangeEvent("u1"));

        var order = inOrder(live);
        order.verify(live).publish("/topic/stations/static", allStatic);
        order.verify(live).publish("/topic/stations/dynamic", allDynamic);
        order.verify(live).publish("/topic/stations/dynamic/u1", oneDynamic);
        order.verify(live).publish("/topic/stations/static/u1", oneStatic);
        order.verifyNoMoreInteractions();
    }
}

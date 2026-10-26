package com.example.dinamika_back.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import com.example.dinamika_back.controller.OrderController;
import com.example.dinamika_back.controller.TkpController;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.GateRule;
import com.example.dinamika_back.service.OrderService;
import com.example.dinamika_back.service.TkpService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ключ колбэков внешней системы заказов (пакет B3): колбэки из {@code Feature.ORDERS.callbacks()} без верного
 * {@code X-API-Key} получают 401 без подробностей и до сервисов не доходят; пути, которые вызывает фронтенд, фильтр
 * пропускает; без ключа в настройках отклоняются все колбэки.
 */
class OrdersCallbackKeyFilterTest {

    private static final String KEY = "test-callback-key";

    private final OrderService orders = mock(OrderService.class);
    private final TkpService tkp = mock(TkpService.class);

    private MockMvc mvc(String configuredKey) {
        return MockMvcBuilders.standaloneSetup(new OrderController(orders), new TkpController(tkp))
                .addFilters(new OrdersCallbackKeyFilter(configuredKey)).build();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Колбэки SaaS: трек, статус и причина заказа, новое ТКП, статус счёта */
    private static List<MockHttpServletRequestBuilder> callbacks() {
        return List.of(json(post("/api/orders/u1/track"), "{\"statustrack\":\"x\"}"),
                json(post("/api/orders/u1/status"), "{\"status\":\"closed\"}"),
                json(post("/api/orders/u1/statusreason"), "{\"statusreason\":\"x\"}"),
                json(post("/api/tkp/t1"), "{\"order_uid\":\"u1\"}"),
                json(post("/api/tkp/t1/statusinvoice"), "{\"statusinvoice\":\"paid\"}"));
    }

    @Test
    void callbacksWithoutValidKeyAre401AndReachNothing() throws Exception {
        MockMvc mvc = mvc(KEY);
        for (MockHttpServletRequestBuilder request : callbacks()) {
            mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        }
        for (String wrong : List.of("", "test-callback-ke", "test-callback-key ", "TEST-CALLBACK-KEY",
                "test-callback-key-and-more")) {
            for (MockHttpServletRequestBuilder request : callbacks()) {
                mvc.perform(request.header(OrdersCallbackKeyFilter.HEADER, wrong)).andExpect(status().isUnauthorized());
            }
        }
        verifyNoInteractions(orders, tkp);
    }

    @Test
    void callbacksWithValidKeyPass() throws Exception {
        MockMvc mvc = mvc(KEY);
        for (MockHttpServletRequestBuilder request : callbacks()) {
            mvc.perform(request.header(OrdersCallbackKeyFilter.HEADER, KEY)).andExpect(status().is2xxSuccessful());
        }
        verify(orders).saveTrackLocally("u1", "x");
        verify(orders).receiveStatusUpdate("u1", "closed");
        verify(orders).receiveStatusReasonUpdate("u1", "x");
        verify(tkp).receiveTkp(anyString(), any());
        verify(tkp).receiveTkpStatusUpdate("t1", "paid");
    }

    @Test
    void frontendPathsPassWithoutKey() throws Exception {
        MockMvc mvc = mvc(KEY);
        mvc.perform(get("/api/orders/active")).andExpect(status().isOk());
        mvc.perform(get("/api/orders/u1")).andExpect(status().isOk());
        mvc.perform(json(post("/api/orders/u1"), "{\"products\":[]}")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/u1/cancel")).andExpect(status().isOk());
        mvc.perform(get("/api/tkp/closed")).andExpect(status().isOk());
        mvc.perform(json(post("/api/tkp/t1/confirm"), "{}")).andExpect(status().isOk());
        mvc.perform(post("/api/tkp/t1/cancel")).andExpect(status().isOk());
        verify(orders).cancelOrder("u1");
        verify(tkp).confirmTkp(anyString(), any());
        verify(tkp).cancelTkp("t1");
    }

    @Test
    void withoutConfiguredKeyAllCallbacksAreRejected() throws Exception {
        for (String configured : new String[]{null, "", "   "}) {
            OrdersCallbackKeyFilter filter = new OrdersCallbackKeyFilter(configured);
            assertThat(filter.hasKey()).isFalse();
            MockMvc mvc = mvc(configured);
            for (MockHttpServletRequestBuilder request : callbacks()) {
                // Пустой присланный ключ не совпадает с «пустым ожидаемым»
                mvc.perform(request.header(OrdersCallbackKeyFilter.HEADER, "")).andExpect(status().isUnauthorized());
            }
            mvc.perform(get("/api/orders/active")).andExpect(status().isOk());
        }
        verify(orders, never()).receiveStatusUpdate(anyString(), anyString());
        verifyNoInteractions(tkp);
    }

    @Test
    void rejectedPathIsLoggedRawNotDecoded() throws Exception {
        // Раскодированный путь с пробелами выглядел бы в awms-security.log служебной строкой (головой цепочки
        // JOURNAL_HEAD): в лог идёт сырой getRequestURI с %20, управляющие символы экранируются
        // Только в память: файл журнала безопасности стенда (logs/awms-security.log) тест не трогает
        Logger security = (Logger) LoggerFactory.getLogger("AWMS.SECURITY");
        List<Appender<ILoggingEvent>> detached = new ArrayList<>();
        security.iteratorForAppenders().forEachRemaining(detached::add);
        detached.forEach(security::detachAppender);
        boolean additive = security.isAdditive();
        security.setAdditive(false);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        security.addAppender(captured);
        try {
            String raw = "/api/orders/JOURNAL_HEAD%20id=999999%20hash=" + "f".repeat(64) + "%20type=STARTUP/status";
            MockHttpServletRequest request = new MockHttpServletRequest("POST", raw);
            MockHttpServletResponse response = new MockHttpServletResponse();
            new OrdersCallbackKeyFilter(KEY).doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(captured.list).singleElement().satisfies(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.WARN);
                assertThat(e.getFormattedMessage()).contains(raw).doesNotContain("JOURNAL_HEAD id=");
            });
        } finally {
            security.detachAppender(captured);
            detached.forEach(security::addAppender);
            security.setAdditive(additive);
        }
    }

    @Test
    void callbackListIsExactlyTheRegistry() {
        assertThat(Feature.ORDERS.callbacks()).isNotEmpty();
        for (GateRule rule : Feature.ORDERS.callbacks()) {
            assertThat(OrdersCallbackKeyFilter.isCallback(rule.method(), rule.sampleUrl())).as(rule.spec()).isTrue();
        }
        // HEAD/GET на те же адреса и пути фронтенда колбэками не считаются
        assertThat(OrdersCallbackKeyFilter.isCallback("GET", "/api/orders/u1/status")).isFalse();
        assertThat(OrdersCallbackKeyFilter.isCallback("POST", "/api/orders/u1")).isFalse();
        assertThat(OrdersCallbackKeyFilter.isCallback("POST", "/api/tkp/t1/confirm")).isFalse();
        assertThat(OrdersCallbackKeyFilter.isCallback("POST", "/api/tkp/t1/cancel")).isFalse();
    }
}

package com.example.dinamika_back.config;

import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.service.LiveUpdates;
import com.example.dinamika_back.service.StompLiveUpdates;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Клиент WebSocket только подписывается (пакет B3): кадр SEND от клиента и подписка вне /topic/ отбрасываются
 * перехватчиком clientInboundChannel и до простого брокера не доходят, поэтому подделать живые данные
 * (/topic/stations/dynamic, /topic/orders/refresh) нельзя; публикации сервера идут через brokerChannel без
 * перехватчика и доходят до подписчиков как раньше.
 */
class WebSocketInboundInterceptorTest {

    private final WebSocketConfig.ClientInboundInterceptor interceptor = new WebSocketConfig.ClientInboundInterceptor();
    private final MessageChannel channel = (message, timeout) -> true;

    private static Message<byte[]> frame(StompCommand command, String sessionId, String destination, String body) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        if (destination != null) accessor.setDestination(destination);
        if (command == StompCommand.SUBSCRIBE || command == StompCommand.UNSUBSCRIBE) accessor.setSubscriptionId("sub-0");
        if (command == StompCommand.CONNECT) {
            accessor.setAcceptVersion("1.2");
            accessor.setHeader(SimpMessageHeaderAccessor.HEART_BEAT_HEADER, new long[]{0, 0});
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8),
                accessor.getMessageHeaders());
    }

    // ==================== Перехватчик ====================

    @Test
    void clientSendIsDropped() {
        for (String destination : List.of("/topic/stations/dynamic", "/topic/orders/refresh", "/topic/tkp/new",
                "/app/anything", "/user/queue/x")) {
            assertThat(interceptor.preSend(frame(StompCommand.SEND, "s1", destination, "[]"), channel)).as(destination)
                    .isNull();
        }
        assertThat(interceptor.rejected()).isEqualTo(5);
    }

    @Test
    void subscriptionOnlyToTopics() {
        for (String destination : List.of("/topic/stations/static", "/topic/stations/dynamic/u1", "/topic/tkp/status")) {
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "s1", destination, null);
            assertThat(interceptor.preSend(message, channel)).as(destination).isSameAs(message);
        }
        for (String destination : List.of("/user/queue/errors", "/app/x", "/topicx", "/queue/x")) {
            assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", destination, null), channel))
                    .as(destination).isNull();
        }
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", null, null), channel)).isNull();
    }

    @Test
    void sessionFramesPass() {
        for (StompCommand command : List.of(StompCommand.CONNECT, StompCommand.STOMP, StompCommand.UNSUBSCRIBE,
                StompCommand.DISCONNECT, StompCommand.ACK, StompCommand.NACK)) {
            Message<byte[]> message = frame(command, "s1", null, null);
            assertThat(interceptor.preSend(message, channel)).as(command.name()).isSameAs(message);
        }
        assertThat(interceptor.rejected()).isZero();
    }

    // ==================== В контексте брокера ====================

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of())))
                .withPropertyValues("awms.features.preset=full")
                .withUserConfiguration(FeatureService.class, AccessAudit.Noop.class, WebSocketConfig.class,
                        StompLiveUpdates.class);
    }

    @Test
    void interceptorStandsOnClientInboundOnly() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("clientInboundChannel", AbstractMessageChannel.class).getInterceptors())
                    .hasAtLeastOneElementOfType(WebSocketConfig.ClientInboundInterceptor.class);
            assertThat(context.getBean("brokerChannel", AbstractMessageChannel.class).getInterceptors())
                    .noneMatch(WebSocketConfig.ClientInboundInterceptor.class::isInstance);
        });
    }

    @Test
    void forgedClientMessageNeverReachesSubscribersButServerPublishDoes() {
        runner().run(context -> {
            AbstractMessageChannel inbound = context.getBean("clientInboundChannel", AbstractMessageChannel.class);
            AbstractMessageChannel outbound = context.getBean("clientOutboundChannel", AbstractMessageChannel.class);
            BlockingQueue<Message<?>> delivered = new LinkedBlockingQueue<>();
            outbound.addInterceptor(new ChannelInterceptor() {
                @Override
                public Message<?> preSend(Message<?> message, MessageChannel channel) {
                    if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE) {
                        delivered.add(message);
                    }
                    return message;
                }
            });

            // Подписка обрабатывается брокером в пуле clientInboundChannel — ждём, пока брокер её примет.
            // Реестр подписок не опрашивается: опрос параллельно с регистрацией мог задвоить подписку в его кэше
            CountDownLatch subscribed = new CountDownLatch(1);
            inbound.addInterceptor(new ExecutorChannelInterceptor() {
                @Override
                public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
                                                Exception ex) {
                    if (handler instanceof SimpleBrokerMessageHandler && SimpMessageHeaderAccessor.getMessageType(
                            message.getHeaders()) == SimpMessageType.SUBSCRIBE) {
                        subscribed.countDown();
                    }
                }
            });

            // Оператор s1 подписан на динамику станций
            assertThat(inbound.send(frame(StompCommand.CONNECT, "s1", null, null))).isTrue();
            assertThat(inbound.send(frame(StompCommand.SUBSCRIBE, "s1", "/topic/stations/dynamic", null))).isTrue();
            assertThat(subscribed.await(5, TimeUnit.SECONDS)).as("брокер принял подписку").isTrue();

            // Чужой клиент s2 пытается разослать поддельное состояние: кадр отброшен до брокера
            assertThat(inbound.send(frame(StompCommand.CONNECT, "s2", null, null))).isTrue();
            assertThat(inbound.send(frame(StompCommand.SEND, "s2", "/topic/stations/dynamic", "[{\"uid\":\"fake\"}]")))
                    .isFalse();

            // Публикация сервера доходит
            context.getBean(LiveUpdates.class).publish("/topic/stations/dynamic", List.of(Map.of("uid", "real")));
            Message<?> first = delivered.poll(5, TimeUnit.SECONDS);
            assertThat(first).isNotNull();
            assertThat(SimpMessageHeaderAccessor.getSessionId(first.getHeaders())).isEqualTo("s1");
            assertThat(new String((byte[]) first.getPayload(), StandardCharsets.UTF_8)).contains("real")
                    .doesNotContain("fake");
            assertThat(delivered.poll(300, TimeUnit.MILLISECONDS)).isNull();
        });
    }
}

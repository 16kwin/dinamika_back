package com.example.dinamika_back.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Повтор сохранения настроек таблиц (колонки, фильтры, сортировка) при гонке на первой записи.
 *
 * <p>Настройки хранятся одной строкой на пользователя ({@code UNIQUE(user_id)}). При первом открытии таблицы
 * фронтенд сохраняет колонки, фильтры и сортировку параллельно: оба запроса не находят строку, оба вставляют,
 * второй падает на уникальном ключе — пользователь видел разовую ошибку 500.
 *
 * <p>Аспект стоит снаружи транзакции ({@link Ordered#HIGHEST_PRECEDENCE}, транзакции — {@code LOWEST_PRECEDENCE}):
 * при нарушении ключа сохранение повторяется один раз в новой транзакции — строка уже есть, запрос её обновляет.
 * Методы {@code save*} сервисов настроек вызывают только контроллеры, внешней транзакции у них нет.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ColumnSettingsRetryAspect {

    private static final Logger LOG = LoggerFactory.getLogger(ColumnSettingsRetryAspect.class);

    @Around("execution(public * com.example.dinamika_back.service.*ColumnSettingsService.save*(..))")
    public Object retryOnDuplicateRow(ProceedingJoinPoint call) throws Throwable {
        try {
            return call.proceed();
        } catch (DataIntegrityViolationException e) {
            LOG.debug("Настройки таблицы: строку пользователя создал параллельный запрос, повторяю {}",
                    call.getSignature().toShortString());
            return call.proceed();
        }
    }
}

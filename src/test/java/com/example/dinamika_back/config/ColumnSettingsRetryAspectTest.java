package com.example.dinamika_back.config;

import com.example.dinamika_back.model.UserEmployeeColumnSettings;
import com.example.dinamika_back.repository.UserEmployeeColumnSettingsRepository;
import com.example.dinamika_back.service.EmployeeColumnSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Гонка на первой записи настроек таблицы: параллельный запрос уже вставил строку пользователя, наш падает на
 * {@code UNIQUE(user_id)} — аспект повторяет сохранение один раз, и повтор обновляет найденную строку.
 */
class ColumnSettingsRetryAspectTest {

    private final UserEmployeeColumnSettingsRepository repository = mock(UserEmployeeColumnSettingsRepository.class);

    private EmployeeColumnSettingsService proxied() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new EmployeeColumnSettingsService(repository));
        factory.setProxyTargetClass(true);
        factory.addAspect(new ColumnSettingsRetryAspect());
        return factory.getProxy();
    }

    @Test
    void duplicateRowOnFirstSaveIsRetriedAndUpdatesExistingRow() {
        UserEmployeeColumnSettings existing = UserEmployeeColumnSettings.builder()
                .userId(7).columnsJson("{}").filtersJson("{}").sortJson("{}").build();
        // первый вызов: строки нет, вставка падает на ключе; повтор: строку уже вставил параллельный запрос
        when(repository.findByUserId(7)).thenReturn(Optional.empty(), Optional.of(existing));
        when(repository.save(any()))
                .thenThrow(new DataIntegrityViolationException("user_employee_column_settings_user_id_key"))
                .thenAnswer(call -> call.getArgument(0));

        proxied().saveFiltersJson(7, "{\"name\":\"Иванов\"}");

        verify(repository, times(2)).save(any());
        assertThat(existing.getFiltersJson()).isEqualTo("{\"name\":\"Иванов\"}");
    }

    @Test
    void secondFailureIsNotSwallowed() {
        when(repository.findByUserId(7)).thenReturn(Optional.empty());
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("users_fk"));

        assertThatThrownBy(() -> proxied().saveSortJson(7, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(repository, times(2)).save(any());
    }

    @Test
    void readsAreNotWrapped() {
        when(repository.findByUserId(7)).thenThrow(new DataIntegrityViolationException("x"));

        assertThatThrownBy(() -> proxied().getColumnsJson(7)).isInstanceOf(DataIntegrityViolationException.class);
        verify(repository, times(1)).findByUserId(7);
    }
}

package com.example.dinamika_back.features;

/**
 * Ошибка профиля функций: опечатка в ключе или пресете, ключ белого списка вне файла потолка, чужой ключ в файле
 * потолка, недопустимое значение. Запуск отменяется (fail-closed), сообщение называет ключ и источник.
 */
public class FeatureConfigurationException extends IllegalStateException {

    public FeatureConfigurationException(String message) {
        super(message);
    }

    public FeatureConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

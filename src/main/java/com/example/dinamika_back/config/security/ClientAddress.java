package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.features.net.IpNetwork;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Адрес клиента для правил доступа без входа и журналов (параметр потолка {@code awms.security.trusted-proxies}).
 *
 * <p>Заголовкам {@code X-Forwarded-For} и {@code X-Real-IP} служба верит, только если соединение пришло от
 * доверенного прокси (умолчание — nginx на этом же сервере: 127.0.0.1 и ::1). От любого другого адреса заголовки
 * не читаются: клиент, подключившийся к порту службы напрямую, не выдаст себя за станцию цеха.
 *
 * <p>За доверенным прокси:
 * <ul>
 *   <li>{@code X-Forwarded-For} читается справа налево, доверенные прокси пропускаются, первый чужой адрес — клиент.
 *       Левые записи присылает сам клиент, поэтому их подделка ничего не даёт;</li>
 *   <li>{@code X-Real-IP} ставит nginx ({@code $remote_addr}), им пользуются, если {@code X-Forwarded-For} нет;</li>
 *   <li>оба заголовка есть и называют разных клиентов — один из них прислал клиент, адрес неизвестен;</li>
 *   <li>запись не адрес (имя узла, {@code unknown}) — адрес неизвестен.</li>
 * </ul>
 * Неизвестный адрес не входит ни в одну сеть: анонимный запрос при заданном списке сетей отклоняется.
 * Шаблон {@code deploy/nginx} ставит оба заголовка равными адресу соединения nginx.
 */
public final class ClientAddress {

    /** Адрес клиента: байты (null — неизвестен) и запись для журналов и логов (адрес — в канонической форме) */
    public record Resolved(byte[] address, String text) {

        public boolean known() {
            return address != null;
        }
    }

    private final List<IpNetwork> trustedProxies;

    public ClientAddress(List<IpNetwork> trustedProxies) {
        this.trustedProxies = List.copyOf(trustedProxies);
    }

    /** Доверенные прокси из потолка; запись уже проверена при разборе потолка */
    public static ClientAddress of(FeatureSnapshot ceiling) {
        return new ClientAddress(networks(ceiling, CeilingParam.TRUSTED_PROXIES));
    }

    /** Сети из параметра потолка */
    static List<IpNetwork> networks(FeatureSnapshot ceiling, CeilingParam param) {
        List<IpNetwork> result = new ArrayList<>();
        for (String item : ceiling.listParam(param)) {
            IpNetwork network = IpNetwork.parse(item);
            if (network != null) result.add(network);
        }
        return List.copyOf(result);
    }

    public List<IpNetwork> trustedProxies() {
        return trustedProxies;
    }

    /** Адрес клиента запроса */
    public Resolved resolve(HttpServletRequest request) {
        String remoteText = request.getRemoteAddr();
        byte[] remote = IpNetwork.literal(remoteText);
        if (remote == null) return new Resolved(null, "адрес соединения не разобран");
        if (!trusted(remote)) return known(remote);

        List<String> forwarded = forwardedFor(request);
        String realIpText = singleHeader(request, "X-Real-IP");
        Resolved fromForwarded = null;
        if (forwarded != null) {
            fromForwarded = fromForwardedFor(forwarded);
            if (fromForwarded == null) return unknown(remoteText);
        }
        Resolved fromRealIp = null;
        if (realIpText != null) {
            byte[] realIp = IpNetwork.literal(realIpText);
            if (realIp == null) return unknown(remoteText);
            // X-Real-IP с адресом прокси — его поставил внутренний прокси цепочки, клиента он не называет
            if (!trusted(realIp) || forwarded == null) fromRealIp = known(realIp);
        }
        if (fromForwarded != null && fromRealIp != null && !Arrays.equals(fromForwarded.address(), fromRealIp.address())) {
            return unknown(remoteText);
        }
        if (fromForwarded != null) return fromForwarded;
        if (fromRealIp != null) return fromRealIp;
        return known(remote);
    }

    private boolean trusted(byte[] address) {
        for (IpNetwork proxy : trustedProxies) {
            if (proxy.contains(address)) return true;
        }
        return false;
    }

    /** Справа налево до первого недоверенного; все записи — прокси: клиент — самая левая. Ошибка записи — null */
    private Resolved fromForwardedFor(List<String> entries) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            String entry = entries.get(i);
            byte[] address = IpNetwork.literal(withoutPort(entry));
            if (address == null) return null;
            if (!trusted(address) || i == 0) return known(address);
        }
        return null;
    }

    /** Все строки X-Forwarded-For по порядку, через запятую; заголовка нет — null, пустая запись — ошибка */
    private static List<String> forwardedFor(HttpServletRequest request) {
        List<String> headers = Collections.list(request.getHeaders("X-Forwarded-For"));
        if (headers.isEmpty()) return null;
        List<String> entries = new ArrayList<>();
        for (String header : headers) {
            for (String part : header.split(",", -1)) entries.add(part.trim());
        }
        return entries;
    }

    /** Значение заголовка, если он один; несколько строк — значение, которое не разберётся как адрес */
    private static String singleHeader(HttpServletRequest request, String name) {
        List<String> values = Collections.list(request.getHeaders(name));
        if (values.isEmpty()) return null;
        return values.size() == 1 ? values.get(0) : "";
    }

    /** {@code 10.0.5.7:51234} → {@code 10.0.5.7}, {@code [fd00::1]:443} → {@code fd00::1}; IPv6 без скобок — как есть */
    private static String withoutPort(String entry) {
        if (entry.startsWith("[")) {
            int end = entry.indexOf(']');
            return end < 0 ? entry : entry.substring(1, end);
        }
        int colon = entry.indexOf(':');
        if (colon > 0 && colon == entry.lastIndexOf(':')) return entry.substring(0, colon);
        return entry;
    }

    private static Resolved known(byte[] address) {
        return new Resolved(address, format(address));
    }

    private static Resolved unknown(String remoteText) {
        return new Resolved(null, "за прокси " + format(IpNetwork.literal(remoteText))
                + ": X-Forwarded-For и X-Real-IP не сходятся или не адрес");
    }

    /** Каноническая запись адреса: 10.0.5.7, fd00:0:0:0:0:0:0:1 */
    static String format(byte[] address) {
        try {
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException e) {
            return "?";
        }
    }
}

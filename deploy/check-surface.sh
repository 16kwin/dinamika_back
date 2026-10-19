#!/usr/bin/env bash
# Проверка поверхности AWMS «ДИНАМИКА» на сервере Linux: профиль функций, порты и соединения службы,
# дочерние процессы, ffmpeg и ответы закрытых путей на GET и HEAD. Аналог check-surface.ps1.
#
# Скрипт только читает: ничего не меняет, службу не перезапускает, в систему не входит (пробы анонимные).
# Аргументы командной строки процессов, значения переменных окружения и файлов настроек НЕ выводятся: в них
# бывают пароли. Печатаются только имена процессов, PID, порты, имена ключей и переменных, признаки «да/нет».
# Ключи белого списка потолка ищутся в аргументах и окружении процесса службы и в application*.properties
# его рабочего каталога (config/, config/*/).
#
# Запуск (лучше от root — иначе ss не покажет чужие процессы):
#   sudo bash check-surface.sh
#   sudo bash check-surface.sh --expect-off video,orders,websocket,pwa
#   sudo bash check-surface.sh --base-url https://awms.zavod.local --insecure
# Параметры:
#   --port N          порт службы (server.port), по нему ищется процесс java; по умолчанию 8084
#   --pid N           PID службы, если поиск по порту не подходит
#   --base-url URL    адрес для проб; по умолчанию http://localhost:<порт> — сама служба, без nginx
#   --config FILE     файл потолка; по умолчанию $AWMS_FEATURES_FILE, иначе ./config/awms-features.properties
#   --ffmpeg FILE     путь к ffmpeg; по умолчанию video.ffmpeg-path из файла потолка
#   --expect-off LIST выключенные в профиле функции через запятую (из отчёта или print-features): для них и их
#                     дочерних ждём 404, для остальных — не 404. Без параметра коды просто печатаются
#   --insecure        не проверять сертификат HTTPS

set -u

PORT=8084
SVC_PID=""
BASE_URL=""
CONFIG_FILE="${AWMS_FEATURES_FILE:-./config/awms-features.properties}"
FFMPEG_PATH=""
EXPECT_OFF=""
CURL_INSECURE=""
WORKDIR=""

while [ $# -gt 0 ]; do
    case "$1" in
        --port) PORT="$2"; shift 2 ;;
        --pid) SVC_PID="$2"; shift 2 ;;
        --base-url) BASE_URL="$2"; shift 2 ;;
        --config) CONFIG_FILE="$2"; shift 2 ;;
        --ffmpeg) FFMPEG_PATH="$2"; shift 2 ;;
        --expect-off) EXPECT_OFF="$2"; shift 2 ;;
        --insecure|-k) CURL_INSECURE="-k"; shift ;;
        -h|--help) sed -n '2,23p' "$0"; exit 0 ;;
        *) echo "Неизвестный параметр: $1 (см. --help)" >&2; exit 2 ;;
    esac
done
[ -n "$BASE_URL" ] || BASE_URL="http://localhost:$PORT"
BASE_URL="${BASE_URL%/}"

section() { printf '\n=== %s ===\n' "$1"; }
has() { command -v "$1" >/dev/null 2>&1; }
# ss без строки заголовка (ключ -H есть не во всех версиях iproute2)
ssq() { ss "$@" 2>/dev/null | tail -n +2; }

# Значение ключа из .properties (без продолжений строк — в потолке их нет)
prop() {
    [ -f "$1" ] || return 0
    sed -nE "s/^[[:space:]]*$(printf '%s' "$2" | sed 's/[.]/\\./g')[[:space:]]*[=:][[:space:]]*(.*)$/\1/p" "$1" | tail -n 1 | tr -d '\r'
}

# Имена ключей .properties без значений (комментарии и строки-продолжения после «\» пропускаются)
prop_names() {
    awk '
        { sub(/\r$/, "") }
        cont { cont = ($0 ~ /(^|[^\\])(\\\\)*\\$/); next }
        /^[ \t]*([#!]|$)/ { next }
        {
            line = $0; sub(/^[ \t]+/, "", line)
            if (match(line, /^[^ \t=:]+/)) print substr(line, 1, RLENGTH)
            cont = ($0 ~ /(^|[^\\])(\\\\)*\\$/)
        }
    ' "$1"
}

# Белый список потолка (раздел 4.5 проекта): эти ключи служба принимает только из файла потолка и умолчаний jar,
# в аргументах, окружении и внешних application.properties они отменяют запуск. Прочие server.*, spring.*,
# logging.* разрешены. Вид ключа: list — белый список, extra — дополнительный источник настроек.
# Имена сравниваются, как в Spring: без учёта регистра, дефисов и подчёркиваний
key_kind() {
    case "$(printf '%s' "$1" | tr 'A-Z' 'a-z' | tr -d '_-')" in
        server.address|server.port|server.ssl.enabled|server.ssl.keystore|server.ssl.protocol|sync.service.url|video.ffmpegpath|awms.*)
            echo list ;;
        spring.config.*|spring.application.json) echo extra ;;
    esac
}

# То же для имени переменной окружения (обе формы Spring: VIDEO_FFMPEGPATH и VIDEO_FFMPEG_PATH)
env_kind() {
    case "$1" in *.*) key_kind "$1"; return ;; esac
    case "$(printf '%s' "$1" | tr 'a-z' 'A-Z')" in
        AWMS_FEATURES_FILE|AWMS_SECRETS_FILE) ;;
        SERVER_ADDRESS|SERVER_PORT|SERVER_SSL_ENABLED|SERVER_SSL_KEYSTORE|SERVER_SSL_KEY_STORE|SERVER_SSL_PROTOCOL|\
        SYNC_SERVICE_URL|VIDEO_FFMPEGPATH|VIDEO_FFMPEG_PATH|AWMS_*) echo list ;;
        SPRING_APPLICATION_JSON|SPRING_CONFIG_*) echo extra ;;
    esac
}

# Печать найденных имён через пробел (значения не печатаются никогда); код 1 — ничего не найдено
LIST_FOUND=0
report_keys() {
    local where="$1" list extra
    list="$(printf '%s' "$2" | tr ' ' '\n' | sed '/^$/d' | sort -u | paste -sd, - | sed 's/,/, /g')"
    extra="$(printf '%s' "$3" | tr ' ' '\n' | sed '/^$/d' | sort -u | paste -sd, - | sed 's/,/, /g')"
    [ -n "$list$extra" ] || return 1
    if [ -n "$list" ]; then
        echo "  ВНИМАНИЕ: $where — ключи белого списка (значения не показаны): $list"
        LIST_FOUND=$((LIST_FOUND + 1))
    fi
    [ -z "$extra" ] || echo "  $where — дополнительные источники настроек: $extra. Проверить, нет ли в них ключей белого списка"
    return 0
}

file_info() {
    local label="$1" path="$2"
    if [ ! -f "$path" ]; then
        echo "$label: нет файла ($path)"
        return
    fi
    echo "$label: $(readlink -f "$path" 2>/dev/null || echo "$path")"
    if has stat; then
        stat -c '  владелец: %U:%G, права: %A, изменён: %y' "$path" 2>/dev/null
    fi
}

echo "Проверка поверхности AWMS «ДИНАМИКА» — $(date '+%Y-%m-%d %H:%M:%S'), хост $(hostname)"

# ---------------------------------------------------------------------------------------------------------
section 'Профиль функций (файл потолка) и файл секретов'
if [ -f "$CONFIG_FILE" ]; then
    file_info 'Файл потолка' "$CONFIG_FILE"
    if has sha256sum; then echo "  SHA-256: $(sha256sum "$CONFIG_FILE" | cut -d' ' -f1)"; fi
    preset="$(prop "$CONFIG_FILE" awms.features.preset)"
    echo "  пресет: ${preset:-не задан — служба работает в isolated}"
    if [ "$(head -c 3 "$CONFIG_FILE" | od -An -tx1 | tr -d ' \n')" = "efbbbf" ]; then
        echo "  ВНИМАНИЕ: файл сохранён с BOM — первая строка прочитается неверно. Пересохранить как UTF-8 без BOM"
    fi
else
    echo "Файл потолка: нет ($CONFIG_FILE). Служба работает в «Изолированном минимуме» (isolated)"
fi
SECRETS_FILE="${AWMS_SECRETS_FILE:-$(dirname "$CONFIG_FILE")/awms-secrets.properties}"
file_info 'Файл секретов (содержимое не читается)' "$SECRETS_FILE"

# ---------------------------------------------------------------------------------------------------------
section 'Процесс службы'
if [ -z "$SVC_PID" ]; then
    if has ss; then
        SVC_PID="$(ssq -ltnp "sport = :$PORT" | sed -nE 's/.*pid=([0-9]+).*/\1/p' | head -n 1)"
    fi
    if [ -z "$SVC_PID" ] && has lsof; then
        SVC_PID="$(lsof -t -iTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | head -n 1)"
    fi
fi

FAMILY=""
if [ -z "$SVC_PID" ] || [ ! -d "/proc/$SVC_PID" ]; then
    echo "Процесс не найден (порт $PORT${SVC_PID:+, PID $SVC_PID}). Укажите --port или --pid; без root ss не видит чужие процессы."
    echo "Пробы ниже всё равно выполняются"
else
    echo "$(cat "/proc/$SVC_PID/comm" 2>/dev/null), PID $SVC_PID, учётная запись $(stat -c %U "/proc/$SVC_PID" 2>/dev/null)"
    echo "  исполняемый файл: $(readlink "/proc/$SVC_PID/exe" 2>/dev/null || echo 'нет доступа')"
    echo "  рабочий каталог: $(readlink "/proc/$SVC_PID/cwd" 2>/dev/null || echo 'нет доступа')"
    WORKDIR="$(readlink "/proc/$SVC_PID/cwd" 2>/dev/null)"
    # Аргументы и окружение не печатаются: только признаки и имена ключей белого списка
    if [ -r "/proc/$SVC_PID/cmdline" ]; then
        jdwp='нет'; jmx='нет'; arg_list=''; arg_extra=''
        while IFS= read -r -d '' a; do
            case "$a" in
                -agentlib:jdwp*|-Xrunjdwp*|-Xdebug*) jdwp='ДА' ;;
                -Dcom.sun.management.jmxremote*) jmx='ДА' ;;
            esac
            case "$a" in
                --?*=*|-D?*=*)
                    k="${a#--}"; k="${k#-D}"; k="${k%%=*}"
                    case "$(key_kind "$k")" in list) arg_list="$arg_list $k" ;; extra) arg_extra="$arg_extra $k" ;; esac ;;
            esac
        done < "/proc/$SVC_PID/cmdline"
        echo "  отладчик JDWP в аргументах: $jdwp"
        echo "  удалённый JMX в аргументах: $jmx"
        report_keys 'в аргументах' "$arg_list" "$arg_extra" || echo "  ключей белого списка в аргументах нет"
    else
        echo "  аргументы недоступны (нужен запуск от root)"
    fi
    if [ -r "/proc/$SVC_PID/environ" ]; then
        env_list=''; env_extra=''
        while IFS= read -r -d '' e; do
            n="${e%%=*}"
            case "$(env_kind "$n")" in list) env_list="$env_list $n" ;; extra) env_extra="$env_extra $n" ;; esac
        done < "/proc/$SVC_PID/environ"
        report_keys 'в окружении службы' "$env_list" "$env_extra" || echo "  ключей белого списка в окружении службы нет"
    else
        echo "  окружение недоступно (нужен запуск от root)"
    fi

    # Дерево потомков по /proc: PID и имя (comm), без аргументов
    children=""
    queue="$SVC_PID"
    while [ -n "$queue" ]; do
        set -- $queue
        parent="$1"; shift; queue="$*"
        for stat in /proc/[0-9]*/stat; do
            p="${stat#/proc/}"; p="${p%/stat}"
            ppid="$(sed -E 's/^[0-9]+ \(.*\) [A-Za-z] ([0-9]+) .*/\1/' "$stat" 2>/dev/null)"
            if [ "$ppid" = "$parent" ]; then
                children="$children $p"
                queue="$queue $p"
            fi
        done
    done
    FAMILY="$SVC_PID$children"

    section 'Входящие: что слушает служба'
    if has ss; then
        out="$(ssq -ltnup | grep -E "pid=$SVC_PID," | awk '{print "  " $1 " " $5}')"
        echo "${out:-нет}"
    else
        echo "ss не найден (пакет iproute2)"
    fi

    section 'Установленные соединения службы и её дочерних процессов'
    if has ss; then
        found=0
        for p in $FAMILY; do
            name="$(cat "/proc/$p/comm" 2>/dev/null)"
            ssq -tunp state established | grep -E "pid=$p," | awk -v n="$name" -v p="$p" '{print "  " n " (PID " p "): " $1 " " $(NF-2) " -> " $(NF-1)}'
            if ssq -tunp state established | grep -qE "pid=$p,"; then found=1; fi
        done
        [ "$found" = 1 ] || echo "нет"
    else
        echo "ss не найден (пакет iproute2)"
    fi

    section 'Дочерние процессы службы (только имена)'
    if [ -z "$children" ]; then
        echo "нет"
    else
        with_password=0
        for p in $children; do
            echo "  $(cat "/proc/$p/comm" 2>/dev/null), PID $p"
            # Пароль в адресе камеры (rtsp://логин:пароль@...) — только счётчик, без вывода
            if tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null | grep -Eq '://[^/[:space:]:@]+:[^@[:space:]/]+@'; then
                with_password=$((with_password + 1))
            fi
        done
        echo "  процессов с паролем в аргументах (адрес вида rtsp://логин:пароль@...): $with_password"
    fi
fi

# ---------------------------------------------------------------------------------------------------------
section 'Ключи белого списка во внешних файлах настроек (application*.properties)'
# Файлы, которые Spring Boot читает сам: в рабочем каталоге службы, в config/ и config/*/
if [ -z "$WORKDIR" ]; then
    cfgdir="$(dirname "$CONFIG_FILE")"
    if [ "$(basename "$cfgdir")" = config ]; then WORKDIR="$(dirname "$cfgdir")"; else WORKDIR=.; fi
fi
WORKDIR="$(cd "$WORKDIR" 2>/dev/null && pwd || printf '%s' "$WORKDIR")"
echo "Рабочий каталог: $WORKDIR"
found_files=0
for f in "$WORKDIR"/application*.* "$WORKDIR"/config/application*.* "$WORKDIR"/config/*/application*.*; do
    [ -f "$f" ] || continue
    case "$f" in
        *.properties)
            found_files=1
            file_list=''; file_extra=''
            while IFS= read -r k; do
                case "$(key_kind "$k")" in list) file_list="$file_list $k" ;; extra) file_extra="$file_extra $k" ;; esac
            done <<EOF
$(prop_names "$f")
EOF
            report_keys "$f" "$file_list" "$file_extra" || echo "  $f: ключей белого списка нет" ;;
        *.yml|*.yaml)
            found_files=1
            echo "  $f: есть — YAML скрипт не разбирает, проверить вручную" ;;
    esac
done
[ "$found_files" = 1 ] || echo "  файлов application*.properties и application*.yml нет"
echo
if [ "$LIST_FOUND" -gt 0 ]; then
    echo "  Ключи белого списка перенести в файл потолка (секреты AWMS_* — в файл секретов) и убрать отсюда:"
    echo "  с новой версией служба с ними не запустится. Прочие server.*, spring.*, logging.* оставить на месте"
else
    echo "  Ключей белого списка вне файла потолка не найдено"
fi
[ -n "$FAMILY" ] || echo "  (процесс службы не найден — аргументы и окружение не проверены)"

# ---------------------------------------------------------------------------------------------------------
section 'ffmpeg'
[ -n "$FFMPEG_PATH" ] || FFMPEG_PATH="$(prop "$CONFIG_FILE" video.ffmpeg-path)"
if [ -n "$FFMPEG_PATH" ]; then
    for f in "$FFMPEG_PATH" "$(dirname "$FFMPEG_PATH")/ffprobe"; do
        if [ -e "$f" ]; then echo "  $f: ЕСТЬ"; else echo "  $f: нет"; fi
    done
else
    echo "  video.ffmpeg-path в файле потолка не задан (умолчание jar — путь Windows C:/ffmpeg/bin/ffmpeg.exe)"
fi
for cmd in ffmpeg ffprobe; do
    echo "  $cmd в PATH: $(command -v "$cmd" 2>/dev/null || echo 'нет')"
done
if has pgrep; then
    # pgrep без -a/-l печатает только PID
    pids="$(pgrep -x 'ffmpeg|ffprobe' 2>/dev/null | paste -sd, -)"
    echo "  запущено процессов ffmpeg/ffprobe на сервере: $( [ -n "$pids" ] && echo "$pids" | tr ',' '\n' | wc -l || echo 0 )${pids:+ (PID $pids)}"
fi
if has ss; then
    echo "  соединений RTSP (TCP 554) с сервера: $(ssq -tn state established '( dport = :554 )' | wc -l)"
fi
echo "  При выключенном видео ffmpeg на сервере быть не должно: не устанавливать, установленный удалить (INSTALL.md)"

# ---------------------------------------------------------------------------------------------------------
section "Пробы закрытых путей: анонимно, $BASE_URL"
echo "404 — путь закрыт воротами (функция выключена). 401/403/405/200 — маршрут есть (функция включена;"
echo "без входа запрос дальше проверки прав не проходит). Для сравнения в конце — ядро и несуществующий путь."
if ! has curl; then
    echo "curl не найден — пробы пропущены"
    exit 0
fi

probe() { # метод путь → код ответа
    case "$1" in
        HEAD) curl -s $CURL_INSECURE --max-time 10 -o /dev/null -w '%{http_code}' -I "$BASE_URL$2" ;;
        POST) curl -s $CURL_INSECURE --max-time 10 -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' "$BASE_URL$2" ;;
        POST+файл) printf 'probe' | curl -s $CURL_INSECURE --max-time 10 -o /dev/null -w '%{http_code}' \
                       -F 'file=@-;filename=probe.txt;type=text/plain' "$BASE_URL$2" ;;
        *) curl -s $CURL_INSECURE --max-time 10 -o /dev/null -w '%{http_code}' -X "$1" "$BASE_URL$2" ;;
    esac
}

# Функция выключена, если выключена она сама, её родитель или то, от чего она зависит
expected_off() {
    local k="$1"
    while [ -n "$k" ]; do
        case ",$EXPECT_OFF," in *",$k,"*) return 0 ;; esac
        [ "$k" = "dashboards.graph" ] && expected_off dashboards.audit && return 0
        case "$k" in *.*) k="${k%.*}" ;; *) k="" ;; esac
    done
    return 1
}
EXPECT_OFF="$(printf '%s' "$EXPECT_OFF" | tr -d ' ')"

# Пути — те же, что probe() в реестре функций (Feature.java) и раздел «Проверка из браузера» отчёта.
# Живой поток (/live) не трогаем: даже анонимная проба не должна приближаться к запуску ffmpeg
PROBES="video|GET HEAD|/api/video/status
video.live|GET HEAD|/api/video/cameras/1/snapshot
video.archive|GET HEAD|/api/video/records
video.download|GET HEAD|/api/video/files/1/download
map.plan|GET HEAD|/api/station-plans/workshops/0
model3d|GET HEAD|/api/nomenclature/0/models
gpp|GET HEAD|/api/gpp
dashboards|GET HEAD|/api/dashboard/control-events
dashboards.audit|GET HEAD|/api/dashboard/audit
dashboards.screen|GET HEAD|/api/dashboard/day-events
dashboards.graph|GET HEAD|/api/dashboard/purchase-graph
orders|GET HEAD|/api/orders/active
websocket|GET HEAD|/ws-stations/info
testsync|GET HEAD|/api/test-documents/drafts
export|POST|/api/templates/export-pdf
uploads|POST+файл|/api/nomenclature/0/images"

mismatch=0
printf '\n%-18s %-10s %-34s %-6s %s\n' 'функция' 'метод' 'путь' 'код' 'вывод'
while IFS='|' read -r key methods path; do
    for m in $methods; do
        code="$(probe "$m" "$path")"
        if [ "$code" = "000" ] || [ -z "$code" ]; then
            verdict='нет ответа'
        else
            if [ "$code" = "404" ]; then closed=1; verdict='закрыто'; else closed=0; verdict='маршрут есть'; fi
            if [ -n "$EXPECT_OFF" ]; then
                if expected_off "$key"; then want=1; else want=0; fi
                if [ "$want" = "$closed" ]; then verdict="$verdict — как ожидалось"; else verdict="$verdict — НЕ СОВПАДАЕТ с профилем"; mismatch=$((mismatch + 1)); fi
            fi
        fi
        printf '%-18s %-10s %-34s %-6s %s\n' "$key" "$m" "$path" "$code" "$verdict"
    done
done <<EOF
$PROBES
EOF

echo
printf '%-18s %-10s %-34s %-6s %s\n' '(ядро)' 'GET' '/api/features/public' "$(probe GET /api/features/public)" 'ожидается 200'
printf '%-18s %-10s %-34s %-6s %s\n' '(нет такого)' 'GET' '/api/awms-check-surface-no-such-path' "$(probe GET /api/awms-check-surface-no-such-path)" 'аноним получает 403, не 404'
if [ -n "$EXPECT_OFF" ]; then
    echo
    if [ "$mismatch" = 0 ]; then echo "Итог: все пробы совпадают с профилем"; else echo "Итог: расхождений с профилем — $mismatch"; fi
fi
echo
echo "Повторить вручную: curl -i $BASE_URL/api/video/status   и   curl -I $BASE_URL/api/video/files/1/download"

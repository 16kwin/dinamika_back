# AWMS «ДИНАМИКА»: установка и выкатка с выключателями функций

С версии с выключателями функций сервер сам решает, какие функции работают, — по файлу
`config/awms-features.properties` («потолок установки»). Файл меняет только администратор сервера,
применяется он при перезапуске службы. Подробности — `docs/feature-switches-design.md`, разделы 5 и 14.

Что лежит в `deploy/`:

| Файл | Зачем |
|---|---|
| `features/isolated.properties` | профиль «Изолированный минимум»: только ядро учёта |
| `features/standard.properties` | профиль «Стандарт»: рекомендуемый для закрытого контура |
| `features/full.properties` | профиль «Расширенный»: наш стенд и dynamikaawms.ru, как раньше, кроме четырёх функций (раздел 1, шаг 4) |
| `db/roles.sql` | роли PostgreSQL: владелец схемы и рабочая роль без права править журналы |
| `service/winsw.xml.example`, `service/nssm.txt` | служба Windows с правильными путями |
| `nginx/awms.conf.example` | nginx: только 443, ликвидатор service worker при выключенной PWA |
| `check-surface.ps1`, `check-surface.sh` | проверка: порты, соединения, процессы, ffmpeg, ответы закрытых путей |

В `config/` рядом: `awms-features.properties.example` — все ключи с пояснениями,
`awms-secrets.properties.example` — образец файла секретов (без значений).

---

## 1. Перед выкаткой на dynamikaawms.ru (для тимлида)

**Главное.** Новая версия бэкенда без файла `config/awms-features.properties` поднимется в «Изолированном
минимуме»: учёт и справочники работают, а **видео, карта и план цеха, 3D, ГПП, панели главной, заказы и ТКП,
живые обновления и PWA пропадут**. Это не поломка, а защита: значит, файл не положили или он не там.
Поэтому файл кладётся **до** замены jar.

1. Найти рабочий каталог службы на сервере — тот, где лежат `uploads/` и `video-archive/`. Относительно него
   сервер ищет `config/awms-features.properties`.
2. Создать в нём папку `config` и скопировать туда `deploy/features/full.properties` под именем
   `awms-features.properties`.
   - Копировать файлом (Проводник, `copy`, `cp`). Не создавать через PowerShell `Set-Content`/`Out-File`:
     PowerShell 5.1 добавляет BOM, и первая строка прочитается неверно.
   - Править можно Блокнотом, сохранять «UTF-8» (не «UTF-8 с BOM»). Значения — латиницей.
3. Найти и убрать из настроек службы **ключи белого списка потолка** — иначе новая версия **не запустится**
   (в логе будут имя ключа и место, откуда он взят). Белый список — ровно эти ключи:
   - `server.address`, `server.port`, `server.ssl.enabled`, `server.ssl.key-store`, `server.ssl.protocol`;
   - `sync.service.url`, `video.ffmpeg-path`;
   - все `awms.*`: выключатели `awms.features.*` и параметры безопасности (`awms.cors.allowed-origins`,
     `awms.network.outbound.allowed`, `awms.video.*`, `awms.ui.*` и др.; полный список —
     `config/awms-features.properties.example`).

   Где они бывают:
   - аргументы службы: `--server.port=…`, `-Dserver.address=…`, `--spring.application.json=…`;
   - переменные окружения службы и компьютера: `SERVER_PORT`, `SERVER_ADDRESS`, `SERVER_SSL_ENABLED`,
     `SERVER_SSL_KEYSTORE` (или `SERVER_SSL_KEY_STORE`), `SERVER_SSL_PROTOCOL`, `SYNC_SERVICE_URL`,
     `VIDEO_FFMPEGPATH` (или `VIDEO_FFMPEG_PATH`), `SPRING_APPLICATION_JSON` и любые `AWMS_…`,
     кроме `AWMS_FEATURES_FILE` и `AWMS_SECRETS_FILE`;
   - файлы, которые Spring Boot читает сам: `application.properties` (и `application.yml`) в рабочем каталоге,
     в его `config\` и `config\<папка>\`, а также файлы из `spring.config.location`,
     `spring.config.additional-location`, `spring.config.import` (аргументы или переменные `SPRING_CONFIG_…`).

   Что куда:
   - ключи белого списка — в файл потолка из шага 2 (значения те же), из службы и внешних файлов — убрать;
   - **прочие** `server.*`, `spring.*`, `logging.*` (например `server.tomcat.accesslog.enabled`,
     `server.forward-headers-strategy`) остаются, где были. В файл потолка их **не переносить**: там любой ключ
     вне белого списка отменяет запуск;
   - пароли (`server.ssl.key-store-password`, пароли БД) в файл потолка не класть никогда: его показывают ИБ
     и хэшируют в отчёте. До пакета B10 они остаются в настройках службы, после B10 — в `config/awms-secrets.properties`.

   Подсказка: `check-surface.ps1` (Windows) или `sudo bash check-surface.sh` (Linux) печатает имена таких
   ключей — без значений: в аргументах процесса, в окружении службы и в файлах `application*.properties`
   рабочего каталога (раздел 9). Ограничение на Windows: переменные, заданные только в окне консоли
   (`set X=…`, `$env:X=…`), из которого запущен бэкенд, скрипт не видит — проверить в том окне.
4. Проверить четыре функции, которые выключены даже в `full`, — поэтому `full` не повторяет прежнее поведение
   буквально. Раньше они работали всегда; на нашем стенде ими не пользуются (проверено: каталогов нет,
   флаг импорта есть, фронтенд к ним не обращается):
   - `legacy` — раздача файлов из каталогов рабочего каталога `images/`, `proc_docs/`, `other_docs/`,
     `form_docs/`, `example_docs/`, `instr_docs/`, `posesh_rules_docs/`, `curator_rules_docs/`, `profiles/`
     по адресам `/images/**`, `/proc_docs/**` и т. д.;
   - `testsync` — приём `POST /api/sync/**` (без входа) и тестовые документы `/api/test-documents/**`;
   - `photoimport` — разовый импорт фото из `migration_photos/` при старте (если нет флага
     `uploads/nomenclature/.migration_photos_imported`);
   - `passwords` — предложение браузера сохранить пароль на странице входа (браузер перестанет его предлагать).

   Если на сервере перечисленные каталоги есть и их открывают (видно в журнале веб-сервера), кто-то шлёт
   `POST /api/sync` или есть `migration_photos/` без флага импорта — дописать в файл потолка строку
   `awms.features.legacy.enabled=true` (для `testsync` и `photoimport` — так же). Иначе ничего не делать.
5. Если служба запускается не из этого каталога (WinSW, NSSM, systemd с другим рабочим каталогом) — задать
   в службе переменную `AWMS_FEATURES_FILE` с полным путём к файлу.
6. Если сайт открывают с адреса, которого нет в строке `awms.cors.allowed-origins` файла, — дописать его через
   запятую точно как в адресной строке браузера: схема (`http://` или `https://`), домен, порт, если он
   не стандартный. Иначе вход с этого адреса не работает: служба отвечает 403 «Invalid CORS request».
   В строке те же адреса, что были зашиты в коде, плюс `http://127.0.0.1` и `http://109.69.22.155`.
   Живые обновления (WebSocket) после пакета B3 принимают только адреса из этой же строки.
7. Выкатить новую версию и перезапустить службу.
8. Проверить:
   - в логе при старте — таблица функций и пресет `full`; предупреждения про `isolated` быть не должно;
   - `curl -s https://dynamikaawms.ru/api/features/public` отвечает 200 и JSON;
   - на сайте на месте видео, карта, 3D, ГПП, панели главной; вход работает.
9. Если что-то не так — вернуть прежний jar. Старая версия этот файл просто не читает, его можно оставить.

Что поменяется позже, с будущими пакетами:
- **Срок хранения видео 30 дней** (строка `awms.video.retention-days=30`). После пакета B11 записи старше
  30 дней удаляются автоматически раз в сутки. Нужно дольше — поменять число до выкатки B11.
- **Секреты (пакет B10).** До его выкатки выполнить `init-secrets` (раздел 4), иначе служба не запустится.
- **Роли БД** (`roles.sql`, раздел 3) на стенде по желанию, у заказчика — обязательно.

---

## 2. Профиль функций (потолок)

| Пресет | Что работает |
|---|---|
| `isolated` — «Изолированный минимум» | только ядро учёта: справочники, номенклатура со штрихкодами, QR, SKU и поставками, станции плиткой и списком, пополнение, контроль остатков, вход, страница «Безопасность». Действует и когда файла нет |
| `standard` — «Стандарт» | ядро + карта и план цеха, 3D, ГПП, панели главной, выгрузки, загрузка файлов, черновики. Без видео, заказов и ТКП, WebSocket, интеграций, анонимного доступа, PWA |
| `full` — «Расширенный» | всё, как до выключателей, кроме `legacy`, `testsync`, `photoimport`, `passwords` (раздел 1, шаг 4): стенд и демонстрации. У заказчика — только по решению ИБ |

Как поменять: отредактировать `config/awms-features.properties` → перезапустить службу. Отступить от пресета
можно отдельной строкой, например `awms.features.model3d.enabled=false`. Все ключи —
`config/awms-features.properties.example`.

Правила файла:
- только ключи из справочника (белый список, раздел 1, шаг 3); опечатка или чужой ключ — служба
  не запустится и назовёт ключ в логе. Прочие настройки сервера (`server.tomcat.*`,
  `server.forward-headers-strategy`, `logging.*`, `spring.*`) сюда не класть: их место — аргументы службы
  или `application.properties` рядом с jar;
- ключи белого списка нельзя задавать переменными окружения, аргументами, `-D`, `SPRING_APPLICATION_JSON`
  и во внешних `application.properties` — служба не запустится. Точный список и имена переменных — раздел 1,
  шаг 3. Исключения: `AWMS_FEATURES_FILE` (путь к файлу) и `AWMS_FEATURES_PRESET` (только когда файла нет, для
  разработчиков; отчёт помечает такой профиль как непригодный для согласования);
- значения — латиницей и цифрами (файл читается в кодировке ISO-8859-1), комментарии — как угодно; без BOM;
- пароли и ключи сюда нельзя — они в `config/awms-secrets.properties`.

Сверка для безопасника:
- SHA-256 файла: `Get-FileHash config\awms-features.properties` (Windows) или `sha256sum config/awms-features.properties`;
- после пакета B6 — `java -jar dinamika_back.jar print-features --config=<путь к файлу>`: таблица функций
  и отпечаток профиля без запуска службы; тот же отпечаток — на странице «Безопасность».

Имя jar после сборки — `target/dinamika_back-<версия>.jar`; в примерах он назван `dinamika_back.jar`.

---

## 3. Роли базы данных (`db/roles.sql`) — обязательно у заказчика

Зачем: под суперпользователем `postgres` журналы профиля и доступа и переключатели функций можно молча
править и удалять. Скрипт заводит две роли:
- `awms_owner` — владелец схемы, под ним Flyway применяет миграции при старте;
- `awms_app` — рабочая роль приложения: обычные права на таблицы, журналы только дополняются,
  строки переключателей не удаляются, создавать таблицы нельзя.

Шаги (новая установка):
1. Под суперпользователем, в базе приложения:
   `psql -U postgres -d dinamika -v ON_ERROR_STOP=1 -f deploy/db/roles.sql`
2. Там же в `psql` задать пароли (вводятся с клавиатуры, в историю не попадают):
   `\password awms_owner` и `\password awms_app`.
3. Записать роли и пароли службе: после пакета B10 — в `config/awms-secrets.properties`
   (`spring.flyway.user/password`, `spring.datasource.username/password`); до B10 — переменными окружения
   службы (`service/nssm.txt`, конец файла).
4. Запустить службу: Flyway создаст таблицы под `awms_owner`.
5. **Запустить `roles.sql` ещё раз** — после первого запуска и после каждого обновления с новыми миграциями.
   Скрипт можно запускать сколько угодно раз.
6. Проверить (после версии с журналами, миграция V24):
   `psql -U awms_app -d dinamika -c "DELETE FROM sys_feature_log WHERE false"` → `permission denied`.
   Ничего не удаляется: `WHERE false`, а права проверяются раньше.

Существующая установка (таблицы уже принадлежат `postgres`): скрипт передаст их `awms_owner`. Делать
в окно обслуживания: снять резервную копию (`pg_dump`), остановить службу, выполнить шаги 1–3 и 5.

В `pg_hba.conf` для этих ролей — `scram-sha-256` и только с адреса сервера приложения.

---

## 4. Секреты установки (после пакета B10)

1. `java -jar dinamika_back.jar init-secrets` — создаёт `config/awms-secrets.properties` с ключами,
   своими у каждой установки, и печатает, какие права выставить.
2. Вписать в него роли и пароли БД из раздела 3.
3. Права: владелец — администраторы, читает только учётная запись службы (раздел 6).

До выкатки B10 служба этот файл не читает. После B10 без него или с ключами из репозитория она не запустится.

---

## 5. Служба Windows

- Шаблоны: `service/winsw.xml.example` (WinSW) или `service/nssm.txt` (NSSM).
- Рабочий каталог — каталог приложения; `AWMS_FEATURES_FILE` и `AWMS_SECRETS_FILE` — полные пути.
- Учётная запись — виртуальная `NT SERVICE\awms` (без пароля и без прав администратора):
  `sc.exe config awms obj= "NT SERVICE\awms"`.
- В аргументах и окружении службы — никаких ключей белого списка (раздел 1, шаг 3), отладчика (`-agentlib:jdwp`)
  и JMX. Пароли (БД, хранилища ключей HTTPS) — только до пакета B10 (`service/nssm.txt`, конец файла),
  после B10 — в файле секретов.
- Прочие настройки сервера (`server.tomcat.*`, `server.forward-headers-strategy`, `logging.*`) задаются здесь,
  в аргументах службы, или в `application.properties` рядом с jar — не в файле потолка.

Linux (systemd), то же самое: `WorkingDirectory=/opt/awms`,
`Environment=AWMS_FEATURES_FILE=/opt/awms/config/awms-features.properties`,
`Environment=AWMS_SECRETS_FILE=/opt/awms/config/awms-secrets.properties`, `User=awms`.

---

## 6. Права на каталоги

Windows, PowerShell от имени администратора, после установки службы (раздел 5): учётная запись
`NT SERVICE\awms` появляется вместе со службой. `*S-1-5-32-544` — Администраторы, `*S-1-5-18` — SYSTEM
(так команды работают в любой языковой версии Windows). `<группа ИБ>` заменить на группу безопасников.

```powershell
$app = 'C:\AWMS\app'; $svc = 'NT SERVICE\awms'
# Каталог приложения и jar: меняют администраторы, служба только читает. Наследование от диска снимается:
# в корне C:\ право изменения есть у всех вошедших пользователей
icacls "$app" /inheritance:r /grant:r "*S-1-5-32-544:(OI)(CI)F" "*S-1-5-18:(OI)(CI)F" "${svc}:(OI)(CI)RX"
# config (потолок и секреты): то же, явно — на случай, если права каталога приложения потом поменяют
icacls "$app\config" /inheritance:r /grant:r "*S-1-5-32-544:(OI)(CI)F" "*S-1-5-18:(OI)(CI)F" "${svc}:(OI)(CI)RX"
# logs: пишет служба, ИБ читает
New-Item -ItemType Directory -Force "$app\logs" | Out-Null
icacls "$app\logs" /inheritance:r /grant:r "*S-1-5-32-544:(OI)(CI)F" "*S-1-5-18:(OI)(CI)F" "${svc}:(OI)(CI)M" "<группа ИБ>:(OI)(CI)RX"
# данные: служба пишет
foreach ($d in 'uploads', 'data', 'video-archive') {
    New-Item -ItemType Directory -Force "$app\$d" | Out-Null
    icacls "$app\$d" /grant "${svc}:(OI)(CI)M"
}
```

Linux: `config/` — `root:awms`, `750`, файлы `640`; `logs/` — `awms:<группа ИБ>`, `750`;
`uploads/`, `data/`, `video-archive/` — владелец `awms`.

Изменения файла потолка отслеживаются аудитом ОС (Windows: аудит доступа к объектам на `config\`).

---

## 7. ffmpeg

- **Видео выключено** (`isolated`, `standard`): ffmpeg на сервер **не ставить**, а установленный — **удалить**
  (по умолчанию `C:\ffmpeg`). Проверка: `check-surface` пишет «нет» у файла по пути `video.ffmpeg-path`.
- **Видео включено**: ffmpeg и ffprobe положить по пути `video.ffmpeg-path` (умолчание `C:/ffmpeg/bin/ffmpeg.exe`;
  другой путь — строкой в файле потолка). Версия 7.1 или новее: так пароль камеры не попадает в аргументы
  процесса (пакет B11). Брать только официальные сборки и сверять контрольную сумму.

---

## 8. nginx

Шаблон — `nginx/awms.conf.example`:
- снаружи открыт только 443; службе в потолке задать `server.address=127.0.0.1`, тогда порт 8084 снаружи не виден;
- **в потолке обязательна строка `awms.cors.allowed-origins=https://<домен сайта>`** (в шаблонах `isolated`
  и `standard` она уже есть — заменить домен). Причина: TLS снимает nginx, и служба видит запрос как
  `http://<домен>:80` — заголовки `X-Forwarded-*` она пока не читает. Браузер же присылает
  `Origin: https://<домен>`, и для службы это чужой адрес. Без строки любой `POST`, в том числе вход, получает
  403 «Invalid CORS request» — не войдёт никто. Живые обновления (WebSocket) после пакета B3 берут этот же
  список. Проверка: `curl -s -o /dev/null -w "%{http_code}" -H "Origin: https://<домен>" https://<домен>/api/features/public`
  → 200 (на Windows вместо `/dev/null` — `NUL`); с другим `Origin` — 403;
- **адреса клиентов служба пока не видит.** Шаблон передаёт `X-Real-IP` и `X-Forwarded-For`, но служба их
  не читает: для неё все запросы приходят с `127.0.0.1` — и в сводке отказов ворот, и в будущих журналах
  доступа. До решения в пакетах B5/B6 адрес клиента смотреть в журнале nginx. Варианты решения: включить в
  службе `server.forward-headers-strategy=native` с `server.tomcat.remoteip.internal-proxies=127\.0\.0\.1`
  (доверять заголовкам только от nginx на этом же сервере) или читать `X-Forwarded-For` только от доверенного
  прокси. После этого служба увидит и `https`, и строка CORS для своего домена станет не обязательна;
- PWA выключена — вместо `/sw.js` отдаётся ликвидатор, `/manifest.json` и `/offline.html` → 404;
- `client_max_body_size` в шаблоне — `20m`: выгрузки и печать (функция `export`, есть в `standard` и `full`)
  присылают всю таблицу в теле запроса, и при `1m` крупная таблица получит 413 от nginx. Если выключены и
  `export`, и `uploads` (`isolated`) — хватит `1m`; с `uploads` — по регламенту заказчика, но не больше предела
  службы (`spring.servlet.multipart.max-request-size`, сейчас 1GB).

---

## 9. Проверка поверхности (`check-surface`)

```powershell
powershell -ExecutionPolicy Bypass -File deploy\check-surface.ps1 -ExpectOff video,orders,websocket,pwa
```
```bash
sudo bash deploy/check-surface.sh --expect-off video,orders,websocket,pwa
```

Показывает: файл потолка (путь, SHA-256, пресет, права), процесс службы (учётная запись, есть ли отладчик
и JMX), имена ключей белого списка у службы, порты и соединения, дочерние процессы (только имена), ffmpeg,
ответы закрытых путей на `GET` и `HEAD` (404 — закрыто). В `-ExpectOff` — выключенные функции из отчёта
«Профиль безопасности»: скрипт отметит расхождения. Аргументы процессов, значения переменных и файлов
не выводятся — в них бывают пароли. Запускать от имени администратора (root), иначе часть данных не видна.
Ничего не меняет.

Где ищутся ключи белого списка (раздел 1, шаг 3):
- Linux: аргументы и окружение процесса службы, `application*.properties` в его рабочем каталоге и `config/`;
- Windows: аргументы процесса; окружение — из параметров службы (значение `Environment` в реестре, у NSSM —
  `AppEnvironment` и `AppEnvironmentExtra`, у WinSW — строки `<env>` в его xml), переменные компьютера и
  пользователя, от имени которого запущен скрипт; `application*.properties` в рабочем каталоге службы
  (у NSSM — `AppDirectory`, у WinSW — `<workingdirectory>`, иначе — каталог над `config\` файла потолка).
  Окружение работающего процесса Windows штатными средствами не показывает, поэтому переменные, заданные
  только в окне консоли, из которого запущен бэкенд, скрипт не видит. Службу, имя которой не нашлось само,
  указать параметром `-ServiceName`.

---

## 10. Разработчикам и стенду

- **Наш стенд** (бэкенд из `C:\AWMS\Back\dinamika_back` через `mvnw spring-boot:run`): файл
  `config/awms-features.properties` — копия `deploy/features/full.properties`, лежит в корне бэкенда,
  в git не попадает. Пересоздать: `copy deploy\features\full.properties config\awms-features.properties`.
- **Без файла**: переменная `AWMS_FEATURES_PRESET=full` — работает, только когда файла нет.
- **Тесты** (`./mvnw test`): пути к тестовому профилю и секретам задаёт `pom.xml` (пакет B1),
  файл разработчика в `config/` тесты не подхватывают.
- **Тесты из IDE** — те же два пути вручную (рабочий каталог — корень бэкенда):
  - IntelliJ IDEA: Run → Edit Configurations → Edit configuration templates → JUnit → VM options:
    `-DAWMS_FEATURES_FILE=src/test/resources/features/test-full.properties -DAWMS_SECRETS_FILE=src/test/resources/features/test-secrets.properties`
  - VS Code: в `.vscode/settings.json` бэкенда
    `"java.test.config": { "vmArgs": ["-DAWMS_FEATURES_FILE=src/test/resources/features/test-full.properties", "-DAWMS_SECRETS_FILE=src/test/resources/features/test-secrets.properties"] }`

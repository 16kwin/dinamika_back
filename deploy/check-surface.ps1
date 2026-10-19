<#
.SYNOPSIS
    Проверка поверхности AWMS «ДИНАМИКА» на сервере Windows: профиль функций, порты и соединения службы,
    дочерние процессы, ffmpeg и ответы закрытых путей на GET и HEAD.

.DESCRIPTION
    Скрипт только читает: ничего не меняет, службу не перезапускает, в систему не входит (пробы анонимные).
    Аргументы командной строки процессов, значения переменных окружения и файлов настроек НЕ выводятся: в них
    бывают пароли. Печатаются только имена процессов, PID, порты, имена ключей и переменных, признаки «да/нет».
    Ключи белого списка потолка ищутся в аргументах процесса, в окружении службы (реестр: Environment, у NSSM —
    AppEnvironment и AppEnvironmentExtra; у WinSW — <env> в его xml), в переменных компьютера и пользователя
    и в application*.properties рабочего каталога. Переменные окна консоли, из которого запущен бэкенд, не видны.

    Запуск (PowerShell 5.1 или 7; от имени администратора — иначе чужие процессы и права файлов могут быть не видны):
        powershell -ExecutionPolicy Bypass -File check-surface.ps1
        powershell -ExecutionPolicy Bypass -File check-surface.ps1 -ExpectOff video,orders,websocket,pwa
        powershell -ExecutionPolicy Bypass -File check-surface.ps1 -BaseUrl https://awms.zavod.local -Insecure
    Сохранить результат в файл: ... -File check-surface.ps1 > surface.txt

.PARAMETER Port
    Порт службы (server.port), по нему ищется процесс java. По умолчанию 8084.
.PARAMETER ProcessId
    PID службы, если поиск по порту не подходит.
.PARAMETER ServiceName
    Имя службы Windows (например awms), если оно не нашлось само по процессу java и его родителю (WinSW, NSSM).
    По нему читаются окружение и рабочий каталог службы — только имена переменных, без значений.
.PARAMETER BaseUrl
    Адрес для проб. По умолчанию http://localhost:<Port> — сама служба, без nginx.
.PARAMETER ConfigFile
    Файл потолка. По умолчанию $env:AWMS_FEATURES_FILE, иначе .\config\awms-features.properties.
.PARAMETER FfmpegPath
    Путь к ffmpeg. По умолчанию video.ffmpeg-path из файла потолка, иначе умолчание jar C:/ffmpeg/bin/ffmpeg.exe.
.PARAMETER ExpectOff
    Ключи функций, выключенных в профиле (из отчёта «Профиль безопасности» или print-features), через запятую.
    Для них и их дочерних функций ждём 404, для остальных — не 404. Без параметра коды просто печатаются.
.PARAMETER Insecure
    Не проверять сертификат HTTPS (самоподписанный на стенде).
#>
param(
    [int]$Port = 8084,
    [int]$ProcessId = 0,
    [string]$ServiceName = '',
    [string]$BaseUrl = '',
    [string]$ConfigFile = '',
    [string]$FfmpegPath = '',
    [string[]]$ExpectOff = @(),
    [switch]$Insecure
)

$ErrorActionPreference = 'Continue'
if (-not $BaseUrl) { $BaseUrl = "http://localhost:$Port" }
$BaseUrl = $BaseUrl.TrimEnd('/')
# -ExpectOff video,orders из cmd приходит одной строкой — делим сами
$expectOffKeys = @($ExpectOff | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })

function Write-Section([string]$title) {
    Write-Output ''
    Write-Output ('=== ' + $title + ' ' + ('=' * [Math]::Max(3, 100 - $title.Length)))
}

# Значение ключа из .properties (без разбора продолжений строк — в потолке их нет)
function Get-PropertyValue([string]$path, [string]$key) {
    if (-not $path -or -not (Test-Path -LiteralPath $path -PathType Leaf)) { return $null }
    foreach ($line in [IO.File]::ReadAllLines($path, [Text.Encoding]::GetEncoding('ISO-8859-1'))) {
        $t = $line.Trim()
        if ($t -match ('^' + [regex]::Escape($key) + '\s*[=:]\s*(.*)$')) { return $Matches[1].Trim() }
    }
    return $null
}

# Белый список потолка (раздел 4.5 проекта): эти ключи служба принимает только из файла потолка и умолчаний jar,
# в аргументах, окружении и внешних application.properties они отменяют запуск. Прочие server.*, spring.*,
# logging.* разрешены. Имена сравниваются, как в Spring: без учёта регистра, дефисов и подчёркиваний
$whitelistUniform = @('server.address', 'server.port', 'server.ssl.enabled', 'server.ssl.keystore', 'server.ssl.protocol',
                      'sync.service.url', 'video.ffmpegpath')
# Те же ключи в виде переменных окружения (обе формы Spring: VIDEO_FFMPEGPATH и VIDEO_FFMPEG_PATH)
$whitelistEnv = @('SERVER_ADDRESS', 'SERVER_PORT', 'SERVER_SSL_ENABLED', 'SERVER_SSL_KEYSTORE', 'SERVER_SSL_KEY_STORE',
                  'SERVER_SSL_PROTOCOL', 'SYNC_SERVICE_URL', 'VIDEO_FFMPEGPATH', 'VIDEO_FFMPEG_PATH')
# Пути к файлам потолка и секретов — допустимы
$allowedEnv = @('AWMS_FEATURES_FILE', 'AWMS_SECRETS_FILE')
$listFound = 0

# Вид ключа свойства: 'list' — белый список (служба не запустится), 'extra' — дополнительный источник настроек
function Get-KeyKind([string]$key) {
    $u = $key.ToLowerInvariant() -replace '[-_]', ''
    if ($whitelistUniform -contains $u -or $u.StartsWith('awms.')) { return 'list' }
    if ($u.StartsWith('spring.config.') -or $u -eq 'spring.application.json') { return 'extra' }
    return ''
}

# То же для имени переменной окружения
function Get-EnvKind([string]$name) {
    if ($name.Contains('.')) { return Get-KeyKind $name }
    $n = $name.ToUpperInvariant()
    if ($allowedEnv -contains $n) { return '' }
    if ($whitelistEnv -contains $n -or $n.StartsWith('AWMS_')) { return 'list' }
    if ($n -eq 'SPRING_APPLICATION_JSON' -or $n.StartsWith('SPRING_CONFIG_')) { return 'extra' }
    return ''
}

# Печать найденных имён (значения не печатаются никогда)
function Write-KeyFindings([string]$where, [string[]]$listNames, [string[]]$extraNames) {
    if ($listNames) {
        Write-Output ("  ВНИМАНИЕ: {0} — ключи белого списка (значения не показаны): {1}" -f $where, (($listNames | Sort-Object -Unique) -join ', '))
    }
    if ($extraNames) {
        Write-Output ("  {0} — дополнительные источники настроек: {1}. Проверить, нет ли в них ключей белого списка" -f $where, (($extraNames | Sort-Object -Unique) -join ', '))
    }
}

# Имена ключей .properties (без значений; строки-продолжения после «\» пропускаются)
function Get-PropertyNames([string]$path) {
    $names = @()
    $continued = $false
    foreach ($line in [IO.File]::ReadAllLines($path, [Text.Encoding]::GetEncoding('ISO-8859-1'))) {
        $endsWithSlash = $line -match '(^|[^\\])(\\\\)*\\$'
        if ($continued) { $continued = $endsWithSlash; continue }
        if ($line -match '^\s*([#!]|$)') { continue }
        if ($line -match '^\s*([^\s=:]+)') { $names += $Matches[1] }
        $continued = $endsWithSlash
    }
    return $names
}

function Show-FileAcl([string]$label, [string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Write-Output ("{0}: нет файла ({1})" -f $label, $path)
        return
    }
    $item = Get-Item -LiteralPath $path
    Write-Output ("{0}: {1}" -f $label, $item.FullName)
    Write-Output ("  изменён: {0:yyyy-MM-dd HH:mm:ss}" -f $item.LastWriteTime)
    try {
        $acl = Get-Acl -LiteralPath $path
        Write-Output ("  владелец: {0}" -f $acl.Owner)
        foreach ($rule in $acl.Access) {
            Write-Output ("  доступ: {0} — {1} {2}" -f $rule.IdentityReference, $rule.AccessControlType, $rule.FileSystemRights)
        }
    } catch {
        Write-Output '  права: не удалось прочитать (нужен запуск от имени администратора)'
    }
}

Write-Output ('Проверка поверхности AWMS «ДИНАМИКА» — {0:yyyy-MM-dd HH:mm:ss}, компьютер {1}' -f (Get-Date), $env:COMPUTERNAME)

# --------------------------------------------------------------------------------------------------------------
Write-Section 'Профиль функций (файл потолка) и файл секретов'
if (-not $ConfigFile) {
    $ConfigFile = if ($env:AWMS_FEATURES_FILE) { $env:AWMS_FEATURES_FILE } else { '.\config\awms-features.properties' }
}
$configPath = $ConfigFile
if (Test-Path -LiteralPath $configPath -PathType Leaf) {
    $configPath = (Resolve-Path -LiteralPath $configPath).Path
    Show-FileAcl 'Файл потолка' $configPath
    Write-Output ("  SHA-256: {0}" -f (Get-FileHash -LiteralPath $configPath -Algorithm SHA256).Hash)
    $preset = Get-PropertyValue $configPath 'awms.features.preset'
    Write-Output ("  пресет: {0}" -f $(if ($preset) { $preset } else { 'не задан — служба работает в isolated' }))
    $bytes = [IO.File]::ReadAllBytes($configPath)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        Write-Output '  ВНИМАНИЕ: файл сохранён с BOM — первая строка прочитается неверно. Пересохранить как UTF-8 без BOM'
    }
} else {
    Write-Output ("Файл потолка: нет ({0}). Служба работает в «Изолированном минимуме» (isolated)" -f $configPath)
}
$secretsPath = if ($env:AWMS_SECRETS_FILE) { $env:AWMS_SECRETS_FILE } else { Join-Path (Split-Path -Parent $configPath) 'awms-secrets.properties' }
Show-FileAcl 'Файл секретов (содержимое не читается)' $secretsPath

# --------------------------------------------------------------------------------------------------------------
Write-Section 'Процесс службы'
$svc = $null
if ($ProcessId -gt 0) {
    $svc = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction SilentlyContinue
} else {
    $listen = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listen) { $svc = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $listen.OwningProcess) -ErrorAction SilentlyContinue }
}
$familyPids = @()
if (-not $svc) {
    Write-Output ("Процесс не найден (порт {0}, PID {1}). Укажите -Port или -ProcessId. Пробы ниже всё равно выполняются" -f $Port, $ProcessId)
} else {
    $svcPid = [int]$svc.ProcessId
    Write-Output ("{0}, PID {1}, запущен {2:yyyy-MM-dd HH:mm:ss}" -f $svc.Name, $svcPid, $svc.CreationDate)
    Write-Output ("  исполняемый файл: {0}" -f $svc.ExecutablePath)
    $owner = Invoke-CimMethod -InputObject $svc -MethodName GetOwner -ErrorAction SilentlyContinue
    if ($owner -and $owner.User) { Write-Output ("  учётная запись: {0}\{1}" -f $owner.Domain, $owner.User) }
    # Аргументы не печатаются: только признаки и имена ключей
    $cl = [string]$svc.CommandLine
    if ($cl) {
        $debug = $cl -match '-agentlib:jdwp|-Xrunjdwp|-Xdebug'
        $jmx = $cl -match '-Dcom\.sun\.management\.jmxremote'
        Write-Output ("  отладчик JDWP в аргументах: {0}" -f $(if ($debug) { 'ДА' } else { 'нет' }))
        Write-Output ("  удалённый JMX в аргументах: {0}" -f $(if ($jmx) { 'ДА' } else { 'нет' }))
        $argKeys = @([regex]::Matches($cl, '(?:^|\s)"?(?:--|-D)([A-Za-z][A-Za-z0-9_.\-\[\]]*)=') | ForEach-Object { $_.Groups[1].Value })
        $argList = @($argKeys | Where-Object { (Get-KeyKind $_) -eq 'list' })
        $argExtra = @($argKeys | Where-Object { (Get-KeyKind $_) -eq 'extra' })
        if ($argList -or $argExtra) {
            Write-KeyFindings 'в аргументах' $argList $argExtra
            $listFound += $argList.Count
        } else {
            Write-Output '  ключей белого списка в аргументах нет'
        }
    } else {
        Write-Output '  аргументы недоступны (нужен запуск от имени администратора)'
    }

    # Дерево потомков: имена и PID
    $all = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue)
    $queue = New-Object System.Collections.Queue
    $queue.Enqueue($svcPid)
    $children = @()
    while ($queue.Count -gt 0) {
        $parentPid = $queue.Dequeue()
        foreach ($p in $all | Where-Object { $_.ParentProcessId -eq $parentPid -and $_.ProcessId -ne $parentPid }) {
            if ($children.ProcessId -notcontains $p.ProcessId) { $children += $p; $queue.Enqueue([int]$p.ProcessId) }
        }
    }
    $familyPids = @($svcPid) + @($children | ForEach-Object { [int]$_.ProcessId })

    Write-Section 'Входящие: что слушает служба'
    $tcp = @(Get-NetTCPConnection -OwningProcess $svcPid -State Listen -ErrorAction SilentlyContinue)
    $udp = @(Get-NetUDPEndpoint -OwningProcess $svcPid -ErrorAction SilentlyContinue)
    if (-not $tcp -and -not $udp) { Write-Output 'нет' }
    $tcp | Sort-Object LocalPort, LocalAddress | ForEach-Object { Write-Output ("  TCP {0}:{1}" -f $_.LocalAddress, $_.LocalPort) }
    $udp | Sort-Object LocalPort, LocalAddress | ForEach-Object { Write-Output ("  UDP {0}:{1}" -f $_.LocalAddress, $_.LocalPort) }

    Write-Section 'Установленные соединения службы и её дочерних процессов'
    $est = @(Get-NetTCPConnection -State Established -ErrorAction SilentlyContinue | Where-Object { $familyPids -contains $_.OwningProcess })
    if (-not $est) { Write-Output 'нет' }
    foreach ($c in $est | Sort-Object RemoteAddress, RemotePort) {
        $name = if ($c.OwningProcess -eq $svcPid) { $svc.Name } else { ($children | Where-Object ProcessId -eq $c.OwningProcess | Select-Object -First 1).Name }
        Write-Output ("  {0} (PID {1}): {2}:{3} -> {4}:{5}" -f $name, $c.OwningProcess, $c.LocalAddress, $c.LocalPort, $c.RemoteAddress, $c.RemotePort)
    }
    $udpChildren = @(Get-NetUDPEndpoint -ErrorAction SilentlyContinue | Where-Object { $children.ProcessId -contains $_.OwningProcess })
    foreach ($u in $udpChildren) { Write-Output ("  UDP дочернего процесса PID {0}: {1}:{2} (RTP от камер)" -f $u.OwningProcess, $u.LocalAddress, $u.LocalPort) }

    Write-Section 'Дочерние процессы службы (только имена)'
    if (-not $children) { Write-Output 'нет' }
    foreach ($p in $children) { Write-Output ("  {0}, PID {1}" -f $p.Name, $p.ProcessId) }
    # Пароль в адресе камеры (rtsp://логин:пароль@...) — только счётчик, без вывода
    $withPassword = @($children | Where-Object { ([string]$_.CommandLine) -match '://[^/\s:@"]+:[^@\s/"]+@' }).Count
    if ($children) {
        Write-Output ("  процессов с паролем в аргументах (адрес вида rtsp://логин:пароль@...): {0}" -f $withPassword)
    }
}

# --------------------------------------------------------------------------------------------------------------
Write-Section 'Ключи белого списка вне файла потолка: окружение службы и внешние файлы настроек'
# Окружение чужого процесса Windows не отдаёт, поэтому читаются настройки службы (реестр, xml WinSW)
# и переменные компьютера и пользователя. Печатаются только имена
$service = $null
if ($ServiceName) {
    $service = Get-CimInstance Win32_Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -eq $ServiceName } | Select-Object -First 1
    if (-not $service) { Write-Output ("Служба «{0}» не найдена" -f $ServiceName) }
} elseif ($svc) {
    # Служба — сам процесс java или его родитель (WinSW, NSSM)
    foreach ($candidate in @([int]$svc.ProcessId, [int]$svc.ParentProcessId)) {
        if ($candidate -gt 0 -and -not $service) {
            $service = Get-CimInstance Win32_Service -Filter ("ProcessId=" + $candidate) -ErrorAction SilentlyContinue | Select-Object -First 1
        }
    }
}
$envSets = @()
$workDir = ''
if ($service) {
    Write-Output ("Служба: {0} ({1})" -f $service.Name, $service.DisplayName)
    $regPath = 'HKLM:\SYSTEM\CurrentControlSet\Services\' + $service.Name
    $root = Get-ItemProperty -LiteralPath $regPath -ErrorAction SilentlyContinue
    if ($root -and $root.Environment) {
        $envSets += [pscustomobject]@{ Label = 'окружение службы (реестр, Environment)'; Entries = @($root.Environment) }
    }
    $par = Get-ItemProperty -LiteralPath ($regPath + '\Parameters') -ErrorAction SilentlyContinue
    if ($par) {
        foreach ($v in 'AppEnvironment', 'AppEnvironmentExtra') {
            if ($par.$v) { $envSets += [pscustomobject]@{ Label = "окружение службы (NSSM, $v)"; Entries = @($par.$v) } }
        }
        if ($par.AppDirectory) { $workDir = [string]$par.AppDirectory }
    }
    # WinSW: настройки — в xml рядом с exe службы, с тем же именем
    $exe = ([string]$service.PathName).Trim()
    if ($exe.StartsWith('"') -and $exe.IndexOf('"', 1) -gt 0) { $exe = $exe.Substring(1, $exe.IndexOf('"', 1) - 1) } else { $exe = ($exe -split '\s+')[0] }
    $xmlPath = if ($exe) { [IO.Path]::ChangeExtension($exe, '.xml') } else { '' }
    if ($xmlPath -and (Test-Path -LiteralPath $xmlPath -PathType Leaf)) {
        try {
            [xml]$winsw = Get-Content -LiteralPath $xmlPath -Raw -Encoding UTF8
            $envSets += [pscustomobject]@{ Label = ('окружение службы (WinSW, <env> в ' + $xmlPath + ')')
                                           Entries = @($winsw.service.env | ForEach-Object { [string]$_.name }) }
            $wd = [string]$winsw.service.workingdirectory
            if ($wd) {
                $workDir = [Environment]::ExpandEnvironmentVariables(($wd -replace '%BASE%', (Split-Path -Parent $xmlPath)))
            }
        } catch {
            Write-Output ("  xml WinSW не прочитан: {0}" -f $xmlPath)
        }
    }
    if (-not $envSets) { Write-Output '  окружение службы: не задано (ни Environment в реестре, ни NSSM, ни <env> WinSW)' }
} elseif (-not $ServiceName) {
    Write-Output 'Служба Windows для процесса не найдена (бэкенд запущен не службой). Имя службы — параметром -ServiceName'
}
$envSets += [pscustomobject]@{ Label = 'переменные компьютера'
                               Entries = @([Environment]::GetEnvironmentVariables('Machine').Keys) }
$envSets += [pscustomobject]@{ Label = ('переменные пользователя {0} (действуют, если бэкенд запущен из его сеанса, а не службой)' -f $env:USERNAME)
                               Entries = @([Environment]::GetEnvironmentVariables('User').Keys) }
foreach ($set in $envSets) {
    # Записи вида ИМЯ=значение: берётся только имя
    $names = @($set.Entries | ForEach-Object { ([string]$_ -split '=', 2)[0].Trim().TrimStart('+') } | Where-Object { $_ })
    $l = @($names | Where-Object { (Get-EnvKind $_) -eq 'list' })
    $e = @($names | Where-Object { (Get-EnvKind $_) -eq 'extra' })
    if ($l -or $e) {
        Write-KeyFindings $set.Label $l $e
        $listFound += $l.Count
    } else {
        Write-Output ("  {0}: нет" -f $set.Label)
    }
}

# Файлы, которые Spring Boot читает сам: application*.properties|yml в рабочем каталоге, config\ и config\*\
if (-not $workDir) {
    $cfgDir = Split-Path -Parent $configPath
    $workDir = if ($cfgDir -and (Split-Path -Leaf $cfgDir) -eq 'config') { Split-Path -Parent $cfgDir } else { '.' }
    if (-not $workDir) { $workDir = '.' }
}
if (-not [IO.Path]::IsPathRooted($workDir)) { $workDir = Join-Path (Get-Location).Path $workDir }
$workDir = [IO.Path]::GetFullPath($workDir)
Write-Output ("Рабочий каталог: {0}" -f $workDir)
$cfgDirs = @($workDir, (Join-Path $workDir 'config')) +
           @(Get-ChildItem -LiteralPath (Join-Path $workDir 'config') -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
$cfgFiles = @($cfgDirs | ForEach-Object { Get-ChildItem -LiteralPath $_ -File -ErrorAction SilentlyContinue } |
              Where-Object { $_.Name -match '^application.*\.(properties|ya?ml)$' })
if (-not $cfgFiles) { Write-Output '  файлов application*.properties и application*.yml нет' }
foreach ($f in $cfgFiles) {
    if ($f.Extension -eq '.properties') {
        $names = @(Get-PropertyNames $f.FullName)
        $l = @($names | Where-Object { (Get-KeyKind $_) -eq 'list' })
        $e = @($names | Where-Object { (Get-KeyKind $_) -eq 'extra' })
        if ($l -or $e) {
            Write-KeyFindings $f.FullName $l $e
            $listFound += $l.Count
        } else {
            Write-Output ("  {0}: ключей белого списка нет" -f $f.FullName)
        }
    } else {
        Write-Output ("  {0}: есть — YAML скрипт не разбирает, проверить вручную" -f $f.FullName)
    }
}
Write-Output ''
if ($listFound -gt 0) {
    Write-Output '  Ключи белого списка перенести в файл потолка (секреты AWMS_* — в файл секретов) и убрать отсюда:'
    Write-Output '  с новой версией служба с ними не запустится. Прочие server.*, spring.*, logging.* оставить на месте'
} else {
    Write-Output '  Ключей белого списка вне файла потолка не найдено'
}
Write-Output '  Не видны скрипту: переменные, заданные только в окне консоли, из которого запущен бэкенд (INSTALL.md, раздел 9)'

# --------------------------------------------------------------------------------------------------------------
Write-Section 'ffmpeg'
if (-not $FfmpegPath) {
    $FfmpegPath = Get-PropertyValue $configPath 'video.ffmpeg-path'
    if (-not $FfmpegPath) { $FfmpegPath = 'C:/ffmpeg/bin/ffmpeg.exe' }
}
$ffDir = Split-Path -Parent $FfmpegPath
foreach ($f in @($FfmpegPath, (Join-Path $ffDir 'ffprobe.exe'))) {
    $exists = Test-Path -LiteralPath $f -PathType Leaf
    Write-Output ("  {0}: {1}" -f $f, $(if ($exists) { 'ЕСТЬ' } else { 'нет' }))
}
foreach ($cmd in @('ffmpeg', 'ffprobe')) {
    $found = Get-Command $cmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    Write-Output ("  {0} в PATH: {1}" -f $cmd, $(if ($found) { $found.Source } else { 'нет' }))
}
$running = @(Get-Process -Name ffmpeg, ffprobe -ErrorAction SilentlyContinue)
Write-Output ("  запущено процессов ffmpeg/ffprobe на сервере: {0}{1}" -f $running.Count,
        $(if ($running) { ' (PID ' + (($running | ForEach-Object Id) -join ', ') + ')' } else { '' }))
$rtsp = @(Get-NetTCPConnection -RemotePort 554 -State Established -ErrorAction SilentlyContinue)
Write-Output ("  соединений RTSP (TCP 554) с сервера: {0}" -f $rtsp.Count)
Write-Output '  При выключенном видео ffmpeg на сервере быть не должно: не устанавливать, установленный удалить (INSTALL.md)'

# --------------------------------------------------------------------------------------------------------------
Write-Section ("Пробы закрытых путей: анонимно, {0}" -f $BaseUrl)
Write-Output '404 — путь закрыт воротами (функция выключена). 401/403/405/200 — маршрут есть (функция включена;'
Write-Output 'без входа запрос дальше проверки прав не проходит). Для сравнения в конце — ядро и несуществующий путь.'

try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch { }
if ($Insecure) {
    if (-not ('AwmsTrustAllCerts' -as [type])) {
        Add-Type -TypeDefinition @'
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class AwmsTrustAllCerts : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint sp, X509Certificate cert, WebRequest req, int problem) { return true; }
}
'@
    }
    [Net.ServicePointManager]::CertificatePolicy = New-Object AwmsTrustAllCerts
}

function Invoke-Probe([string]$method, [string]$path, [bool]$multipart) {
    try {
        $req = [Net.HttpWebRequest]::Create($BaseUrl + $path)
        $req.Method = $method
        $req.AllowAutoRedirect = $false
        $req.Timeout = 10000
        $req.ReadWriteTimeout = 10000
        $req.UserAgent = 'awms-check-surface'
        if ($multipart) {
            $boundary = 'awmsprobe' + [Guid]::NewGuid().ToString('N')
            $req.ContentType = 'multipart/form-data; boundary=' + $boundary
            $body = [Text.Encoding]::ASCII.GetBytes("--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"probe.txt`"`r`nContent-Type: text/plain`r`n`r`nprobe`r`n--$boundary--`r`n")
            $req.ContentLength = $body.Length
            $stream = $req.GetRequestStream(); $stream.Write($body, 0, $body.Length); $stream.Close()
        } elseif ($method -eq 'POST') {
            $req.ContentType = 'application/json'
            $req.ContentLength = 0
        }
        $resp = $req.GetResponse()
        $code = [int]$resp.StatusCode
        $resp.Close()
        return $code
    } catch [Net.WebException] {
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
            $_.Exception.Response.Close()
            return $code
        }
        return ('нет ответа: ' + $_.Exception.Status)
    } catch {
        return ('ошибка: ' + $_.Exception.Message)
    }
}

# Функция выключена, если выключена она сама, её родитель или то, от чего она зависит
$requires = @{ 'dashboards.graph' = @('dashboards.audit') }
function Test-ExpectedOff([string]$key) {
    $k = $key
    while ($k) {
        if ($expectOffKeys -contains $k) { return $true }
        if ($requires.ContainsKey($k)) { foreach ($r in $requires[$k]) { if (Test-ExpectedOff $r) { return $true } } }
        $i = $k.LastIndexOf('.')
        $k = if ($i -gt 0) { $k.Substring(0, $i) } else { '' }
    }
    return $false
}

# Пути — те же, что probe() в реестре функций (Feature.java) и раздел «Проверка из браузера» отчёта.
# Живой поток (/live) не трогаем: даже анонимная проба не должна приближаться к запуску ffmpeg
$probes = @(
    @{ Key = 'video';             Methods = 'GET', 'HEAD'; Path = '/api/video/status' },
    @{ Key = 'video.live';        Methods = 'GET', 'HEAD'; Path = '/api/video/cameras/1/snapshot' },
    @{ Key = 'video.archive';     Methods = 'GET', 'HEAD'; Path = '/api/video/records' },
    @{ Key = 'video.download';    Methods = 'GET', 'HEAD'; Path = '/api/video/files/1/download' },
    @{ Key = 'map.plan';          Methods = 'GET', 'HEAD'; Path = '/api/station-plans/workshops/0' },
    @{ Key = 'model3d';           Methods = 'GET', 'HEAD'; Path = '/api/nomenclature/0/models' },
    @{ Key = 'gpp';               Methods = 'GET', 'HEAD'; Path = '/api/gpp' },
    @{ Key = 'dashboards';        Methods = 'GET', 'HEAD'; Path = '/api/dashboard/control-events' },
    @{ Key = 'dashboards.audit';  Methods = 'GET', 'HEAD'; Path = '/api/dashboard/audit' },
    @{ Key = 'dashboards.screen'; Methods = 'GET', 'HEAD'; Path = '/api/dashboard/day-events' },
    @{ Key = 'dashboards.graph';  Methods = 'GET', 'HEAD'; Path = '/api/dashboard/purchase-graph' },
    @{ Key = 'orders';            Methods = 'GET', 'HEAD'; Path = '/api/orders/active' },
    @{ Key = 'websocket';         Methods = 'GET', 'HEAD'; Path = '/ws-stations/info' },
    @{ Key = 'testsync';          Methods = 'GET', 'HEAD'; Path = '/api/test-documents/drafts' },
    @{ Key = 'export';            Methods = @('POST');     Path = '/api/templates/export-pdf' },
    @{ Key = 'uploads';           Methods = @('POST+файл'); Path = '/api/nomenclature/0/images' }
)

$mismatch = 0
Write-Output ''
Write-Output ('{0,-18} {1,-10} {2,-34} {3,-6} {4}' -f 'функция', 'метод', 'путь', 'код', 'вывод')
foreach ($p in $probes) {
    foreach ($m in $p.Methods) {
        $multipart = $m -eq 'POST+файл'
        $code = Invoke-Probe $(if ($multipart) { 'POST' } else { $m }) $p.Path $multipart
        $closed = ($code -is [int]) -and $code -eq 404
        $verdict = if (-not ($code -is [int])) { '?' } elseif ($closed) { 'закрыто' } else { 'маршрут есть' }
        if ($expectOffKeys.Count -gt 0 -and ($code -is [int])) {
            $wantClosed = Test-ExpectedOff $p.Key
            if ($wantClosed -eq $closed) { $verdict += ' — как ожидалось' } else { $verdict += ' — НЕ СОВПАДАЕТ с профилем'; $mismatch++ }
        }
        Write-Output ('{0,-18} {1,-10} {2,-34} {3,-6} {4}' -f $p.Key, $m, $p.Path, $code, $verdict)
    }
}
Write-Output ''
$core = Invoke-Probe 'GET' '/api/features/public' $false
Write-Output ('{0,-18} {1,-10} {2,-34} {3,-6} {4}' -f '(ядро)', 'GET', '/api/features/public', $core, 'ожидается 200')
$none = Invoke-Probe 'GET' '/api/awms-check-surface-no-such-path' $false
Write-Output ('{0,-18} {1,-10} {2,-34} {3,-6} {4}' -f '(нет такого)', 'GET', '/api/awms-check-surface-no-such-path', $none, 'аноним получает 403, не 404')
if ($expectOffKeys.Count -gt 0) {
    Write-Output ''
    Write-Output $(if ($mismatch -eq 0) { 'Итог: все пробы совпадают с профилем' } else { "Итог: расхождений с профилем — $mismatch" })
}
Write-Output ''
Write-Output ("Повторить вручную: curl.exe -i {0}/api/video/status   и   curl.exe -I {0}/api/video/files/1/download" -f $BaseUrl)

<#
.SYNOPSIS
    Gate de validacion de las PRUEBAS DE INTEGRACION (entorno asistido por IA).

.DESCRIPTION
    Un unico comando que levanta el ambiente, ejecuta la coleccion de Postman contra
    el microservicio real y devuelve un exit code inequivoco. Es el equivalente de
    tools/validate-tests.ps1, pero un nivel mas arriba: alli se prueban unidades
    aisladas, aqui la API completa contra una base de datos real.

        1. comprueba los prerrequisitos del modo elegido
        2. levanta el ambiente de pruebas (base de datos EFIMERA)
        3. espera a que /actuator/health responda UP
        4. ejecuta la coleccion con Newman y parsea el resultado
        5. baja el ambiente e imprime el resumen

    Ninguna prueba de integracion generada por IA se da por buena sin que este
    script devuelva 0.

.PARAMETER Mode
    docker : (por defecto) docker-compose.integration.yml -> PostgreSQL real efimero.
    local  : compila el jar y lo arranca con el perfil 'it' (H2 en memoria). Rapido,
             sin Docker. Respeta SPRING_DATASOURCE_* si estan definidas, que es como
             lo usa el CI contra su servicio de PostgreSQL.
    none   : la aplicacion ya esta levantada; solo ejecuta la coleccion.

.PARAMETER KeepUp
    No baja el ambiente al terminar (util para depurar un fallo con Postman abierto).

.PARAMETER Twice
    Ejecuta la coleccion DOS VECES seguidas contra la misma base de datos y exige que las
    dos vueltas pasen. Es la comprobacion de repetibilidad: una coleccion que solo pasa
    con la base recien creada no es una prueba, es una demo.

.PARAMETER Report
    Ademas del JSON, genera el reporte HTML navegable (newman-reporter-htmlextra) en
    docs/reportes/integracion-postman/index.html, que es el que se entrega. Fuerza el uso
    de npx para cargar el reporter sin instalarlo globalmente.

.PARAMETER BaseUrl
    URL del microservicio. Por defecto http://localhost:8082.

.EXAMPLE
    .\tools\validate-integration.ps1
    .\tools\validate-integration.ps1 -Mode local
    .\tools\validate-integration.ps1 -KeepUp
    .\tools\validate-integration.ps1 -Mode local -Twice -Report

.OUTPUTS
    exit 0 = OK | 1 = aserciones fallidas | 2 = el ambiente no levanto
    exit 3 = falta un prerrequisito | 4 = timeout esperando /actuator/health
#>
[CmdletBinding()]
param(
    [ValidateSet("docker", "local", "none")]
    [string] $Mode = "docker",
    [switch] $KeepUp,
    [switch] $Twice,
    [switch] $Report,
    [string] $BaseUrl = "http://localhost:8082",
    [string] $Module = "ms-production-packaging",
    [string] $NpmRegistry = "https://registry.npmjs.org"
)

# 'Continue' y no 'Stop': en Windows PowerShell 5.1 el stderr de un ejecutable nativo
# (docker, mvnw, newman) se convierte en ErrorRecord y con 'Stop' abortaria el script
# aunque el comando haya terminado bien. El control se hace con $LASTEXITCODE.
$ErrorActionPreference = "Continue"

$repoRoot    = Split-Path -Parent $PSScriptRoot
$moduleDir   = Join-Path $repoRoot $Module
$composeFile = Join-Path $repoRoot "docker-compose.integration.yml"
$coleccion   = Join-Path $repoRoot "postman\bc4-integracion.postman_collection.json"
$entorno     = Join-Path $repoRoot "postman\local.postman_environment.json"
$reporteDir  = Join-Path $moduleDir "target\newman"
$reporteJson = Join-Path $reporteDir "report.json"
$reporteHtml = Join-Path $repoRoot "docs\reportes\integracion-postman\index.html"

$appProcess = $null

function Write-Section($texto) {
    Write-Host ""
    Write-Host "=== $texto " -NoNewline
    Write-Host ("=" * [Math]::Max(0, 60 - $texto.Length))
}

function Test-Comando($nombre) {
    try   { $null = Get-Command $nombre -ErrorAction Stop; return $true }
    catch { return $false }
}

# Actuator responde con content-type 'application/vnd.spring-boot.actuator.v3+json'.
# Windows PowerShell 5.1 no lo reconoce como texto y devuelve .Content como byte[],
# de modo que un -match directo sobre .Content nunca casa. Hay que decodificarlo.
function Get-CuerpoTexto($respuesta) {
    if ($respuesta.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($respuesta.Content)
    }
    return [string]$respuesta.Content
}

function Stop-Ambiente {
    if ($KeepUp) {
        Write-Host "  -KeepUp: el ambiente se queda levantado en $BaseUrl"
        if ($Mode -eq "docker") {
            Write-Host "  Para bajarlo: docker compose -f docker-compose.integration.yml down -v"
        }
        return
    }
    if ($Mode -eq "docker") {
        Write-Host "  Bajando el ambiente Docker..."
        & docker compose -f $composeFile down -v | Out-Null
    }
    elseif ($Mode -eq "local" -and $null -ne $appProcess -and -not $appProcess.HasExited) {
        Write-Host "  Parando la aplicacion (PID $($appProcess.Id))..."
        # /T mata el arbol: el 'java' del PATH suele ser el shim de Oracle
        # (javapath\java.exe), que lanza la JVM real como proceso hijo. Con
        # Stop-Process solo moriria el shim y la app seguiria ocupando el puerto.
        & taskkill /PID $appProcess.Id /T /F 2>&1 | Out-Null
    }
}

# ---------------------------------------------------------------------------
# 1. Prerrequisitos
# ---------------------------------------------------------------------------
Write-Section "1/5  Prerrequisitos"

if (-not (Test-Path $coleccion)) {
    Write-Host "ERROR: no se encontro la coleccion en $coleccion" -ForegroundColor Red
    exit 3
}

# Newman: se prefiere la instalacion global; si no esta, se usa npx (necesita Node).
$newmanExe  = $null
$newmanArgs = @()
if ($Report -and (Test-Comando "npx")) {
    # El reporter HTML no viene con Newman: npx carga los dos paquetes juntos.
    $newmanExe  = "npx.cmd"
    $newmanArgs = @("--yes", "--registry=$NpmRegistry", "-p", "newman", "-p", "newman-reporter-htmlextra", "newman")
}
elseif (Test-Comando "newman") {
    $newmanExe = "newman"
}
elseif (Test-Comando "npx") {
    $newmanExe = "npx.cmd"
    # El npm de esta maquina puede estar apuntando a un registro privado (p. ej.
    # AWS CodeArtifact) con el token caducado; entonces npx falla con E401 sin que
    # tenga nada que ver con las pruebas. Se fuerza el registro publico.
    $newmanArgs = @("--yes", "--registry=$NpmRegistry", "newman")
}
else {
    Write-Host "ERROR: no hay forma de ejecutar Newman." -ForegroundColor Red
    Write-Host "       Instala Node.js (trae npx) o ejecuta: npm install -g newman"
    exit 3
}

if ($Mode -eq "docker" -and -not (Test-Comando "docker")) {
    Write-Host "ERROR: Docker no esta disponible." -ForegroundColor Red
    Write-Host "       Arranca Docker Desktop, o usa el modo sin Docker: -Mode local"
    exit 3
}

Write-Host "  Modo      : $Mode"
Write-Host "  Base URL  : $BaseUrl"
Write-Host "  Newman    : $newmanExe $($newmanArgs -join ' ')"
Write-Host "  Coleccion : postman\bc4-integracion.postman_collection.json"

# ---------------------------------------------------------------------------
# 2. Levantar el ambiente
# ---------------------------------------------------------------------------
Write-Section "2/5  Ambiente de pruebas"

if ($Mode -eq "docker") {
    Write-Host "  docker compose up --build (la primera vez compila la imagen: tarda)..."
    & docker compose -f $composeFile up -d --build
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "FALLO: no se pudo levantar el ambiente Docker." -ForegroundColor Red
        exit 2
    }
    Write-Host "  Contenedores arriba: bc4-it-db (PostgreSQL efimero) + bc4-it-app"
}
elseif ($Mode -eq "local") {
    if (-not (Test-Path $moduleDir)) {
        Write-Host "ERROR: no existe el modulo '$Module'" -ForegroundColor Red
        exit 3
    }
    Set-Location $moduleDir

    Write-Host "  Compilando el jar (sin tests)..."
    & .\mvnw.cmd -B -DskipTests package | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FALLO: el empaquetado del jar fallo. Revisa: .\mvnw.cmd -B -DskipTests package" -ForegroundColor Red
        exit 2
    }

    $jar = Get-ChildItem "target\*.jar" | Where-Object { $_.Name -notlike "*sources*" } | Select-Object -First 1
    if (-not $jar) {
        Write-Host "FALLO: no se encontro el jar en target\" -ForegroundColor Red
        exit 2
    }

    if (-not (Test-Path $reporteDir)) { New-Item -ItemType Directory -Path $reporteDir -Force | Out-Null }
    $appLog = Join-Path $reporteDir "app-it.log"

    Write-Host "  Arrancando $($jar.Name) con el perfil 'it' (H2 en memoria)..."
    # La ruta del jar puede contener espacios: Start-Process une los argumentos sin
    # comillas, asi que hay que ponerlas explicitamente.
    $appProcess = Start-Process -FilePath "java" `
        -ArgumentList "-jar", "`"$($jar.FullName)`"", "--spring.profiles.active=it" `
        -PassThru -NoNewWindow -RedirectStandardOutput $appLog -RedirectStandardError "$appLog.err"
    Write-Host "  PID $($appProcess.Id) - log en target\newman\app-it.log"
}
else {
    Write-Host "  Modo 'none': se asume que la aplicacion ya esta levantada."
}

# ---------------------------------------------------------------------------
# 3. Esperar a que la aplicacion responda
# ---------------------------------------------------------------------------
Write-Section "3/5  Esperando /actuator/health"

$healthUrl = "$BaseUrl/actuator/health"
$arriba    = $false
$intentos  = 60      # 60 x 2 s = 2 minutos

for ($i = 1; $i -le $intentos; $i++) {
    try {
        $r = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200 -and (Get-CuerpoTexto $r) -match '"status"\s*:\s*"UP"') {
            $arriba = $true
            Write-Host "  UP tras $($i * 2) s aprox."
            break
        }
    } catch {
        # todavia arrancando
    }
    if ($Mode -eq "local" -and $null -ne $appProcess -and $appProcess.HasExited) {
        Write-Host ""
        Write-Host "FALLO: la aplicacion termino durante el arranque. Log:" -ForegroundColor Red
        if (Test-Path (Join-Path $reporteDir "app-it.log")) {
            Get-Content (Join-Path $reporteDir "app-it.log") -Tail 25 | ForEach-Object { Write-Host "  $_" }
        }
        exit 2
    }
    Start-Sleep -Seconds 2
}

if (-not $arriba) {
    Write-Host ""
    Write-Host "FALLO: $healthUrl no respondio UP en 2 minutos." -ForegroundColor Red
    if ($Mode -eq "docker") {
        Write-Host "  Logs: docker compose -f docker-compose.integration.yml logs app-it"
    }
    Stop-Ambiente
    exit 4
}

# ---------------------------------------------------------------------------
# 4. Ejecutar la coleccion
# ---------------------------------------------------------------------------
Write-Section "4/5  Coleccion Postman (Newman)"

if (-not (Test-Path $reporteDir)) { New-Item -ItemType Directory -Path $reporteDir -Force | Out-Null }

$vueltas = 1
if ($Twice) { $vueltas = 2 }

$newmanExit  = 0
$totalFallos = 0
$resumenes   = @()

for ($v = 1; $v -le $vueltas; $v++) {

    if ($Twice) {
        Write-Host ""
        Write-Host "  --- Vuelta $v de $vueltas (misma base de datos) ---"
    }

    $reporteVuelta = $reporteJson
    if ($Twice) { $reporteVuelta = Join-Path $reporteDir "report-$v.json" }

    $argsNewman = $newmanArgs + @(
        "run", $coleccion,
        "-e", $entorno,
        "--env-var", "baseUrl=$BaseUrl",
        "--reporters", "cli,json",
        "--reporter-json-export", $reporteVuelta,
        "--color", "off"
    )
    # El HTML sale de la ultima vuelta: con -Twice es la que demuestra la repetibilidad.
    if ($Report -and $v -eq $vueltas) {
        $argsNewman[$argsNewman.IndexOf("cli,json")] = "cli,json,htmlextra"
        $argsNewman += @(
            "--reporter-htmlextra-export", $reporteHtml,
            "--reporter-htmlextra-title", "BC4 - Pruebas de integracion por flujo",
            "--reporter-htmlextra-browserTitle", "BC4 integracion"
        )
    }

    & $newmanExe @argsNewman
    if ($LASTEXITCODE -ne 0) { $newmanExit = $LASTEXITCODE }

    if (Test-Path $reporteVuelta) {
        $reporte = Get-Content $reporteVuelta -Raw | ConvertFrom-Json
        $st = $reporte.run.stats
        $totalFallos += [int]$st.assertions.failed
        $resumenes += [PSCustomObject]@{
            Vuelta    = $v
            Reporte   = $reporte
            Stats     = $st
            Archivo   = (Split-Path -Leaf $reporteVuelta)
        }
    } else {
        Write-Host "  AVISO: Newman no genero el reporte JSON de la vuelta $v." -ForegroundColor Yellow
        $newmanExit = 1
    }
}

# ---------------------------------------------------------------------------
# 5. Resumen
# ---------------------------------------------------------------------------
Write-Section "5/5  Resumen"

foreach ($r in $resumenes) {
    $etiqueta = ""
    if ($Twice) { $etiqueta = "vuelta $($r.Vuelta) - " }
    Write-Host ("  {0}Requests   : {1} ejecutados, {2} fallidos" -f $etiqueta, $r.Stats.requests.total, $r.Stats.requests.failed)
    Write-Host ("  {0}Aserciones : {1} ejecutadas, {2} fallidas" -f $etiqueta, $r.Stats.assertions.total, $r.Stats.assertions.failed)

    if ([int]$r.Stats.assertions.failed -gt 0) {
        Write-Host ""
        Write-Host "  Aserciones fallidas:" -ForegroundColor Red
        foreach ($f in $r.Reporte.run.failures) {
            Write-Host ("    - [{0}] {1}" -f $f.source.name, $f.error.test) -ForegroundColor Red
            Write-Host ("      {0}" -f $f.error.message)
        }
    }
    Write-Host "  Reporte    : $Module\target\newman\$($r.Archivo)"
}
if ($Report -and (Test-Path $reporteHtml)) {
    Write-Host "  HTML       : docs\reportes\integracion-postman\index.html"
}

if ($Twice -and $resumenes.Count -eq 2 -and
    [int]$resumenes[0].Stats.assertions.failed -eq 0 -and
    [int]$resumenes[1].Stats.assertions.failed -gt 0) {
    Write-Host ""
    Write-Host "  La primera vuelta paso y la segunda no: la coleccion NO es repetible." -ForegroundColor Red
    Write-Host "  Busca datos fijos que chocan con los creados en la vuelta anterior."
}

Stop-Ambiente

if ($newmanExit -ne 0 -or $totalFallos -gt 0) {
    Write-Host ""
    Write-Host "FALLO: la validacion de integracion no paso." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "OK: validacion de integracion superada." -ForegroundColor Green
exit 0

<#
.SYNOPSIS
    Gate de validacion de pruebas unitarias (entorno de pruebas asistido por IA).

.DESCRIPTION
    Un unico comando que ejecuta la misma comprobacion en la maquina del desarrollador,
    en la sesion de un agente de IA y en el CI:

        1. verifica que el entorno tiene JDK 21
        2. compila y ejecuta TODA la suite unitaria (surefire excluye los *IT.java)
        3. genera el reporte de cobertura JaCoCo y valida el umbral (jacoco:check)
        4. opcionalmente ejecuta mutation testing con PIT (-Mutation)
        5. imprime un resumen y devuelve un exit code inequivoco

    Ningun test generado por IA se da por bueno sin que este script devuelva 0.

.PARAMETER Module
    Microservicio a validar. Por defecto ms-production-packaging.

.PARAMETER Mutation
    Ejecuta ademas PIT (mutation testing). Es lento (minutos): la cobertura de lineas
    dice que se ejecuto, los mutantes dicen que se comprobo.

.PARAMETER JdkPath
    Ruta al JDK 21. Por defecto se autodetecta.

.EXAMPLE
    .\tools\validate-tests.ps1
    .\tools\validate-tests.ps1 -Mutation

.OUTPUTS
    exit 0 = OK | 1 = tests fallidos o error de build | 2 = cobertura bajo umbral
    exit 3 = entorno invalido (sin JDK 21) | 4 = mutantes bajo umbral
#>
[CmdletBinding()]
param(
    [string] $Module = "ms-production-packaging",
    [switch] $Mutation,
    [string] $JdkPath = ""
)

# 'Continue' y no 'Stop': en Windows PowerShell 5.1, redirigir el stderr de un
# ejecutable nativo (java -version, mvnw) convierte cada linea en un ErrorRecord y
# con 'Stop' aborta el script aunque el comando haya terminado bien. Aqui el control
# de errores se hace con los exit codes ($LASTEXITCODE), que es lo fiable.
$ErrorActionPreference = "Continue"

$repoRoot  = Split-Path -Parent $PSScriptRoot
$moduleDir = Join-Path $repoRoot $Module

function Write-Section($texto) {
    Write-Host ""
    Write-Host "=== $texto " -NoNewline
    Write-Host ("=" * [Math]::Max(0, 60 - $texto.Length))
}

# ---------------------------------------------------------------------------
# 1. Entorno
# ---------------------------------------------------------------------------
Write-Section "1/4  Entorno"

if (-not (Test-Path $moduleDir)) {
    Write-Host "ERROR: no existe el modulo '$Module' en $repoRoot" -ForegroundColor Red
    exit 3
}

if ([string]::IsNullOrWhiteSpace($JdkPath)) {
    $candidatos = @(
        $env:JAVA_HOME_21_X64,                      # runners de GitHub Actions
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Eclipse Adoptium\jdk-21",
        "C:\Program Files\Microsoft\jdk-21"
    )
    foreach ($c in $candidatos) {
        if (-not [string]::IsNullOrWhiteSpace($c) -and (Test-Path (Join-Path $c "bin\java.exe"))) {
            $JdkPath = $c
            break
        }
    }
}

# Si JAVA_HOME ya apunta a un JDK 21, se respeta.
if ([string]::IsNullOrWhiteSpace($JdkPath) -and -not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $version = & "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | Select-Object -First 1
    if ($version -match '"21') { $JdkPath = $env:JAVA_HOME }
}

if ([string]::IsNullOrWhiteSpace($JdkPath)) {
    Write-Host "ERROR: no se encontro un JDK 21." -ForegroundColor Red
    Write-Host "       El build exige Java 21 (release version 21 not supported con JDK menores)."
    Write-Host "       Indicalo con: .\tools\validate-tests.ps1 -JdkPath 'C:\ruta\al\jdk-21'"
    exit 3
}

$env:JAVA_HOME = $JdkPath
$javaVersion = & "$JdkPath\bin\java.exe" -version 2>&1 | Select-Object -First 1
Write-Host "  Modulo    : $Module"
Write-Host "  JAVA_HOME : $JdkPath"
Write-Host "  Java      : $javaVersion"

Set-Location $moduleDir

# ---------------------------------------------------------------------------
# 2. Suite unitaria + umbral de cobertura
# ---------------------------------------------------------------------------
Write-Section "2/4  Tests unitarios + cobertura"

$log = & .\mvnw.cmd -B verify 2>&1
$mvnExit = $LASTEXITCODE

$resultado = $log | Select-String -Pattern "Tests run:.*Failures" | Select-Object -Last 1
if ($resultado) { Write-Host "  $($resultado.ToString().Trim())" }

if ($mvnExit -ne 0) {
    $reglaViolada = $log | Select-String -Pattern "Rule violated|coveredRatio is"
    if ($reglaViolada) {
        Write-Host ""
        Write-Host "FALLO: la cobertura esta por debajo del umbral configurado en pom.xml" -ForegroundColor Red
        $reglaViolada | ForEach-Object { Write-Host "  $($_.ToString().Trim())" -ForegroundColor Red }
        Write-Host "  -> Escribe los tests que faltan. Bajar el umbral no es una opcion."
        exit 2
    }
    Write-Host ""
    Write-Host "FALLO: la suite no paso. Ultimas lineas del log:" -ForegroundColor Red
    $log | Select-Object -Last 25 | ForEach-Object { Write-Host "  $_" }
    exit 1
}

# ---------------------------------------------------------------------------
# 3. Resumen de cobertura desde jacoco.csv
# ---------------------------------------------------------------------------
Write-Section "3/4  Cobertura (JaCoCo)"

$csv = Join-Path $moduleDir "target\site\jacoco\jacoco.csv"
if (Test-Path $csv) {
    $filas = Import-Csv $csv
    $lm = ($filas | Measure-Object -Property LINE_MISSED    -Sum).Sum
    $lc = ($filas | Measure-Object -Property LINE_COVERED   -Sum).Sum
    $bm = ($filas | Measure-Object -Property BRANCH_MISSED  -Sum).Sum
    $bc = ($filas | Measure-Object -Property BRANCH_COVERED -Sum).Sum

    $pctLineas = [Math]::Round(100.0 * $lc / [Math]::Max(1, $lm + $lc), 1)
    $pctRamas  = [Math]::Round(100.0 * $bc / [Math]::Max(1, $bm + $bc), 1)

    Write-Host "  Lineas : $pctLineas %  ($lc / $($lm + $lc))"
    Write-Host "  Ramas  : $pctRamas %  ($bc / $($bm + $bc))"

    $sinCubrir = $filas | Where-Object { [int]$_.LINE_MISSED -gt 0 } |
                 Sort-Object { [int]$_.LINE_MISSED } -Descending | Select-Object -First 5
    if ($sinCubrir) {
        Write-Host "  Clases con lineas sin cubrir:"
        $sinCubrir | ForEach-Object { Write-Host "    - $($_.CLASS)  (missed $($_.LINE_MISSED))" }
    }
    Write-Host "  Reporte: target\site\jacoco\index.html"
} else {
    Write-Host "  AVISO: no se encontro jacoco.csv" -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# 4. Mutation testing (opcional)
# ---------------------------------------------------------------------------
Write-Section "4/4  Mutation testing (PIT)"

if (-not $Mutation) {
    Write-Host "  Omitido. Ejecuta con -Mutation para validar la CALIDAD de los tests,"
    Write-Host "  no solo que lineas tocan."
} else {
    Write-Host "  Ejecutando PIT (esto tarda varios minutos)..."
    # Comillas obligatorias: sin ellas Windows PowerShell 5.1 parte el argumento en
    # "-Djacoco" y ".skip=true", y Maven falla con "Unknown lifecycle phase".
    $pitLog = & .\mvnw.cmd -B "-Djacoco.skip=true" test-compile org.pitest:pitest-maven:mutationCoverage 2>&1
    $pitExit = $LASTEXITCODE

    $stats = $pitLog | Select-String -Pattern "Generated \d+ mutations|mutations? with|Mutation Coverage"
    if ($stats) { $stats | ForEach-Object { Write-Host "  $($_.ToString().Trim())" } }

    if ($pitExit -ne 0 -and -not $stats) {
        # PIT ni siquiera llego a mutar: es un error de build, no de calidad de los tests.
        Write-Host ""
        Write-Host "FALLO: PIT no se pudo ejecutar. Salida de Maven:" -ForegroundColor Red
        $pitLog | Select-String -Pattern "ERROR" | Select-Object -First 10 |
            ForEach-Object { Write-Host "  $($_.ToString().Trim())" }
        exit 1
    }
    if ($pitExit -ne 0) {
        Write-Host ""
        Write-Host "FALLO: demasiados mutantes sobrevivieron (umbral en pom.xml)." -ForegroundColor Red
        Write-Host "  Un mutante superviviente = codigo ejecutado por los tests pero NO comprobado."
        Write-Host "  Detalle: target\pit-reports\index.html"
        exit 4
    }
    Write-Host "  Reporte: target\pit-reports\index.html"
}

Write-Host ""
Write-Host "OK: validacion superada." -ForegroundColor Green
exit 0

<#
.SYNOPSIS
    Gate de validacion de las PRUEBAS DE CONTRATO (Pact) - entorno asistido por IA.

.DESCRIPTION
    Un unico comando que recorre el ciclo completo de contract testing entre los dos
    microservicios y devuelve un exit code inequivoco:

        1. comprueba el entorno (JDK 21 y los dos modulos)
        2. CONSUMIDOR: ejecuta las pruebas Pact de bc5-logistic-consumer, que
           publican el pacto en los recursos de prueba del proveedor
        3. inspecciona el pacto generado (interacciones y estados exigidos)
        4. PROVEEDOR: reproduce cada interaccion contra ms-production-packaging
           realmente levantado (perfil 'it', H2 en memoria)
        5. imprime el resumen

    Ninguna prueba de contrato generada por IA se da por buena sin que este script
    devuelva 0.

.PARAMETER Only
    consumer : solo genera el pacto (paso 2).
    provider : solo verifica el pacto ya publicado (paso 4).
    all      : (por defecto) el ciclo completo.

.PARAMETER BreakCheck
    Comprobacion de falsabilidad, el equivalente aqui del mutation testing.
    Altera a proposito una expectativa del pacto (el estado del paquete que BC5
    recoge) y EXIGE que la verificacion del proveedor falle. Si pasa igualmente,
    la verificacion no estaba comprobando nada y el gate es decorativo: exit 5.
    El pacto original se restaura siempre, tambien si el script se interrumpe.

.PARAMETER Module
    Modulo proveedor. Por defecto ms-production-packaging.

.PARAMETER ConsumerModule
    Ruta (relativa a la raiz del repo) del modulo consumidor.

.PARAMETER JdkPath
    Ruta al JDK 21. Por defecto se autodetecta.

.EXAMPLE
    .\tools\validate-contracts.ps1
    .\tools\validate-contracts.ps1 -Only provider
    .\tools\validate-contracts.ps1 -BreakCheck

.OUTPUTS
    exit 0 = OK | 1 = interacciones no verificadas por el proveedor
    exit 2 = el consumidor no pudo generar el pacto | 3 = entorno invalido
    exit 4 = el pacto no existe o no tiene interacciones
    exit 5 = el gate es falsificable (-BreakCheck: el pacto alterado tambien paso)
#>
[CmdletBinding()]
param(
    [ValidateSet("all", "consumer", "provider")]
    [string] $Only = "all",
    [switch] $BreakCheck,
    [string] $Module = "ms-production-packaging",
    [string] $ConsumerModule = "contract-testing\bc5-logistic-consumer",
    [string] $JdkPath = ""
)

# 'Continue' y no 'Stop': en Windows PowerShell 5.1 el stderr de un ejecutable nativo
# (java -version, mvnw) se convierte en ErrorRecord y con 'Stop' abortaria el script
# aunque el comando haya terminado bien. El control se hace con $LASTEXITCODE.
$ErrorActionPreference = "Continue"

$repoRoot     = Split-Path -Parent $PSScriptRoot
$providerDir  = Join-Path $repoRoot $Module
$consumerDir  = Join-Path $repoRoot $ConsumerModule
$pactDir      = Join-Path $providerDir "src\test\resources\pacts"
$pactFile     = Join-Path $pactDir "ms-logistic-delivery-ms-production-packaging.json"

function Write-Section($texto) {
    Write-Host ""
    Write-Host "=== $texto " -NoNewline
    Write-Host ("=" * [Math]::Max(0, 60 - $texto.Length))
}

# Suma tests/fallos de los XML de surefire o failsafe. Se leen los XML y no la
# salida de Maven: el resumen de consola miente cuando el build muere antes.
function Get-ResultadoTests($reportDir) {
    $total = 0; $fallos = 0; $errores = 0; $saltados = 0; $encontrados = $false
    if (Test-Path $reportDir) {
        foreach ($xml in Get-ChildItem -Path $reportDir -Filter "TEST-*.xml" -ErrorAction SilentlyContinue) {
            $encontrados = $true
            [xml] $doc = Get-Content $xml.FullName -Raw
            $total    += [int] $doc.testsuite.tests
            $fallos   += [int] $doc.testsuite.failures
            $errores  += [int] $doc.testsuite.errors
            $saltados += [int] $doc.testsuite.skipped
        }
    }
    return [PSCustomObject]@{
        Total = $total; Fallos = $fallos; Errores = $errores
        Saltados = $saltados; Encontrados = $encontrados
    }
}

function Invoke-ProviderVerification {
    Set-Location $providerDir
    $log = & .\mvnw.cmd -B -Pcontract test-compile failsafe:integration-test failsafe:verify 2>&1
    return [PSCustomObject]@{ Exit = $LASTEXITCODE; Log = $log }
}

# ---------------------------------------------------------------------------
# 1. Entorno
# ---------------------------------------------------------------------------
Write-Section "1/5  Entorno"

if (-not (Test-Path $providerDir)) {
    Write-Host "ERROR: no existe el modulo proveedor '$Module' en $repoRoot" -ForegroundColor Red
    exit 3
}
if (-not (Test-Path $consumerDir)) {
    Write-Host "ERROR: no existe el modulo consumidor '$ConsumerModule' en $repoRoot" -ForegroundColor Red
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

if ([string]::IsNullOrWhiteSpace($JdkPath) -and -not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $version = & "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | Select-Object -First 1
    if ($version -match '"21') { $JdkPath = $env:JAVA_HOME }
}

if ([string]::IsNullOrWhiteSpace($JdkPath)) {
    Write-Host "ERROR: no se encontro un JDK 21." -ForegroundColor Red
    Write-Host "       Indicalo con: .\tools\validate-contracts.ps1 -JdkPath 'C:\ruta\al\jdk-21'"
    exit 3
}

$env:JAVA_HOME = $JdkPath
$javaVersion = & "$JdkPath\bin\java.exe" -version 2>&1 | Select-Object -First 1

Write-Host "  Consumidor : $ConsumerModule  (ms-logistic-delivery / BC5)"
Write-Host "  Proveedor  : $Module  (ms-production-packaging / BC4)"
Write-Host "  Pacto      : $Module\src\test\resources\pacts"
Write-Host "  Alcance    : $Only"
Write-Host "  JAVA_HOME  : $JdkPath"
Write-Host "  Java       : $javaVersion"

$consumidor = $null
$proveedor  = $null

# ---------------------------------------------------------------------------
# 2. Consumidor: genera el pacto
# ---------------------------------------------------------------------------
Write-Section "2/5  Consumidor (genera el pacto)"

if ($Only -eq "provider") {
    Write-Host "  Omitido (-Only provider): se verifica el pacto ya publicado."
}
else {
    Set-Location $consumerDir
    Write-Host "  mvnw test - cada prueba ejecuta el cliente real contra el mock de Pact..."

    $logConsumer = & .\mvnw.cmd -B test 2>&1
    $exitConsumer = $LASTEXITCODE
    $consumidor = Get-ResultadoTests (Join-Path $consumerDir "target\surefire-reports")

    Write-Host ("  Tests: {0} ejecutados, {1} fallidos, {2} con error" -f `
        $consumidor.Total, $consumidor.Fallos, $consumidor.Errores)

    if ($exitConsumer -ne 0 -or $consumidor.Fallos -gt 0 -or $consumidor.Errores -gt 0) {
        Write-Host ""
        Write-Host "FALLO: las pruebas del consumidor no pasaron; sin ellas no hay pacto." -ForegroundColor Red
        $logConsumer | Select-String -Pattern "ERROR|FAILURE|expected" | Select-Object -Last 15 |
            ForEach-Object { Write-Host "  $($_.ToString().Trim())" }
        Set-Location $repoRoot
        exit 2
    }
}

# ---------------------------------------------------------------------------
# 3. El pacto
# ---------------------------------------------------------------------------
Write-Section "3/5  Pacto publicado"

if (-not (Test-Path $pactFile)) {
    Write-Host "FALLO: no se genero el pacto en $pactFile" -ForegroundColor Red
    Write-Host "       Revisa la propiedad pact.rootDir del pom del consumidor."
    Set-Location $repoRoot
    exit 4
}

$pacto = Get-Content $pactFile -Raw | ConvertFrom-Json
$interacciones = @($pacto.interactions)

if ($interacciones.Count -eq 0) {
    Write-Host "FALLO: el pacto no contiene ninguna interaccion." -ForegroundColor Red
    Set-Location $repoRoot
    exit 4
}

Write-Host ("  {0} -> {1}   ({2} interacciones)" -f `
    $pacto.consumer.name, $pacto.provider.name, $interacciones.Count)
Write-Host "  Generado   : $((Get-Item $pactFile).LastWriteTime)"
Write-Host ""

foreach ($i in $interacciones) {
    $ruta = $i.request.path
    if ($i.request.query) {
        $pares = foreach ($p in $i.request.query.PSObject.Properties) { "$($p.Name)=$($p.Value -join ',')" }
        # Concatenacion explicita: en una cadena interpolada, "$ruta?" se come la ruta.
        $ruta = $ruta + "?" + ($pares -join "&")
    }
    Write-Host ("    [{0} {1}] {2} -> {3}" -f $i.request.method, $ruta, $i.description, $i.response.status)
    foreach ($estado in @($i.providerStates)) {
        Write-Host ("        given: {0}" -f $estado.name)
    }
}

# ---------------------------------------------------------------------------
# 4. Proveedor: verifica el pacto
# ---------------------------------------------------------------------------
Write-Section "4/5  Proveedor (verifica el pacto)"

if ($Only -eq "consumer") {
    Write-Host "  Omitido (-Only consumer): el pacto queda publicado sin verificar."
}
else {
    Write-Host "  mvnw -Pcontract failsafe:integration-test - levanta BC4 y reproduce cada interaccion..."

    $resultadoProveedor = Invoke-ProviderVerification
    $proveedor = Get-ResultadoTests (Join-Path $providerDir "target\failsafe-reports")

    Write-Host ("  Interacciones verificadas: {0} de {1}, {2} fallidas" -f `
        ($proveedor.Total - $proveedor.Fallos - $proveedor.Errores), $interacciones.Count, `
        ($proveedor.Fallos + $proveedor.Errores))

    if (-not $proveedor.Encontrados) {
        Write-Host ""
        Write-Host "FALLO: el proveedor no ejecuto ninguna verificacion." -ForegroundColor Red
        Write-Host "       Una verificacion que no corre es peor que una que falla: parece verde."
        $resultadoProveedor.Log | Select-String -Pattern "ERROR" | Select-Object -Last 15 |
            ForEach-Object { Write-Host "  $($_.ToString().Trim())" }
        Set-Location $repoRoot
        exit 1
    }

    if ($resultadoProveedor.Exit -ne 0 -or $proveedor.Fallos -gt 0 -or $proveedor.Errores -gt 0) {
        Write-Host ""
        Write-Host "FALLO: el proveedor no cumple el contrato que publico el consumidor." -ForegroundColor Red
        $resultadoProveedor.Log |
            Select-String -Pattern "has a matching body|BodyMismatch|expected|Actual:|but was|No such state|Missing" |
            Select-Object -Last 20 | ForEach-Object { Write-Host "  $($_.ToString().Trim())" }
        Write-Host ""
        Write-Host "  Reporte completo: $Module\target\failsafe-reports"
        Write-Host "  Recuerda: el pacto manda. Si el proveedor cambio a proposito, el cambio se"
        Write-Host "  negocia con BC5 y se regenera el pacto; no se relaja la expectativa."
        Set-Location $repoRoot
        exit 1
    }

    # -----------------------------------------------------------------------
    # 4b. Falsabilidad: el pacto alterado TIENE que hacer fallar la verificacion
    # -----------------------------------------------------------------------
    if ($BreakCheck) {
        Write-Host ""
        Write-Host "  --- Comprobacion de falsabilidad (-BreakCheck) ---"
        Write-Host "  Se altera el pacto (estado LISTO -> TRANSFERIDO) y se exige que falle."

        $respaldo = "$pactFile.bak"
        Copy-Item $pactFile $respaldo -Force
        $falsificable = $false

        try {
            $alterado = Get-Content $pactFile -Raw | ConvertFrom-Json
            $tocada = 0
            foreach ($i in @($alterado.interactions)) {
                if ($i.response.body -and $i.response.body.PSObject.Properties.Name -contains "estado") {
                    $i.response.body.estado = "TRANSFERIDO"
                    $tocada++
                }
            }

            if ($tocada -eq 0) {
                Write-Host "  AVISO: ninguna interaccion expone 'estado'; no se pudo falsear." -ForegroundColor Yellow
            }
            else {
                $alterado | ConvertTo-Json -Depth 100 | Set-Content $pactFile -Encoding UTF8
                $intento = Invoke-ProviderVerification
                $tras = Get-ResultadoTests (Join-Path $providerDir "target\failsafe-reports")

                if ($intento.Exit -eq 0 -and $tras.Fallos -eq 0 -and $tras.Errores -eq 0) {
                    $falsificable = $true
                }
                else {
                    Write-Host ("  OK: la verificacion fallo con el pacto alterado ({0} interacciones rotas)." -f `
                        ($tras.Fallos + $tras.Errores)) -ForegroundColor Green
                }
            }
        }
        finally {
            Move-Item $respaldo $pactFile -Force
            Write-Host "  Pacto original restaurado."
        }

        if ($falsificable) {
            Write-Host ""
            Write-Host "FALLO: el pacto alterado tambien paso la verificacion." -ForegroundColor Red
            Write-Host "       La verificacion no esta comprobando el cuerpo de la respuesta:"
            Write-Host "       probablemente todo el body usa matchers de tipo y no queda ningun"
            Write-Host "       valor exacto que pueda romperse. Revisa los matchers del consumidor."
            Set-Location $repoRoot
            exit 5
        }

        # La ultima corrida dejo los reportes del intento fallido: se repite la
        # verificacion buena para que target\failsafe-reports refleje la realidad.
        Write-Host "  Rehaciendo la verificacion con el pacto restaurado..."
        $final = Invoke-ProviderVerification
        $proveedor = Get-ResultadoTests (Join-Path $providerDir "target\failsafe-reports")
        if ($final.Exit -ne 0) {
            Write-Host "FALLO: la verificacion dejo de pasar tras restaurar el pacto." -ForegroundColor Red
            Set-Location $repoRoot
            exit 1
        }
    }
}

# ---------------------------------------------------------------------------
# 5. Resumen
# ---------------------------------------------------------------------------
Write-Section "5/5  Resumen"

if ($consumidor) {
    Write-Host ("  Consumidor : {0} pruebas Pact, {1} fallidas   ({2})" -f `
        $consumidor.Total, ($consumidor.Fallos + $consumidor.Errores), $ConsumerModule)
}
Write-Host ("  Pacto      : {0} interacciones, {1} estados exigidos" -f `
    $interacciones.Count, (@($interacciones.providerStates).Count))
if ($proveedor) {
    Write-Host ("  Proveedor  : {0} interacciones verificadas, {1} fallidas" -f `
        $proveedor.Total, ($proveedor.Fallos + $proveedor.Errores))
}
if ($BreakCheck -and $Only -ne "consumer") {
    Write-Host "  Falsabilidad: comprobada (el pacto alterado hace fallar la verificacion)"
}

Set-Location $repoRoot

if ($Only -eq "consumer") {
    Write-Host ""
    Write-Host "OK: pacto generado. Falta verificarlo en el proveedor (-Only provider)." -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "OK: contrato verificado de punta a punta." -ForegroundColor Green
exit 0

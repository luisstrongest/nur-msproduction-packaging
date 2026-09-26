<#
.SYNOPSIS
    Ejecuta la CAPA DE TESTING COMPLETA de BC4 y deja los reportes entregables.

.DESCRIPTION
    Un unico comando para el Proyecto Final. Encadena los gates que ya existen, uno por
    nivel de prueba, y copia los reportes a docs/reportes/ (versionado; target/ no lo esta).

        1. Unitarias + cobertura  tools\validate-tests.ps1        -> docs\reportes\coverage-unitarias\
        2. Integracion en Java    mvnw -Pintegration (failsafe)  -> docs\reportes\integracion-java\
        3. Integracion Postman    tools\validate-integration.ps1 -> docs\reportes\integracion-postman\
        4. Contrato (Pact)        tools\validate-contracts.ps1

    No se detiene en el primer fallo: ejecuta todos los niveles e imprime la tabla final,
    para que se vea de una vez que paso y que no.

.PARAMETER SkipContracts
    Omite el nivel 4 (el requerimiento del Proyecto Final pide unitarias + integracion).

.PARAMETER Mutation
    Pasa -Mutation al gate de unitarias (PIT). Tarda varios minutos mas.

.EXAMPLE
    .\tools\run-all-tests.ps1
    .\tools\run-all-tests.ps1 -SkipContracts
    .\tools\run-all-tests.ps1 -Mutation

.OUTPUTS
    exit 0 = todos los niveles en verde | 1 = al menos un nivel fallo
#>
[CmdletBinding()]
param(
    [switch] $SkipContracts,
    [switch] $Mutation,
    [string] $Module = "ms-production-packaging"
)

$ErrorActionPreference = "Continue"

$repoRoot  = Split-Path -Parent $PSScriptRoot
$moduleDir = Join-Path $repoRoot $Module
$reportes  = Join-Path $repoRoot "docs\reportes"
$resultados = @()

function Write-Nivel($texto) {
    Write-Host ""
    Write-Host ("#" * 70) -ForegroundColor Cyan
    Write-Host "#  $texto" -ForegroundColor Cyan
    Write-Host ("#" * 70) -ForegroundColor Cyan
}

function Invoke-Nivel($nombre, [scriptblock] $accion) {
    Push-Location $repoRoot
    $inicio = Get-Date
    try     { & $accion; $codigo = $LASTEXITCODE }
    finally { Pop-Location }
    $script:resultados += [PSCustomObject]@{
        Nivel     = $nombre
        Resultado = $(if ($codigo -eq 0) { "OK" } else { "FALLO (exit $codigo)" })
        Segundos  = [int]((Get-Date) - $inicio).TotalSeconds
    }
}

function Copy-Reporte($origen, $destino) {
    if (-not (Test-Path $origen)) { return }
    if (Test-Path $destino) { Remove-Item -Recurse -Force $destino }
    New-Item -ItemType Directory -Force (Split-Path -Parent $destino) | Out-Null
    Copy-Item -Recurse $origen $destino
}

# ---------------------------------------------------------------------------
Write-Nivel "1/4  Pruebas unitarias + cobertura JaCoCo (umbral 85 % lineas / 80 % ramas)"
Invoke-Nivel "1. Unitarias + cobertura" {
    if ($Mutation) { & (Join-Path $PSScriptRoot "validate-tests.ps1") -Module $Module -Mutation }
    else           { & (Join-Path $PSScriptRoot "validate-tests.ps1") -Module $Module }
}
Copy-Reporte (Join-Path $moduleDir "target\site\jacoco") (Join-Path $reportes "coverage-unitarias")
if ($Mutation) {
    Copy-Reporte (Join-Path $moduleDir "target\pit-reports") (Join-Path $reportes "mutation-pit")
}

# ---------------------------------------------------------------------------
Write-Nivel "2/4  Integracion en Java: un test por flujo (FlujoProduccionIT, FlujoEnvasadoIT)"
Invoke-Nivel "2. Integracion Java (MockMvc + H2)" {
    Push-Location $moduleDir
    try {
        & .\mvnw.cmd -B -Pintegration test-compile failsafe:integration-test failsafe:verify
    } finally { Pop-Location }
}
$failsafe = Join-Path $moduleDir "target\failsafe-reports"
$destJava = Join-Path $reportes "integracion-java"
if (Test-Path $failsafe) {
    if (Test-Path $destJava) { Remove-Item -Recurse -Force $destJava }
    New-Item -ItemType Directory -Force $destJava | Out-Null
    Get-ChildItem $failsafe -Filter "*integration*" | Copy-Item -Destination $destJava
}

# ---------------------------------------------------------------------------
Write-Nivel "3/4  Integracion Postman: 3 flujos, dos vueltas contra la misma BD"
Invoke-Nivel "3. Integracion Postman (Newman)" {
    & (Join-Path $PSScriptRoot "validate-integration.ps1") -Mode local -Twice -Report
}

# ---------------------------------------------------------------------------
if (-not $SkipContracts) {
    Write-Nivel "4/4  Contrato Pact BC5 -> BC4 (con comprobacion de falsabilidad)"
    Invoke-Nivel "4. Contrato (Pact)" {
        & (Join-Path $PSScriptRoot "validate-contracts.ps1") -BreakCheck
    }
}

# ---------------------------------------------------------------------------
Write-Nivel "Resumen"
$resultados | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "  Reportes entregables en docs\reportes\:"
Write-Host "    coverage-unitarias\index.html    (JaCoCo)"
Write-Host "    integracion-java\                (failsafe)"
Write-Host "    integracion-postman\index.html   (Newman htmlextra)"

if ($resultados | Where-Object { $_.Resultado -ne "OK" }) {
    Write-Host ""
    Write-Host "FALLO: al menos un nivel no paso." -ForegroundColor Red
    exit 1
}
Write-Host ""
Write-Host "OK: la capa de testing completa esta en verde." -ForegroundColor Green
exit 0

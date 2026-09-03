# ============================================================
#   VAULT 2.0 · PREBUILD WIKI (regenera .github-wiki-build/)
#   Ejecutar con:
#   powershell -ExecutionPolicy Bypass -File prebuild-wiki.ps1
# ============================================================

$ErrorActionPreference = "Stop"

$source = Join-Path $PSScriptRoot "wiki"
$dest   = Join-Path $PSScriptRoot ".github-wiki-build"

if (-not (Test-Path -LiteralPath $source)) {
    Write-Error "[ERROR] No existe carpeta wiki\ en $source"
    exit 1
}

Write-Host "Limpiando $dest ..." -ForegroundColor DarkCyan
if (Test-Path -LiteralPath $dest) { Remove-Item -LiteralPath $dest -Recurse -Force }
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$files = Get-ChildItem -Path $source -Recurse -File -Include *.md |
    Where-Object { $_.Name -notin @("README.md", "docs.json") }

foreach ($f in $files) {
    $rel  = $f.FullName.Substring($source.Length).TrimStart('\','/')
    $flat = $rel -replace '[\\/]','-'
    Copy-Item -LiteralPath $f.FullName -Destination (Join-Path $dest $flat) -Force
    Write-Host "  $rel  ->  $flat" -ForegroundColor Gray
}

# GitHub Wiki requiere Home.md
Copy-Item -LiteralPath (Join-Path $dest "index.md") -Destination (Join-Path $dest "Home.md") -Force
Write-Host "Home.md (copia index.md) OK" -ForegroundColor Green

$n = (Get-ChildItem -LiteralPath $dest -File -Filter *.md).Count
Write-Host ""
Write-Host "[OK] $n archivos Markdown listos en:  $dest" -ForegroundColor Green
exit 0

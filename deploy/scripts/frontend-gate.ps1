$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir '..\..')).Path
$FrontendDir = Join-Path $ProjectRoot 'frontend'
$Attempts = if ($env:DUTYLOG_FRONTEND_INSTALL_ATTEMPTS) { [int]$env:DUTYLOG_FRONTEND_INSTALL_ATTEMPTS } else { 3 }
$DelaySeconds = if ($env:DUTYLOG_FRONTEND_RETRY_DELAY_SECONDS) { [int]$env:DUTYLOG_FRONTEND_RETRY_DELAY_SECONDS } else { 15 }

foreach ($required in @('package.json','package-lock.json','generated-lockfile-manifest.txt','.node-version','.npm-version','.npmrc')) {
  $path = Join-Path $FrontendDir $required
  if (-not (Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).Length -le 0) {
    throw "Vue frontend committed delivery input is missing: $path"
  }
}

$expectedNode = (Get-Content -Raw (Join-Path $FrontendDir '.node-version')).Trim()
$expectedNpm = (Get-Content -Raw (Join-Path $FrontendDir '.npm-version')).Trim()
$NpmCommand = (Get-Command npm.cmd -ErrorAction Stop).Source
$actualNode = ((& node --version).Trim() -replace '^v','')
$actualNpm = (& $NpmCommand --version).Trim()
if ($actualNode -ne $expectedNode) { throw "Node $actualNode is running; $expectedNode is required." }
if ($actualNpm -ne $expectedNpm) { throw "npm $actualNpm is running; $expectedNpm is required." }

& node (Join-Path $FrontendDir 'scripts\verify-authentic-lockfile.mjs')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Remove-Item -LiteralPath (Join-Path $FrontendDir 'node_modules') -Recurse -Force -ErrorAction SilentlyContinue

$installed = $false
for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
  Write-Host "Vue frontend npm ci attempt $attempt/$Attempts"
  & $NpmCommand --prefix $FrontendDir ci --no-audit --no-fund --prefer-online
  if ($LASTEXITCODE -eq 0) { $installed = $true; break }
  if ($attempt -lt $Attempts) {
    Write-Warning "Vue frontend npm ci failed; retrying in ${DelaySeconds}s..."
    Start-Sleep -Seconds $DelaySeconds
  }
}
if (-not $installed) { throw "Vue frontend npm ci failed after $Attempts attempts." }

foreach ($command in @('vue-tsc','vitest','vite')) {
  $launcher = Join-Path $FrontendDir "node_modules\.bin\$command.cmd"
  if (-not (Test-Path -LiteralPath $launcher)) { throw "Vue frontend local executable is missing after npm ci: node_modules/.bin/$command.cmd" }
}

& node (Join-Path $FrontendDir 'scripts\verify-authentic-lockfile.mjs'); if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $NpmCommand --prefix $FrontendDir ls --all *> $null; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
foreach ($script in @('verify:delivery','contract:check','typecheck','test:unit','build')) {
  & $NpmCommand --prefix $FrontendDir run $script
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

& git -C $ProjectRoot diff --exit-code -- frontend/package.json frontend/package-lock.json frontend/generated-lockfile-manifest.txt frontend/src/generated/dutylog-api.ts
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

foreach ($asset in @('dutylog-vue-app-shell.js','dutylog-vue-app-shell.css')) {
  $path = Join-Path $FrontendDir "dist\$asset"
  if (-not (Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).Length -le 0) { throw "Vue frontend build asset is missing: $path" }
}
$ChunkDir = Join-Path $FrontendDir 'dist\chunks'
$Chunks = if (Test-Path -LiteralPath $ChunkDir) { @(Get-ChildItem -LiteralPath $ChunkDir -Filter '*.js' -File -Recurse | Where-Object Length -gt 0) } else { @() }
if ($Chunks.Count -eq 0) { throw 'Vue frontend segmented build emitted no async JS chunks.' }
Write-Host 'Vue frontend committed-lockfile delivery gate passed.'

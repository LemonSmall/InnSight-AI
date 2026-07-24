param(
  [string]$Tag = "latest",
  [string]$OutputDir = "docker-images",
  [switch]$NoSave
)

$ErrorActionPreference = "Stop"

function Require-Command($Name) {
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "$Name is required. Please install Docker Desktop or Docker Engine first."
  }
}

Require-Command docker

docker version *> $null
if ($LASTEXITCODE -ne 0) {
  throw "Docker daemon is not running. Please start Docker Desktop first, then rerun this script."
}

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$images = @(
  @{ Name = "sushijia-hotel"; Context = "sushijia-server"; Dockerfile = "sushijia-server/Dockerfile"; Args = @("--build-arg", "MODULE=sushijia-hotel") },
  @{ Name = "sushijia-admin"; Context = "sushijia-server"; Dockerfile = "sushijia-server/Dockerfile"; Args = @("--build-arg", "MODULE=sushijia-admin") },
  @{ Name = "sushijia-web"; Context = "."; Dockerfile = "Dockerfile"; Args = @() }
)

foreach ($image in $images) {
  $fullName = "$($image.Name):$Tag"
  Write-Host "Building $fullName" -ForegroundColor Cyan
  $buildArgs = @("build", "-t", $fullName, "-f", $image.Dockerfile) + $image.Args + @($image.Context)
  docker @buildArgs
  if ($LASTEXITCODE -ne 0) {
    throw "Build failed: $fullName"
  }
}

if (-not $NoSave) {
  New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
  foreach ($image in $images) {
    $fullName = "$($image.Name):$Tag"
    $tarPath = Join-Path $OutputDir "$($image.Name)-$Tag.tar"
    Write-Host "Saving $fullName -> $tarPath" -ForegroundColor Cyan
    docker save -o $tarPath $fullName
    if ($LASTEXITCODE -ne 0) {
      throw "Save failed: $fullName"
    }
  }
}

Write-Host "Done." -ForegroundColor Green

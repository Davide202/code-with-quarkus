Write-Host "=========================================="
Write-Host "1. CONTROLLO E AVVIO DOCKER ENGINE"
Write-Host "=========================================="

# Esegue 'docker info' silenziando TUTTO l'output (sia stdout che stderr)
docker info *>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ATTENZIONE] Il demone Docker non è in esecuzione. Tento di avviarlo..." -ForegroundColor Yellow

    $dockerPath = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerPath) {
        Start-Process -FilePath $dockerPath
    } else {
        Write-Host "[ERRORE] Impossibile trovare Docker Desktop. Avvialo manualmente e riprova." -ForegroundColor Red
        exit 1
    }

    Write-Host "[INFO] Attendo che il motore Docker sia completamente avviato (potrebbe volerci un po')..." -ForegroundColor Cyan
    do {
        Start-Sleep -Seconds 2
        docker info *>$null
    } until ($LASTEXITCODE -eq 0)

    Write-Host "[OK] Docker è ora pronto e operativo!" -ForegroundColor Green
} else {
    Write-Host "[OK] Docker è già in esecuzione." -ForegroundColor Green
}

Write-Host "------------------------------------------"

Write-Host "=========================================="
Write-Host "2. BUILD MAVEN E JACOCO REPORT"
Write-Host "=========================================="

# Salva il percorso assoluto della cartella dello script (la cartella 'compose')
$ComposeDir = $PSScriptRoot

# Spostati nella cartella root del progetto (un livello sopra)
Set-Location -Path "$ComposeDir\.."

Write-Host "[INFO] Avvio compilazione nativa Quarkus in corso nella root: $((Get-Location).Path)..." -ForegroundColor Cyan

# Incapsula Maven in cmd.exe in modo che il suo script .cmd non chiuda PowerShell
cmd.exe /c 'mvn clean package -Dnative "-Dquarkus.package.jar.enabled=false" "-Dquarkus.native.native-image-xmx=8g" "-Dquarkus.native.container-build=true" "-Dquarkus.native.enabled=true"'

# Controlla se Maven ha finito con successo leggendo l'exit code dell'ultimo comando
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRORE] Errore durante la compilazione Maven. Il processo Docker Compose è stato annullato." -ForegroundColor Red

    # Torna alla cartella originale anche in caso di errore per non lasciarti nel percorso sbagliato
    Set-Location -Path $ComposeDir
    exit 1
}

Write-Host "[OK] Compilazione completata! Generazione del report JaCoCo in corso..." -ForegroundColor Cyan
cmd.exe /c 'mvn jacoco:report'

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Report JaCoCo generato con successo." -ForegroundColor Green

    # Punta direttamente al file index.html principale senza fare ricerche ricorsive
    $ReportPath = ".\target\site\jacoco\index.html"

    if (Test-Path $ReportPath) {
        Write-Host "[INFO] Apertura report nel browser..." -ForegroundColor Cyan
        Write-Host "Apri -> $(Resolve-Path $ReportPath)" -ForegroundColor DarkGray
        Start-Process $ReportPath
    } else {
        Write-Host "[WARN] File index.html principale non trovato al percorso $ReportPath" -ForegroundColor Yellow
    }
} else {
    Write-Host "[WARN] Errore durante la generazione del report JaCoCo, ma la build è andata a buon fine." -ForegroundColor Yellow
}

Write-Host "------------------------------------------"

Write-Host "=========================================="
Write-Host "3. AVVIO CONTAINER CON DOCKER COMPOSE"
Write-Host "=========================================="

Write-Host "[INFO] Ricostruzione e avvio dei container..." -ForegroundColor Cyan

# Torna nella cartella 'compose' in modo sicuro per lanciare Docker Compose
Set-Location -Path $ComposeDir
docker compose up -d --build
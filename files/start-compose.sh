#!/bin/bash

# ==========================================
# 1. CONTROLLO E AVVIO DOCKER ENGINE
# ==========================================
# Esegue 'docker info' silenziando l'output. Se fallisce, Docker è spento.
if ! docker info > /dev/null 2>&1; then
    echo "⏳ Il demone Docker non è in esecuzione. Tento di avviarlo..."

    # Rilevamento del sistema operativo per lanciare il comando corretto
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux (potrebbe chiederti la password per sudo)
        sudo systemctl start docker
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS (Avvia Docker Desktop)
        open -a Docker
    elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
        # Windows (Git Bash o terminale compatibile)
        # Nota: il percorso potrebbe variare a seconda dell'installazione
        start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    else
        echo "❌ Sistema operativo non riconosciuto. Avvia Docker manualmente e riprova."
        exit 1
    fi

    # Ciclo di attesa: continua a fare la ping a Docker finché non risponde
    echo "🔄 Attendo che il motore Docker sia completamente avviato..."
    while ! docker info > /dev/null 2>&1; do
        sleep 2
    done
    echo "✅ Docker è ora pronto e operativo!"
else
    echo "✅ Docker è già in esecuzione."
fi

echo "------------------------------------------"

# ==========================================
# 2. BUILD MAVEN E AVVIO CONTAINER
# ==========================================
echo "🚀 Avvio compilazione nativa Quarkus in corso..."
mvn clean package -Dnative "-Dquarkus.package.jar.enabled=false" "-Dquarkus.native.native-image-xmx=8g" "-Dquarkus.native.container-build=true" "-Dquarkus.native.enabled=true"

# Controlla se la build ha avuto successo (codice di uscita 0)
if [ $? -eq 0 ]; then
    echo "🐳 Compilazione completata con successo. Ricostruzione e avvio dei container..."
    # Ricostruisce l'immagine forzatamente (--build) e la avvia in background (-d)
    docker compose up -d --build
else
    echo "❌ Errore durante la compilazione Maven. Il processo Docker Compose è stato annullato."
    exit 1
fi


# Se sei su un ambiente basato su Unix (Linux, macOS, o WSL), ricordati che la prima volta che crei il file dovrai dargli i permessi di esecuzione digitando da terminale:
# chmod +x start.sh
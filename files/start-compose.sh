#!/bin/bash

echo "🚀 Avvio compilazione nativa Quarkus..."
# Il tuo comando esatto
mvn clean package -Dnative "-Dquarkus.package.jar.enabled=false" "-Dquarkus.native.native-image-xmx=8g" "-Dquarkus.native.container-build=true" "-Dquarkus.native.enabled=true"

# Controlla se la build ha avuto successo
if [ $? -eq 0 ]; then
    echo "🐳 Compilazione completata. Avvio dei container..."
    # Aggiungi --build per essere sicuro che prenda il nuovo eseguibile
    docker compose up -d --build app
else
    echo "❌ Errore durante la compilazione Maven. Docker Compose annullato."
    exit 1
fi
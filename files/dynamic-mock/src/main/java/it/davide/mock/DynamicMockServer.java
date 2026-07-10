package it.davide.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@ApplicationScoped
public class DynamicMockServer {

    private static final Logger LOG = Logger.getLogger(DynamicMockServer.class);

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "mock.routes.file", defaultValue = "routes.json")
    String routesFilePath;

    public void init(@Observes Router router) {

        // --- LOG GLOBALE DELLE CHIAMATE IN INGRESSO ---
        router.route().order(Integer.MIN_VALUE).handler(rc -> {
            LOG.infof("➡️ Ricevuta chiamata: [%s] %s", rc.request().method(), rc.request().uri());
            rc.next();
        });

        InputStream is = null;
        File configFile = new File(routesFilePath);

        try {
            // 1. Tenta di caricare dal file esterno
            if (configFile.exists() && configFile.isFile()) {
                is = new FileInputStream(configFile);
                LOG.infof("Caricamento rotte mock dal file ESTERNO: %s", configFile.getAbsolutePath());
            } else {
                // 2. FALLBACK: Cerca nelle resources
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream("routes.json");
                if (is != null) {
                    LOG.info("File esterno non trovato. Caricamento delle rotte di DEFAULT integrate nell'immagine.");
                }
            }

            if (is != null) {
                try {
                    List<MockEndpoint> endpoints = objectMapper.readValue(is, new TypeReference<List<MockEndpoint>>() {});
                    LOG.infof("Inizializzazione di %d configurazioni mock...", endpoints.size());

                    for (MockEndpoint endpoint : endpoints) {

                        if (endpoint.method() == null || endpoint.method().isEmpty()) {
                            LOG.warnf("Nessun metodo specificato per l'URI %s. Rotta ignorata.", endpoint.uri());
                            continue;
                        }

                        for (String methodStr : endpoint.method()) {
                            try {
                                HttpMethod method = HttpMethod.valueOf(methodStr.toUpperCase());
                                String uri = endpoint.uri();

                                // Logica 'startsWith'
                                String routePath = uri.endsWith("*") ? uri : uri + "*";

                                String responseBody = endpoint.responseBody() instanceof String ?
                                        (String) endpoint.responseBody() :
                                        objectMapper.writeValueAsString(endpoint.responseBody());

                                router.route(method, routePath).handler(rc -> {
                                    LOG.infof("✅ Match trovato per [%s] %s - Restituisco mock payload", rc.request().method(), rc.request().uri());

                                    rc.response()
                                            .putHeader("Content-Type", "application/json")
                                            .setStatusCode(200)
                                            .end(responseBody);
                                });

                                LOG.infof("Rotta registrata: [%s] %s", method, routePath);

                            } catch (IllegalArgumentException e) {
                                LOG.errorf("Metodo HTTP non valido '%s' per l'URI %s", methodStr, endpoint.uri());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Errore durante il parsing del JSON", e);
                } finally {
                    try { is.close(); } catch (IOException ignored) {}
                }
            } else {
                LOG.warn("Nessun file 'routes.json' trovato. Nessun mock caricato.");
            }

        } catch (IOException e) {
            LOG.error("Errore durante la lettura del file JSON delle rotte", e);
        }

        // --- NUOVO: GESTIONE FALLBACK (DEFAULT RESPONSE) ---
        // Se nessuna delle rotte registrate sopra fa match, Vert.x genera un 404.
        // Intercettiamo questo 404 e restituiamo la risposta di default con HTTP 200 (OK).
        router.errorHandler(404, rc -> {
            LOG.warnf("⚠️ Nessun match trovato per [%s] %s - Restituisco risposta di default", rc.request().method(), rc.request().uri());
            rc.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200) // Imposto a 200 OK come richiesto, ma puoi mettere 404 se preferisci
                    .end("{\"status\":\"ok\"}");
        });
    }
}
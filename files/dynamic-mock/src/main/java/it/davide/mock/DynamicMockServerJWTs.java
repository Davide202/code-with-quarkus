package it.davide.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@ApplicationScoped
public class DynamicMockServerJWTs {

    private static final Logger LOG = Logger.getLogger(DynamicMockServer.class);

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "mock.routes.file", defaultValue = "routes.json")
    String routesFilePath;

    @ConfigProperty(name = "mock.token.regex", defaultValue = ".*token.*")
    String tokenRegex;

    // La chiave privata usata per firmare i token
    private PrivateKey privateKey;

    @PostConstruct
    void initCrypto() {
        try {
            // 1. Cerchiamo la chiave privata nelle variabili d'ambiente (Base64 senza a capo)
            String envPrivateKey = System.getenv("MOCK_JWT_PRIVATE_KEY");

            if (envPrivateKey != null && !envPrivateKey.trim().isEmpty()) {
                LOG.info("🔑 Trovata MOCK_JWT_PRIVATE_KEY nelle variabili d'ambiente. Caricamento in corso...");

                // Rimuoviamo eventuali spazi o a capo e decodifichiamo
                byte[] privKeyBytes = Base64.getDecoder().decode(envPrivateKey.replaceAll("\\s+", ""));

                // Ricostruiamo l'oggetto PrivateKey di Java
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privKeyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                this.privateKey = keyFactory.generatePrivate(keySpec);

                LOG.info("✅ Chiave privata caricata con successo dall'ambiente!");

            } else {
                LOG.info("⚠️ Nessuna chiave trovata nell'ambiente. Generazione nuova coppia di chiavi RSA...");

                // Generiamo la coppia
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                KeyPair kp = kpg.generateKeyPair();
                this.privateKey = kp.getPrivate();
                PublicKey publicKey = kp.getPublic();

                // Codifichiamo in Base64 (singola stringa comoda per il .env)
                String privBase64 = Base64.getEncoder().encodeToString(this.privateKey.getEncoded());
                String pubBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());

                // Scriviamo sui file fisici (nella root di esecuzione dell'app dentro il container)
                Path privPath = Paths.get("mock-private.key");
                Path pubPath = Paths.get("mock-public.key");
                Files.writeString(privPath, privBase64);
                Files.writeString(pubPath, pubBase64);

                LOG.infof("💾 Chiavi salvate su file fisici: %s e %s", privPath.toAbsolutePath(), pubPath.toAbsolutePath());
                LOG.info("💡 SUGGERIMENTO: Copia il contenuto di questi file nelle variabili MOCK_JWT_PRIVATE_KEY e MOCK_JWT_PUBLIC_KEY del tuo dockercomposelocal.env");
            }
        } catch (Exception e) {
            LOG.error("❌ Errore critico durante l'inizializzazione crittografica", e);
        }
    }

    public void init(@Observes Router router) {

        // --- LOG GLOBALE DELLE CHIAMATE IN INGRESSO ---
        router.route().order(Integer.MIN_VALUE).handler(rc -> {
            LOG.infof("➡️ Ricevuta chiamata: [%s] %s", rc.request().method(), rc.request().uri());
            rc.next();
        });

        // =================================================================
        // ROTTA HARDCODED PER GENERAZIONE JWT FIRMATO
        // =================================================================
        router.routeWithRegex(HttpMethod.POST, tokenRegex).handler(rc -> {
            LOG.infof("🔑 Generazione JWT firmato dinamicamente per [%s] %s", rc.request().method(), rc.request().uri());

            if (this.privateKey == null) {
                rc.response().setStatusCode(500).end("{\"error\":\"Errore interno: Chiave privata non inizializzata\"}");
                return;
            }

            try {
                String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
                long exp = Instant.now().getEpochSecond() + 36000;
                String payload = "{\"sub\":\"mock-admin\",\"roles\":[\"ADMIN\",\"USER\",\"maas.provider.admin\"],\"exp\":" + exp + "}";

                String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes());
                String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
                String dataToSign = encodedHeader + "." + encodedPayload;

                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initSign(this.privateKey);
                sig.update(dataToSign.getBytes());
                byte[] signatureBytes = sig.sign();

                String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

                String jwt = dataToSign + "." + encodedSignature;
                String responseBody = String.format("{\"access_token\":\"%s\",\"token_type\":\"Bearer\",\"expires_in\":36000}", jwt);
                LOG.infof("Response Body: %s",responseBody);
                rc.response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(200)
                        .end(responseBody);

            } catch (Exception e) {
                LOG.error("Errore durante la firma del token JWT", e);
                rc.response().setStatusCode(500).end("{\"error\":\"Internal Server Error\"}");
            }
        });

        InputStream is = null;
        File configFile = new File(routesFilePath);

        try {
            if (configFile.exists() && configFile.isFile()) {
                is = new FileInputStream(configFile);
                LOG.infof("Caricamento rotte mock dal file ESTERNO: %s", configFile.getAbsolutePath());
            } else {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream("routes.json");
                if (is != null) {
                    LOG.info("File esterno non trovato. Caricamento delle rotte di DEFAULT integrate nell'immagine.");
                }
            }

            if (is != null) {
                try {
                    List<MockEndpoint> endpoints = objectMapper.readValue(is, new TypeReference<List<MockEndpoint>>() {});

                    for (MockEndpoint endpoint : endpoints) {
                        if (endpoint.method() == null || endpoint.method().isEmpty()) continue;

                        for (String methodStr : endpoint.method()) {
                            try {
                                HttpMethod method = HttpMethod.valueOf(methodStr.toUpperCase());
                                String uri = endpoint.uri();
                                String routePath = uri.endsWith("*") ? uri : uri + "*";
                                String responseBody = endpoint.responseBody() instanceof String ?
                                        (String) endpoint.responseBody() :
                                        objectMapper.writeValueAsString(endpoint.responseBody());

                                router.route(method, routePath).handler(rc -> {
                                    LOG.infof("✅ Match trovato per [%s] %s - Restituisco mock payload: %s", rc.request().method(), rc.request().uri(),responseBody);
                                    rc.response()
                                            .putHeader("Content-Type", "application/json")
                                            .setStatusCode(200)
                                            .end(responseBody);
                                });
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
            }
        } catch (IOException e) {
            LOG.error("Errore durante la lettura del file JSON delle rotte", e);
        }

        router.errorHandler(404, rc -> {
            LOG.warnf("⚠️ Nessun match trovato per [%s] %s - Restituisco risposta di default", rc.request().method(), rc.request().uri());
            rc.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end("{\"status\":\"ok\"}");
        });
    }
}
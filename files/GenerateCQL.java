import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generatore dinamico di CQL per Cassandra.
 * - Suffisso "UDT" -> CREATE TYPE
 * - Suffisso "Pk"  -> Chiavi Primarie (Partition e Clustering keys)
 * - Altro          -> CREATE TABLE
 *
 * Eseguire il file con il comando 'java GenerateCQL.java' dopo aver verificato
 * che le variabili 'entityPaths' e 'keySpace' siano configurate correttamente.
 */
public class GenerateCQL {
    //it.almaviva.wtf.mms.medialifecycle.repository.dataentity
    static final String[] entityPaths = {
            "repository/src/main/java/it/almaviva/wtf/mms/medialifecycle/repository/dataentity",
            "model/src/main/java/it/almaviva/wtf/mms/medialifecycle/repository/dataentity"
    };
    static final String keySpace = "ihbtkt_medialifecycle_ks_medialifecycle";
    static final String outputFileName = "init.cql";

    // Set di tipi base di Cassandra che NON devono essere avvolti nel tag frozen<>
    static final Set<String> CASSANDRA_PRIMITIVES = new HashSet<>(Arrays.asList(
            "text", "ascii", "varchar", "int", "bigint", "boolean", "decimal", "double",
            "float", "date", "timestamp", "time", "uuid", "timeuuid", "blob"
    ));

    static class FieldInfo {
        String cqlName;
        String javaType;
        String javaName;
    }

    static class ClassInfo {
        String className;
        String cqlName;
        String classType; // Valori possibili: "UDT", "PK", o "ENTITY"
        List<FieldInfo> fields = new ArrayList<>();
        Set<String> dependencies = new HashSet<>();
        ClassInfo pkClass; // Riferimento alla classe PK (utilizzato solo per i tipi "ENTITY")
    }

    public static void main(String[] args) throws Exception {
        Map<String, ClassInfo> allClasses = new HashMap<>();

        System.out.println("======================================================");
        System.out.println(" INIZIO GENERAZIONE CQL DA ENTITY CASSANDRA");
        System.out.println("======================================================");
        System.out.println("[INFO] Percorsi entità: " + String.join(", ", entityPaths));
        System.out.println("[INFO] Keyspace target: " + keySpace);
        System.out.println("------------------------------------------------------\n");

        // 1. Scansione dinamica di tutte le classi nelle sottocartelle
        for (String entityPath : entityPaths) {
            try (Stream<Path> paths = Files.walk(Paths.get(entityPath))) {
                List<Path> javaFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .collect(Collectors.toList());

                System.out.println("[INFO] Trovati " + javaFiles.size() + " file .java da analizzare in: " + entityPath + "\n");

                for (Path path : javaFiles) {
                    String content = Files.readString(path);

                    // Estrazione del nome della classe dal file
                    Matcher classMatcher = Pattern.compile("class\\s+(\\w+)").matcher(content);
                    if (!classMatcher.find()) continue;
                    String className = classMatcher.group(1);

                    ClassInfo info = new ClassInfo();
                    info.className = className;

                    // Classificazione della classe in base al suo suffisso
                    if (className.endsWith("UDT")) info.classType = "UDT";
                    else if (className.endsWith("Pk")) info.classType = "PK";
                    else info.classType = "ENTITY";

                    // Estrazione dell'annotazione @CqlName a livello di classe (se presente)
                    String beforeClass = content.substring(0, content.indexOf("class " + className));
                    Matcher classCqlMatcher = Pattern.compile("@CqlName\\(\"([^\"]+)\"\\)").matcher(beforeClass);
                    if (classCqlMatcher.find()) {
                        info.cqlName = classCqlMatcher.group(1);
                    } else {
                        // Fallback: se manca @CqlName, convertiamo il nome della classe in snake_case pulito
                        info.cqlName = toSnakeCase(className).replace("_udt", "").replace("_entity", "s").replace("_pk", "");
                    }

                    System.out.println("=> ANALISI CLASSE: " + className + " [" + info.classType + "] -> Mapping CQL: " + info.cqlName);

                    // TAGLIO NETTO: Rimuove dal buffer tutto ciò che è all'interno o dopo le dichiarazioni di "static class".
                    // Questa pulizia preventiva elimina i Builder di Lombok o le inner class che "sporcano" la lettura dei campi.
                    content = content.replaceAll("(?s)\\s*(?:public|private|protected)?\\s*static\\s+class\\s+.*", "");

                    // REGEX AGGIORNATA: Il modificatore di accesso (private, protected, public) ora è opzionale
                    // per supportare i campi "package-private" che non hanno dichiarazioni esplicite.
                    Matcher fieldMatcher = Pattern.compile(
                            "((?:@[A-Za-z0-9_.]+(?:\\([^)]*\\))?\\s*)*)(?:(?:private|protected|public)\\s+)?([A-Za-z0-9_<>]+)\\s+([A-Za-z0-9_]+)\\s*;"
                    ).matcher(content);

                    Set<String> processedFieldNames = new HashSet<>(); // Set di controllo per evitare i duplicati

                    while (fieldMatcher.find()) {
                        String annotations = fieldMatcher.group(1);
                        String javaType = fieldMatcher.group(2);
                        String javaName = fieldMatcher.group(3);

                        // Ignora esplicitamente SerialVersionUID e i classici artefatti generati durante la compilazione o da Lombok
                        // FIX: Aggiunti i controlli per parole chiave Java ed espressioni catturate per errore nei body dei metodi
                        if (javaName.equals("serialVersionUID") || javaName.equals("this$0") || javaName.equals("builder") || javaName.equals("this") || javaName.equals("out") ||
                                javaName.matches("^(true|false|null|\\d+)$") || javaType.equals("return") || javaType.equals("throw")) {
                            System.out.println("   [SKIP] Campo ignorato (Artefatto di sistema/regex): " + javaName);
                            continue;
                        }

                        // Ignora i campi transitori (annotati con @Transient), a meno che non facciano parte della Primary Key
                        if (annotations.contains("@Transient") && !javaType.endsWith("Pk")) {
                            System.out.println("   [SKIP] Campo ignorato (Annotato con @Transient): " + javaName);
                            continue;
                        }

                        // Se il campo possiede l'annotazione @CqlName, usiamo quel nome.
                        // Altrimenti applichiamo il fallback convertendo in snake_case.
                        String cqlName = toSnakeCase(javaName);
                        Matcher fieldCqlMatcher = Pattern.compile("@CqlName\\(\"([^\"]+)\"\\)").matcher(annotations);
                        if (fieldCqlMatcher.find()) {
                            cqlName = fieldCqlMatcher.group(1);
                        }

                        // Prevenzione rigida contro i duplicati nello stesso oggetto (es. omonimie tra alias CQL e campi base)
                        if (processedFieldNames.contains(cqlName)) {
                            System.out.println("   [WARN] Campo saltato (Duplicato CQL identificato): " + cqlName + " (Java: " + javaName + ")");
                            continue;
                        }
                        processedFieldNames.add(cqlName);

                        FieldInfo fi = new FieldInfo();
                        fi.cqlName = cqlName;
                        fi.javaType = javaType;
                        fi.javaName = javaName;
                        info.fields.add(fi);

                        System.out.println("   [OK]   Campo mappato: " + javaName + " (" + javaType + ") -> " + cqlName);
                    }
                    allClasses.put(className, info);
                    System.out.println("   Totale campi validi estratti: " + info.fields.size() + "\n");
                }
            }
        }

        System.out.println("------------------------------------------------------");
        System.out.println("[INFO] Risoluzione delle dipendenze e delle Primary Keys in corso...");

        // 2. Risoluzione delle dipendenze tra UDT e aggancio delle Primary Keys per le Tabelle
        for (ClassInfo info : allClasses.values()) {
            if (info.classType.equals("ENTITY")) {
                // Cerca all'interno dell'Entity se c'è un campo che richiama una classe PK
                for (FieldInfo fi : info.fields) {
                    if (fi.javaType.endsWith("Pk") && allClasses.containsKey(fi.javaType)) {
                        info.pkClass = allClasses.get(fi.javaType);
                        System.out.println("   [LINK] PK trovata per Tabella '" + info.cqlName + "': " + info.pkClass.className);
                    }
                }
                // Rimuoviamo l'oggetto aggregatore PK dai campi standard per poi "esploderlo" nelle sue colonne individuali
                info.fields.removeIf(f -> f.javaType.endsWith("Pk"));
            }

            // Estrazione delle dipendenze: se un UDT contiene un altro UDT, dobbiamo tracciarlo
            // per ordinarli correttamente nel file .cql (chi è contenuto deve essere creato prima di chi lo contiene)
            if (info.classType.equals("UDT")) {
                for (FieldInfo fi : info.fields) {
                    String base = getBaseType(fi.javaType);
                    if (allClasses.containsKey(base) && allClasses.get(base).classType.equals("UDT")) {
                        info.dependencies.add(base);
                    }
                }
            }
        }

        // Sort Topologico degli UDT per rispettare i vincoli di creazione di Cassandra
        List<ClassInfo> sortedUDTs = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (ClassInfo info : allClasses.values()) {
            if (info.classType.equals("UDT")) {
                visit(info, allClasses, visited, sortedUDTs);
            }
        }

        System.out.println("------------------------------------------------------");
        System.out.println("[INFO] Generazione del file " + outputFileName + " in corso...");

        // 3. Scrittura fisica del file init.cql
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFileName))) {
            writer.println("-- 1. Creazione del Keyspace");
            writer.println("CREATE KEYSPACE IF NOT EXISTS " + keySpace);
            writer.println("WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};");
            writer.println("\nUSE " + keySpace + ";\n");

            writer.println("-- 2. Creazione degli UDT (User Defined Types)");
            for (ClassInfo info : sortedUDTs) {
                System.out.println("   Scrittura UDT: " + info.cqlName);
                writer.println("CREATE TYPE IF NOT EXISTS " + info.cqlName + " (");
                for (int i = 0; i < info.fields.size(); i++) {
                    FieldInfo fi = info.fields.get(i);
                    String type = mapType(fi.javaType, allClasses);
                    writer.print("    " + fi.cqlName + " " + type);
                    writer.println(i < info.fields.size() - 1 ? "," : "");
                }
                writer.println(");\n");
            }

            writer.println("-- 3. Creazione delle Tabelle (Entities)");
            for (ClassInfo info : allClasses.values()) {
                if (!info.classType.equals("ENTITY")) continue;

                // FIX: Evita di generare Tabelle per classi ausiliarie o DTO sprovviste di Primary Key
                if (info.pkClass == null) {
                    System.out.println("   [SKIP] Creazione tabella ignorata (Nessuna PK identificata): " + info.cqlName);
                    continue;
                }

                System.out.println("   Scrittura Tabella: " + info.cqlName);
                writer.println("CREATE TABLE IF NOT EXISTS " + info.cqlName + " (");
                List<String> pkNames = new ArrayList<>();

                // Inseriamo in cima i campi esplosi della chiave primaria
                if (info.pkClass != null) {
                    writer.println("    -- Colonne della Primary Key (" + info.pkClass.className + ")");
                    for (FieldInfo pkf : info.pkClass.fields) {
                        String type = mapType(pkf.javaType, allClasses);
                        writer.println("    " + pkf.cqlName + " " + type + ",");
                        pkNames.add(pkf.cqlName);
                    }
                }

                // Seguiti da tutti i campi normali
                writer.println("    -- Colonne Base e UDT");
                for (FieldInfo fi : info.fields) {
                    String type = mapType(fi.javaType, allClasses);
                    writer.println("    " + fi.cqlName + " " + type + ",");
                }

                // Definizione della clausola PRIMARY KEY
                if (!pkNames.isEmpty()) {
                    // Cassandra: Il primo elemento della classe PK diventa la Partition Key.
                    // I restanti elementi diventano le Clustering Columns.
                    String partition = pkNames.get(0);
                    String clustering = pkNames.size() > 1 ? ", " + String.join(", ", pkNames.subList(1, pkNames.size())) : "";
                    writer.println("\n    PRIMARY KEY ((" + partition + ")" + clustering + ")");
                } else {
                    writer.println("\n    -- ATTENZIONE: PRIMARY KEY definition missing! Non ho trovato una classe Pk per questa entità.");
                    System.out.println("   [ERR] Attenzione: Nessuna Primary Key identificata per la tabella " + info.cqlName);
                }
                writer.println(");\n");
            }

            System.out.println("\n======================================================");
            System.out.println(" COMPLETO! File '" + outputFileName + "' generato con successo.");
            System.out.println(" -> Generati: " + sortedUDTs.size() + " UDTs");
            System.out.println(" -> Generati: " + allClasses.values().stream().filter(c -> c.classType.equals("ENTITY") && c.pkClass != null).count() + " Tabelle");
            System.out.println("======================================================");
        }
    }

    // --- Helper Methods ---

    /**
     * Converte una stringa in CamelCase (es. "mioCampoJava") in snake_case (es. "mio_campo_java").
     */
    static String toSnakeCase(String camel) {
        return camel.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    /**
     * Pulisce i tipi Collection (es. List<String> diventa String) per estrarre la classe di base.
     */
    static String getBaseType(String javaType) {
        if (javaType.startsWith("Set<")) return javaType.substring(4, javaType.length() - 1);
        if (javaType.startsWith("List<")) return javaType.substring(5, javaType.length() - 1);
        return javaType;
    }

    /**
     * Esegue l'ordinamento topologico ricorsivo per assicurare che gli UDT vengano creati
     * dopo che tutte le loro dipendenze (gli UDT che contengono) siano già state create.
     */
    static void visit(ClassInfo info, Map<String, ClassInfo> classes, Set<String> visited, List<ClassInfo> sorted) {
        if (!visited.contains(info.className)) {
            visited.add(info.className);
            for (String dep : info.dependencies) {
                if (classes.containsKey(dep)) {
                    visit(classes.get(dep), classes, visited, sorted);
                }
            }
            sorted.add(info);
        }
    }

    /**
     * Mappa il tipo nativo Java nel corretto tipo dati CQL, applicando 'frozen<>'
     * alle collezioni o agli UDT nidificati.
     */
    static String mapType(String javaType, Map<String, ClassInfo> classes) {
        String cqlType;

        // 1. Risoluzione Mappature Tipi Base
        if (javaType.equals("String")) cqlType = "text";
        else if (javaType.equals("Integer") || javaType.equals("int")) cqlType = "int";
        else if (javaType.equals("Long") || javaType.equals("long")) cqlType = "bigint";
        else if (javaType.equals("Boolean") || javaType.equals("boolean")) cqlType = "boolean";
        else if (javaType.equals("BigDecimal")) cqlType = "decimal";
        else if (javaType.equals("LocalDate")) cqlType = "date";
        else if (javaType.equals("LocalDateTime")) cqlType = "timestamp";
        else if (javaType.equals("LocalTime")) cqlType = "time";
        else if (javaType.equals("Float") || javaType.equals("float")) cqlType = "float";

            // 2. Risoluzione Mappature Collezioni (richiedono frozen sugli elementi custom/collezioni)
        else if (javaType.startsWith("Set<")) {
            String inner = javaType.substring(4, javaType.length() - 1);
            return "set<" + freezeIfNeeded(mapType(inner, classes)) + ">";
        }
        else if (javaType.startsWith("List<")) {
            String inner = javaType.substring(5, javaType.length() - 1);
            return "list<" + freezeIfNeeded(mapType(inner, classes)) + ">";
        }

        // 3. Risoluzione UDT personalizzati mappati nel sistema
        // FIX: Ora applica rigorosamente il tag 'frozen<>' anche ai singoli oggetti UDT
        else if (classes.containsKey(javaType) && classes.get(javaType).classType.equals("UDT")) {
            cqlType = freezeIfNeeded(classes.get(javaType).cqlName);
        }
        // 4. Fallback disperato per tipi non mappati (es. Enum, classi non passate al parser)
        else {
            cqlType = "text";
        }

        return cqlType;
    }

    /**
     * Applica il vincolo 'frozen<>' al tipo CQL solo se quest'ultimo non appartiene
     * alla lista dei tipi primitivi base di Cassandra.
     */
    static String freezeIfNeeded(String cqlType) {
        // FIX: Evita di aggiungere frozen<> a tipi che lo possiedono già (es. double frozen da List/Set)
        if (CASSANDRA_PRIMITIVES.contains(cqlType.toLowerCase()) || cqlType.startsWith("frozen<")) {
            return cqlType;
        }
        return "frozen<" + cqlType + ">";
    }
}
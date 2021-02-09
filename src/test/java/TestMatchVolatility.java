import ch.qos.logback.classic.LoggerContext;
import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Resources;
import grakn.client.GraknClient;
import graql.lang.query.GraqlMatch;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static graql.lang.Graql.parseQuery;

public class TestMatchVolatility {

    static int BATCH_SIZE = 1; //3
    static MetricRegistry registry = new MetricRegistry();
    static ConsoleReporter reporter = ConsoleReporter.forRegistry(registry).convertDurationsTo(TimeUnit.SECONDS).build();

    @Test
    public void emptyDbTesting() throws IOException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        var graknHost = "localhost";
        var graknPort = 1729;
        var graknKeyspace = "grakn";

        //first batch
        for (int i = 0; i < BATCH_SIZE; i++) {
            //connect to grakn
            GraknClient graknClient;
            try (var t = registry.timer("setup").time()) {
                graknClient = GraknClient.core(graknHost + ":" + graknPort);
                if (graknClient.databases().contains(graknKeyspace)) graknClient.databases().delete(graknKeyspace);
                graknClient.databases().create(graknKeyspace);
            }

            //save schema
            try (var t = registry.timer("saveSchema").time()) {
                var schemaSession = graknClient.session(graknKeyspace, GraknClient.Session.Type.SCHEMA);
                var tx = schemaSession.transaction(GraknClient.Transaction.Type.WRITE);
                tx.query().define(parseQuery(Resources.toString(Resources.getResource("schema_bigger.gql"), StandardCharsets.UTF_8)));
                tx.commit();
                tx.close();
                schemaSession.close();
            }

            //read data
            GraknClient.Session dataSession;
            try (var t = registry.timer("readData-withoutRel").time()) {
                dataSession = graknClient.session(graknKeyspace, GraknClient.Session.Type.DATA);
                try (var readTx = dataSession.transaction(GraknClient.Transaction.Type.READ)) {
                    var results = readTx.query().match((GraqlMatch.Aggregate) parseQuery(
                            "match\n" +
                                    "$function isa SourceArtifact;\n" +
                                    "(is_parent: $function, is_child: $functionName);" +
                                    "($functionName) isa IDENTIFIER;" +
                                    "$functionName has token \"main\";\n" +
                                    "get $function; count;")).get();
                    System.out.println("Results: " + results);
                }
            }

            //clean up
            dataSession.close();
            graknClient.close();

            reporter.report();
        }

        //second batch
        for (int i = 0; i < BATCH_SIZE; i++) {
            //connect to grakn
            GraknClient graknClient;
            try (var t = registry.timer("setup").time()) {
                graknClient = GraknClient.core(graknHost + ":" + graknPort);
                if (graknClient.databases().contains(graknKeyspace)) graknClient.databases().delete(graknKeyspace);
                graknClient.databases().create(graknKeyspace);
            }

            //save schema
            try (var t = registry.timer("saveSchema").time()) {
                var schemaSession = graknClient.session(graknKeyspace, GraknClient.Session.Type.SCHEMA);
                var tx = schemaSession.transaction(GraknClient.Transaction.Type.WRITE);
                tx.query().define(parseQuery(Resources.toString(Resources.getResource("schema_bigger.gql"), StandardCharsets.UTF_8)));
                tx.commit();
                tx.close();
                schemaSession.close();
            }

            //read data
            GraknClient.Session dataSession;
            try (var t = registry.timer("readData-withRel").time()) {
                dataSession = graknClient.session(graknKeyspace, GraknClient.Session.Type.DATA);
                try (var readTx = dataSession.transaction(GraknClient.Transaction.Type.READ)) {
                    var results = readTx.query().match((GraqlMatch.Aggregate) parseQuery(
                            "match\n" +
                                    "$function isa SourceArtifact;\n" +
                                    "($function) isa DECLARATION;\n" +
                                    "($function) isa FUNCTION;\n" +
                                    "not { ($function) isa ARGUMENT; };\n" +
                                    "not { ($function) isa RETURN; };\n" +
                                    "not { ($function) isa INCOMPLETE; };\n" +
                                    "not { ($function) isa BODY; };\n" +
                                    "{ ($function) isa IDENTIFIER; $function has token \"main\"; } or { (is_parent: $function, is_child: $functionName); ($functionName) isa IDENTIFIER; $functionName has token \"main\"; };\n" +
                                    "get $function; count;")).get();
                    System.out.println("Results: " + results);
                }
            }

            //clean up
            dataSession.close();
            graknClient.close();

            reporter.report();
        }

        reporter.close();
    }
}

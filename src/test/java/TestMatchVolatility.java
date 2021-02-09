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

import static graql.lang.Graql.parseQuery;

public class TestMatchVolatility {

    static MetricRegistry registry = new MetricRegistry();
    static ConsoleReporter reporter = ConsoleReporter.forRegistry(registry).build();

    @Test
    public void emptyDbTesting() throws IOException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        //connect to grakn
        var graknHost = "localhost";
        var graknPort = 1729;
        var graknKeyspace = "grakn";

        for (int i = 0; i < 100; i++) {
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
            try (var t = registry.timer("readData-2").time()) {
                dataSession = graknClient.session(graknKeyspace, GraknClient.Session.Type.DATA);
                try (var readTx = dataSession.transaction(GraknClient.Transaction.Type.READ)) {
                    var results = readTx.query().match((GraqlMatch.Aggregate) parseQuery(
                            "match\n" +
                                    //"$function isa SourceArtifact;\n" +
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

        reporter.close();
    }
}
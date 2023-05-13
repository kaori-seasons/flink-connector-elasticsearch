package org.apache.flink.streaming.connectors.elasticsearch.table;

import org.apache.flink.connector.elasticsearch.test.DockerImageVersions;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.test.util.AbstractTestBase;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.junit.ClassRule;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static org.apache.flink.table.api.Expressions.row;

public class Elasticsearch7DynamicTableTestBase extends AbstractTestBase {

    @ClassRule
    public static ElasticsearchContainer elasticsearchContainer =
            new ElasticsearchContainer(DockerImageName.parse(DockerImageVersions.ELASTICSEARCH_7));

    public final RestHighLevelClient getClient() {
        return new RestHighLevelClient(
                RestClient.builder(HttpHost.create(elasticsearchContainer.getHttpHostAddress())));
    }

    public boolean createIndex(
            RestHighLevelClient client, String index, String type, String stringType)
            throws IOException {
        // create index
        CreateIndexRequest request = new CreateIndexRequest(index);
        request.settings(
                Settings.builder()
                        .put("index.number_of_shards", 3)
                        .put("index.number_of_replicas", 0));

        // set the string field if 'b' to keyword or text
        /**
         * request.mapping( type, "{\n" + " \"properties\": {\n" + " \"b\": {\n" + " \"type\":
         * \"text\"\n" + " }\n" + " }\n" + "}", XContentType.JSON );
         */
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        {
            builder.startObject("properties");
            {
                builder.startObject("b");
                {
                    builder.field("type", stringType);
                }
                builder.endObject();
            }
            builder.endObject();
        }
        builder.endObject();
        request.mapping(builder);
        CreateIndexResponse createIndexResponse =
                client.indices().create(request, RequestOptions.DEFAULT);
        return createIndexResponse.isAcknowledged();
    }

    public void insertData(StreamTableEnvironment tEnv, String index, String type)
            throws ExecutionException, InterruptedException {
        String sinkTable = "esTable" + Math.abs(new Random().nextInt());

        tEnv.executeSql(
                "CREATE TABLE "
                        + sinkTable
                        + "("
                        + "a BIGINT NOT NULL,\n"
                        + "b STRING NOT NULL,\n"
                        + "c FLOAT,\n"
                        + "d TINYINT NOT NULL,\n"
                        + "e TIME,\n"
                        + "f DATE,\n"
                        + "g TIMESTAMP NOT NULL,\n"
                        + "h as a + 2,\n"
                        + "PRIMARY KEY (c, d) NOT ENFORCED\n"
                        + ")\n"
                        + "WITH (\n"
                        + String.format("'%s'='%s',\n", "connector", "elasticsearch-7")
                        + String.format(
                                "'%s'='%s',\n",
                                ElasticsearchConnectorOptions.INDEX_OPTION.key(), index)
                        + String.format(
                                "'%s'='%s',\n",
                                ElasticsearchConnectorOptions.HOSTS_OPTION.key(),
                                "http://127.0.0.1:9200")
                        + String.format(
                                "'%s'='%s'\n",
                                ElasticsearchConnectorOptions.FLUSH_ON_CHECKPOINT_OPTION.key(),
                                "false")
                        + ")");

        tEnv.fromValues(
                        row(
                                1L,
                                "A B",
                                12.10f,
                                (byte) 2,
                                LocalTime.ofNanoOfDay(12345L * 1_000_000L),
                                LocalDate.ofEpochDay(12345),
                                LocalDateTime.parse("2012-12-12T12:12:12")),
                        row(
                                1L,
                                "A",
                                12.11f,
                                (byte) 2,
                                LocalTime.ofNanoOfDay(12345L * 1_000_000L),
                                LocalDate.ofEpochDay(12345),
                                LocalDateTime.parse("2012-12-12T12:12:12")),
                        row(
                                1L,
                                "A",
                                12.12f,
                                (byte) 2,
                                LocalTime.ofNanoOfDay(12345L * 1_000_000L),
                                LocalDate.ofEpochDay(12345),
                                LocalDateTime.parse("2012-12-12T12:12:12")),
                        row(
                                2L,
                                "B",
                                12.13f,
                                (byte) 3,
                                LocalTime.ofNanoOfDay(12346L * 1_000_000L),
                                LocalDate.ofEpochDay(12346),
                                LocalDateTime.parse("2012-12-12T12:12:13")),
                        row(
                                3L,
                                "C",
                                12.14f,
                                (byte) 4,
                                LocalTime.ofNanoOfDay(12347L * 1_000_000L),
                                LocalDate.ofEpochDay(12347),
                                LocalDateTime.parse("2012-12-12T12:12:14")),
                        row(
                                4L,
                                "D",
                                12.15f,
                                (byte) 5,
                                LocalTime.ofNanoOfDay(12348L * 1_000_000L),
                                LocalDate.ofEpochDay(12348),
                                LocalDateTime.parse("2012-12-12T12:12:15")),
                        row(
                                5L,
                                "E",
                                12.16f,
                                (byte) 6,
                                LocalTime.ofNanoOfDay(12349L * 1_000_000L),
                                LocalDate.ofEpochDay(12349),
                                LocalDateTime.parse("2012-12-12T12:12:16")))
                .executeInsert(sinkTable)
                .getJobClient()
                .get()
                .getJobExecutionResult()
                .get();
    }
}

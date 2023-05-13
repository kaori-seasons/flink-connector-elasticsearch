package org.apache.flink.streaming.connectors.elasticsearch.table;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;

import org.apache.flink.shaded.guava30.com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Elasticsearch7DynamicSourceITCase extends Elasticsearch7DynamicTableTestBase {

    private final String scanKeywordIndex = "scan-keyword-index";
    private final String scanKeywordType = "scan-keyword-type";

    @Before
    public void before() throws IOException, ExecutionException, InterruptedException {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);
        assertTrue(createIndex(getClient(), scanKeywordIndex, scanKeywordType, "keyword"));
        insertData(tEnv, scanKeywordIndex, scanKeywordType);
    }

    @Test
    public void testElasticsearchSource() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);
        tEnv.executeSql(
                "CREATE TABLE esTableSource ("
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
                                ElasticsearchConnectorOptions.INDEX_OPTION.key(), scanKeywordIndex)
                        + String.format(
                                "'%s'='%s',\n",
                                ElasticsearchConnectorOptions.HOSTS_OPTION.key(),
                                "http://127.0.0.1:9200")
                        + String.format(
                                "'%s'='%s',\n",
                                ElasticsearchConnectorOptions.SCROLL_MAX_SIZE_OPTION.key(), 10)
                        + String.format(
                                "'%s'='%s'\n",
                                ElasticsearchConnectorOptions.SCROLL_TIMEOUT_OPTION.key(), 1000)
                        + ")");

        Iterator<Row> collected =
                tEnv.executeSql("SELECT a, b, c, d, e, f, g, h FROM esTableSource").collect();
        List<String> result =
                Lists.newArrayList(collected).stream()
                        .map(Row::toString)
                        .sorted()
                        .collect(Collectors.toList());

        List<String> expected =
                Stream.of(
                                "1,A B,12.1,2,00:00:12,2003-10-20,2012-12-12T12:12:12,3",
                                "1,A,12.11,2,00:00:12,2003-10-20,2012-12-12T12:12:12,3",
                                "1,A,12.12,2,00:00:12,2003-10-20,2012-12-12T12:12:12,3",
                                "2,B,12.13,3,00:00:12,2003-10-21,2012-12-12T12:12:13,4",
                                "3,C,12.14,4,00:00:12,2003-10-22,2012-12-12T12:12:14,5",
                                "4,D,12.15,5,00:00:12,2003-10-23,2012-12-12T12:12:15,6",
                                "5,E,12.16,6,00:00:12,2003-10-24,2012-12-12T12:12:16,7")
                        .sorted()
                        .collect(Collectors.toList());
        assertEquals(expected, result);
    }
}

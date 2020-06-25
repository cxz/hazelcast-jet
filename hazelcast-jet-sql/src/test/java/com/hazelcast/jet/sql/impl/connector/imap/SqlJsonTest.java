package com.hazelcast.jet.sql.impl.connector.imap;

import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.jet.sql.SqlTestSupport;
import com.hazelcast.sql.impl.connector.LocalPartitionedMapConnector;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;

import static com.hazelcast.sql.impl.connector.SqlConnector.JSON_SERIALIZATION_FORMAT;
import static com.hazelcast.sql.impl.connector.SqlKeyValueConnector.TO_SERIALIZATION_KEY_FORMAT;
import static com.hazelcast.sql.impl.connector.SqlKeyValueConnector.TO_SERIALIZATION_VALUE_FORMAT;
import static java.lang.String.format;
import static java.time.Instant.ofEpochMilli;
import static java.time.ZoneId.systemDefault;
import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

// TODO: move it to IMDG when INSERTs are supported, or at least move to one of Jet connector tests ?
public class SqlJsonTest extends SqlTestSupport {

    @Test
    public void supportsNulls() {
        String name = generateRandomName();
        executeSql(format("CREATE EXTERNAL TABLE %s (" +
                        "  __key INT," +
                        "  id INT," +
                        "  name VARCHAR" +
                        ") TYPE \"%s\" " +
                        "OPTIONS (" +
                        "  \"%s\" '%s'" +
                        ")",
                name, LocalPartitionedMapConnector.TYPE_NAME,
                TO_SERIALIZATION_VALUE_FORMAT, JSON_SERIALIZATION_FORMAT
        ));

        executeSql(format("INSERT OVERWRITE %s VALUES (1, null, null)", name));

        Map<Integer, HazelcastJsonValue> map = instance().getMap(name);
        assertThat(map.get(1).toString()).isEqualTo("{\"id\":null,\"name\":null}");

        assertRowsEventuallyAnyOrder(
                format("SELECT __key, id, name FROM %s", name),
                singletonList(new Row(1, null, null)));
    }

    @Test
    public void keyShadowsValue() {
        String name = generateRandomName();
        executeSql(format("CREATE EXTERNAL TABLE %s (" +
                        "  name VARCHAR" +
                        ") TYPE \"%s\" " +
                        "OPTIONS (" +
                        "  \"%s\" '%s'," +
                        "  \"%s\" '%s'" +
                        ")",
                name, LocalPartitionedMapConnector.TYPE_NAME,
                TO_SERIALIZATION_KEY_FORMAT, JSON_SERIALIZATION_FORMAT,
                TO_SERIALIZATION_VALUE_FORMAT, JSON_SERIALIZATION_FORMAT
        ));

        executeSql(format("INSERT OVERWRITE %s VALUES ('Alice')", name));

        Map<HazelcastJsonValue, HazelcastJsonValue> map = instance().getMap(name);
        assertThat(map.get(new HazelcastJsonValue("{\"name\":\"Alice\"}")).toString()).isEqualTo("{}");

        assertRowsEventuallyAnyOrder(
                format("SELECT * FROM %s", name),
                singletonList(new Row("Alice")));
    }

    @Test
    public void supportsSchemaEvolution() {
        String name = generateRandomName();
        executeSql(format("CREATE EXTERNAL TABLE %s (" +
                        "  __key INT," +
                        "  name VARCHAR" +
                        ") TYPE \"%s\" " +
                        "OPTIONS (" +
                        "  \"%s\" '%s'" +
                        ")",
                name, LocalPartitionedMapConnector.TYPE_NAME,
                TO_SERIALIZATION_VALUE_FORMAT, JSON_SERIALIZATION_FORMAT
        ));

        // insert initial record
        executeSql(format("INSERT OVERWRITE %s VALUES (13, 'Alice')", name));

        // alter schema
        executeSql(format("CREATE OR REPLACE EXTERNAL TABLE %s (" +
                        "  __key INT," +
                        "  name VARCHAR," +
                        "  ssn BIGINT" +
                        ") TYPE \"%s\" " +
                        "OPTIONS (" +
                        "  \"%s\" '%s'" +
                        ")",
                name, LocalPartitionedMapConnector.TYPE_NAME,
                TO_SERIALIZATION_VALUE_FORMAT, JSON_SERIALIZATION_FORMAT
        ));
        // insert record against new schema/class definition
        executeSql(format("INSERT OVERWRITE %s VALUES (69, 'Bob', 123456789)", name));

        // assert both - initial & evolved - records are correctly read
        assertRowsEventuallyAnyOrder(
                format("SELECT * FROM %s", name),
                asList(
                        new Row(13, "Alice", null),
                        new Row(69, "Bob", 123456789L)));
    }

    @Test
    public void supportsAllTypes() {
        String name = generateRandomName();
        executeSql(format("CREATE EXTERNAL TABLE %s (" +
                        " __key DECIMAL(10, 0)," +
                        " string VARCHAR," +
                        " character0 CHAR," +
                        " character1 CHARACTER," +
                        " boolean0 BOOLEAN," +
                        " boolean1 BOOLEAN," +
                        " byte0 TINYINT," +
                        " byte1 TINYINT," +
                        " short0 SMALLINT," +
                        " short1 SMALLINT," +
                        " int0 INT, " +
                        " int1 INTEGER," +
                        " long0 BIGINT," +
                        " long1 BIGINT," +
                        " bigDecimal DEC(10, 1)," +
                        " bigInteger NUMERIC(5, 0)," +
                        " float0 REAL," +
                        " float1 FLOAT," +
                        " double0 DOUBLE," +
                        " double1 DOUBLE PRECISION," +
                        " \"localTime\" TIME," +
                        " localDate DATE," +
                        " localDateTime TIMESTAMP," +
                        " \"date\" TIMESTAMP WITH LOCAL TIME ZONE (\"DATE\")," +
                        " calendar TIMESTAMP WITH TIME ZONE (\"CALENDAR\")," +
                        " instant TIMESTAMP WITH LOCAL TIME ZONE," +
                        " zonedDateTime TIMESTAMP WITH TIME ZONE (\"ZONED_DATE_TIME\")," +
                        " offsetDateTime TIMESTAMP WITH TIME ZONE" +
                        ") TYPE \"%s\" " +
                        "OPTIONS (" +
                        "  \"%s\" '%s'" +
                        ")",
                name, LocalPartitionedMapConnector.TYPE_NAME,
                TO_SERIALIZATION_VALUE_FORMAT, JSON_SERIALIZATION_FORMAT
        ));

        executeSql(format("INSERT OVERWRITE %s VALUES (\n" +
                "1, --key\n" +
                "'string', --varchar\n" +
                "'a', --character\n" +
                "'b',\n" +
                "true, --boolean\n" +
                "false,\n" +
                "126, --byte\n" +
                "127, \n" +
                "32766, --short\n" +
                "32767, \n" +
                "2147483646, --int \n" +
                "2147483647,\n" +
                "9223372036854775806, --long\n" +
                "9223372036854775807,\n" +
                "9223372036854775.123, --bigDecimal\n" +
                "9223372036854775222, --bigInteger\n" +
                "1234567890.1, --float\n" +
                "1234567890.2, \n" +
                "123451234567890.1, --double\n" +
                "123451234567890.2,\n" +
                "time'12:23:34', -- local time\n" +
                "date'2020-04-15', -- local date \n" +
                "timestamp'2020-04-15 12:23:34.1', --timestamp\n" +
                "timestamp'2020-04-15 12:23:34.2', --timestamp with tz\n" +
                "timestamp'2020-04-15 12:23:34.3', --timestamp with tz\n" +
                "timestamp'2020-04-15 12:23:34.4', --timestamp with tz\n" +
                "timestamp'2020-04-15 12:23:34.5', --timestamp with tz\n" +
                "timestamp'2020-04-15 12:23:34.6' --timestamp with tz\n" +
                ")", name
        ));

        assertRowsEventuallyAnyOrder(
                format("SELECT * FROM %s", name),
                singletonList(new Row(
                        BigDecimal.valueOf(1),
                        "string",
                        "a",
                        "b",
                        true,
                        false,
                        (byte) 126,
                        (byte) 127,
                        (short) 32766,
                        (short) 32767,
                        2147483646,
                        2147483647,
                        9223372036854775806L,
                        9223372036854775807L,
                        new BigDecimal("9223372036854775.123"),
                        new BigDecimal("9223372036854775222"),
                        1234567890.1f,
                        1234567890.2f,
                        123451234567890.1,
                        123451234567890.2,
                        LocalTime.of(12, 23, 34),
                        LocalDate.of(2020, 4, 15),
                        // TODO: should be LocalDateTime.of(2020, 4, 15, 12, 23, 34, 100_000_000)
                        //  when temporal types are fixed
                        LocalDateTime.of(2020, 4, 15, 12, 23, 34, 0),
                        OffsetDateTime.ofInstant(Date.from(ofEpochMilli(1586953414200L)).toInstant(), systemDefault()),
                        ZonedDateTime.of(2020, 4, 15, 12, 23, 34, 300_000_000, UTC)
                                     .withZoneSameInstant(localOffset())
                                     .toOffsetDateTime(),
                        ZonedDateTime.of(2020, 4, 15, 12, 23, 34, 400_000_000, UTC)
                                     .withZoneSameInstant(localOffset())
                                     .toOffsetDateTime(),
                        ZonedDateTime.of(2020, 4, 15, 12, 23, 34, 500_000_000, UTC)
                                     .withZoneSameInstant(localOffset())
                                     .toOffsetDateTime(),
                        ZonedDateTime.of(2020, 4, 15, 12, 23, 34, 600_000_000, UTC)
                                     .withZoneSameInstant(systemDefault())
                                     .toOffsetDateTime()
                )));
    }

    private static ZoneOffset localOffset() {
        return systemDefault().getRules().getOffset(LocalDateTime.now());
    }

    private static String generateRandomName() {
        return "m_" + randomString().replace('-', '_');
    }
}

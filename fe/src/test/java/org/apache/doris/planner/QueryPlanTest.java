// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.planner;


import org.apache.doris.analysis.CreateDbStmt;
import org.apache.doris.analysis.CreateTableStmt;
import org.apache.doris.catalog.Catalog;
import org.apache.doris.catalog.Type;
import org.apache.doris.qe.ConnectContext;

import org.apache.doris.utframe.UtFrameUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

public class QueryPlanTest {
    // use a unique dir so that it won't be conflict with other unit test which
    // may also start a Mocked Frontend
    private static String runningDir = "fe/mocked/QueryPlanTest/" + UUID.randomUUID().toString() + "/";

    private static ConnectContext connectContext;

    @BeforeClass
    public static void beforeClass() throws Exception {
        UtFrameUtils.createMinDorisCluster(runningDir);

        // create connect context
        connectContext = UtFrameUtils.createDefaultCtx();
        // create database
        String createDbStmtStr = "create database test;";
        CreateDbStmt createDbStmt = (CreateDbStmt) UtFrameUtils.parseAndAnalyzeStmt(createDbStmtStr, connectContext);
        Catalog.getCurrentCatalog().createDb(createDbStmt);

        createTable("CREATE TABLE test.bitmap_table (\n" +
                "  `id` int(11) NULL COMMENT \"\",\n" +
                "  `id2` bitmap bitmap_union NULL\n" +
                ") ENGINE=OLAP\n" +
                "AGGREGATE KEY(`id`)\n" +
                "DISTRIBUTED BY HASH(`id`) BUCKETS 1\n" +
                "PROPERTIES (\n" +
                " \"replication_num\" = \"1\"\n" +
                ");");

        createTable("CREATE TABLE test.bitmap_table_2 (\n" +
                "  `id` int(11) NULL COMMENT \"\",\n" +
                "  `id2` bitmap bitmap_union NULL\n" +
                ") ENGINE=OLAP\n" +
                "AGGREGATE KEY(`id`)\n" +
                "DISTRIBUTED BY HASH(`id`) BUCKETS 1\n" +
                "PROPERTIES (\n" +
                " \"replication_num\" = \"1\"\n" +
                ");");

        createTable("CREATE TABLE test.hll_table (\n" +
                "  `id` int(11) NULL COMMENT \"\",\n" +
                "  `id2` hll hll_union NULL\n" +
                ") ENGINE=OLAP\n" +
                "AGGREGATE KEY(`id`)\n" +
                "DISTRIBUTED BY HASH(`id`) BUCKETS 1\n" +
                "PROPERTIES (\n" +
                " \"replication_num\" = \"1\"\n" +
                ");");
    }

    private static void createTable(String sql) throws Exception {
        CreateTableStmt createTableStmt = (CreateTableStmt) UtFrameUtils.parseAndAnalyzeStmt(sql, connectContext);
        Catalog.getCurrentCatalog().createTable(createTableStmt);
    }

    @Test
    public void testBitmapInsertInto() throws Exception {
        String queryStr = "explain INSERT INTO test.bitmap_table (id, id2) VALUES (1001, to_bitmap(1000)), (1001, to_bitmap(2000));";
        String explainString = UtFrameUtils.getSQLPlanOrErrorMsg(connectContext, queryStr);
        Assert.assertTrue(explainString.contains("OLAP TABLE SINK"));

        queryStr = "explain insert into test.bitmap_table select id, bitmap_union(id2) from test.bitmap_table_2 group by id;";
        explainString = UtFrameUtils.getSQLPlanOrErrorMsg(connectContext, queryStr);
        Assert.assertTrue(explainString.contains("OLAP TABLE SINK"));
        Assert.assertTrue(explainString.contains("bitmap_union"));
        Assert.assertTrue(explainString.contains("1:AGGREGATE"));
        Assert.assertTrue(explainString.contains("0:OlapScanNode"));

        queryStr = "explain insert into test.bitmap_table select id, id2 from test.bitmap_table_2;";
        explainString = UtFrameUtils.getSQLPlanOrErrorMsg(connectContext, queryStr);
        Assert.assertTrue(explainString.contains("OLAP TABLE SINK"));
        Assert.assertTrue(explainString.contains("OUTPUT EXPRS:`id` | `id2`"));
        Assert.assertTrue(explainString.contains("0:OlapScanNode"));

        queryStr = "explain insert into test.bitmap_table select id, to_bitmap(id2) from test.bitmap_table_2;";
        explainString = UtFrameUtils.getSQLPlanOrErrorMsg(connectContext, queryStr);
        Assert.assertTrue(explainString.contains("OLAP TABLE SINK"));
        Assert.assertTrue(explainString.contains("OUTPUT EXPRS:`id` | to_bitmap(`id2`)"));
        Assert.assertTrue(explainString.contains("0:OlapScanNode"));

        queryStr = "explain insert into test.bitmap_table select id, bitmap_hash(id2) from test.bitmap_table_2;";
        explainString = UtFrameUtils.getSQLPlanOrErrorMsg(connectContext, queryStr);
        Assert.assertTrue(explainString.contains("OLAP TABLE SINK"));
        Assert.assertTrue(explainString.contains("OUTPUT EXPRS:`id` | bitmap_hash(`id2`)"));
        Assert.assertTrue(explainString.contains("0:OlapScanNode"));

        queryStr = "explain insert into test.bitmap_table select id, id from test.bitmap_table_2;";
        String errorMsg = UtFrameUtils.getSQLPlanOrErrorMsg(connectContext, queryStr);
        Assert.assertTrue(errorMsg.contains("bitmap column id2 require the function return type is BITMAP"));
    }

    private static void testBitmapQueryPlan(String sql, String result) throws Exception {
        String explainString = UtFrameUtils.getSQLPlanOrErrorMsg(connectContext, "explain " + sql);
        Assert.assertTrue(explainString.contains(result));
    }

    @Test
    public void testBitmapQuery() throws Exception {
        testBitmapQueryPlan(
                "select * from test.bitmap_table;",
                Type.OnlyMetricTypeErrorMsg
        );

        testBitmapQueryPlan(
                "select count(id2) from test.bitmap_table;",
                Type.OnlyMetricTypeErrorMsg
        );

        testBitmapQueryPlan(
                "select group_concat(id2) from test.bitmap_table;",
                "group_concat requires first parameter to be of type STRING: group_concat(`id2`)"
        );

        testBitmapQueryPlan(
                "select sum(id2) from test.bitmap_table;",
                "sum requires a numeric parameter: sum(`id2`)"
        );

        testBitmapQueryPlan(
                "select avg(id2) from test.bitmap_table;",
                "avg requires a numeric parameter: avg(`id2`)"
        );

        testBitmapQueryPlan(
                "select max(id2) from test.bitmap_table;",
                Type.OnlyMetricTypeErrorMsg
        );

        testBitmapQueryPlan(
                "select min(id2) from test.bitmap_table;",
                Type.OnlyMetricTypeErrorMsg
        );

        testBitmapQueryPlan(
                "select count(*) from test.bitmap_table group by id2;",
                Type.OnlyMetricTypeErrorMsg
        );

        testBitmapQueryPlan(
                "select count(*) from test.bitmap_table where id2 = 1;",
                "type not match, originType=BITMAP, targeType=DOUBLE"
        );

    }

    private static void testHLLQueryPlan(String sql, String result) throws Exception {
        String explainString = UtFrameUtils.getSQLPlanOrErrorMsg(connectContext, "explain " + sql);
        Assert.assertTrue(explainString.contains(result));
    }

    @Test
    public void testHLLTypeQuery() throws Exception {
        testHLLQueryPlan(
                "select * from test.hll_table;",
                Type.OnlyMetricTypeErrorMsg
        );

        testHLLQueryPlan(
                "select count(id2) from test.hll_table;",
                Type.OnlyMetricTypeErrorMsg
        );

        testHLLQueryPlan(
                "select group_concat(id2) from test.hll_table;",
                "group_concat requires first parameter to be of type STRING: group_concat(`id2`)"
        );

        testHLLQueryPlan(
                "select sum(id2) from test.hll_table;",
                "sum requires a numeric parameter: sum(`id2`)"
        );

        testHLLQueryPlan(
                "select avg(id2) from test.hll_table;",
                "avg requires a numeric parameter: avg(`id2`)"
        );

        testHLLQueryPlan(
                "select max(id2) from test.hll_table;",
                Type.OnlyMetricTypeErrorMsg
        );

        testHLLQueryPlan(
                "select min(id2) from test.hll_table;",
                Type.OnlyMetricTypeErrorMsg
        );

        testHLLQueryPlan(
                "select min(id2) from test.hll_table;",
                Type.OnlyMetricTypeErrorMsg
        );

        testHLLQueryPlan(
                "select count(*) from test.hll_table group by id2;",
                Type.OnlyMetricTypeErrorMsg
        );

        testHLLQueryPlan(
                "select count(*) from test.hll_table where id2 = 1",
                "type not match, originType=HLL, targeType=DOUBLE"
        );
    }
}

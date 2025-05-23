/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.operations;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.expressions.ResolvedExpression;
import org.apache.flink.table.expressions.SqlFactory;
import org.apache.flink.table.operations.utils.OperationExpressionsUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Expresses sort operation of rows of the underlying relational operation with given order. It also
 * allows specifying offset and number of rows to fetch from the sorted data set/stream.
 */
@Internal
public class SortQueryOperation implements QueryOperation {

    private static final String INPUT_ALIAS = "$$T_SORT";
    private final List<ResolvedExpression> order;
    private final QueryOperation child;
    private final int offset;
    private final int fetch;

    public SortQueryOperation(List<ResolvedExpression> order, QueryOperation child) {
        this(order, child, -1, -1);
    }

    public SortQueryOperation(
            List<ResolvedExpression> order, QueryOperation child, int offset, int fetch) {
        this.order = order;
        this.child = child;
        this.offset = offset;
        this.fetch = fetch;
    }

    public List<ResolvedExpression> getOrder() {
        return order;
    }

    public QueryOperation getChild() {
        return child;
    }

    public int getOffset() {
        return offset;
    }

    public int getFetch() {
        return fetch;
    }

    @Override
    public ResolvedSchema getResolvedSchema() {
        return child.getResolvedSchema();
    }

    @Override
    public String asSummaryString() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("order", order);
        args.put("offset", offset);
        args.put("fetch", fetch);

        return OperationUtils.formatWithChildren(
                "Sort", args, getChildren(), Operation::asSummaryString);
    }

    @Override
    public String asSerializableString(SqlFactory sqlFactory) {
        final StringBuilder s =
                new StringBuilder(
                        String.format(
                                "SELECT %s FROM (%s\n) %s ORDER BY %s",
                                OperationUtils.formatSelectColumns(
                                        getResolvedSchema(), INPUT_ALIAS),
                                OperationUtils.indent(child.asSerializableString(sqlFactory)),
                                INPUT_ALIAS,
                                order.stream()
                                        .map(
                                                expr ->
                                                        OperationExpressionsUtils
                                                                .scopeReferencesWithAlias(
                                                                        INPUT_ALIAS, expr))
                                        .map(
                                                resolvedExpression ->
                                                        resolvedExpression.asSerializableString(
                                                                sqlFactory))
                                        .collect(Collectors.joining(", "))));

        if (offset >= 0) {
            s.append(" OFFSET ");
            s.append(offset);
            s.append(" ROWS");
        }
        if (fetch >= 0) {
            s.append(" FETCH NEXT ");
            s.append(fetch);
            s.append(" ROWS ONLY");
        }
        return s.toString();
    }

    @Override
    public List<QueryOperation> getChildren() {
        return Collections.singletonList(child);
    }

    @Override
    public <T> T accept(QueryOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}

/**
 * Copyright (c) 2015, STARSCHEMA LTD.
 * All rights reserved.

 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.starschema.clouddb.jdbc.list;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import net.starschema.clouddb.jdbc.Logger;

public class Resolver {
    Connection connection;
    TreeBuilder builder;
    CallContainer container;
    protected Logger logger = Logger.getLogger(this.getClass());

    public Resolver(TreeBuilder builder) {

        this.connection = builder.getConnection();
        this.builder = builder;
        this.container = builder.callContainer;
    }

    /**
     * Gets a sourceTable's all possible columns
     *
     * @param sourceTable
     * @return a List with the columns
     */
    protected List<ColumnCall> parseSrcTableForJokers(
            SourceTable sourceTable) {
        List<ColumnCall> returnlist = new ArrayList<ColumnCall>();
        List<String> columnstrings = this.builder.GetColumns(sourceTable);
        for (String string : columnstrings) {
            ColumnCall columnforreturn = new ColumnCall(null, null,
                    this.builder, string, null);
            returnlist.add(columnforreturn);
        }
        return returnlist;
    }


    protected List<ColumnCall> parseSubQForJokers(SubQuery subQuery) {
        List<ColumnCall> returnlist = new ArrayList<ColumnCall>();
        this.logger.debug("GETTING EXPRESSION");
        Expression expression = subQuery.getSelectStatement().getExpression();
        if (expression != null) {
            this.logger.debug("EXPRESSION is NOT NULL");
            this.logger.debug(expression.toPrettyString());
        }
        List<ColumnCall> columns = expression.getColumns();
        List<FunctionCall> functionCalls = expression.getFunctionCalls();

        if (functionCalls != null) {
            this.logger.debug("HAS FUNCTIONCALLS");
            for (FunctionCall functionCall : functionCalls) {
                returnlist.add(new ColumnCall(new String[]{subQuery.getUniqueId()},
                        null, this.builder, functionCall.getAlias(), functionCall, null));
            }
        }

        if (columns != null) {
            this.logger.debug("HAS COLUMNS");
            for (ColumnCall column : columns) {
                if (column.getAlias() != null) {
                    ColumnCall returncolumn = new ColumnCall(null, null,
                            this.builder, column.getAlias(), column, null);
                    returncolumn.addPrefixtoFront(subQuery.getAlias());

                    returnlist.add(returncolumn);
                } else {
                    ColumnCall returncolumn = new ColumnCall(null, null,
                            this.builder, column.getName(), column, null);

                    returncolumn.addPrefixtoFront(subQuery.getAlias());
                    returnlist.add(returncolumn);
                }
            }
        }
        return returnlist;
    }


}

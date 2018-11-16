package com.blazingdb.calcite.application;

import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParserPos;

import java.util.Arrays;
import java.util.List;

public class SqlSyntaxException extends Exception {
  private static final long serialVersionUID = -1689099602920569510L;

  public SqlSyntaxException(final String queryString,
                            final SqlParseException sqlParseException) {
    super(description(queryString, sqlParseException));
  }

  private static String description(final String queryString,
                                    final SqlParseException sqlParseException) {
    StringBuilder builder = new StringBuilder();
    builder.append("SqlSyntaxException\n\n");

    List<String> queryLines = Arrays.asList(queryString.split("\n"));
    SqlParserPos pos = sqlParseException.getPos();

    // append unaffected lines
    for (int i = 0; i < pos.getLineNum(); i++) {
      builder.append(queryLines.get(i));
      builder.append('\n');
    }

    // append lines with syntax error
    for (int i = 1; i < pos.getColumnNum(); i++) {
      builder.append(' ');
    }
    for (int i = pos.getColumnNum(); i <= pos.getEndColumnNum(); i++) {
      builder.append('^');
    }
    builder.append('\n');

    // append rest of lines
    for (int i = pos.getEndLineNum(); i < queryLines.size(); i++) {
      builder.append(queryLines.get(i));
      builder.append('\n');
    }

    builder.append('\n');
    builder.append(sqlParseException.getMessage());

    return builder.toString();
  }
}
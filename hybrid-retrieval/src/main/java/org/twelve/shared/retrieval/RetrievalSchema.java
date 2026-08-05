package org.twelve.shared.retrieval;

import java.util.regex.Pattern;

/** Validated database identifiers used to parameterize all retrieval SQL. */
public record RetrievalSchema(String schema, String lexicalTable, String vectorTable) {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public static final RetrievalSchema DEFAULT =
            new RetrievalSchema("public", "hybrid_retrieval_lexical", "hybrid_retrieval_vector");

    public RetrievalSchema {
        schema = identifier(schema, "schema");
        lexicalTable = identifier(lexicalTable, "lexicalTable");
        vectorTable = identifier(vectorTable, "vectorTable");
        if (lexicalTable.equals(vectorTable)) {
            throw new IllegalArgumentException("lexical and vector tables must differ");
        }
    }

    public String lexicalQualified() {
        return schema + "." + lexicalTable;
    }

    public String vectorQualified() {
        return schema + "." + vectorTable;
    }

    private static String identifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " is not a safe SQL identifier");
        }
        return value;
    }
}

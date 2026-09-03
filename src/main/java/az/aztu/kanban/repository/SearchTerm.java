package az.aztu.kanban.repository;

/**
 * Builds the LIKE pattern used by the optional free-text filters.
 *
 * The pattern is passed to the query already wrapped in % so the parameter appears in a
 * `LIKE :param` position. That matters on PostgreSQL: with the term concatenated inside
 * the query instead, Hibernate cannot infer the parameter type, binds a null as an
 * untyped parameter, and PostgreSQL fails the whole statement with
 * "function lower(bytea) does not exist". H2 accepts it, so only the real database
 * catches the difference.
 */
public final class SearchTerm {

    private SearchTerm() {
    }

    /** Null when there is nothing to search for, otherwise a lower-cased %pattern%. */
    public static String like(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return "%" + raw.trim().toLowerCase() + "%";
    }
}

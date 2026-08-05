package org.twelve.shared.retrieval;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.twelve.shared.dbops.AtomicDbOps;

import java.util.UUID;

final class TestDatabase {
    private TestDatabase() {}

    static AtomicDbOps create() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate tx =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        return new AtomicDbOps(jdbc, tx, event -> {});
    }
}

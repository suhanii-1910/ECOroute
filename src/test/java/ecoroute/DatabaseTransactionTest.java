package ecoroute;

import ecoroute.db.Database;
import ecoroute.exception.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseTransactionTest {
    private static Connection connection(List<String> calls, boolean failCommit, boolean failRollback) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            calls.add(method.getName());
            if (method.getName().equals("commit") && failCommit) throw new SQLException("commit failed");
            if (method.getName().equals("rollback") && failRollback) throw new SQLException("rollback failed");
            return null;
        });
    }
    @Test void commitsOnlySuccessfulWorkAndClosesConnection() {
        List<String> calls = new ArrayList<>();
        Database database = new Database(() -> connection(calls, false, false));
        assertEquals("saved", database.transaction(c -> "saved"));
        assertEquals(List.of("setTransactionIsolation", "setAutoCommit", "commit", "close"), calls);
    }
    @Test void runtimeValidationFailuresRollbackAndClose() {
        List<String> calls = new ArrayList<>();
        Database database = new Database(() -> connection(calls, false, false));
        InvalidRequestException original = new InvalidRequestException("invalid line");
        assertSame(original, assertThrows(InvalidRequestException.class, () -> database.transaction(c -> { throw original; })));
        assertEquals(List.of("setTransactionIsolation", "setAutoCommit", "rollback", "close"), calls);
    }
    @Test void commitAndRollbackFailuresKeepOriginalCause() {
        List<String> calls = new ArrayList<>();
        Database database = new Database(() -> connection(calls, true, true));
        DatabaseOperationException error = assertThrows(DatabaseOperationException.class, () -> database.transaction(c -> "saved"));
        assertInstanceOf(SQLException.class, error.getCause());
        assertEquals("commit failed", error.getCause().getMessage());
        assertEquals("rollback failed", error.getCause().getSuppressed()[0].getMessage());
        assertEquals("close", calls.get(calls.size() - 1));
    }
    @Test void connectionFailureIsNotAnEmptyResult() {
        SQLException cause = new SQLException("connection refused");
        Database database = new Database(() -> { throw cause; });
        assertSame(cause, assertThrows(DatabaseOperationException.class, () -> database.read(c -> List.of())).getCause());
    }
}

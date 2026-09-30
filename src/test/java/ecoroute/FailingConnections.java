package ecoroute;

import ecoroute.db.ConnectionProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/** Injects a JDBC error into a real transaction, after earlier SQL has succeeded. */
final class FailingConnections {
    private FailingConnections() {}
    static ConnectionProvider onStatement(ConnectionProvider provider, String sqlPrefix, int occurrence) {
        AtomicInteger count = new AtomicInteger();
        return () -> {
            Connection real = provider.open();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement") && ((String) args[0]).startsWith(sqlPrefix)
                        && count.incrementAndGet() == occurrence) throw new SQLException("Injected statement failure", "45000");
                try { return method.invoke(real, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        };
    }
}

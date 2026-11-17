package com.chaosforge.recordcoding.store;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/** 本机 SQLite 单文件；七张表 + schema 版本 + 索引，随 data/ 清理任务排除。 */
public final class Database implements AutoCloseable {
    public static final int SCHEMA_VERSION = 1;
    public static final List<String> TABLES = List.of(
            "review_batch", "evidence_snapshot", "finding", "confirmation",
            "idempotency", "receipt", "audit_event");

    private final Connection connection;

    private Database(Connection connection) { this.connection = connection; }

    public static Database inMemory() throws SQLException { return open("jdbc:sqlite::memory:"); }

    public static Database open(Path file) throws SQLException {
        if (file.getParent() != null) {
            try { java.nio.file.Files.createDirectories(file.getParent()); }
            catch (java.io.IOException e) { throw new SQLException("无法创建数据目录", e); }
        }
        return open("jdbc:sqlite:" + file.toAbsolutePath());
    }

    private static Database open(String url) throws SQLException {
        Connection c = DriverManager.getConnection(url);
        Database db = new Database(c);
        db.migrate();
        return db;
    }

    public Connection connection() { return connection; }

    public void migrate() throws SQLException {
        Jdbc.execute(connection, "CREATE TABLE IF NOT EXISTS schema_meta (k TEXT PRIMARY KEY, v INTEGER)");
        Jdbc.update(connection, "INSERT OR REPLACE INTO schema_meta(k,v) VALUES('schema_version',?)", SCHEMA_VERSION);
        Jdbc.execute(connection, "CREATE TABLE IF NOT EXISTS review_batch ("
                + "batch_id TEXT PRIMARY KEY, visit_no TEXT NOT NULL, state TEXT NOT NULL, "
                + "evidence_hash TEXT, created_at TEXT NOT NULL)");
        Jdbc.execute(connection, "CREATE TABLE IF NOT EXISTS evidence_snapshot ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, batch_id TEXT NOT NULL, visit_no TEXT NOT NULL, "
                + "evidence_id TEXT NOT NULL, source_system TEXT, field TEXT, field_locator TEXT, "
                + "value_text TEXT, row_hash TEXT, content_hash TEXT)");
        Jdbc.execute(connection, "CREATE TABLE IF NOT EXISTS finding ("
                + "finding_id TEXT PRIMARY KEY, batch_id TEXT NOT NULL, home_code TEXT, verdict TEXT, "
                + "attribution TEXT, confidence REAL, evidence_refs TEXT)");
        Jdbc.execute(connection, "CREATE TABLE IF NOT EXISTS confirmation ("
                + "finding_id TEXT PRIMARY KEY, batch_id TEXT NOT NULL, confirmed_by TEXT NOT NULL, "
                + "confirmed_at TEXT NOT NULL, decision TEXT NOT NULL, reject_reason TEXT)");
        Jdbc.execute(connection, "CREATE TABLE IF NOT EXISTS idempotency ("
                + "idem_key TEXT PRIMARY KEY, batch_id TEXT NOT NULL, created_at TEXT NOT NULL, "
                + "receipt_id TEXT)");
        Jdbc.execute(connection, "CREATE TABLE IF NOT EXISTS receipt ("
                + "receipt_id TEXT PRIMARY KEY, batch_id TEXT NOT NULL, record_id TEXT, "
                + "received_at TEXT, evidence_hash TEXT)");
        Jdbc.execute(connection, "CREATE TABLE IF NOT EXISTS audit_event ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, batch_id TEXT NOT NULL, event_type TEXT NOT NULL, "
                + "event_time TEXT NOT NULL, actor TEXT, payload_json TEXT NOT NULL)");
        createIndexes();
    }

    private void createIndexes() throws SQLException {
        Jdbc.execute(connection, "CREATE INDEX IF NOT EXISTS idx_batch_visit ON review_batch(visit_no, batch_id)");
        Jdbc.execute(connection, "CREATE INDEX IF NOT EXISTS idx_snapshot_batch ON evidence_snapshot(batch_id)");
        Jdbc.execute(connection, "CREATE INDEX IF NOT EXISTS idx_snapshot_hash ON evidence_snapshot(row_hash)");
        Jdbc.execute(connection, "CREATE INDEX IF NOT EXISTS idx_finding_batch ON finding(batch_id)");
        Jdbc.execute(connection, "CREATE INDEX IF NOT EXISTS idx_confirmation_batch ON confirmation(batch_id)");
        Jdbc.execute(connection, "CREATE INDEX IF NOT EXISTS idx_idempotency_batch ON idempotency(batch_id)");
        Jdbc.execute(connection, "CREATE INDEX IF NOT EXISTS idx_receipt_batch ON receipt(batch_id)");
        Jdbc.execute(connection, "CREATE INDEX IF NOT EXISTS idx_audit_batch ON audit_event(batch_id, id)");
    }

    public int schemaVersion() throws SQLException {
        String v = Jdbc.scalar(connection, "SELECT v FROM schema_meta WHERE k='schema_version'");
        return v == null ? 0 : Integer.parseInt(v);
    }

    public boolean tableExists(String name) throws SQLException {
        return Jdbc.scalar(connection, "SELECT name FROM sqlite_master WHERE type='table' AND name=?", name) != null;
    }

    /** 索引命中自检：EXPLAIN QUERY PLAN 输出是否含指定索引名。 */
    public boolean usesIndex(String sql, String indexName) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map<String, Object> row : Jdbc.query(connection, "EXPLAIN QUERY PLAN " + sql)) {
            sb.append(row.values()).append("\n");
        }
        return sb.toString().contains(indexName);
    }

    @Override public void close() throws SQLException { connection.close(); }
}

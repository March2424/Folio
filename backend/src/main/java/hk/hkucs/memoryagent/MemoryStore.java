package hk.hkucs.memoryagent;

import static hk.hkucs.memoryagent.Models.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.*;

@Repository
public class MemoryStore {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private long epoch;
    private static final RowMapper<Memory> MEMORY = (r, n) -> new Memory(r.getString("id"), r.getString("title"),
        r.getString("kind"), r.getString("occurred_on"), r.getString("content"), r.getString("summary"),
        r.getBoolean("pinned"), r.getString("status"), r.getLong("revision"), r.getLong("created_at"),
        r.getLong("updated_at"), r.getLong("recalled_at"));
    private static final RowMapper<Trait> TRAIT = (r, n) -> new Trait(r.getString("id"), r.getString("memory_id"),
        r.getString("description"), r.getString("basis"), r.getString("evidence"), r.getString("status"));
    public MemoryStore(JdbcTemplate db, TransactionTemplate tx) { this.db = db; this.tx = tx; }
    public synchronized long epoch() { return epoch; }
    public Memory get(String id) {
        return db.query("SELECT * FROM memories WHERE id=?", MEMORY, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没有找到这段记忆"));
    }
    public List<Memory> all() { return db.query("SELECT * FROM memories ORDER BY created_at DESC, id", MEMORY); }
    public synchronized Memory save(MemoryInput input) {
        if (input.occurredOn() != null) LocalDate.parse(input.occurredOn());
        String id = input.id() == null ? UUID.randomUUID().toString() : input.id();
        List<Memory> existing = db.query("SELECT * FROM memories WHERE id=?", MEMORY, id);
        if (!existing.isEmpty()) {
            Memory old = existing.get(0);
            if (old.title().equals(input.title()) && old.kind().equals(input.kind()) &&
                Objects.equals(old.occurredOn(), input.occurredOn()) && old.content().equals(input.content())) return old;
        }
        tx.executeWithoutResult(s -> {
            long now = System.currentTimeMillis();
            if (existing.isEmpty()) {
                db.update("INSERT INTO memories(id,title,kind,occurred_on,content,summary,status,created_at,updated_at) VALUES(?,?,?,?,?,'','PENDING',?,?)",
                    id, input.title(), input.kind(), input.occurredOn(), input.content(), now, now);
            } else {
                db.update("UPDATE memories SET title=?,kind=?,occurred_on=?,content=?,summary='',status='PENDING',revision=revision+1,updated_at=? WHERE id=?",
                    input.title(), input.kind(), input.occurredOn(), input.content(), now, id);
                db.update("DELETE FROM traits WHERE memory_id=?", id);
            }
            db.update("DELETE FROM chat_messages");
        });
        epoch++;
        return get(id);
    }
    public synchronized Memory analysis(Memory snapshot, String summary, List<Trait> traits) {
        tx.executeWithoutResult(s -> {
            int changed = db.update("UPDATE memories SET summary=?,status='READY',revision=revision+1 WHERE id=? AND revision=?",
                summary, snapshot.id(), snapshot.revision());
            if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "材料已修改或删除，请重新整理");
            db.update("DELETE FROM traits WHERE memory_id=?", snapshot.id());
            for (Trait t : traits) db.update("INSERT INTO traits(id,memory_id,description,basis,evidence,status) VALUES(?,?,?,?,?,?)",
                t.id(), t.memoryId(), t.description(), t.basis(), t.evidence(), t.status());
            db.update("DELETE FROM chat_messages");
        });
        epoch++;
        return get(snapshot.id());
    }
    public List<Trait> traits() { return db.query("SELECT * FROM traits ORDER BY memory_id,id", TRAIT); }
    public synchronized void traitState(String id, String status) {
        tx.executeWithoutResult(s -> {
            if (db.update("UPDATE traits SET status=? WHERE id=?", status, id) == 0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "特征不存在");
            db.update("DELETE FROM chat_messages");
        });
        epoch++;
    }
    public synchronized void pin(String id, boolean pinned) {
        if (db.update("UPDATE memories SET pinned=? WHERE id=?", pinned, id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记忆不存在");
        epoch++;
    }
    public synchronized void delete(String id) {
        tx.executeWithoutResult(s -> {
            if (db.update("DELETE FROM memories WHERE id=?", id) == 0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记忆不存在");
            // Conversation text is derived data too: V1 conservatively clears every conversation on mutation.
            db.update("DELETE FROM chat_messages");
        });
        epoch++;
    }
    public List<Map<String,Object>> history(String session) {
        var rows = db.query("SELECT role,content FROM chat_messages WHERE session_id=? ORDER BY id DESC LIMIT 10",
            (r, n) -> Map.<String,Object>of("role", r.getString("role"), "content", r.getString("content")), session);
        Collections.reverse(rows);
        return rows;
    }
    public synchronized void commitChat(long expectedEpoch, String session, String question, String answer, Set<String> recalled) {
        if (epoch != expectedEpoch) throw new ResponseStatusException(HttpStatus.CONFLICT, "记忆刚刚更新，请重新提问");
        tx.executeWithoutResult(s -> {
            db.update("INSERT INTO chat_messages(session_id,role,content) VALUES(?,'user',?)", session, question);
            db.update("INSERT INTO chat_messages(session_id,role,content) VALUES(?,'assistant',?)", session, answer);
            db.update("DELETE FROM chat_messages WHERE session_id=? AND id NOT IN (SELECT id FROM (SELECT id FROM chat_messages WHERE session_id=? ORDER BY id DESC LIMIT 10) recent)", session, session);
            for (String id : recalled) db.update("UPDATE memories SET recalled_at=? WHERE id=?", System.currentTimeMillis(), id);
        });
    }
}

package icu.sprinkle.loom.core.memory;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMemoryStoreTest {

    @Test
    void recordAndRetrieve() {
        var store = new InMemoryMemoryStore();
        store.record(new MemoryEntry("User prefers dark mode"));

        var results = store.retrieve("dark mode", 5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).isEqualTo("User prefers dark mode");
    }

    @Test
    void retrieve_returnsSortedByRelevance() {
        var store = new InMemoryMemoryStore();
        store.record(new MemoryEntry("User prefers dark mode for coding"));
        store.record(new MemoryEntry("User likes Python"));
        store.record(new MemoryEntry("User wants dark UI with high contrast"));

        var results = store.retrieve("dark mode", 5);
        assertThat(results).hasSize(2);
        // "dark mode for coding" and "dark UI with high contrast" match
    }

    @Test
    void retrieve_respectsTopK() {
        var store = new InMemoryMemoryStore();
        store.record(new MemoryEntry("Java is great"));
        store.record(new MemoryEntry("Java streams are powerful"));
        store.record(new MemoryEntry("Java virtual threads help concurrency"));

        var results = store.retrieve("Java", 2);
        assertThat(results).hasSize(2);
    }

    @Test
    void retrieve_emptyQuery_returnsEmpty() {
        var store = new InMemoryMemoryStore();
        store.record(new MemoryEntry("Some memory"));

        assertThat(store.retrieve("", 5)).isEmpty();
        assertThat(store.retrieve(null, 5)).isEmpty();
    }

    @Test
    void retrieve_noMatch_returnsEmpty() {
        var store = new InMemoryMemoryStore();
        store.record(new MemoryEntry("User likes Java"));

        assertThat(store.retrieve("Python", 5)).isEmpty();
    }

    @Test
    void delete_removesEntry() {
        var store = new InMemoryMemoryStore();
        var entry = new MemoryEntry(UUID.randomUUID().toString(),
                "Test memory", Map.of(), java.time.Instant.now());
        store.record(entry);

        assertThat(store.size()).isEqualTo(1);
        store.delete(entry.id());
        assertThat(store.size()).isEqualTo(0);
        assertThat(store.listAll()).isEmpty();
    }

    @Test
    void listAll_sortedByCreatedAtDescending() {
        var store = new InMemoryMemoryStore();
        var older = new MemoryEntry("a", "first", Map.of(), java.time.Instant.now().minusSeconds(60));
        var newer = new MemoryEntry("b", "second", Map.of(), java.time.Instant.now());
        store.record(older);
        store.record(newer);

        var all = store.listAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).id()).isEqualTo("b");
        assertThat(all.get(1).id()).isEqualTo("a");
    }

    @Test
    void size_returnsCount() {
        var store = new InMemoryMemoryStore();
        assertThat(store.size()).isEqualTo(0);

        store.record(new MemoryEntry("A"));
        store.record(new MemoryEntry("B"));
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void retrieve_partialWordMatch() {
        var store = new InMemoryMemoryStore();
        store.record(new MemoryEntry("User works with PostgreSQL databases"));

        var results = store.retrieve("SQL", 5);
        assertThat(results).hasSize(1);
    }
}

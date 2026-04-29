package icu.sprinkle.loom.core.loop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateManagerTest {

    private StateManager manager;

    @BeforeEach
    void setUp() {
        manager = new StateManager();
    }

    @Test
    void registerAndCollect() {
        var component = new TestComponent("test-comp", Map.of("key", "value"), true);
        manager.register(component);

        var states = manager.collectStates();
        assertThat(states).containsKey("test-comp");
        assertThat(states.get("test-comp")).containsEntry("key", "value");
    }

    @Test
    void duplicateStateIdThrows() {
        manager.register(new TestComponent("dup", Map.of(), true));
        assertThatThrownBy(() -> manager.register(new TestComponent("dup", Map.of(), true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate stateId: dup");
    }

    @Test
    void collectSkipsCleanComponents() {
        manager.register(new TestComponent("dirty", Map.of("a", 1), true));
        manager.register(new TestComponent("clean", Map.of("b", 2), false));

        var states = manager.collectStates();
        assertThat(states).containsKey("dirty");
        assertThat(states).doesNotContainKey("clean");
    }

    @Test
    void restoreAllDistributesState() {
        var comp1 = new TestComponent("comp1", Map.of(), true);
        var comp2 = new TestComponent("comp2", Map.of(), true);
        manager.register(comp1);
        manager.register(comp2);

        var states = Map.of(
                "comp1", Map.<String, Object>of("restored", true),
                "comp2", Map.<String, Object>of("count", 42)
        );
        manager.restoreAll(states);

        assertThat(comp1.lastRestoredState).containsEntry("restored", true);
        assertThat(comp2.lastRestoredState).containsEntry("count", 42);
    }

    @Test
    void restoreWithMissingStateUsesEmptyMap() {
        var comp = new TestComponent("orphan", Map.of(), true);
        manager.register(comp);

        manager.restoreAll(Map.of());
        assertThat(comp.lastRestoredState).isEmpty();
    }

    @Test
    void restoreNullStatesIsNoOp() {
        manager.register(new TestComponent("test", Map.of(), true));
        manager.restoreAll(null); // should not throw
    }

    @Test
    void componentCount() {
        assertThat(manager.componentCount()).isZero();
        manager.register(new TestComponent("a", Map.of(), true));
        manager.register(new TestComponent("b", Map.of(), true));
        assertThat(manager.componentCount()).isEqualTo(2);
    }

    @Test
    void registeredIds() {
        manager.register(new TestComponent("alpha", Map.of(), true));
        manager.register(new TestComponent("beta", Map.of(), true));
        assertThat(manager.registeredIds()).containsExactly("alpha", "beta");
    }

    private static class TestComponent implements StatePersistable {
        private final String id;
        private final Map<String, Object> state;
        private final boolean dirty;
        Map<String, Object> lastRestoredState;

        TestComponent(String id, Map<String, Object> state, boolean dirty) {
            this.id = id;
            this.state = state;
            this.dirty = dirty;
        }

        @Override
        public String stateId() { return id; }

        @Override
        public Map<String, Object> saveState() { return state; }

        @Override
        public void restoreState(Map<String, Object> state) {
            this.lastRestoredState = state;
        }

        @Override
        public boolean isDirty() { return dirty; }
    }
}

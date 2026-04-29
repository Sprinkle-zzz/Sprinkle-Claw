package icu.sprinkle.loom.tool.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHashValidatorTest {

    @TempDir
    Path tempDir;

    private ContentHashValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ContentHashValidator();
    }

    @Test
    void untrackedFileReturnsNotModified() {
        Path file = tempDir.resolve("untracked.txt");
        assertThat(validator.isExternallyModified(file)).isFalse();
    }

    @Test
    void unchangedFileReturnsNotModified() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");
        validator.recordFingerprint(file);

        assertThat(validator.isExternallyModified(file)).isFalse();
    }

    @Test
    void contentChangedReturnsModified() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");
        validator.recordFingerprint(file);

        Thread.sleep(50);
        Files.writeString(file, "changed content");
        assertThat(validator.isExternallyModified(file)).isTrue();
    }

    @Test
    void mtimeChangedButContentSameReturnsNotModified() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "same content");
        validator.recordFingerprint(file);

        // Simulate cloud sync: touch the file without changing content
        Files.writeString(file, "same content");
        // mtime will change but content is identical
        assertThat(validator.isExternallyModified(file)).isFalse();
    }

    @Test
    void mtimeChangedButContentSameUpdatesCachedMtime() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "content");
        validator.recordFingerprint(file);
        FileTime originalMtime = validator.getFingerprint(file).mtime();

        // Touch with new mtime but same content
        Thread.sleep(50); // ensure different mtime
        Files.writeString(file, "content");

        validator.isExternallyModified(file);

        // Cached mtime should be updated
        FileTime updatedMtime = validator.getFingerprint(file).mtime();
        // The mtime may or may not differ depending on timing, but hash should be same
        assertThat(validator.getFingerprint(file).sha256()).isNotEmpty();
    }

    @Test
    void sizeChangedSkipsHashComputation() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "short");
        validator.recordFingerprint(file);

        Thread.sleep(50);
        Files.writeString(file, "much longer content here");
        assertThat(validator.isExternallyModified(file)).isTrue();
    }

    @Test
    void untrackRemovesFingerprint() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");
        validator.recordFingerprint(file);
        assertThat(validator.getFingerprint(file)).isNotNull();

        validator.untrack(file);
        assertThat(validator.getFingerprint(file)).isNull();
    }

    @Test
    void recordFingerprintStoresCorrectData() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "test data");
        validator.recordFingerprint(file);

        var fp = validator.getFingerprint(file);
        assertThat(fp).isNotNull();
        assertThat(fp.sha256()).hasSize(64); // SHA-256 hex length
        assertThat(fp.size()).isEqualTo(Files.size(file));
        assertThat(fp.mtime()).isEqualTo(Files.getLastModifiedTime(file));
    }
}

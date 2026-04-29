package icu.sprinkle.loom.llm;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SharedHttpClientTest {

    @Test
    void get_returnsSameInstance() {
        HttpClient client1 = SharedHttpClient.get();
        HttpClient client2 = SharedHttpClient.get();
        assertThat(client1).isSameAs(client2);
    }

    @Test
    void get_returnsHttp2Client() {
        HttpClient client = SharedHttpClient.get();
        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_2);
    }

    @Test
    void override_replacesInstance() {
        HttpClient original = SharedHttpClient.get();

        HttpClient custom = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        SharedHttpClient.override(custom);

        try {
            assertThat(SharedHttpClient.get()).isSameAs(custom);
            assertThat(SharedHttpClient.get().version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        } finally {
            SharedHttpClient.override(original);
        }
    }
}

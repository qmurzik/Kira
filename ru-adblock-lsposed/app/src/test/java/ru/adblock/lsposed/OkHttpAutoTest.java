package ru.adblock.lsposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * Проверка структурного поиска OkHttp на настоящем OkHttp 4 — без использования имён классов
 * okhttp3/okio в самом модуле (как после обфускации R8).
 */
public class OkHttpAutoTest {
    private MockWebServer server;

    @Before
    public void setUp() throws Exception {
        Config cfg = new Config();
        JsonFilter.init(Rules.load(null, cfg));
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private static final String FEED = "{\"items\":[{\"id\":1},{\"id\":2,\"erid\":\"2Vtzqx\"},"
            + "{\"id\":3,\"isAd\":true},{\"id\":4}]}";

    @Test
    public void findsChainMethodInStack() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));
        AtomicReference<Method> found = new AtomicReference<>();
        // Сетевой интерцептор вызывается глубоко в цепочке — как DNS/подключение сокета.
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(chain.request()))
                .addNetworkInterceptor(chain -> {
                    found.set(OkHttpAuto.findChainMethod(new Throwable().getStackTrace(),
                            getClass().getClassLoader()));
                    return chain.proceed(chain.request());
                })
                .build();
        client.newCall(new Request.Builder().url(server.url("/feed")).build()).execute().close();
        assertNotNull("proceed не найден по стеку", found.get());
        assertEquals("proceed", found.get().getName());
        assertEquals(Request.class, found.get().getParameterTypes()[0]);
    }

    @Test
    public void filtersJsonBody() throws Exception {
        server.enqueue(new MockResponse().setBody(FEED)
                .setHeader("Content-Type", "application/json; charset=utf-8"));
        String body = call("/api/feed", true);
        JSONArray items = new JSONObject(body).getJSONArray("items");
        assertEquals(2, items.length());
        assertEquals(1, items.getJSONObject(0).getInt("id"));
        assertEquals(4, items.getJSONObject(1).getInt("id"));
    }

    @Test
    public void filtersGzipBody() throws Exception {
        okio.Buffer gz = new okio.Buffer();
        try (okio.BufferedSink sink = okio.Okio.buffer(new okio.GzipSink(gz))) {
            sink.writeUtf8(FEED);
        }
        server.enqueue(new MockResponse().setBody(gz)
                .setHeader("Content-Encoding", "gzip")
                .setHeader("Content-Type", "application/json"));
        String body = call("/api/feed", true);
        assertEquals(2, new JSONObject(body).getJSONArray("items").length());
    }

    @Test
    public void keepsNonJsonAndPaymentUrls() throws Exception {
        server.enqueue(new MockResponse().setBody(FEED).setHeader("Content-Type", "text/html"));
        assertEquals(FEED, call("/page", false));
        server.enqueue(new MockResponse().setBody(FEED).setHeader("Content-Type", "application/json"));
        assertEquals(FEED, call("/api/payment/methods", false));
        server.enqueue(new MockResponse().setBody("{\"items\":[{\"id\":1}]}")
                .setHeader("Content-Type", "application/json"));
        assertEquals("{\"items\":[{\"id\":1}]}", call("/api/feed", false));
    }

    private String call(String path, boolean expectReplaced) throws Exception {
        AtomicBoolean replaced = new AtomicBoolean();
        Interceptor outer = chain -> {
            Response r = chain.proceed(chain.request());
            try {
                replaced.set(OkHttpAuto.process(chain.request(), r));
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            return r;
        };
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(outer).build();
        try (Response r = client.newCall(new Request.Builder().url(server.url(path)).build()).execute()) {
            String s = r.body().string();
            if (expectReplaced) assertTrue("тело не подменено", replaced.get());
            else assertFalse("тело не должно меняться", replaced.get());
            return s;
        }
    }
}

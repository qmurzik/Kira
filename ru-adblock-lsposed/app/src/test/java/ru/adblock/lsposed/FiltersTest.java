package ru.adblock.lsposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

public class FiltersTest {
    private static Rules rules;

    @BeforeClass
    public static void setUp() {
        Config cfg = new Config();
        cfg.extraDomains = "ads.example.org\n~adserv";
        cfg.extraJsonKeys = "flag:isBanner\nkeep:offerId";
        rules = Rules.load(null, cfg); // встроенный список
        JsonFilter.init(rules);
    }

    @Test
    public void domains() {
        assertTrue(rules.isDomainBlocked("an.yandex.ru"));
        assertTrue(rules.isDomainBlocked("AN.YANDEX.RU."));
        assertTrue(rules.isDomainBlocked("ads.adfox.ru"));
        assertTrue(rules.isDomainBlocked("x.mobile.yandexadexchange.net"));
        assertTrue(rules.isDomainBlocked("cdn.mytarget-static.com"));
        assertTrue(rules.isDomainBlocked("ads.example.org"));
        assertTrue(rules.isDomainBlocked("my-adserv.net"));
        assertFalse(rules.isDomainBlocked("yandex.ru"));
        assertFalse(rules.isDomainBlocked("api.avito.ru"));
        assertFalse(rules.isDomainBlocked("notadfox.ru"));
        assertFalse(rules.isDomainBlocked("127.0.0.1"));
        assertFalse(rules.isDomainBlocked("::1"));
    }

    @Test
    public void jsonRemovesOnlyArrayElements() throws Exception {
        String json = "{\"erid\":\"root-stays\",\"items\":["
                + "{\"id\":1,\"title\":\"Товар\"},"
                + "{\"id\":2,\"erid\":\"2SDnjdXyz\"},"
                + "{\"id\":3,\"isAd\":true},"
                + "{\"id\":4,\"isAd\":false},"
                + "{\"id\":5,\"payload\":{\"advertErid\":\"abc\"}},"
                + "{\"id\":6,\"promoted\":\"true\"},"
                + "{\"id\":7,\"erid\":\"\"},"
                + "{\"id\":8,\"isBanner\":true},"
                + "{\"id\":9,\"isPromo\":true,\"subscription\":{}},"
                + "{\"id\":10,\"sponsored\":true,\"offerId\":5}"
                + "]}";
        String out = JsonFilter.filterString(json);
        JSONObject o = new JSONObject(out);
        assertEquals("root-stays", o.getString("erid"));
        JSONArray items = o.getJSONArray("items");
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < items.length(); i++) ids.append(items.getJSONObject(i).getInt("id")).append(',');
        assertEquals("1,4,7,9,10,", ids.toString());
    }

    @Test
    public void jsonNoChange() {
        assertNull(JsonFilter.filterString("{\"items\":[{\"id\":1},{\"id\":2}]}"));
        assertNull(JsonFilter.filterString("{\"items\":[{\"isAd\":false}]}"));
        assertNull(JsonFilter.filterString("not json isAd\""));
    }

    @Test
    public void jsonNested() {
        String out = JsonFilter.filterString(
                "[{\"section\":{\"items\":[{\"id\":1},{\"id\":2,\"ad_erid\":\"x\"}]}}]");
        assertEquals("[{\"section\":{\"items\":[{\"id\":1}]}}]", out);
    }

    @Test
    public void adText() {
        assertTrue(AdText.matches("Реклама"));
        assertTrue(AdText.matches(" реклама · ООО Ромашка"));
        assertTrue(AdText.matches("Реклама ·"));
        assertTrue(AdText.matches("erid: 2SDnjdXyz"));
        assertTrue(AdText.matches("Спонсорский"));
        assertTrue(AdText.matches("Промо"));
        assertFalse(AdText.matches("Промокод"));
        assertFalse(AdText.matches("Рекламация"));
        assertFalse(AdText.matches("Реклама на Авито: как разместить объявление быстро"));
        assertFalse(AdText.matches("Смартфон"));
        assertFalse(AdText.matches(null));
    }

    @Test
    public void exclusions() {
        assertTrue(Exclusions.isExcluded("ru.ozon.fintech.finance", "ru.ozon.fintech.finance"));
        assertTrue(Exclusions.isExcluded("com.yandex.bank", "com.yandex.bank"));
        assertTrue(Exclusions.isExcluded("ru.yandex.taxi", "ru.yandex.taxi:yandexpay"));
        assertTrue(Exclusions.isExcluded("com.wildberries.ru", "com.wildberries.ru:payments"));
        assertFalse(Exclusions.isExcluded("ru.ozon.app.android", "ru.ozon.app.android"));
        assertFalse(Exclusions.isExcluded("com.wildberries.ru", "com.wildberries.ru"));
        assertFalse(Exclusions.isExcluded("ru.yandex.music", "ru.yandex.music:player"));
        assertFalse(Exclusions.isExcluded("com.yandex.browser", "com.yandex.browser"));
    }

    @Test
    public void bundledResourceFiles() throws Exception {
        java.nio.file.Path raw = java.nio.file.Paths.get("src/main/res/raw");
        Rules r = Rules.fromLines(
                java.nio.file.Files.readAllLines(raw.resolve("ad_domains.txt")),
                java.nio.file.Files.readAllLines(raw.resolve("json_ad_keys.txt")),
                new Config());
        assertTrue(r.domains.size() > 20);
        assertTrue(r.isDomainBlocked("banners.adfox.ru"));
        assertTrue(r.isDomainBlocked("googleads.g.doubleclick.net"));
        assertTrue(r.isDomainBlocked("ad.mail.ru"));
        assertFalse(r.isDomainBlocked("report.appmetrica.yandex.net"));
        assertFalse(r.isDomainBlocked("api.ozon.ru"));
        assertTrue(r.jsonMarkers.contains("erid"));
        assertTrue(r.jsonFlags.contains("isadvert"));
        assertTrue(r.jsonKeep.contains("purchasetoken"));
    }
}

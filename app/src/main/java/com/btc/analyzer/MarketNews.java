package com.btc.analyzer;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Keyless public RSS news headlines. No AI inference or prediction. */
public final class MarketNews {
    private MarketNews() {}
    public static class Item {
        public String title = "", link = "", source = "", published = "";
        public long publishedMillis = 0;
        public String category = "중립 / 영향 불명";
    }
    public static ArrayList<Item> load(OkHttpClient client) throws Exception {
        String query = "(bitcoin OR BTC OR cryptocurrency) (ETF OR Federal Reserve OR FOMC OR CPI OR regulation OR exchange OR hack) when:2d";
        String url = "https://news.google.com/rss/search?q=" + URLEncoder.encode(query, "UTF-8") + "&hl=en-US&gl=US&ceid=US:en";
        Request request = new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 BybitFuturesAnalyzer/2.4").build();
        ArrayList<Item> results = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new Exception("뉴스 서버 HTTP " + response.code());
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(response.body().byteStream(), "UTF-8");
            int type = parser.getEventType();
            boolean inItem = false;
            Item item = null;
            while (type != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("item".equals(tag)) { inItem=true; item = new Item(); }
                    else if (inItem && item != null && ("title".equals(tag) || "link".equals(tag) || "pubDate".equals(tag) || "source".equals(tag))) {
                        String value = parser.nextText();
                        switch(tag) {
                            case "title": item.title = value.trim(); break;
                            case "link": item.link = value.trim(); break;
                            case "pubDate": item.published = value.trim(); break;
                            case "source": item.source = value.trim(); break;
                        }
                    }
                } else if (type == XmlPullParser.END_TAG && "item".equals(parser.getName())) {
                    if (item != null && !item.title.isEmpty() && item.link.startsWith("https://") && seen.add(item.title.toLowerCase(Locale.US))) {
                        item.category = classify(item.title);
                        item.publishedMillis = parseDate(item.published);
                        results.add(item);
                    }
                    inItem = false;
                }
                type = parser.next();
            }
        }
        results.sort((a,b) -> Long.compare(b.publishedMillis,a.publishedMillis));
        if (results.size()>30) return new ArrayList<>(results.subList(0,30));
        return results;
    }
    private static long parseDate(String s) {
        try { SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",Locale.US); Date d=f.parse(s);return d==null?0:d.getTime(); }
        catch(Exception e) {return 0;}
    }
    public static String timeLabel(Item item) {
        if(item.publishedMillis==0) return item.published;
        return new SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(new Date(item.publishedMillis));
    }
    public static String classify(String title) {
        String t=title.toLowerCase(Locale.US);
        // Simple keyword tags only; never a trading direction or probability.
        if (has(t,"hack","hacked","breach","exploit","liquidation","crash","ban","lawsuit","sell-off","outflows")) return "위험 관련 키워드";
        if (has(t,"approval","approved","inflows","rally","adoption","record high")) return "긍정 관련 키워드";
        if (has(t,"cpi","fomc","federal reserve","rate decision","interest rate","jobs report","inflation")) return "거시경제 · 변동성 주의";
        return "영향 미확인";
    }
    private static boolean has(String s,String... words){ for(String w:words) if(s.contains(w)) return true;return false; }
}

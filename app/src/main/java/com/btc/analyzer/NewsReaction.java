package com.btc.analyzer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** BTC price changes around publication time; correlation NOT causation. */
public final class NewsReaction {
    private NewsReaction(){}
    static class Minute {
        final long start; final double open,close;
        Minute(long t,double o,double c){start=t;open=o;close=c;}
    }
    private static ArrayList<Minute> get(OkHttpClient client)throws Exception{
        Request req=new Request.Builder().url("https://api.bybit.com/v5/market/kline?category=linear&symbol=BTCUSDT&interval=1&limit=500").build();
        try(Response resp=client.newCall(req).execute()){
            if(!resp.isSuccessful() || resp.body()==null)throw new IOException("가격 HTTP "+resp.code());
            JSONObject o=new JSONObject(resp.body().string());
            if(o.optInt("retCode",-1)!=0)throw new IOException("BTC 가격 조회 불가");
            JSONArray raw=o.getJSONObject("result").getJSONArray("list");
            ArrayList<Minute> out=new ArrayList<>();
            for(int i=raw.length()-1;i>=0;i--){JSONArray a=raw.getJSONArray(i);out.add(new Minute(a.getLong(0),a.getDouble(1),a.getDouble(4)));}
            return out;
        }
    }
    private static String window(ArrayList<Minute> bars,long timestamp,int minutes){
        if(bars.isEmpty())return "--";
        long anchor=(timestamp/60000L)*60000L, target=anchor+minutes*60000L;
        Minute start=null,end=null;
        for(Minute c:bars){if(c.start==anchor)start=c;if(c.start==target)end=c;}
        // Only closed target candles, and ensure start candle exists.
        if(start==null||end==null||end.start+60000L>System.currentTimeMillis()||start.open<=0)return "대기/조회 범위 밖";
        return String.format(Locale.US,"%+.2f%%",(end.close/start.open-1)*100);
    }
    public static String latestReaction(OkHttpClient client,ArrayList<MarketNews.Item> items)throws Exception{
        long now=System.currentTimeMillis();
        MarketNews.Item recent=null;
        for(MarketNews.Item item:items)if(item.publishedMillis>now-4*3600000L && item.publishedMillis<=now){recent=item;break;}
        if(recent==null)return "최근 4시간 내 발행 시각 확인 가능한 기사 없음";
        ArrayList<Minute> bars=get(client);
        return "최근 기사 ("+MarketNews.timeLabel(recent)+") 발행 시각 기준 BTC 변동: "
            +"1분 "+window(bars,recent.publishedMillis,1)+" / "
            +"5분 "+window(bars,recent.publishedMillis,5)+" / "
            +"15분 "+window(bars,recent.publishedMillis,15)
            +"\n※ 발행 시각 이후 가격 변동이며 인과관계나 뉴스 영향 점수가 아닙니다.";
    }
}

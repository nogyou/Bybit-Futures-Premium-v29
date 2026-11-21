package com.btc.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import okhttp3.OkHttpClient;

public class NewsActivity extends Activity {
    private static final int BG=0xff0b1220, PANEL=0xff172439, MUTED=0xffa6b8cf;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final OkHttpClient client=new OkHttpClient();
    private LinearLayout articles;
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(18,16,18,24);scroll.addView(root);
        TextView title=txt("시장 뉴스 · BTC 영향 모니터",21,Color.WHITE);root.addView(title);
        root.addView(txt("Google 뉴스 RSS · 기사 지연 가능 · 제목 키워드 분류이며 AI 예측이나 매매 신호가 아닙니다",12,MUTED));
        Button refresh=new Button(this); refresh.setText("뉴스 새로고침");refresh.setOnClickListener(v->refresh());root.addView(refresh);
        status=txt("뉴스 수신 대기",12,MUTED);root.addView(status);
        articles=new LinearLayout(this);articles.setOrientation(1);root.addView(articles);
        setContentView(scroll);refresh();
    }
    private TextView txt(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setPadding(8,10,8,10);return t;}
    private void refresh(){
        status.setText("기사 조회 중…");
        worker.execute(()->{
            try{
                ArrayList<MarketNews.Item> results=MarketNews.load(client);
                runOnUiThread(()->{
                    if(isFinishing())return;
                    articles.removeAllViews();
                    status.setText("수신 " + results.size() + "건 · 현재 조회 결과 (실시간 초단위 보장 안 됨)");
                    for(MarketNews.Item item:results){
                        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(12,8,12,10);
                        card.setBackgroundColor(PANEL);
                        card.addView(txt(item.category+" · "+MarketNews.timeLabel(item),12,0xfffacc15));
                        card.addView(txt(item.title,15,Color.WHITE));
                        card.addView(txt(item.source.isEmpty()?"원문 보기 ↗":item.source+" · 원문 보기 ↗",12,MUTED));
                        card.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(item.link)));}catch(Exception ignored){}});
                        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=10;articles.addView(card,lp);
                    }
                    if(results.isEmpty())articles.addView(txt("수신된 뉴스가 없습니다. 원문 제공처 연결을 확인하세요.",13,MUTED));
                });
            }catch(Exception ex){
                runOnUiThread(()->status.setText("뉴스 수신 실패: "+ex.getMessage()+" · 인터넷/지역 제한 확인"));
            }
        });
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}

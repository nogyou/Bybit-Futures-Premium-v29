package com.btc.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.app.AlertDialog;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;
import android.widget.HorizontalScrollView;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.graphics.Color;
import android.widget.ImageView;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.Spinner;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.content.res.Configuration;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends Activity {
    private final OkHttpClient client = new OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebSocket socket;
    private WebSocket sidebarSocket;
    private TextView timeframeMatrix;
    private long sidebarLastUpdate=0;
    private String symbol = "BTCUSDT", category = "linear";
    private TextView symbolView, changeView, sidebarListTitle, planSummary, scoreDescription;
    private LinearLayout quickSymbols;
    private TextView newsPreview;
    private TextView newsReactionView;
    private TextView consensusView;
    private volatile long latestMacroHeadline=0;
    private MarketCandleChartView mainChart;
    private String chartFrame = "15";
    private final Map<String, Double> dayChanges = new HashMap<>();
    private final Map<String, Double> tickerPrices = new HashMap<>();
    private TextView entryGuideView, guideUpdatedView;
    private String strategyMode = "SCALP";
    private long lastGuideCompute = 0;
    private final Map<String, Double> turnovers = new HashMap<>();
    private final Map<String, Double> volumes = new HashMap<>();
    private final Map<String, Double> dayRanges = new HashMap<>();
    private TextView scoreBreakdown;
    private final Handler chartHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<Instrument> instruments = new ArrayList<>();
    private int generation = 0;
    private static class Instrument {
        final String symbol, category;
        Instrument(String s, String c) { symbol=s; category=c; }
        String label() { return symbol + "  (" + category + ")"; }
    }
    private TextView priceView, statusView, signalView, metricsView, riskView, bookView, timeView;
    private final Map<String, ArrayList<Candle>> bars = new HashMap<>();
    private double lastPrice = Double.NaN, bidTotal = Double.NaN, askTotal = Double.NaN;
    private long lastFeedAt = 0;
    private boolean closed = false;
    private static final String[] FRAMES = {"1", "3", "5", "15", "60"};
    private static final int GREEN = Color.rgb(68, 220, 157), RED = Color.rgb(248, 104, 116);
    private static class Candle {
        long start;
        double open, high, low, close, volume;
        boolean confirmed;
        Candle(long s, double o, double h, double l, double c, double v, boolean f) { start=s; open=o; high=h; low=l; close=c; volume=v; confirmed=f; }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences selected = getSharedPreferences("market", MODE_PRIVATE);
        symbol = selected.getString("symbol", "BTCUSDT");
        category = selected.getString("category", "linear");
        buildDashboard();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
        reconnect();
        loadInstruments();
        startSidebarSocket();
        loadNewsPreview();
        chartHandler.postDelayed(new Runnable() {
            private int passes=0;
            @Override public void run() {
                if (closed) return;
                // 연결 중단 시 일부 비기본 주기를 보충 조회합니다.
                if (!chartFrame.equals("5") && !chartFrame.equals("15") && !chartFrame.equals("60") && System.currentTimeMillis()-lastFeedAt>90000) loadChartFrame();
                // Sidebar ticker polling runs separately every 15 seconds.
                if (passes % 10 == 0) loadNewsPreview();
                chartHandler.postDelayed(this, 30000);
            }
        }, 30000);
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if (closed) return;
                loadAllTickerChanges();
                ui.postDelayed(this, 15000);
            }
        }, 15000);
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if (closed) return;
                if (lastFeedAt > 0 && System.currentTimeMillis() - lastFeedAt > 25000) {
                    statusView.setText("데이터 지연 · 연결을 확인하거나 새로고침하세요");
                }
                ui.postDelayed(this, 5000);
            }
        }, 5000);
    }
    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
    private static final int BG=0xff070d18, PANEL=0xff111b2b, EDGE=0xff263a50, MUTED=0xff9fb0c5;
    private LinearLayout column() {
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);return b;
    }
    private LinearLayout row() {LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.HORIZONTAL);return b;}
    private TextView styled(String label,int sp,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(label);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);
        t.setPadding(dp(6),dp(7),dp(6),dp(7));return t;
    }
    private LinearLayout panel() {
        LinearLayout p=column();p.setPadding(dp(9),dp(8),dp(9),dp(9));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff18253a,PANEL,0xff0c1726});
        bg.setCornerRadius(dp(17));bg.setStroke(dp(1),EDGE);p.setBackground(bg);return p;
    }
    private Button button(String label, Runnable action) {
        Button b=new Button(this);b.setText(label);b.setTextSize(11);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setPadding(dp(10),dp(5),dp(10),dp(5));
        GradientDrawable bd=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xff49369a,0xff243d6b});
        bd.setCornerRadius(dp(13));bd.setStroke(dp(1),0xff7765bf);b.setBackground(bd);
        b.setOnClickListener(v->action.run());return b;
    }
    private String explainSnapshot(String label,Snapshot s,int weight){
        if(s==null)return label+" : 캔들 데이터 부족\n";
        String ema=s.ema9>s.ema21?"롱":"숏";
        String macd=s.macd>s.macdSignal?"롱":"숏";
        String rsi=s.rsi>55?"롱":s.rsi<45?"숏":"중립";
        return String.format(Locale.US,"%s (가중치 %d) : %+d점\n  EMA9/21 %s %+.0f점 · RSI %.1f %s %+.0f점 · MACD %s %+.0f점\n",
            label,weight,s.bias*weight,ema,(double)(s.ema9>s.ema21?weight:-weight),s.rsi,rsi,(double)(s.rsi>55?weight:s.rsi<45?-weight:0),macd,(double)(s.macd>s.macdSignal?weight:-weight));
    }
    private void showScoreDetails(){
        Snapshot s5=snapshot("5"),s15=snapshot("15"),s60=snapshot("60");
        String detail="점수 = (5분봉 지표합×5) + (15분봉 지표합×4) + (1시간봉 지표합×3)\n"
            +"각 지표: 롱 +1 / 숏 -1 / 중립 0\nEMA9 > EMA21: 롱, 그 외 숏\nRSI >55: 롱 / <45: 숏 / 나머지 중립\nMACD > Signal: 롱, 그 외 숏\n\n"
            +explainSnapshot("5분봉",s5,5)+"\n"+explainSnapshot("15분봉",s15,4)+"\n"+explainSnapshot("1시간봉",s60,3)
            +"\n총점 범위 -36~+36\n+12 이상 LONG / -12 이하 SHORT / 나머지 관망\n\n점수는 승률이나 수익 보장이 아닙니다.";
        new AlertDialog.Builder(this).setTitle("✦ LONG / SHORT 점수 산출 근거").setMessage(detail).setPositiveButton("확인",null).show();
    }
    private void startTradePlan(){
        Intent intent=new Intent(this,TradePlanActivity.class);
        intent.putExtra("symbol",symbol);intent.putExtra("category",category);startActivity(intent);
    }
    private void buildDashboard() {
        boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        ScrollView screen=new ScrollView(this);screen.setFillViewport(true);screen.setBackgroundColor(BG);
        LinearLayout root=column();root.setPadding(dp(9),dp(8),dp(9),dp(12));screen.addView(root);
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=styled("◈  BYBIT FUTURES PREMIUM v2.9  ✦ LIVE TRADE DESK",wide?18:17,Color.WHITE,true);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(button("⟳ 새로고침",()->reconnect()));
        top.addView(button("설정 / 업데이트",()->checkUpdate()));root.addView(top);
        TextView notice=styled("LIVE MARKET · 분봉별 진입 전략 · 조건 미충족 시 WAIT · 실제 주문은 사용자 판단",11,MUTED,false);root.addView(notice);
        LinearLayout primary=row();primary.setGravity(Gravity.TOP);root.addView(primary);
        LinearLayout left=panel();
        LinearLayout center=column();center.setPadding(dp(7),0,dp(7),0);
        LinearLayout right=panel();
        if(wide){
            primary.addView(left,new LinearLayout.LayoutParams(dp(220),-2));
            primary.addView(center,new LinearLayout.LayoutParams(0,-2,1));
            primary.addView(right,new LinearLayout.LayoutParams(dp(235),-2));
        } else {
            root.removeView(primary);
            root.addView(center);root.addView(left);root.addView(right);
        }
        left.addView(styled("◈  실시간 선물 종목",15,Color.WHITE,true));
        left.addView(button("⌕  종목 스캐너 · 정렬 / 검색",()->showSymbolPicker()));
        left.addView(styled("모든 인기 종목 실시간 수신 · 24시간 등락률",11,MUTED,false));
        sidebarListTitle=styled("인기 종목 · 조회 중",12,MUTED,false);left.addView(sidebarListTitle);
        quickSymbols=column();left.addView(quickSymbols);refreshQuickSymbols();
        left.addView(button("전체 시장 등락률 새로고침",()->loadAllTickerChanges()));
        LinearLayout marketCard=panel();center.addView(marketCard);
        symbolView=styled(symbol+" · "+category.toUpperCase(Locale.US),19,Color.WHITE,true);marketCard.addView(symbolView);
        LinearLayout priceLine=row();priceLine.setGravity(Gravity.CENTER_VERTICAL);
        priceView=styled("가격 수신 대기",23,Color.WHITE,true);
        priceLine.addView(priceView,new LinearLayout.LayoutParams(0,-2,1));
        changeView=styled("24h --",17,MUTED,true);priceLine.addView(changeView);marketCard.addView(priceLine);
        statusView=styled("데이터 연결 중…",12,MUTED,false);marketCard.addView(statusView);
        LinearLayout chartPanel=panel();LinearLayout.LayoutParams chartCardParams=new LinearLayout.LayoutParams(-1,-2);
        chartCardParams.topMargin=dp(7);center.addView(chartPanel,chartCardParams);
        chartPanel.addView(styled("캔들 차트 · 봉 주기 선택",15,Color.WHITE,true));
        HorizontalScrollView frameScroll=new HorizontalScrollView(this);frameScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout frameBar=row();
        String[][] frameOptions={{"1","1분"},{"3","3분"},{"5","5분"},{"15","15분"},{"30","30분"},{"60","1시간"},{"120","2시간"},{"240","4시간"},{"360","6시간"},{"720","12시간"},{"D","일봉"},{"W","주봉"},{"M","월봉"}};
        for(String[] option:frameOptions) {
            String frame=option[0];Button b=button(option[1],()->{
                chartFrame=frame;getSharedPreferences("market",MODE_PRIVATE).edit().putString("chartFrame",frame).apply();
                reconnect();
            });frameBar.addView(b);
        }
        frameScroll.addView(frameBar);chartPanel.addView(frameScroll);
        chartFrame=getSharedPreferences("market",MODE_PRIVATE).getString("chartFrame","15");
        mainChart=new MarketCandleChartView(this);
        chartPanel.addView(mainChart,new LinearLayout.LayoutParams(-1,dp(wide?340:285)));
        chartPanel.addView(styled("캔들 차트 · 두 손가락 확대/축소 및 좌우 이동 · 지표는 참고용",10,MUTED,false));
        LinearLayout trades=panel();LinearLayout.LayoutParams tradesLp=new LinearLayout.LayoutParams(-1,-2);tradesLp.topMargin=dp(7);
        center.addView(trades,tradesLp);
        trades.addView(styled("거래 시나리오 · 차트 기반 TP/SL",15,Color.WHITE,true));
        planSummary=styled("실제 가격 구간은 [차트 분석]에서 계산합니다.\n진입·지지·저항·TP1·TP2·손절·순손익비 제공",13,MUTED,false);
        trades.addView(planSummary);
        trades.addView(button("차트 분석으로 목표가 · 손절 · 손익비 계산",()->startTradePlan()));
        right.addView(styled("✦  AI 실시간 매매 가이드",17,Color.WHITE,true));
        right.addView(styled("● LONG 초록  ● SHORT 빨강  ● WAIT 노랑",11,MUTED,false));
        LinearLayout modeRow=row();
        modeRow.addView(button("⚡ 초단타 1·3·5분",()->{strategyMode="SCALP";render();}),new LinearLayout.LayoutParams(0,-2,1));
        modeRow.addView(button("▤ 단타 5·15·60분",()->{strategyMode="DAY";render();}),new LinearLayout.LayoutParams(0,-2,1));
        right.addView(modeRow);
        entryGuideView=styled("진입 가격 분석 준비 중…",13,Color.WHITE,true);right.addView(entryGuideView);
        guideUpdatedView=styled("마지막 분석: --",10,MUTED,false);right.addView(guideUpdatedView);
        right.addView(styled("✦  분봉별 진입 가격 · 손절 · 익절",15,Color.WHITE,true));
        timeframeMatrix=styled("각 시간봉 분석 대기…",12,Color.WHITE,false);right.addView(timeframeMatrix);
        right.addView(styled("✦  전체 추세 · 별도 참고",13,MUTED,true));
        signalView=styled("●  WAIT · 분석 준비 중",23,Color.YELLOW,true);right.addView(signalView);
        scoreDescription=styled("지표 점수는 승률이 아닙니다",11,MUTED,false);right.addView(scoreDescription);
        right.addView(button("▾  점수 산출 기준 · 상세 보기",()->showScoreDetails()));
        scoreBreakdown=styled("시간봉별 EMA · RSI · MACD 결과 대기",11,MUTED,false);right.addView(scoreBreakdown);
        metricsView=styled("5분 · 15분 · 1시간 캔들 수신 대기",12,MUTED,false);right.addView(metricsView);
        right.addView(styled("다중 분석 합의 · 별도 검증 전",14,Color.WHITE,true));
        consensusView=styled("완료 캔들로 분석 중…",12,MUTED,false);right.addView(consensusView);
        right.addView(styled("호가 압력 (최우선 1호가)",14,Color.WHITE,true));
        bookView=styled("호가 수신 대기",12,MUTED,false);right.addView(bookView);
        right.addView(styled("포지션 가디언",15,Color.WHITE,true));
        right.addView(button("LONG / SHORT 감시 시작",()->showGuardianSetup()));
        right.addView(button("포지션 감시 중지",()->{
            stopService(new Intent(this,PositionGuardianService.class));
            getSharedPreferences("guardian",MODE_PRIVATE).edit().putBoolean("enabled",false).apply();
            Toast.makeText(this,"포지션 감시 종료",Toast.LENGTH_SHORT).show();
        }));
        riskView=styled("가디언 알림은 거래소 손절 주문이 아닙니다. 백그라운드 실행은 OS 제한을 받습니다.",11,MUTED,false);right.addView(riskView);
        right.addView(styled("시장 뉴스 · BTC 영향 키워드",15,Color.WHITE,true));
        newsPreview=styled("뉴스 수신 중…",12,MUTED,false);right.addView(newsPreview);
        newsReactionView=styled("발행 시각별 BTC 변동 확인 중…",11,MUTED,false);right.addView(newsReactionView);
        right.addView(button("뉴스 전체 보기 · 원문",()->startActivity(new Intent(this, NewsActivity.class))));
        timeView=styled("",11,MUTED,false);right.addView(timeView);
        LinearLayout nav=row();nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.addView(button("전체 종목",()->showSymbolPicker()),new LinearLayout.LayoutParams(0,-2,1));
        nav.addView(button("차트 분석",()->startTradePlan()),new LinearLayout.LayoutParams(0,-2,1));
        nav.addView(button("가디언",()->showGuardianSetup()),new LinearLayout.LayoutParams(0,-2,1));
        nav.addView(button("뉴스",()->startActivity(new Intent(this, NewsActivity.class))),new LinearLayout.LayoutParams(0,-2,1));
        root.addView(nav);setContentView(screen);
    }
    private void loadNewsPreview() {
        worker.execute(()->{
            try {
                ArrayList<MarketNews.Item> items=MarketNews.load(client);
                StringBuilder b=new StringBuilder();
                for(int i=0;i<Math.min(3,items.size());i++) {
                    MarketNews.Item n=items.get(i);
                    b.append("• ").append(n.title).append("\n")
                        .append("  ").append(MarketNews.timeLabel(n)).append(" · ").append(n.category).append("\n");
                }
                String summary=b.length()==0?"최근 뉴스 없음":b.toString().trim();
                long macroSeen=0;
                long now=System.currentTimeMillis();
                for(MarketNews.Item article:items) {
                    if ("거시경제 · 변동성 주의".equals(article.category) && article.publishedMillis>now-90*60*1000L && article.publishedMillis<=now+5*60*1000L)
                        macroSeen=Math.max(macroSeen,article.publishedMillis);
                }
                latestMacroHeadline=macroSeen;
                ui.post(()->{if(!closed && newsPreview!=null)newsPreview.setText(summary);});
                try{
                    String reaction=NewsReaction.latestReaction(client,items);
                    ui.post(()->{if(!closed && newsReactionView!=null)newsReactionView.setText(reaction);});
                }catch(Exception e){
                    ui.post(()->{if(!closed && newsReactionView!=null)newsReactionView.setText("기사 시각별 BTC 가격 비교 불가 · API 확인");});
                }
            } catch(Exception ex) {
                ui.post(()->{if(!closed && newsPreview!=null)newsPreview.setText("뉴스 연결 불가 · 뉴스 전체 보기에서 재시도");});
            }
        });
    }
    private int coinLogo(String symbol) {
        String base=symbol.replace("USDT","").toLowerCase(Locale.US);
        int id=getResources().getIdentifier("coin_"+base,"drawable",getPackageName());
        return id!=0?id:android.R.drawable.ic_menu_compass;
    }
    private void refreshQuickSymbols() {
        if(quickSymbols==null)return;
        quickSymbols.removeAllViews();
        String[] samples={"BTCUSDT","ETHUSDT","XRPUSDT","SOLUSDT","DOGEUSDT","BNBUSDT","ADAUSDT","LINKUSDT"};
        for(String item:samples){
            Double ch=dayChanges.get("linear:"+item);
            String formatted=ch==null?"24h --":String.format(Locale.US,"%+.2f%%",ch);
            LinearLayout entry=row();entry.setGravity(Gravity.CENTER_VERTICAL);
            entry.setPadding(dp(6),dp(5),dp(6),dp(5));
            if(item.equals(symbol)) {
                GradientDrawable selected=new GradientDrawable();selected.setColor(0xff1c3146);
                selected.setCornerRadius(dp(11));selected.setStroke(dp(1),0xff315c76);
                entry.setBackground(selected);
            }
            ImageView logo=new ImageView(this);logo.setImageResource(coinLogo(item));
            LinearLayout.LayoutParams logoLp=new LinearLayout.LayoutParams(dp(29),dp(29));logoLp.rightMargin=dp(7);
            entry.addView(logo,logoLp);
            Double tickPrice=tickerPrices.get("linear:"+item);
            String priceText=tickPrice==null?"--":String.format(Locale.US,"%,.4f",tickPrice);
            TextView ticker=styled(item.replace("USDT", "")+" / USDT\n$"+priceText,12,Color.WHITE,item.equals(symbol));
            entry.addView(ticker,new LinearLayout.LayoutParams(0,-2,1));
            TextView change=styled(formatted,11,ch==null?MUTED:ch>=0?GREEN:RED,true);
            entry.addView(change);
            quickSymbols.addView(entry);
            entry.setOnClickListener(v->{
                boolean listed=false;
                for(Instrument instrument:instruments)if(instrument.symbol.equals(item)&&instrument.category.equals("linear")){listed=true;break;}
                if(!listed){Toast.makeText(this,"거래 가능 종목 목록을 확인 중입니다",Toast.LENGTH_SHORT).show();return;}
                symbol=item;category="linear";
                getSharedPreferences("market",MODE_PRIVATE).edit().putString("symbol",symbol).putString("category",category).apply();
                symbolView.setText(symbol+" · linear");showSelectedChange();refreshQuickSymbols();reconnect();
            });
        }
    }
    private TextView add(LinearLayout root, String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, 12, 0, 17); t.setGravity(Gravity.START);
        root.addView(t); return t;
    }
    private void showGuardianSetup() {
        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(35, 14, 35, 8);
        panel.setOrientation(LinearLayout.VERTICAL);
        TextView explain = new TextView(this);
        explain.setText("현재 선택한 종목만 수동 감시합니다. 실제 바이비트 보유 포지션을 자동 조회하지 않으며, 알림은 손절 주문이 아닙니다.");
        panel.addView(explain);
        EditText entry = new EditText(this);
        entry.setHint("진입가 (선택, 양수)");
        entry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        panel.addView(entry);
        new AlertDialog.Builder(this).setTitle("포지션 가디언 · " + symbol)
            .setView(panel).setNegativeButton("취소", null)
            .setNeutralButton("SHORT 감시", (d,w) -> startGuardian("SHORT", entry.getText().toString()))
            .setPositiveButton("LONG 감시", (d,w) -> startGuardian("LONG", entry.getText().toString()))
            .show();
    }
    private void startGuardian(String side, String entryString) {
        double entry=0;
        if (!entryString.trim().isEmpty()) {
            try { entry=Double.parseDouble(entryString.trim()); }
            catch (Exception e) { Toast.makeText(this,"진입가 형식을 확인하세요",Toast.LENGTH_LONG).show(); return; }
            if (!Double.isFinite(entry) || entry<=0) { Toast.makeText(this,"진입가는 양수여야 합니다",Toast.LENGTH_LONG).show(); return; }
        }
        getSharedPreferences("guardian",MODE_PRIVATE).edit()
            .remove("stop").remove("tp1").remove("tp2")
            .putBoolean("enabled",true).putString("symbol",symbol)
            .putString("category",category).putString("side",side)
            .putString("entry",Double.toString(entry)).apply();
        try {
            Intent intent = new Intent(this,PositionGuardianService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
            Toast.makeText(this,"가디언 시작: " + side + " " + symbol,Toast.LENGTH_LONG).show();
        } catch (Exception ex) { Toast.makeText(this,"감시 시작 오류: " + ex.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void loadInstruments() {
        worker.execute(() -> {
            ArrayList<Instrument> all = new ArrayList<>();
            try {
                for (String market : new String[]{"linear", "inverse"}) {
                    String cursor = "";
                    do {
                        String url = "https://api.bybit.com/v5/market/instruments-info?category="
                            + market + "&status=Trading&limit=1000"
                            + (cursor.isEmpty() ? "" : "&cursor=" + java.net.URLEncoder.encode(cursor, "UTF-8"));
                        try (Response res = client.newCall(new Request.Builder().url(url).build()).execute()) {
                            if (!res.isSuccessful() || res.body() == null) throw new IOException("HTTP " + res.code());
                            JSONObject json = new JSONObject(res.body().string());
                            if (json.optInt("retCode", -1) != 0) throw new IOException(json.optString("retMsg"));
                            JSONObject result = json.getJSONObject("result");
                            JSONArray list = result.getJSONArray("list");
                            for (int i=0; i<list.length(); i++) {
                                JSONObject item = list.getJSONObject(i);
                                if ("Trading".equals(item.optString("status"))) {
                                    all.add(new Instrument(item.getString("symbol"), market));
                                }
                            }
                            cursor = result.optString("nextPageCursor", "");
                        }
                    } while (!cursor.isEmpty());
                }
                all.sort(Comparator.comparing(a -> a.symbol));
                ui.post(() -> {
                    if (closed) return;
                    instruments.clear(); instruments.addAll(all);
                    loadAllTickerChanges();
                    symbolView.setText(symbol + " · " + category + " · " + all.size() + "종목 검색 가능");
                    sidebarListTitle.setText("인기 종목 · 최근 24시간");refreshQuickSymbols();
                });
            } catch (Exception e) {
                ui.post(() -> { if (!closed) symbolView.setText("선택 종목: " + symbol + " · 목록 조회 실패: " + e.getMessage()); });
            }
        });
    }
    private void loadAllTickerChanges() {
        worker.execute(() -> {
            Map<String,Double> changes=new HashMap<>(), prices=new HashMap<>();
            Map<String,Double> amounts=new HashMap<>(), vols=new HashMap<>(), ranges=new HashMap<>();
            for (String market : new String[]{"linear","inverse"}) {
                try (Response res=client.newCall(new Request.Builder().url("https://api.bybit.com/v5/market/tickers?category="+market).build()).execute()) {
                    if (!res.isSuccessful() || res.body()==null) continue;
                    JSONObject obj=new JSONObject(res.body().string());
                    if (obj.optInt("retCode",-1)!=0) continue;
                    JSONArray list=obj.getJSONObject("result").getJSONArray("list");
                    for(int i=0;i<list.length();i++) {
                        JSONObject item=list.getJSONObject(i);
                        String symbol=item.optString("symbol");
                        String key=market+":"+symbol;
                        try { prices.put(key,Double.parseDouble(item.optString("lastPrice","0"))); }catch(Exception ignored){}
                        try { amounts.put(key,Double.parseDouble(item.optString("turnover24h","0"))); }catch(Exception ignored){}
                        try { vols.put(key,Double.parseDouble(item.optString("volume24h","0"))); }catch(Exception ignored){}
                        try { double hi=Double.parseDouble(item.optString("highPrice24h","0"));double lo=Double.parseDouble(item.optString("lowPrice24h","0"));
                            if(lo>0)ranges.put(key,(hi-lo)/lo*100); }catch(Exception ignored){}
                        if(item.has("price24hPcnt")) {
                            try { changes.put(market+":"+symbol,100.0*Double.parseDouble(item.getString("price24hPcnt"))); } catch(Exception ignored){}
                        }
                    }
                } catch(Exception ignored) {}
            }
            ui.post(() -> {
                if (closed) return;
                dayChanges.clear(); dayChanges.putAll(changes);
                tickerPrices.clear(); tickerPrices.putAll(prices);
                turnovers.clear();turnovers.putAll(amounts);volumes.clear();volumes.putAll(vols);dayRanges.clear();dayRanges.putAll(ranges);
                showSelectedChange();refreshQuickSymbols();
            });
        });
    }
    private void showSelectedChange() {
        Double change=dayChanges.get(category+":"+symbol);
        if (change==null) { changeView.setText("24h --"); changeView.setTextColor(Color.LTGRAY); }
        else { changeView.setText(String.format(Locale.US,"24h %+.2f%%",change)); changeView.setTextColor(change>=0?GREEN:RED); }
    }
    private String instrumentLabel(Instrument instrument) {
        Double pct=dayChanges.get(instrument.category+":"+instrument.symbol);
        Double turnover=turnovers.get(instrument.category+":"+instrument.symbol);
        return instrument.symbol + " ("+instrument.category+")   " + (pct==null ? "24h --" : String.format(Locale.US,"24h %+.2f%%",pct))
            + (turnover==null?"":String.format(Locale.US,"   거래대금 %.1fM",turnover/1e6));
    }
    private void loadChartFrame() {
        final String selSymbol=symbol, selCategory=category, frame=chartFrame;
        mainChart.setLoading(frame);
        worker.execute(() -> {
            try {
                ArrayList<Candle> data=fetch(selCategory,selSymbol,frame);
                ui.post(() -> {
                    if(closed || !symbol.equals(selSymbol) || !category.equals(selCategory) || !chartFrame.equals(frame)) return;
                    bars.put(frame,data);
                    updateMainChart();
                });
            } catch(Exception e) { ui.post(() -> { if(!closed && chartFrame.equals(frame)) mainChart.setMessage("차트 로딩 실패: "+e.getMessage()); }); }
        });
    }
    private void updateMainChart() {
        ArrayList<Candle> data=bars.get(chartFrame);
        if(data==null || data.isEmpty()) return;
        ArrayList<MarketCandleChartView.Bar> chartBars=new ArrayList<>();
        for(Candle c : data) chartBars.add(new MarketCandleChartView.Bar(c.open,c.high,c.low,c.close,c.volume));
        mainChart.setBars(chartBars,chartFrame,symbol);
    }
    private void showSymbolPicker() {
        if (instruments.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("종목 목록을 불러오는 중이거나 API 접속에 실패했습니다.")
                .setPositiveButton("다시 조회", (d,w) -> loadInstruments()).setNegativeButton("닫기", null).show();
            return;
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(24, 12, 24, 12);
        panel.setOrientation(LinearLayout.VERTICAL);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("BTC, ETH, XRP, USDT 등 검색");
        panel.addView(search);
        Spinner sortSpinner=new Spinner(this);
        String[] sortOptions={"등락률 높은순","등락률 낮은순","거래대금 높은순","거래대금 낮은순","거래량 높은순","24시간 변동폭 높은순","종목명 오름차순","종목명 내림차순"};
        ArrayAdapter<String> sortAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sortOptions);
        sortSpinner.setAdapter(sortAdapter);panel.addView(sortSpinner);
        TextView note=styled("시가총액은 별도 공급 데이터가 필요해 제외 · 24h 거래대금은 거래소 제공값",11,0xff67748b,false);panel.addView(note);
        ListView listView = new ListView(this);
        panel.addView(listView, new LinearLayout.LayoutParams(-1, (int)(getResources().getDisplayMetrics().heightPixels*0.53)));
        ArrayList<Instrument> filtered = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View row=super.getView(position,convertView,parent);
                if (row instanceof TextView) {
                    TextView t=(TextView)row;
                    String label=getItem(position);
                    if (label!=null) {
                        int p=label.lastIndexOf("24h ");
                        if (p>=0 && !label.substring(p).contains("--")) {
                            SpannableString styled=new SpannableString(label);
                            styled.setSpan(new ForegroundColorSpan(label.substring(p).contains("-")?RED:GREEN),p,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            t.setText(styled);
                        }
                    }
                }
                return row;
            }
        };
        listView.setAdapter(adapter);
        Runnable filter = () -> {
            String q = search.getText().toString().trim().toUpperCase(Locale.US);
            filtered.clear(); labels.clear();
            for (Instrument instrument : instruments) if(instrument.symbol.contains(q)) filtered.add(instrument);
            final int mode=sortSpinner.getSelectedItemPosition();
            filtered.sort((a,b)->{
                String ka=a.category+":"+a.symbol,kb=b.category+":"+b.symbol;
                if(mode>=6) return mode==6?a.symbol.compareTo(b.symbol):b.symbol.compareTo(a.symbol);
                Map<String,Double> data=mode<=1?dayChanges:mode<=3?turnovers:mode==4?volumes:dayRanges;
                Double av=data.get(ka),bv=data.get(kb);
                if(av==null && bv==null)return a.symbol.compareTo(b.symbol);
                if(av==null)return 1;if(bv==null)return -1;
                int cmp=Double.compare(av,bv);
                return mode==1||mode==3?cmp:-cmp;
            });
            if(filtered.size()>300)filtered.subList(300,filtered.size()).clear();
            for(Instrument i:filtered)labels.add(instrumentLabel(i));
            adapter.notifyDataSetChanged();
        };
        sortSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){filter.run();}
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { filter.run(); }
            public void afterTextChanged(Editable e) {}
        });
        filter.run();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("바이비트 선물 종목 선택")
            .setView(panel).setNegativeButton("닫기",null).create();
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Instrument selected = filtered.get(position);
            symbol = selected.symbol; category = selected.category;
            getSharedPreferences("market", MODE_PRIVATE).edit()
                .putString("symbol",symbol).putString("category",category).apply();
            symbolView.setText(symbol + " · " + category);refreshQuickSymbols();
            showSelectedChange();
            dialog.dismiss();
            reconnect();
        });
        dialog.show();
    }
    private int compareVersion(String a, String b) {
        try {
            String[] aa = a.split("\\."), bb = b.split("\\.");
            for (int i=0; i<Math.max(aa.length, bb.length); i++) {
                int av = i<aa.length ? Integer.parseInt(aa[i]) : 0;
                int bv = i<bb.length ? Integer.parseInt(bb[i]) : 0;
                if (av != bv) return Integer.compare(av, bv);
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }
    private void checkUpdate() {
        SharedPreferences prefs = getSharedPreferences("update", MODE_PRIVATE);
        EditText repoInput = new EditText(this);
        repoInput.setSingleLine(true);
        repoInput.setHint("GitHub 계정명/저장소명");
        repoInput.setText(prefs.getString("repo", ""));
        new AlertDialog.Builder(this).setTitle("업데이트 확인")
            .setMessage("APK를 GitHub Releases에 게시할 저장소를 입력하세요. 예: myname/Bybit-Futures-Analyzer")
            .setView(repoInput).setNegativeButton("취소",null)
            .setPositiveButton("확인", (d,w) -> {
                String repo = repoInput.getText().toString().trim();
                if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                    statusView.setText("저장소 형식은 계정명/저장소명입니다."); return;
                }
                prefs.edit().putString("repo",repo).apply();
                statusView.setText("최신 버전 확인 중…");
                worker.execute(() -> {
                    try {
                        String url = "https://api.github.com/repos/" + repo + "/releases/latest";
                        Request req = new Request.Builder().url(url).header("Accept","application/vnd.github+json").build();
                        try (Response res = client.newCall(req).execute()) {
                            if (!res.isSuccessful() || res.body()==null) throw new IOException("릴리스 조회 HTTP " + res.code());
                            JSONObject release = new JSONObject(res.body().string());
                            String tag = release.optString("tag_name", "");
                            String page = release.optString("html_url", "");
                            String installed = "2.3.0-beta";
                            String remote = tag.startsWith("v") ? tag.substring(1) : tag;
                            boolean newer = compareVersion(remote, installed) > 0;
                            ui.post(() -> new AlertDialog.Builder(this).setTitle("배포 버전: " + tag)
                                .setMessage("설치 버전: " + installed + "\n" + (newer ? "업데이트가 있습니다. " : "현재보다 새로운 릴리스는 확인되지 않았습니다. ")
                                    + "GitHub 릴리스에 서명된 APK를 게시해야 설치할 수 있습니다. 같은 서명키로 빌드해야 덮어쓰기가 가능합니다.")
                                .setNegativeButton("닫기", null)
                                .setPositiveButton("릴리스 열기", (d2,w2) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page))))
                                .show());
                        }
                    } catch (Exception e) {
                        ui.post(() -> statusView.setText("업데이트 조회 실패: " + e.getMessage() + " · GitHub Releases 게시 여부 확인"));
                    }
                });
            }).show();
    }
    private void reconnect() {
        final int myGeneration = ++generation;
        lastFeedAt = 0;
        lastPrice = Double.NaN;
        bidTotal = askTotal = Double.NaN;
        priceView.setText("가격 수신 대기");
        signalView.setText("분석 준비 중");
        metricsView.setText("캔들 불러오는 중…");
        bookView.setText("호가 수신 대기");
        final String selectedSymbol = symbol, selectedCategory = category;
        if (socket != null) { socket.close(1000, "refresh"); socket = null; }
        statusView.setText("Bybit 캔들 불러오는 중…");
        mainChart.setLoading(chartFrame);
        showSelectedChange();
        worker.execute(() -> {
            try {
                Map<String, ArrayList<Candle>> loaded = new HashMap<>();
                for (String interval : FRAMES) loaded.put(interval, fetch(selectedCategory, selectedSymbol, interval));
                if (!loaded.containsKey(chartFrame)) loaded.put(chartFrame,fetch(selectedCategory,selectedSymbol,chartFrame));
                ui.post(() -> {
                    if (closed || myGeneration != generation) return;
                    bars.clear(); bars.putAll(loaded);
                    updateMainChart();
                    render();
                    startSocket(selectedCategory, selectedSymbol);
                });
            } catch (Exception e) {
                ui.post(() -> { if (myGeneration == generation) statusView.setText("초기 데이터 실패: " + e.getMessage() + " · 재시도하세요"); });
            }
        });
    }
    private ArrayList<Candle> fetch(String marketCategory, String marketSymbol, String interval) throws Exception {
        String url = "https://api.bybit.com/v5/market/kline?category=" + marketCategory + "&symbol=" + marketSymbol + "&interval=" + interval + "&limit=200";
        Request request = new Request.Builder().url(url).build();
        try (Response res = client.newCall(request).execute()) {
            if (!res.isSuccessful() || res.body()==null) throw new IOException("HTTP " + res.code());
            JSONObject obj = new JSONObject(res.body().string());
            if (obj.optInt("retCode", -1) != 0) throw new IOException(obj.optString("retMsg", "API 응답 오류"));
            JSONArray list = obj.getJSONObject("result").getJSONArray("list");
            ArrayList<Candle> result = new ArrayList<>();
            for (int i=list.length()-1; i>=0; i--) {
                JSONArray c = list.getJSONArray(i);
                result.add(new Candle(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5), false));
            }
            return result;
        }
    }
    private void startSocket(String marketCategory, String marketSymbol) {
        if (closed) return;
        statusView.setText("실시간 스트림 연결 중…");
        Request request = new Request.Builder().url("wss://stream.bybit.com/v5/public/" + marketCategory).build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                String extra = (chartFrame.equals("1") || chartFrame.equals("3") || chartFrame.equals("5") || chartFrame.equals("15") || chartFrame.equals("60")) ? "" : ",\"kline."+chartFrame+"."+marketSymbol+"\"";
                ws.send("{\"op\":\"subscribe\",\"args\":[\"tickers." + marketSymbol
                    + "\",\"orderbook.1." + marketSymbol + "\",\"kline.5." + marketSymbol
                    + "\",\"kline.15." + marketSymbol + "\",\"kline.60." + marketSymbol + "\",\"kline.1." + marketSymbol + "\",\"kline.3." + marketSymbol + "\""+extra+"]}");
                ui.post(() -> { if (!closed) statusView.setText("Bybit " + marketSymbol + " 실시간 연결됨"); });
            }
            @Override public void onMessage(WebSocket ws, String text) {
                ui.post(() -> { if (!closed && ws == socket) handle(text); });
            }
            @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
                ui.post(() -> { if (!closed && ws == socket) statusView.setText("연결 오류: " + error.getMessage() + " · 재연결 버튼을 누르세요"); });
            }
            @Override public void onClosed(WebSocket ws, int code, String reason) {
                ui.post(() -> { if (!closed && ws == socket) statusView.setText("연결 종료 · 재연결 버튼을 누르세요"); });
            }
        });
    }
    private void handle(String text) {
        try {
            JSONObject event = new JSONObject(text);
            String topic = event.optString("topic", "");
            if (topic.isEmpty()) return;
            lastFeedAt = System.currentTimeMillis();
            if (topic.startsWith("tickers.")) {
                JSONObject data = event.getJSONObject("data");
                if (data.has("lastPrice")) {
                    lastPrice = Double.parseDouble(data.getString("lastPrice"));
                    tickerPrices.put(category+":"+symbol,lastPrice);
                }
                if (data.has("price24hPcnt")) {
                    double pct=100*Double.parseDouble(data.getString("price24hPcnt"));
                    dayChanges.put(category+":"+symbol,pct);
                    showSelectedChange();
                }
                refreshQuickSymbols();
            } else if (topic.startsWith("orderbook.")) {
                JSONObject data = event.getJSONObject("data");
                JSONArray bids = data.optJSONArray("b"), asks = data.optJSONArray("a");
                if (bids!=null && bids.length()>0) bidTotal = bids.getJSONArray(0).getDouble(1);
                if (asks!=null && asks.length()>0) askTotal = asks.getJSONArray(0).getDouble(1);
            } else if (topic.startsWith("kline.")) {
                String interval = topic.split("\\.")[1];
                JSONArray updates = event.getJSONArray("data");
                ArrayList<Candle> list = bars.get(interval);
                if (list != null) {
                    for (int i=0;i<updates.length();i++) {
                        JSONObject c = updates.getJSONObject(i);
                        Candle candle = new Candle(c.getLong("start"), c.getDouble("open"), c.getDouble("high"), c.getDouble("low"), c.getDouble("close"), c.getDouble("volume"), c.optBoolean("confirm", false));
                        boolean found = false;
                        for (int j=list.size()-1;j>=Math.max(0,list.size()-3);j--) {
                            if (list.get(j).start == candle.start) { list.set(j,candle); found=true; break; }
                        }
                        if (!found && (list.isEmpty() || candle.start > list.get(list.size()-1).start)) list.add(candle);
                        while (list.size()>200) list.remove(0);
                    }
                }
            }
            if (topic.startsWith("kline.") && topic.split("\\.")[1].equals(chartFrame)) updateMainChart();
            render();
        } catch (Exception ignore) { /* malformed individual update: leave latest valid data visible */ }
    }

    /** Separate stream: every displayed sidebar symbol keeps updating even when another symbol is selected. */
    private void startSidebarSocket() {
        if (closed) return;
        if (sidebarSocket!=null) sidebarSocket.close(1000,"restart");
        Request request=new Request.Builder().url("wss://stream.bybit.com/v5/public/linear").build();
        sidebarSocket=client.newWebSocket(request,new WebSocketListener(){
            @Override public void onOpen(WebSocket ws,Response response){
                ws.send("{\"op\":\"subscribe\",\"args\":[\"tickers.BTCUSDT\",\"tickers.ETHUSDT\",\"tickers.XRPUSDT\",\"tickers.SOLUSDT\",\"tickers.DOGEUSDT\",\"tickers.BNBUSDT\",\"tickers.ADAUSDT\",\"tickers.LINKUSDT\"]}");
            }
            @Override public void onMessage(WebSocket ws,String text){
                ui.post(()->{
                    if(closed || ws!=sidebarSocket)return;
                    try {
                        JSONObject ev=new JSONObject(text);String topic=ev.optString("topic");
                        if(!topic.startsWith("tickers."))return;
                        String ticker=topic.substring("tickers.".length());JSONObject obj=ev.optJSONObject("data");
                        if(obj==null)return;
                        if(obj.has("lastPrice"))tickerPrices.put("linear:"+ticker,Double.parseDouble(obj.getString("lastPrice")));
                        if(obj.has("price24hPcnt"))dayChanges.put("linear:"+ticker,100*Double.parseDouble(obj.getString("price24hPcnt")));
                        sidebarLastUpdate=System.currentTimeMillis();
                        refreshQuickSymbols();
                    }catch(Exception ignored){}
                });
            }
            @Override public void onFailure(WebSocket ws,Throwable t,Response r){
                ui.post(()->{if(!closed && ws==sidebarSocket && sidebarListTitle!=null)sidebarListTitle.setText("시세 연결 지연 · 15초마다 재조회");});
            }
        });
    }

    /** Candidate levels use completed candles and ATR; they are NOT unconditional buy/sell signals. */
    private void renderTimeframeMatrix() {
        if(timeframeMatrix==null)return;
        if(lastFeedAt==0 || System.currentTimeMillis()-lastFeedAt>30000 || !Double.isFinite(lastPrice)) {
            timeframeMatrix.setText("WAIT · 시세 연결 지연\n실시간 진입 가격 안내 중단");
            timeframeMatrix.setTextColor(Color.YELLOW);return;
        }
        StringBuilder sb=new StringBuilder();
        for(String frame:FRAMES) {
            Snapshot snap=snapshot(frame);ArrayList<Candle> c=bars.get(frame);
            String label=frame.equals("60")?"1시간":frame+"분";
            if(snap==null || c==null || c.size()<40){sb.append(label).append("  WAIT · 캔들 수집 중\n\n");continue;}
            int end=c.size()-1;
            double tr=0, support=Double.POSITIVE_INFINITY, resistance=Double.NEGATIVE_INFINITY;
            for(int i=end-14;i<end;i++) {
                Candle now=c.get(i),prev=c.get(i-1);
                tr+=Math.max(now.high-now.low,Math.max(Math.abs(now.high-prev.close),Math.abs(now.low-prev.close)));
                support=Math.min(support,now.low);resistance=Math.max(resistance,now.high);
            }
            double atr=tr/14.0;
            if(!(atr>0) || !Double.isFinite(atr)){sb.append(label).append("  WAIT · 변동성 계산 불가\n\n");continue;}
            // A support/resistance level is a WATCH zone, not an immediate order recommendation.
            boolean longBias=snap.bias>=2,shortBias=snap.bias<=-2;
            String state=longBias?"LONG 관심":shortBias?"SHORT 관심":"WAIT";
            double entry=longBias?Math.min(lastPrice,support+0.30*atr):shortBias?Math.max(lastPrice,resistance-0.30*atr):lastPrice;
            double sl=longBias?Math.min(support-0.30*atr,entry-1.2*atr):shortBias?Math.max(resistance+0.30*atr,entry+1.2*atr):Double.NaN;
            double risk=Math.abs(entry-sl);
            double tp1=longBias?entry+2*risk:shortBias?entry-2*risk:Double.NaN;
            double tp2=longBias?entry+3*risk:shortBias?entry-3*risk:Double.NaN;
            boolean viable=Double.isFinite(tp1) && tp1>0 && sl>0;
            if(!viable){sb.append(label).append("  WAIT · 유효한 목표가 없음\n\n");continue;}
            double distance=Math.abs(entry-lastPrice)/lastPrice*100;
            sb.append(label).append("  ").append(state).append(" · RSI ").append(String.format(Locale.US,"%.0f",snap.rsi)).append("\n");
            sb.append(String.format(Locale.US,"진입 관심 $%,.2f · 현재가 거리 %.2f%%\n",entry,distance));
            sb.append(String.format(Locale.US,"SL $%,.2f (위험 %.2f%%)\n",sl,100*risk/entry));
            sb.append(String.format(Locale.US,"TP1 $%,.2f · TP2 $%,.2f · RR 1:2\n",tp1,tp2));
            sb.append("가격 도달 후 지지/저항 재확인 · 자동 주문 아님\n\n");
        }
        timeframeMatrix.setText(sb.toString());timeframeMatrix.setTextColor(Color.WHITE);
    }

    private static double ema(double[] a, int period) {
        double k = 2.0 / (period+1); double e=a[0];
        for (int i=1;i<a.length;i++) e = k*a[i]+(1-k)*e;
        return e;
    }
    private static double rsi(double[] a) {
        if (a.length < 16) return Double.NaN;
        double gain=0,loss=0;
        for (int i=1;i<=14;i++) { double d=a[i]-a[i-1]; gain+=Math.max(0,d); loss+=Math.max(0,-d); }
        gain/=14; loss/=14;
        for (int i=15;i<a.length;i++) { double d=a[i]-a[i-1]; gain=(gain*13+Math.max(0,d))/14; loss=(loss*13+Math.max(0,-d))/14; }
        return loss==0 ? (gain==0 ? 50 : 100) : 100-100/(1+gain/loss);
    }
    private static double[] macd(double[] a) {
        double e12=a[0], e26=a[0], signal=0, macd=0;
        for (int i=0;i<a.length;i++) {
            e12=e12+(2.0/13)*(a[i]-e12);
            e26=e26+(2.0/27)*(a[i]-e26);
            macd=e12-e26;
            if (i==0) signal=macd; else signal+=2.0/10*(macd-signal);
        }
        return new double[]{macd,signal};
    }
    private static class Snapshot {
        double ema9, ema21, rsi, macd, macdSignal, last;
        int bias;
        String text;
    }
    private Snapshot snapshot(String frame) {
        ArrayList<Candle> list=bars.get(frame);
        if (list==null || list.size()<40) return null;
        // REST can include the current unfinished candle. Use completed candles only.
        int len=list.size()-1;
        if (len<35) return null;
        double[] a = new double[len];
        for (int i=0;i<len;i++) a[i]=list.get(i).close;
        Snapshot s=new Snapshot();
        s.last=a[len-1]; s.ema9=ema(a,9); s.ema21=ema(a,21); s.rsi=rsi(a);
        double[] m=macd(a); s.macd=m[0]; s.macdSignal=m[1];
        s.bias=(s.ema9>s.ema21 ? 1:-1)+(s.macd>s.macdSignal ? 1:-1)+(s.rsi>55?1:(s.rsi<45?-1:0));
        s.text=(s.bias>0?"상승":(s.bias<0?"하락":"중립"));
        return s;
    }
    private ConsensusEngine.Frame consensusFrame(String frame) {
        ArrayList<Candle> b=bars.get(frame);
        if(b==null || b.size()<61)return null;
        // Always exclude current open candle, even if WS is active.
        int n=b.size()-1;
        double[] close=new double[n], volume=new double[n];
        for(int i=0;i<n;i++){close[i]=b.get(i).close;volume[i]=b.get(i).volume;}
        return new ConsensusEngine.Frame(frame,close,volume);
    }
    /** Informational, condition-based levels. No automatic order placement. */
    private void renderEntryGuide() {
        if (entryGuideView==null || guideUpdatedView==null) return;
        boolean stale=lastFeedAt==0 || System.currentTimeMillis()-lastFeedAt>30000;
        String[] frames=strategyMode.equals("SCALP")?new String[]{"1","3","5"}:new String[]{"5","15","60"};
        // Scalp candles are subscribed separately so an absent frame must produce WAIT.
        Snapshot a=snapshot(frames[0]),b=snapshot(frames[1]),c=snapshot(frames[2]);
        if (stale || a==null || b==null || c==null || !Double.isFinite(lastPrice) || lastPrice<=0) {
            entryGuideView.setText("WAIT · 관망\n"+(stale?"시세 연결 지연":"분석에 필요한 시간봉 데이터 수집 중")+"\n진입 가격 추천 보류");
            entryGuideView.setTextColor(Color.YELLOW);
            return;
        }
        int score=a.bias*5+b.bias*4+c.bias*3;
        int side=score>=12?1:score<=-12?-1:0;
        ArrayList<Candle> candles=bars.get(frames[0]);
        if (candles==null || candles.size()<30 || side==0) {
            entryGuideView.setText("WAIT · 관망\n추세 신호가 충분히 일치하지 않습니다.\n점수 "+score+"/36");
            entryGuideView.setTextColor(Color.YELLOW);
            return;
        }
        int n=candles.size()-1;
        double range=0;
        for(int i=n-14;i<n;i++) {
            Candle x=candles.get(i), prev=candles.get(i-1);
            range+=Math.max(x.high-x.low,Math.max(Math.abs(x.high-prev.close),Math.abs(x.low-prev.close)));
        }
        double atr=range/14.0;
        if(!Double.isFinite(atr) || atr<=0) {entryGuideView.setText("WAIT · 변동성 계산 불가");return;}
        double near=lastPrice-side*0.35*atr,far=lastPrice-side*0.75*atr;
        double low=Math.min(near,far),high=Math.max(near,far);
        double midpoint=(low+high)/2;
        double sl=midpoint-side*1.4*atr,tp1=midpoint+side*2.1*atr,tp2=midpoint+side*3.0*atr;
        boolean inZone=lastPrice>=low && lastPrice<=high;
        String sideText=side>0?"LONG":"SHORT";
        String state=inZone?"진입 후보 구간 · 추가 확인 필요":"추천 구간 대기";
        entryGuideView.setText(String.format(Locale.US,
            "%s · %s\n추천 진입 구간 $%,.4f ~ $%,.4f\n손절 SL $%,.4f\n목표 TP1 $%,.4f · TP2 $%,.4f\n예상 손익비 1:1.5 / 1:2.1 · 점수 %+d/36\n조건: 추세 유지·구간 도달·캔들 확인\n무효: 손절선 이탈 또는 신호 반전",
            sideText,state,low,high,sl,tp1,tp2,score));
        entryGuideView.setTextColor(side>0?GREEN:RED);
        lastGuideCompute=System.currentTimeMillis();
        guideUpdatedView.setText("최근 평가: "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.KOREA).format(new java.util.Date(lastGuideCompute))
            +" · "+(strategyMode.equals("SCALP")?"초단타":"단타")+" · 추정 구간/실제 주문 아님");
    }
    private void render() {
        if (!Double.isNaN(lastPrice)) priceView.setText(String.format(Locale.US,"$%,.2f",lastPrice));
        Snapshot s5=snapshot("5"), s15=snapshot("15"), s60=snapshot("60");
        if (s5!=null && s15!=null && s60!=null) {
            int score = s5.bias*5+s15.bias*4+s60.bias*3;
            String direction = score>=12 ? "LONG 우위" : score<=-12 ? "SHORT 우위" : "관망 / 혼조";
            signalView.setText((score>=12?"●  LONG ↗":score<=-12?"●  SHORT ↘":"●  WAIT · 관망") + "  [" + score + "/36]");
            scoreDescription.setText("EMA·RSI·MACD / 5·15·60분봉 종합 · 승률 아님");
            signalView.setTextColor(score>=12 ? GREEN : score<=-12 ? RED : Color.YELLOW);
            scoreBreakdown.setText(String.format(Locale.US,"5분 %+d / 15점  ·  15분 %+d / 12점  ·  1시간 %+d / 9점\nEMA·RSI·MACD 각각 ±시간봉 가중치",s5.bias*5,s15.bias*4,s60.bias*3));
            metricsView.setText(String.format(Locale.US,
                "5분봉: %s · RSI %.1f · EMA9 %.0f / EMA21 %.0f\n15분봉: %s · RSI %.1f\n1시간봉: %s · RSI %.1f\n5분봉 MACD %.2f / Signal %.2f",
                s5.text,s5.rsi,s5.ema9,s5.ema21,s15.text,s15.rsi,s60.text,s60.rsi,s5.macd,s5.macdSignal));
        }
        renderEntryGuide();
        renderTimeframeMatrix();
        if(consensusView!=null) {
            boolean stale=lastFeedAt==0 || System.currentTimeMillis()-lastFeedAt>30000;
            boolean newsCaution=latestMacroHeadline>0 && System.currentTimeMillis()-latestMacroHeadline<90*60*1000L;
            ConsensusEngine.Result result=ConsensusEngine.evaluate(
                consensusFrame("5"),consensusFrame("15"),consensusFrame("60"),stale,newsCaution);
            StringBuilder detail=new StringBuilder(result.decision).append("\n")
                .append(result.explanation).append("\n")
                .append("상승 ").append(result.bullish).append(" · 하락 ").append(result.bearish)
                .append(" · 중립 ").append(result.neutral).append(" / 5\n");
            for(ConsensusEngine.Vote v:result.votes) {
                detail.append(v.name).append(": ").append(v.value>0?"상승":v.value<0?"하락":"중립")
                    .append(" ( ").append(v.reason).append(" )\n");
            }
            detail.append("독립 규칙 합의 · AI 예측/승률 아님 · 손익비는 차트 분석에서 확인");
            consensusView.setText(detail.toString());
            consensusView.setTextColor(result.tradable?(result.decision.startsWith("LONG")?GREEN:RED):Color.YELLOW);
        }
        if (!Double.isNaN(bidTotal) && !Double.isNaN(askTotal) && bidTotal+askTotal>0) {
            double share=100*bidTotal/(bidTotal+askTotal);
            bookView.setText(String.format(Locale.US,"최우선 호가 잔량 비중: 매수 %.1f%% / 매도 %.1f%%\n(1호가 잔량이며 전체 호가·실제 체결 방향은 아님)", share,100-share));
        }
        if (lastFeedAt>0) timeView.setText("최종 수신: " + new java.text.SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new java.util.Date(lastFeedAt)) + " · 인터넷 연결 필요");
    }
    @Override protected void onDestroy() {
        closed=true;
        if (socket!=null) socket.close(1000,"Activity closed");
        if (sidebarSocket!=null) sidebarSocket.close(1000,"Activity closed");
        worker.shutdownNow();
        client.dispatcher().executorService().shutdown();
        super.onDestroy();
    }
}

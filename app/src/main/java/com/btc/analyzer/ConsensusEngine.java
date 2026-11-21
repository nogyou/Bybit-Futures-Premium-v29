package com.btc.analyzer;

/**
 * Deterministic, explainable indicator consensus, NOT a trained AI, forecast,
 * statistical probability, or financial advice. Java-only to permit unit tests.
 */
public final class ConsensusEngine {
    private ConsensusEngine() {}
    public static final class Frame {
        public final String name;
        public final double[] close, volume;
        public Frame(String name, double[] close, double[] volume) {
            this.name=name; this.close=close; this.volume=volume;
        }
    }
    public static final class Vote {
        public final String name, reason;
        public final int value; // -1 short, 0 neutral, +1 long
        Vote(String n,int v,String r){ name=n;value=v;reason=r; }
    }
    public static final class Result {
        public final String decision, explanation;
        public final int bullish, bearish, neutral;
        public final boolean tradable;
        public final Vote[] votes;
        Result(String d,String e,int bull,int bear,int flat,boolean ready,Vote[] v){
            decision=d;explanation=e;bullish=bull;bearish=bear;neutral=flat;tradable=ready;votes=v;
        }
    }
    private static double ema(double[] a,int period){
        double alpha=2.0/(period+1),v=a[0];
        for(int i=1;i<a.length;i++)v+=alpha*(a[i]-v);
        return v;
    }
    private static double[] macd(double[] a){
        double f=a[0],s=a[0],sig=0,hist=0;
        for(int i=0;i<a.length;i++){
            f+=2.0/13*(a[i]-f);s+=2.0/27*(a[i]-s);
            hist=f-s;
            sig=i==0?hist:sig+2.0/10*(hist-sig);
        }
        return new double[]{hist,sig};
    }
    private static double rsi(double[] a){
        double up=0,down=0;
        for(int i=1;i<=14;i++){
            double ch=a[i]-a[i-1]; up+=Math.max(ch,0);down+=Math.max(-ch,0);
        }
        up/=14;down/=14;
        for(int i=15;i<a.length;i++){
            double ch=a[i]-a[i-1];
            up=(up*13+Math.max(ch,0))/14;down=(down*13+Math.max(-ch,0))/14;
        }
        return down==0?(up==0?50:100):100-100/(1+up/down);
    }
    private static int sign(double a,double threshold){return a>threshold?1:a< -threshold?-1:0;}
    public static Result evaluate(Frame f5,Frame f15,Frame f60,boolean stale,boolean macroNewsCaution){
        Vote[] empty=new Vote[0];
        for(Frame f:new Frame[]{f5,f15,f60}) {
            if(f==null || f.close==null || f.volume==null || f.close.length<60 || f.volume.length!=f.close.length) {
                return new Result("데이터 부족 · 관망","분석에 필요한 완료 캔들이 부족합니다.",0,0,0,false,empty);
            }
            for(int i=0;i<f.close.length;i++) if(!Double.isFinite(f.close[i])||f.close[i]<=0||!Double.isFinite(f.volume[i])||f.volume[i]<0)
                return new Result("데이터 오류 · 관망","유효하지 않은 캔들 데이터입니다.",0,0,0,false,empty);
        }
        if(stale) return new Result("데이터 지연 · 관망","실시간 데이터가 오래되었습니다. 연결을 확인하세요.",0,0,0,false,empty);
        double[] c5=f5.close,c15=f15.close,c60=f60.close,v5=f5.volume;
        int trend=sign(ema(c60,9)-ema(c60,21),c60[c60.length-1]*0.0003);
        int alignment=sign(ema(c15,9)-ema(c15,21),c15[c15.length-1]*0.0003);
        double[] m=macd(c5);
        int momentum=sign(m[0]-m[1],c5[c5.length-1]*0.00002);
        double rs=rsi(c15);
        int strength=rs>=57&&rs<=75?1:rs<=43&&rs>=25?-1:0;
        double avgVol=0;
        for(int i=v5.length-21;i<v5.length-1;i++)avgVol+=v5[i];
        avgVol/=20;
        int participation=0;
        if(avgVol>0 && v5[v5.length-1]>=avgVol*1.3) {
            participation=sign(c5[c5.length-1]-c5[c5.length-2],c5[c5.length-2]*0.00015);
        }
        Vote[] votes={
            new Vote("1시간 추세",trend,"1시간봉 EMA9/21 방향"),
            new Vote("15분 추세",alignment,"15분봉 EMA9/21 방향"),
            new Vote("5분 모멘텀",momentum,"5분봉 MACD 신호선 격차"),
            new Vote("15분 강도",strength,"15분봉 RSI: "+Math.round(rs)),
            new Vote("거래량 확인",participation,"마감 5분봉 거래량 ≥ 이전 20봉 평균의 1.3배")
        };
        int bull=0,bear=0,flat=0;
        for(Vote v:votes)if(v.value==1)bull++;else if(v.value==-1)bear++;else flat++;
        boolean longOk=bull>=4 && bear==0 && trend==1 && alignment==1 && participation==1;
        boolean shortOk=bear>=4 && bull==0 && trend==-1 && alignment==-1 && participation==-1;
        String decision=longOk?"LONG 조건부 우위":shortOk?"SHORT 조건부 우위":"관망 · 신호 불일치";
        String explanation=longOk||shortOk?"5개 분석 중 최소 4개 일치 · 거래량 및 중장기 추세 확인":"분석 간 의견 충돌 또는 거래량 확인 미흡";
        if(macroNewsCaution){
            decision="주요 뉴스 주의 · 관망";
            explanation="최근 거시경제 관련 헤드라인이 있습니다. 실제 가격 영향은 확인되지 않았습니다.";
            longOk=false;shortOk=false;
        }
        return new Result(decision,explanation,bull,bear,flat,longOk||shortOk,votes);
    }
}

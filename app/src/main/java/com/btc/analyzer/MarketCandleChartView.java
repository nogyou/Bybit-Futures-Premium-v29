package com.btc.analyzer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lightweight native OHLC candlestick chart. Market data comes from Bybit V5. */
public class MarketCandleChartView extends View {
    public static final class Bar {
        final double open,high,low,close,volume;
        public Bar(double o,double h,double l,double c,double v) {
            open=o;high=h;low=l;close=c;volume=v;
        }
    }
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Bar> bars=new ArrayList<>();
    private final int green=Color.rgb(49,211,146), red=Color.rgb(245,91,113);
    private String caption="캔들 수신 대기";
    private String message="";
    private int visibleCount=80;
    private int scrollBack=0;
    private float dragStartX;
    private final ScaleGestureDetector zoomDetector;
    public void zoomIn(){visibleCount=Math.max(12,visibleCount-12);invalidate();}
    public void zoomOut(){visibleCount=Math.min(200,visibleCount+12);invalidate();}
    public void resetZoom(){visibleCount=80;scrollBack=0;invalidate();}
    @Override public boolean onTouchEvent(MotionEvent event){
        zoomDetector.onTouchEvent(event);
        if(event.getPointerCount()>1)return true;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){dragStartX=event.getX();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE && !zoomDetector.isInProgress()){
            float step=Math.max(4f,getWidth()/(float)Math.max(1,visibleCount));
            int delta=(int)((dragStartX-event.getX())/step);
            if(delta!=0){scrollBack=Math.max(0,Math.min(Math.max(0,bars.size()-visibleCount),scrollBack+delta));dragStartX=event.getX();invalidate();}
            return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP){performClick();return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public MarketCandleChartView(Context context) {
        super(context);setBackgroundColor(Color.rgb(11,20,36));
        zoomDetector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){
                int next=(int)(visibleCount/Math.max(.75f,Math.min(1.25f,detector.getScaleFactor())));
                visibleCount=Math.max(12,Math.min(200,next));
                scrollBack=Math.min(scrollBack,Math.max(0,bars.size()-visibleCount));invalidate();return true;
            }
        });
    }
    public void setLoading(String interval) { message="";caption=interval+" 봉 불러오는 중..."; bars.clear();invalidate(); }
    public void setMessage(String m) { message=m;invalidate(); }
    public void setBars(List<Bar> data,String interval,String symbol) {
        bars.clear();bars.addAll(data);message="";caption=symbol+"  ·  "+interval+" 봉";invalidate();
    }
    private void label(Canvas c,String str,float x,float y,int color,float size) {
        p.setColor(color);p.setTextSize(size);p.setStyle(Paint.Style.FILL);c.drawText(str,x,y,p);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density=getResources().getDisplayMetrics().density;
        float w=getWidth(), h=getHeight();
        p.setColor(Color.rgb(220,231,243));p.setTextSize(13*density);p.setStyle(Paint.Style.FILL);
        canvas.drawText(caption,12*density,21*density,p);
        if (!message.isEmpty()) { label(canvas,message,12*density,64*density,Color.LTGRAY,12*density);return; }
        if(bars.isEmpty()) { label(canvas,"시세 데이터 대기 중",12*density,70*density,Color.LTGRAY,12*density);return; }
        float left=10*density,right=w-73*density,top=34*density,bottom=h-54*density;
        if(right<=left+5 || bottom<=top+5) return;
        int count=Math.min(bars.size(),visibleCount),start=Math.max(0,bars.size()-count-scrollBack);
        count=Math.min(count,bars.size()-start);
        double max=Double.NEGATIVE_INFINITY,min=Double.POSITIVE_INFINITY,maxVol=1;
        for(int i=start;i<start+count;i++) { Bar b=bars.get(i);max=Math.max(max,b.high);min=Math.min(min,b.low);maxVol=Math.max(maxVol,b.volume); }
        if(!Double.isFinite(max)||!Double.isFinite(min)) return;
        double pad=Math.max((max-min)*.06,Math.max(Math.abs(max)*.0001,0.000001));max+=pad;min-=pad;
        float span=bottom-top, width=right-left,step=width/count,candleW=Math.max(1,step*.62f);
        p.setStrokeWidth(1*density);
        for(int row=0;row<=4;row++) {
            float y=top+(bottom-top)*row/4;
            p.setColor(Color.rgb(48,62,81));canvas.drawLine(left,y,right,y,p);
            label(canvas,format(max-(max-min)*row/4),right+4*density,y+3*density,Color.rgb(165,181,202),9*density);
        }
        for(int i=0;i<count;i++) {
            Bar b=bars.get(start+i);
            float cx=left+(i+.5f)*step;
            float yh=top+(float)((max-b.high)/(max-min))*span;
            float yl=top+(float)((max-b.low)/(max-min))*span;
            float yo=top+(float)((max-b.open)/(max-min))*span;
            float yc=top+(float)((max-b.close)/(max-min))*span;
            p.setColor(b.close>=b.open?green:red);p.setStrokeWidth(Math.max(1,1*density));
            canvas.drawLine(cx,yh,cx,yl,p);
            canvas.drawRect(new RectF(cx-candleW/2,Math.min(yo,yc),cx+candleW/2,Math.max(yo+1,yc+1)),p);
        }
        float volumeBase=h-10*density;
        float volumeHeight=34*density;
        for(int i=0;i<count;i++) {
            Bar b=bars.get(start+i);
            float cx=left+(i+.5f)*step;
            float vh=(float)(Math.max(0,b.volume)/maxVol)*volumeHeight;
            p.setColor(b.close>=b.open?Color.rgb(25,120,91):Color.rgb(127,43,61));
            canvas.drawRect(cx-candleW/2,volumeBase-vh,cx+candleW/2,volumeBase,p);
        }
        label(canvas,"거래량",left,volumeBase-volumeHeight-2*density,Color.rgb(165,181,202),9*density);
    }
    private String format(double d) {
        if(Math.abs(d)>=1000) return String.format(Locale.US,"%,.0f",d);
        if(Math.abs(d)>=1) return String.format(Locale.US,"%.2f",d);
        return String.format(Locale.US,"%.5f",d);
    }
}

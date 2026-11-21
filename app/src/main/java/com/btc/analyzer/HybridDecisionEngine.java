package com.btc.analyzer;

/** Safety-gated fusion of deterministic indicators with an OPTIONAL external AI opinion.
 * An AI opinion is never fabricated; when absent, decision is WAIT.
 * This class is a v2.7 foundation; existing UI is not yet wired to it.
 */
public final class HybridDecisionEngine {
    private HybridDecisionEngine() {}
    public enum Direction { LONG, SHORT, WAIT }
    public static final class AiOpinion {
        public final Direction direction;
        public final String rationale;
        public AiOpinion(Direction direction, String rationale) {
            this.direction=direction; this.rationale=rationale;
        }
    }
    public static final class Decision {
        public final Direction direction;
        public final String reason;
        public final double maxNotional;
        public Decision(Direction direction,String reason,double maxNotional) {
            this.direction=direction;this.reason=reason;this.maxNotional=maxNotional;
        }
    }
    /** Scores are 0-100 heuristic indicators, not probabilities. */
    public static Decision evaluate(int technicalLong, int technicalShort, AiOpinion ai,
            boolean stale, boolean volatilitySpike, boolean newsCaution,
            double accountEquity, double stopDistanceFraction, double maxLeverage) {
        if(stale || volatilitySpike || newsCaution) return wait("시장 데이터/변동성/뉴스 안전 차단");
        if(ai==null || ai.direction==null || ai.direction==Direction.WAIT) return wait("AI 판단 부재 또는 관망");
        if(technicalLong<0 || technicalLong>100 || technicalShort<0 || technicalShort>100)
            return wait("기술 점수 오류");
        Direction technical=technicalLong>=75 && technicalShort<60?Direction.LONG:
                technicalShort>=75 && technicalLong<60?Direction.SHORT:Direction.WAIT;
        if(technical==Direction.WAIT || technical!=ai.direction) return wait("기술 분석과 AI 의견 불일치");
        if(!Double.isFinite(accountEquity)||accountEquity<=0||!Double.isFinite(stopDistanceFraction)||
           stopDistanceFraction<=0||!Double.isFinite(maxLeverage)||maxLeverage<=0)
            return wait("위험 관리 입력값 오류");
        // Risk at stop capped at 0.5% equity, not accounting for slippage or fees.
        double riskBudget=accountEquity*0.005;
        double notional=Math.min(riskBudget/stopDistanceFraction, accountEquity*Math.min(maxLeverage,3.0));
        if(!Double.isFinite(notional)||notional<=0)return wait("허용 포지션 없음");
        return new Decision(technical,"지표와 AI 동의 (모의거래용)",notional);
    }
    private static Decision wait(String why){return new Decision(Direction.WAIT,why,0);}
}

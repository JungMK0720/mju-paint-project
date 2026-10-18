package recommend;

import java.util.List;

// [2026-10-18 수정자: Jung Minkyu] FEAT-01 Strategy 인터페이스
// 자유선 특징 → 추천 도형 목록. 구현체를 바꿔 끼울 수 있다.
//   - GGeometricRecognizer : 로컬 기하 휴리스틱 (항상 동작, 즉시 응답)
//   - GOllamaRecognizer    : 로컬 LLM(Ollama HTTP API) (비동기, 실패 시 기하 결과만 사용)
// 연관: frames/GRecommendPanel.java (두 전략을 조합)
public interface GShapeRecognizer {
	String getName();

	/** 점수 내림차순 정렬된 추천 목록. 예외를 던지면 호출자가 fallback 한다. */
	List<GShapeSuggestion> recognize(GStrokeFeatures features) throws Exception;
}

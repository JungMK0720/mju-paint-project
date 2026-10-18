package recommend;

import shapes.GFreeLine;

// [2026-10-18 수정자: Jung Minkyu] FEAT-01 추천 도형 사이드바
// GDrawingPanel 이 자유선 그리기를 끝냈을 때 알리는 Observer 인터페이스.
// GDrawingPanel(frames) 은 이 인터페이스만 알고, 실제 분석/표시는 frames.GRecommendPanel 이 한다.
// 연관: frames/GDrawingPanel.finishTransform() (FEAT-01), frames/GRecommendPanel.onStrokeFinished()
public interface GRecommendListener {
	void onStrokeFinished(GFreeLine freeLine);
}

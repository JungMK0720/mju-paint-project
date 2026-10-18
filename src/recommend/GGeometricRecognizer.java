package recommend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// [2026-10-18 수정자: Jung Minkyu] FEAT-01
// 외부 의존성 없이 동작하는 기하 휴리스틱 인식기.
// 각 후보의 "이상적인 특징값" 주변에 가우시안 창을 두고 곱해서 0~1 점수를 만든다.
//   선      : 열린 선 + straightness 높음
//   사각형  : 닫힘 + rectangularity≈1 + 꼭짓점 4
//   타원    : 닫힘 + circularity≈1 + rectangularity≈0.785 + 꼭짓점 많음(둥글다)
//   삼각형  : 닫힘 + rectangularity≈0.5 + 꼭짓점 3
//   다각형  : 닫힘 + 꼭짓점 5개 이상
// 연관: recommend/GStrokeFeatures.java (특징 정의), frames/GRecommendPanel.java
public class GGeometricRecognizer implements GShapeRecognizer {

	public static final String SOURCE = "geometric";

	@Override
	public String getName() {
		return SOURCE;
	}

	@Override
	public List<GShapeSuggestion> recognize(GStrokeFeatures f) {
		List<GShapeSuggestion> out = new ArrayList<>();
		int n = f.getCornerCount();
		boolean closed = f.isClosed();
		double open = closed ? 0.05 : 1.0;
		double shut = closed ? 1.0 : 0.05;

		double line = open * Math.pow(f.getStraightness(), 3);

		double rect = shut * gauss(f.getRectangularity(), 1.0, 0.12) * cornerFit(n, 4);

		double ellipse = shut * gauss(f.getCircularity(), 1.0, 0.15) * gauss(f.getRectangularity(), 0.785, 0.12)
				* roundness(n);

		double tri = shut * gauss(f.getRectangularity(), 0.5, 0.12) * cornerFit(n, 3);

		// 꼭짓점이 5개 이상인 닫힌 선. 원에 가까울수록(circularity→1) 타원 쪽으로 양보한다
		double poly = shut * (n >= 5 ? 0.6 : (n == 4 ? 0.3 : 0.15)) * gauss(f.getCircularity(), 0.78, 0.18);

		out.add(new GShapeSuggestion(ESuggestShape.eLine, line, SOURCE));
		out.add(new GShapeSuggestion(ESuggestShape.eRectangle, rect, SOURCE));
		out.add(new GShapeSuggestion(ESuggestShape.eEllipse, ellipse, SOURCE));
		out.add(new GShapeSuggestion(ESuggestShape.eTriangle, tri, SOURCE));
		out.add(new GShapeSuggestion(ESuggestShape.ePolygon, poly, SOURCE));
		Collections.sort(out);
		return out;
	}

	private static double gauss(double v, double mu, double sigma) {
		double d = v - mu;
		return Math.exp(-(d * d) / (2 * sigma * sigma));
	}

	/** 꼭짓점 수가 기대값과 같으면 1, ±1 이면 0.6, 그 외 0.25 */
	private static double cornerFit(int n, int expected) {
		int d = Math.abs(n - expected);
		return d == 0 ? 1.0 : (d == 1 ? 0.6 : 0.25);
	}

	/** 꼭짓점이 많을수록 둥근 도형(타원)일 가능성이 높다 */
	private static double roundness(int n) {
		if (n >= 6) {
			return 1.0;
		}
		if (n == 5) {
			return 0.9;
		}
		if (n == 4) {
			return 0.5;
		}
		return 0.25;
	}
}

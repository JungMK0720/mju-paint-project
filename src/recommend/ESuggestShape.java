package recommend;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import global.GConstants.ERecommend;
import shapes.GEllipse;
import shapes.GLine;
import shapes.GPolygon;
import shapes.GRectangle;
import shapes.GShape;

// [2026-10-18 수정자: Jung Minkyu] FEAT-01
// 추천 후보 도형. 각 후보는 자유선 특징(GStrokeFeatures)에 맞춰 실제 GShape 를 만들어 준다(Factory Method).
// 라벨은 GConstants.ERecommend 에서 가져오므로 XML 로 다국어 처리된다.
// 연관: recommend/GShapeSuggestion.java, frames/GRecommendPanel.java, global/GConstants.ERecommend
public enum ESuggestShape {
	eLine(ERecommend.eLine), eRectangle(ERecommend.eRectangle), eEllipse(ERecommend.eEllipse),
	eTriangle(ERecommend.eTriangle), ePolygon(ERecommend.ePolygon);

	private final ERecommend labelKey;

	private ESuggestShape(ERecommend labelKey) {
		this.labelKey = labelKey;
	}

	public String getLabel() {
		return labelKey.getLabel();
	}

	/** LLM 응답의 도형 이름(대소문자 무관, 동의어 허용)을 후보로 변환. 모르면 null */
	public static ESuggestShape fromName(String name) {
		if (name == null) {
			return null;
		}
		String n = name.trim().toUpperCase();
		switch (n) {
		case "LINE":
		case "STRAIGHT_LINE":
		case "SEGMENT":
			return eLine;
		case "RECTANGLE":
		case "RECT":
		case "SQUARE":
		case "BOX":
			return eRectangle;
		case "ELLIPSE":
		case "CIRCLE":
		case "OVAL":
			return eEllipse;
		case "TRIANGLE":
			return eTriangle;
		case "POLYGON":
		case "PENTAGON":
		case "HEXAGON":
		case "OCTAGON":
		case "DIAMOND":
		case "RHOMBUS":
			return ePolygon;
		default:
			return null;
		}
	}

	/** 자유선 특징에 맞춘 도형 생성 */
	public GShape build(GStrokeFeatures f) {
		Rectangle2D b = f.getBounds();
		int x1 = (int) Math.round(b.getX());
		int y1 = (int) Math.round(b.getY());
		int x2 = (int) Math.round(b.getX() + b.getWidth());
		int y2 = (int) Math.round(b.getY() + b.getHeight());
		switch (this) {
		case eLine: {
			GLine line = new GLine();
			List<Point2D> pts = f.getPoints();
			Point2D s = pts.get(0);
			Point2D e = pts.get(pts.size() - 1);
			line.setPoint((int) Math.round(s.getX()), (int) Math.round(s.getY()));
			line.dragPoint((int) Math.round(e.getX()), (int) Math.round(e.getY()));
			return line;
		}
		case eRectangle: {
			GRectangle r = new GRectangle();
			r.setPoint(x1, y1);
			r.dragPoint(x2, y2);
			return r;
		}
		case eEllipse: {
			GEllipse e = new GEllipse();
			e.setPoint(x1, y1);
			e.dragPoint(x2, y2);
			return e;
		}
		case eTriangle: {
			GPolygon p = new GPolygon();
			if (f.getCornerCount() == 3) {
				fillVertices(p, f.getCorners());
			} else {
				int[] xs = { (x1 + x2) / 2, x2, x1 };
				int[] ys = { y1, y2, y2 };
				p.setVertices(xs, ys, 3);
			}
			return p;
		}
		case ePolygon:
		default: {
			GPolygon p = new GPolygon();
			if (f.getCornerCount() >= 3) {
				fillVertices(p, f.getCorners());
			} else {
				// 꼭짓점이 부족하면 바운딩 박스에 내접하는 마름모
				int[] xs = { (x1 + x2) / 2, x2, (x1 + x2) / 2, x1 };
				int[] ys = { y1, (y1 + y2) / 2, y2, (y1 + y2) / 2 };
				p.setVertices(xs, ys, 4);
			}
			return p;
		}
		}
	}

	private static void fillVertices(GPolygon p, List<Point2D> corners) {
		int n = corners.size();
		int[] xs = new int[n];
		int[] ys = new int[n];
		for (int i = 0; i < n; i++) {
			xs[i] = (int) Math.round(corners.get(i).getX());
			ys[i] = (int) Math.round(corners.get(i).getY());
		}
		p.setVertices(xs, ys, n);
	}
}

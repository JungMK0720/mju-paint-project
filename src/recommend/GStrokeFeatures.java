package recommend;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// [2026-10-18 수정자: Jung Minkyu] FEAT-01
// 자유선(점 열)에서 "도형다움"을 수치화한 특징(feature) 모음.
// - corners        : Ramer–Douglas–Peucker(RDP) 로 단순화한 꼭짓점 (닫힌 선이면 닫는 점 제외)
// - closed         : 시작점과 끝점이 충분히 가까운가
// - straightness   : 시작-끝 거리 / 경로 길이 (직선이면 1 에 가깝다)
// - circularity    : 4πA / P² (원이면 1, 정사각형 0.785, 정삼각형 0.605)
// - rectangularity : 다각형 넓이 / 바운딩 박스 넓이 (사각형 1, 타원 0.785, 삼각형 0.5)
// 기하 휴리스틱(GGeometricRecognizer) 과 LLM 프롬프트(GOllamaRecognizer) 가 같은 값을 공유한다.
// 연관: recommend/GGeometricRecognizer.java, recommend/GOllamaRecognizer.java, shapes/GFreeLine.getPoints()
public class GStrokeFeatures {

	private static final double CORNER_EPSILON_RATIO = 0.07; // 꼭짓점 검출용 RDP 허용 오차 (대각선 대비)
	private static final double OUTLINE_EPSILON_RATIO = 0.02; // 넓이/둘레 계산용 RDP 허용 오차
	private static final double CLOSED_RATIO = 0.25; // 시작-끝 거리가 대각선의 25% 이내면 닫힌 것으로 본다

	private final List<Point2D> points;
	private final List<Point2D> corners;
	private final Rectangle2D bounds;
	private final double pathLength;
	private final double endDistance;
	private final double diagonal;
	private final boolean closed;
	private final double straightness;
	private final double circularity;
	private final double rectangularity;
	private final double aspect;

	private GStrokeFeatures(List<Point2D> points, List<Point2D> corners, Rectangle2D bounds, double pathLength,
			double endDistance, double diagonal, boolean closed, double straightness, double circularity,
			double rectangularity, double aspect) {
		this.points = points;
		this.corners = corners;
		this.bounds = bounds;
		this.pathLength = pathLength;
		this.endDistance = endDistance;
		this.diagonal = diagonal;
		this.closed = closed;
		this.straightness = straightness;
		this.circularity = circularity;
		this.rectangularity = rectangularity;
		this.aspect = aspect;
	}

	/** 점이 2개 미만이거나 크기가 0 이면 null */
	public static GStrokeFeatures of(List<Point2D> raw) {
		if (raw == null || raw.size() < 2) {
			return null;
		}
		List<Point2D> pts = dedupe(raw);
		if (pts.size() < 2) {
			return null;
		}
		Rectangle2D bounds = boundsOf(pts);
		double diagonal = Math.hypot(bounds.getWidth(), bounds.getHeight());
		if (diagonal < 2.0) {
			return null;
		}
		double pathLength = 0;
		for (int i = 1; i < pts.size(); i++) {
			pathLength += pts.get(i - 1).distance(pts.get(i));
		}
		double endDistance = pts.get(0).distance(pts.get(pts.size() - 1));
		boolean closed = endDistance <= Math.max(10.0, diagonal * CLOSED_RATIO) && pathLength > endDistance * 1.5;
		double straightness = (pathLength > 0) ? Math.min(1.0, endDistance / pathLength) : 0;

		// 닫힌 선이면 중심에서 가장 먼 점(진짜 꼭짓점일 확률이 높다)에서 시작하도록 순서를 돌린다.
		// 그렇지 않으면 변 중간에서 그리기 시작한 사각형의 시작점이 가짜 꼭짓점으로 잡힌다.
		List<Point2D> ordered = pts;
		if (closed) {
			Point2D c = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
			int far = 0;
			double farD = -1;
			for (int i = 0; i < pts.size(); i++) {
				double d = pts.get(i).distance(c);
				if (d > farD) {
					farD = d;
					far = i;
				}
			}
			ordered = new ArrayList<>(pts.size() + 1);
			for (int i = 0; i < pts.size(); i++) {
				ordered.add(pts.get((far + i) % pts.size()));
			}
			ordered.add(pts.get(far)); // 닫기
		}

		List<Point2D> cornerList = simplify(ordered, Math.max(3.0, diagonal * CORNER_EPSILON_RATIO));
		if (closed && cornerList.size() > 1) {
			cornerList = new ArrayList<>(cornerList.subList(0, cornerList.size() - 1)); // 닫는 점 제거
		}

		// 넓이/둘레는 손떨림이 둘레를 부풀리지 않도록 닫힌 선은 꼭짓점 수준(coarse)의 다각형으로,
		// 열린 선은 세밀한(fine) 단순화 결과로 잰다.
		List<Point2D> outline = closed ? cornerList : simplify(ordered, Math.max(1.0, diagonal * OUTLINE_EPSILON_RATIO));
		double area = Math.abs(shoelace(outline));
		double perimeter = perimeterOf(outline, closed);
		double circularity = (perimeter > 0) ? Math.min(1.0, 4 * Math.PI * area / (perimeter * perimeter)) : 0;
		double boxArea = bounds.getWidth() * bounds.getHeight();
		double rectangularity = (boxArea > 0) ? Math.min(1.0, area / boxArea) : 0;
		double w = bounds.getWidth();
		double h = bounds.getHeight();
		double aspect = (Math.max(w, h) > 0) ? Math.min(w, h) / Math.max(w, h) : 0;

		return new GStrokeFeatures(Collections.unmodifiableList(pts), Collections.unmodifiableList(cornerList),
				bounds, pathLength, endDistance, diagonal, closed, straightness, circularity, rectangularity, aspect);
	}

	// ---- getters ----
	public List<Point2D> getPoints() {
		return points;
	}

	public List<Point2D> getCorners() {
		return corners;
	}

	public int getCornerCount() {
		return corners.size();
	}

	public Rectangle2D getBounds() {
		return bounds;
	}

	public double getPathLength() {
		return pathLength;
	}

	public double getEndDistance() {
		return endDistance;
	}

	public double getDiagonal() {
		return diagonal;
	}

	public boolean isClosed() {
		return closed;
	}

	public double getStraightness() {
		return straightness;
	}

	public double getCircularity() {
		return circularity;
	}

	public double getRectangularity() {
		return rectangularity;
	}

	public double getAspect() {
		return aspect;
	}

	/** LLM 프롬프트용 요약. 꼭짓점은 바운딩 박스 기준 0~100 으로 정규화한다. */
	public String toPromptText() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("closed=%b, corners=%d, straightness=%.2f, circularity=%.2f, rectangularity=%.2f, aspect=%.2f",
				closed, getCornerCount(), straightness, circularity, rectangularity, aspect));
		sb.append(", corner_points_0_100=[");
		double w = Math.max(1.0, bounds.getWidth());
		double h = Math.max(1.0, bounds.getHeight());
		for (int i = 0; i < corners.size(); i++) {
			Point2D p = corners.get(i);
			int nx = (int) Math.round((p.getX() - bounds.getX()) / w * 100);
			int ny = (int) Math.round((p.getY() - bounds.getY()) / h * 100);
			if (i > 0) {
				sb.append(',');
			}
			sb.append('(').append(nx).append(',').append(ny).append(')');
		}
		sb.append(']');
		return sb.toString();
	}

	@Override
	public String toString() {
		return toPromptText();
	}

	// ---- geometry helpers ----
	private static List<Point2D> dedupe(List<Point2D> raw) {
		List<Point2D> out = new ArrayList<>(raw.size());
		Point2D last = null;
		for (Point2D p : raw) {
			if (last == null || last.distance(p) > 0.5) {
				out.add(p);
				last = p;
			}
		}
		return out;
	}

	private static Rectangle2D boundsOf(List<Point2D> pts) {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (Point2D p : pts) {
			minX = Math.min(minX, p.getX());
			minY = Math.min(minY, p.getY());
			maxX = Math.max(maxX, p.getX());
			maxY = Math.max(maxY, p.getY());
		}
		return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
	}

	private static double shoelace(List<Point2D> poly) {
		double a = 0;
		int n = poly.size();
		for (int i = 0; i < n; i++) {
			Point2D p = poly.get(i);
			Point2D q = poly.get((i + 1) % n);
			a += p.getX() * q.getY() - q.getX() * p.getY();
		}
		return a / 2.0;
	}

	private static double perimeterOf(List<Point2D> poly, boolean closed) {
		double p = 0;
		for (int i = 1; i < poly.size(); i++) {
			p += poly.get(i - 1).distance(poly.get(i));
		}
		if (closed && poly.size() > 1) {
			p += poly.get(poly.size() - 1).distance(poly.get(0));
		}
		return p;
	}

	/** Ramer–Douglas–Peucker 단순화 */
	public static List<Point2D> simplify(List<Point2D> pts, double epsilon) {
		if (pts.size() < 3) {
			return new ArrayList<>(pts);
		}
		boolean[] keep = new boolean[pts.size()];
		keep[0] = true;
		keep[pts.size() - 1] = true;
		rdp(pts, 0, pts.size() - 1, epsilon, keep);
		List<Point2D> out = new ArrayList<>();
		for (int i = 0; i < pts.size(); i++) {
			if (keep[i]) {
				out.add(pts.get(i));
			}
		}
		return out;
	}

	private static void rdp(List<Point2D> pts, int first, int last, double eps, boolean[] keep) {
		if (last <= first + 1) {
			return;
		}
		Point2D a = pts.get(first);
		Point2D b = pts.get(last);
		int index = -1;
		double maxD = 0;
		for (int i = first + 1; i < last; i++) {
			double d = perpendicularDistance(pts.get(i), a, b);
			if (d > maxD) {
				maxD = d;
				index = i;
			}
		}
		if (index >= 0 && maxD > eps) {
			keep[index] = true;
			rdp(pts, first, index, eps, keep);
			rdp(pts, index, last, eps, keep);
		}
	}

	private static double perpendicularDistance(Point2D p, Point2D a, Point2D b) {
		double dx = b.getX() - a.getX();
		double dy = b.getY() - a.getY();
		double len2 = dx * dx + dy * dy;
		if (len2 == 0) {
			return p.distance(a);
		}
		double t = ((p.getX() - a.getX()) * dx + (p.getY() - a.getY()) * dy) / len2;
		t = Math.max(0, Math.min(1, t));
		double px = a.getX() + t * dx;
		double py = a.getY() + t * dy;
		return p.distance(px, py);
	}
}

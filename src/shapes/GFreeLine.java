package shapes;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GFreeLine extends GShape implements Serializable {
	// attribute
	private static final long serialVersionUID = 1L;

	// components
	private GeneralPath path;

	public GFreeLine() {
		super(new GeneralPath());
		this.path = (GeneralPath) this.getShape();
	}

	@Override
	public void setPoint(int x, int y) {
		path.reset();
		path.moveTo(x, y);
	}

	@Override
	public void dragPoint(int x, int y) {
		path.lineTo(x, y);
	}

	@Override
	public void addPoint(int x, int y) {
	}

	@Override
	public void draw(Graphics2D g2d) {
		// 자유선은 열린 경로이므로 채우지 않고 외곽선만 그린다
		g2d.draw(getTransformedShape());
		if (isSelected()) {
			this.drawAnchors(g2d);
		}
	}

	// [2026-10-18 수정자: Jung Minkyu] FIX-06
	// As-Is: contains(x,y) override 가 GeneralPath.contains() 를 사용 → 선 위가 아니라
	//        선이 둘러싼 "안쪽"을 클릭해야 선택되었다. getBounds() override 는 부모와 동일한 중복.
	// To-Be: 두 override 제거. GShape.hitBody() 가 내부 + stroke 외곽선으로 판정한다.
	// 연관: GShape.hitBody() (FIX-06)

	// [2026-10-18 수정자: Jung Minkyu] FEAT-01 추천 도형 분석용 점 열람
	// 자유선이 지나간 점들을 world 좌표계(affineTransform 적용)로 돌려준다.
	// 연관: recommend/GStrokeFeatures.java, recommend/GGeometricRecognizer.java
	public List<Point2D> getPoints() {
		List<Point2D> points = new ArrayList<>();
		PathIterator it = getTransformedShape().getPathIterator(null);
		double[] coords = new double[6];
		while (!it.isDone()) {
			int type = it.currentSegment(coords);
			if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
				points.add(new Point2D.Double(coords[0], coords[1]));
			}
			it.next();
		}
		return points;
	}

	@Override
	public Rectangle getSideBounds() {
		return null;
	}
}

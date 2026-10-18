package shapes;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.Serializable;

public class GPolygon extends GShape implements Serializable {
	// attribute
	private static final long serialVersionUID = 1L;

	// components
	private Polygon polygon;

	public GPolygon() {
		super(new Polygon());
		this.polygon = (Polygon) this.getShape();
	}

	// below all methods will be into Transformer
	public void setPoint(int x, int y) {
		this.polygon.addPoint(x, y);
		this.polygon.addPoint(x, y);
	}

	public void dragPoint(int x, int y) {
		this.polygon.xpoints[this.polygon.npoints - 1] = x;
		this.polygon.ypoints[this.polygon.npoints - 1] = y;
	}

	@Override
	public void addPoint(int x, int y) {
		this.polygon.addPoint(x, y);
	}

	// [2026-10-18 수정자: Jung Minkyu] FEAT-01
	// 추천 도형을 적용할 때 꼭짓점 목록으로 다각형을 한 번에 만들기 위한 편의 메서드.
	// 연관: recommend/ESuggestShape.java
	public void setVertices(int[] xs, int[] ys, int n) {
		this.polygon.reset();
		for (int i = 0; i < n; i++) {
			this.polygon.addPoint(xs[i], ys[i]);
		}
	}

	@Override
	public Rectangle getSideBounds() {
		return null;
	}
}

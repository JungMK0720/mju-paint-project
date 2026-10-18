package shapes;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.io.Serializable;

public class GLine extends GShape implements Serializable {
	// attribute
	private static final long serialVersionUID = 1L;

	// components
	private Line2D line;

	public GLine() {
		// [2026-10-18 수정자: Jung Minkyu] Float→Double: 변환이 누적될 때 float 오차로 바운드가 1px 밀리는 것(399.99→399) 방지
		super(new Line2D.Double(0, 0, 0, 0));
		this.line = (Line2D) this.getShape();
	}

	@Override
	public void setPoint(int x, int y) {
		this.line.setLine(x, y, x, y);
	}

	@Override
	public void dragPoint(int x, int y) {
		this.line.setLine(this.line.getX1(), this.line.getY1(), x, y);
	}

	@Override
	public void addPoint(int x, int y) {
	}

	@Override
	public void draw(Graphics2D g2d) {
		// 선은 채우기가 없으므로 외곽선만 그린다 (fillColor 무시)
		g2d.draw(getTransformedShape());
		if (isSelected()) {
			this.drawAnchors(g2d);
		}
	}

	// [2026-10-18 수정자: Jung Minkyu] FIX-06
	// As-Is: contains(x,y) 를 override 해 Line2D.contains() 를 썼다. 선은 면적이 0 이라 항상 false
	//        → 선은 클릭으로 선택할 수 없었다. getBounds() override 도 부모와 동일한 중복이었다.
	// To-Be: 두 override 모두 제거. GShape.hitBody() 가 stroke 허용 오차로 판정한다.
	// 연관: GShape.hitBody() (FIX-06)

	@Override
	public Rectangle getSideBounds() {
		return null;
	}
}

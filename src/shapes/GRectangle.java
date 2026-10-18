package shapes;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

public class GRectangle extends GShape implements Serializable {
	// attribute
	private static final long serialVersionUID = 1L;

	// components
	private Rectangle2D rectangle;

	public GRectangle() {
		// [2026-10-18 수정자: Jung Minkyu] Float→Double: 변환이 누적될 때 float 오차로 바운드가 1px 밀리는 것(399.99→399) 방지
		super(new Rectangle2D.Double(0, 0, 0, 0));
		this.rectangle = (Rectangle2D) this.getShape();
	}

	// [2026-10-18 수정자: Jung Minkyu] FIX-13 역방향 드래그 시 음수 폭/높이
	// As-Is: setFrame(ox, oy, x-ox, y-oy) 는 오른쪽→왼쪽(또는 아래→위) 으로 그리면 폭/높이가 음수가 된다.
	//        Rectangle2D/Ellipse2D 는 음수 크기일 때 contains() 가 항상 false 이고 getBounds() 가 빈 사각형이라
	//        그렇게 그린 도형은 클릭 선택이 안 되고 앵커가 (0,0) 에 뭉쳤다.
	// To-Be: 시작점(ox, oy) 을 필드로 기억하고 드래그마다 min/abs 로 정규화한다.
	// 연관: GShape.hitBody() (FIX-06), GShape.getLocalBounds() (FIX-01), GEllipse/GTextArea (동일 수정)
	private double ox, oy;

	public void setPoint(int x, int y) {
		this.ox = x;
		this.oy = y;
		this.rectangle.setFrame(x, y, 0, 0);
	}

	public void dragPoint(int x, int y) {
		double nx = Math.min(ox, x);
		double ny = Math.min(oy, y);
		double w = Math.abs(x - ox);
		double h = Math.abs(y - oy);
		this.rectangle.setFrame(nx, ny, w, h);
	}

	@Override
	public void addPoint(int x, int y) {
	}

	@Override
	public Rectangle getSideBounds() {
		return null;
	}

	// [2026-10-18 수정자: Jung Minkyu]
	// As-Is: draw() 를 override 해 GShape.draw() 와 같은 내용(채우기+외곽선+앵커)을 복사해 두었다.
	// To-Be: 부모 GShape.draw() 가 동일하게 처리하므로 override 제거. 연관: GShape.drawAnchors()
}

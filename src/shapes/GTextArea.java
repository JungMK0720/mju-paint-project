package shapes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

public class GTextArea extends GShape implements Serializable {
	// attribute
	private static final long serialVersionUID = 1L;

	// components
	private String text = "";
	private Rectangle2D rectangle;

	// constructor
	public GTextArea() {
		// [2026-10-18 수정자: Jung Minkyu] Float→Double: 변환이 누적될 때 float 오차로 바운드가 1px 밀리는 것(399.99→399) 방지
		super(new Rectangle2D.Double(0, 0, 0, 0));
		this.rectangle = (Rectangle2D) this.getShape();
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}

	// [2026-10-18 수정자: Jung Minkyu] FIX-09
	// As-Is: getBounds() 가 affineTransform 을 무시한 rectangle.getBounds() 를 돌려줘
	//        이동한 글 상자의 러버밴드 선택/리사이즈 고정점이 원래 자리에 남았다.
	//        contains() override 는 GShape 와 중복, draw() 는 선택돼도 앵커를 그리지 않았다.
	// To-Be: getBounds()/contains() override 제거(부모가 transformed 기준으로 처리), draw() 는 앵커를 그린다.
	// 연관: GShape.getBounds(), GShape.hitBody() (FIX-06), GShape.drawAnchors()

	// [2026-10-18 수정자: Jung Minkyu] FIX-13 역방향 드래그 시 음수 폭/높이
	// As-Is: setFrame(ox, oy, x-ox, y-oy) 는 오른쪽→왼쪽(또는 아래→위) 으로 그리면 폭/높이가 음수가 된다.
	//        Rectangle2D/Ellipse2D 는 음수 크기일 때 contains() 가 항상 false 이고 getBounds() 가 빈 사각형이라
	//        그렇게 그린 도형은 클릭 선택이 안 되고 앵커가 (0,0) 에 뭉쳤다.
	// To-Be: 시작점(ox, oy) 을 필드로 기억하고 드래그마다 min/abs 로 정규화한다.
	// 연관: GShape.hitBody() (FIX-06), GShape.getLocalBounds() (FIX-01), GEllipse/GTextArea (동일 수정)
	private double ox, oy;

	@Override
	public void setPoint(int x, int y) {
		this.ox = x;
		this.oy = y;
		this.rectangle.setFrame(x, y, 0, 0);
	}

	@Override
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

	@Override
	public void draw(Graphics2D graphics) {
		Shape transformedShape = getTransformedShape();

		Color prev = graphics.getColor();
		if (getFillColor() != null) {
			graphics.setColor(getFillColor());
			graphics.fill(transformedShape);
		}
		graphics.setColor(Color.BLACK);
		graphics.draw(transformedShape);

		// 텍스트 표시
		if (text != null && !text.isEmpty()) {
			Rectangle2D bounds = transformedShape.getBounds2D();
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, (int) bounds.getX() + 5, (int) (bounds.getY() + bounds.getHeight() / 2 + 5));
		}
		graphics.setColor(prev);

		if (isSelected()) {
			this.drawAnchors(graphics);
		}
	}
}

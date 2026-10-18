package shapes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

// [2026-10-18 수정자: Jung Minkyu] FIX-07 그룹(Composite) 정리
// As-Is: - add() 된 자식이 bSelected=true 인 채로 남아 그룹 안에서 자식 앵커가 그대로 그려졌다.
//        - 그룹은 shape==null 이라 GShape.setAnchors() 가 바로 return → 앵커가 없고 이동만 가능했다.
//          draw() 의 pts 배열이 비어 있어 "앵커를 그리려다 만" 흔적만 남아 있었다.
//        - 그룹의 affineTransform 은 아무도 그리지 않으므로 GResizer/GRotater 가 그것을 바꿔도
//          화면에 변화가 없었다. GMover 만 instanceof GGroup 으로 자식을 직접 돌았다.
// To-Be: - add() 시 자식 선택 해제. 그룹의 affineTransform 은 항상 identity 로 유지한다(그룹 local == world).
//        - getLocalBounds() 를 자식 합집합으로 override → GShape 의 앵커/히트테스트가 그대로 동작한다.
//        - translate/rotate/scaleLocal 을 override 하여 자식에게 world 좌표계 연산으로 전달한다.
//          (그룹 local == world 이므로 scaleLocal 의 고정점은 그대로 world 점이다.)
// 연관: GShape (FIX-01 변환 API), GMover/GResizer/GRotater (instanceof 제거), GDrawingPanel.groupSelectedShapes()
public class GGroup extends GShape implements Serializable {
	// attribute
	private static final long serialVersionUID = 1L;

	// components
	private List<GShape> children = new ArrayList<>();

	public GGroup() {
		super(null);
	}

	public void add(GShape shape) {
		shape.setSelected(false);
		shape.setESelectedAnchor(null);
		children.add(shape);
	}

	public void remove(GShape shape) {
		children.remove(shape);
	}

	public List<GShape> getChildren() {
		return children;
	}

	// getter&& setter
	// getter
	@Override
	public Shape getShape() {
		Rectangle b = getBounds();
		return new Rectangle(b.x, b.y, b.width, b.height);
	}

	@Override
	public Rectangle getBounds() {
		if (children.isEmpty())
			return new Rectangle();
		Rectangle r = children.get(0).getBounds();
		for (int i = 1; i < children.size(); i++) {
			r = r.union(children.get(i).getBounds());
		}
		return r;
	}

	@Override
	public Rectangle2D getLocalBounds() {
		// 그룹의 affineTransform 은 identity 이므로 local == world
		return getBounds().getBounds2D();
	}

	@Override
	public Rectangle getSideBounds() {
		if (children.isEmpty())
			return new Rectangle();
		Rectangle r = children.get(0).getSideBounds();
		for (int i = 1; i < children.size(); i++) {
			Rectangle c = children.get(i).getSideBounds();
			if (c != null) {
				r = (r == null) ? c : r.union(c);
			}
		}
		return r;
	}

	// ---- Composite: 변환을 자식에게 전달 (FIX-01/FIX-07) ----
	@Override
	public void translate(double dx, double dy) {
		children.forEach(c -> c.translate(dx, dy));
	}

	@Override
	public void rotate(double theta, double cx, double cy) {
		children.forEach(c -> c.rotate(theta, cx, cy));
	}

	@Override
	public void scaleLocal(double sx, double sy, double cx, double cy) {
		// 그룹 local == world 이므로 자식에게는 world 기준 scale 로 전달
		children.forEach(c -> c.scaleWorld(sx, sy, cx, cy));
	}

	@Override
	public void scaleWorld(double sx, double sy, double cx, double cy) {
		children.forEach(c -> c.scaleWorld(sx, sy, cx, cy));
	}

	@Override
	public void setFillColor(Color color) {
		super.setFillColor(color);
		children.forEach(c -> c.setFillColor(color));
	}

	@Override
	protected boolean hitBody(int x, int y) {
		// 1) 자식 도형 중 하나라도 hit 이면 그룹 전체 hit
		for (GShape child : children) {
			if (child.contains(x, y)) {
				return true;
			}
		}
		// 2) 자식은 아니지만 그룹 바운딩 박스 내이면 hit
		return getBounds().contains(x, y);
	}

	// contains(int,int) 는 override 하지 않는다: 앵커 검사는 GShape.contains 가 getLocalBounds() 로,
	// 본체 검사는 위 hitBody() 로 위임된다 (FIX-06/FIX-07).

	@Override
	public GShape clone() {
		GGroup copy = new GGroup();
		children.forEach(c -> copy.add(c.clone()));
		copy.setSelected(isSelected());
		return copy;
	}

	// setter
	@Override
	public void setPoint(int x, int y) {
		children.forEach(c -> c.setPoint(x, y));
	}

	@Override
	public void addPoint(int x, int y) {
		children.forEach(c -> c.addPoint(x, y));
	}

	@Override
	public void dragPoint(int x, int y) {
		children.forEach(c -> c.dragPoint(x, y));
	}

	// method
	@Override
	public void draw(Graphics2D g2D) {
		children.forEach(c -> c.draw(g2D));
		if (isSelected()) {
			Rectangle bounds = getBounds();
			Stroke old = g2D.getStroke();
			Color prev = g2D.getColor();
			g2D.setColor(Color.BLUE);
			g2D.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f,
					new float[] { 6.0f }, 0f));
			g2D.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
			g2D.setStroke(old);
			g2D.setColor(prev);
			// 그룹도 리사이즈/회전 앵커를 가진다 (FIX-07)
			this.drawAnchors(g2D);
		}
	}
}

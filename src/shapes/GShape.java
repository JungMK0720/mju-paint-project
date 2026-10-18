package shapes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import global.GConstants.EAnchor;

public abstract class GShape implements Serializable {
	// attribute
	private static final long serialVersionUID = 1L;

	// components
	private final static int ANCHOR_W = 10;
	private final static int ANCHOR_H = 10;
	// FIX-14: 회전 핸들이 도형 위쪽에서 떨어져 있는 거리(px, world 기준 고정)
	private final static int ROTATE_HANDLE_OFFSET = 30;

	// [2026-10-18 수정자: Jung Minkyu] FIX-06
	// 선/자유선처럼 면적이 0인 도형을 클릭으로 잡기 위한 허용 오차(px). hitBody() 참조.
	private final static float HIT_TOLERANCE = 6.0f;

	public enum EPoints {
		e2P, eNP
	}

	// this is not expose. this is called "Encapsulation"
	private Shape shape;
	// temp protected
	protected Ellipse2D anchors[];
	private boolean bSelected;
	private EAnchor eSelectedAnchor; // anchors type (NN, NE, etc.)
	protected Color fillColor;

	private AffineTransform affineTransform;

	public AffineTransform getAffineTransform() {
		return this.affineTransform;
	}

	public GShape(Shape shape) {
		this.shape = shape;
		this.affineTransform = new AffineTransform();

		this.anchors = new Ellipse2D[EAnchor.values().length - 1]; // move is excluded
		for (int i = 0; i < this.anchors.length; i++) {
			this.anchors[i] = new Ellipse2D.Double();
		}
		this.bSelected = false;
		this.eSelectedAnchor = null;
		this.fillColor = null;
	}

	@Override
	public GShape clone() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(this);
			oos.flush();
			ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bis);
			return (GShape) ois.readObject();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	// getters && setters
	// Through Function is only to use Shape.
	// color Parts
	public void setFillColor(Color color) {
		this.fillColor = color;
	}

	public Color getFillColor() {
		return fillColor;
	}

	protected Shape getShape() {
		return this.shape;
	}

	public Shape getTransformedShape() {
		return this.affineTransform.createTransformedShape(this.shape);
	}

	public boolean isSelected() {
		return this.bSelected;
	}

	public void setSelected(boolean bSelected) {
		this.bSelected = bSelected;
	}

	public EAnchor getESelectedAnchor() {
		return this.eSelectedAnchor;
	}

	public void setESelectedAnchor(EAnchor anchor) {
		this.eSelectedAnchor = anchor;
	}

	/** 화면(world) 좌표계 기준 바운딩 박스. affineTransform 이 적용된 결과. */
	public Rectangle getBounds() {
		Shape tShape = this.getTransformedShape();
		if (tShape == null) {
			return (this.shape != null) ? this.shape.getBounds() : new Rectangle(0, 0, 0, 0);
		}
		return tShape.getBounds();
	}

	// [2026-10-18 수정자: Jung Minkyu] FIX-01
	// 도형 자신의(local) 좌표계 기준 바운딩 박스. affineTransform 이 적용되기 전 값.
	// As-Is: setAnchors()/GResizer 가 this.shape.getBounds() 와 getBounds()(=world) 를
	//        섞어 썼고, GGroup(shape == null) 은 앵커를 아예 만들지 못했다.
	// To-Be: local 바운드는 이 메서드로만 얻는다. GGroup 은 override 하여 자식 합집합을 돌려준다
	//        (그룹의 affineTransform 은 항상 identity 이므로 그룹의 local == world).
	// 연관: GResizer.start()/drag() (transformer/GResizer.java FIX-01), GGroup.getLocalBounds()
	public Rectangle2D getLocalBounds() {
		Shape s = this.getShape();
		return (s != null) ? s.getBounds2D() : new Rectangle2D.Double();
	}

	// ------------------------------------------------------------------
	// [2026-10-18 수정자: Jung Minkyu] FIX-01 변환(transform) 공개 API
	//
	// As-Is: GMover/GResizer/GRotater 가 getAffineTransform() 을 직접 꺼내
	//        at.translate(dx,dy) / at.rotate(theta,cx,cy) / at.translate(c).scale(s).translate(-c)
	//        처럼 "post-concatenate(local 좌표계 기준)" 연산을 호출하면서, 인자 cx/cy/dx/dy 는
	//        getBounds()(= world 좌표계) 로 계산했다. 좌표계가 섞이면
	//          - 이동 후 회전: 회전 중심이 (이동량만큼) 어긋나 도형이 공전(orbit)한다.
	//          - 회전 후 이동: 마우스를 오른쪽으로 끌어도 local x축(회전된 축)으로 움직인다.
	//          - 이동 후 리사이즈: 고정점이 틀려 도형이 "점프"한다.
	//        또 GMover 만 instanceof GGroup 으로 자식을 돌았고 Resizer/Rotater 는 그룹의
	//        (아무도 쓰지 않는) affineTransform 만 바꿔 그룹 리사이즈/회전이 동작하지 않았다.
	// To-Be: 변환은 반드시 아래 메서드로만 수행한다.
	//          translate / rotate : world 좌표계 (preConcatenate)
	//          scaleLocal         : local 좌표계 (post-concatenate) - 회전된 도형도 자기 축으로 늘어난다
	//        GGroup 은 세 메서드를 override 해 자식에게 전달한다(Composite). 호출자는 instanceof 가 필요 없다.
	// 연관: transformer/GMover.java, transformer/GRotater.java, transformer/GResizer.java,
	//       shapes/GGroup.java (FIX-01, FIX-07)
	// ------------------------------------------------------------------

	/** world 좌표계에서 (dx, dy) 만큼 평행이동 */
	public void translate(double dx, double dy) {
		this.affineTransform.preConcatenate(AffineTransform.getTranslateInstance(dx, dy));
	}

	/** world 좌표계의 점 (cx, cy) 를 중심으로 theta(rad) 만큼 회전 */
	public void rotate(double theta, double cx, double cy) {
		this.affineTransform.preConcatenate(AffineTransform.getRotateInstance(theta, cx, cy));
	}

	/** local 좌표계의 점 (cx, cy) 를 고정점으로 (sx, sy) 배 확대/축소 */
	public void scaleLocal(double sx, double sy, double cx, double cy) {
		this.affineTransform.translate(cx, cy);
		this.affineTransform.scale(sx, sy);
		this.affineTransform.translate(-cx, -cy);
	}

	/** world 좌표계의 점 (cx, cy) 를 고정점으로 (sx, sy) 배 확대/축소 (GGroup 이 자식에게 전달할 때 사용) */
	public void scaleWorld(double sx, double sy, double cx, double cy) {
		AffineTransform t = AffineTransform.getTranslateInstance(cx, cy);
		t.scale(sx, sy);
		t.translate(-cx, -cy);
		this.affineTransform.preConcatenate(t);
	}

	/** world 좌표 -> local 좌표 변환. 변환이 특이(singular) 하면 입력을 그대로 돌려준다. */
	public Point2D toLocal(double x, double y) {
		try {
			return this.affineTransform.inverseTransform(new Point2D.Double(x, y), null);
		} catch (NoninvertibleTransformException e) {
			return new Point2D.Double(x, y);
		}
	}

	// methods
	// [2026-10-18 수정자: Jung Minkyu] FIX-01/FIX-07/FIX-14 앵커 좌표계
	// As-Is: (1) this.shape.getBounds() 직접 참조 → GGroup(shape==null) 은 앵커 불가.
	//        (2) 앵커 원(10px) 자체를 local 좌표에 만들고 draw/contains 에서 affineTransform 으로 변환했다.
	//            그래서 도형을 2배 키우면 앵커도 20px 로 커지고, 회전 핸들(eRR) 의 30px 오프셋도 같이 늘어나
	//            핸들이 도형에서 멀리 떨어지거나(확대) 도형에 붙어 버렸다(축소).
	// To-Be: 앵커의 "중심점" 만 local 바운드로 구해 affineTransform 으로 world 로 옮기고,
	//        앵커 원은 world 좌표에 항상 10px 로 만든다. 회전 핸들은 world 에서 도형의 local 위쪽 방향으로 30px.
	//        → 회전된 도형의 앵커는 회전된 모서리에 붙어 있으면서 크기는 변하지 않는다.
	// 연관: drawAnchors(), contains(int,int), hitTestAnchor(), GResizer (앵커 이름은 local 모서리 기준 유지)
	protected void setAnchors() {
		Rectangle2D bounds = this.getLocalBounds();
		if (bounds == null) {
			this.eSelectedAnchor = null;
			return;
		}

		// anchors point
		double bx = bounds.getX();
		double by = bounds.getY();
		double bw = bounds.getWidth();
		double bh = bounds.getHeight();

		double cx = 0;
		double cy = 0;

		for (int i = 0; i < this.anchors.length; i++) {
			switch (EAnchor.values()[i]) {
			case eSS:
				cx = bx + (bw / 2);
				cy = by + bh;
				break;
			case eSE:
				cx = bx + bw;
				cy = by + bh;
				break;
			case eSW:
				cx = bx;
				cy = by + bh;
				break;
			case eNN:
				cx = bx + (bw / 2);
				cy = by;
				break;
			case eNE:
				cx = bx + bw;
				cy = by;
				break;
			case eNW:
				cx = bx;
				cy = by;
				break;
			case eEE:
				cx = bx + bw;
				cy = by + (bh / 2);
				break;
			case eWW:
				cx = bx;
				cy = by + (bh / 2);
				break;
			case eRR:
				cx = bx + (bw / 2);
				cy = by;
				break;
			default:
				break;
			}

			// local 중심점 → world 중심점
			Point2D world = this.affineTransform.transform(new Point2D.Double(cx, cy), null);
			double wx = world.getX();
			double wy = world.getY();
			if (EAnchor.values()[i] == EAnchor.eRR) {
				// 도형의 local "위쪽" 방향을 world 로 옮겨 그 방향으로 ROTATE_HANDLE_OFFSET 만큼 띄운다
				Point2D up = this.affineTransform.deltaTransform(new Point2D.Double(0, -1), null);
				double len = Math.hypot(up.getX(), up.getY());
				if (len < 1e-9) {
					up = new Point2D.Double(0, -1);
					len = 1;
				}
				wx += up.getX() / len * ROTATE_HANDLE_OFFSET;
				wy += up.getY() / len * ROTATE_HANDLE_OFFSET;
			}
			// world 좌표, 고정 크기
			anchors[i].setFrame(wx - (ANCHOR_W / 2), wy - (ANCHOR_H / 2), ANCHOR_W, ANCHOR_H);
		}

	}

	// [2026-10-18 수정자: Jung Minkyu] 중복 제거
	// As-Is: GRectangle/GLine/GFreeLine 이 각자 draw() 안에서 앵커 그리기 코드를 복사해 두었고
	//        색(WHITE/BLUE vs WHITE/BLACK vs 배경색) 까지 제각각이었다. GTextArea 는 아예 안 그렸다.
	// To-Be: 앵커 그리기는 이 메서드 하나로 통일하고, 하위 클래스는 본체만 그린 뒤 호출한다.
	// 연관: GRectangle.draw, GLine.draw, GFreeLine.draw, GTextArea.draw, GGroup.draw
	protected void drawAnchors(Graphics2D g2d) {
		this.setAnchors();
		Color prev = g2d.getColor();
		for (int i = 0; i < this.anchors.length; i++) {
			Shape anchor = anchors[i]; // FIX-14: 이미 world 좌표
			g2d.setColor(Color.WHITE);
			g2d.fill(anchor);
			g2d.setColor(EAnchor.values()[i] == EAnchor.eRR ? Color.RED : Color.BLUE);
			g2d.draw(anchor);
		}
		g2d.setColor(prev);
	}

	public void draw(Graphics2D graphics2D) {
		// it apply for current shape at transformed shape
		Shape transformedShape = this.getTransformedShape();

		// 내부 색상 채우기
		if (fillColor != null) {
			Color prev = graphics2D.getColor();
			graphics2D.setColor(fillColor);
			graphics2D.fill(transformedShape);
			graphics2D.setColor(prev);
		}

		graphics2D.draw(transformedShape);

		if (bSelected) {
			this.drawAnchors(graphics2D);
		}
	}

	// +@ current selected vs unselected
	public boolean contains(GShape other) {
		Rectangle currentBounds = this.getBounds();
		Rectangle otherBounds = other.getBounds();
		return currentBounds.contains(otherBounds);
	}

	// [2026-10-18 수정자: Jung Minkyu] FIX-06
	// As-Is: Shape.contains(x,y) 는 "내부 영역" 판정이라 Line2D(면적 0) 는 항상 false,
	//        GeneralPath(자유선) 는 선 위가 아니라 선이 둘러싼 안쪽을 클릭해야 true 였다.
	//        → 선/자유선은 클릭으로 선택이 사실상 불가능했다.
	// To-Be: 내부 판정이 실패하면 HIT_TOLERANCE 두께로 stroke 한 외곽선 영역으로 다시 판정한다.
	//        모든 도형이 이 메서드를 공유하므로 GLine/GFreeLine/GTextArea 의 contains override 는 제거했다.
	// 연관: contains(int,int), hitTestAnchor(int,int), GLine/GFreeLine/GTextArea (override 제거)
	protected boolean hitBody(int x, int y) {
		Shape s = this.getShape();
		if (s == null) {
			return false;
		}
		Shape body = this.affineTransform.createTransformedShape(s);
		if (body.contains(x, y)) {
			return true;
		}
		Shape outline = new BasicStroke(HIT_TOLERANCE).createStrokedShape(body);
		return outline.contains(x, y);
	}

	public boolean contains(int x, int y) {
		// 1) 선택된 상태라면 앵커(hit) 검사
		if (bSelected) {
			this.setAnchors();
			for (int i = 0; i < anchors.length; i++) {
				if (anchors[i].contains(x, y)) { // FIX-14: world 좌표
					this.eSelectedAnchor = EAnchor.values()[i];
					return true;
				}
			}
		}

		// 2) 본체(hit) 검사
		if (this.hitBody(x, y)) {
			this.eSelectedAnchor = EAnchor.eMM;
			return true;
		}

		// 3) 아무 것도 hit 하지 않음
		this.eSelectedAnchor = null;
		return false;
	}

	public EAnchor hitTestAnchor(int x, int y) {
		// 0) shape 검사
		if (this.getShape() == null) {
			this.eSelectedAnchor = null;
			return null;
		}
		// 1) 앵커 위치 계산 (always)
		setAnchors();
		for (int i = 0; i < anchors.length; i++) {
			if (anchors[i].contains(x, y)) { // FIX-14: world 좌표
				eSelectedAnchor = EAnchor.values()[i];
				return eSelectedAnchor;
			}
		}

		// 2) 본체(hit) 검사
		if (this.hitBody(x, y)) {
			eSelectedAnchor = EAnchor.eMM; // 이동 커서
			return eSelectedAnchor;
		}

		// 3) 아무 곳도 아니면
		eSelectedAnchor = null;
		return null;
	}

	// abstract Method
	public abstract void setPoint(int x, int y);

	public abstract void addPoint(int x, int y);

	public abstract void dragPoint(int x, int y);

	public abstract Rectangle getSideBounds();

}

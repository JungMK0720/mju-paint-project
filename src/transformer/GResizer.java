package transformer;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import global.GConstants.EAnchor;
import shapes.GShape;

public class GResizer extends GTransformer {

	// [2026-10-18 수정자: Jung Minkyu] FIX-10
	// 0 나눗셈 방지: 폭/높이가 이 값보다 작은 축은 scale 하지 않는다.
	// As-Is: 수평선(height 0) 의 앵커를 끌면 yScale = h2/0 = Infinity/NaN 이 affineTransform 에 들어가
	//        도형이 화면에서 사라졌다.
	private static final double MIN_SIZE = 1.0;

	// local 좌표계 기준 이전 마우스 위치
	private double px, py;
	// local 좌표계 기준 고정점
	private double cx, cy;
	// 앵커별 부호: 마우스 이동량을 폭/높이 증가량으로 바꿀 때 곱한다
	private int signX, signY;

	public GResizer(GShape shape) {
		super(shape);
	}

	// [2026-10-18 수정자: Jung Minkyu] FIX-01 리사이즈 고정점(anchor) 어긋남
	// As-Is: 고정점 (cx, cy) 를 getBounds()(world) 로 구하고, 적용은
	//        at.translate(cx,cy).scale(sx,sy).translate(-cx,-cy) (post-concatenate = local 좌표계) 로 했다.
	//        도형을 한 번이라도 이동하면 local != world 가 되어 고정점이 이동량만큼 틀어지고
	//        도형이 "점프" 했다. 회전된 도형은 world 축 기준 마우스 이동량을 local 축에 그대로 적용해
	//        엉뚱한 방향으로 늘어났다. (= "앵커 문제")
	// To-Be: 모든 계산을 local 좌표계에서 한다.
	//        - 마우스 좌표는 GShape.toLocal() 로 역변환해 쓴다.
	//        - 고정점은 getLocalBounds() 로 구한다.
	//        - GShape.scaleLocal() 로 적용하고, 적용 후 마우스 local 좌표를 다시 구해 둔다
	//          (scale 이 누적되면 같은 world 점의 local 좌표가 달라지기 때문).
	//        회전된 도형도 자기 축 방향으로 늘어나며(찌그러지지 않음), 그룹은 GGroup.scaleLocal 이
	//        자식에게 world 기준으로 전달한다 (그룹의 local == world).
	// 연관: GShape.toLocal()/getLocalBounds()/scaleLocal() (shapes/GShape.java FIX-01),
	//       GGroup.scaleLocal() (FIX-07), GRotater (같은 원인)
	public void start(Graphics2D graphics, int x, int y) {
		Point2D p = this.shape.toLocal(x, y);
		this.px = p.getX();
		this.py = p.getY();

		Rectangle2D r = this.shape.getLocalBounds();
		double x1 = r.getX();
		double y1 = r.getY();
		double x2 = r.getX() + r.getWidth();
		double y2 = r.getY() + r.getHeight();
		double mx = r.getCenterX();
		double my = r.getCenterY();

		EAnchor eSelectedAnchor = this.shape.getESelectedAnchor();
		if (eSelectedAnchor == null) {
			eSelectedAnchor = EAnchor.eSE;
		}
		switch (eSelectedAnchor) {
		case eNW:
			cx = x2; cy = y2; signX = -1; signY = -1;
			break;
		case eNN:
			cx = mx; cy = y2; signX = 0; signY = -1;
			break;
		case eNE:
			cx = x1; cy = y2; signX = 1; signY = -1;
			break;
		case eEE:
			cx = x1; cy = my; signX = 1; signY = 0;
			break;
		case eSE:
			cx = x1; cy = y1; signX = 1; signY = 1;
			break;
		case eSS:
			cx = mx; cy = y1; signX = 0; signY = 1;
			break;
		case eSW:
			cx = x2; cy = y1; signX = -1; signY = 1;
			break;
		case eWW:
			cx = x2; cy = my; signX = -1; signY = 0;
			break;
		default:
			cx = x1; cy = y1; signX = 1; signY = 1;
			break;
		}
	}

	public void drag(Graphics2D graphics, int x, int y) {
		Point2D p = this.shape.toLocal(x, y);
		double dx = signX * (p.getX() - px);
		double dy = signY * (p.getY() - py);

		Rectangle2D r = this.shape.getLocalBounds();
		double w1 = r.getWidth();
		double h1 = r.getHeight();

		double xScale = 1.0;
		double yScale = 1.0;
		if (signX != 0 && Math.abs(w1) >= MIN_SIZE) {
			double w2 = w1 + dx;
			if (w2 < MIN_SIZE) {
				w2 = MIN_SIZE; // 뒤집힘(flip)/0 크기 방지
			}
			xScale = w2 / w1;
		}
		if (signY != 0 && Math.abs(h1) >= MIN_SIZE) {
			double h2 = h1 + dy;
			if (h2 < MIN_SIZE) {
				h2 = MIN_SIZE;
			}
			yScale = h2 / h1;
		}

		this.shape.scaleLocal(xScale, yScale, cx, cy);

		// scale 적용 후 같은 world 점의 local 좌표가 바뀌므로 다시 계산
		Point2D q = this.shape.toLocal(x, y);
		this.px = q.getX();
		this.py = q.getY();
	}

	public void finish(Graphics2D graphics, int x, int y) {

	}

	@Override
	public void addPoint(Graphics2D graphics, int x, int y) {

	}
}

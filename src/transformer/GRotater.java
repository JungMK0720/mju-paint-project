package transformer;

import java.awt.Graphics2D;
import java.awt.Rectangle;

import shapes.GShape;

public class GRotater extends GTransformer {
	private double cx, cy;
	private double startAngle;

	public GRotater(GShape shape) {
		super(shape);
	}

	// [2026-10-18 수정자: Jung Minkyu] FIX-01 회전 중심 어긋남
	// As-Is: 중심 (cx, cy) 는 getBounds() 즉 world 좌표로 구하고, 적용은
	//        affineTransform.rotate(theta, cx, cy) (post-concatenate = local 좌표계) 로 했다.
	//        도형을 이동/확대한 뒤에는 local 과 world 가 다르므로 회전 중심이 어긋나
	//        도형이 제자리에서 돌지 않고 다른 점을 중심으로 공전했다. (= "로테이션 문제")
	// To-Be: GShape.rotate(theta, cx, cy) 는 preConcatenate(world 좌표계) 로 적용하므로
	//        world 로 구한 중심을 그대로 쓸 수 있다. 그룹은 GGroup.rotate 가 자식에게 같은 중심으로 전달한다.
	// 연관: GShape.rotate() (shapes/GShape.java FIX-01), GGroup.rotate() (FIX-07),
	//       GResizer (같은 원인, 리사이즈는 local 좌표계로 처리)
	@Override
	public void start(Graphics2D graphics, int x, int y) {
		Rectangle bounds = this.shape.getBounds();
		this.cx = bounds.getCenterX();
		this.cy = bounds.getCenterY();
		this.startAngle = Math.atan2(y - cy, x - cx);
	}

	@Override
	public void drag(Graphics2D graphics, int x, int y) {
		double currentAngle = Math.atan2(y - cy, x - cx);
		double angleDelta = currentAngle - startAngle;

		this.shape.rotate(angleDelta, cx, cy);

		this.startAngle = currentAngle;
	}

	@Override
	public void finish(Graphics2D graphics, int x, int y) {

	}

	@Override
	public void addPoint(Graphics2D graphics, int x, int y) {

	}

}

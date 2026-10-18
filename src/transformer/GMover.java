package transformer;

import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;

import shapes.GShape;

public class GMover extends GTransformer {

	private int px, py;
	// 함께 움직일 대상 (단일 도형이면 그 하나, 다중 선택이면 선택된 전부)
	private final List<GShape> targets;

	public GMover(GShape shape) {
		super(shape);
		this.targets = Collections.singletonList(shape);
	}

	// [2026-10-18 수정자: Jung Minkyu] FIX-02 다중 선택 이동
	// 러버밴드로 여러 개를 고른 뒤 그중 하나를 끌면 선택된 전부가 함께 움직인다.
	// 연관: frames/GDrawingPanel.startTransform()
	public GMover(List<GShape> shapes) {
		super(shapes.isEmpty() ? null : shapes.get(0));
		this.targets = shapes;
	}

	public void start(Graphics2D graphics, int x, int y) {
		this.px = x;
		this.py = y;
	}

	// [2026-10-18 수정자: Jung Minkyu] FIX-01 / FIX-07
	// As-Is: shape.getAffineTransform().translate(dx, dy) 는 post-concatenate 라 "local 좌표계" 이동이다.
	//        도형을 한 번 회전시킨 뒤에는 마우스를 오른쪽으로 끌어도 회전된 축 방향으로 움직였고,
	//        확대된 도형은 마우스보다 빠르게 움직였다. 그룹은 instanceof GGroup 으로 자식을 직접 돌았다.
	//        또 movePoint()/setMovePoint() 와 null 인 affineTransform 필드가 죽은 코드로 남아 있었다.
	// To-Be: GShape.translate(dx, dy) (world 좌표계, preConcatenate) 하나만 호출한다.
	//        GGroup 이 translate 를 override 해 자식에게 전달하므로 instanceof 분기가 사라진다.
	// 연관: GShape.translate() (shapes/GShape.java FIX-01), GGroup.translate() (FIX-07)
	public void drag(Graphics2D graphics, int x, int y) {
		int dx = x - px;
		int dy = y - py;

		for (GShape target : this.targets) {
			target.translate(dx, dy);
		}

		this.px = x;
		this.py = y;
	}

	public void finish(Graphics2D graphics, int x, int y) {

	}

	@Override
	public void addPoint(Graphics2D graphics, int x, int y) {

	}
}

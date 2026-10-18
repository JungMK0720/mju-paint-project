package transformer;

import java.awt.Graphics2D;

import shapes.GShape;

public class GDrawer extends GTransformer {

	// [2026-10-18 수정자: Jung Minkyu]
	// As-Is: 부모 GTransformer 에 protected shape 가 있는데도 같은 필드를 private 으로 한 번 더 들고 있었다.
	// To-Be: 부모 필드를 그대로 사용. (GMover/GResizer 도 동일하게 정리)
	public GDrawer(GShape shape) {
		super(shape);
	}

	public void start(Graphics2D graphics, int x, int y) {
		this.shape.setPoint(x, y);
	}

	public void drag(Graphics2D graphics, int x, int y) {
		this.shape.dragPoint(x, y);
	}

	public void finish(Graphics2D graphics, int x, int y) {
	}

	@Override
	public void addPoint(Graphics2D graphics, int x, int y) {
		this.shape.addPoint(x, y);
	}
}

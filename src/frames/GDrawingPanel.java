package frames;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import global.GConstants.EAnchor;
import global.GConstants.EShapeTool;
import recommend.GRecommendListener;
import shapes.GFreeLine;
import shapes.GGroup;
import shapes.GShape;
import shapes.GShape.EPoints;
import shapes.GTextArea;
import transformer.GDrawer;
import transformer.GMover;
import transformer.GResizer;
import transformer.GRotater;
import transformer.GTransformer;

public class GDrawingPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	public enum EDrawingState {
		eIdle, e2P, eNP
	}

	// components
	private Vector<GShape> shapes;

	// [2026-10-18 수정자: Jung Minkyu] FIX-08 선택 상태의 단일 출처(single source of truth)
	// As-Is: selectedShapes(Vector) 와 각 GShape.isSelected() 플래그가 따로 놀았다.
	//        러버밴드 선택만 Vector 에 넣고, 클릭 선택/그룹/삭제는 플래그를 봤기 때문에
	//        복사(Ctrl+C) 는 러버밴드로 고른 것만 복사되는 등 기능마다 "선택" 의 의미가 달랐다.
	// To-Be: 선택 상태는 GShape.isSelected() 플래그 하나뿐이고, 목록이 필요하면 getSelectedShapes() 로 계산한다.
	// 연관: getSelectedShapes(), clearSelection(), copySelectedShapes(), removeSelectedShapes()

	// redo / undo
	private Stack<List<GShape>> undoStack;
	private Stack<List<GShape>> redoStack;
	// copy / paste
	private List<GShape> copyBuffer;

	// working objects
	private GTransformer transformer;
	private GShape currentShape;
	private GShape selectedShape;

	// [2026-10-18 수정자: Jung Minkyu] FIX-02 러버밴드(rubber band) 분리
	// As-Is: 선택 도구의 드래그 사각형을 shapes 목록에 넣었다가 finishTransform 에서
	//        shapes.remove(size-1) 로 빼는 방식이었다. 그래서 (1) 더블클릭 등으로 finish 가 한 번 더
	//        불리면 진짜 도형이 지워졌고, (2) 클릭 이동/리사이즈 때는 러버밴드가 0 크기이므로
	//        currentShape.contains(shape) 가 전부 false → 변환이 끝나면 선택이 항상 풀렸다.
	//        (= 이동 후 앵커가 사라져 리사이즈/회전을 하려면 매번 러버밴드로 다시 선택해야 했다)
	// To-Be: 러버밴드는 shapes 밖의 별도 필드로 두고 paintComponent 가 점선으로 따로 그린다.
	//        변환(이동/리사이즈/회전)이 끝나면 선택을 유지한다.
	// 연관: startTransform(), finishTransform(), paintComponent(), MouseEventHandler.mouse2Clicked() (FIX-04)
	private GShape rubberBand;

	private EShapeTool eShapeTool;
	private EDrawingState eDrawingState;
	private boolean bUpdated;

	// [2026-10-18 수정자: Jung Minkyu] FEAT-01 추천 도형 사이드바 Observer
	// 연관: recommend/GRecommendListener.java, frames/GRecommendPanel.associate()
	private GRecommendListener recommendListener;

	// constructors
	public GDrawingPanel() {
		MouseEventHandler mouseHandler = new MouseEventHandler();
		this.addMouseListener(mouseHandler);
		this.addMouseMotionListener(mouseHandler);

		constMembers();
		setupKeyBindings();
	}

	private void constMembers() {
		this.shapes = new Vector<GShape>();
		this.undoStack = new Stack<>();
		this.redoStack = new Stack<>();
		this.copyBuffer = new ArrayList<>();

		this.currentShape = null;
		this.selectedShape = null;
		this.rubberBand = null;
		this.eShapeTool = null;
		this.eDrawingState = EDrawingState.eIdle;
		this.bUpdated = false;
	}

	private void setupKeyBindings() {
		InputMap im = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = this.getActionMap();

		setCommandKey(im, am);
	}

	public void initialize() {
		this.shapes.clear();
		this.undoStack.clear();
		this.redoStack.clear();
		this.repaint();
	}

	// getters and setters
	public List<GShape> getSelectedShapes() {
		return shapes.stream().filter(GShape::isSelected).collect(Collectors.toList());
	}

	/** 모든 도형의 선택 상태를 해제 */
	public void clearSelection() {
		for (GShape s : shapes) {
			s.setSelected(false);
			s.setESelectedAnchor(null);
		}
		this.selectedShape = null;
	}

	// getter
	public Vector<GShape> getShapes() {
		return this.shapes;
	}

	// setter
	public void setEShapeTool(EShapeTool eShapeType) {
		this.eShapeTool = eShapeType;
	}

	@SuppressWarnings("unchecked")
	public void setShapes(Object shapes) {
		this.shapes = (Vector<GShape>) shapes; // Type Casting
		// 파일을 새로 열면 이전 문서의 undo/redo 는 의미가 없다
		this.undoStack.clear();
		this.redoStack.clear();
		this.clearSelection();
		this.repaint();
	}

	public boolean isUpdated() {
		return this.bUpdated;
	}

	public void setBUpdated(boolean bUpdated) {
		this.bUpdated = bUpdated;
	}

	public void setRecommendListener(GRecommendListener listener) {
		this.recommendListener = listener;
	}

	// Fill
	// [2026-10-18 수정자: Jung Minkyu] FIX-12
	// As-Is: 마지막으로 "클릭한" 도형(selectedShape) 하나에만 색을 칠했다. 러버밴드로 여러 개를 골라도
	//        selectedShape 는 null 이라 아무 일도 없었다. 스냅샷도 색을 바꾼 "뒤"에 찍었다 (FIX-03).
	// To-Be: 선택된 모든 도형에 적용하고, 바꾸기 전에 스냅샷을 찍는다.
	public void setSelectedShapeFillColor(Color color) {
		List<GShape> sel = getSelectedShapes();
		if (sel.isEmpty()) {
			return;
		}
		saveStateForUndo();
		for (GShape s : sel) {
			s.setFillColor(color);
		}
		this.bUpdated = true;
		this.repaint();
	}

	public void removeSelectedShapes() {
		List<GShape> toRemove = getSelectedShapes();
		if (toRemove.isEmpty())
			return;

		// FIX-03: 바꾸기 전에 스냅샷
		saveStateForUndo();
		shapes.removeAll(toRemove);
		clearSelection();
		this.bUpdated = true;
		repaint();
	}

	// methods

	// Group
	// [2026-10-18 수정자: Jung Minkyu] FIX-07
	// As-Is: 자식을 그룹에 넣은 뒤 clearSelection() 을 불렀지만 자식은 이미 shapes 에서 빠진 뒤라
	//        bSelected=true 로 남았고, 그룹 안에서 자식 앵커가 그대로 그려졌다.
	// To-Be: GGroup.add() 가 자식 선택을 해제한다. 스냅샷은 변경 전에 찍는다 (FIX-03).
	// 연관: shapes/GGroup.add()
	public void groupSelectedShapes() {
		List<GShape> sel = getSelectedShapes();
		if (sel.size() < 2)
			return;

		saveStateForUndo();
		GGroup group = new GGroup();
		for (GShape s : sel) {
			group.add(s);
			shapes.remove(s);
		}
		shapes.add(group);

		clearSelection();
		group.setSelected(true);

		this.setBUpdated(true);
		repaint();
	}

	// unGroup
	public void ungroupSelectedShapes() {
		List<GGroup> groups = getSelectedShapes().stream().filter(GGroup.class::isInstance).map(GGroup.class::cast)
				.collect(Collectors.toList());
		if (groups.isEmpty())
			return;

		saveStateForUndo();
		clearSelection();
		for (GGroup g : groups) {
			// 그룹이 있던 자리(z-order)에 자식을 되돌려 놓는다
			int index = shapes.indexOf(g);
			shapes.remove(g);
			shapes.addAll(index, g.getChildren());
			for (GShape child : g.getChildren()) {
				child.setSelected(true);
			}
		}

		this.setBUpdated(true);
		repaint();
	}

	// copy
	public void copySelectedShapes() {
		copyBuffer.clear();
		for (GShape shape : getSelectedShapes()) {
			GShape copy = shape.clone(); // Deep Copy
			if (copy != null) {
				copy.setSelected(false);
				copyBuffer.add(copy);
			}
		}
	}

	// paste
	// [2026-10-18 수정자: Jung Minkyu]
	// As-Is: 원본과 같은 자리에 붙여 넣어 겹쳐 보이지 않았고, 빈 버퍼여도 undo 스택이 쌓였다.
	// To-Be: 붙여 넣을 때마다 (10,10) 씩 비켜 놓고, 붙여 넣은 도형을 선택 상태로 둔다.
	public void pasteShapes() {
		if (copyBuffer.isEmpty())
			return;
		saveStateForUndo();
		clearSelection();
		for (GShape shape : copyBuffer) {
			shape.translate(10, 10); // 다음 붙여넣기도 계단식으로
			GShape pastedShape = shape.clone();
			if (pastedShape == null)
				continue;
			pastedShape.setSelected(true);
			this.shapes.add(pastedShape);
		}
		this.bUpdated = true;
		repaint();
	}

	// re-do / undo
	// [2026-10-18 수정자: Jung Minkyu] FIX-03 Undo 가 한 단계 늦게 동작
	// As-Is: 모든 호출처가 "변경한 뒤에" saveStateForUndo() 를 불렀다. 그러면 undo 스택 top 은
	//        현재 화면과 같은 상태라서 Ctrl+Z 를 한 번 눌러도 아무 변화가 없고, 두 번 눌러야 한 단계 돌아갔다.
	// To-Be: 규칙은 하나 - "도형 목록을 바꾸기 직전에 saveStateForUndo()". undo() 는 현재 상태를 redo 에
	//        넣고 undo top 을 복원, redo() 는 그 반대. 호출처: startTransform(그리기/변환 시작),
	//        removeSelectedShapes, groupSelectedShapes, ungroupSelectedShapes, pasteShapes,
	//        setSelectedShapeFillColor, bringToFront, sendToBack, replaceShape
	private void saveStateForUndo() {
		undoStack.push(deepCopy(this.shapes));
		redoStack.clear();
	}

	public void undo() {
		if (!undoStack.isEmpty()) {
			redoStack.push(deepCopy(this.shapes));
			List<GShape> prev = undoStack.pop();
			this.shapes.clear();
			this.shapes.addAll(prev);
			this.clearSelection();
			this.bUpdated = true;
			this.repaint();
		}
	}

	public void redo() {
		if (!redoStack.isEmpty()) {
			undoStack.push(deepCopy(this.shapes));
			List<GShape> next = redoStack.pop();
			this.shapes.clear();
			this.shapes.addAll(next);
			this.clearSelection();
			this.bUpdated = true;
			this.repaint();
		}
	}

	// deepCopy 내부 함수
	private List<GShape> deepCopy(List<GShape> list) {
		List<GShape> copy = new ArrayList<>();
		for (GShape shape : list) {
			GShape c = shape.clone();
			if (c != null) {
				copy.add(c);
			}
		}
		return copy;
	}

	// Z - Order
	// Move Front
	public void bringToFront() {
		List<GShape> sel = getSelectedShapes();
		if (sel.isEmpty())
			return;
		saveStateForUndo();
		// shapes 리스트에서 삭제 후 맨 끝에 추가
		for (GShape s : sel) {
			shapes.remove(s);
			shapes.add(s);
		}
		this.bUpdated = true;
		repaint();
	}

	// Move -Back
	public void sendToBack() {
		List<GShape> sel = getSelectedShapes();
		if (sel.isEmpty())
			return;
		saveStateForUndo();
		// shapes 리스트에서 삭제 후 맨 앞에 추가 (역순으로)
		for (int i = sel.size() - 1; i >= 0; i--) {
			GShape s = sel.get(i);
			shapes.remove(s);
			shapes.add(0, s);
		}
		this.bUpdated = true;
		repaint();
	}

	// [2026-10-18 수정자: Jung Minkyu] FEAT-01
	// 추천 도형 적용: oldShape 가 있던 자리(z-order)에 newShape 를 넣는다. Undo 가능.
	// 연관: frames/GRecommendPanel.apply()
	public boolean replaceShape(GShape oldShape, GShape newShape) {
		int index = shapes.indexOf(oldShape);
		if (index < 0 || newShape == null) {
			return false;
		}
		saveStateForUndo();
		shapes.set(index, newShape);
		clearSelection();
		newShape.setSelected(true);
		this.bUpdated = true;
		repaint();
		return true;
	}

	// Paint
	public void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g2d = (Graphics2D) graphics;
		for (GShape shape : shapes) {
			shape.draw(g2d);
		}
		// FIX-02: 러버밴드는 도형 목록과 별개로 점선으로 그린다
		if (rubberBand != null) {
			Stroke old = g2d.getStroke();
			Color prev = g2d.getColor();
			g2d.setColor(Color.GRAY);
			g2d.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 4f }, 0f));
			g2d.draw(rubberBand.getTransformedShape());
			g2d.setStroke(old);
			g2d.setColor(prev);
		}
	}

	// Check
	// [2026-10-18 수정자: Jung Minkyu] FIX-05
	// As-Is: shapes 를 앞(0)부터 돌아 맨 "아래" 도형이 먼저 잡혔다. 겹친 도형은 위에 보이는 것을
	//        클릭해도 아래 것이 선택되었다 (bringToFront 와도 모순).
	// To-Be: 그리는 순서의 역순(위→아래)으로 검사해 화면에서 보이는 도형을 잡는다.
	private GShape onShape(int x, int y) {
		for (int i = shapes.size() - 1; i >= 0; i--) {
			GShape shape = shapes.get(i);
			if (shape.contains(x, y)) {
				return shape;
			}
		}
		return null;
	}

	/** 너비/높이가 모두 0 인 도형(클릭만 하고 끝낸 경우)인지 */
	private boolean isEmptyShape(GShape shape) {
		Rectangle b = shape.getBounds();
		return b.width == 0 && b.height == 0;
	}

	// Transform
	// [2026-10-18 수정자: Jung Minkyu] FIX-02 / FIX-03
	// As-Is: 선택 도구는 항상 GRectangle 을 shapes 에 추가한 뒤 분기했다 (러버밴드가 도형 목록에 섞임).
	// To-Be: (1) 빈 곳 클릭 → 러버밴드(목록 밖) (2) 도형/앵커 클릭 → 선택 유지한 채 변환 시작.
	//        변환/그리기 시작 직전에 saveStateForUndo() 로 스냅샷을 찍는다.
	//        여러 개가 선택된 상태에서 그중 하나의 본체를 끌면 선택된 전부를 함께 옮긴다.
	private void startTransform(int x, int y) {
		if (this.eShapeTool == EShapeTool.eSelect) {
			GShape hit = onShape(x, y);
			if (hit == null) {
				this.selectedShape = null;
				this.rubberBand = eShapeTool.newShape();
				this.currentShape = this.rubberBand;
				this.transformer = new GDrawer(this.rubberBand);
			} else {
				if (!hit.isSelected()) {
					// 선택되지 않은 도형을 클릭: 그 도형만 선택 (clearSelection 이 selectedShape 를 null 로 만들므로 지역변수 사용)
					clearSelection();
					hit.setSelected(true);
					hit.setESelectedAnchor(EAnchor.eMM);
				}
				this.selectedShape = hit;
				saveStateForUndo();
				this.currentShape = this.selectedShape;
				EAnchor anchor = this.selectedShape.getESelectedAnchor();
				if (anchor == EAnchor.eMM) {
					List<GShape> sel = getSelectedShapes();
					this.transformer = (sel.size() > 1) ? new GMover(sel) : new GMover(this.selectedShape);
				} else if (anchor == EAnchor.eRR) {
					this.transformer = new GRotater(this.selectedShape);
				} else {
					this.transformer = new GResizer(this.selectedShape);
				}
			}
		} else {
			saveStateForUndo();
			this.currentShape = eShapeTool.newShape();
			this.shapes.add(this.currentShape);
			this.transformer = new GDrawer(this.currentShape);
		}
		this.transformer.start((Graphics2D) getGraphics(), x, y);
	}

	private void keepTransform(int x, int y) {
		this.transformer.drag((Graphics2D) getGraphics(), x, y);
		this.repaint();
	}

	private void finishTransform(int x, int y) {
		this.transformer.finish((Graphics2D) getGraphics(), x, y);

		if (this.eShapeTool == EShapeTool.eSelect) {
			if (this.rubberBand != null) {
				// 러버밴드 선택: 완전히 포함된 도형만 선택 (단순 클릭이면 전부 해제)
				boolean emptyBand = isEmptyShape(this.rubberBand);
				for (GShape shape : this.shapes) {
					shape.setSelected(!emptyBand && this.rubberBand.contains(shape));
					shape.setESelectedAnchor(null);
				}
				this.rubberBand = null;
				this.selectedShape = null;
			} else {
				// 이동/리사이즈/회전 완료: 선택 유지 (FIX-02)
				this.bUpdated = true;
			}
		} else if (this.eShapeTool == EShapeTool.eTextArea) {
			String text = JOptionPane.showInputDialog(this, "텍스트를 입력하세요:");
			if (text == null) {
				// 취소: 방금 만든 글 상자와 스냅샷을 되돌린다
				this.shapes.remove(this.currentShape);
				if (!undoStack.isEmpty())
					undoStack.pop();
			} else {
				((GTextArea) this.currentShape).setText(text);
				clearSelection();
				this.currentShape.setSelected(true);
				this.bUpdated = true;
			}
		} else {
			if (isEmptyShape(this.currentShape)) {
				// 클릭만 하고 끝낸 0 크기 도형은 남기지 않는다
				this.shapes.remove(this.currentShape);
				if (!undoStack.isEmpty())
					undoStack.pop();
			} else {
				clearSelection();
				this.currentShape.setSelected(true); // 방금 그린 도형만 선택
				this.bUpdated = true;
				// FEAT-01: 자유선이면 추천 사이드바에 알린다
				if (this.currentShape instanceof GFreeLine && this.recommendListener != null) {
					this.recommendListener.onStrokeFinished((GFreeLine) this.currentShape);
				}
			}
		}
		this.currentShape = null;
		this.transformer = null;
		this.repaint();
	}

	// For Polygon
	private void addPoint(int x, int y) {
		this.transformer.addPoint((Graphics2D) getGraphics(), x, y);
	}

	private void changeCursor(int x, int y) {
		if (this.eShapeTool != EShapeTool.eSelect) {
			setCursor(Cursor.getDefaultCursor());
			return;
		}

		GShape hovered = onShape(x, y);
		if (hovered == null) {
			setCursor(Cursor.getDefaultCursor());
			return;
		}
		// 앵커는 선택된 도형에만 보이므로 선택된 도형에서만 앵커 커서를 판정한다
		EAnchor hit = hovered.isSelected() ? hovered.hitTestAnchor(x, y) : EAnchor.eMM;
		setCursor(hit != null ? hit.getCursor() : Cursor.getDefaultCursor());
	}

	private class MouseEventHandler implements MouseListener, MouseMotionListener {

		@Override
		public void mouseClicked(MouseEvent e) {
			if (e.getClickCount() == 1) {
				this.mouse1Clicked(e);
			} else if (e.getClickCount() == 2) {
				this.mouse2Clicked(e);
			}
		}

		@Override
		public void mouseMoved(MouseEvent e) {
			if (eDrawingState == EDrawingState.e2P) {
				keepTransform(e.getX(), e.getY());
			} else if (eDrawingState == EDrawingState.eNP) {
				keepTransform(e.getX(), e.getY());
			} // Idle Statements. but explicitly codes is essential
			else if (eDrawingState == EDrawingState.eIdle) {
				changeCursor(e.getX(), e.getY());
			}
		}

		// [2026-10-18 수정자: Jung Minkyu] FIX-04 더블클릭이 마지막 도형을 지움
		// As-Is: 상태와 무관하게 finishTransform() 을 불렀다. 더블클릭은 clickCount=1 이벤트 뒤에
		//        clickCount=2 이벤트가 따로 오므로, 2점 도형은 첫 클릭에서 이미 finish 되어 Idle 인데
		//        한 번 더 finish 되었다. 선택 도구에서는 shapes.remove(size-1) 로 진짜 도형이 삭제되고,
		//        transformer/currentShape 가 null 이면 NPE 가 났다.
		// To-Be: 진행 중(e2P/eNP)일 때만 finish 한다. Idle 이면 무시.
		// 연관: FIX-02 (러버밴드 분리로 remove(size-1) 자체가 사라짐)
		private void mouse2Clicked(MouseEvent e) {
			if (eDrawingState == EDrawingState.eIdle || transformer == null) {
				return;
			}
			finishTransform(e.getX(), e.getY());
			eDrawingState = EDrawingState.eIdle;
		}

		private void mouse1Clicked(MouseEvent e) {
			if (eShapeTool == null) {
				return;
			}
			if (eDrawingState == EDrawingState.eIdle) {
				if (eShapeTool.getEPoints() == EPoints.e2P) {
					startTransform(e.getX(), e.getY());
					eDrawingState = EDrawingState.e2P;
				} else if (eShapeTool.getEPoints() == EPoints.eNP) {
					startTransform(e.getX(), e.getY());
					eDrawingState = EDrawingState.eNP;
				}
			} else if (eDrawingState == EDrawingState.e2P) {
				finishTransform(e.getX(), e.getY());
				eDrawingState = EDrawingState.eIdle;
			} else if (eDrawingState == EDrawingState.eNP) {
				addPoint(e.getX(), e.getY());
			}
		}

		@Override
		public void mouseDragged(MouseEvent e) {

		}

		@Override
		public void mousePressed(MouseEvent e) {

		}

		@Override
		public void mouseReleased(MouseEvent e) {

		}

		@Override
		public void mouseEntered(MouseEvent e) {

		}

		@Override
		public void mouseExited(MouseEvent e) {

		}

	}

	// Key Setter
	private void setCommandKey(InputMap im, ActionMap am) {
		// Undo (Ctrl+Z)
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
		am.put("undo", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				undo();
			}
		});

		// Redo (Ctrl+Y)
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
		am.put("redo", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				redo();
			}
		});

		// Copy (Ctrl+C)
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
		am.put("copy", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				copySelectedShapes();
			}
		});

		// Paste (Ctrl+V)
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
		am.put("paste", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				pasteShapes();
			}
		});

		// Delete (Delete)
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
		am.put("delete", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				removeSelectedShapes();
			}
		});

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "toFront");
		am.put("toFront", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent e) {
				bringToFront();
			}
		});

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), "toBack");
		am.put("toBack", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent e) {
				sendToBack();
			}
		});

	}

	public void cropSelectedArea() {
		// TODO 미구현 기능 (툴바 버튼만 존재). 구조 진단 문서의 To-Be 참고.
	}

}

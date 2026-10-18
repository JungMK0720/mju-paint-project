package frames;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

import global.GConstants.ERecommend;
import global.GConstants.GRecommender;
import recommend.ESuggestShape;
import recommend.GGeometricRecognizer;
import recommend.GOllamaRecognizer;
import recommend.GRecommendListener;
import recommend.GShapeRecognizer;
import recommend.GShapeSuggestion;
import recommend.GStrokeFeatures;
import shapes.GFreeLine;
import shapes.GShape;

// [2026-10-18 수정자: Jung Minkyu] FEAT-01 연관 도형 추천 사이드바
// 자유선을 다 그리면(GRecommendListener.onStrokeFinished) 두 전략으로 분석한다.
//   1) GGeometricRecognizer : 즉시 결과 표시 (EDT 에서 계산, 수 ms)
//   2) GOllamaRecognizer    : SwingWorker 로 백그라운드 호출. 성공하면 기하 점수와 가중 합산해 다시 표시,
//                             실패(Ollama 미실행 등)하면 상태줄에만 알리고 기하 결과를 유지한다.
// 항목을 클릭하면 GDrawingPanel.replaceShape() 로 자유선을 깔끔한 도형으로 바꾼다 (Undo 가능).
// 연관: frames/GDrawingPanel.setRecommendListener()/replaceShape(), frames/GMainFrame (EAST 배치),
//       manager/GPanelManager.associate(), global/GConstants.ERecommend (라벨), GRecommender (Ollama 설정)
public class GRecommendPanel extends JPanel implements GRecommendListener {
	private static final long serialVersionUID = 1L;

	private static final int PANEL_WIDTH = 230;
	private static final double GEOMETRIC_WEIGHT = 0.4;
	private static final double LLM_WEIGHT = 0.6;

	// association
	private GDrawingPanel drawingPanel;

	// strategies
	private final GShapeRecognizer geometric = new GGeometricRecognizer();
	private final GOllamaRecognizer llm = new GOllamaRecognizer();

	// components
	private final JCheckBox useLLM;
	private final JLabel status;
	private final JPanel listPanel;
	private final JLabel engineLabel;

	// state
	private GFreeLine currentStroke;
	private List<GShapeSuggestion> geometricResult = new ArrayList<>();
	private SwingWorker<List<GShapeSuggestion>, Void> worker;
	private int requestSeq = 0;

	public GRecommendPanel() {
		this.setLayout(new BorderLayout(4, 4));
		this.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
		this.setBorder(BorderFactory.createTitledBorder(ERecommend.eTitle.getLabel()));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		this.useLLM = new JCheckBox(ERecommend.eUseLLM.getLabel(), GRecommender.isEnabled());
		this.useLLM.setToolTipText(GRecommender.getUrl() + " / " + GRecommender.getModel());
		this.engineLabel = new JLabel(" ");
		this.engineLabel.setFont(engineLabel.getFont().deriveFont(10f));
		this.engineLabel.setForeground(Color.DARK_GRAY);
		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		JButton reanalyze = new JButton(ERecommend.eReanalyze.getLabel());
		reanalyze.addActionListener(e -> reanalyzeSelected());
		JButton keep = new JButton(ERecommend.eKeep.getLabel());
		keep.addActionListener(e -> {
			clearSuggestions();
			setStatus(ERecommend.eStatusIdle);
		});
		buttons.add(reanalyze);
		buttons.add(Box.createHorizontalStrut(4));
		buttons.add(keep);
		top.add(useLLM);
		top.add(engineLabel);
		top.add(buttons);
		this.add(top, BorderLayout.NORTH);

		this.listPanel = new JPanel();
		this.listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		JPanel listHolder = new JPanel(new BorderLayout());
		listHolder.add(listPanel, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(listHolder);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		this.add(scroll, BorderLayout.CENTER);

		this.status = new JLabel();
		this.status.setFont(status.getFont().deriveFont(11f));
		this.status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		this.add(status, BorderLayout.SOUTH);
	}

	public void initialize() {
		setStatus(ERecommend.eStatusIdle);
		clearSuggestions();
	}

	// association: GDrawingPanel 이 자유선을 끝내면 이 패널에 알리도록 연결
	public void associate(GDrawingPanel drawingPanel) {
		this.drawingPanel = drawingPanel;
		if (drawingPanel != null) {
			drawingPanel.setRecommendListener(this);
		}
	}

	// ---- GRecommendListener ----
	@Override
	public void onStrokeFinished(GFreeLine freeLine) {
		analyze(freeLine);
	}

	// ---- actions ----
	private void reanalyzeSelected() {
		if (drawingPanel == null) {
			return;
		}
		for (GShape s : drawingPanel.getSelectedShapes()) {
			if (s instanceof GFreeLine) {
				analyze((GFreeLine) s);
				return;
			}
		}
		setStatus(ERecommend.eStatusNoStroke);
	}

	private void analyze(GFreeLine line) {
		this.currentStroke = line;
		GStrokeFeatures features = GStrokeFeatures.of(line.getPoints());
		if (features == null) {
			clearSuggestions();
			setStatus(ERecommend.eStatusIdle);
			return;
		}
		try {
			this.geometricResult = geometric.recognize(features);
		} catch (Exception e) {
			this.geometricResult = new ArrayList<>();
		}
		render(geometricResult);
		setStatus(ERecommend.eStatusGeometric);
		engineLabel.setText(geometric.getName() + "  " + features.toPromptText().split(",")[1].trim());

		if (useLLM.isSelected()) {
			runLLM(features, ++requestSeq);
		}
	}

	private void runLLM(GStrokeFeatures features, int seq) {
		if (worker != null && !worker.isDone()) {
			worker.cancel(true);
		}
		setStatus(ERecommend.eStatusAnalyzing);
		worker = new SwingWorker<List<GShapeSuggestion>, Void>() {
			@Override
			protected List<GShapeSuggestion> doInBackground() throws Exception {
				return llm.recognize(features);
			}

			@Override
			protected void done() {
				if (seq != requestSeq || isCancelled()) {
					return; // 더 새로운 요청이 있으면 무시
				}
				try {
					List<GShapeSuggestion> llmResult = get();
					render(merge(geometricResult, llmResult));
					setStatus(ERecommend.eStatusLLM);
					engineLabel.setText(geometric.getName() + " + " + llm.getName());
				} catch (Exception e) {
					Throwable cause = (e.getCause() != null) ? e.getCause() : e;
					System.out.println("[recommend] Ollama unavailable: " + cause);
					setStatus(ERecommend.eStatusLLMFail);
					engineLabel.setText(geometric.getName() + " (" + llm.getName() + " x)");
				}
			}
		};
		worker.execute();
	}

	/** 기하 점수와 LLM 점수의 가중 합. LLM 이 빠뜨린 후보는 기하 점수만 반영 */
	static List<GShapeSuggestion> merge(List<GShapeSuggestion> geo, List<GShapeSuggestion> llm) {
		Map<ESuggestShape, Double> geoMap = new EnumMap<>(ESuggestShape.class);
		Map<ESuggestShape, Double> llmMap = new EnumMap<>(ESuggestShape.class);
		for (GShapeSuggestion g : geo) {
			geoMap.put(g.getShape(), g.getScore());
		}
		for (GShapeSuggestion l : llm) {
			llmMap.put(l.getShape(), l.getScore());
		}
		List<GShapeSuggestion> out = new ArrayList<>();
		for (ESuggestShape shape : ESuggestShape.values()) {
			double g = geoMap.getOrDefault(shape, 0.0);
			Double l = llmMap.get(shape);
			double score = (l == null) ? g * GEOMETRIC_WEIGHT : g * GEOMETRIC_WEIGHT + l * LLM_WEIGHT;
			out.add(new GShapeSuggestion(shape, score, (l == null) ? GGeometricRecognizer.SOURCE : "merged"));
		}
		java.util.Collections.sort(out);
		return out;
	}

	private void apply(GShapeSuggestion suggestion) {
		if (drawingPanel == null || currentStroke == null) {
			setStatus(ERecommend.eStatusNoStroke);
			return;
		}
		// 자유선을 그린 뒤 이동/회전했을 수 있으므로 적용 시점의 좌표로 다시 계산
		GStrokeFeatures features = GStrokeFeatures.of(currentStroke.getPoints());
		if (features == null) {
			setStatus(ERecommend.eStatusNoStroke);
			return;
		}
		GShape replacement = suggestion.getShape().build(features);
		replacement.setFillColor(currentStroke.getFillColor());
		if (drawingPanel.replaceShape(currentStroke, replacement)) {
			currentStroke = null;
			clearSuggestions();
			setStatus(ERecommend.eStatusApplied);
		} else {
			setStatus(ERecommend.eStatusNoStroke);
		}
	}

	// ---- view ----
	private void render(List<GShapeSuggestion> suggestions) {
		listPanel.removeAll();
		for (GShapeSuggestion s : suggestions) {
			JButton item = new JButton(String.format("%s  %d%%", s.getShape().getLabel(), Math.round(s.getScore() * 100)),
					new ShapeIcon(s.getShape()));
			item.setHorizontalAlignment(JButton.LEFT);
			item.setAlignmentX(Component.LEFT_ALIGNMENT);
			item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
			item.setToolTipText(s.getSource());
			item.addActionListener(e -> apply(s));
			listPanel.add(item);
			listPanel.add(Box.createVerticalStrut(3));
		}
		listPanel.revalidate();
		listPanel.repaint();
	}

	private void clearSuggestions() {
		listPanel.removeAll();
		listPanel.revalidate();
		listPanel.repaint();
		engineLabel.setText(" ");
	}

	private void setStatus(ERecommend key) {
		status.setText("<html><body style='width:190px'>" + key.getLabel() + "</body></html>");
	}

	/** 후보 도형 미리보기 아이콘 */
	private static class ShapeIcon implements Icon {
		private static final int W = 28, H = 20;
		private final ESuggestShape kind;

		ShapeIcon(ESuggestShape kind) {
			this.kind = kind;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(Color.DARK_GRAY);
			int x1 = x + 2, y1 = y + 2, w = W - 4, h = H - 4;
			switch (kind) {
			case eLine:
				g2.drawLine(x1, y1 + h, x1 + w, y1);
				break;
			case eRectangle:
				g2.drawRect(x1, y1, w, h);
				break;
			case eEllipse:
				g2.draw(new Ellipse2D.Double(x1, y1, w, h));
				break;
			case eTriangle:
				g2.drawPolygon(new int[] { x1 + w / 2, x1 + w, x1 }, new int[] { y1, y1 + h, y1 + h }, 3);
				break;
			case ePolygon:
			default:
				g2.drawPolygon(new int[] { x1 + w / 2, x1 + w, x1 + (int) (w * 0.8), x1 + (int) (w * 0.2), x1 },
						new int[] { y1, y1 + (int) (h * 0.4), y1 + h, y1 + h, y1 + (int) (h * 0.4) }, 5);
				break;
			}
			g2.dispose();
		}

		@Override
		public int getIconWidth() {
			return W;
		}

		@Override
		public int getIconHeight() {
			return H;
		}
	}
}

package recommend;

// [2026-10-18 수정자: Jung Minkyu] FEAT-01
// 추천 결과 한 건: 어떤 도형(ESuggestShape)을 얼마나(score 0~1) 확신하는지, 어느 엔진이 냈는지(source).
public class GShapeSuggestion implements Comparable<GShapeSuggestion> {
	private final ESuggestShape shape;
	private final double score;
	private final String source;

	public GShapeSuggestion(ESuggestShape shape, double score, String source) {
		this.shape = shape;
		this.score = Math.max(0.0, Math.min(1.0, score));
		this.source = source;
	}

	public ESuggestShape getShape() {
		return shape;
	}

	public double getScore() {
		return score;
	}

	public String getSource() {
		return source;
	}

	@Override
	public int compareTo(GShapeSuggestion o) {
		return Double.compare(o.score, this.score); // 내림차순
	}

	@Override
	public String toString() {
		return String.format("%s(%.2f,%s)", shape.name(), score, source);
	}
}

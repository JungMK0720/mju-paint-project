package recommend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import global.GConstants.GRecommender;

// [2026-10-18 수정자: Jung Minkyu] FEAT-01 로컬 LLM 인식기
// 로컬에서 실행 중인 Ollama(https://ollama.com) 의 HTTP API(/api/generate) 에
// 자유선 특징을 텍스트로 보내고 후보 도형 순위를 JSON 으로 받는다.
//   - 외부 네트워크/클라우드 호출 없음: 기본 주소 http://localhost:11434
//   - 모델/주소/타임아웃은 GConstants.GRecommender (XML 또는 -Dpaint.ollama.* 시스템 속성) 로 바꾼다
//   - Ollama 가 꺼져 있으면 예외를 던지고, 호출자(GRecommendPanel)가 기하 휴리스틱 결과로 fallback 한다
// 연관: global/GConstants.GRecommender, recommend/GJson.java, frames/GRecommendPanel.runLLM()
public class GOllamaRecognizer implements GShapeRecognizer {

	public static final String SOURCE = "ollama";

	private final String baseUrl;
	private final String model;
	private final int timeoutMs;
	private final HttpClient client;

	public GOllamaRecognizer() {
		this(GRecommender.getUrl(), GRecommender.getModel(), GRecommender.getTimeoutMs());
	}

	public GOllamaRecognizer(String baseUrl, String model, int timeoutMs) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.model = model;
		this.timeoutMs = timeoutMs;
		this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 2000))).build();
	}

	@Override
	public String getName() {
		return SOURCE + ":" + model;
	}

	public String getModel() {
		return model;
	}

	@Override
	public List<GShapeSuggestion> recognize(GStrokeFeatures features) throws Exception {
		String body = buildRequestBody(features);
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
				.timeout(Duration.ofMillis(timeoutMs)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
		HttpResponse<String> response = client.send(request,
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() != 200) {
			throw new IOException("Ollama HTTP " + response.statusCode() + ": " + response.body());
		}
		Object root = GJson.parse(response.body());
		if (!(root instanceof Map)) {
			throw new IOException("unexpected Ollama response: " + response.body());
		}
		Object text = ((Map<?, ?>) root).get("response");
		if (!(text instanceof String)) {
			throw new IOException("Ollama response has no 'response' field");
		}
		List<GShapeSuggestion> result = parseSuggestions((String) text);
		if (result.isEmpty()) {
			throw new IOException("Ollama returned no usable suggestions: " + text);
		}
		return result;
	}

	String buildRequestBody(GStrokeFeatures f) {
		StringBuilder sb = new StringBuilder();
		sb.append('{');
		sb.append("\"model\":").append(GJson.quote(model)).append(',');
		sb.append("\"prompt\":").append(GJson.quote(buildPrompt(f))).append(',');
		sb.append("\"stream\":false,");
		sb.append("\"format\":\"json\",");
		sb.append("\"options\":{\"temperature\":0}");
		sb.append('}');
		return sb.toString();
	}

	static String buildPrompt(GStrokeFeatures f) {
		return "You are a sketch-recognition assistant inside a vector drawing program.\n"
				+ "A user drew ONE freehand stroke. Decide which clean shape the user most likely intended.\n"
				+ "Stroke features: " + f.toPromptText() + "\n"
				+ "Meaning: closed=true means the start and end meet; corners = vertices left after line simplification; "
				+ "straightness near 1 = straight line; circularity near 1 = circle-like (square 0.79, triangle 0.6); "
				+ "rectangularity near 1 = fills its bounding box (rectangle 1.0, ellipse 0.79, triangle 0.5).\n"
				+ "Candidates: LINE, RECTANGLE, ELLIPSE, TRIANGLE, POLYGON.\n"
				+ "Respond with JSON only, exactly this form, include all five candidates with scores from 0 to 1, "
				+ "sorted by score descending:\n"
				+ "{\"suggestions\":[{\"shape\":\"RECTANGLE\",\"score\":0.9},{\"shape\":\"ELLIPSE\",\"score\":0.3}]}";
	}

	/** 모델 출력(JSON 문자열)을 추천 목록으로. 형식이 조금 달라도 최대한 읽어낸다. */
	static List<GShapeSuggestion> parseSuggestions(String json) {
		List<GShapeSuggestion> out = new ArrayList<>();
		Object root;
		try {
			root = GJson.parse(json.trim());
		} catch (RuntimeException e) {
			return out;
		}
		List<?> items = null;
		if (root instanceof Map) {
			Object s = ((Map<?, ?>) root).get("suggestions");
			if (s instanceof List) {
				items = (List<?>) s;
			} else if (((Map<?, ?>) root).containsKey("shape")) {
				items = Collections.singletonList(root);
			}
		} else if (root instanceof List) {
			items = (List<?>) root;
		}
		if (items == null) {
			return out;
		}
		for (Object item : items) {
			if (!(item instanceof Map)) {
				continue;
			}
			Map<?, ?> m = (Map<?, ?>) item;
			Object name = m.get("shape");
			if (name == null) {
				name = m.get("name");
			}
			ESuggestShape shape = ESuggestShape.fromName(name == null ? null : name.toString());
			if (shape == null) {
				continue;
			}
			double score = 0;
			Object sc = m.get("score");
			if (sc == null) {
				sc = m.get("confidence");
			}
			if (sc instanceof Number) {
				score = ((Number) sc).doubleValue();
			} else if (sc != null) {
				try {
					score = Double.parseDouble(sc.toString());
				} catch (NumberFormatException ignored) {
				}
			}
			if (score > 1.0) {
				score = score / 100.0; // 퍼센트로 답한 경우
			}
			boolean dup = false;
			for (GShapeSuggestion g : out) {
				if (g.getShape() == shape) {
					dup = true;
				}
			}
			if (!dup) {
				out.add(new GShapeSuggestion(shape, score, SOURCE));
			}
		}
		Collections.sort(out);
		return out;
	}
}

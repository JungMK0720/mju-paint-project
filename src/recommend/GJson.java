package recommend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// [2026-10-18 수정자: Jung Minkyu] FEAT-01
// 외부 라이브러리 없이 Ollama 응답을 읽기 위한 최소 JSON 파서/인코더.
// 객체 → Map<String,Object>, 배열 → List<Object>, 문자열 → String, 숫자 → Double, true/false → Boolean, null → null
// 연관: recommend/GOllamaRecognizer.java
public final class GJson {

	private final String s;
	private int i;

	private GJson(String s) {
		this.s = s;
		this.i = 0;
	}

	public static Object parse(String text) {
		if (text == null) {
			throw new IllegalArgumentException("json is null");
		}
		GJson p = new GJson(text);
		p.ws();
		Object v = p.value();
		p.ws();
		return v;
	}

	/** 문자열을 JSON 문자열 리터럴로 인코딩 (따옴표 포함) */
	public static String quote(String text) {
		StringBuilder sb = new StringBuilder("\"");
		for (int k = 0; k < text.length(); k++) {
			char c = text.charAt(k);
			switch (c) {
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			default:
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
		}
		return sb.append('"').toString();
	}

	private void ws() {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
	}

	private Object value() {
		if (i >= s.length()) {
			throw err("unexpected end");
		}
		char c = s.charAt(i);
		if (c == '{') {
			return object();
		}
		if (c == '[') {
			return array();
		}
		if (c == '"') {
			return string();
		}
		if (s.startsWith("true", i)) {
			i += 4;
			return Boolean.TRUE;
		}
		if (s.startsWith("false", i)) {
			i += 5;
			return Boolean.FALSE;
		}
		if (s.startsWith("null", i)) {
			i += 4;
			return null;
		}
		return number();
	}

	private Map<String, Object> object() {
		Map<String, Object> m = new LinkedHashMap<>();
		i++; // {
		ws();
		if (peek() == '}') {
			i++;
			return m;
		}
		while (true) {
			ws();
			if (peek() != '"') {
				throw err("expected key");
			}
			String k = string();
			ws();
			if (peek() != ':') {
				throw err("expected ':'");
			}
			i++;
			ws();
			m.put(k, value());
			ws();
			char c = peek();
			if (c == ',') {
				i++;
				continue;
			}
			if (c == '}') {
				i++;
				return m;
			}
			throw err("expected ',' or '}'");
		}
	}

	private List<Object> array() {
		List<Object> l = new ArrayList<>();
		i++; // [
		ws();
		if (peek() == ']') {
			i++;
			return l;
		}
		while (true) {
			ws();
			l.add(value());
			ws();
			char c = peek();
			if (c == ',') {
				i++;
				continue;
			}
			if (c == ']') {
				i++;
				return l;
			}
			throw err("expected ',' or ']'");
		}
	}

	private String string() {
		StringBuilder sb = new StringBuilder();
		i++; // opening quote
		while (i < s.length()) {
			char c = s.charAt(i++);
			if (c == '"') {
				return sb.toString();
			}
			if (c == '\\') {
				if (i >= s.length()) {
					break;
				}
				char e = s.charAt(i++);
				switch (e) {
				case '"':
				case '\\':
				case '/':
					sb.append(e);
					break;
				case 'b':
					sb.append('\b');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'n':
					sb.append('\n');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 't':
					sb.append('\t');
					break;
				case 'u':
					if (i + 4 > s.length()) {
						throw err("bad unicode escape");
					}
					sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
					i += 4;
					break;
				default:
					sb.append(e);
				}
			} else {
				sb.append(c);
			}
		}
		throw err("unterminated string");
	}

	private Double number() {
		int start = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
			i++;
		}
		if (start == i) {
			throw err("unexpected character '" + s.charAt(i) + "'");
		}
		try {
			return Double.valueOf(s.substring(start, i));
		} catch (NumberFormatException e) {
			throw err("bad number");
		}
	}

	private char peek() {
		if (i >= s.length()) {
			throw err("unexpected end");
		}
		return s.charAt(i);
	}

	private IllegalArgumentException err(String msg) {
		return new IllegalArgumentException("JSON parse error at " + i + ": " + msg);
	}
}

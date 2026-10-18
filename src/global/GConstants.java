package global;

import java.awt.Color;
import java.awt.Cursor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import shapes.GShape;
import shapes.GShape.EPoints;

public final class GConstants {

	public GConstants() {
	}

	// File Path
	public static final class GFilePath {
		public static String DEFAULT_DIR = "E:\\4-1\\MJU_Pattern\\Drawing";
//		public static final String DEFAULT_DIR = "";
	}

	public static void readFromFile(String fileName) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			// Load the input XML document, parse it and return an instance of the
			// Document class.
			File file = new File(fileName);
			Document document = builder.parse(file);
			NodeList nodeList = document.getDocumentElement().getChildNodes();
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					if (node.getNodeName().equals(EMainFrame.class.getSimpleName())) {
						EMainFrame.setValues(node);
					} else if (node.getNodeName().equals(EHelpMenuItem.class.getSimpleName())) {
						EHelpMenuItem.setValues(node);
					} else if (node.getNodeName().equals(EMenu.class.getSimpleName())) {
						EMenu.setValues(node);
					} else if (node.getNodeName().equals(EFileMenuItem.class.getSimpleName())) {
						EFileMenuItem.setValues(node);
					} else if (node.getNodeName().equals(EEditMenuItem.class.getSimpleName())) {
						EEditMenuItem.setValues(node);
					} else if (node.getNodeName().equals(EToolBarGroup.class.getSimpleName())) {
						EToolBarGroup.setValues(node);
					} else if (node.getNodeName().equals(EShapeTool.class.getSimpleName())) {
						EShapeTool.setValues(node);
					} else if (node.getNodeName().equals(EFunctionTool.class.getSimpleName())) {
						EFunctionTool.setValues(node);
					} else if (node.getNodeName().equals(EElement.class.getSimpleName())) {
						EElement.setValues(node);
					} else if (node.getNodeName().equals(ERecommend.class.getSimpleName())) {
						ERecommend.setValues(node); // FEAT-01
					} else if (node.getNodeName().equals(GRecommender.class.getSimpleName())) {
						GRecommender.setValues(node); // FEAT-01
					}
				}
			}
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public enum EAnchor {
		eNN(new Cursor(Cursor.N_RESIZE_CURSOR)), eNE(new Cursor(Cursor.NE_RESIZE_CURSOR)),
		eNW(new Cursor(Cursor.NW_RESIZE_CURSOR)), eSS(new Cursor(Cursor.S_RESIZE_CURSOR)),
		eSE(new Cursor(Cursor.SE_RESIZE_CURSOR)), eSW(new Cursor(Cursor.SW_RESIZE_CURSOR)),
		eEE(new Cursor(Cursor.E_RESIZE_CURSOR)), eWW(new Cursor(Cursor.W_RESIZE_CURSOR)),
		eRR(new Cursor(Cursor.HAND_CURSOR)), eMM(new Cursor(Cursor.MOVE_CURSOR));

		private Cursor cursor;

		private EAnchor(Cursor cursor) {
			this.cursor = cursor;
		}

		public Cursor getCursor() {
			return this.cursor;
		}
	}

	public enum EMainFrame {
		eX(0), eY(0), eW(0), eH(0);

		private int value;

		private EMainFrame(int value) {
			this.value = value;
		}

		public int getValue() {
			return this.value;
		}

		public static void setValues(Node node) {
			for (EMainFrame eMainFrame : EMainFrame.values()) {
				Node attribute = node.getAttributes().getNamedItem(eMainFrame.name());
				System.out.println(node.getAttributes().getNamedItem(eMainFrame.name()));
				eMainFrame.value = Integer.parseInt(attribute.getNodeValue());
			}

		}

	}

	public enum EHelpMenuItem {
		eShortcut("", "showHelpDialog");

		private String label;
		private String methodName;

		private EHelpMenuItem(String label, String methodName) {
			this.label = label;
			this.methodName = methodName;
		}

		public String getLabel() {
			return label;
		}

		public String getMethodName() {
			return methodName;
		}

		public static void setValues(Node node) {
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() != Node.ELEMENT_NODE)
					continue;
				String nodeName = child.getNodeName(); // ex: eShortcut
				String label = child.getAttributes().getNamedItem("label").getNodeValue();
				String method = child.getAttributes().getNamedItem("methodName").getNodeValue();
				for (EHelpMenuItem item : values()) {
					if (item.name().equals(nodeName)) {
						item.label = label;
						item.methodName = method;
					}
				}
			}
		}
	}

	public enum EHelpEntry {
		eUndo("Ctrl+Z", "실행 취소 (Undo)"), eRedo("Ctrl+Y", "다시 실행 (Redo)"), eCopy("Ctrl+C", "복사"),
		ePaste("Ctrl+V", "붙여넣기"), eDelete("Delete", "선택 항목 삭제"), eToFront("Ctrl+F", "앞으로 보내기 (Bring to Front)"),
		eToBack("Ctrl+B", "뒤로 보내기 (Send to Back)");

		private String shortcut;
		private String description;

		private EHelpEntry(String shortcut, String description) {
			this.shortcut = shortcut;
			this.description = description;
		}

		public String getShortcut() {
			return shortcut;
		}

		public String getDescription() {
			return description;
		}

		public static void setValues(Node node) {
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() != Node.ELEMENT_NODE)
					continue;
				String nodeName = child.getNodeName(); // ex: eShortcut
				String shortcut = child.getAttributes().getNamedItem("shortcut").getNodeValue();
				String description = child.getAttributes().getNamedItem("description").getNodeValue();
				for (EHelpEntry item : values()) {
					if (item.name().equals(nodeName)) {
						item.shortcut = shortcut;
						item.description = description;
					}
				}
			}
		}
	}

	public enum EMenu {
		eFileMenu(""), eEditMenu(""), eGraphicsMenu("");

		private String value;

		private EMenu(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		public static void setValues(Node node) {
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					String nodeName = child.getNodeName();
					Node labelAttr = child.getAttributes().getNamedItem("label");
					String labelValue = labelAttr != null ? labelAttr.getNodeValue() : "";

					for (EMenu menu : EMenu.values()) {
						if (menu.name().equals(nodeName)) {
							menu.value = labelValue;
							System.out.println(menu.name() + " label: " + labelValue);
						}
					}
				}
			}
		}
	}

	public enum EFileMenuItem {
		eNew("", ""), eOpen("", ""), eSave("", ""), eSaveAs("", ""), ePrint("", ""), eClose("", ""), eQuit("", "");

		private String value;
		private String methodName;

		private EFileMenuItem(String value, String name) {
			this.value = value;
			this.methodName = name;
		}

		public String getName() {
			return value;
		}

		public String getMethodName() {
			return methodName;
		}

		public static void setValues(Node node) {
			Node defaultPathNode = node.getAttributes().getNamedItem("defaultPathName");
			if (defaultPathNode != null) {
				GFilePath.DEFAULT_DIR = defaultPathNode.getNodeValue();
				System.out.println("Loaded defaultPathName: " + GFilePath.DEFAULT_DIR);
			}
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() != Node.ELEMENT_NODE)
					continue;
				String nodeName = child.getNodeName();
				String labelValue = child.getAttributes().getNamedItem("label").getNodeValue();
				String methodValue = child.getAttributes().getNamedItem("methodName").getNodeValue();
				for (EFileMenuItem item : EFileMenuItem.values()) {
					if (item.name().equals(nodeName)) {
						item.value = labelValue;
						item.methodName = methodValue;
					}
				}
			}
		}

	}

	public enum EEditMenuItem {
		eReDo("", ""), eUnDo("", ""), eCut("", ""), eCopy("", ""), ePaste("", ""), eDelete("", ""), eGroup("", ""),
		eUnGroup("", "");

		private String label;
		private String toolTipText;

		private EEditMenuItem(String label, String toolTipText) {
			this.label = label;
			this.toolTipText = toolTipText;
		}

		public String getLabel() {
			return label;
		}

		public String getToolTipText() {
			return toolTipText;
		}

		public static void setValues(Node node) {
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					String nodeName = child.getNodeName();
					Node labelAttr = child.getAttributes().getNamedItem("label");
					Node tipAttr = child.getAttributes().getNamedItem("toolTipText");
					String labelValue = labelAttr != null ? labelAttr.getNodeValue() : "";
					String tipValue = tipAttr != null ? tipAttr.getNodeValue() : "";

					for (EEditMenuItem item : EEditMenuItem.values()) {
						if (item.name().equals(nodeName)) {
							item.label = labelValue;
							item.toolTipText = tipValue;
						}
					}
				}
			}
		}
	}

	public enum EToolBarGroup {
		eSelect("", ""), eDrawing("", ""), eColor("", ""), eLine("", ""), eTextArea("", ""), eFunction("", "");

		private String label;
		private String toolTipText;

		private EToolBarGroup(String label, String toolTipText) {
			this.label = label;
			this.toolTipText = toolTipText;
		}

		public String getLabel() {
			return label;
		}

		public String getToolTipText() {
			return toolTipText;
		}

		public static void setValues(Node node) {
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					String nodeName = child.getNodeName();
					Node labelAttr = child.getAttributes().getNamedItem("label");
					Node tipAttr = child.getAttributes().getNamedItem("toolTipText");

					String labelValue = labelAttr != null ? labelAttr.getNodeValue() : "";
					String tipValue = tipAttr != null ? tipAttr.getNodeValue() : "";

					for (EToolBarGroup item : EToolBarGroup.values()) {
						if (item.name().equals(nodeName)) {
							item.label = labelValue;
							item.toolTipText = tipValue;
						}
					}
				}
			}
		}
	}

	public enum EShapeTool {
		eSelect("선택", EPoints.e2P, "도형을 선택합니다.", "shapes.GRectangle", EToolBarGroup.eSelect),
		eRectnalge("네모", EPoints.e2P, "네모를 그립니다.", "shapes.GRectangle", EToolBarGroup.eDrawing),
		eEllipse("원", EPoints.e2P, "원을 그립니다.", "shapes.GEllipse", EToolBarGroup.eDrawing),
		eFreeLine("", EPoints.e2P, "자유 선을 그립니다.", "shapes.GFreeLine", EToolBarGroup.eLine),
		eLine("선", EPoints.e2P, "자유 선을 그립니다.", "shapes.GLine", EToolBarGroup.eDrawing),
		ePolygon("다각형", EPoints.eNP, "다각형을 그립니다.", "shapes.GPolygon", EToolBarGroup.eDrawing),
		eTextArea("글 상자", EPoints.e2P, "글 상자를 넣습니다.", "shapes.GTextArea", EToolBarGroup.eTextArea);

		private String label;
		private EPoints ePoints;
		private String toolTipText;
		private Class<?> classShape;
		private EToolBarGroup pertainGroup;

		private EShapeTool(String label, EPoints ePoints, String toolTipText, String className, EToolBarGroup group) {
			this.label = label;
			this.ePoints = ePoints;
			this.toolTipText = toolTipText;
			this.pertainGroup = group;
			try {
				this.classShape = Class.forName(className);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				this.classShape = null;
			}
		}

		public String getLabel() {
			return label;
		}

		public EPoints getEPoints() {
			return ePoints;
		}

		public String getToolTipText() {
			return toolTipText;
		}

		public Class<?> getClassShape() {
			return classShape;
		}

		public EToolBarGroup getPertainGroup() {
			return pertainGroup;
		}

		public GShape newShape() {
			if (classShape == null)
				return null;
			try {
				return (GShape) classShape.getConstructor().newInstance();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		public static void setValues(Node eShapeToolNode) {
			NodeList children = eShapeToolNode.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() != Node.ELEMENT_NODE)
					continue;

				String nodeName = child.getNodeName();
				String label = getAttributeValue(child, "label");
				String ePointsStr = getAttributeValue(child, "ePoints");
				String toolTip = getAttributeValue(child, "toolTipText");
				String className = getAttributeValue(child, "className");
				String groupStr = getAttributeValue(child, "pertainGroup");

				for (EShapeTool tool : EShapeTool.values()) {
					if (tool.name().equals(nodeName)) {
						tool.label = label.isEmpty() ? tool.label : label;
						if (!ePointsStr.isEmpty()) {
							try {
								tool.ePoints = EPoints.valueOf(ePointsStr);
							} catch (IllegalArgumentException ignored) {
							}
						}
						tool.toolTipText = toolTip.isEmpty() ? tool.toolTipText : toolTip;
						if (!className.isEmpty()) {
							try {
								tool.classShape = Class.forName(className);
							} catch (ClassNotFoundException e) {
								e.printStackTrace();
							}
						}
						if (!groupStr.isEmpty()) {
							try {
								tool.pertainGroup = EToolBarGroup.valueOf(groupStr);
							} catch (IllegalArgumentException ignored) {
							}
						}
					}
				}
			}
		}

		private static String getAttributeValue(Node node, String attr) {
			if (node.getAttributes() == null)
				return "";
			Node attrNode = node.getAttributes().getNamedItem(attr);
			return (attrNode != null) ? attrNode.getNodeValue() : "";
		}
	}

	public enum EElement {
		colorBtn("... 더보기", "색깔을 더 볼 수 있습니다.", EToolBarGroup.eColor),
		colorDialog("색깔 선택", "색깔을 선택할 수 있습니다.", EToolBarGroup.eColor);

		private String label;
		private String toolTipText;
		private EToolBarGroup pertainGroup;

		private EElement(String label, String toolTipText, EToolBarGroup pertainGroup) {
			this.label = label;
			this.toolTipText = toolTipText;
			this.pertainGroup = pertainGroup;
		}

		public String getLabel() {
			return label;
		}

		public String getToolTipText() {
			return toolTipText;
		}

		public EToolBarGroup getPertainGroup() {
			return pertainGroup;
		}

		public static void setValues(Node node) {
			NodeList children = node.getChildNodes();
			colorSwatches.clear(); // 팔레트 초기화
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() != Node.ELEMENT_NODE)
					continue;
				String nodeName = child.getNodeName();

				String labelValue = getAttributeValue(child, "label");
				String tipValue = getAttributeValue(child, "toolTipText");
				String groupStr = getAttributeValue(child, "pertainGroup");

				for (EElement item : EElement.values()) {
					if (item.name().equals(nodeName)) {
						if (!labelValue.isEmpty())
							item.label = labelValue;
						if (!tipValue.isEmpty())
							item.toolTipText = tipValue;
						if (!groupStr.isEmpty()) {
							try {
								item.pertainGroup = EToolBarGroup.valueOf(groupStr);
							} catch (Exception e) {
							}
						}
					}
				}
				if ("colorSwatch".equals(nodeName)) {
					colorSwatches.add(new ColorSwatch(labelValue, tipValue));
				}
			}
		}

		private static String getAttributeValue(Node node, String attr) {
			if (node == null || node.getAttributes() == null)
				return "";
			Node n = node.getAttributes().getNamedItem(attr);
			return (n != null) ? n.getNodeValue() : "";
		}

		public static class ColorSwatch {
			public final Color color;
			public final String tooltip;

			public ColorSwatch(String hex, String tooltip) {
				this.color = parseHexColor(hex);
				this.tooltip = tooltip;
			}

			private Color parseHexColor(String hex) {
				try {
					return Color.decode(hex);
				} catch (Exception e) {
					return Color.BLACK;
				}
			}
		}

		public static List<ColorSwatch> colorSwatches = new ArrayList<>();
	}

	public enum EFunctionTool {
		eGroup("", "", EToolBarGroup.eFunction), eUnGroup("", "", EToolBarGroup.eFunction),
		eCrop("", "", EToolBarGroup.eFunction), eRedo("", "", EToolBarGroup.eFunction),
		eUndo("", "", EToolBarGroup.eFunction), eDelete("", "", EToolBarGroup.eFunction),
		eCopy("", "", EToolBarGroup.eFunction), ePaste("", "", EToolBarGroup.eFunction),
		eToFront("", "", EToolBarGroup.eFunction), eToBack("", "", EToolBarGroup.eFunction);

		private String label;
		private String toolTipText;
		private EToolBarGroup pertainGroup;

		private EFunctionTool(String label, String toolTipText, EToolBarGroup pertainGroup) {
			this.label = label;
			this.toolTipText = toolTipText;
			this.pertainGroup = pertainGroup;
		}

		public String getLabel() {
			return label;
		}

		public String getToolTipText() {
			return toolTipText;
		}

		public EToolBarGroup getPertainGroup() {
			return pertainGroup;
		}

		public static void setValues(Node eFunctionToolNode) {

			NodeList children = eFunctionToolNode.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() != Node.ELEMENT_NODE)
					continue;

				String nodeName = child.getNodeName();
				String label = getAttributeValue(child, "label");
				String toolTip = getAttributeValue(child, "toolTipText");
				String groupStr = getAttributeValue(child, "pertainGroup");

				for (EFunctionTool tool : EFunctionTool.values()) {
					if (tool.name().equals(nodeName)) {
						tool.label = label.isEmpty() ? tool.label : label;
						tool.toolTipText = toolTip.isEmpty() ? tool.toolTipText : toolTip;
						if (!groupStr.isEmpty()) {
							try {
								tool.pertainGroup = EToolBarGroup.valueOf(groupStr);
							} catch (IllegalArgumentException ignored) {
							}
						}
					}
				}
			}
		}

		private static String getAttributeValue(Node node, String attr) {
			if (node.getAttributes() == null)
				return "";
			Node attrNode = node.getAttributes().getNamedItem(attr);
			return (attrNode != null) ? attrNode.getNodeValue() : "";
		}

	}


	// [2026-10-18 수정자: Jung Minkyu] FEAT-01 추천 사이드바 라벨 (XML <ERecommend> 로 다국어)
	// 연관: frames/GRecommendPanel.java, recommend/ESuggestShape.java, rsc/*Config.xml
	public enum ERecommend {
		eTitle("연관 도형 추천"), eUseLLM("로컬 LLM(Ollama) 사용"), eReanalyze("다시 분석"), eKeep("자유선 유지"),
		eStatusIdle("자유선을 그리면 비슷한 도형을 추천합니다."), eStatusGeometric("기하 분석 결과입니다. 항목을 누르면 도형으로 바꿉니다."),
		eStatusAnalyzing("기하 분석 결과입니다. 로컬 LLM 분석 중..."), eStatusLLM("로컬 LLM 결과를 반영했습니다. 항목을 누르면 도형으로 바꿉니다."),
		eStatusLLMFail("로컬 LLM(Ollama) 에 연결할 수 없어 기하 분석 결과만 표시합니다."),
		eStatusApplied("도형을 적용했습니다. (Ctrl+Z 로 되돌리기)"), eStatusNoStroke("선택된 자유선이 없습니다."),
		eLine("직선"), eRectangle("사각형"), eEllipse("타원"), eTriangle("삼각형"), ePolygon("다각형");

		private String label;

		private ERecommend(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}

		public static void setValues(Node node) {
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() != Node.ELEMENT_NODE)
					continue;
				Node labelAttr = child.getAttributes().getNamedItem("label");
				if (labelAttr == null)
					continue;
				for (ERecommend item : values()) {
					if (item.name().equals(child.getNodeName())) {
						item.label = labelAttr.getNodeValue();
					}
				}
			}
		}
	}

	// [2026-10-18 수정자: Jung Minkyu] FEAT-01 로컬 LLM(Ollama) 설정
	// 우선순위: 시스템 속성(-Dpaint.ollama.url / model / timeoutMs / enabled) > XML <GRecommender ...> > 기본값
	// 연관: recommend/GOllamaRecognizer.java, frames/GRecommendPanel.java, rsc/*Config.xml
	public static final class GRecommender {
		private static String url = "http://localhost:11434";
		private static String model = "llama3.2";
		private static int timeoutMs = 15000;
		private static boolean enabled = true;

		public static String getUrl() {
			return System.getProperty("paint.ollama.url", url);
		}

		public static String getModel() {
			return System.getProperty("paint.ollama.model", model);
		}

		public static int getTimeoutMs() {
			try {
				return Integer.parseInt(System.getProperty("paint.ollama.timeoutMs", String.valueOf(timeoutMs)));
			} catch (NumberFormatException e) {
				return timeoutMs;
			}
		}

		public static boolean isEnabled() {
			return Boolean.parseBoolean(System.getProperty("paint.ollama.enabled", String.valueOf(enabled)));
		}

		public static void setValues(Node node) {
			if (node.getAttributes() == null)
				return;
			Node a = node.getAttributes().getNamedItem("url");
			if (a != null && !a.getNodeValue().isEmpty())
				url = a.getNodeValue();
			a = node.getAttributes().getNamedItem("model");
			if (a != null && !a.getNodeValue().isEmpty())
				model = a.getNodeValue();
			a = node.getAttributes().getNamedItem("timeoutMs");
			if (a != null) {
				try {
					timeoutMs = Integer.parseInt(a.getNodeValue());
				} catch (NumberFormatException ignored) {
				}
			}
			a = node.getAttributes().getNamedItem("enabled");
			if (a != null)
				enabled = Boolean.parseBoolean(a.getNodeValue());
		}
	}

}

package manager;

import java.util.ArrayList;
import java.util.List;

import frames.GBelowPanel;
import frames.GColorToolBar;
import frames.GDrawingPanel;
import frames.GFunctionToolBar;
import frames.GMainFrame;
import frames.GMenuBar;
import frames.GRecommendPanel;
import frames.GShapeToolBar;
import global.GConstants.EShapeTool;
import menus.GFileMenu;

public class GPanelManager {
	// components
    private List<GDrawingPanel> panels = new ArrayList<>();
    private GDrawingPanel currentPanel;
    private GMainFrame mainFrame;

    // components to be Dependency Injected
    private GFileMenu fileMenu;
    private GMenuBar menuBar;
    private GShapeToolBar toolBar;
    private GFunctionToolBar functionTool;
    private GColorToolBar colorTool;
    @SuppressWarnings("unused")
	private GBelowPanel belowPanel;
    private GContainer container;
    // [2026-10-18 수정자: Jung Minkyu] FEAT-01 새 탭의 GDrawingPanel 도 사이드바와 연결되도록 DI
    private GRecommendPanel recommendPanel;

    // constructor
    public GPanelManager(GMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.container = new GContainer();
    }

    public void addPanel(GDrawingPanel panel) {
        panels.add(panel);

        if (toolBar != null && menuBar != null && functionTool != null) {
            associate(panel);
        }

        panel.setEShapeTool(EShapeTool.eSelect);
        panel.initialize();

        selectPanel(panels.size() - 1);
    }

    public void selectPanel(int idx) {
        if (idx < 0 || idx >= panels.size()) return;
        currentPanel = panels.get(idx);

        if (toolBar != null && menuBar != null) {
            associate(currentPanel);
        }

        mainFrame.setCenterPanel(currentPanel);
    }

    // Getter
    public GDrawingPanel getCurrentPanel() {
        return currentPanel;
    }

    public int getPanelCount() {
        return panels.size();
    }
    
    public GContainer getToolContainer() {
        return container;
    }

    // associate
    public void associate(GDrawingPanel panel) {
        toolBar.associate(panel);
        menuBar.associate(panel);
        fileMenu.associate(panel);
        functionTool.associate(panel);
        colorTool.associate(panel);
        if (recommendPanel != null) {
            recommendPanel.associate(panel); // FEAT-01
        }
    }

    // DI 세터들
    // setter
    public void setToolBar(GShapeToolBar toolBar) {
        this.toolBar = toolBar;
    }

    public void setMenuBar(GMenuBar menuBar) {
        this.menuBar = menuBar;
        setFileMenu(this.menuBar);
    }

    public void setBelowPanel(GBelowPanel belowPanel) {
        this.belowPanel = belowPanel;
    }

	public void setFunctionBar(GFunctionToolBar functionTool) {
		// TODO Auto-generated method stub
		this.functionTool = functionTool;
		
	}

	public void setColorBar(GColorToolBar colorTool) {
		// TODO Auto-generated method stub
		this.colorTool = colorTool;
	}
	
	public void setRecommendPanel(GRecommendPanel recommendPanel) {
		this.recommendPanel = recommendPanel;
	}

	public void setFileMenu(GMenuBar Parent) {
		this.fileMenu = Parent.getFileMenu();
	}
}

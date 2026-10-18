package menus;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import frames.GDrawingPanel;
import global.GConstants.EFileMenuItem;
import global.GConstants.EMenu;
import global.GConstants.GFilePath;
import manager.GProcessManager;

public class GFileMenu extends JMenu {
	private static final long serialVersionUID = 1L;

	// components
	private File dir;
	private File file;

	// association
	private GDrawingPanel drawingpanel;
	private GProcessManager processManager;

	// [2026-10-18 수정자: Jung Minkyu] FIX-11
	// As-Is: XML 의 <EMenu><eFileMenu label="파일"/> 을 읽어 두고도 "File" 을 하드코딩해 메뉴 이름만 다국어가 안 됐다.
	// To-Be: 로드된 라벨이 있으면 사용. 연관: global/GConstants.EMenu, rsc/*Config.xml
	public GFileMenu() {
		super(EMenu.eFileMenu.getValue().isEmpty() ? "File" : EMenu.eFileMenu.getValue());
		ActionHandler actionHandler = new ActionHandler();
		for (EFileMenuItem emenuItem : EFileMenuItem.values()) {
			JMenuItem menuItem = new JMenuItem(emenuItem.getName());
			menuItem.setActionCommand(emenuItem.name());
			menuItem.addActionListener(actionHandler);
			this.add(menuItem);
		}
	}

	public void initialize() {
		this.dir = new File(GFilePath.DEFAULT_DIR);
		this.file = null;
		this.processManager = new GProcessManager();
	}

	// associate
	public void associate(GDrawingPanel drawingPanel) {
		this.drawingpanel = drawingPanel;
	}

	public void newPanel() {
		if (!this.close()) {
			try {
				processManager.launchNewJavaProcess("global.GMain");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void open() {
		JFileChooser chooser = new JFileChooser(this.dir);
		FileNameExtensionFilter filter = new FileNameExtensionFilter("Graphics Data (*.gvs)", "gvs");
		chooser.setFileFilter(filter);
		chooser.setAcceptAllFileFilterUsed(false);

		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File selected = chooser.getSelectedFile();
			try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(selected)))) {
				Object shapes = in.readObject();
				this.drawingpanel.setShapes(shapes);
				this.file = selected;
				this.drawingpanel.setBUpdated(false);
			} catch (IOException | ClassNotFoundException ex) {
				ex.printStackTrace();
				JOptionPane.showMessageDialog(this, "파일 열기에 실패했습니다.", "Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public void save() {
		if (this.file == null) {
			if (this.saveAs()) {
				return;
			}
		}
		try (ObjectOutputStream out = new ObjectOutputStream(
				new BufferedOutputStream(new FileOutputStream(this.file)))) {
			out.writeObject(this.drawingpanel.getShapes());
			this.drawingpanel.setBUpdated(false);
		} catch (IOException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "저장에 실패했습니다.", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	public boolean saveAs() {
		JFileChooser chooser = new JFileChooser(this.dir);
		FileNameExtensionFilter filter = new FileNameExtensionFilter("Graphics Data (*.gvs)", "gvs");
		chooser.setFileFilter(filter);
		chooser.setAcceptAllFileFilterUsed(false);

		int result = chooser.showSaveDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			File sel = chooser.getSelectedFile();
			String path = sel.getAbsolutePath();
			if (!path.toLowerCase().endsWith(".gvs")) {
				sel = new File(path + ".gvs");
			}
			this.file = sel;
			this.dir = chooser.getCurrentDirectory();
			return false;
		} else {
			return true;
		}
	}

	public void print() {
		try {
			PrinterJob job = PrinterJob.getPrinterJob();
			job.setJobName("정민규의 프린터");

			job.setPrintable(new Printable() {
				@Override
				public int print(Graphics graphics, PageFormat format, int pageIndex) {
					if (pageIndex > 0) {
						return NO_SUCH_PAGE;
					}
					Graphics2D graphic = (Graphics2D) graphics;
					graphic.translate(format.getImageableX(), format.getImageableY());
					drawingpanel.paint(graphic);
					return PAGE_EXISTS;
				}
			});

			if (job.printDialog()) {
				job.print();
			}
		} catch (PrinterException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "Print Error" + ex.getMessage() + JOptionPane.ERROR_MESSAGE);
		}
	}

	public boolean close() {
		if (this.drawingpanel.isUpdated()) {
			int choice = JOptionPane.showConfirmDialog(this.drawingpanel, "변경 내용이 있습니다. 저장하시겠습니까?", "Confirm",
					JOptionPane.YES_NO_CANCEL_OPTION);
			if (choice == JOptionPane.CANCEL_OPTION) {
				return true;
			} else if (choice == JOptionPane.YES_OPTION) {
				this.save();
			}
		}
		return false;
	}

	public void quit() {
		if (!this.close()) {
			System.exit(0);
		}
	}

	private void invokeMethod(String methodName) {
		try {
			this.getClass().getMethod(methodName).invoke(this);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
			ex.printStackTrace();
		}
	}

	private class ActionHandler implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent event) {
			EFileMenuItem item = EFileMenuItem.valueOf(event.getActionCommand());
			invokeMethod(item.getMethodName());
		}
	}
}

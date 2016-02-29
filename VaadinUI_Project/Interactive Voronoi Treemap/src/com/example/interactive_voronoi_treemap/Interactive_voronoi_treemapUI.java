package com.example.interactive_voronoi_treemap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import javax.servlet.annotation.WebServlet;

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.FileResource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.BrowserFrame;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.TwinColSelect;
import com.vaadin.ui.UI;
import com.vaadin.ui.Upload;
import com.vaadin.ui.Upload.Receiver;
import com.vaadin.ui.Upload.SucceededEvent;
import com.vaadin.ui.Upload.SucceededListener;
import com.vaadin.ui.VerticalLayout;

@SuppressWarnings("serial")
@Theme("interactive_voronoi_treemap")
public class Interactive_voronoi_treemapUI extends UI {
	
	final String resourcePath = "C://Users/Matze/Dropbox/Studium/Bsc-Arbeit/Final/";
	
	TwinColSelect select = new TwinColSelect("Select column names in hierarchical order");
	Button button = new Button("Create Treemap");

	ProgressBar load = new ProgressBar();
	
	Label label_selection = new Label();
	Label label_in = new Label();
	Label label_err = new Label();
	
	File tempFile;
	FileReceiver receiver = new FileReceiver();
	Upload uploadFile = new Upload("Upload file to be mapped", receiver);
	
	@WebServlet(value = "/*", asyncSupported = true)
	@VaadinServletConfiguration(productionMode = false, ui = Interactive_voronoi_treemapUI.class)
	public static class Servlet extends VaadinServlet {
	}

		@Override
	protected void init(VaadinRequest request) {
		HorizontalLayout main = new HorizontalLayout();
		VerticalLayout left = new VerticalLayout();
		configureComponents(left, main);
		
		left.addComponent(uploadFile);
		left.addComponent(select);
		left.addComponent(new HorizontalLayout(button, load));
		left.addComponent(label_selection);
		
		main.addComponent(left);
		setContent(main);
	}

	private void configureComponents(VerticalLayout l, HorizontalLayout m) {
		uploadFile.addSucceededListener(receiver);
		
		select.addValueChangeListener(event -> { // Java 8
			label_selection.setCaption("Selected: " + event.getProperty().getValue());
		});
		select.setRows(10);
		
		load.setVisible(false);
		load.setIndeterminate(true);
		
		button.addClickListener(new Button.ClickListener() {
			public void buttonClick(ClickEvent event) {
				load.setVisible(true);
				
				createTreemap(new Runnable() {

					@Override
					public void run() {
						m.addComponent(createTreemapFrame());
						load.setVisible(false);
						tempFile.delete();
					}
				});

			}
		});
	}
	
	class FileReceiver implements Receiver, SucceededListener {
		
		@Override
		public OutputStream receiveUpload(String filename, String mimeType) {
			try {
				tempFile = File.createTempFile("temp", ".csv");
				return new FileOutputStream(tempFile);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
		
		public void uploadSucceeded(SucceededEvent event) {
			addColSelectItems(tempFile);
		}
	};
	
	private void addColSelectItems(File file) {
		select.removeAllItems();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			String[] colNames = null;
			String[] nextLine;						
			if ((nextLine = reader.readLine().split("\t")) != null)
				colNames = nextLine;
			reader.close();
			
			for (String c : colNames) {
				select.addItem(c);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	private VerticalLayout createTreemapFrame() {
		BrowserFrame browser = new BrowserFrame("Voronoi Treemap", new FileResource(new File(resourcePath + "VoroTreemap.html")));
		
		browser.setWidth("1500px");
		browser.setHeight("1000px");
		
		return new VerticalLayout(browser);
	}

	public void createTreemap(final Runnable ready) {		
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				String cmd = "java -jar " + resourcePath + "VoronoiTreemapFromTable.jar "
						+ tempFile.getAbsolutePath() + " ";
				String selected = "null";
				if (!select.isEmpty())
					selected = select.getValue().toString();
				if (selected != null) {
					String[] colNames = selected.substring(1, selected.length() - 1).split(",");
					for (String c : colNames) {
						cmd += c;
					}
				}
				try {
					Process p = Runtime.getRuntime().exec(cmd);
					InputStream in = p.getInputStream();
					BufferedReader br = new BufferedReader(new InputStreamReader(in));
					for (String line = br.readLine(); line != null; line = br.readLine()) {
						System.out.println(line);
					}
					br.close();
					p.waitFor();
				} catch (Exception e) {
					e.printStackTrace();
				}
				UI.getCurrent().access(ready); // ready = klasse mit run, die ausgeführt wird, wenn process fertig ist (e.g. vaadin view stuff)
				UI.getCurrent().setPollInterval(-1);
			}
		});
		t.start();
		UI.getCurrent().setPollInterval(200);// wie oft die UI geupdated wird, wenn überhaupt nötig
	}
	
}
package org.example;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GraphViewerApp extends Application {

    // Datastrukturer
    private XYSeriesCollection dataset;
    private XYPlot plot;
    private File selectedFile;
    private ScheduledExecutorService scheduler;
    private List<DataRow> currentData = new ArrayList<>();

    // UI Komponenter
    private CheckBox[] channelCheckBoxes = new CheckBox[8];
    private RadioButton sampleRadio;
    private RadioButton timeRadio;
    private CheckBox autoScaleXCheck;
    private CheckBox autoScaleYCheck;
    private TextField minXInput, maxXInput, minYInput, maxYInput;
    private TextField intervalInput;
    private ToggleButton repeatToggle;
    private Label statusLabel;

    // KRAV: MS Excel Standard 8 färger
    private final Color[] excelColors = {
            Color.decode("#4472C4"), Color.decode("#ED7D31"), Color.decode("#A5A5A5"), Color.decode("#FFC000"),
            Color.decode("#5B9BD5"), Color.decode("#70AD47"), Color.decode("#264478"), Color.decode("#9E480E")
    };

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("GraphViewer - PC Version");

        // 1. Initiera Grafen (JFreeChart)
        dataset = new XYSeriesCollection();
        for (int i = 1; i <= 8; i++) {
            dataset.addSeries(new XYSeries("Kanal " + i)); // KRAV: Namn i färgförklaring
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Datalogger Grafer", "Horisontell Axel", "Vertikal Axel",
                dataset, PlotOrientation.VERTICAL, true, true, false
        );

        plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE); // KRAV: Vit grafyta
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        for (int i = 0; i < 8; i++) {
            renderer.setSeriesPaint(i, excelColors[i]); // KRAV: Excel-färger
            renderer.setSeriesShapesVisible(i, false);
        }
        plot.setRenderer(renderer);

        ChartViewer chartViewer = new ChartViewer(chart); // KRAV: Inbyggd zoom (ruta/punkt/återställ)

        // 2. Bygg Kontrollpanelen (Höger sida)
        VBox controlPanel = new VBox(10);
        controlPanel.setPadding(new Insets(10));
        controlPanel.setPrefWidth(250);

        // Filval & Läsning
        Button openFileBtn = new Button("Välj textfil..."); // KRAV: Filväljar-browser
        openFileBtn.setOnAction(e -> handleFileSelect(primaryStage));

        intervalInput = new TextField("1000");
        intervalInput.setPromptText("ms");
        repeatToggle = new ToggleButton("Repeterande läsning"); // KRAV: Enkel/Repeterande
        repeatToggle.setOnAction(e -> toggleRepeatingLoad());

        // Axelläge (Sampel eller Tid)
        Label axisLabel = new Label("Horisontell axel:");
        ToggleGroup axisGroup = new ToggleGroup();
        sampleRadio = new RadioButton("Antal sampel");
        sampleRadio.setToggleGroup(axisGroup);
        sampleRadio.setSelected(true);
        timeRadio = new RadioButton("Tid");
        timeRadio.setToggleGroup(axisGroup);
        axisGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> updateGraphView());

        // KRAV: Tända/Släcka grafer
        Label channelsLabel = new Label("Visa kanaler:");
        VBox channelsBox = new VBox(5);
        for (int i = 0; i < 8; i++) {
            channelCheckBoxes[i] = new CheckBox("Kanal " + (i + 1));
            channelCheckBoxes[i].setSelected(true);
            final int index = i;
            int finalI = i;
            channelCheckBoxes[i].setOnAction(e -> {
                renderer.setSeriesVisible(index, channelCheckBoxes[finalI].isSelected());
            });
            channelsBox.getChildren().add(channelCheckBoxes[i]);
        }

        // KRAV: Skalning (Autoscale vs Fast)
        Label scalingLabel = new Label("Skalningsinställningar:");
        autoScaleXCheck = new CheckBox("Autoscale X");
        autoScaleXCheck.setSelected(true);
        minXInput = new TextField("0"); maxXInput = new TextField("100");
        minXInput.setPrefWidth(50); maxXInput.setPrefWidth(50);
        HBox xLimits = new HBox(5, new Label("Min:"), minXInput, new Label("Max:"), maxXInput);

        autoScaleYCheck = new CheckBox("Autoscale Y");
        autoScaleYCheck.setSelected(true);
        minYInput = new TextField("0"); maxYInput = new TextField("1");
        minYInput.setPrefWidth(50); maxYInput.setPrefWidth(50);
        HBox yLimits = new HBox(5, new Label("Min:"), minYInput, new Label("Max:"), maxYInput);

        autoScaleXCheck.setOnAction(e -> applyScaling());
        autoScaleYCheck.setOnAction(e -> applyScaling());

        statusLabel = new Label("Ingen fil laddad.");
        statusLabel.setWrapText(true);

        controlPanel.getChildren().addAll(
                openFileBtn, new HBox(5, repeatToggle, intervalInput), new Separator(),
                axisLabel, sampleRadio, timeRadio, new Separator(),
                scalingLabel, autoScaleXCheck, xLimits, autoScaleYCheck, yLimits, new Separator(),
                channelsLabel, channelsBox, new Separator(), statusLabel
        );

        // Layoutinställningar
        BorderPane root = new BorderPane();
        root.setCenter(chartViewer);
        root.setRight(controlPanel);

        Scene scene = new Scene(root, 1024, 768);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // KRAV: Välj fil via Browser
    private void handleFileSelect(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Textfiler (*.txt)", "*.txt"));
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            selectedFile = file;
            statusLabel.setText("Vald fil: " + file.getName());
            loadDataOnce();
        }
    }

    private void loadDataOnce() {
        if (selectedFile == null) return;
        try {
            currentData = readFileWithoutLock(selectedFile);
            updateGraphView();
        } catch (IOException e) {
            statusLabel.setText("Fel vid läsning: " + e.getMessage());
        }
    }

    // KRAV: Repeterande läsning med valbart intervall
    private void toggleRepeatingLoad() {
        if (repeatToggle.isSelected()) {
            if (selectedFile == null) {
                statusLabel.setText("Välj en fil först!");
                repeatToggle.setSelected(false);
                return;
            }
            int interval = Integer.parseInt(intervalInput.getText());
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    currentData = readFileWithoutLock(selectedFile);
                    Platform.runLater(this::updateGraphView);
                } catch (Exception e) {
                    Platform.runLater(() -> statusLabel.setText("Fel vid periodisk läsning."));
                }
            }, 0, interval, TimeUnit.MILLISECONDS);
            statusLabel.setText("Repeterande läsning aktiv...");
        } else {
            if (scheduler != null) scheduler.shutdown();
            statusLabel.setText("Repeterande läsning stoppad.");
        }
    }

    // KRAV: Får inte låsa textfilen (Try-with-resources stänger strömmen direkt)
    private List<DataRow> readFileWithoutLock(File file) throws IOException {
        List<DataRow> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("Dato") || line.startsWith("-")) continue;

                String[] tokens = line.split("\\|");
                if (tokens.length >= 9) {
                    String time = tokens[0].trim();
                    double[] vals = new double[8];
                    for (int i = 0; i < 8; i++) {
                        vals[i] = Double.parseDouble(tokens[i + 1].trim());
                    }
                    rows.add(new DataRow(time, vals));
                }
            }
        }
        return rows;
    }

    // Uppdaterar innehållet i grafen utifrån vald axeltyp
    private void updateGraphView() {
        for (int i = 0; i < 8; i++) dataset.getSeries(i).clear();

        boolean useTime = timeRadio.isSelected();
        for (int i = 0; i < currentData.size(); i++) {
            DataRow row = currentData.get(i);
            double x = useTime ? parseTimeToSeconds(row.timestamp) : i; // KRAV: Antal sampel eller tid
            for (int ch = 0; ch < 8; ch++) {
                dataset.getSeries(ch).add(x, row.channels[ch]);
            }
        }
        applyScaling();
    }

    // KRAV: Fast eller Autoscale på varje axel
    private void applyScaling() {
        if (autoScaleXCheck.isSelected()) {
            plot.getDomainAxis().setAutoRange(true);
        } else {
            plot.getDomainAxis().setAutoRange(false);
            double min = Double.parseDouble(minXInput.getText());
            double max = Double.parseDouble(maxXInput.getText());
            plot.getDomainAxis().setRange(min, max);
        }

        if (autoScaleYCheck.isSelected()) {
            plot.getRangeAxis().setAutoRange(true);
        } else {
            plot.getRangeAxis().setAutoRange(false);
            double min = Double.parseDouble(minYInput.getText());
            double max = Double.parseDouble(maxYInput.getText());
            plot.getRangeAxis().setRange(min, max);
        }
    }

    private double parseTimeToSeconds(String timestamp) {
        try {
            LocalDateTime ldt = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return ldt.toEpochSecond(java.time.ZoneOffset.UTC);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdown();
    }

    // Databärare för filinläsningen
    static class DataRow {
        String timestamp;
        double[] channels;
        DataRow(String t, double[] c) { this.timestamp = t; this.channels = c; }
    }
}

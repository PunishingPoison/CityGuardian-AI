package com.cityguardian.controller;

import com.cityguardian.model.City;
import com.cityguardian.model.Tile;
import com.cityguardian.model.TileType;
import com.cityguardian.engine.SimulationEngine;
import com.cityguardian.model.Citizen;
import com.cityguardian.model.disaster.Disaster.Severity;
import com.cityguardian.model.disaster.EarthquakeDisaster;
import com.cityguardian.model.disaster.FireDisaster;
import com.cityguardian.model.disaster.FloodDisaster;
import com.cityguardian.model.resource.EmergencyResource;
import com.cityguardian.model.resource.FireTruck;
import com.cityguardian.model.resource.Helicopter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.chart.PieChart;
import javafx.scene.paint.Color;

public class DashboardController {

    @FXML private Canvas mapCanvas;
    @FXML private ComboBox<String> speedCombo;
    @FXML private ListView<String> insightsList;
    @FXML private Label totalCitizensLabel;
    @FXML private Label savedLabel;
    @FXML private Label casualtiesLabel;
    @FXML private PieChart statusChart;
    @FXML private TextField truckInput;
    @FXML private TextField heliInput;
    @FXML private TextField ambulanceInput;
    @FXML private TextField apiKeyInput;
    @FXML private CheckBox fineGrainedTestCheck;
    private ObservableList<PieChart.Data> chartData;

    private City city;
    private SimulationEngine engine;
    
    private final int TILE_SIZE = 10;
    
    @FXML
    public void initialize() {
        speedCombo.getItems().addAll("1x", "2x", "5x", "10x", "15x", "20x");
        speedCombo.setValue("1x");
        speedCombo.setOnAction(e -> {
            String val = speedCombo.getValue();
            double s = Double.parseDouble(val.replace("x", ""));
            if (engine != null) engine.setSpeed(s);
        });
        
        city = new City(70, 60);
        engine = new SimulationEngine(city, this::onTick);
        
        chartData = FXCollections.observableArrayList(
            new PieChart.Data("Safe", 0),
            new PieChart.Data("Injured", 0),
            new PieChart.Data("Casualties", 0),
            new PieChart.Data("Evacuated", 0)
        );
        statusChart.setData(chartData);
        
        drawMap();
        
        insightsList.setCellFactory(param -> new javafx.scene.control.ListCell<String>() {
            private javafx.scene.text.Text text = new javafx.scene.text.Text();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    text.setText(item);
                    text.wrappingWidthProperty().bind(getListView().widthProperty().subtract(20));
                    text.setFill(javafx.scene.paint.Color.WHITE);
                    setGraphic(text);
                }
            }
        });
        
        insightsList.getItems().add("System Initialized. Awaiting simulation start.");
    }
    
    private void onTick() {
        Platform.runLater(() -> {
            drawMap();
            updateStats();
            
            // Sync vehicle limits from UI
            try {
                if (engine != null) {
                    engine.setMaxFiretrucks(Integer.parseInt(truckInput.getText()));
                    engine.setMaxHelicopters(Integer.parseInt(heliInput.getText()));
                    if (ambulanceInput != null && ambulanceInput.getText() != null) {
                        engine.setMaxAmbulances(Integer.parseInt(ambulanceInput.getText()));
                    }
                }
            } catch(NumberFormatException ex) {}
        });
    }

    private void updateStats() {
        int safe = 0, injured = 0, casualties = 0, evacuated = 0;
        for (Citizen c : city.getCitizens()) {
            if (c.isDead()) casualties++;
            else if (c.isEvacuated()) evacuated++;
            else if (c.isInjured()) injured++;
            else safe++;
        }
        
        totalCitizensLabel.setText("Total Citizens: " + city.getCitizens().size());
        savedLabel.setText("Saved: " + evacuated);
        casualtiesLabel.setText("Casualties: " + casualties);
        
        chartData.get(0).setPieValue(safe);
        chartData.get(1).setPieValue(injured);
        chartData.get(2).setPieValue(casualties);
        chartData.get(3).setPieValue(evacuated);
    }

    @FXML
    public void generateProceduralCity() {
        clearCity();
        this.city = com.cityguardian.engine.CityGenerator.generateProceduralCity(city.getWidth(), city.getHeight(), System.currentTimeMillis());
        // We must replace the engine's city reference
        engine = new SimulationEngine(this.city, this::onTick);
        drawMap();
        updateStats();
        insightsList.getItems().add("City procedurally generated.");
    }
    
    @FXML
    public void clearCity() {
        for (int x = 0; x < city.getWidth(); x++) {
            for (int y = 0; y < city.getHeight(); y++) {
                city.getTile(x, y).setType(TileType.EMPTY);
                city.getTile(x, y).setHasDisaster(false);
            }
        }
        city.getResources().clear();
        city.getDisasters().clear();
        drawMap();
        insightsList.getItems().add("City cleared.");
    }

    @FXML
    public void startSimulation() {
        engine.start();
        insightsList.getItems().add("Simulation started.");
    }

    @FXML
    public void pauseSimulation() {
        engine.pause();
        insightsList.getItems().add("Simulation paused.");
    }

    @FXML
    public void resetSimulation() {
        engine.pause();
        clearCity();
        insightsList.getItems().add("Simulation reset.");
    }
    
    @FXML
    public void triggerEarthquake() {
        insightsList.getItems().add("WARNING: Earthquake triggered!");
        city.addDisaster(new EarthquakeDisaster(35, 30, Severity.CRITICAL));
        if (!city.getDisasters().isEmpty()) engine.start();
    }
    
    @FXML
    public void triggerFire() {
        insightsList.getItems().add("WARNING: Major multi-block fire outbreak detected!");
        int startX = 35;
        int startY = 30;
        int initialRadius = 6; // Massive 13x13 initial fire covering ~4 city blocks
        
        for (int x = startX - initialRadius; x <= startX + initialRadius; x++) {
            for (int y = startY - initialRadius; y <= startY + initialRadius; y++) {
                Tile t = city.getTile(x, y);
                // Prevent roads from burning initially so firetrucks can navigate
                if (t != null && t.getType() != com.cityguardian.model.TileType.WATER && t.getType() != com.cityguardian.model.TileType.ROAD) {
                    t.setHasDisaster(true);
                    t.setRiskLevel(1.0);
                }
            }
        }
        
        city.addDisaster(new FireDisaster(startX, startY, Severity.HIGH));
        if (!city.getDisasters().isEmpty()) engine.start();
        drawMap();
    }
    
    @FXML
    public void triggerFlood() {
        insightsList.getItems().add("WARNING: Flood warnings issued. Water level rising rapidly.");
        if (city.getTile(10, 10) != null) {
            city.getTile(10, 10).setType(TileType.WATER);
            city.getTile(10, 10).setHasDisaster(true);
            city.addDisaster(new FloodDisaster(10, 10, Severity.HIGH));
            if (!city.getDisasters().isEmpty()) engine.start();
        }
        drawMap();
    }

    private void drawMap() {
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, mapCanvas.getWidth(), mapCanvas.getHeight());
        
        for (int x = 0; x < city.getWidth(); x++) {
            for (int y = 0; y < city.getHeight(); y++) {
                Tile tile = city.getTile(x, y);
                
                if (tile.hasDisaster()) {
                    gc.setFill(Color.RED);
                } else {
                    switch (tile.getType()) {
                        case RESIDENTIAL: gc.setFill(Color.LIGHTBLUE); break;
                        case COMMERCIAL: gc.setFill(Color.ORANGE); break;
                        case ROAD: gc.setFill(Color.DARKGRAY); break;
                        case HOSPITAL: gc.setFill(Color.WHITE); break;
                        case SHELTER: gc.setFill(Color.LIGHTGREEN); break;
                        case WATER: gc.setFill(Color.DARKBLUE); break;
                        case BURNT: gc.setFill(Color.rgb(50, 50, 50)); break;
                        case OBSTACLE: gc.setFill(Color.rgb(139, 69, 19)); break; // SaddleBrown
                        default: 
                            double elev = Math.max(0, Math.min(1.0, tile.getElevation()));
                            int cVal = (int) (elev * 50) + 20;
                            gc.setFill(Color.rgb(cVal, cVal, cVal));
                            break;
                    }
                }
                
                gc.fillRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE - 1, TILE_SIZE - 1);
            }
        }
        
        // Draw citizens
        for (Citizen c : city.getCitizens()) {
            if (c.isDead() || c.isEvacuated()) continue;
            
            if (c.isInjured()) {
                gc.setFill(Color.YELLOW);
            } else {
                gc.setFill(Color.rgb(100, 255, 100)); // Greenish
            }
            double px = c.getX() * TILE_SIZE + TILE_SIZE / 2.0;
            double py = c.getY() * TILE_SIZE + TILE_SIZE / 2.0;
            gc.fillOval(px - 2, py - 2, 4, 4);
        }
        
        // Draw resources
        for (EmergencyResource r : city.getResources()) {
            double px = r.getX() * TILE_SIZE;
            double py = r.getY() * TILE_SIZE;
            
            if (r instanceof Helicopter) {
                Helicopter h = (Helicopter)r;
                px = h.getCurrentX() * TILE_SIZE;
                py = h.getCurrentY() * TILE_SIZE;
                gc.setFill(Color.CYAN);
                gc.fillRect(px, py, TILE_SIZE, TILE_SIZE);
            } else if (r instanceof FireTruck) {
                FireTruck ft = (FireTruck)r;
                px = ft.getCurrentX() * TILE_SIZE;
                py = ft.getCurrentY() * TILE_SIZE;
                gc.setFill(Color.MAGENTA);
                gc.fillRect(px, py, TILE_SIZE, TILE_SIZE);
            } else if (r instanceof com.cityguardian.model.resource.Ambulance) {
                com.cityguardian.model.resource.Ambulance a = (com.cityguardian.model.resource.Ambulance)r;
                px = a.getCurrentX() * TILE_SIZE;
                py = a.getCurrentY() * TILE_SIZE;
                gc.setFill(Color.WHITE);
                gc.fillRect(px, py, TILE_SIZE, TILE_SIZE);
            }
        }
    }

    @FXML
    public void runAIOptimizer() {
        insightsList.getItems().add("AI Disaster-Specific Optimization started.");
        
        int totalTiles = city.getWidth() * city.getHeight();
        double budget = totalTiles * 550.0;
        insightsList.getItems().add("City Budget calculated: $" + String.format("%.0f", budget));

        boolean isFineGrained = fineGrainedTestCheck != null && fineGrainedTestCheck.isSelected();
        int[] testAmounts = isFineGrained ? 
            new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15} : 
            new int[]{5, 10, 15};
        String simSpeedStr = isFineGrained ? "20x" : "10x";
        double simSpeedVal = isFineGrained ? 20.0 : 10.0;

        new Thread(() -> {
            try {
                long seed = System.currentTimeMillis();
                
                int[] fireResults = new int[testAmounts.length];
                int[] quakeResults = new int[testAmounts.length];
                int[] floodResults = new int[testAmounts.length];

                // Phase 1: Fire Tests (Firetrucks only)
                for (int i = 0; i < testAmounts.length; i++) {
                    final int amount = testAmounts[i];
                    final int idx = i;
                    Platform.runLater(() -> {
                        insightsList.getItems().add("Fire Test: " + amount + " Firetrucks...");
                        speedCombo.setValue(simSpeedStr);
                        clearCity();
                        DashboardController.this.city = com.cityguardian.engine.CityGenerator.generateProceduralCity(city.getWidth(), city.getHeight(), seed);
                        engine = new SimulationEngine(DashboardController.this.city, DashboardController.this::onTick);
                        engine.setSpeed(simSpeedVal);
                        
                        truckInput.setText(String.valueOf(amount));
                        ambulanceInput.setText("0");
                        heliInput.setText("0");
                        engine.setMaxFiretrucks(amount);
                        engine.setMaxAmbulances(0);
                        engine.setMaxHelicopters(0);
                        
                        triggerFire();
                        engine.start();
                    });
                    Thread.sleep(5000);
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    Platform.runLater(() -> {
                        engine.pause();
                        int casualties = 0;
                        for (Citizen c : city.getCitizens()) if (c.isDead()) casualties++;
                        fireResults[idx] = casualties;
                        insightsList.getItems().add("Result: " + casualties + " casualties.");
                        latch.countDown();
                    });
                    latch.await();
                }

                // Phase 2: Earthquake Tests (Ambulances only)
                for (int i = 0; i < testAmounts.length; i++) {
                    final int amount = testAmounts[i];
                    final int idx = i;
                    Platform.runLater(() -> {
                        insightsList.getItems().add("Earthquake Test: " + amount + " Ambulances...");
                        speedCombo.setValue(simSpeedStr);
                        clearCity();
                        DashboardController.this.city = com.cityguardian.engine.CityGenerator.generateProceduralCity(city.getWidth(), city.getHeight(), seed);
                        engine = new SimulationEngine(DashboardController.this.city, DashboardController.this::onTick);
                        engine.setSpeed(simSpeedVal);
                        
                        truckInput.setText("0");
                        ambulanceInput.setText(String.valueOf(amount));
                        heliInput.setText("0");
                        engine.setMaxFiretrucks(0);
                        engine.setMaxAmbulances(amount);
                        engine.setMaxHelicopters(0);
                        
                        triggerEarthquake();
                        engine.start();
                    });
                    Thread.sleep(5000);
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    Platform.runLater(() -> {
                        engine.pause();
                        int casualties = 0;
                        for (Citizen c : city.getCitizens()) if (c.isDead()) casualties++;
                        quakeResults[idx] = casualties;
                        insightsList.getItems().add("Result: " + casualties + " casualties.");
                        latch.countDown();
                    });
                    latch.await();
                }

                // Phase 3: Flood Tests (Helicopters only)
                for (int i = 0; i < testAmounts.length; i++) {
                    final int amount = testAmounts[i];
                    final int idx = i;
                    Platform.runLater(() -> {
                        insightsList.getItems().add("Flood Test: " + amount + " Helicopters...");
                        speedCombo.setValue(simSpeedStr);
                        clearCity();
                        DashboardController.this.city = com.cityguardian.engine.CityGenerator.generateProceduralCity(city.getWidth(), city.getHeight(), seed);
                        engine = new SimulationEngine(DashboardController.this.city, DashboardController.this::onTick);
                        engine.setSpeed(simSpeedVal);
                        
                        truckInput.setText("0");
                        ambulanceInput.setText("0");
                        heliInput.setText(String.valueOf(amount));
                        engine.setMaxFiretrucks(0);
                        engine.setMaxAmbulances(0);
                        engine.setMaxHelicopters(amount);
                        
                        triggerFlood();
                        engine.start();
                    });
                    waitForSimulationToEnd();
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    Platform.runLater(() -> {
                        engine.pause();
                        int casualties = 0;
                        for (Citizen c : city.getCitizens()) if (c.isDead()) casualties++;
                        floodResults[idx] = casualties;
                        insightsList.getItems().add("Result: " + casualties + " casualties.");
                        latch.countDown();
                    });
                    latch.await();
                }

                StringBuilder promptBuilder = new StringBuilder();
                promptBuilder.append("You are an emergency logistics AI. The city budget is $").append(budget).append(". ")
                        .append("Cost: Firetrucks $40k, Ambulances $20k, Helicopters $80k.\n")
                        .append("I ran isolated tests for each disaster to find the impact of specific vehicles. Casualties:\n");

                promptBuilder.append("FIRE DISASTER (Firetrucks only): ");
                for (int i = 0; i < testAmounts.length; i++) {
                    promptBuilder.append(testAmounts[i]).append(" -> ").append(fireResults[i]).append(" cas");
                    if (i < testAmounts.length - 1) promptBuilder.append(", ");
                }
                promptBuilder.append(".\nEARTHQUAKE (Ambulances only): ");
                for (int i = 0; i < testAmounts.length; i++) {
                    promptBuilder.append(testAmounts[i]).append(" -> ").append(quakeResults[i]).append(" cas");
                    if (i < testAmounts.length - 1) promptBuilder.append(", ");
                }
                promptBuilder.append(".\nFLOOD (Helicopters only): ");
                for (int i = 0; i < testAmounts.length; i++) {
                    promptBuilder.append(testAmounts[i]).append(" -> ").append(floodResults[i]).append(" cas");
                    if (i < testAmounts.length - 1) promptBuilder.append(", ");
                }
                promptBuilder.append(".\nAnalyze the optimal number of vehicles needed for each disaster to minimize casualties. ")
                        .append("Then, combine these numbers. If the combined total cost exceeds $").append(budget)
                        .append(", you must reduce the numbers to fit the budget while prioritizing the most critical vehicles. ")
                        .append("Explain your reasoning and output the final counts. You MUST include strings like 'Firetrucks: X', 'Ambulances: Y', 'Helicopters: Z'.");
                
                String prompt = promptBuilder.toString();
                String apiKey = apiKeyInput.getText();
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    apiKey = System.getenv("NVIDIA_API_KEY");
                }

                Platform.runLater(() -> insightsList.getItems().add("Visual tests complete. Querying NVIDIA NIM API (Llama 3)..."));

                com.cityguardian.engine.OptimizationResult result = com.cityguardian.engine.NvidiaNimClient.optimizeResources(apiKey, prompt);

                Platform.runLater(() -> {
                    truckInput.setText(String.valueOf(result.firetrucks));
                    ambulanceInput.setText(String.valueOf(result.ambulances));
                    heliInput.setText(String.valueOf(result.helicopters));
                    
                    if (engine != null) {
                        engine.setMaxFiretrucks(result.firetrucks);
                        engine.setMaxAmbulances(result.ambulances);
                        engine.setMaxHelicopters(result.helicopters);
                    }
                    
                    insightsList.getItems().add("AI Optimization Complete!");
                    for (String part : result.explanation.split("\n")) {
                        if (!part.trim().isEmpty()) {
                            insightsList.getItems().add(part);
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> insightsList.getItems().add("Error during AI Optimization: " + e.getMessage()));
            }
        }).start();
    }

    private void waitForSimulationToEnd() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        int totalTiles = city.getWidth() * city.getHeight();
        while (System.currentTimeMillis() - startTime < 30000) { // Max 30s timeout
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            boolean[] isDone = new boolean[1];
            Platform.runLater(() -> {
                if (city.getDisasters().isEmpty()) {
                    isDone[0] = true;
                } else {
                    int redTiles = 0;
                    for (int x = 0; x < city.getWidth(); x++) {
                        for (int y = 0; y < city.getHeight(); y++) {
                            if (city.getTile(x, y).hasDisaster()) redTiles++;
                        }
                    }
                    if (redTiles >= totalTiles * 0.90) { // 90% covered
                        isDone[0] = true;
                    }
                }
                latch.countDown();
            });
            latch.await();
            if (isDone[0]) break;
            Thread.sleep(500);
        }
    }
}

package com.cityguardian;

import com.cityguardian.db.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize Database
        DatabaseManager.initialize();

        Parent root = FXMLLoader.load(getClass().getResource("/fxml/Dashboard.fxml"));
        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        
        primaryStage.setTitle("CityGuardian - AI Urban Crisis Response Simulator");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        DatabaseManager.close();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

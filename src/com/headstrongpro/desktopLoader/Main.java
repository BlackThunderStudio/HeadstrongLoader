package com.headstrongpro.desktopLoader;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * HeadstrongLoader
 * <p>
 * <p>
 * Created by rajmu on 17.06.07.
 */
public class Main extends Application{
    public static void main(String[] args) {
        launch(args);
    }

    private Stage stage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        stage = primaryStage;
        stage.setTitle("Headstrong launcher");

        stage.setOnCloseRequest(e -> {
            e.consume();
            closeApp();
        });

        stage.setResizable(false);

        Parent root = FXMLLoader.load(getClass().getResource("/layout/updater.fxml"));
        stage.setScene(new Scene(root));

        stage.show();
    }

    public void closeApp() {
        stage.close();
    }
}

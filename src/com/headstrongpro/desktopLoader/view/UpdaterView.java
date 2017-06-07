package com.headstrongpro.desktopLoader.view;

import com.jfoenix.controls.JFXProgressBar;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ThreadLocalRandom;

/**
 * HeadstrongLoader
 * <p>
 * <p>
 * Created by rajmu on 17.06.07.
 */
public class UpdaterView implements Initializable {
    @FXML
    public JFXProgressBar progressBar;
    @FXML
    public Label infoLabel;

    private static final String UPDATE_PATH = "http://remix1436.ct8.pl/resources/headstrong/version.json";
    private static final String UPDATE_ROOT = "desktop";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        infoLabel.setText("Checking for updates...");
        progressBar.setProgress(-1.0f);
        progressBar.setVisible(true);

        checkForUpdates.stateProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue.equals(Worker.State.SUCCEEDED)){
                progressBar.setVisible(false);
            }
        }));

        checkForUpdates.valueProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue != null){
                if(newValue){
                    infoLabel.setText("Update found! Downloading...");
                    downloadUpdate();
                } else {
                    infoLabel.setText("");
                    System.out.println("Starting the main application");
                    try {
                        openHeadstrongManager();
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                    Platform.exit();
                }
            }
        }));

        new Thread(checkForUpdates).start();
    }

    private void downloadUpdate() {
        //TODO: to be implemented
    }

    private Task<Boolean> checkForUpdates = new Task<Boolean>() {
        @Override
        protected Boolean call() throws Exception {
            JSONParser parser = new JSONParser();
            JSONObject rawJson = (JSONObject) parser.parse(new BufferedReader(new InputStreamReader(new URL(UPDATE_PATH).openStream())));
            rawJson = (JSONObject)rawJson.get(UPDATE_ROOT);
            String version = (String)rawJson.get("version");
            Thread.sleep(1000);
            System.out.println("server version: " + version);
            String localVersion = getLocalVersion();
            System.out.println("Local version: " + localVersion);

            //compare
            String[] verSplit = version.split("\\.");
            String[] localSplit = localVersion.split("\\.");

            for (int i = 0; i < localSplit.length; i++){
                int a = Integer.parseInt(localSplit[i]);
                int b = Integer.parseInt(verSplit[i]);
                if(a < b) return true;
            }
            return false;
        }
    };

    @NotNull
    private String getLocalVersion() {
        String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        path = path.substring(1, path.lastIndexOf('/')) + "/cfg";
        path = path.replaceAll("%20", " ");

        //test:
        path = "C:/users/rajmu/IdeaProjects/desktop-manager/out/artifacts/Headstrong_manager/cfg";
        try {
            JSONObject jsonObject = (JSONObject) new JSONParser().parse(
                    new InputStreamReader(new FileInputStream(path + "/update.json"))
            );
            jsonObject = (JSONObject)jsonObject.get("local");
            return (String)jsonObject.get("version");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private void openHeadstrongManager() throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"java", "-jar", "desktop_manager.jar"});
    }
}

package com.headstrongpro.desktopLoader.view;

import com.headstrongpro.core.UnzipUtility;
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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ResourceBundle;

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

    private String newVersionNumber;
    private String downloadedFilePath;

    private static final String UPDATE_PATH = "http://remix1436.ct8.pl/resources/headstrong/version.json";
    private static final String UPDATE_ROOT = "desktop";
    private static final String DOWNLOAD_ROOT = "http://headstrongpro.com/data/updates/update_";
    private static final int BUFFER_SIZE = 1024;

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
                    System.out.println("Update found! Downloading...");
                    new Thread(download).start();
                    try {
                        openHeadstrongManager();
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                    Platform.exit();
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

        download.valueProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue != null && newValue){
                String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
                path = path.substring(1, path.lastIndexOf('/')) + "/updates/update_" + newVersionNumber + ".zip";
                path = path.replaceAll("%20", " ");
                downloadedFilePath = path;
                progressBar.setProgress(-1.0f);
                infoLabel.setText("Extracting update...");
                System.out.println("Extracting update...");
                extract();
            } else {
                infoLabel.setText("ERROR!");
                progressBar.setVisible(false);
            }
        }));

        new Thread(checkForUpdates).start();
    }

    private void extract() {
        System.out.println("Exctract method called");
       new Thread(unzip).start();
        unzip.stateProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue.equals(Worker.State.SUCCEEDED)){
                progressBar.setVisible(false);
                infoLabel.setText("Update installation completed.");
                System.out.println("Update installation completed.");
            } else if(newValue.equals(Worker.State.FAILED) || newValue.equals(Worker.State.CANCELLED)){
                infoLabel.setText("ERROR!");
                progressBar.setVisible(false);
            }
        }));
    }

    private Task<Boolean> checkForUpdates = new Task<Boolean>() {
        @Override
        protected Boolean call() throws Exception {
            JSONParser parser = new JSONParser();
            JSONObject rawJson = (JSONObject) parser.parse(new BufferedReader(new InputStreamReader(new URL(UPDATE_PATH).openStream())));
            rawJson = (JSONObject)rawJson.get(UPDATE_ROOT);
            String version = (String)rawJson.get("version");
            Thread.sleep(1000);
            newVersionNumber = version;
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

    private Task<Boolean> download = new Task<Boolean>() {
        @Override
        protected Boolean call() throws Exception {
            URL url = new URL(DOWNLOAD_ROOT + newVersionNumber + ".zip");
            //TODO:test
            System.out.println("Downloading from: " + DOWNLOAD_ROOT + newVersionNumber + ".zip");
            HttpURLConnection httpURLConnection = (HttpURLConnection)url.openConnection();
            long fileSize = httpURLConnection.getContentLength();

            BufferedInputStream in = new BufferedInputStream(httpURLConnection.getInputStream());
            String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            path = path.substring(1, path.lastIndexOf('/')) + "/updates/";
            path = path.replaceAll("%20", " ");

            //TODO: test:
            path = "C:/Users/rajmu/Desktop/Headstrong desktop manager/bin/updates/";
            FileOutputStream fileOutputStream = new FileOutputStream(path + "update_" + newVersionNumber + ".zip");
            BufferedOutputStream bout = new BufferedOutputStream(fileOutputStream, BUFFER_SIZE);
            byte[] data = new byte[BUFFER_SIZE];
            long downloadedFileSize = 0;
            int x = 0;
            while ((x = in.read(data, 0, BUFFER_SIZE)) > 0){
                downloadedFileSize += x;
                bout.write(data, 0, x);
            }
            bout.close();
            in.close();

            return true;
        }
    };

    private Task<Void> unzip = new Task<Void>() {
        @Override
        protected Void call() throws Exception {
            UnzipUtility zipper = new UnzipUtility();
            String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            path = path.substring(1, path.lastIndexOf('/')) + "/";

            //TODO: test:
            path = "C:/Users/rajmu/Desktop/Headstrong desktop manager/";
            try {
                zipper.unzip(downloadedFilePath, path);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    };

    @NotNull
    private String getLocalVersion() {
        String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        path = path.substring(1, path.lastIndexOf('/')) + "/cfg";
        path = path.replaceAll("%20", " ");

        //TODO: test:
        path = "C:/Users/rajmu/Desktop/Headstrong desktop manager/cfg";
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

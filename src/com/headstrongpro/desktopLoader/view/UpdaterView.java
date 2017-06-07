package com.headstrongpro.desktopLoader.view;

import com.headstrongpro.core.UnzipUtility;
import com.jfoenix.controls.JFXProgressBar;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
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
                    Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                            "Bear in mind that in order to further use the application you MUST perform an update. \nDo you want to proceed?",
                            ButtonType.YES,
                            ButtonType.CLOSE);
                    a.setHeaderText("There is a new update available! Version " + newVersionNumber);
                    Optional<ButtonType> result = a.showAndWait();
                    result.ifPresent(buttonType -> {
                        if(buttonType.equals(ButtonType.YES)){
                            new Thread(download).start();
                        } else Platform.exit();
                    });
                } else {
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
            System.out.println("Downloading from: " + DOWNLOAD_ROOT + newVersionNumber + ".zip");
            HttpURLConnection httpURLConnection = (HttpURLConnection)url.openConnection();
            long fileSize = httpURLConnection.getContentLength();

            BufferedInputStream in = new BufferedInputStream(httpURLConnection.getInputStream());
            String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            path = path.substring(1, path.lastIndexOf('/')) + "/bin/updates/";
            path = path.replaceAll("%20", " ");

            System.out.println("Update destination: " + path + "update_" + newVersionNumber + ".zip");
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

            //extract
            String path1 = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            path1 = path1.substring(1, path1.lastIndexOf('/')) + "/bin/updates/update_" + newVersionNumber + ".zip";
            path1 = path1.replaceAll("%20", " ");

            downloadedFilePath = path1;
            progressBar.setProgress(-1.0f);
            infoLabel.setText("Extracting update...");
            System.out.println("Extracting update... " + downloadedFilePath + "");
            new Thread(unzip).start();
            return true;
        }
    };

    private Task<Void> unzip = new Task<Void>() {
        @Override
        protected Void call() throws Exception {
            UnzipUtility zipper = new UnzipUtility();
            String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            path = path.substring(1, path.lastIndexOf('/')) + "/";
            path = path.replaceAll("%20", " ");
            System.out.println("Update destination: " + path);

            try {
                zipper.unzip(downloadedFilePath, path);
            } catch (IOException e) {
                e.printStackTrace();
            }

            progressBar.setVisible(false);
            infoLabel.setText("Update installation completed.");
            System.out.println("Update installation completed.");
            setNewLocalVersion();

            System.out.println("Starting the main application");
            try {
                openHeadstrongManager();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            Platform.exit();
            return null;
        }
    };

    @NotNull
    private String getLocalVersion() {
        String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        path = path.substring(1, path.lastIndexOf('/')) + "/cfg";
        path = path.replaceAll("%20", " ");

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

    @SuppressWarnings("unchecked")
    private void setNewLocalVersion(){
        String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        path = path.substring(1, path.lastIndexOf('/')) + "/cfg";
        path = path.replaceAll("%20", " ");

        JSONObject obj = new JSONObject();
        JSONObject version = new JSONObject();
        version.put("version", newVersionNumber);
        obj.put("local", version);
        try (FileWriter fileWriter = new FileWriter(path + "/update.json")){
            fileWriter.write(obj.toJSONString());
            fileWriter.flush();
            System.out.println("Updating version to: " + obj);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openHeadstrongManager() throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"java", "-jar", "desktop_manager.jar"});
    }
}

package com.headstrongpro.desktopLoader.view;

import com.headstrongpro.core.UnzipUtility;
import com.jfoenix.controls.JFXProgressBar;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
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
    @FXML
    public Label secondaryLabel;

    private String newVersionNumber;
    private String remoteLauncherVersion;
    private String downloadedFilePath;

    private static final String LAUNCHER_VERSION = "1.0.0";

    private static String UPDATE_PATH;
    private static String UPDATE_ROOT;
    private static String DOWNLOAD_ROOT;
    private static String UPDATE_LOCAL;
    private static final int BUFFER_SIZE = 1024;
    private static String LAUNCHER_DOWNLOAD;
    private static String TARGET;

    private static final String PROGRESS_INFINITE = "infinite";
    private static final String SECONDARY_DEFAULT = "This might take a while.";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadConfigData();

        infoLabel.setText("Checking for updates...");
        progressBar.setProgress(-1.0f);
        progressBar.setVisible(true);
        secondaryLabel.setText(SECONDARY_DEFAULT);

        download.progressProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue != null){
                progressBar.setProgress(newValue.doubleValue());
                secondaryLabel.setText(String.format("Download progress: %.2f%%", newValue.doubleValue() * 100));
            }
        }));

        unzip.messageProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue != null){
                if(newValue.equals(PROGRESS_INFINITE)) {
                    progressBar.setProgress(-1.0f);
                    secondaryLabel.setText(SECONDARY_DEFAULT);
                }
                else {
                    infoLabel.setText(newValue);
                    System.out.printf("[Info] %s", newValue);
                }
            }
        }));

        checkForUpdates.valueProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue != null){
                if(newValue){
                    infoLabel.setText("Update found! Downloading...");
                    System.out.println("Update found! Downloading...");
                    Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                            "Bear in mind that in order to further use the application you SHOULD perform an update. Otherwise any damages or unwanted behaviour caused by the outdated version fall into the user's responsibility. \nDo you want to proceed?",
                            ButtonType.YES,
                            ButtonType.CANCEL,
                            ButtonType.CLOSE);
                    a.setHeaderText("There is a new update available! Version " + newVersionNumber);
                    Optional<ButtonType> result = a.showAndWait();
                    result.ifPresent(buttonType -> {
                        if(buttonType.equals(ButtonType.YES)){
                            new Thread(download).start();
                        } else if(buttonType.equals(ButtonType.CANCEL)){
                            startMainApp();
                        } else Platform.exit();
                    });
                } else {
                    startMainApp();
                }
            } else notifyFailure();
        }));

        download.stateProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue.equals(Worker.State.SUCCEEDED)){
                //extract
                String path1 = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
                path1 = path1.substring(1, path1.lastIndexOf('/')) + UPDATE_LOCAL + newVersionNumber + ".zip";
                path1 = path1.replaceAll("%20", " ");

                downloadedFilePath = path1;
                infoLabel.setText("Extracting update...");
                System.out.println("Extracting update... " + downloadedFilePath + "");
                new Thread(unzip).start();
            } else if(newValue.equals(Worker.State.FAILED) || newValue.equals(Worker.State.CANCELLED)){
                notifyFailure();
            }
        }));

        unzip.stateProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue.equals(Worker.State.SUCCEEDED)){
                setNewLocalVersion();

                System.out.println("Starting the main application");
                try {
                    openHeadstrongManager();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                Platform.exit();
            } else if(newValue.equals(Worker.State.FAILED) || newValue.equals(Worker.State.CANCELLED)){
                notifyFailure();
            }
        }));

        checkLauncherVersion.valueProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue != null){
                if(newValue){
                    //download newest
                    Alert alert = new Alert(Alert.AlertType.WARNING,
                            "This update requires you to re-downlaod the whole application due to backwards incompatible changes. Please click OK to be redirected to the download location.",
                            ButtonType.OK,
                            ButtonType.CLOSE);
                    alert.setHeaderText("New update for the launcher is available. Version " + remoteLauncherVersion);
                    Optional<ButtonType> response = alert.showAndWait();
                    response.ifPresent(e -> {
                        if(e.equals(ButtonType.OK)){
                            try {
                                Desktop.getDesktop().browse(new URL(LAUNCHER_DOWNLOAD + remoteLauncherVersion + ".exe").toURI());
                                Platform.exit();
                            } catch (IOException | URISyntaxException e1) {
                                e1.printStackTrace();
                                notifyFailure();
                            }
                        } else {
                            Platform.exit();
                        }
                    });
                } else {
                    new Thread(checkForUpdates).start();
                }
            } else notifyFailure();
        }));

        new Thread(checkLauncherVersion).start();
    }

    private void startMainApp(){
        System.out.println("Starting the main application");
        try {
            openHeadstrongManager();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        Platform.exit();
    }

    private void notifyFailure(){
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText("Unexpected error occurred!");
        a.setContentText("Please try again later.");
        Optional<ButtonType> response = a.showAndWait();
        response.ifPresent(e -> Platform.exit());
    }

    private Task<Boolean> checkLauncherVersion = new Task<Boolean>() {
        @Contract(pure = true)
        @Override
        protected Boolean call() throws Exception {
            JSONObject json = (JSONObject) new JSONParser().parse(new BufferedReader(new InputStreamReader(new URL(UPDATE_PATH).openStream())));
            json = (JSONObject)json.get(UPDATE_ROOT);
            String version = (String)json.get("launcher");
            Thread.sleep(500);
            remoteLauncherVersion = version;
            System.out.printf("Local launcher version: %s\nRemote launcher version: %s", LAUNCHER_VERSION, remoteLauncherVersion);

            return compVersions(LAUNCHER_VERSION, remoteLauncherVersion);
        }
    };

    private Task<Boolean> checkForUpdates = new Task<Boolean>() {
        @NotNull
        @Override
        protected Boolean call() throws Exception {
            JSONParser parser = new JSONParser();
            JSONObject rawJson = (JSONObject) parser.parse(new BufferedReader(new InputStreamReader(new URL(UPDATE_PATH).openStream())));
            rawJson = (JSONObject)rawJson.get(UPDATE_ROOT);
            String version = (String)rawJson.get("version");
            Thread.sleep(500);
            newVersionNumber = version;
            System.out.println("server version: " + version);
            String localVersion = getLocalVersion();
            System.out.println("Local version: " + localVersion);

            //compare
            return compVersions(localVersion, version);
        }
    };

    private boolean compVersions(String local, String remote){
        String[] verSplit = remote.split("\\.");
        String[] localSplit = local.split("\\.");

        for (int i = 0; i < localSplit.length; i++){
            int a = Integer.parseInt(localSplit[i]);
            int b = Integer.parseInt(verSplit[i]);
            if(a < b) return true;
        }
        return false;
    }

    private Task<Boolean> download = new Task<Boolean>() {
        @NotNull
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
                updateProgress(downloadedFileSize, fileSize);
            }
            bout.close();
            in.close();
            return true;
        }
    };

    private Task<Void> unzip = new Task<Void>() {
        @Nullable
        @Override
        protected Void call() throws Exception {
            updateMessage(PROGRESS_INFINITE);
            UnzipUtility zipper = new UnzipUtility();
            String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            path = path.substring(1, path.lastIndexOf('/')) + "/";
            path = path.replaceAll("%20", " ");
            System.out.println("Update destination: " + path);

            try {
                zipper.unzip(downloadedFilePath, path);
            } catch (IOException e) {
                e.printStackTrace();
                notifyFailure();
            }

            progressBar.setVisible(false);
            updateMessage("Update installation completed.");
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
            notifyFailure();
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
            notifyFailure();
        }
    }

    private void loadConfigData(){
        System.out.println("Initializing the launcher...");
        String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        path = path.substring(1, path.lastIndexOf('/')) + "/cfg";
        path = path.replaceAll("%20", " ");

        try {
            System.out.println("Loading launcher config data...");
            JSONObject rootObject = (JSONObject) new JSONParser().parse(new InputStreamReader(new FileInputStream(path + "/launcher.json")));
            JSONObject obj = (JSONObject) rootObject.get("remote_path");
            UPDATE_PATH = (String)obj.get("update");
            DOWNLOAD_ROOT = (String)obj.get("download");
            LAUNCHER_DOWNLOAD = (String)obj.get("launcher");
            obj = (JSONObject)rootObject.get("local_path");
            UPDATE_ROOT = (String)rootObject.get("update_root");
            UPDATE_LOCAL = (String)obj.get("download");
            TARGET = (String)rootObject.get("target");
            System.out.println("Data loaded.");
        } catch (IOException | ParseException e) {
            e.printStackTrace();
            notifyFailure();
        }
    }

    private void openHeadstrongManager() throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"java", "-jar", TARGET + ".jar"});
    }
}

package edu.cmu.hcii.sugilite.pumice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.EditTextPreference;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.ArraySet;
import android.widget.Toast;
import android.util.Log;

import edu.cmu.hcii.sugilite.accessibility_service.SugiliteAccessibilityService;
import edu.cmu.hcii.sugilite.recording.newrecording.fullscreen_overlay.SugiliteRecordingConfirmationDialog;
import tech.gusavila92.websocketclient.WebSocketClient;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IllegalFormatCodePointException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cmu.hcii.sugilite.Const;
import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.automation.ServiceStatusManager;
import edu.cmu.hcii.sugilite.communication.SugiliteBlockJSONProcessor;
import edu.cmu.hcii.sugilite.communication.SugiliteCommunicationHelper;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptDao;
import edu.cmu.hcii.sugilite.model.block.SugiliteBlock;
import edu.cmu.hcii.sugilite.model.block.SugiliteStartingBlock;
import edu.cmu.hcii.sugilite.model.variable.Variable;
import edu.cmu.hcii.sugilite.ontology.CombinedOntologyQuery;
import edu.cmu.hcii.sugilite.ontology.LeafOntologyQuery;
import edu.cmu.hcii.sugilite.ontology.OntologyQuery;
import edu.cmu.hcii.sugilite.ontology.SerializableUISnapshot;
import edu.cmu.hcii.sugilite.ontology.SugiliteRelation;
import edu.cmu.hcii.sugilite.ontology.SugiliteSerializableEntity;
import edu.cmu.hcii.sugilite.ontology.UISnapshot;
import edu.cmu.hcii.sugilite.pumice.dialog.PumiceDialogManager;
import edu.cmu.hcii.sugilite.recording.SugiliteScreenshotManager;
import edu.cmu.hcii.sugilite.ui.LocalScriptDetailActivity;
import edu.cmu.hcii.sugilite.ui.dialog.NewScriptDialog;
import edu.cmu.hcii.sugilite.ui.dialog.VariableSetValueDialog;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.VerbalInstructionIconManager;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.speech.SugiliteAndroidAPIVoiceRecognitionListener;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.speech.SugiliteGoogleCloudVoiceRecognitionListener;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.speech.SugiliteVoiceRecognitionListener;
import okhttp3.WebSocket;

import static edu.cmu.hcii.sugilite.Const.OVERLAY_TYPE;

/**
 * @author toby
 * @date 1/7/19
 * @time 2:44 PM
 */
public class PumiceDemonstrationUtil {
    /**
     * initiate a demonstration recording -> need to call endRecording() when the recording ends
     * @param context
     * @param serviceStatusManager
     * @param sharedPreferences
     * @param scriptName
     * @param sugiliteData
     * @param afterRecordingCallback
     * @param sugiliteScriptDao
     * @param verbalInstructionIconManager
     */

    private static WebSocketClient webSocketClient=null;
    private static boolean isCleared = false;
    private static boolean clearFailed = false;

    public static void initiateDemonstration(Context context, ServiceStatusManager serviceStatusManager, SharedPreferences sharedPreferences, String scriptName, SugiliteData sugiliteData, Runnable afterRecordingCallback, SugiliteScriptDao sugiliteScriptDao, VerbalInstructionIconManager verbalInstructionIconManager){
        if(!serviceStatusManager.isRunning()){
            //prompt the user if the accessibility service is not active
            AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
            builder1.setTitle("Service not running")
                    .setMessage("The " + Const.appNameUpperCase + " accessiblity service is not enabled. Please enable the service in the phone settings before recording.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            serviceStatusManager.promptEnabling();
                            //do nothing
                        }
                    }).show();
        } else {
            //Register to the server and start the recording
            createWebSocketClient();
            System.out.println("Executed create web socket client");

            //start demonstration
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("scriptName", scriptName);
            editor.putBoolean("recording_in_process", true);
            editor.commit();

            //set the system state
            sugiliteData.setCurrentSystemState(SugiliteData.RECORDING_STATE);


            //set the active script to the newly created script
            sugiliteData.initiateScriptRecording(PumiceDemonstrationUtil.addScriptExtension(scriptName), afterRecordingCallback); //add the end recording callback
            sugiliteData.initiatedExternally = false;

            //save the newly created script to DB
            try {
                sugiliteScriptDao.save(sugiliteData.getScriptHead());
                sugiliteScriptDao.commitSave(null);
            }
            catch (Exception e){
                e.printStackTrace();
            }


            //send the phone back to the home screen
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(startMain);

            //turn on the cat overlay to prepare for demonstration
            if(verbalInstructionIconManager != null){
                verbalInstructionIconManager.turnOnCatOverlay();
            }
        }
    }

    public static void initiateDemonstration(Context context, ServiceStatusManager serviceStatusManager, SharedPreferences sharedPreferences, String scriptName, SugiliteData sugiliteData, Runnable afterRecordingCallback, SugiliteScriptDao sugiliteScriptDao, VerbalInstructionIconManager verbalInstructionIconManager, String packageName){
        if(!serviceStatusManager.isRunning()){
            //prompt the user if the accessibility service is not active
            AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
            builder1.setTitle("Service not running")
                    .setMessage("The " + Const.appNameUpperCase + " accessiblity service is not enabled. Please enable the service in the phone settings before recording.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            serviceStatusManager.promptEnabling();
                            //do nothing
                        }
                    }).show();
        } else {
            //Register to the server and start the recording
            createWebSocketClient();
            System.out.println("Executed create web socket client");

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    JSONObject startRecordSM=new JSONObject();
                    try {
                        startRecordSM.put("action","START");
                        startRecordSM.put("package_name", packageName);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    webSocketClient.send(String.valueOf(startRecordSM));
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    isCleared = false;
                                    clearFailed = false;
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putString("scriptName", scriptName);
                                    editor.putBoolean("recording_in_process", true);
                                    editor.commit();

                                    //set the system state
                                    sugiliteData.setCurrentSystemState(SugiliteData.RECORDING_STATE);


                                    //set the active script to the newly created script
                                    sugiliteData.initiateScriptRecording(PumiceDemonstrationUtil.addScriptExtension(scriptName), afterRecordingCallback); //add the end recording callback
                                    sugiliteData.initiatedExternally = false;

                                    //save the newly created script to DB
                                    try {
                                        sugiliteScriptDao.save(sugiliteData.getScriptHead());
                                        sugiliteScriptDao.commitSave(null);
                                    }
                                    catch (Exception e){
                                        e.printStackTrace();
                                    }

                                    context.startActivity(context.getPackageManager().getLaunchIntentForPackage(packageName));  //Start the specified application
                                    //turn on the cat overlay to prepare for demonstration
                                    if(verbalInstructionIconManager != null){
                                        verbalInstructionIconManager.turnOnCatOverlay();
                                    }
                                }
                            },0);


                        }
                    },500);
                    //start demonstration


                }
            },1500);






        }
    }

    /**
     * execute a script  --> check the service status and the variable values before doing so
     * @param activityContext
     * @param serviceStatusManager
     * @param script
     * @param sugiliteData
     * @param sharedPreferences
     * @param dialogManager
     */
    public static void executeScript(Activity activityContext, ServiceStatusManager serviceStatusManager, SugiliteStartingBlock script, SugiliteData sugiliteData, SharedPreferences sharedPreferences, boolean isForReconstructing, @Nullable PumiceDialogManager dialogManager, @Nullable SugiliteBlock afterExecutionOperation, @Nullable Runnable afterExecutionRunnable){
        if(!serviceStatusManager.isRunning()){
            //prompt the user if the accessiblity service is not active
            activityContext.runOnUiThread(() -> {
                AlertDialog.Builder builder1 = new AlertDialog.Builder(activityContext);
                builder1.setTitle("Service not running")
                        .setMessage("The Sugilite accessiblity service is not enabled. Please enable the service in the phone settings before recording.")
                        .setPositiveButton("OK", (dialog, which) -> {
                            serviceStatusManager.promptEnabling();
                            //do nothing
                        }).show();
            });
        }
        else {
            //check if pumice dialog manager is available, create a new one if needed
            if (dialogManager == null) {
                if (sugiliteData.pumiceDialogManager != null) {
                    dialogManager = sugiliteData.pumiceDialogManager;
                } else {
                    dialogManager = new PumiceDialogManager(activityContext, true);
                    SugiliteVoiceRecognitionListener sugiliteVoiceRecognitionListener = null;
                    TextToSpeech tts = sugiliteData.getTTS();
                    if (Const.SELECTED_SPEECH_RECOGNITION_TYPE == Const.SpeechRecognitionType.ANDROID) {
                        sugiliteVoiceRecognitionListener = new SugiliteAndroidAPIVoiceRecognitionListener(activityContext, null, tts);
                    } else if (Const.SELECTED_SPEECH_RECOGNITION_TYPE == Const.SpeechRecognitionType.GOOGLE_CLOUD) {
                        sugiliteVoiceRecognitionListener = new SugiliteGoogleCloudVoiceRecognitionListener(activityContext, sugiliteData,  null, tts);
                    }
                    dialogManager.setSugiliteVoiceRecognitionListener(sugiliteVoiceRecognitionListener);
                    sugiliteData.pumiceDialogManager = dialogManager;
                }
            }

            final PumiceDialogManager finalDialogManager = dialogManager;

            activityContext.runOnUiThread(() -> {
                VariableSetValueDialog variableSetValueDialog = new VariableSetValueDialog(sugiliteData.getApplicationContext(), sugiliteData, script, sharedPreferences, SugiliteData.EXECUTION_STATE, finalDialogManager, isForReconstructing);
                if(script.variableNameDefaultValueMap.size() > 0) {

                    //has variable
                    sugiliteData.variableNameVariableValueMap.putAll(script.variableNameDefaultValueMap);
                    boolean needUserInput = false;

                    //check if any of the variables needs user input
                    for(Map.Entry<String, Variable> entry : script.variableNameVariableObjectMap.entrySet()){
                        if(entry.getValue().getVariableType() == Variable.USER_INPUT){
                            needUserInput = true;
                            break;
                        }
                    }
                    if(needUserInput) {
                        //show the dialog to obtain user input
                        variableSetValueDialog.show();
                    }
                    else {
                        variableSetValueDialog.executeScript(afterExecutionOperation, finalDialogManager, afterExecutionRunnable);
                    }
                }
                else{
                    //execute the script without showing the dialog
                    variableSetValueDialog.executeScript(afterExecutionOperation, finalDialogManager, afterExecutionRunnable);
                }
            });
        }
    }

    /**
     * end the current recording
     * @param context
     * @param sugiliteData
     * @param sharedPreferences
     * @param sugiliteScriptDao
     */
    public static void endRecording(Context context, SugiliteData sugiliteData, SharedPreferences sharedPreferences, SugiliteScriptDao sugiliteScriptDao) {
        SharedPreferences.Editor prefEditor = sharedPreferences.edit();
        SugiliteBlockJSONProcessor jsonProcessor = new SugiliteBlockJSONProcessor(context);
        //end recording
        prefEditor.putBoolean("recording_in_process", false);
        prefEditor.apply();
        JSONObject endRecordSM=new JSONObject();
        SugiliteRecordingConfirmationDialog.setStep(0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sugiliteScriptDao != null) {
                        if (sugiliteData.getScriptHead() != null) {
                            if (sugiliteData.verbalInstructionIconManager != null) {
                                SugiliteScreenshotManager sugiliteScreenshotManager = SugiliteScreenshotManager.getInstance(sharedPreferences, sugiliteData);
                                UISnapshot latestUISnapshot = sugiliteData.verbalInstructionIconManager.getLatestUISnapshot();
                                if (latestUISnapshot != null) {
                                    latestUISnapshot.annotateStringEntitiesIfNeeded();
                                }
                                sugiliteData.getScriptHead().uiSnapshotOnEnd = new SerializableUISnapshot(latestUISnapshot);
                                String outputPath = Paths.get(String.valueOf(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)),NewScriptDialog.getPackageName(),"RECORDER").toString();
                                sugiliteScreenshotManager.setDirectoryPath(outputPath + "/");
                                sugiliteData.getScriptHead().screenshotOnEnd = sugiliteScreenshotManager.takeScreenshot(SugiliteScreenshotManager.DIRECTORY_PATH, "S_END.png", 100);
                                try {
                                    SugiliteAccessibilityService sugiliteAccessibilityService = (SugiliteAccessibilityService) context;
                                    sugiliteAccessibilityService.captureLayout(outputPath.toString(), "S_END.xml", 100);
                                }
                                catch (NullPointerException e){
                                    e.printStackTrace();
                                }
                                try {
                                    endRecordSM.put("action","ENDRECORD");
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                webSocketClient.send(String.valueOf(endRecordSM));
                                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            createTarGzipFolder(Paths.get(String.valueOf(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)), NewScriptDialog.getPackageName()));
                                            File tarFile = new File(String.valueOf(Paths.get(String.valueOf(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)), NewScriptDialog.getPackageName()+".tar.gz")));
                                            webSocketClient.send(Files.readAllBytes(tarFile.toPath()));
                                            File rootFile = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                                            for(File file : rootFile.listFiles() ){
                                                if (file.isDirectory()) {
                                                    FileUtils.deleteDirectory(file);
                                                }
                                                else if (file.isFile()){
                                                    file.delete();
                                                }
                                            }

                                        } catch (IOException exception) {
                                            exception.printStackTrace();
                                        }
                                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                if(null != webSocketClient){
                                                    webSocketClient.close();
                                                }
                                            }
                                        },1000);

                                    }
                                },1500);

                            }

                            Path jsonScriptPath;
                            jsonScriptPath = Paths.get(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) + "/"+NewScriptDialog.getPackageName() + "/RECORDER");
                            if (!Files.exists(jsonScriptPath)){
                                jsonScriptPath.toFile().mkdirs();
                            }

                            sugiliteScriptDao.save(sugiliteData.getScriptHead());
                        }
                        sugiliteScriptDao.commitSave(new Runnable() {
                            @Override
                            public void run() {
                                //invoke the callback
                                if (sugiliteData.initiatedExternally && sugiliteData.getScriptHead() != null) {
                                    //return the recording to the external caller
                                    sugiliteData.communicationController.sendRecordingFinishedSignal(sugiliteData.getScriptHead().getScriptName());
                                    sugiliteData.sendCallbackMsg(SugiliteCommunicationHelper.FINISHED_RECORDING, jsonProcessor.scriptToJson(sugiliteData.getScriptHead()), sugiliteData.callbackString);
                                }

                                //call the after recording callback
                                if (sugiliteData.getScriptHead() != null && sugiliteData.afterRecordingCallback != null){
                                    //call the endRecordingCallback
                                    Runnable r = sugiliteData.afterRecordingCallback;
                                    sugiliteData.afterRecordingCallback = null;
                                    r.run();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();





        //turn off the recording overlay if any
        if(sugiliteData.verbalInstructionIconManager != null){
            sugiliteData.verbalInstructionIconManager.turnOffCatOverlay();
        }


        sugiliteData.setCurrentSystemState(SugiliteData.DEFAULT_STATE);
        PumiceDemonstrationUtil.showSugiliteToast("end recording", Toast.LENGTH_SHORT);
    }

    public static void showSugiliteToast(String text, int length) {
        SugiliteData.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SugiliteData.getAppContext(), text, length).show();

            }
        });
    }

    public static void showSugiliteAlertDialog(String content) {
        SugiliteData.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = new AlertDialog.Builder(SugiliteData.getAppContext()).setMessage(content).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create();
                dialog.getWindow().setType(OVERLAY_TYPE);
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }
        });
    }

    public static String removeScriptExtension (String scriptName) {
        if (scriptName.endsWith(".SugiliteScript")) {
            return scriptName.replace(".SugiliteScript", "");
        } else {
            return scriptName;
        }
    }

    public static String addScriptExtension (String scriptName) {
        if (scriptName.endsWith(".SugiliteScript")) {
            return scriptName;
        } else {
            return scriptName + ".SugiliteScript";
        }
    }

    public static String joinListGrammatically(final List<String> list, String lastWordSeparator) {
        if (list.size() == 0) {
            return "";
        }
        return list.size() > 1
                ? StringUtils.join(list.subList(0, list.size() - 1), ", ")
                .concat(String.format("%s %s ", list.size() > 2 ? "," : "", lastWordSeparator))
                .concat(list.get(list.size() - 1))
                : list.get(0);
    }

    public static String boldify(String string){
        return "<b>" + string + "</b>";
    }

    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static int pxToDp(int px) {
        return (int) (px / Resources.getSystem().getDisplayMetrics().density);
    }
    public static boolean isInputMethodPackageName (String packageName) {
        Set<String> inputMethodNames = new HashSet<>(Arrays.asList(Const.INPUT_METHOD_PACKAGE_NAMES));
        return inputMethodNames.contains(packageName);
    }

    public static Drawable getScaledDrawable(Drawable drawable, double scale) {
        return getScaledDrawable(drawable, (int) (scale * drawable.getIntrinsicWidth()), (int) (scale * drawable.getIntrinsicHeight()));
    }

    public static Drawable getScaledDrawable(Drawable drawable, int width, int height) {
        //scale the drawable so it fits into the dialog
        // Read your drawable from somewhere
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        // Scale it to 50 x 50
        Drawable d = new BitmapDrawable(SugiliteData.getAppContext().getResources(), Bitmap.createScaledBitmap(bitmap, width, height, true));
        // Set your new, scaled drawable "d"
        return d;
    }

    public static boolean checkIfOntologyQueryContains (OntologyQuery ontologyQuery, SugiliteRelation relation, Object value) {
        if (ontologyQuery instanceof LeafOntologyQuery) {
            if (((LeafOntologyQuery) ontologyQuery).getR().equals(relation)) {
                Set<SugiliteSerializableEntity> objectEntities = ((LeafOntologyQuery) ontologyQuery).getObject();
                if (objectEntities != null) {
                    for (SugiliteSerializableEntity entity : objectEntities) {
                        if (value.equals(entity.getEntityValue())) {
                            return true;
                        }
                    }
                }
            }
        }
        if (ontologyQuery instanceof CombinedOntologyQuery) {
            Set<OntologyQuery> subQueries = ((CombinedOntologyQuery) ontologyQuery).getSubQueries();
            if (subQueries != null) {
                for (OntologyQuery subQuery : subQueries) {
                    boolean subQueryContains = checkIfOntologyQueryContains(subQuery, relation, value);
                    if (subQueryContains) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static Location getBestLocation() {
        LocationManager lm = (LocationManager) SugiliteData.getAppContext().getSystemService(Context.LOCATION_SERVICE);


        if (ActivityCompat.checkSelfPermission(SugiliteData.getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(SugiliteData.getAppContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //TODO: handle when the permission is not granted
        }
        List<String> providers = lm.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            Location l = lm.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                // Found best last known location: %s", l);
                bestLocation = l;
            }
        }
        return bestLocation;
    }

    private static void createWebSocketClient() {
        URI uri;
        try {
            String serverAddress = NewScriptDialog.getServerAddress();
            System.out.println("serverAddress is : "+serverAddress);
//            uri = new URI("ws://"+ serverAddress + ":8765");
            uri = new URI(serverAddress);
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen() {
                    Log.i("WebSocket", "Session is starting");
                    JSONObject registerSM = new JSONObject();

                    try {
                        registerSM.put("action","REGISTER");
                        registerSM.put("name", "RECORDER");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    webSocketClient.send(String.valueOf(registerSM));
                }

                @Override
                public void onTextReceived(String message) {
                    if (message.equals("cleared")){
                        isCleared = true;
                    }
                    else if (message.equals("clear failed")){
                        clearFailed = true;
                    }

                }


                @Override
                public void onBinaryReceived(byte[] data) {
                }

                @Override
                public void onPingReceived(byte[] data) {
                }

                @Override
                public void onPongReceived(byte[] data) {
                }

                @Override
                public void onException(Exception e) {
                    System.out.println(e.getMessage());
                }

                @Override
                public void onCloseReceived() {
                    Log.i("WebSocket", "Closed ");
                    System.out.println("onCloseReceived");
                }
            };
            webSocketClient.setConnectTimeout(10000);
            webSocketClient.setReadTimeout(60000);
            webSocketClient.enableAutomaticReconnection(5000);
            webSocketClient.connect();



    }

    public static WebSocketClient getWebSocketClientInst(){
        if(webSocketClient != null) return webSocketClient;
        createWebSocketClient();
        return webSocketClient;
    }

    public static void createTarGzipFiles(List<Path> paths, Path output) throws IOException {
        try (OutputStream fOut = Files.newOutputStream(output);
             BufferedOutputStream buffOut = new BufferedOutputStream(fOut);
             GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(buffOut);
             TarArchiveOutputStream tOut = new TarArchiveOutputStream(gzOut)) {
            for (Path path : paths) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(
                        path.toFile(),
                        path.getFileName().toString());
                tOut.putArchiveEntry(tarEntry);
                Files.copy(path, tOut);
                tOut.closeArchiveEntry();
            }
            tOut.finish();
        }
    }

    public static List<Path> getRelevantScreenShot(String scriptName){
        File rootDir=new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/edu.cmu.hcii.sugilite/ScreenShot/");
        List<Path> relevantPaths = new ArrayList<>();
        for (File file : rootDir.listFiles()){
            if(file.getName().contains(scriptName))
                relevantPaths.add(file.toPath());
        }
        return relevantPaths;
    }

    public static void createTarGzipFolder(Path source) throws IOException {


        String tarFileName = source.toFile()+".tar.gz";

        try (OutputStream fOut = Files.newOutputStream(Paths.get(tarFileName));
             BufferedOutputStream buffOut = new BufferedOutputStream(fOut);
             GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(buffOut);
             TarArchiveOutputStream tOut = new TarArchiveOutputStream(gzOut)) {

            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {


                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attributes) {

                    if (attributes.isSymbolicLink()) {
                        return FileVisitResult.CONTINUE;
                    }

                    Path targetFile = source.getParent().relativize(file);

                    try {
                        TarArchiveEntry tarEntry = new TarArchiveEntry(
                                file.toFile(), targetFile.toString());

                        tOut.putArchiveEntry(tarEntry);

                        Files.copy(file, tOut);

                        tOut.closeArchiveEntry();

                    } catch (IOException e) {

                    }

                    return FileVisitResult.CONTINUE;
                }
            });
            tOut.finish();
        } catch (Exception e){
            e.printStackTrace();
        }

    }







}

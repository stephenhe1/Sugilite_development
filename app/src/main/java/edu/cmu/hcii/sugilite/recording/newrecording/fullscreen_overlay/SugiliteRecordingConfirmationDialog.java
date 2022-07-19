package edu.cmu.hcii.sugilite.recording.newrecording.fullscreen_overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import edu.cmu.hcii.sugilite.Const;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptDao;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptFileDao;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptSQLDao;
import edu.cmu.hcii.sugilite.model.Node;
import edu.cmu.hcii.sugilite.R;
import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.model.block.util.SugiliteAvailableFeaturePack;
import edu.cmu.hcii.sugilite.model.block.SugiliteOperationBlock;
import edu.cmu.hcii.sugilite.model.operation.trinary.SugiliteLoadVariableOperation;
import edu.cmu.hcii.sugilite.ontology.OntologyQuery;
import edu.cmu.hcii.sugilite.ontology.SugiliteEntity;
import edu.cmu.hcii.sugilite.ontology.UISnapshot;
import edu.cmu.hcii.sugilite.ontology.description.OntologyDescriptionGenerator;
import edu.cmu.hcii.sugilite.pumice.PumiceDemonstrationUtil;
import edu.cmu.hcii.sugilite.recording.newrecording.SugiliteBlockBuildingHelper;
import edu.cmu.hcii.sugilite.recording.newrecording.dialog_management.SugiliteDialogManager;
import edu.cmu.hcii.sugilite.recording.newrecording.dialog_management.SugiliteDialogSimpleState;
import edu.cmu.hcii.sugilite.recording.newrecording.dialog_management.SugiliteDialogUtteranceFilter;
import edu.cmu.hcii.sugilite.ui.dialog.NewScriptDialog;
import tech.gusavila92.websocketclient.WebSocketClient;

import static edu.cmu.hcii.sugilite.Const.OVERLAY_TYPE;
import static edu.cmu.hcii.sugilite.Const.SQL_SCRIPT_DAO;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author toby
 * @date 2/16/18
 * @time 4:37 PM
 */
public class SugiliteRecordingConfirmationDialog extends SugiliteDialogManager {
    private SugiliteOperationBlock block;
    private SugiliteAvailableFeaturePack featurePack;
    private List<Pair<OntologyQuery, Double>> queryScoreList;
    private Runnable clickUnderlyingButtonRunnable;
    private SugiliteBlockBuildingHelper blockBuildingHelper;
    private LayoutInflater layoutInflater;
    private UISnapshot uiSnapshot;
    private SugiliteEntity<Node> actualClickedNode;
    private SugiliteData sugiliteData;
    private SharedPreferences sharedPreferences;
    private OntologyDescriptionGenerator ontologyDescriptionGenerator;
    private Dialog dialog;
    private View dialogView;
    private TextView confirmationPromptTextView;
    private ImageButton speakButton;
    private SugiliteScriptDao sugiliteScriptDao;


    //construct the 2 states
    private SugiliteDialogSimpleState askingForConfirmationState = new SugiliteDialogSimpleState("ASKING_FOR_CONFIRMATION", this, true);
    private SugiliteDialogSimpleState detailPromptState = new SugiliteDialogSimpleState("DETAIL_PROMPT", this, true);


    public SugiliteRecordingConfirmationDialog(Context context, SugiliteOperationBlock block, SugiliteAvailableFeaturePack featurePack, List<Pair<OntologyQuery, Double>> queryScoreList, Runnable clickUnderlyingButtonRunnable, SugiliteBlockBuildingHelper blockBuildingHelper, UISnapshot uiSnapshot, SugiliteEntity<Node> actualClickedNode, SugiliteData sugiliteData, SharedPreferences sharedPreferences, TextToSpeech tts) {
        super(context, sugiliteData, tts);
        this.context = context;
        this.block = block;
        this.featurePack = featurePack;
        this.queryScoreList = queryScoreList;
        this.clickUnderlyingButtonRunnable = clickUnderlyingButtonRunnable;
        this.blockBuildingHelper = blockBuildingHelper;
        this.layoutInflater = LayoutInflater.from(context);
        this.uiSnapshot = uiSnapshot;
        this.actualClickedNode = actualClickedNode;
        this.sugiliteData = sugiliteData;
        this.sharedPreferences = sharedPreferences;
        this.ontologyDescriptionGenerator = new OntologyDescriptionGenerator();

        if(Const.DAO_TO_USE == SQL_SCRIPT_DAO) {
            sugiliteScriptDao = new SugiliteScriptSQLDao(context);
        }
        else {
            sugiliteScriptDao = new SugiliteScriptFileDao(context, sugiliteData);
        }


        AlertDialog.Builder builder = new AlertDialog.Builder(context);
//        Spanned newDescription = ontologyDescriptionGenerator.getSpannedDescriptionForOperation(block.getOperation(), block.getOperation().getDataDescriptionQueryIfAvailable());
        builder.setTitle("Save Operation Confirmation");

        dialogView = layoutInflater.inflate(R.layout.dialog_confirmation_popup_spoken, null);
        confirmationPromptTextView = (TextView) dialogView.findViewById(R.id.text_confirmation_prompt);
        if(confirmationPromptTextView != null){
            //TODO: show the source code temporarily
            SpannableStringBuilder text = new SpannableStringBuilder();
            text.append("Are you sure you want to record the operation: ");
//            text.append(newDescription);
            confirmationPromptTextView.setText(text);


        }
//        speakButton = (ImageButton) dialogView.findViewById(R.id.button_verbal_instruction_talk);
//
//
//        if(speakButton != null){
//            speakButton.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    // speak button
//                    if (isListening() || (tts != null && tts.isSpeaking())) {
//                        stopASRandTTS();
//                    } else {
//                        initDialogManager();
//                    }
//                }
//            });
//            refreshSpeakButtonStyle(speakButton);
//        }

        builder.setView(dialogView);

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //save the block
                positiveButtonOnClick();
            }
        })
                .setNegativeButton("Skip", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        skipButtonOnClick();
                    }
                })
                .setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//                        editButtonOnClick();
                        dialog.dismiss();
                    }
                });

        dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                //stop ASR and TTS when the dialog is dismissed
                stopASRandTTS();
                onDestroy();
            }
        });

    }

    public void show() {
        dialog.getWindow().setType(OVERLAY_TYPE);
        dialog.show();
//        refreshSpeakButtonStyle(speakButton);

        //initiate the dialog manager when the dialog is shown
        initDialogManager();
    }

    private void positiveButtonOnClick() {
        dialog.dismiss();
        if (sharedPreferences.getBoolean("recording_in_process", false)) {
            try {
//                takeScreenShot(getActivity(context).getWindow().getDecorView().getRootView(),NewScriptDialog.getScript_name());
                sendNodeInfo(featurePack);
                writeTestScript(NewScriptDialog.getScript_name(),featurePack);
                blockBuildingHelper.saveBlock(block, featurePack);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        clickUnderlyingButtonRunnable.run();

        //
        if (block != null && block.getOperation() instanceof SugiliteLoadVariableOperation) {
            if (sugiliteData.currentPumiceValueDemonstrationType != null && sugiliteData.valueDemonstrationVariableName != null) {
                //value demonstration
                SharedPreferences.Editor prefEditor = sharedPreferences.edit();
                prefEditor.putBoolean("recording_in_process", false);
                prefEditor.apply();
                new Thread(new Runnable() {
                    @Override
                    public void run()
                    {
                        //commit the script
                        try {
                            sugiliteScriptDao.commitSave(new Runnable() {
                                @Override
                                public void run() {
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
                        catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }).start();




                //turn off the recording overlay if any
                if(sugiliteData.verbalInstructionIconManager != null){
                    sugiliteData.verbalInstructionIconManager.turnOffCatOverlay();
                }

                //reset the valueDemonstrationVariableName
                sugiliteData.valueDemonstrationVariableName = "";


                sugiliteData.setCurrentSystemState(SugiliteData.DEFAULT_STATE);
                PumiceDemonstrationUtil.showSugiliteToast("end recording", Toast.LENGTH_SHORT);

            }
        }
    }

    private void skipButtonOnClick() {
        dialog.cancel();
        clickUnderlyingButtonRunnable.run();
    }

    private void editButtonOnClick() {
        dialog.dismiss();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                RecordingAmbiguousPopupDialog recordingAmbiguousPopupDialog = new RecordingAmbiguousPopupDialog(context, queryScoreList, featurePack, blockBuildingHelper, clickUnderlyingButtonRunnable, uiSnapshot, actualClickedNode, sugiliteData, sharedPreferences, getTTS(), 0);
                recordingAmbiguousPopupDialog.show();
            }
        }, 500);

    }

//    @Override
//    public void speakingStartedCallback() {
//        super.speakingStartedCallback();
//        refreshSpeakButtonStyle(speakButton);
//    }
//
//    @Override
//    public void speakingEndedCallback() {
//        super.speakingEndedCallback();
//        refreshSpeakButtonStyle(speakButton);
//    }
//
//    @Override
//    public void listeningStartedCallback() {
//        super.listeningStartedCallback();
//        refreshSpeakButtonStyle(speakButton);
//    }
//
//    @Override
//    public void listeningEndedCallback() {
//        super.listeningEndedCallback();
//        refreshSpeakButtonStyle(speakButton);
//    }

    /**
     * initiate the dialog manager
     */
    @Override
    public void initDialogManager() {
        //set the prompt
//        Spanned newDescription = ontologyDescriptionGenerator.getSpannedDescriptionForOperation(block.getOperation(), block.getOperation().getDataDescriptionQueryIfAvailable());
//        askingForConfirmationState.setPrompt(context.getString(R.string.ask_if_record) + newDescription.toString());
       // askingForConfirmationState.setPrompt(R.string.ask_if_record + newDescription.toString());

        detailPromptState.setPrompt(context.getString(R.string.expand_ask_if_record));

        //link the states
        askingForConfirmationState.setNoASRResultState(detailPromptState);
        askingForConfirmationState.setUnmatchedState(detailPromptState);
        askingForConfirmationState.addNextStateUtteranceFilter(detailPromptState, SugiliteDialogUtteranceFilter.getConstantFilter(true));

        detailPromptState.setNoASRResultState(detailPromptState);
        detailPromptState.setUnmatchedState(detailPromptState);

        //set exit runnables
        askingForConfirmationState.addExitRunnableUtteranceFilter(SugiliteDialogUtteranceFilter.getSimpleContainingFilter("yes", "yeah"), new Runnable() {
            @Override
            public void run() {
                positiveButtonOnClick();
            }
        });

        detailPromptState.addExitRunnableUtteranceFilter(SugiliteDialogUtteranceFilter.getSimpleContainingFilter("confirm"), new Runnable() {
            @Override
            public void run() {
                positiveButtonOnClick();
            }
        });
        detailPromptState.addExitRunnableUtteranceFilter(SugiliteDialogUtteranceFilter.getSimpleContainingFilter("skip"), new Runnable() {
            @Override
            public void run() {
                skipButtonOnClick();
            }
        });
        detailPromptState.addExitRunnableUtteranceFilter(SugiliteDialogUtteranceFilter.getSimpleContainingFilter("cancel"), new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
            }
        });
        detailPromptState.addExitRunnableUtteranceFilter(SugiliteDialogUtteranceFilter.getSimpleContainingFilter("modify"), new Runnable() {
            @Override
            public void run() {
                editButtonOnClick();
            }
        });

        //set current sate
        setCurrentState(askingForConfirmationState);
        initPrompt();
    }

    private void sendNodeInfo(SugiliteAvailableFeaturePack sugiliteAvailableFeaturePack){
        //Get the websocket instance
        WebSocketClient webSocketClient=PumiceDemonstrationUtil.getWebSocketClientInst();
        JSONObject targetObject=new JSONObject();
        if(null != sugiliteAvailableFeaturePack){
            if(null != sugiliteAvailableFeaturePack.text){
                try {
                    targetObject.put("text",sugiliteAvailableFeaturePack.text);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if(null != sugiliteAvailableFeaturePack.contentDescription){
                try {
                    targetObject.put("content_desc",sugiliteAvailableFeaturePack.contentDescription);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if(null != sugiliteAvailableFeaturePack.className){
                try {
                    targetObject.put("class_name",sugiliteAvailableFeaturePack.className);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if(null != sugiliteAvailableFeaturePack.viewId){
                try {
                    targetObject.put("resource_id",sugiliteAvailableFeaturePack.viewId);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if(null != sugiliteAvailableFeaturePack.packageName){
                try {
                    targetObject.put("pkg_name",sugiliteAvailableFeaturePack.packageName);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            try {
                targetObject.put("xpath",sugiliteAvailableFeaturePack.xPath);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            JSONObject clickCommand = new JSONObject();
            try {
                clickCommand.put("action","click");
                clickCommand.put("target",targetObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            JSONObject sendCommandSM = new JSONObject();
            try {
                sendCommandSM.put("action","SENDCOMMAND");
                sendCommandSM.put("command",clickCommand);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            webSocketClient.send(String.valueOf(sendCommandSM));
        }
    }

    private void writeTestScript(String fileName,SugiliteAvailableFeaturePack sugiliteAvailableFeaturePack){
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(new File(context.getFilesDir().getPath()+"/scripts/" + fileName+".jsonl"),true));
            JSONObject targetObject=new JSONObject();
            if(null != sugiliteAvailableFeaturePack) {
                if (null != sugiliteAvailableFeaturePack.text) {
                    try {
                        targetObject.put("text", sugiliteAvailableFeaturePack.text);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if (null != sugiliteAvailableFeaturePack.contentDescription) {
                    try {
                        targetObject.put("content_desc", sugiliteAvailableFeaturePack.contentDescription);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if (null != sugiliteAvailableFeaturePack.className) {
                    try {
                        targetObject.put("class_name", sugiliteAvailableFeaturePack.className);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if (null != sugiliteAvailableFeaturePack.viewId) {
                    try {
                        targetObject.put("resource_id", sugiliteAvailableFeaturePack.viewId);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if (null != sugiliteAvailableFeaturePack.packageName) {
                    try {
                        targetObject.put("pkg_name", sugiliteAvailableFeaturePack.packageName);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    targetObject.put("xpath", sugiliteAvailableFeaturePack.xPath);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                JSONObject clickCommand = new JSONObject();
                try {
                    clickCommand.put("action", "click");
                    clickCommand.put("target", targetObject);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                bw.write(clickCommand.toString()+"\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

//    private File takeScreenShot(View view, String fileName) {
//        Date now = new Date();
//        CharSequence format=android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);
//        try{
//            Path dirPath= Paths.get(Environment.getExternalStorageDirectory().getAbsolutePath() + "/edu.cmu.hcii.sugilite/screenshots");
//            if (!Files.exists(dirPath)) {
//                File file1=dirPath.toFile();
//                file1.mkdir();
//            }
//
//            String path = dirPath + "/" + fileName + "-" + format + ".jpeg";
//
//            view.setDrawingCacheEnabled(true);
//            Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
//            view.setDrawingCacheEnabled(false);
//
//            File imageFile = new File(path);
//            FileOutputStream fileOutputStream = new FileOutputStream(imageFile);
//            int quality = 100;
//            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fileOutputStream);
//            fileOutputStream.flush();
//            fileOutputStream.close();
//            return imageFile;
//        }
//        catch (FileNotFoundException e){
//            e.printStackTrace();
//        }
//        catch (IOException e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    private Activity getActivity(Context context) {
//        if (context == null) return null;
//        if (context instanceof Activity) return (Activity) context;
//        if (context instanceof ContextWrapper) return getActivity(((ContextWrapper)context).getBaseContext());
//        return null;
//    }

}

package edu.cmu.hcii.sugilite.ui.dialog;

import static android.content.Context.ACTIVITY_SERVICE;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.LightingColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import edu.cmu.hcii.sugilite.R;
import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.automation.ServiceStatusManager;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptDao;
import edu.cmu.hcii.sugilite.pumice.PumiceDemonstrationUtil;
import edu.cmu.hcii.sugilite.recording.newrecording.dialog_management.SugiliteDialogManager;
import edu.cmu.hcii.sugilite.recording.newrecording.dialog_management.SugiliteDialogSimpleState;
import edu.cmu.hcii.sugilite.recording.newrecording.dialog_management.SugiliteDialogUtteranceFilter;
import edu.cmu.hcii.sugilite.recording.newrecording.fullscreen_overlay.OverlayClickedDialog;
import edu.cmu.hcii.sugilite.ui.SettingsActivity;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.VerbalInstructionIconManager;

import static edu.cmu.hcii.sugilite.Const.MUL_ZEROS;
import static edu.cmu.hcii.sugilite.Const.OVERLAY_TYPE;
import static edu.cmu.hcii.sugilite.Const.RECORDING_DARK_GRAY_COLOR;
import static edu.cmu.hcii.sugilite.Const.RECORDING_OFF_BUTTON_COLOR;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author toby
 * @date 8/3/16
 * @time 6:14 PM
 */

/**
 * Dialog used for creating a new script -> asking the user to give the script a name and set the system into the recording state
 */
public class NewScriptDialog extends SugiliteDialogManager implements AbstractSugiliteDialog {
    private Context context;
    private SugiliteScriptDao sugiliteScriptDao;
    private ServiceStatusManager serviceStatusManager;
    private SharedPreferences sharedPreferences;
    private SugiliteData sugiliteData;
    private AlertDialog dialog;
    private TextToSpeech tts;
    private SugiliteDialogSimpleState askingForScriptNameState = new SugiliteDialogSimpleState("ASKING_FOR_SCRIPT_NAME", this, true);
    private SugiliteDialogSimpleState askingForScriptNameConfirmationState = new SugiliteDialogSimpleState("ASKING_FOR_SCRIPT_NAME_CONFIRMATION", this, true);
    private VerbalInstructionIconManager verbalInstructionIconManager;
    private View dialogView;
    private static String script_name;

    private ImageButton mySpeakButton;
    private EditText scriptNameEditText;
    private Spinner appPackagesSpinner;
    private PackageManager packageManager;
//    private EditText ipAddressEditText;
    private static String serverAddress = null;
    private static String packageName = null;

    public NewScriptDialog(Context context, SugiliteScriptDao sugiliteScriptDao, ServiceStatusManager serviceStatusManager,
                           SharedPreferences sharedPreferences, SugiliteData sugiliteData, boolean isSystemAlert, final Dialog.OnClickListener positiveCallback, final Dialog.OnClickListener negativeCallback){
        super(context, sugiliteData, sugiliteData.getTTS());
        this.tts = sugiliteData.getTTS();
        this.context = context;
        this.sugiliteScriptDao = sugiliteScriptDao;
        this.serviceStatusManager = serviceStatusManager;
        this.sharedPreferences = sharedPreferences;
        this.sugiliteData = sugiliteData;
        this.verbalInstructionIconManager = sugiliteData.verbalInstructionIconManager;
        this.packageManager=context.getPackageManager();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        sugiliteData.clearInstructionQueue();
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        dialogView = layoutInflater.inflate(R.layout.dialog_new_script, null);
        scriptNameEditText = (EditText) dialogView.findViewById(R.id.edittext_instruction_content);
//        this.ipAddressEditText=(EditText) dialogView.findViewById(R.id.edittext_ip_address);
        scriptNameEditText.setText(sugiliteScriptDao.getNextAvailableDefaultName());
        appPackagesSpinner = (Spinner) dialogView.findViewById(R.id.spinner1);
        List<ApplicationInfo> applicationInfos = getInstalledPackageName();
        List<String> appNames=new ArrayList<>();
        for(ApplicationInfo applicationInfo : applicationInfos){
            appNames.add(packageManager.getApplicationLabel(applicationInfo).toString());
        }


        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, appNames);
        appPackagesSpinner.setAdapter(adapter);



        builder.setMessage("Specify the name for your new script and the app you want to record")
                .setView(dialogView)
                .setPositiveButton("Start Recording", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String scriptName = scriptNameEditText.getText().toString();
                        script_name=scriptName;
//                        context.startActivity(packageManager.getLaunchIntentForPackage(applicationInfos.get(0).packageName));  //Start the specified application
                        String appName=appPackagesSpinner.getSelectedItem().toString();
                        int index = -1;
                        for(ApplicationInfo applicationInfo : applicationInfos){
                            if(appName.equals(packageManager.getApplicationLabel(applicationInfo).toString())){
                                index += 1;
                                break;
                            }
                            index++;
                        }
                        packageName = applicationInfos.get(index).packageName;
                        serverAddress = PreferenceManager.getDefaultSharedPreferences(context).getString("remote_server_address", "ws://10.0.2.2:8765/");
//                        try {
//                            Runtime.getRuntime().exec("pm clear com.colpit.diamondcoming.isavemoney");
//                        } catch (IOException exception) {
//                            exception.printStackTrace();
//                        }
                        PumiceDemonstrationUtil.initiateDemonstration(context, serviceStatusManager, sharedPreferences, scriptName, sugiliteData, null, sugiliteScriptDao, verbalInstructionIconManager, packageName);

                        if(positiveCallback != null) {
                            positiveCallback.onClick(dialog, 0);
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(negativeCallback != null)
                            negativeCallback.onClick(dialog, 0);
                        dialog.dismiss();
                    }
                })
                .setTitle("New Script");

        dialog = builder.create();

        if(dialog.getWindow() != null) {
            if (isSystemAlert) {
                dialog.getWindow().setType(OVERLAY_TYPE);
            }
        }


        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if(tts != null) {
                    if (isListening() || tts.isSpeaking()) {
                        stopASRandTTS();
                    }
                }
                onDestroy();
            }
        });




        scriptNameEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId == EditorInfo.IME_ACTION_DONE)
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                return true;
            }
        });
    }
    public void show(){
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        initDialogManager();
        refreshSpeakButtonStyle(mySpeakButton);
    }

    @Override
    public void initDialogManager() {
        askingForScriptNameState.setPrompt("What's the name for the script?");
        askingForScriptNameState.setNoASRResultState(askingForScriptNameState);
        askingForScriptNameState.addNextStateUtteranceFilter(askingForScriptNameConfirmationState, SugiliteDialogUtteranceFilter.getConstantFilter(true));
        askingForScriptNameState.setOnInitiatedRunnable(new Runnable() {
            @Override
            public void run() {
                if(! scriptNameEditText.getText().toString().startsWith("Untitled")) {
                    scriptNameEditText.setText("");
                }
            }
        });
        //set on switched away runnable - the verbal instruction state should set the value for the text box
        askingForScriptNameState.setOnSwitchedAwayRunnable(new Runnable() {
            @Override
            public void run() {
                if (askingForScriptNameState.getASRResult() != null && (!askingForScriptNameState.getASRResult().isEmpty())) {
                    scriptNameEditText.setText(askingForScriptNameState.getASRResult().get(0));
                }
            }
        });


        askingForScriptNameConfirmationState.setPrompt("Is this script name correct?");
        askingForScriptNameConfirmationState.setNoASRResultState(askingForScriptNameState);
        askingForScriptNameConfirmationState.setUnmatchedState(askingForScriptNameState);
        askingForScriptNameConfirmationState.addNextStateUtteranceFilter(askingForScriptNameState, SugiliteDialogUtteranceFilter.getSimpleContainingFilter("no", "nah"));
        askingForScriptNameConfirmationState.addExitRunnableUtteranceFilter(SugiliteDialogUtteranceFilter.getSimpleContainingFilter("yes", "yeah"), new Runnable() {
            @Override
            public void run() {
                if(dialog != null || dialog.getButton(DialogInterface.BUTTON_POSITIVE) != null) {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                    speak("Please start demonstrating the task.", null);

                }
            }
        });

        //set current sate
        setCurrentState(askingForScriptNameState);
        initPrompt();
    }

    public static String getScript_name() {
        return script_name;
    }

    private List<ApplicationInfo> getInstalledPackageName(){
        List<ApplicationInfo> installedApplications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        // Remove system apps
        Iterator<ApplicationInfo> it = installedApplications.iterator();
        while (it.hasNext()) {
            ApplicationInfo appInfo = it.next();
            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                it.remove();
            }
        }


        // Return installed applications
        return installedApplications;
    }

    public static String getServerAddress(){
//        if (serverAddress == null || serverAddress.equals("")){
//            return "10.0.2.2";
//        }
        return serverAddress;
    }

    public static String getPackageName(){
        return packageName;
    }


}

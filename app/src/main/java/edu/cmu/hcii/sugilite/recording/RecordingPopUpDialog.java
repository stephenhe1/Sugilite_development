package edu.cmu.hcii.sugilite.recording;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import edu.cmu.hcii.sugilite.Const;
import edu.cmu.hcii.sugilite.R;
import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.communication.SugiliteBlockJSONProcessor;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptDao;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptFileDao;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptSQLDao;
import edu.cmu.hcii.sugilite.model.block.SugiliteBlockMetaInfo;
import edu.cmu.hcii.sugilite.model.block.util.SerializableNodeInfo;
import edu.cmu.hcii.sugilite.model.block.util.SugiliteAvailableFeaturePack;
import edu.cmu.hcii.sugilite.model.block.SugiliteBlock;
import edu.cmu.hcii.sugilite.model.block.SugiliteErrorHandlingForkBlock;
import edu.cmu.hcii.sugilite.model.block.SugiliteOperationBlock;
import edu.cmu.hcii.sugilite.model.block.SugiliteSpecialOperationBlock;
import edu.cmu.hcii.sugilite.model.block.SugiliteStartingBlock;
import edu.cmu.hcii.sugilite.model.block.util.UIElementMatchingFilter;
import edu.cmu.hcii.sugilite.model.operation.trinary.SugiliteLoadVariableOperation;
import edu.cmu.hcii.sugilite.model.operation.SugiliteOperation;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteReadoutOperation;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteSetTextOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteClickOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteLongClickOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteSelectOperation;
import edu.cmu.hcii.sugilite.model.variable.Variable;
import edu.cmu.hcii.sugilite.model.variable.VariableHelper;
import edu.cmu.hcii.sugilite.model.variable.VariableValue;
import edu.cmu.hcii.sugilite.ontology.*;
import edu.cmu.hcii.sugilite.pumice.PumiceDemonstrationUtil;
import edu.cmu.hcii.sugilite.ui.dialog.AbstractSugiliteDialog;
import edu.cmu.hcii.sugilite.ui.dialog.ChooseVariableDialog;

import static edu.cmu.hcii.sugilite.Const.OVERLAY_TYPE;
import static edu.cmu.hcii.sugilite.Const.SQL_SCRIPT_DAO;

/**
 * @author toby
 * @date 7/22/16
 * @time 12:18 PM
 */

@Deprecated
public class RecordingPopUpDialog implements AbstractSugiliteDialog {

    private int triggerMode;
    private SugiliteAvailableFeaturePack featurePack;
    private SharedPreferences sharedPreferences;
    private SugiliteScriptDao sugiliteScriptDao;
    private SugiliteBlockJSONProcessor jsonProcessor;
    private Set<Map.Entry<String, String>> allParentFeatures = new HashSet<>();
    private Set<Map.Entry<String, String>> allChildFeatures = new HashSet<>();
    private Set<Map.Entry<String, String>> allSiblingFeatures = new HashSet<>();
    private Set<Map.Entry<String, String>> selectedParentFeatures = new HashSet<>();
    private Set<Map.Entry<String, String>> selectedChildFeatures = new HashSet<>();
    private Set<Map.Entry<String, String>> selectedSiblingFeatures = new HashSet<>();
    private SugiliteData sugiliteData;
    private ReadableDescriptionGenerator readableDescriptionGenerator;
    private UIElementFeatureRecommender recommender;
    private Map<Map.Entry<String, String>, CheckBox> checkBoxChildEntryMap;
    private Map<Map.Entry<String, String>, CheckBox> checkBoxParentEntryMap;
    private Map<Map.Entry<String, String>, CheckBox> checkBoxSiblingEntryMap;
    private Map<String, CheckBox> identifierCheckboxMap;
    private Set<Map.Entry<String, String>> alternativeLabels;
    private SugiliteScreenshotManager screenshotManager;
    private DialogInterface.OnClickListener editCallback;
    private RecordingSkipManager skipManager;
    private AlternativeNodesFilterTester filterTester;
    private String childText = "";
    private String siblingText = "";
    private String scriptName;
    private Context context;
//    private SoviteAppNameAppInfoManager soviteAppNameAppInfoManager;
    protected static final String TAG = RecordingPopUpDialog.class.getSimpleName();


    private Spinner actionSpinner, targetTypeSpinner, withInAppSpinner, readoutParameterSpinner, loadVariableParameterSpinner;
    private CheckBox textCheckbox, contentDescriptionCheckbox, viewIdCheckbox, boundsInParentCheckbox, boundsInScreenCheckbox;
    private EditText setTextEditText, loadVariableVariableDefaultValue, loadVariableVariableName;
    private String textContent, contentDescriptionContent, viewIdContent;
    private LinearLayout actionParameterSection, actionSection, readoutParameterSection, loadVariableParameterSection;
    private View dialogRootView;
    private LayoutInflater layoutInflater;

    private SugiliteStartingBlock originalScript;
    private SugiliteOperationBlock blockToEdit;

    private AlertDialog dialog;
    private File screenshot = null;

    public static final int TRIGGERED_BY_NEW_EVENT = 1;
    public static final int TRIGGERED_BY_EDIT = 2;

    public RecordingPopUpDialog(final SugiliteData sugiliteData, Context context, SugiliteAvailableFeaturePack featurePack, SharedPreferences sharedPreferences, int triggerMode, Set<Map.Entry<String, String>> alternativeLabels){
        this.sharedPreferences = sharedPreferences;
        this.sugiliteData = sugiliteData;
        this.featurePack = featurePack;
        this.triggerMode = triggerMode;
        this.layoutInflater = LayoutInflater.from(context);
        this.context = context;
//        this.soviteAppNameAppInfoManager = SoviteAppNameAppInfoManager.getInstance(SugiliteData.getAppContext());
        if(Const.KEEP_ALL_ALTERNATIVES_IN_THE_FILTER) {
            this.alternativeLabels = new HashSet<>(alternativeLabels);
        }
        else {
            this.alternativeLabels = new HashSet<>();
        }
        this.screenshotManager = SugiliteScreenshotManager.getInstance(sharedPreferences, sugiliteData);
        this.skipManager = new RecordingSkipManager();
        this.filterTester = new AlternativeNodesFilterTester();
        this.scriptName = sugiliteData.getScriptHead().getScriptName();
        jsonProcessor = new SugiliteBlockJSONProcessor(context);
        if(Const.DAO_TO_USE == SQL_SCRIPT_DAO) {
            this.sugiliteScriptDao = new SugiliteScriptSQLDao(context);
        }
        else {
            this.sugiliteScriptDao = new SugiliteScriptFileDao(context, sugiliteData);
        }
        readableDescriptionGenerator = new ReadableDescriptionGenerator(context);
        checkBoxChildEntryMap = new HashMap<>();
        checkBoxParentEntryMap = new HashMap<>();
        checkBoxSiblingEntryMap = new HashMap<>();
        identifierCheckboxMap = new HashMap<>();
        dialogRootView = layoutInflater.inflate(R.layout.dialog_recording_pop_up, null);
        ContextThemeWrapper ctw = new ContextThemeWrapper(context, R.style.AlertDialogCustom);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialogRootView)
                .setTitle("Edit SUGILITE Operation");
        dialog = builder.create();
        dialog.getWindow().setType(OVERLAY_TYPE);
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_box);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                sugiliteData.hasRecordingPopupActive = false;
                if (!sugiliteData.recordingPopupDialogQueue.isEmpty())
                    sugiliteData.recordingPopupDialogQueue.poll().show();
            }
        });
        //fetch the data capsuled in the intent
        //TODO: refactor so the service passes in a feature pack instead
        setupSelections();


    }

    //THIS CONSTRUCTOR IS USED FOR EDITING ONLY!
    public RecordingPopUpDialog(final SugiliteData sugiliteData, Context context, SugiliteStartingBlock originalScript, SharedPreferences sharedPreferences, SugiliteOperationBlock blockToEdit, int triggerMode, DialogInterface.OnClickListener callback){
        this.sharedPreferences = sharedPreferences;
        this.sugiliteData = sugiliteData;
        this.featurePack = blockToEdit.getFeaturePack();
        this.triggerMode = triggerMode;
        this.layoutInflater = LayoutInflater.from(context);
        this.originalScript = originalScript;
        this.blockToEdit = blockToEdit;
        this.editCallback = callback;
        this.skipManager = new RecordingSkipManager();
        this.filterTester = new AlternativeNodesFilterTester();
        this.scriptName = originalScript.getScriptName();
        this.context = context;
        jsonProcessor = new SugiliteBlockJSONProcessor(context);
        if(blockToEdit.getElementMatchingFilter().alternativeLabels != null)
            this.alternativeLabels = new HashSet<>(blockToEdit.getElementMatchingFilter().alternativeLabels);
        else
            this.alternativeLabels = new HashSet<>();
        this.screenshotManager = SugiliteScreenshotManager.getInstance(sharedPreferences, sugiliteData);
        if(Const.DAO_TO_USE == SQL_SCRIPT_DAO)
            sugiliteScriptDao = new SugiliteScriptSQLDao(context);
        else
            sugiliteScriptDao = new SugiliteScriptFileDao(context, sugiliteData);
        readableDescriptionGenerator = new ReadableDescriptionGenerator(context);
        checkBoxChildEntryMap = new HashMap<>();
        checkBoxParentEntryMap = new HashMap<>();
        checkBoxSiblingEntryMap = new HashMap<>();
        identifierCheckboxMap = new HashMap<>();
        dialogRootView = layoutInflater.inflate(R.layout.dialog_recording_pop_up, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialogRootView)
                .setTitle("Edit Sugilite Operation");
        dialog = builder.create();
        dialog.getWindow().setType(OVERLAY_TYPE);
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_box);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                sugiliteData.hasRecordingPopupActive = false;
                if (!sugiliteData.recordingPopupDialogQueue.isEmpty()) {
                    sugiliteData.hasRecordingPopupActive = true;
                    sugiliteData.recordingPopupDialogQueue.poll().show();
                }
            }
        });
        //fetch the data capsuled in the intent
        //TODO: refactor so the service passes in a feature pack instead
        setupSelections();

        //check skip status, click on the ok button if skip manager returns true
        //TODO: restore the selections based on blockToEdit
    }

    public void show(boolean doNotSkip){
        //show() needs to be on the UI Thread.
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "FLAG-SHOW_START-1");
                dialog.show();
                dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                Log.i(TAG, "FLAG-SHOW_DONE-2");
            }
        };

        Log.i(TAG, "FLAG-SHOW1");
        //to skip
        if(skipManager.checkSkip(featurePack, triggerMode, generateFilter(), featurePack.alternativeNodes).contentEquals("skip") && (!doNotSkip)) {
            Log.i(TAG, "FLAG-SHOW2");
            OKButtonOnClick(null);
        }
        //to show the disambiguation panel

        else if (skipManager.checkSkip(featurePack, triggerMode, generateFilter(), featurePack.alternativeNodes).contentEquals("disambiguation")){
            hideUnrelevantInfo(true, "Sugilite finds multiple possible features for the object you've just opearted on and can't determine the best feature to use.\n\nCan you choose the best feature to use for identifying this object in future executions of this script?");
            SugiliteData.runOnUiThread(runnable);

        }
        else if (skipManager.checkSkip(featurePack, triggerMode, generateFilter(), featurePack.alternativeNodes).contentEquals("multipleMatch")){
            hideUnrelevantInfo(false, "Sugilte's automatically generated feature set can match more than one objects on the current screen.\n\nCan you choose the best set of features to use for identifying this object in future executions of this script?");
            Log.i(TAG, "FLAG-SHOW2");
            SugiliteData.runOnUiThread(runnable);

        }
        //to show the full popup
        else {
            //context can be either from an activity (editing) or the accessibility service
            Log.i(TAG, "FLAG-SHOW2");
            SugiliteData.runOnUiThread(runnable);

        }
    }

    /**
     * this function is used to keep compability with AbstractSugiliteDialog class
     */
    public void show(){
        show(false);
    }

    /**
     * hide unrelevant info in the popup and only show relevant info
     */
    public void hideUnrelevantInfo(boolean hideLocation, String prompt){
        //TODO: add some sort of user friendly prompt
        //hide action section
        dialog.setTitle("Sugilite Disambiguation Panel");
        dialogRootView.findViewById(R.id.action_section).setVisibility(View.GONE);
        dialogRootView.findViewById(R.id.within_app_section).setVisibility(View.GONE);
        dialogRootView.findViewById(R.id.target_type_section).setVisibility(View.GONE);
        dialogRootView.findViewById(R.id.text_ambiguation_prompt).setVisibility(View.VISIBLE);
        ((TextView)dialogRootView.findViewById(R.id.text_ambiguation_prompt)).setText(prompt);

        //TODO: hide unlikely chosen features (e.g. location/view ID when text label is available)
        Set<String> availableLabel = new HashSet<>();
        if(featurePack.text != null && (!featurePack.text.contentEquals("NULL")))
            availableLabel.add(featurePack.text);
        if(featurePack.contentDescription != null && (!featurePack.contentDescription.contentEquals("NULL")))
            availableLabel.add(featurePack.contentDescription);
        for(SerializableNodeInfo node : featurePack.childNodes){
            if(node.text != null)
                availableLabel.add(node.text);
            if(node.contentDescription != null)
                availableLabel.add(node.contentDescription);
        }

        for(SerializableNodeInfo node : featurePack.siblingNodes){
            if(node.text != null)
                availableLabel.add(node.text);
            if(node.contentDescription != null)
                availableLabel.add(node.contentDescription);
        }

        if(availableLabel.size() > 1 && hideLocation){
            //have 1+ text labels, hide other features
            if(boundsInParentCheckbox != null){
                boundsInParentCheckbox.setVisibility(View.GONE);
            }
            if(boundsInScreenCheckbox != null){
                boundsInScreenCheckbox.setVisibility(View.GONE);
            }
        }

        //change the button to allow the user to view more options (see the full popup)
        final Button editButton = (Button)dialogRootView.findViewById(R.id.recordingOffButton);
        editButton.setText("More..");
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //unhide hidden info - undo "hideUnrelevantInfo"
                dialog.setTitle("Sugilite Advanced Recording Panel");
                dialogRootView.findViewById(R.id.target_type_section).setVisibility(View.VISIBLE);
                dialogRootView.findViewById(R.id.action_section).setVisibility(View.VISIBLE);
                dialogRootView.findViewById(R.id.within_app_section).setVisibility(View.VISIBLE);
                dialogRootView.findViewById(R.id.text_ambiguation_prompt).setVisibility(View.GONE);
                if (boundsInParentCheckbox != null) {
                    boundsInParentCheckbox.setVisibility(View.VISIBLE);
                }
                if (boundsInScreenCheckbox != null) {
                    boundsInScreenCheckbox.setVisibility(View.VISIBLE);
                }
                editButton.setText("Recording Off");
                editButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        turnOffRecording(v);
                    }
                });
                refreshAfterChange();
            }
        });
        refreshAfterChange();
    }

    public void finishActivity(View view){
        dialog.dismiss();
    }
    public void turnOffRecording(View view) {
        PumiceDemonstrationUtil.endRecording(context, sugiliteData, sharedPreferences, sugiliteScriptDao);
        dialog.dismiss();
    }

    public void OKButtonOnClick(final View view){
        //check if all fields are filled
        String actionSpinnerSelectedItem = actionSpinner.getSelectedItem().toString();
        if (actionSpinnerSelectedItem.contentEquals("Load as Variable")){
            if(loadVariableVariableName.getText().length() < 1){
                //variable name not filled, popup window
                Runnable popupRunnable = new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(dialog.getContext());
                        builder.setTitle("Variable Name not Set").setMessage("Please set the name of the varilable").setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
                    }
                };
                SugiliteData.runOnUiThread(popupRunnable);
                return;
            }
        }
        //TODO: add popup if current selection can't get unique button


        //add head if no one is present && this popup is triggered by new event
        if(triggerMode == TRIGGERED_BY_NEW_EVENT &&
                (sugiliteData.getScriptHead() == null ||
                        (!(sugiliteData.getScriptHead()).getScriptName().contentEquals(PumiceDemonstrationUtil.addScriptExtension(sharedPreferences.getString("scriptName", "defaultScript")))))){
            sugiliteData.setScriptHead(new SugiliteStartingBlock(PumiceDemonstrationUtil.addScriptExtension(sharedPreferences.getString("scriptName", "defaultScript"))));
            sugiliteData.setCurrentScriptBlock(sugiliteData.getScriptHead());
        }

        final SugiliteOperationBlock operationBlock = generateBlock();

        AlertDialog.Builder builder = new AlertDialog.Builder(dialog.getContext());
        //disable the confirmation dialog
        DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //take screenshot
                File screenshot;
                if (sharedPreferences.getBoolean("root_enabled", false)) {
                    try {
                        System.out.println("taking screen shot");
                        //--not taking screenshot
                        //screenshot = screenshotManager.take(false, SugiliteScreenshotManager.DIRECTORY_PATH, SugiliteScreenshotManager.getScreenshotFileNameWithDate());
                        //operationBlock.setScreenshot(screenshot);

                    } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("[ERROR] Error in taking screenshot, is root access granted?");
                    }
                }
                saveBlock(operationBlock, dialogRootView.getContext());
                /*
                //fill in the text box if the operation is of SET_TEXT type
                if (operationBlock.getOperation().getOperationType() == SugiliteOperation.SET_TEXT && triggerMode == TRIGGERED_BY_NEW_EVENT) {
                    //should NOT change the current state in SugiliteData here because this is only one step added to specifically handle text entry recording
                    sugiliteData.addInstruction(operationBlock);
                }
                */
                if (editCallback != null) {
                    System.out.println("calling callback");
                    editCallback.onClick(null, 0);
                }
            }
        };
        if(view == null) {
            //the main panel is skipped
            builder.setTitle("Save Operation Confirmation").setMessage((Spanned) TextUtils.concat("Are you sure you want to record the operation: ", readableDescriptionGenerator.generateReadableDescription(operationBlock), "?"));
            builder.setPositiveButton("Yes", onClickListener)
                    .setNegativeButton("Skip", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    })
                    .setNeutralButton("Edit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            show(true);
                        }
                    });
            final AlertDialog dialog = builder.create();
            dialog.getWindow().setType(OVERLAY_TYPE);
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    sugiliteData.hasRecordingPopupActive = false;
                    if (!sugiliteData.recordingPopupDialogQueue.isEmpty()) {
                        sugiliteData.hasRecordingPopupActive = true;
                        sugiliteData.recordingPopupDialogQueue.poll().show();
                    }
                }
            });
            Runnable showDialogRunnable = new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "FLAG-SHOW_START");
                    dialog.show();
                    Log.i(TAG, "FLAG-SHOW_DONE");
                }
            };
            SugiliteData.runOnUiThread(showDialogRunnable);
        }

        else {
            onClickListener.onClick(null, 0);
        }
    }




    public void setAsAParameterOnClick(View view){
        EditText actionParameter = (EditText)dialogRootView.findViewById(R.id.action_parameter_set_text);
        setAsAParameterOnClick(view, actionParameter, "", "");
    }


    public void setAsAParameterOnClick(final View view, final TextView actionParameter, final String label, final String defaultDefaultValue){
        PumiceDemonstrationUtil.showSugiliteToast("set as a parameter", Toast.LENGTH_SHORT);

        ChooseVariableDialog chooseVariableDialog = null;
        if(actionParameter != null) {
            switch (triggerMode) {
                case TRIGGERED_BY_NEW_EVENT:
                    chooseVariableDialog = new ChooseVariableDialog(view.getContext(), actionParameter, layoutInflater, sugiliteData, sugiliteData.getScriptHead(), label, defaultDefaultValue);
                    break;
                case TRIGGERED_BY_EDIT:
                    chooseVariableDialog = new ChooseVariableDialog(view.getContext(), actionParameter, layoutInflater, sugiliteData, originalScript, label, defaultDefaultValue);
                    break;
            }
        }
        final ChooseVariableDialog finalChooseVariableDialog = chooseVariableDialog;
        Runnable showChooseVariableDialogRunnable = new Runnable() {
            @Override
            public void run() {
                finalChooseVariableDialog.show();
            }
        };
        SugiliteData.runOnUiThread(showChooseVariableDialogRunnable);
    }

    public void seeAlternativeLabelLinkOnClick(View view) {
        if(alternativeLabels == null)
            return;
        List<String> labelList = new ArrayList<>();
        for(Map.Entry<String, String> entry : alternativeLabels){
            labelList.add(entry.getKey() + ": " + entry.getValue());
        }
        ListView listView = new ListView(view.getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(dialog.getContext(), android.R.layout.simple_list_item_single_choice, labelList);
        listView.setAdapter(adapter);
        final AlertDialog.Builder builder = new AlertDialog.Builder(dialog.getContext());
        builder.setTitle("Alternative Labels")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setView(listView);
        Runnable showDialogRunnable = new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = builder.create();
                dialog.getWindow().setType(OVERLAY_TYPE);
                dialog.show();
            }
        };
        SugiliteData.runOnUiThread(showDialogRunnable);
    }

    private void setupSelections(){

        //set up on click listeners
        ((TextView)dialogRootView.findViewById(R.id.parameterLink)).setText(Html.fromHtml("<p><u>Set as a parameter</u></p>"));
        dialogRootView.findViewById(R.id.okButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OKButtonOnClick(v);
            }
        });
        dialogRootView.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
              finishActivity(v);
          }
      });
        dialogRootView.findViewById(R.id.recordingOffButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                turnOffRecording(v);
            }
        });
        dialogRootView.findViewById(R.id.parameterLink).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAsAParameterOnClick(v);
            }
        });
        dialogRootView.findViewById(R.id.see_alternative_link).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seeAlternativeLabelLinkOnClick(v);
            }
        });
        //populate parent features
        if(featurePack.parentNode != null){
            if(featurePack.parentNode.text != null)
                allParentFeatures.add(new AbstractMap.SimpleEntry<>("Parent Text", featurePack.parentNode.text.toString()));
            if(featurePack.parentNode.contentDescription != null)
                allParentFeatures.add(new AbstractMap.SimpleEntry<>("Parent ContentDescription", featurePack.parentNode.contentDescription.toString()));
            if(featurePack.parentNode.viewId != null)
                allParentFeatures.add(new AbstractMap.SimpleEntry<>("Parent Object ID", featurePack.parentNode.viewId));
        }



        //populate child features
        for(SerializableNodeInfo childNode : featurePack.childNodes){
            if(childNode != null){
                if(childNode.text != null)
                    allChildFeatures.add(new AbstractMap.SimpleEntry<>("Child Text", childNode.text.toString()));
                if(childNode.contentDescription != null)
                    allChildFeatures.add(new AbstractMap.SimpleEntry<>("Child ContentDescription", childNode.contentDescription.toString()));
                if(childNode.viewId != null)
                    allChildFeatures.add(new AbstractMap.SimpleEntry<>("Child Object ID", childNode.viewId));
            }
        }

        // populate sibling features
        for(SerializableNodeInfo sibNode: featurePack.siblingNodes) {
            if(sibNode != null){
                if(sibNode.text != null)
                    allSiblingFeatures.add(new AbstractMap.SimpleEntry<>("Sibling Text", sibNode.text.toString()));
                if(sibNode.contentDescription != null)
                    allSiblingFeatures.add(new AbstractMap.SimpleEntry<>("Sibling ContentDescription", sibNode.contentDescription.toString()));
                if(sibNode.viewId != null)
                    allSiblingFeatures.add(new AbstractMap.SimpleEntry<>("Sibling Object ID", sibNode.viewId));
            }
        }

        recommender = new UIElementFeatureRecommender(featurePack.packageName, featurePack.className, featurePack.text, featurePack.contentDescription, featurePack.viewId, featurePack.boundsInParent, featurePack.boundsInScreen, scriptName, featurePack.isEditable, featurePack.time, featurePack.eventType, allParentFeatures, allChildFeatures, allSiblingFeatures);

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(featurePack.time);
        SimpleDateFormat dateFormat;
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("US/Eastern"));
        boolean autoFillEnabled = true;
        LinearLayout identifierLayout = (LinearLayout) dialogRootView.findViewById(R.id.identifier_layout);


        Set<String> existingFeatureValues = new HashSet<>();


        withInAppSpinner = (Spinner)dialogRootView.findViewById(R.id.within_app_dropdown);
        actionSpinner = (Spinner)dialogRootView.findViewById(R.id.action_dropdown);
        readoutParameterSpinner = (Spinner)dialogRootView.findViewById(R.id.text_to_read_out_spinner);
        setTextEditText = (EditText)dialogRootView.findViewById(R.id.action_parameter_set_text);
        loadVariableVariableDefaultValue = (EditText)dialogRootView.findViewById(R.id.load_variable_default_value);
        loadVariableVariableName = (EditText)dialogRootView.findViewById(R.id.load_variable_variable_name);
        targetTypeSpinner = (Spinner)dialogRootView.findViewById(R.id.target_type_dropdown);

        actionParameterSection = (LinearLayout)dialogRootView.findViewById(R.id.action_parameter_section);
        readoutParameterSection = (LinearLayout)dialogRootView.findViewById(R.id.read_out_parameter_section);
        loadVariableParameterSection = (LinearLayout)dialogRootView.findViewById(R.id.load_variable_parameter_section);
        actionSection = (LinearLayout)dialogRootView.findViewById(R.id.action_section);

        //setup identifier
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(20, 0, 0 ,0);

        UIElementMatchingFilter existingFilter = null;
        if(triggerMode == TRIGGERED_BY_EDIT)
            existingFilter = blockToEdit.getElementMatchingFilter();

        if((!featurePack.text.contentEquals("NULL")) && featurePack.text.length() > 0) {
            textCheckbox = new CheckBox(dialogRootView.getContext());
            textCheckbox.setText(Html.fromHtml(boldify("Text Label: ") + featurePack.text));
            existingFeatureValues.add(featurePack.text);
            textContent = new String(featurePack.text);
            if(autoFillEnabled && triggerMode == TRIGGERED_BY_NEW_EVENT)
                //fill the box by recommender
                textCheckbox.setChecked(recommender.chooseText());
            if(triggerMode == TRIGGERED_BY_EDIT){
                if(existingFilter.getText() != null) {
                    textCheckbox.setChecked(true);
                    if(VariableHelper.isAVariable(existingFilter.getText())) {
                        VariableValue<String> defaultVariable = originalScript.variableNameDefaultValueMap.get(VariableHelper.getVariableName(existingFilter.getText()));
                        String defaultVariableValue = null;
                        if(defaultVariable != null && defaultVariable.getVariableValue() instanceof String) {
                            defaultVariableValue = defaultVariable.getVariableValue();
                        }
                        textCheckbox.setText(Html.fromHtml(boldify("Text Label: ") + existingFilter.getText() + (defaultVariableValue != null ? ": (" + defaultVariableValue + ")" : "")));
                    }
                }
            }
            identifierLayout.addView(generateRow(textCheckbox, "Text Label", featurePack.text), layoutParams);
            identifierCheckboxMap.put("Text", textCheckbox);

        }


        if((!featurePack.contentDescription.contentEquals("NULL")) && featurePack.contentDescription.length() > 0) {
            contentDescriptionCheckbox = new CheckBox(dialogRootView.getContext());
            contentDescriptionCheckbox.setText(Html.fromHtml(boldify("ContentDescription: ") + featurePack.contentDescription));
            existingFeatureValues.add(featurePack.contentDescription);
            contentDescriptionContent = new String(featurePack.contentDescription);
            if(autoFillEnabled && triggerMode == TRIGGERED_BY_NEW_EVENT)
                //fill the box by recommender
                contentDescriptionCheckbox.setChecked(recommender.chooseContentDescription());
            if(triggerMode == TRIGGERED_BY_EDIT){
                if(existingFilter.getContentDescription() != null) {
                    contentDescriptionCheckbox.setChecked(true);
                    if(VariableHelper.isAVariable(existingFilter.getContentDescription())) {
                        VariableValue<String> defaultVariable = originalScript.variableNameDefaultValueMap.get(VariableHelper.getVariableName(existingFilter.getContentDescription()));
                        String defaultVariableValue = null;
                        if(defaultVariable != null && defaultVariable.getVariableValue() instanceof String) {
                            defaultVariableValue = defaultVariable.getVariableValue();
                        }
                        textCheckbox.setText(Html.fromHtml(boldify("ContentDescription: ") + existingFilter.getContentDescription() + (defaultVariableValue != null ? ": (" + defaultVariableValue + ")" : "")));
                    }
                }
            }
            identifierLayout.addView(generateRow(contentDescriptionCheckbox, "ContentDescription", featurePack.contentDescription), layoutParams);
            identifierCheckboxMap.put("ContentDescription", contentDescriptionCheckbox);

        }

        if(!featurePack.viewId.contentEquals("NULL")) {
            viewIdCheckbox = new CheckBox(dialogRootView.getContext());
            viewIdCheckbox.setText(Html.fromHtml(boldify("Object ID: ") + featurePack.viewId));
            existingFeatureValues.add(featurePack.viewId);
            viewIdContent = new String(featurePack.viewId);
            if(autoFillEnabled && triggerMode == TRIGGERED_BY_NEW_EVENT)
                //fill the box by recommender
                viewIdCheckbox.setChecked(recommender.chooseViewId());
            if(triggerMode == TRIGGERED_BY_EDIT){
                if(existingFilter.getViewId() != null) {
                    viewIdCheckbox.setChecked(true);
                    if(VariableHelper.isAVariable(existingFilter.getViewId())) {
                        VariableValue<String> defaultVariable = originalScript.variableNameDefaultValueMap.get(VariableHelper.getVariableName(existingFilter.getViewId()));
                        String defaultVariableValue = null;
                        if(defaultVariable != null && defaultVariable.getVariableValue() instanceof String) {
                            defaultVariableValue = defaultVariable.getVariableValue();
                        }
                        textCheckbox.setText(Html.fromHtml(boldify("Object ID: ") + existingFilter.getViewId() + (defaultVariableValue != null ? ": (" + defaultVariableValue + ")" : "")));
                    }
                }
            }
            identifierLayout.addView(generateRow(viewIdCheckbox, "Object ID", featurePack.viewId), layoutParams);
            identifierCheckboxMap.put("ViewId", viewIdCheckbox);
        }

        if(autoFillEnabled && triggerMode == TRIGGERED_BY_NEW_EVENT){
            selectedChildFeatures.addAll(recommender.chooseChildFeatures());
            selectedParentFeatures.addAll(recommender.chooseParentFeatures());
            selectedSiblingFeatures.addAll(recommender.chooseSiblingFeatures());
        }
        else if (triggerMode == TRIGGERED_BY_EDIT){
            UIElementMatchingFilter parentFilter = existingFilter.getParentFilter();
            Set<UIElementMatchingFilter> childFilter = existingFilter.getChildFilter();
            Set<UIElementMatchingFilter> siblingFilter = existingFilter.getSiblingFilter();
            if(parentFilter != null) {
                if (parentFilter.getText() != null)
                    selectedParentFeatures.add(new AbstractMap.SimpleEntry<String, String>("Text", parentFilter.getText()));
                if (parentFilter.getContentDescription() != null)
                    selectedParentFeatures.add(new AbstractMap.SimpleEntry<String, String>("ContentDescription", parentFilter.getContentDescription()));
                if (parentFilter.getViewId() != null)
                    selectedParentFeatures.add(new AbstractMap.SimpleEntry<String, String>("ViewID", parentFilter.getViewId()));
            }

            if(childFilter != null && childFilter.size() != 0) {
                for(UIElementMatchingFilter cf : childFilter) {
                    if (cf.getText() != null)
                        selectedChildFeatures.add(new AbstractMap.SimpleEntry<String, String>("Text", cf.getText()));
                    if (cf.getContentDescription() != null)
                        selectedChildFeatures.add(new AbstractMap.SimpleEntry<String, String>("ContentDescription", cf.getContentDescription()));
                    if (cf.getViewId() != null)
                        selectedChildFeatures.add(new AbstractMap.SimpleEntry<String, String>("ViewID", cf.getViewId()));
                }
            }

            if(siblingFilter != null && siblingFilter.size() != 0) {
                for (UIElementMatchingFilter sf : siblingFilter) {
                    if (sf.getText() != null)
                        selectedSiblingFeatures.add(new AbstractMap.SimpleEntry<String, String>("Text", sf.getText()));
                    if (sf.getContentDescription() != null)
                        selectedSiblingFeatures.add(new AbstractMap.SimpleEntry<String, String>("ContentDescription", sf.getContentDescription()));
                    if (sf.getViewId() != null)
                        selectedSiblingFeatures.add(new AbstractMap.SimpleEntry<String, String>("ViewID", sf.getViewId()));
                }
            }
        }

        //NOTE: temporarily remove parent features

        /*
        for(Map.Entry<String, String> feature : allParentFeatures){
            if(feature.getKey() != null && feature.getValue() != null){
                CheckBox parentCheckBox = new CheckBox(this);
                parentCheckBox.setText(Html.fromHtml(boldify("Parent " + feature.getKey() + ": ") + feature.getValue()));
                if(!existingFeatureValues.contains(feature.getValue())) {
                    if (selectedParentFeatures.contains(feature))
                        parentCheckBox.setChecked(true);
                    existingFeatureValues.add(feature.getValue());
                    identifierLayout.addView(parentCheckBox, layoutParams);
                    checkBoxParentEntryMap.put(feature, parentCheckBox);
                    parentCheckBox.setOnCheckedChangeListener(identiferCheckboxChangeListener);
                }
                else
                    continue;
            }
        }
        */

        boolean hasChildText = false;
        for(Map.Entry<String, String> feature : allChildFeatures){
            if(feature.getKey() != null && feature.getValue() != null && feature.getValue().length() > 0){
                CheckBox childCheckBox = new CheckBox(dialogRootView.getContext());
                childCheckBox.setText(Html.fromHtml(boldify("" + feature.getKey() + ": ") + feature.getValue()));
                if(feature.getKey().contains("Text")) {
                    hasChildText = true;
                    childText += feature.getValue();
                    childText += " ";
                }
                if(!existingFeatureValues.contains(feature.getValue())) {
                    if (selectedChildFeatures.contains(feature))
                        childCheckBox.setChecked(true);

                    if(triggerMode == TRIGGERED_BY_EDIT){
                        //handle editing child feature with parameters
                        for(Map.Entry<String, String> selectedFeature: selectedChildFeatures){
                            if(VariableHelper.isAVariable(selectedFeature.getValue())) {
                                VariableValue<String> defaultVariable = originalScript.variableNameDefaultValueMap.get(VariableHelper.getVariableName(selectedFeature.getValue()));
                                if(defaultVariable != null && defaultVariable.getVariableValue() instanceof String){
                                    AbstractMap.SimpleEntry<String, String> parsedFeature = new AbstractMap.SimpleEntry<String, String>(selectedFeature.getKey(), defaultVariable.getVariableValue());
                                    if (parsedFeature.equals(feature)){
                                        childCheckBox.setChecked(true);
                                        childCheckBox.setText(Html.fromHtml(boldify("" + feature.getKey() + ": ") + selectedFeature.getValue() + ": (" + defaultVariable.getVariableValue() + ")"));
                                    }
                                }
                            }
                        }
                    }

                    existingFeatureValues.add(feature.getValue());
                    identifierLayout.addView(generateRow(childCheckBox, "" + feature.getKey(), feature.getValue()), layoutParams);
                    checkBoxChildEntryMap.put(feature, childCheckBox);
                }
                else
                    continue;
            }
        }

        boolean hasSiblingText = false;
        for(Map.Entry<String, String> feature : allSiblingFeatures){
            if(feature.getKey() != null && feature.getValue() != null && feature.getValue().length() > 0){
                CheckBox siblingCheckBox = new CheckBox(dialogRootView.getContext());
                siblingCheckBox.setText(Html.fromHtml(boldify("" + feature.getKey() + ": ") + feature.getValue()));
                if(feature.getKey().contains("Text")) {
                    hasSiblingText = true;
                    siblingText += feature.getValue();
                    siblingText += " ";
                }
                if(!existingFeatureValues.contains(feature.getValue())) {
                    if (selectedSiblingFeatures.contains(feature))
                        siblingCheckBox.setChecked(true);

                    if(triggerMode == TRIGGERED_BY_EDIT){
                        //handle editing sibling feature with parameters
                        for(Map.Entry<String, String> selectedFeature: selectedSiblingFeatures){
                            if(VariableHelper.isAVariable(selectedFeature.getValue())) {
                                VariableValue<String> defaultVariable = originalScript.variableNameDefaultValueMap.get(VariableHelper.getVariableName(selectedFeature.getValue()));
                                if(defaultVariable != null && defaultVariable.getVariableValue() instanceof String){
                                    AbstractMap.SimpleEntry<String, String> parsedFeature = new AbstractMap.SimpleEntry<String, String>(selectedFeature.getKey(), defaultVariable.getVariableValue());
                                    if (parsedFeature.equals(feature)){
                                        siblingCheckBox.setChecked(true);
                                        siblingCheckBox.setText(Html.fromHtml(boldify("" + feature.getKey() + ": ") + selectedFeature.getValue() + ": (" + defaultVariable.getVariableValue() + ")"));
                                    }
                                }
                            }
                        }
                    }

                    existingFeatureValues.add(feature.getValue());
                    identifierLayout.addView(generateRow(siblingCheckBox, "" + feature.getKey(), feature.getValue()), layoutParams);
                    checkBoxSiblingEntryMap.put(feature, siblingCheckBox);
                }
                else
                    continue;
            }
        }

        boundsInParentCheckbox = new CheckBox(dialogRootView.getContext());
        boundsInParentCheckbox.setText(Html.fromHtml(boldify("Parent Location: ") + featurePack.boundsInParent));
        if(autoFillEnabled && triggerMode == TRIGGERED_BY_NEW_EVENT)
            boundsInParentCheckbox.setChecked(recommender.chooseBoundsInParent());
        if(triggerMode == TRIGGERED_BY_EDIT) {
            if (existingFilter.getBoundsInParent() != null)
                boundsInParentCheckbox.setChecked(true);
        }
        identifierLayout.addView(boundsInParentCheckbox, layoutParams);
        identifierCheckboxMap.put("boundsInParent", boundsInParentCheckbox);


        boundsInScreenCheckbox = new CheckBox(dialogRootView.getContext());
        boundsInScreenCheckbox.setText(Html.fromHtml(boldify("Screen Location: ") + featurePack.boundsInScreen));
        if(autoFillEnabled && triggerMode == TRIGGERED_BY_NEW_EVENT)
            boundsInScreenCheckbox.setChecked(recommender.chooseBoundsInScreen());
        if(triggerMode == TRIGGERED_BY_EDIT){
            if(existingFilter.getBoundsInScreen() != null)
                boundsInScreenCheckbox.setChecked(true);
        }
        identifierLayout.addView(boundsInScreenCheckbox, layoutParams);
        identifierCheckboxMap.put("boundsInScreen", boundsInScreenCheckbox);



        //set up action
        List<String> actionSpinnerItems = new ArrayList<>();
        Map<Integer, Integer> actionOrderMap = new HashMap<>();
        int actionSpinnerItemCount = 0;
        if(featurePack.isEditable) {
            actionSpinnerItems.add("Set Text");
            actionOrderMap.put(SugiliteOperation.SET_TEXT, actionSpinnerItemCount ++);
        }
        if(featurePack.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            actionSpinnerItems.add("Select");
            actionOrderMap.put(SugiliteOperation.SELECT, actionSpinnerItemCount++);
        }
        actionSpinnerItems.add("Click");
        actionOrderMap.put(SugiliteOperation.CLICK, actionSpinnerItemCount++);
        if(featurePack.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            actionSpinnerItems.add("Long Click");
            actionOrderMap.put(SugiliteOperation.LONG_CLICK, actionSpinnerItemCount++);
        }

        //=== temporarily disable "read out" and "load as variable" options ===

        /*
        if((featurePack.text != null && (! featurePack.text.contentEquals("NULL"))) || hasChildText || hasSiblingText || (featurePack.contentDescription != null && (! featurePack.contentDescription.contentEquals("NULL")))) {
            actionSpinnerItems.add("Read Out");
            actionOrderMap.put(SugiliteOperation.READ_OUT, actionSpinnerItemCount++);
            actionSpinnerItems.add("Load as Variable");
            actionOrderMap.put(SugiliteOperation.LOAD_AS_VARIABLE, actionSpinnerItemCount++);
        }
        */

        ArrayAdapter<String> actionAdapter = new ArrayAdapter<String>(dialogRootView.getContext(), android.R.layout.simple_spinner_item, actionSpinnerItems);
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionSpinner.setAdapter(actionAdapter);
        if(triggerMode == TRIGGERED_BY_NEW_EVENT) {
            if (featurePack.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                actionSpinner.setSelection(actionSpinnerItems.indexOf("Long Click"));
            }
            else
                actionSpinner.setSelection(0);
        }
        else if (triggerMode == TRIGGERED_BY_EDIT){
            SugiliteOperation oldOperation = blockToEdit.getOperation();
            try {
                actionSpinner.setSelection(actionOrderMap.get(oldOperation.getOperationType()));
            }
            catch (Exception e){
                e.printStackTrace();
                actionSpinner.setSelection(0);
            }
        }

        //set up read out parameter spinner
        Map<String, Integer> readOutSpinnerOrderMap = new HashMap<>();
        int readOutSpinnerActionCount = 0;

        List<String> readoutParameterItems  = new ArrayList<>();
        if(featurePack.text != null && (!featurePack.text.contentEquals("NULL"))) {
            readoutParameterItems.add("Text: (" + featurePack.text + ")");
            readOutSpinnerOrderMap.put("Text", readOutSpinnerActionCount ++);
        }
        if(featurePack.contentDescription != null && (!featurePack.contentDescription.contentEquals("NULL"))) {
            readoutParameterItems.add("Content Description: (" + featurePack.contentDescription + ")");
            readOutSpinnerOrderMap.put("Content Description", readOutSpinnerActionCount ++);
        }
        if(hasChildText) {
            readoutParameterItems.add("Child Text: (" + childText + ")");
            readOutSpinnerOrderMap.put("Child Text", readOutSpinnerActionCount++);
        }
        if(hasSiblingText) {
            readoutParameterItems.add("Sibling Text: (" + siblingText + ")");
            readOutSpinnerOrderMap.put("Sibling Text", readOutSpinnerActionCount++);
        }
        ArrayAdapter<String> readoutAdapter = new ArrayAdapter<String>(dialogRootView.getContext(), android.R.layout.simple_spinner_item, readoutParameterItems);
        readoutParameterSpinner.setAdapter(readoutAdapter);
        if(triggerMode == TRIGGERED_BY_NEW_EVENT)
            readoutParameterSpinner.setSelection(0);
        else if (triggerMode == TRIGGERED_BY_EDIT){
            SugiliteOperation oldOperation = blockToEdit.getOperation();
            try {
                if(readOutSpinnerOrderMap.containsKey(oldOperation.getParameter()))
                    readoutParameterSpinner.setSelection(readOutSpinnerOrderMap.get(oldOperation.getParameter()));
            }
            catch (Exception e){
                e.printStackTrace();
                readoutParameterSpinner.setSelection(0);
            }
        }

        //set up load variable parameter spinner
        Map<String, Integer> loadVariableParameterSpinnerOrderMap = new HashMap<>();
        int loadVariableParameterSpinnerActionCount = 0;

        loadVariableParameterSpinner = (Spinner)dialogRootView.findViewById(R.id.element_to_load_variable_spinner);
        List<String> loadVariableParameterItems  = new ArrayList<>();
        if(featurePack.text != null && (!featurePack.text.contentEquals("NULL"))) {
            loadVariableParameterItems.add("Text: (" + featurePack.text + ")");
            loadVariableParameterSpinnerOrderMap.put("Text", loadVariableParameterSpinnerActionCount++);
        }
        if(featurePack.contentDescription != null && (!featurePack.contentDescription.contentEquals("NULL"))) {
            loadVariableParameterItems.add("Content Description: (" + featurePack.contentDescription + ")");
            loadVariableParameterSpinnerOrderMap.put("Content Description", loadVariableParameterSpinnerActionCount++);
        }
        if(hasChildText) {
            loadVariableParameterItems.add("Child Text: (" + childText + ")");
            loadVariableParameterSpinnerOrderMap.put("Child Text", loadVariableParameterSpinnerActionCount++);
        }
        if(hasSiblingText) {
            loadVariableParameterItems.add("Sibling Text: (" + siblingText + ")");
            loadVariableParameterSpinnerOrderMap.put("Sibling Text", loadVariableParameterSpinnerActionCount++);
        }
        ArrayAdapter<String> loadVariableAdapter = new ArrayAdapter<String>(dialogRootView.getContext(), android.R.layout.simple_spinner_item, loadVariableParameterItems);
        loadVariableParameterSpinner.setAdapter(loadVariableAdapter);
        if(triggerMode == TRIGGERED_BY_NEW_EVENT)
            loadVariableParameterSpinner.setSelection(0);
        else if (triggerMode == TRIGGERED_BY_EDIT){
            SugiliteOperation oldOperation = blockToEdit.getOperation();
            try {
                if(loadVariableParameterSpinnerOrderMap.containsKey(oldOperation.getParameter()))
                    loadVariableParameterSpinner.setSelection(loadVariableParameterSpinnerOrderMap.get(oldOperation.getParameter()));
            }
            catch (Exception e){
                e.printStackTrace();
                loadVariableParameterSpinner.setSelection(0);
            }
        }


        final InputMethodManager imm = (InputMethodManager) dialog.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        setTextEditText.setOnClickListener(new View.OnClickListener() {
            //force the keyboard to show when edit text on click
            @Override
            public void onClick(final View v) {
                imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        if(triggerMode == TRIGGERED_BY_EDIT && blockToEdit.getOperation() instanceof SugiliteSetTextOperation){
            String text = ((SugiliteSetTextOperation) blockToEdit.getOperation()).getText();
            if(text != null) {
                setTextEditText.setText(text);
            }
        }
        else if(featurePack.afterText != null){
            setTextEditText.setText(featurePack.afterText);
        }

        //fill the above two edittext
        if(triggerMode == TRIGGERED_BY_EDIT && blockToEdit.getOperation() instanceof SugiliteLoadVariableOperation){
            String variableName = ((SugiliteLoadVariableOperation) blockToEdit.getOperation()).getVariableName();
            if(variableName != null){
                loadVariableVariableName.setText(variableName);
                if(originalScript.variableNameDefaultValueMap.containsKey(variableName)){
                    VariableValue<String> defaultVariable = originalScript.variableNameDefaultValueMap.get(variableName);
                    if(defaultVariable != null) {
                        loadVariableVariableDefaultValue.setText(defaultVariable.getVariableValue());
                    }
                }
            }

        }



        actionSection.removeView(actionParameterSection);
        actionSection.removeView(readoutParameterSection);
        actionSection.removeView(loadVariableParameterSection);

        //set up target type spinner
        List<String> targetTypeSpinnerItems = new ArrayList<>();
        targetTypeSpinnerItems.add(featurePack.className);
        targetTypeSpinnerItems.add("Any widget");
        ArrayAdapter<String> targetTypeAdapter = new ArrayAdapter<String>(dialogRootView.getContext(), android.R.layout.simple_spinner_item, targetTypeSpinnerItems);
        targetTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetTypeSpinner.setAdapter(targetTypeAdapter);
        if(triggerMode == TRIGGERED_BY_NEW_EVENT)
            targetTypeSpinner.setSelection(0);
        else if (triggerMode == TRIGGERED_BY_EDIT){
            if(existingFilter.getClassName() != null)
                targetTypeSpinner.setSelection(0);
            else
                targetTypeSpinner.setSelection(1);
        }

        //set up within app spinner
        List<String> withinAppSpinnerItems = new ArrayList<>();
//        withinAppSpinnerItems.add(soviteAppNameAppInfoManager.getReadableAppNameForPackageName(featurePack.packageName));
        withinAppSpinnerItems.add("Any app");
        ArrayAdapter<String> withInAppAdapter = new ArrayAdapter<String>(dialogRootView.getContext(), android.R.layout.simple_spinner_item, withinAppSpinnerItems);
        withInAppAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        withInAppSpinner.setAdapter(withInAppAdapter);
        if(triggerMode == TRIGGERED_BY_NEW_EVENT)
            withInAppSpinner.setSelection(0);
        else if (triggerMode == TRIGGERED_BY_EDIT){
            if(existingFilter.getPackageName() != null)
                withInAppSpinner.setSelection(0);
            else
                withInAppSpinner.setSelection(1);
        }

        AdapterView.OnItemSelectedListener spinnerSelectedListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshAfterChange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                refreshAfterChange();
            }
        };
        if(targetTypeSpinner != null)
            targetTypeSpinner.setOnItemSelectedListener(spinnerSelectedListener);
        if(withInAppSpinner != null)
            withInAppSpinner.setOnItemSelectedListener(spinnerSelectedListener);
        if(actionSpinner != null)
            actionSpinner.setOnItemSelectedListener(spinnerSelectedListener);
        if(readoutParameterSpinner != null)
            readoutParameterSpinner.setOnItemSelectedListener(spinnerSelectedListener);
        if(loadVariableParameterSpinner != null)
            loadVariableParameterSpinner.setOnItemSelectedListener(spinnerSelectedListener);

        CompoundButton.OnCheckedChangeListener identiferCheckboxChangeListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                refreshAfterChange();
            }
        };
        if(textCheckbox != null)
            textCheckbox.setOnCheckedChangeListener(identiferCheckboxChangeListener);
        if(contentDescriptionCheckbox != null)
            contentDescriptionCheckbox.setOnCheckedChangeListener(identiferCheckboxChangeListener);
        if(viewIdCheckbox != null)
            viewIdCheckbox.setOnCheckedChangeListener(identiferCheckboxChangeListener);
        if(boundsInParentCheckbox != null)
            boundsInParentCheckbox.setOnCheckedChangeListener(identiferCheckboxChangeListener);
        if(boundsInScreenCheckbox != null)
            boundsInScreenCheckbox.setOnCheckedChangeListener(identiferCheckboxChangeListener);
        for(CheckBox checkBox : checkBoxChildEntryMap.values()){
            checkBox.setOnCheckedChangeListener(identiferCheckboxChangeListener);
        }
        for(CheckBox checkBox : checkBoxSiblingEntryMap.values()) {
            checkBox.setOnCheckedChangeListener(identiferCheckboxChangeListener);
        }

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshAfterChange();
            }
        };
        if(textCheckbox != null)
            textCheckbox.addTextChangedListener(textWatcher);
        if(contentDescriptionCheckbox != null)
            contentDescriptionCheckbox.addTextChangedListener(textWatcher);
        if(viewIdCheckbox != null)
            viewIdCheckbox.addTextChangedListener(textWatcher);
        for(CheckBox checkBox : checkBoxChildEntryMap.values()){
            checkBox.addTextChangedListener(textWatcher);
        }
        for(CheckBox checkBox : checkBoxSiblingEntryMap.values()){
            checkBox.addTextChangedListener(textWatcher);
        }

        if(setTextEditText != null)
            setTextEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    refreshAfterChange();
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

        //((TextView)findViewById(R.id.time)).setText("Event Time: " + dateFormat.format(c.getTime()) + "\nRecording script: " + sharedPreferences.getString("scriptName", "NULL"));
        //((TextView)findViewById(R.id.filteredNodeCount)).setText(generateFilterCount());
        ((TextView) dialogRootView.findViewById(R.id.previewContent)).setText(readableDescriptionGenerator.generateReadableDescription(generateBlock()));


    }

    private void refreshAfterChange(){
        //collapse and expand the action parameter
        Map<String, VariableValue> variableDefaultValueMap = null;
        switch (triggerMode) {
            case TRIGGERED_BY_NEW_EVENT:
                variableDefaultValueMap = sugiliteData.getScriptHead().variableNameDefaultValueMap;
                break;
            case TRIGGERED_BY_EDIT:
                variableDefaultValueMap = originalScript.variableNameDefaultValueMap;
                break;
        }
        String actionSpinnerSelectedItem = actionSpinner.getSelectedItem().toString();
        if (actionSpinnerSelectedItem.contentEquals("Set Text") && actionParameterSection.getParent() == null) {
            actionSection.addView(actionParameterSection);
        }
        if ((!actionSpinnerSelectedItem.contentEquals("Set Text")) && (actionParameterSection.getParent() != null))
            actionSection.removeView(actionParameterSection);

        if (actionSpinnerSelectedItem.contentEquals("Read Out") && readoutParameterSection.getParent() == null) {
            actionSection.addView(readoutParameterSection);
        }
        if ((!actionSpinnerSelectedItem.contentEquals("Read Out")) && (readoutParameterSection.getParent() != null))
            actionSection.removeView(readoutParameterSection);

        if (actionSpinnerSelectedItem.contentEquals("Load as Variable") && loadVariableParameterSection.getParent() == null) {
            actionSection.addView(loadVariableParameterSection);
            String selectedTarget = loadVariableParameterSpinner.getSelectedItem().toString();
            if(loadVariableVariableDefaultValue.getText().toString().length() < 1) {
                if (selectedTarget.contains("Text")) {
                    loadVariableVariableDefaultValue.setText(featurePack.text);
                } else if (selectedTarget.contains("Content Description")) {
                    loadVariableVariableDefaultValue.setText(featurePack.contentDescription);
                } else if (selectedTarget.contains("Child Text")) {
                    loadVariableVariableDefaultValue.setText(childText);
                } else if (selectedTarget.contains("Sibling Text")) {
                    loadVariableVariableDefaultValue.setText(siblingText);
                }
            }
        }
        if ((!actionSpinnerSelectedItem.contentEquals("Load as Variable")) && (loadVariableParameterSection.getParent() != null))
            actionSection.removeView(loadVariableParameterSection);

        //refresh "selectedchildren" and "selectedparent" and "selectedSibling"
        selectedParentFeatures.clear();
        selectedChildFeatures.clear();
        selectedSiblingFeatures.clear();

        for(Map.Entry<String, String> feature : allChildFeatures){
            CheckBox checkBox = checkBoxChildEntryMap.get(feature);
            Map.Entry<String, String> featureToAdd = new AbstractMap.SimpleEntry<String, String>(feature);
            if(checkBox != null && checkBox.isChecked()) {
                String childCheckboxLabel = extractParameter(checkBox.getText().toString());
                if(VariableHelper.isAVariable(childCheckboxLabel) && variableDefaultValueMap != null && variableDefaultValueMap.keySet().contains(VariableHelper.getVariableName(childCheckboxLabel))){
                    featureToAdd.setValue(VariableHelper.getVariableName(childCheckboxLabel));
                }
                selectedChildFeatures.add(featureToAdd);
            }
        }
        for(Map.Entry<String, String> feature : allSiblingFeatures){
            CheckBox checkBox = checkBoxSiblingEntryMap.get(feature);
            Map.Entry<String, String> featureToAdd = new AbstractMap.SimpleEntry<String, String>(feature);
            if(checkBox != null && checkBox.isChecked()) {
                String siblingCheckboxLabel = extractParameter(checkBox.getText().toString());
                if(VariableHelper.isAVariable(siblingCheckboxLabel) && variableDefaultValueMap != null && variableDefaultValueMap.keySet().contains(VariableHelper.getVariableName(siblingCheckboxLabel))){
                    featureToAdd.setValue(VariableHelper.getVariableName(siblingCheckboxLabel));
                }
                selectedSiblingFeatures.add(featureToAdd);
            }
        }

        for(Map.Entry<String, String> feature : allParentFeatures){
            CheckBox checkBox = checkBoxParentEntryMap.get(feature);
            if(checkBox != null && checkBox.isChecked())
                selectedParentFeatures.add(feature);
        }




        //use the "***Content" to generate the filter later

        if(textCheckbox != null && textCheckbox.getText() != null) {
            String textCheckboxLabel = extractParameter(textCheckbox.getText().toString());
            if (VariableHelper.isAVariable(textCheckboxLabel) && variableDefaultValueMap != null && variableDefaultValueMap.keySet().contains(VariableHelper.getVariableName(textCheckboxLabel))) {
                textContent = VariableHelper.getVariableName(textCheckboxLabel);
            } else {
                textContent = featurePack.text;
            }
        }

        if(contentDescriptionCheckbox != null && contentDescriptionCheckbox.getText() != null) {
            String contentDescriptionCheckboxLabel = extractParameter(contentDescriptionCheckbox.getText().toString());
            if (VariableHelper.isAVariable(contentDescriptionCheckboxLabel) && variableDefaultValueMap != null && variableDefaultValueMap.keySet().contains(VariableHelper.getVariableName(contentDescriptionCheckboxLabel))) {
                contentDescriptionContent = VariableHelper.getVariableName(contentDescriptionCheckboxLabel);
            } else {
                contentDescriptionContent = featurePack.contentDescription;
            }
        }

        if(viewIdCheckbox != null && viewIdCheckbox.getText() != null) {
            String viewIdCheckboxLabel = extractParameter(viewIdCheckbox.getText().toString());
            if (VariableHelper.isAVariable(viewIdCheckboxLabel) && variableDefaultValueMap != null && variableDefaultValueMap.keySet().contains(VariableHelper.getVariableName(viewIdCheckboxLabel))) {
                viewIdContent = VariableHelper.getVariableName(viewIdCheckboxLabel);
            } else {
                viewIdContent = featurePack.viewId;
            }
        }

        //refresh the alternative counts
        if(featurePack.alternativeNodes != null) {
            ((TextView) dialogRootView.findViewById(R.id.see_alternative_link)).setText(filterTester.getFilteredAlternativeNodesCount(featurePack.alternativeNodes, generateFilter()) + "element(s) matched" +
                    " out of " + featurePack.alternativeNodes.size() + " total alternative nodes, ");
        }

        //refresh the operation preview
        SugiliteOperationBlock block = generateBlock();
        ((TextView) dialogRootView.findViewById(R.id.previewContent)).setText(readableDescriptionGenerator.generateReadableDescription(block));

    }

    /**
     * save the block appropriately according the the trigger mode
     * 1. set the "next block" of the previous block to the current block
     * 2. set the "previous block" of the current block to the previous block
     *
     * @param operationBlock
     * @param activityContext
     */
    private void saveBlock(SugiliteOperationBlock operationBlock, Context activityContext){
        boolean success = false;

        //add SugiliteBlockMetaInfo if not available
        if (operationBlock.getSugiliteBlockMetaInfo() == null && operationBlock.getFeaturePack() != null) {
            SugiliteBlockMetaInfo sugiliteBlockMetaInfo = new SugiliteBlockMetaInfo(operationBlock, operationBlock.getFeaturePack().serializableUISnapshot, operationBlock.getFeaturePack().targetNodeEntity);
            operationBlock.setSugiliteBlockMetaInfo(sugiliteBlockMetaInfo);
        }

        //add screenshot if not available
        if (operationBlock.getScreenshot() == null) {
            operationBlock.setScreenshot(screenshot);
        }

        //save the variable to the symbol table if the operation is LOAD_AS_VARIABLE
        if(operationBlock.getOperation().getOperationType() == SugiliteOperation.LOAD_AS_VARIABLE){
            //add the variable to the symbol table
            String variableName = loadVariableVariableName.getText().toString();
            VariableValue stringVariable = new VariableValue<String>(variableName);
            String selectedTarget = loadVariableParameterSpinner.getSelectedItem().toString();
            if(loadVariableVariableDefaultValue.getText().toString().length() > 0) {
                //TODO: this need to be modified if we are to change the labels
                if (selectedTarget.contains("Text")) {
                    stringVariable.setVariableValue(featurePack.text);
                } else if (selectedTarget.contains("Content Description")) {
                    stringVariable.setVariableValue(featurePack.contentDescription);
                } else if (selectedTarget.contains("Child Text")) {
                    stringVariable.setVariableValue(childText);
                } else if(selectedTarget.contains("Sibling Text")) {
                    stringVariable.setVariableValue(siblingText);
                }
            }

            if(sugiliteData.variableNameVariableValueMap == null) {
                sugiliteData.variableNameVariableValueMap = new HashMap<>();
            }

            sugiliteData.variableNameVariableValueMap.put(variableName, stringVariable);

            if(triggerMode == TRIGGERED_BY_EDIT) {
                originalScript.variableNameDefaultValueMap.put(variableName, stringVariable);
            }
            else if (triggerMode == TRIGGERED_BY_NEW_EVENT){
                sugiliteData.getScriptHead().variableNameDefaultValueMap.put(variableName, stringVariable);
            }
        }

        switch (triggerMode){
            case TRIGGERED_BY_NEW_EVENT:
                operationBlock.setPreviousBlock(sugiliteData.getCurrentScriptBlock());
                if (sugiliteData.getCurrentScriptBlock() instanceof SugiliteOperationBlock) {
                    ((SugiliteOperationBlock) sugiliteData.getCurrentScriptBlock()).setNextBlock(operationBlock);
                }
                else if (sugiliteData.getCurrentScriptBlock() instanceof SugiliteStartingBlock) {
                    ((SugiliteStartingBlock) sugiliteData.getCurrentScriptBlock()).setNextBlock(operationBlock);
                }
                else if (sugiliteData.getCurrentScriptBlock() instanceof SugiliteErrorHandlingForkBlock){
                    ((SugiliteErrorHandlingForkBlock) sugiliteData.getCurrentScriptBlock()).setAlternativeNextBlock(operationBlock);
                }
                else if (sugiliteData.getCurrentScriptBlock() instanceof SugiliteSpecialOperationBlock){
                    ((SugiliteSpecialOperationBlock) sugiliteData.getCurrentScriptBlock()).setNextBlock(operationBlock);
                }
                else{
                    throw new RuntimeException("Unsupported Block Type!");
                }
                sugiliteData.setCurrentScriptBlock(operationBlock);
                try {
                    if (featurePack.packageName != null) {
                        sugiliteData.getScriptHead().relevantPackages.add(featurePack.packageName);
                    }
                    sugiliteScriptDao.save(sugiliteData.getScriptHead());
                    success = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    success = false;
                }
                System.out.println("saved block");
                break;

            case TRIGGERED_BY_EDIT:
                SugiliteBlock currentBlock = originalScript;
                SugiliteBlock previousBlock = null;
                while(true){
                    if(currentBlock == null) {
                        new Exception("can't find the block to edit").printStackTrace();
                        break;
                    }
                    else if(currentBlock instanceof SugiliteStartingBlock) {
                        previousBlock = currentBlock;
                        currentBlock = ((SugiliteStartingBlock) currentBlock).getNextBlockToRun();
                    }
                    else if (currentBlock instanceof SugiliteOperationBlock){
                        if(currentBlock.getBlockId() == blockToEdit.getBlockId()){
                            //matched
                            if(previousBlock instanceof SugiliteOperationBlock){
                                ((SugiliteOperationBlock)previousBlock).setNextBlock(operationBlock);
                                operationBlock.setPreviousBlock(previousBlock);
                                operationBlock.setNextBlock(((SugiliteOperationBlock) currentBlock).getNextBlockToRun());
                                try {
                                    if (featurePack.packageName != null) {
                                        originalScript.relevantPackages.add(featurePack.packageName);
                                    }
                                    sugiliteScriptDao.save(originalScript);
                                    //commit save for triggered_by_edit
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run()
                                        {
                                            try {
                                                sugiliteScriptDao.commitSave(null);
                                            }
                                            catch (Exception e){
                                                e.printStackTrace();
                                            }
                                        }
                                    }).start();
                                    success = true;
                                }
                                catch (Exception e){
                                    e.printStackTrace();
                                    success = false;
                                }
                                break;
                            }
                            else if(currentBlock.getPreviousBlock() instanceof SugiliteStartingBlock){
                                ((SugiliteStartingBlock)previousBlock).setNextBlock(operationBlock);
                                operationBlock.setPreviousBlock(previousBlock);
                                operationBlock.setNextBlock(((SugiliteOperationBlock) currentBlock).getNextBlockToRun());
                                try {
                                    sugiliteScriptDao.save(originalScript);
                                    //commit save for triggered_by_edit
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run()
                                        {
                                            try {
                                                sugiliteScriptDao.commitSave(null);
                                            }
                                            catch (Exception e){
                                                e.printStackTrace();
                                            }
                                        }
                                    }).start();
                                    success = true;
                                }
                                catch (Exception e){
                                    success = false;
                                    e.printStackTrace();
                                }
                                break;
                            }
                            else {
                                //something wrong here
                            }
                        }
                        else{
                            previousBlock = currentBlock;
                            currentBlock = ((SugiliteOperationBlock) currentBlock).getNextBlockToRun();
                        }
                    }
                }

                break;
        }
        dialog.dismiss();
        /*
        new AlertDialog.Builder(activityContext)
                .setTitle("Operation Recorded")
                .setMessage(Html.fromHtml(readableDescriptionGenerator.generateReadableDescription(operationBlock)))
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue
                        setResult((retVal ? RESULT_OK : RESULT_CANCELED));
                        finish();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
       */

    }

    private String extractParameter(String content){
        if(content.contains("[") && content.contains("]") && content.contains(":")) {
            if(content.lastIndexOf(":") > content.indexOf("]")) {
                return content.substring(content.indexOf("["), content.lastIndexOf(":")).trim();
            }
            else {
                return content.substring(content.indexOf("[")).trim();
            }
        }
        else {
            return content.trim();
        }
    }

    /**
     * generate a UIElementMatchingFilter based on the selection
     * @return
     */
    @Deprecated
    public UIElementMatchingFilter generateFilter(){
        UIElementMatchingFilter filter = new UIElementMatchingFilter();
//        if(withInAppSpinner.getSelectedItem().toString().contentEquals(soviteAppNameAppInfoManager.getReadableAppNameForPackageName(featurePack.packageName))){
//            filter.setPackageName(featurePack.packageName);
//        }
        if(targetTypeSpinner.getSelectedItem().toString().contentEquals(featurePack.className)){
            filter.setClassName(featurePack.className);
        }
        if(textCheckbox != null && textCheckbox.isChecked()){
            filter.setText(extractParameter(textContent));
        }
        if(contentDescriptionCheckbox != null && contentDescriptionCheckbox.isChecked()){
            filter.setContentDescription(extractParameter(contentDescriptionContent));
        }
        if(viewIdCheckbox != null && viewIdCheckbox.isChecked()){
            filter.setViewId(extractParameter(viewIdContent));
        }
        if(boundsInParentCheckbox != null && boundsInParentCheckbox.isChecked()){
            filter.setBoundsInParent(Rect.unflattenFromString(featurePack.boundsInParent));
        }
        if(boundsInScreenCheckbox != null && boundsInScreenCheckbox.isChecked()){
            filter.setBoundsInScreen(Rect.unflattenFromString(featurePack.boundsInScreen));
        }

        if (selectedChildFeatures.size() > 0){
            for(Map.Entry<String, String> entry : selectedChildFeatures){
                UIElementMatchingFilter childFilter = new UIElementMatchingFilter();
                if(entry.getKey().contains("Text")){
                    childFilter.setText(extractParameter(entry.getValue()));
                }
                if(entry.getKey().contains("ContentDescription")){
                    childFilter.setContentDescription(extractParameter(entry.getValue()));
                }
                if(entry.getKey().contains("ViewID")){
                    childFilter.setViewId(extractParameter(entry.getValue()));
                }
                // if there are multiple children properties, set all of them in the filter
                filter.setChildFilter(childFilter);
            }
        }
        if (selectedSiblingFeatures.size() > 0){
            for(Map.Entry<String, String> entry : selectedSiblingFeatures){
                UIElementMatchingFilter siblingFilter = new UIElementMatchingFilter();
                if(entry.getKey().contains("Text")){
                    siblingFilter.setText(extractParameter(entry.getValue()));
                }
                if(entry.getKey().contains("ContentDescription")){
                    siblingFilter.setContentDescription(extractParameter(entry.getValue()));
                }
                if(entry.getKey().contains("ViewID")){
                    siblingFilter.setViewId(extractParameter(entry.getValue()));
                }
                filter.setSiblingFilter(siblingFilter);
            }
        }
        if (selectedParentFeatures.size() > 0){
            UIElementMatchingFilter parentFilter = new UIElementMatchingFilter();
            for(Map.Entry<String, String> entry : selectedParentFeatures){
                if(entry.getKey().contentEquals("Text")){
                    parentFilter.setText(entry.getValue());
                }
                if(entry.getKey().contentEquals("ContentDescription")){
                    parentFilter.setContentDescription(entry.getValue());
                }
                if(entry.getKey().contentEquals("ViewID")){
                    parentFilter.setViewId(entry.getValue());
                }
            }
            filter.setParentFilter(parentFilter);
        }

        if(alternativeLabels != null)
            filter.alternativeLabels = new HashSet<>(alternativeLabels);

        return filter;
    }

    /**
     * Generate a SerializableOntologyQuery based on the selection of the popup
     *
     * @return the result SearializableOntologyQuery
     */
    public OntologyQuery generateQuery(){
        CombinedOntologyQuery q = new CombinedOntologyQuery(CombinedOntologyQuery.RelationType.AND);

//        if(withInAppSpinner.getSelectedItem().toString().contentEquals(soviteAppNameAppInfoManager.getReadableAppNameForPackageName(featurePack.packageName))){
//            LeafOntologyQuery subQuery = new LeafOntologyQuery();
//            Set<SugiliteEntity> object = new HashSet<>();
//            object.add(new SugiliteEntity(-1, String.class, featurePack.packageName));
//            subQuery.setObjectSet(object);
//            subQuery.setQueryFunction(SugiliteRelation.HAS_PACKAGE_NAME);
//            q.addSubQuery(subQuery);
//        }
        if(targetTypeSpinner.getSelectedItem().toString().contentEquals(featurePack.className)){
            LeafOntologyQuery subQuery = new LeafOntologyQuery();
            Set<SugiliteEntity> object = new HashSet<>();
            object.add(new SugiliteEntity(-1, String.class, featurePack.className));
            subQuery.setObjectSet(object);
            subQuery.setQueryFunction(SugiliteRelation.HAS_CLASS_NAME);
            q.addSubQuery(subQuery);
        }
        if(textCheckbox != null && textCheckbox.isChecked()){
            LeafOntologyQuery subQuery = new LeafOntologyQuery();
            Set<SugiliteEntity> object = new HashSet<>();
            object.add(new SugiliteEntity(-1, String.class, textContent));
            subQuery.setObjectSet(object);
            subQuery.setQueryFunction(SugiliteRelation.HAS_TEXT);
            q.addSubQuery(subQuery);
        }
        if(contentDescriptionCheckbox != null && contentDescriptionCheckbox.isChecked()){
            LeafOntologyQuery subQuery = new LeafOntologyQuery();
            Set<SugiliteEntity> object = new HashSet<>();
            object.add(new SugiliteEntity(-1, String.class, contentDescriptionContent));
            subQuery.setObjectSet(object);
            subQuery.setQueryFunction(SugiliteRelation.HAS_CONTENT_DESCRIPTION);
            q.addSubQuery(subQuery);
        }
        if(viewIdCheckbox != null && viewIdCheckbox.isChecked()){
            LeafOntologyQuery subQuery = new LeafOntologyQuery();
            Set<SugiliteEntity> object = new HashSet<>();
            object.add(new SugiliteEntity(-1, String.class, viewIdContent));
            subQuery.setObjectSet(object);
            subQuery.setQueryFunction(SugiliteRelation.HAS_VIEW_ID);
            q.addSubQuery(subQuery);
        }
        if(boundsInParentCheckbox != null && boundsInParentCheckbox.isChecked()){
            LeafOntologyQuery subQuery = new LeafOntologyQuery();
            Set<SugiliteEntity> object = new HashSet<>();
            object.add(new SugiliteEntity(-1, String.class, featurePack.boundsInParent));
            subQuery.setObjectSet(object);
            subQuery.setQueryFunction(SugiliteRelation.HAS_PARENT_LOCATION);
            q.addSubQuery(subQuery);
        }
        if(boundsInScreenCheckbox != null && boundsInScreenCheckbox.isChecked()){
            LeafOntologyQuery subQuery = new LeafOntologyQuery();
            Set<SugiliteEntity> object = new HashSet<>();
            object.add(new SugiliteEntity(-1, String.class, featurePack.boundsInScreen));
            subQuery.setObjectSet(object);
            subQuery.setQueryFunction(SugiliteRelation.HAS_SCREEN_LOCATION);
            q.addSubQuery(subQuery);
        }

        if (selectedChildFeatures.size() > 0){
            for(Map.Entry<String, String> entry : selectedChildFeatures){
                if(entry.getKey().contains("Text")){
                    LeafOntologyQuery subQuery = new LeafOntologyQuery();
                    Set<SugiliteEntity> object = new HashSet<>();
                    object.add(new SugiliteEntity(-1, String.class, entry.getValue()));
                    subQuery.setObjectSet(object);
                    subQuery.setQueryFunction(SugiliteRelation.HAS_CHILD_TEXT);
                    q.addSubQuery(subQuery);
                }
            }
        }
        if (selectedSiblingFeatures.size() > 0){
            for(Map.Entry<String, String> entry : selectedSiblingFeatures){
                if(entry.getKey().contains("Text")){
                    LeafOntologyQuery subQuery = new LeafOntologyQuery();
                    Set<SugiliteEntity> object = new HashSet<>();
                    object.add(new SugiliteEntity(-1, String.class, entry.getValue()));
                    subQuery.setObjectSet(object);
                    subQuery.setQueryFunction(SugiliteRelation.HAS_SIBLING_TEXT);
                    q.addSubQuery(subQuery);
                }
            }
        }

        return q;
    }

    private SugiliteOperationBlock generateBlock(){
        //determine the action first
        SugiliteOperation sugiliteOperation = null;
        String actionSpinnerSelectedItem = actionSpinner.getSelectedItem().toString();
        if (actionSpinnerSelectedItem.contentEquals("Click")) {
            sugiliteOperation = new SugiliteClickOperation();
            ((SugiliteClickOperation)sugiliteOperation).setQuery(generateQuery());
        }
        if (actionSpinnerSelectedItem.contentEquals("Long Click")) {
            sugiliteOperation = new SugiliteLongClickOperation();
            ((SugiliteLongClickOperation)sugiliteOperation).setQuery(generateQuery());
        }
        if(actionSpinnerSelectedItem.contentEquals("Select")) {
            sugiliteOperation = new SugiliteSelectOperation();
            ((SugiliteSelectOperation)sugiliteOperation).setQuery(generateQuery());
        }
        if (actionSpinnerSelectedItem.contentEquals("Read Out")) {
            sugiliteOperation = new SugiliteReadoutOperation();
            if(dialogRootView.findViewById(R.id.text_to_read_out_spinner) != null){
                String selectionText = ((Spinner)dialogRootView.findViewById(R.id.text_to_read_out_spinner)).getSelectedItem().toString();
                ((SugiliteReadoutOperation)sugiliteOperation).setPropertyToReadout(selectionText.substring(0, selectionText.indexOf(":")));
            }
            ((SugiliteReadoutOperation)sugiliteOperation).setQuery(generateQuery());
        }
        if (actionSpinnerSelectedItem.contentEquals("Load as Variable")){
            sugiliteOperation = new SugiliteLoadVariableOperation();
            if(dialogRootView.findViewById(R.id.element_to_load_variable_spinner) != null){
                String selectionText = ((Spinner)dialogRootView.findViewById(R.id.element_to_load_variable_spinner)).getSelectedItem().toString();
                ((SugiliteLoadVariableOperation)sugiliteOperation).setPropertyToSave(selectionText.substring(0, selectionText.indexOf(":")));
                String variableName = loadVariableVariableName.getText().toString();
                ((SugiliteLoadVariableOperation)sugiliteOperation).setVariableName(variableName);
            }
            ((SugiliteLoadVariableOperation)sugiliteOperation).setQuery(generateQuery());
        }
        if (actionSpinnerSelectedItem.contentEquals("Set Text")) {
            sugiliteOperation = new SugiliteSetTextOperation();
            //replace set text parameter with parameter
            if(dialogRootView.findViewById(R.id.action_parameter_set_text) != null) {
                String rawText = ((EditText) dialogRootView.findViewById(R.id.action_parameter_set_text)).getText().toString();
                ((SugiliteSetTextOperation) sugiliteOperation).setText(extractParameter(rawText));
            }
            ((SugiliteSetTextOperation)sugiliteOperation).setQuery(generateQuery());

            /*

            switch (triggerMode) {
                case TRIGGERED_BY_NEW_EVENT:
                    ((SugiliteSetTextOperation)sugiliteOperation).setText(textVariableParse(rawText, sugiliteData.getScriptHead().variableNameSet, sugiliteData.variableNameVariableValueMap));
                    break;
                case TRIGGERED_BY_EDIT:
                    ((SugiliteSetTextOperation)sugiliteOperation).setText(textVariableParse(rawText, originalScript.variableNameSet, sugiliteData.variableNameVariableValueMap));
                    break;
            }
            */
        }

        final SugiliteOperationBlock operationBlock = new SugiliteOperationBlock();
        operationBlock.setOperation(sugiliteOperation);
        operationBlock.setFeaturePack(featurePack);
        operationBlock.setElementMatchingFilter(generateFilter());
        operationBlock.setScreenshot(featurePack.screenshot);
        operationBlock.setDescription(readableDescriptionGenerator.generateReadableDescription(operationBlock));
        return operationBlock;
    }



    private String boldify(String text){
        return "<b>" + text + "</b>";
    }

    private LinearLayout generateRow(final CheckBox checkBox, final String label, final String defaultDefaultValue){
        LinearLayout linearLayout = new LinearLayout(dialogRootView.getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        //REMOVED the "set as a parameter" link under each checkbox

        /*
        TextView setVariableLink = new TextView(dialogRootView.getContext());
        setVariableLink.setText(Html.fromHtml("<u><i>Set as a parameter</i></u>"));
        setVariableLink.setTextColor(Color.parseColor(Const.SCRIPT_LINK_COLOR));
        setVariableLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAsAParameterOnClick(v, checkBox, label, defaultDefaultValue);
            }
        });
        */
        LinearLayout.LayoutParams checkBoxParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        linearLayout.addView(checkBox, checkBoxParams);
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        linkParams.setMargins(50, 0, 0, 0);
        /*
        setVariableLink.setPadding(0, 0 ,0 ,0);
        linearLayout.addView(setVariableLink, linkParams);
        */
        return linearLayout;
    }


}

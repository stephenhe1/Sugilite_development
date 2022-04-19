package edu.cmu.hcii.sugilite.recording.newrecording.fullscreen_overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import org.apache.commons.collections.map.HashedMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.cmu.hcii.sugilite.Const;
import edu.cmu.hcii.sugilite.model.Node;
import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.model.block.util.SugiliteAvailableFeaturePack;
import edu.cmu.hcii.sugilite.model.block.SugiliteOperationBlock;
import edu.cmu.hcii.sugilite.model.operation.SugiliteOperation;
import edu.cmu.hcii.sugilite.ontology.OntologyQuery;
import edu.cmu.hcii.sugilite.ontology.SugiliteEntity;
import edu.cmu.hcii.sugilite.ontology.UISnapshot;
import edu.cmu.hcii.sugilite.pumice.PumiceDemonstrationUtil;
import edu.cmu.hcii.sugilite.recording.newrecording.SugiliteBlockBuildingHelper;
import edu.cmu.hcii.sugilite.ui.AppCompatPreferenceActivity;
import edu.cmu.hcii.sugilite.ui.dialog.NewScriptDialog;
import edu.cmu.hcii.sugilite.ui.main.FragmentScriptListTab;


/**
 * @author toby
 * @date 2/7/18
 * @time 7:39 PM
 */

/**
 * dummy dialog -> will lead to either RecordingAmbiguousPopupDialog or SugiliteRecordingConfirmationDialog
 */
public class OverlayClickedDialog{
    private Context context;
    private SugiliteEntity<Node> node;
    private UISnapshot uiSnapshot;
    private LayoutInflater layoutInflater;
    private float x, y;
    private View overlay;
    private SugiliteAvailableFeaturePack featurePack;
    private Dialog dialog;
    private TextToSpeech tts;
    private FullScreenRecordingOverlayManager recordingOverlayManager;
    private SugiliteBlockBuildingHelper blockBuildingHelper;
    private SharedPreferences sharedPreferences;
    private SugiliteData sugiliteData;
    private boolean isLongClick;
    private File screenshot;



    public OverlayClickedDialog(Context context, SugiliteEntity<Node> node, UISnapshot uiSnapshot, File screenshot, float x, float y, FullScreenRecordingOverlayManager recordingOverlayManager, View overlay, SugiliteData sugiliteData, SharedPreferences sharedPreferences, TextToSpeech tts, boolean isLongClick) {
        this.context = context;
        this.node = node;
        this.uiSnapshot = uiSnapshot;
        this.screenshot = screenshot;
        this.layoutInflater = LayoutInflater.from(context);;
        this.overlay = overlay;
        this.x = x;
        this.y = y;
        this.tts = tts;
        this.recordingOverlayManager = recordingOverlayManager;
        this.blockBuildingHelper = new SugiliteBlockBuildingHelper(context, sugiliteData);
        this.sugiliteData = sugiliteData;
        this.sharedPreferences = sharedPreferences;
        this.isLongClick = isLongClick;
        this.featurePack = new SugiliteAvailableFeaturePack(node, this.uiSnapshot, screenshot);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(Const.appNameUpperCase + " Demonstration");

        List<String> operationList = new ArrayList<>();

        //fill in the options
        operationList.add("Record");
        operationList.add("Click without Recording");
        operationList.add("Cancel");
        String[] operations = new String[operationList.size()];
        operations = operationList.toArray(operations);
        final String[] operationClone = operations.clone();


        builder.setItems(operationClone, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (operationClone[which]) {
                    case "Record":
                        //recording
                        handleRecording();
                        dialog.dismiss();
                        break;
                    case "Click without Recording":
                        recordingOverlayManager.clickNode(node.getEntityValue(), x, y, overlay, isLongClick);
                        dialog.dismiss();
                        break;
                    case "Cancel":
                        dialog.dismiss();
                        break;
                }
            }
        });
        dialog = builder.create();
    }

    private List<Node> getParentalNode(Node nodeEntity){
        List<Node> nodesList=new ArrayList<>();
        while (nodeEntity!=null){
            nodesList.add(nodeEntity);
            nodeEntity=nodeEntity.getParent();
        }
        return nodesList;
    }




    public int getNodeIndex(Node nodeInfo) {
        if  (null!=nodeInfo) {
            if (null!=nodeInfo.getParent()) {
                AccessibilityNodeInfo  parent = nodeInfo.getParentalNode();
                int childCount = parent.getChildCount();
                if (childCount > 1) {
                    int count=0;
                    int length=0;
                    int invisibleNumber=0;
                    for (int i = 0; i < childCount; i++) {
                        if(null!=parent.getChild(i)){
                            try {
                                if(parent.getChild(i).equals(nodeInfo.getThisNode())){
                                    length=count-invisibleNumber;
                                    return length;
                                }
                                if (parent.getChild(i).getClassName().toString().equals(nodeInfo.getClassName())) {
                                    count++;
//                                    parent.getChild(i).getBoundsInScreen(rect);
//                                    if("com.google.android.apps.youtube.music:id/waze_bar_container".equals(parent.getChild(i).getViewIdResourceName())){
//                                        invisibleNumber=1;
//                                    }
//                                    rects.add(rect);
                                }
                            }
                            catch (NullPointerException e){
                                e.printStackTrace();
                            }
                        }
                    }

                } else {
                    return 0;
                }
            }
            return 0;
        }
        return -1;
    }

    public void writeXPATH(String fileName,String XPATH){
        BufferedWriter bw = null;
        try {
//            System.out.println("The saved file path is: "+sugiliteScriptDao.getContext().getFilesDir().getPath()+"/scripts/"+fileName+"_xpath.txt");
            bw = new BufferedWriter(new FileWriter(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + fileName+"_xpath.txt"),true));
            bw.write(XPATH+"\n");
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



    /**
     * handle when the operation is to be recorded
     */
    private void handleRecording() {
        List<Pair<OntologyQuery, Double>> queryScoreList = SugiliteBlockBuildingHelper.newGenerateDefaultQueries(uiSnapshot, node);
        List<Node> nodesList=getParentalNode(node.getEntityValue());

        String xpath="(HAS_XPATH /hierarchy";
        Collections.reverse(nodesList);
        for (Node simpleNode:nodesList){
            int ownIndex=getNodeIndex(simpleNode);
            if (ownIndex>0) {
                xpath=xpath+"/"+simpleNode.getClassName() + "[" + (ownIndex+1) +"]";
            }
            else{
                xpath=xpath+"/"+simpleNode.getClassName();
            }
        }
        xpath=xpath+")";

        System.out.println("scriptName is:"+ NewScriptDialog.getScript_name());
        writeXPATH(NewScriptDialog.getScript_name(),xpath);



        if (queryScoreList.size() > 0) {
            System.out.println("Query Score List in OverlayClickedDialog: " + queryScoreList);

            //TODO: 19/03/11 temporarily disable the ambiguous pop-up for PUMICE study

            //generate alternative query
            SugiliteOperationBlock block = blockBuildingHelper.getUnaryOperationBlockWithOntologyQueryFromQuery(queryScoreList.get(0).first, isLongClick ? SugiliteOperation.LONG_CLICK : SugiliteOperation.CLICK, featurePack, SugiliteBlockBuildingHelper.getFirstNonTextQuery(queryScoreList),SugiliteBlockBuildingHelper.getFirstViewIDQuery(queryScoreList));
            block.setScreenshot(screenshot);
            showConfirmation(block, featurePack, queryScoreList);
        } else {
            //empty result
            PumiceDemonstrationUtil.showSugiliteToast("Empty Results!", Toast.LENGTH_SHORT);
        }
    }


    public void show() {

        /*
        dialog.getWindow().setType(OVERLAY_TYPE);
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_box);
        dialog.show();
        */
        //TODO: bypass the dialog

        //handleRecording();

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                //capture the screen
                handleRecording();
            }
        }, 200);



    }

    /**
     * show the popup for choosing from ambiguous options
     *
     * @param queryScoreList
     * @param featurePack
     */
    //TODO: add support for verbal instruction here
    private void showAmbiguousPopup(List<Pair<OntologyQuery, Double>> queryScoreList, SugiliteAvailableFeaturePack featurePack, SugiliteEntity<Node> actualClickedNode) {
        RecordingAmbiguousPopupDialog recordingAmbiguousPopupDialog = new RecordingAmbiguousPopupDialog(context, queryScoreList, featurePack, blockBuildingHelper, new Runnable() {
            @Override
            public void run() {
                recordingOverlayManager.clickNode(node.getEntityValue(), x, y, overlay, isLongClick);
            }
        },
                uiSnapshot, actualClickedNode, sugiliteData, sharedPreferences, tts, 0);
        recordingAmbiguousPopupDialog.show();
    }

    /**
     * show the popup for recording confirmation
     *
     * @param block
     * @param featurePack
     * @param queryScoreList
     */
    private void showConfirmation(SugiliteOperationBlock block, SugiliteAvailableFeaturePack featurePack, List<Pair<OntologyQuery, Double>> queryScoreList) {
        Runnable clickRunnable = new Runnable() {
            @Override
            public void run() {
                recordingOverlayManager.clickNode(node.getEntityValue(), x, y, overlay, isLongClick);
            }
        };
        SugiliteRecordingConfirmationDialog confirmationDialog = new SugiliteRecordingConfirmationDialog(context, block, featurePack, queryScoreList, clickRunnable, blockBuildingHelper, uiSnapshot, node, sugiliteData, sharedPreferences, tts);
        confirmationDialog.show();
    }
}

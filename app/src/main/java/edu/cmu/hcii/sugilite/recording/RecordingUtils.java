package edu.cmu.hcii.sugilite.recording;

import android.content.Context;
import android.os.Environment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import edu.cmu.hcii.sugilite.model.block.util.SugiliteAvailableFeaturePack;
import edu.cmu.hcii.sugilite.pumice.PumiceDemonstrationUtil;
import edu.cmu.hcii.sugilite.ui.dialog.NewScriptDialog;
import tech.gusavila92.websocketclient.WebSocketClient;

public class RecordingUtils {
    public static void sendNodeInfo(SugiliteAvailableFeaturePack sugiliteAvailableFeaturePack, String action, boolean isTypeCommand){
        //Get the websocket instance
        WebSocketClient webSocketClient= PumiceDemonstrationUtil.getWebSocketClientInst();
        JSONObject targetObject;
        if(null != sugiliteAvailableFeaturePack) {
            targetObject = transferFeatureIntoJSON(sugiliteAvailableFeaturePack);
            JSONObject command = new JSONObject();
            try {
                command.put("action", action);
                command.put("target", targetObject);
                if (isTypeCommand) command.put("text", targetObject.get("text"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            JSONObject sendCommandSM = new JSONObject();
            try {
                sendCommandSM.put("action", "SENDCOMMAND");
                sendCommandSM.put("command", command);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            webSocketClient.send(String.valueOf(sendCommandSM));
        }

    }


    public static void writeTestScript(Context context, String fileName, SugiliteAvailableFeaturePack sugiliteAvailableFeaturePack, String action, boolean isTypeCommand){
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) + "/"+ NewScriptDialog.getPackageName() + "/RECORDER/" + fileName+".jsonl"),true));
            JSONObject targetObject;
            if(null != sugiliteAvailableFeaturePack) {
                targetObject = transferFeatureIntoJSON(sugiliteAvailableFeaturePack);
                JSONObject command = new JSONObject();
                try {
                    command.put("action", action);
                    command.put("target", targetObject);
                    if (isTypeCommand) command.put("text", targetObject.get("text"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                bw.write(command.toString()+"\n");
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

    private static JSONObject transferFeatureIntoJSON(SugiliteAvailableFeaturePack sugiliteAvailableFeaturePack){
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

            try{
                targetObject.put("bounds",sugiliteAvailableFeaturePack.boundsInScreen);
            }
            catch (JSONException e){
                e.printStackTrace();
            }
        }
        return targetObject;
    }
}

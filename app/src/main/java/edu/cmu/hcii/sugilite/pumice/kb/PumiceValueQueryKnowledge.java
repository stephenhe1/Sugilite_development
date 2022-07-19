package edu.cmu.hcii.sugilite.pumice.kb;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.automation.AutomatorUtil;
import edu.cmu.hcii.sugilite.automation.ServiceStatusManager;
import edu.cmu.hcii.sugilite.model.block.SugiliteStartingBlock;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteGetOperation;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteGetValueOperation;
import edu.cmu.hcii.sugilite.model.value.SugiliteSimpleConstant;
import edu.cmu.hcii.sugilite.model.value.SugiliteValue;
import edu.cmu.hcii.sugilite.model.variable.VariableValue;
import edu.cmu.hcii.sugilite.pumice.communication.SkipPumiceJSONSerialization;
import edu.cmu.hcii.sugilite.pumice.dialog.PumiceDialogManager;
import edu.cmu.hcii.sugilite.pumice.PumiceDemonstrationUtil;
import edu.cmu.hcii.sugilite.pumice.kb.default_query.BuiltInValueQuery;
//import edu.cmu.hcii.sugilite.sovite.SoviteAppNameAppInfoManager;


/**
 * @author toby
 * @date 10/30/18
 * @time 3:02 PM
 */
public class PumiceValueQueryKnowledge<T> implements Serializable {
    public enum ValueType {NUMERICAL, STRING}
    private String valueName;
    private ValueType valueType;
    private String utterance;

    //holds the value -- can be a get query, a constant or a resolve query.
    @SkipPumiceJSONSerialization
    private SugiliteValue sugiliteValue;

    //the Sugilite block used to obtain the value at runtime - only used when sugiliteValue is null
    //not serialized for GSON
    @SkipPumiceJSONSerialization
    private SugiliteStartingBlock sugiliteStartingBlock;

    //the list of involvedAppNames -> SHOULD be non-null only if sugiliteStartingBlock is non-null
    private List<String> involvedAppNames;

    public PumiceValueQueryKnowledge(){
        this.involvedAppNames = new ArrayList<>();
    }

    public PumiceValueQueryKnowledge(String valueName, ValueType valueType){
        this();
        this.valueName = valueName;
        this.valueType = valueType;
        this.utterance = valueName;
    }

    public PumiceValueQueryKnowledge(String valueName, String userUtterance, ValueType valueType, SugiliteValue sugiliteValue){
        this(valueName, valueType);
        this.sugiliteValue = sugiliteValue;
        this.utterance = userUtterance;
    }

    public PumiceValueQueryKnowledge(Context context, String valueName, ValueType valueType, SugiliteStartingBlock sugiliteStartingBlock){
        this(valueName, valueType);
        this.sugiliteStartingBlock = sugiliteStartingBlock;
        this.utterance = "demonstrate";
        //populate involvedAppNames
        Set<String> involvedAppPackageNames = new HashSet<>();
        for(String packageName : sugiliteStartingBlock.relevantPackages){
            if (! AutomatorUtil.isHomeScreenPackage(packageName)){
                involvedAppPackageNames.add(packageName);
            }
        }
//        SoviteAppNameAppInfoManager soviteAppNameAppInfoManager = SoviteAppNameAppInfoManager.getInstance(SugiliteData.getAppContext());
//        for(String packageName : involvedAppPackageNames){
//            //get app name for package name
//            involvedAppNames.add(soviteAppNameAppInfoManager.getReadableAppNameForPackageName(packageName));
//        }
    }

    public void copyFrom(PumiceValueQueryKnowledge pumiceValueQueryKnowledge){
        this.valueName = pumiceValueQueryKnowledge.valueName;
        this.valueType = pumiceValueQueryKnowledge.valueType;
        this.sugiliteStartingBlock = pumiceValueQueryKnowledge.sugiliteStartingBlock;
        this.sugiliteValue = pumiceValueQueryKnowledge.sugiliteValue;
        this.utterance = pumiceValueQueryKnowledge.utterance;
        this.involvedAppNames = pumiceValueQueryKnowledge.involvedAppNames;
    }

    public String getValueName() {
        return valueName;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public String getUtterance() {
        return utterance;
    }

    public void setValueName(String valueName) {
        this.valueName = valueName;
    }

    public SugiliteValue getSugiliteValue() {
        return sugiliteValue;
    }

    public T evaluate(SugiliteData sugiliteData){
        //getting the value using the script stored in sugiliteStartingBlock
        PumiceDialogManager pumiceDialogManager = sugiliteData.pumiceDialogManager;
        if (pumiceDialogManager != null) {
            if (sugiliteValue != null){
                //if there is a sugiliteValue, simply return the result of evaluating it;
                Object result = sugiliteValue.evaluate(sugiliteData);
                if (sugiliteValue instanceof BuiltInValueQuery) {
                    //say the result of BuiltInValue if the type of sugiliteValue is BuiltInValue
                    pumiceDialogManager.sendAgentMessage(((BuiltInValueQuery) sugiliteValue).getFeedbackMessage(result), true, false);
                }
                try {
                    //result SHOULD be type T
                    return (T) result;
                } catch (Exception e){
                    throw new RuntimeException("error in processing the value query -- can't find the target value knowledge");
                }

            } else {
                //otherwise, retrieve the value by running the script
                Activity context = pumiceDialogManager.getContext();
                ServiceStatusManager serviceStatusManager = ServiceStatusManager.getInstance(context);
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

                //shared variable between threads
                StringBuffer returnValue = new StringBuffer("");

                //this runnable gets executed at the end of the value query script
                Runnable afterExecutionRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            System.out.println(sugiliteData.variableNameVariableValueMap);
                            if (sugiliteData.variableNameVariableValueMap.containsKey(valueName)) {
                                VariableValue returnVariable = sugiliteData.variableNameVariableValueMap.get(valueName);
                                if (returnVariable.getVariableValue() instanceof String) {
                                    synchronized (returnValue) {
                                        returnValue.append((String)returnVariable.getVariableValue());
                                        returnValue.notify();
                                    }
                                } else {
                                    throw new RuntimeException("error -- wrong type of variable");
                                }
                            } else {
                                throw new RuntimeException("error -- can't find the variable / failure in extracting value");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
                PumiceDemonstrationUtil.executeScript(context, serviceStatusManager, sugiliteStartingBlock, sugiliteData, sharedPreferences, false, pumiceDialogManager, null, afterExecutionRunnable);

                synchronized (returnValue) {
                    try {
                        System.out.println("waiting for the script to return the value");
                        returnValue.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                //return the value extracted from the screen
                pumiceDialogManager.sendAgentMessage("The value of " + valueName + " is " + returnValue.toString(), true, false);
                try {
                    return (T) (returnValue.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("error in returning the evaluation result!");
                }
            }
        } else {
            throw new RuntimeException("empty dialog manager!");
        }
    }

    public String getValueDescription(){
        String description = "How to get the value of " + valueName;
        if (sugiliteValue != null && sugiliteValue instanceof SugiliteGetValueOperation){
            description = description + ", which is the value of " + ((SugiliteGetValueOperation) sugiliteValue).getName();
        }

        else if (sugiliteValue != null && sugiliteValue instanceof SugiliteSimpleConstant) {
            description = description + ", which is a constant " + ((SugiliteSimpleConstant) sugiliteValue).toString();
        }

        else if (involvedAppNames != null && involvedAppNames.size() > 0) {
            description = description + " in " + PumiceDemonstrationUtil.joinListGrammatically(involvedAppNames, "and");
        }
        return description;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder()
            .addSerializationExclusionStrategy(new ExclusionStrategy()
            {
                @Override
                public boolean shouldSkipField(FieldAttributes f)
                {
                    return f.getAnnotation(SkipPumiceJSONSerialization.class) != null;
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz)
                {
                    return false;
                }
            })
            .create();
        return gson.toJson(this);
    }

    public SugiliteGetOperation getSugiliteOperation(){
        return new SugiliteGetValueOperation(valueName);
    }
}

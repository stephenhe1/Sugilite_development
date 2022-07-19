package edu.cmu.hcii.sugilite.pumice.kb;
import android.content.Context;
import android.support.annotation.Nullable;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.automation.AutomatorUtil;
import edu.cmu.hcii.sugilite.model.block.SugiliteStartingBlock;
import edu.cmu.hcii.sugilite.model.variable.Variable;
import edu.cmu.hcii.sugilite.model.variable.VariableValue;
import edu.cmu.hcii.sugilite.pumice.PumiceDemonstrationUtil;
import edu.cmu.hcii.sugilite.pumice.communication.SkipPumiceJSONSerialization;
//import edu.cmu.hcii.sugilite.sovite.SoviteAppNameAppInfoManager;
/**
 * @author toby
 * @date 10/29/18
 * @time 11:54 AM
 */
public class PumiceProceduralKnowledge implements Serializable {
    private String procedureName;
    private String utterance;

    //this map is null for "redirecting" procedure knowledge
    @Nullable
    private Map<String, PumiceProceduralKnowledgeParameter> parameterNameParameterMap;

    //point to another PumiceProceduralKnowledge by its procedureName
    private String targetProcedureKnowledgeName;

    //this is null for "redirecting" procedure knowledge
    //the procedure itself -- not really used -> SHOULD be null if targetProcedureKnowledgeName is non-null
    //not serialized for GSON
    @Nullable
    @SkipPumiceJSONSerialization
    private SugiliteStartingBlock sugiliteStartingBlock;

    //the name of sugiliteStartingBlock -> SHOULD be null if targetProcedureKnowledgeName is non-null for "redirecting" procedure knowledge
    @Nullable
    private String scriptName;

    //the list of involvedAppNames -> SHOULD be null if targetProcedureKnowledgeName is non-null for "redirecting" procedure knowledge
    @Nullable
    private List<String> involvedAppNames;


    @SkipPumiceJSONSerialization
    public boolean isNewlyLearned = true;

    public PumiceProceduralKnowledge(){

    }

    /**
     * constructor used to build a "redirecting" procedural knowledge without an actual script attached
     * @param procedureName
     * @param utterance
     * @param targetProcedureKnowledgeName
     */
    public PumiceProceduralKnowledge(String procedureName, String utterance, String targetProcedureKnowledgeName, @Nullable List<String> involvedAppNames){
        this.procedureName = procedureName;
        this.utterance = utterance;
        this.involvedAppNames = involvedAppNames;
        this.parameterNameParameterMap = null;
        this.sugiliteStartingBlock = null;
        this.scriptName = null;
        this.targetProcedureKnowledgeName = targetProcedureKnowledgeName;
    }

    /**
     * constructor used to build a "terminal" procedural knowledge with an actual script attached
     * @param context
     * @param procedureName
     * @param utterance
     * @param startingBlock
     */
    public PumiceProceduralKnowledge(Context context, String procedureName, String utterance, SugiliteStartingBlock startingBlock){
        this.procedureName = procedureName;
        this.utterance = utterance;
        this.involvedAppNames = new ArrayList<>();
        this.parameterNameParameterMap = new HashMap<>();
        this.sugiliteStartingBlock = startingBlock;
        this.scriptName = sugiliteStartingBlock.getScriptName();

        //populate involvedAppNames
        Set<String> involvedAppPackageNames = new HashSet<>();
        for(String packageName : startingBlock.relevantPackages){
            if (!AutomatorUtil.isHomeScreenPackage(packageName)){
                involvedAppPackageNames.add(packageName);
            }
        }

//        SoviteAppNameAppInfoManager soviteAppNameAppInfoManager = SoviteAppNameAppInfoManager.getInstance(SugiliteData.getAppContext());
//        for(String packageName : involvedAppPackageNames){
//            //get app name for package name
//            involvedAppNames.add(soviteAppNameAppInfoManager.getReadableAppNameForPackageName(packageName));
//        }
        //populate parameterNameParameterMap
        if(startingBlock.variableNameDefaultValueMap != null) {
            for (Map.Entry<String, Variable> variableNameVariableObject : startingBlock.variableNameVariableObjectMap.entrySet()) {
                if (variableNameVariableObject.getValue().getVariableType() == Variable.USER_INPUT){
                    //only deal with USER_INPUT type of parameters

                    VariableValue defaultValueObject = startingBlock.variableNameDefaultValueMap.get(variableNameVariableObject.getKey());
                    String parameterName = defaultValueObject.getVariableName();
                    String defaultValue = defaultValueObject.getVariableValue().toString();
                    List<String> alternativeValues = new ArrayList<>();
                    if (startingBlock.variableNameAlternativeValueMap.containsKey(parameterName)){
                        Set<VariableValue> alternativeValueObjcets = startingBlock.variableNameAlternativeValueMap.get(parameterName);
                        alternativeValueObjcets.forEach(alternativeValue -> alternativeValues.add(alternativeValue.getVariableValue().toString()));
                    }
                    parameterNameParameterMap.put(parameterName, new PumiceProceduralKnowledgeParameter(parameterName, defaultValue, alternativeValues));
                }
            }
        }
    }

    public void copyFrom(PumiceProceduralKnowledge pumiceProceduralKnowledge){
        this.procedureName = pumiceProceduralKnowledge.procedureName;
        this.utterance = pumiceProceduralKnowledge.utterance;
        this.involvedAppNames = pumiceProceduralKnowledge.involvedAppNames;
        this.parameterNameParameterMap = pumiceProceduralKnowledge.parameterNameParameterMap;
        this.sugiliteStartingBlock = pumiceProceduralKnowledge.sugiliteStartingBlock;
        this.targetProcedureKnowledgeName = pumiceProceduralKnowledge.targetProcedureKnowledgeName;
        this.scriptName = pumiceProceduralKnowledge.scriptName;
        this.isNewlyLearned = pumiceProceduralKnowledge.isNewlyLearned;
    }

    public void addParameter(PumiceProceduralKnowledgeParameter parameter){
        if (parameterNameParameterMap == null){
            parameterNameParameterMap = new HashMap<String, PumiceProceduralKnowledgeParameter>();
        }
        parameterNameParameterMap.put(parameter.parameterName, parameter);
    }

    public void addParameters(Collection<PumiceProceduralKnowledgeParameter> parameters){
        for(PumiceProceduralKnowledgeParameter parameter : parameters){
            addParameter(parameter);
        }
    }

    public void setProcedureName(String procedureName) {
        this.procedureName = procedureName;
    }


    public String getTargetProcedureKnowledgeName(){
        return targetProcedureKnowledgeName;
    }

    /**
     * used for getting the real targetScriptName for execution
     * @param knowledgeManager
     * @return
     */
    public String getTargetScriptName(PumiceKnowledgeManager knowledgeManager){
        if (scriptName != null) {
            return scriptName;
        } else if (targetProcedureKnowledgeName != null && (!targetProcedureKnowledgeName.equals(procedureName))) {
            //look up in PumiceKnowledgeManager
            for(PumiceProceduralKnowledge proceduralKnowledge : knowledgeManager.getPumiceProceduralKnowledges()){
                if (targetProcedureKnowledgeName.equals(proceduralKnowledge.procedureName)){
                    return proceduralKnowledge.getTargetScriptName(knowledgeManager);
                }
            }
        } else {
            throw new RuntimeException("not valid scriptName or targetProcedureKnowledgeName");
        }
        throw new RuntimeException("can't find the target procedureKnowledge");
    }

    /**
     * used for changing the real targetScriptName for execution
     * @param knowledgeManager
     * @param newScriptName
     * @return
     */
    public void changeTargetScriptName(PumiceKnowledgeManager knowledgeManager, String newScriptName) {
        if (scriptName != null) {
            this.scriptName = newScriptName;
            return;
        } else if (targetProcedureKnowledgeName != null && (!targetProcedureKnowledgeName.equals(procedureName))) {
            //look up in PumiceKnowledgeManager
            for(PumiceProceduralKnowledge proceduralKnowledge : knowledgeManager.getPumiceProceduralKnowledges()){
                if (targetProcedureKnowledgeName.equals(proceduralKnowledge.procedureName)){
                   proceduralKnowledge.changeTargetScriptName(knowledgeManager, newScriptName);
                   return;
                }
            }
        } else {
            throw new RuntimeException("not valid scriptName or targetProcedureKnowledgeName");
        }
        throw new RuntimeException("can't find the target procedureKnowledge");
    }


    /**
     * used for getting the real involved app names for execution
     * @param knowledgeManager
     * @return
     */
    public List<String> getInvolvedAppNames(PumiceKnowledgeManager knowledgeManager){
        if (involvedAppNames != null) {
            return involvedAppNames;
        } else if (targetProcedureKnowledgeName != null && (!targetProcedureKnowledgeName.equals(procedureName))) {
            //look up in PumiceKnowledgeManager
            for(PumiceProceduralKnowledge proceduralKnowledge : knowledgeManager.getPumiceProceduralKnowledges()){
                if (targetProcedureKnowledgeName.equals(proceduralKnowledge.procedureName)){
                    return proceduralKnowledge.getInvolvedAppNames(knowledgeManager);
                }
            }
        } else {
            throw new RuntimeException("not valid scriptName or targetProcedureKnowledgeName");
        }
        throw new RuntimeException("can't find the target procedureKnowledge");
    }


    /**
     * used for getting the real parameters for execution
     * @param knowledgeManager
     * @return
     */
    public Map<String, PumiceProceduralKnowledgeParameter> getParameterNameParameterMap(PumiceKnowledgeManager knowledgeManager){
        if (parameterNameParameterMap != null) {
            return parameterNameParameterMap;
        } else if (targetProcedureKnowledgeName != null && (!targetProcedureKnowledgeName.equals(procedureName))) {
            //look up in PumiceKnowledgeManager
            for(PumiceProceduralKnowledge proceduralKnowledge : knowledgeManager.getPumiceProceduralKnowledges()){
                if (targetProcedureKnowledgeName.equals(proceduralKnowledge.procedureName)){
                    return proceduralKnowledge.getParameterNameParameterMap(knowledgeManager);
                }
            }
        } else {
            throw new RuntimeException("not valid scriptName or targetProcedureKnowledgeName");
        }
        throw new RuntimeException("can't find the target procedureKnowledge");
    }

    public Map<String, String> getParameterNameParameterDefaultValueMap(PumiceKnowledgeManager knowledgeManager){
        Map<String, String> parameterNameParameterDefaultValueMap = new HashMap<>();
        if (parameterNameParameterMap != null) {
            for (Map.Entry<String, PumiceProceduralKnowledgeParameter> parameterNameParameterEntry : parameterNameParameterMap.entrySet()) {
                parameterNameParameterDefaultValueMap.put(parameterNameParameterEntry.getKey(), parameterNameParameterEntry.getValue().parameterDefaultValue.toString());
            }
            return parameterNameParameterDefaultValueMap;
        } else if (targetProcedureKnowledgeName != null && (!targetProcedureKnowledgeName.equals(procedureName))) {
            //look up in PumiceKnowledgeManager
            for(PumiceProceduralKnowledge proceduralKnowledge : knowledgeManager.getPumiceProceduralKnowledges()){
                if (targetProcedureKnowledgeName.equals(proceduralKnowledge.procedureName)){
                    Map<String, PumiceProceduralKnowledgeParameter> newParameterNameParameterMap = proceduralKnowledge.getParameterNameParameterMap(knowledgeManager);
                    if (newParameterNameParameterMap != null) {
                        for (Map.Entry<String, PumiceProceduralKnowledgeParameter> parameterNameParameterEntry : newParameterNameParameterMap.entrySet()) {
                            parameterNameParameterDefaultValueMap.put(parameterNameParameterEntry.getKey(), parameterNameParameterEntry.getValue().parameterDefaultValue.toString());
                        }
                        return parameterNameParameterDefaultValueMap;
                    }
                }
            }
        } else {
            throw new RuntimeException("not valid scriptName or targetProcedureKnowledgeName");
        }
        throw new RuntimeException("can't find the target procedureKnowledge");
    }

    public String getProcedureDescription(PumiceKnowledgeManager knowledgeManager, boolean addHowTo){
        String parameterizedUtterance = new String(utterance);
        Map<String, PumiceProceduralKnowledgeParameter> parameterNameParameterMap = getParameterNameParameterMap(knowledgeManager);
        Map<String, String> parameterNameParameterDefaultValueMap = getParameterNameParameterDefaultValueMap(knowledgeManager);
        if (parameterNameParameterMap != null) {
            for (String parameterName : parameterNameParameterMap.keySet()) {
                String parameterDefaultValue = parameterNameParameterDefaultValueMap.get(parameterName);
                if (parameterDefaultValue != null) {
                    if (Pattern.compile(String.format("[^\\[]+\"%s\"", parameterDefaultValue.toLowerCase())).matcher(parameterizedUtterance.toLowerCase()).find()) {
                        parameterizedUtterance = parameterizedUtterance.toLowerCase().replace(parameterDefaultValue.toLowerCase(), "[" + parameterName + "]");
                    }
                }
            }
        }
        if (targetProcedureKnowledgeName != null)     {
            parameterizedUtterance = parameterizedUtterance + ", which is to " + targetProcedureKnowledgeName;
        }
        else if (involvedAppNames != null && involvedAppNames.size() > 0) {
            parameterizedUtterance = parameterizedUtterance + " in " + PumiceDemonstrationUtil.joinListGrammatically(involvedAppNames, "and");
        }

        if (addHowTo) {
            return "How to " + parameterizedUtterance;
        } else {
            return parameterizedUtterance;
        }
    }

    public void setUtterance(String utterance) {
        this.utterance = utterance;
    }


    public String getProcedureName() {
        return procedureName;
    }

    public static class PumiceProceduralKnowledgeParameter<T> implements Serializable {
        //T currently supports numerical and String
        private String parameterName;
        private T parameterDefaultValue;
        private List<T> parameterAlternativeValues;

        public PumiceProceduralKnowledgeParameter(String parameterName, T parameterDefaultValue){
            this(parameterName, parameterDefaultValue, null);
        }

        public PumiceProceduralKnowledgeParameter(String parameterName, T parameterDefaultValue, List<T> parameterAlternativeValues){
            this.parameterName = parameterName;
            this.parameterDefaultValue = parameterDefaultValue;
            this.parameterAlternativeValues = new ArrayList<>();
            this.parameterAlternativeValues = parameterAlternativeValues;
        }

        public void addParameterAlternativeValues (T value){
            parameterAlternativeValues.add(value);
        }

        public String getParameterName() {
            return parameterName;
        }

        public void setParameterName(String parameterName) {
            this.parameterName = parameterName;
        }

        public T getParameterDefaultValue() {
            return parameterDefaultValue;
        }

        public List<T> getParameterAlternativeValues() {
            return parameterAlternativeValues;
        }
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

    @Nullable
    public Map<String, PumiceProceduralKnowledgeParameter> getParameterNameParameterMap() {
        return parameterNameParameterMap;
    }
}

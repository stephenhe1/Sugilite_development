package edu.cmu.hcii.sugilite.pumice.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.cmu.hcii.sugilite.Const;
import edu.cmu.hcii.sugilite.R;
import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.automation.ServiceStatusManager;

import edu.cmu.hcii.sugilite.pumice.communication.SkipPumiceJSONSerialization;
import edu.cmu.hcii.sugilite.pumice.dao.PumiceKnowledgeDao;

import edu.cmu.hcii.sugilite.pumice.dialog.intent_handler.PumiceDefaultUtteranceIntentHandler;

import edu.cmu.hcii.sugilite.pumice.dialog.intent_handler.PumiceUtteranceIntentHandler;

import edu.cmu.hcii.sugilite.pumice.kb.PumiceKnowledgeManager;
import edu.cmu.hcii.sugilite.pumice.ui.PumiceDialogActivity;
import edu.cmu.hcii.sugilite.pumice.ui.util.PumiceDialogUIHelper;
//import edu.cmu.hcii.sugilite.sovite.conversation_state.SoviteConversationState;
//import edu.cmu.hcii.sugilite.sovite.conversation_state.SoviteSerializableRecoverableIntentHanlder;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.server_comm.SugiliteVerbalInstructionHTTPQueryManager;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.speech.SugiliteVoiceRecognitionListener;

import static edu.cmu.hcii.sugilite.pumice.dialog.PumiceDialogManager.Sender.USER;

/**
 * @author toby
 * @date 10/9/18
 * @time 3:56 PM
 */
public class PumiceDialogManager{
    public enum Sender {AGENT, USER}
    private Activity context;
    private PumiceDialogView pumiceDialogView;
    private PumiceDialogUIHelper pumiceDialogUIHelper;
    private View speakButtonForCallback;
    private PumiceInitInstructionParsingHandler pumiceInitInstructionParsingHandler;
    private SugiliteVoiceRecognitionListener sugiliteVoiceRecognitionListener;
    private SugiliteVerbalInstructionHTTPQueryManager httpQueryManager;
    private SharedPreferences sharedPreferences;
    private ExecutorService executorService;
    private SugiliteData sugiliteData;
    private ServiceStatusManager serviceStatusManager;
    private PumiceKnowledgeDao pumiceKnowledgeDao;

    private List<PumiceDialogState> stateHistoryList;


    //TODO: need to add a structure to represent undo

    //represents the current state of the dialog
    private PumiceDialogState pumiceDialogState;

    public PumiceDialogManager(Activity context, boolean toLoadKB){
        this.context = context;
        this.pumiceDialogView = new PumiceDialogView(context);
        this.pumiceDialogUIHelper = new PumiceDialogUIHelper(context);
        this.sugiliteData = (SugiliteData)(context.getApplication());
        this.pumiceInitInstructionParsingHandler = new PumiceInitInstructionParsingHandler(context, this, sugiliteData);
        this.stateHistoryList = new ArrayList<>();
        this.pumiceKnowledgeDao = new PumiceKnowledgeDao(context, sugiliteData);
        try {
            // set "toAddDefaultContentForNewInstance" to true for testing purpose
            PumiceKnowledgeManager pumiceKnowledgeManager;
            if (toLoadKB) {
                pumiceKnowledgeManager = pumiceKnowledgeDao.getPumiceKnowledgeOrANewInstanceIfNotAvailable(false, true);
            } else {
                pumiceKnowledgeManager = new PumiceKnowledgeManager();
            }
            this.pumiceDialogState = new PumiceDialogState(new PumiceDefaultUtteranceIntentHandler(this, context, sugiliteData), pumiceKnowledgeManager);

        } catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException("failed to initiate/load the knowledge manager");
        }
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.httpQueryManager = SugiliteVerbalInstructionHTTPQueryManager.getInstance(context);
        this.executorService = Executors.newFixedThreadPool(Const.UI_SNAPSHOT_TEXT_PARSING_THREAD_COUNT);
        this.serviceStatusManager = ServiceStatusManager.getInstance(context);
        this.sugiliteData.pumiceDialogManager = this;
    }

    public void sendUserMessage(String message){
        //send the user message with the current in use intent handler
        sendUserMessage(message, pumiceDialogState.getPumiceUtteranceIntentHandlerInUse());
    }

    /**
     * used when the user's message needs a different intent handler than the one currently in use
     * @param message
     * @param pumiceUtteranceIntentHandler
     */
    private void sendUserMessage(String message, PumiceUtteranceIntentHandler pumiceUtteranceIntentHandler){
        updateUtteranceIntentHandlerInANewState(pumiceUtteranceIntentHandler);
        // ** finished saving the current PumiceDialogState **

        PumiceUtterance utterance = new PumiceUtterance(USER, message, Calendar.getInstance().getTimeInMillis(), true,false);
        pumiceDialogState.getUtteranceHistory().add(utterance);
        pumiceDialogView.addMessage(utterance);

        //classify the intent of user message
        PumiceUtteranceIntentHandler.PumiceIntent intent = pumiceUtteranceIntentHandler.detectIntentFromUtterance(utterance);

        //handle the incoming user message based on the identified intent
        pumiceUtteranceIntentHandler.handleIntentWithUtterance(this, intent, utterance);

    }

    public void updateUtteranceIntentHandlerInANewState(PumiceUtteranceIntentHandler pumiceUtteranceIntentHandler){
        //save the current PumiceDialogState to the deque and get a new one
        stateHistoryList.add(pumiceDialogState);
        pumiceDialogState = pumiceDialogState.getDuplicateWithNewIntentHandler(context, pumiceUtteranceIntentHandler);
        if(stateHistoryList.size() >= 1){
            pumiceDialogState.setPreviousState(stateHistoryList.get(stateHistoryList.size() - 1));
        }
    }

    /**
     * send a message from the agent that contains a view -- add the alt-text to the utterance history
     * @param viewContent
     * @param altText
     * @param isSpokenMessage
     * @param requireUserResponse
     */
    public void sendAgentViewMessage(View viewContent, Sender sender, String altText, boolean isSpokenMessage, boolean requireUserResponse){
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (context instanceof PumiceDialogActivity) {
                    ((PumiceDialogActivity) context).clearUserTextBox();
                }
                PumiceUtterance utterance = new PumiceUtterance(sender, "[CARD]" + altText, Calendar.getInstance().getTimeInMillis(), isSpokenMessage, requireUserResponse);
                pumiceDialogState.getUtteranceHistory().add(utterance);
                pumiceDialogView.addMessage(viewContent, sender);
                handleSpeakingAndUserResponse(altText, isSpokenMessage, requireUserResponse);
            }
        });
    }
    public void sendAgentViewMessage(View viewContent, String altText, boolean isSpokenMessage, boolean requireUserResponse){
        sendAgentViewMessage(viewContent, Sender.AGENT, altText, isSpokenMessage, requireUserResponse);
    }


    /**
     * send a message from the agent that contains a string -- add the alttext to the utterance history
     * @param message
     * @param isSpokenMessage
     * @param requireUserResponse
     */
    public void sendAgentMessage(String message, boolean isSpokenMessage, boolean requireUserResponse){
        sendAgentMessage(new SpannableString(message), isSpokenMessage, requireUserResponse);
    }

    /**
     * send a message from the agent that contains a Spanned -- add the alttext to the utterance history
     * @param message
     * @param isSpokenMessage
     * @param requireUserResponse
     */
    public void sendAgentMessage(CharSequence message, boolean isSpokenMessage, boolean requireUserResponse){
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (context instanceof PumiceDialogActivity) {
                    ((PumiceDialogActivity) context).clearUserTextBox();
                }
                PumiceUtterance utterance = new PumiceUtterance(Sender.AGENT, message, Calendar.getInstance().getTimeInMillis(), isSpokenMessage, requireUserResponse);
                pumiceDialogState.getUtteranceHistory().add(utterance);
                pumiceDialogView.addMessage(utterance);
                handleSpeakingAndUserResponse(message.toString(), isSpokenMessage, requireUserResponse);
            }
        });
    }

    public void revertToLastState(){
        if(pumiceDialogState.getPreviousState() != null && pumiceDialogState.getPreviousState().getPreviousState() != null) {
            revertToState(pumiceDialogState.getPreviousState().getPreviousState());
        } else {
            sendAgentMessage("Can't undo, already at the start of the conversation", true, false);
        }
    }

    /**
     * revert to a state by creating a DUPLICATE of the state and add it to the end of the state list
     * @param state
     */
    public void revertToState(PumiceDialogState state){
        if(pumiceDialogState.getUtteranceHistory().size() >= 1) {
            sendAgentMessage("Going back to a previous state...", true, false);

            this.pumiceDialogState = state.getDuplicateWithNewIntentHandler(context, state.getPumiceUtteranceIntentHandlerInUse());
            this.pumiceDialogState.setPreviousState(state.getPreviousState());

            //the last utterance should be the last one from the agent before the user say something..
            PumiceUtterance lastUtterance = getLastAgentPromptUtterance(pumiceDialogState.utteranceHistory);
            if (lastUtterance != null && lastUtterance.getSender().equals(Sender.AGENT)) {
                pumiceDialogState.getUtteranceHistory().remove(pumiceDialogState.getUtteranceHistory().size() - 1);
                sendAgentMessage(lastUtterance.getContent(), lastUtterance.isSpoken(), lastUtterance.isRequireUserResponse());
            }
        } else {
            sendAgentMessage("Can't undo, already at the start of the conversation", true, false);
        }
    }

    private PumiceUtterance getLastAgentPromptUtterance(List<PumiceUtterance> history){
        for (int i = history.size() - 1; i > 0 ; i --) {
            if (history.get(i).getSender().equals(USER) && history.get(i - 1).getSender().equals(Sender.AGENT)) {
                return history.get(i - 1);
            }
        }

        return null;
    }

    public void startOverState(){
        if(stateHistoryList.size() > 0 && stateHistoryList.get(0) != null){
            revertToState(stateHistoryList.get(0));
        }
        callSendPromptForTheIntentHandlerForCurrentIntentHandler();
    }

    public Activity getContext() {
        return context;
    }

    public PumiceKnowledgeManager getPumiceKnowledgeManager() {
        return pumiceDialogState.getPumiceKnowledgeManager();
    }

    public void setPumiceKnowledgeManager(PumiceKnowledgeManager pumiceKnowledgeManager) {
        try {
            pumiceKnowledgeDao.savePumiceKnowledge(pumiceKnowledgeManager);
            pumiceDialogState.setPumiceKnowledgeManager(pumiceKnowledgeManager);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSpeakingAndUserResponse(String utterance, boolean isSpokenMessage, boolean requireUserResponse){
        if(isSpokenMessage && sugiliteVoiceRecognitionListener != null) {
            sugiliteVoiceRecognitionListener.speak(utterance, String.valueOf(Calendar.getInstance().getTimeInMillis()), new Runnable() {
                @Override
                public void run() {
                    if(requireUserResponse && speakButtonForCallback != null){
                        pumiceDialogView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                //replace this line to scroll up or down
                                try {
                                    speakButtonForCallback.callOnClick();
                                } catch (Exception e){
                                    e.printStackTrace();
                                }
                            }
                        }, 500L);
                    }
                }
            });
        } else {
            if(requireUserResponse && speakButtonForCallback != null){
                pumiceDialogView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //replace this line to scroll up or down
                        speakButtonForCallback.callOnClick();
                    }
                }, 500L);
            }
        }

    }

    public PumiceDialogView getPumiceDialogView() {
        return pumiceDialogView;
    }

    public void setSpeakButtonForCallback(View speakButtonForCallback) {
        this.speakButtonForCallback = speakButtonForCallback;
    }

    public void setSugiliteVoiceRecognitionListener(SugiliteVoiceRecognitionListener sugiliteVoiceRecognitionListener) {
        this.sugiliteVoiceRecognitionListener = sugiliteVoiceRecognitionListener;
    }



    public class PumiceDialogView extends LinearLayout {
        public PumiceDialogView(Context context){
            super(context);
            this.setOrientation(VERTICAL);
            //this.setGravity(Gravity.BOTTOM);
        }

        public void addMessage(PumiceUtterance utterance){
            View view = pumiceDialogUIHelper.getDialogLayout(utterance);
            this.addView(view);
            ScrollView mScrollView = (ScrollView) context.findViewById(R.id.pumice_dialog_scrollLayout);
            if(mScrollView != null) {
                mScrollView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //replace this line to scroll up or down
                        mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                }, 1000L);
            }
        }

        public void addMessage(View contentView, Sender sender){
            View view = pumiceDialogUIHelper.getDialogLayout(contentView, sender);
            this.addView(view);
            ScrollView mScrollView = (ScrollView) context.findViewById(R.id.pumice_dialog_scrollLayout);
            if(mScrollView != null) {
                mScrollView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //replace this line to scroll up or down
                        mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                }, 1000L);
            }
        }

        public void clearMessage(){
            this.removeAllViews();
        }
    }

    public void callSendPromptForTheIntentHandlerForCurrentIntentHandler(){
        if (pumiceDialogState != null && pumiceDialogState.getPumiceUtteranceIntentHandlerInUse() != null) {
            pumiceDialogState.getPumiceUtteranceIntentHandlerInUse().sendPromptForTheIntentHandler();
        }
    }

    public class PumiceDialogState implements Serializable {
        private List<PumiceUtterance> utteranceHistory;
        private transient PumiceUtteranceIntentHandler pumiceUtteranceIntentHandlerInUse;
        private PumiceKnowledgeManager pumiceKnowledgeManager;
        private PumiceDialogState previousState;


        public PumiceDialogState (List<PumiceUtterance> utteranceHistory, PumiceUtteranceIntentHandler pumiceUtteranceIntentHandler, PumiceKnowledgeManager pumiceKnowledgeManager){
            this.utteranceHistory = utteranceHistory;
            this.pumiceUtteranceIntentHandlerInUse = pumiceUtteranceIntentHandler;
            this.pumiceKnowledgeManager = pumiceKnowledgeManager;
        }

        public PumiceDialogState(PumiceUtteranceIntentHandler pumiceUtteranceIntentHandler, PumiceKnowledgeManager pumiceKnowledgeManager){
            this.utteranceHistory = new ArrayList<>();
            this.pumiceUtteranceIntentHandlerInUse = pumiceUtteranceIntentHandler;
            this.pumiceKnowledgeManager = pumiceKnowledgeManager;
        }

        public PumiceKnowledgeManager getPumiceKnowledgeManager() {
            return pumiceKnowledgeManager;
        }

        public void setPumiceKnowledgeManager(PumiceKnowledgeManager pumiceKnowledgeManager) {
            this.pumiceKnowledgeManager = pumiceKnowledgeManager;
        }

        public List<PumiceUtterance> getUtteranceHistory() {
            return utteranceHistory;
        }

        public PumiceUtteranceIntentHandler getPumiceUtteranceIntentHandlerInUse() {
            return pumiceUtteranceIntentHandlerInUse;
        }

        public void setPumiceUtteranceIntentHandlerInUse(PumiceUtteranceIntentHandler pumiceUtteranceIntentHandlerInUse) {
            this.pumiceUtteranceIntentHandlerInUse = pumiceUtteranceIntentHandlerInUse;
        }

        public void setPreviousState(PumiceDialogState previousState) {
            this.previousState = previousState;
        }

        public PumiceDialogState getPreviousState() {
            return previousState;
        }

        PumiceDialogState getDuplicateWithNewIntentHandler(Context context, PumiceUtteranceIntentHandler intentHandler){
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
            List<PumiceUtterance> newUtteranceHistory =  new ArrayList<>(utteranceHistory);
            //PumiceKnowledgeManager newPumiceKnowledgeManager = gson.fromJson(gson.toJson(pumiceKnowledgeManager), PumiceKnowledgeManager.class);
            //TODO: duplicate the knowledge manager
            return new PumiceDialogState(newUtteranceHistory, intentHandler, pumiceKnowledgeManager);
        }
    }

    public void setPumiceUtteranceIntentHandlerInUse(PumiceUtteranceIntentHandler p) {
        this.pumiceDialogState.setPumiceUtteranceIntentHandlerInUse(p);
    }

    public SugiliteVerbalInstructionHTTPQueryManager getHttpQueryManager() {
        return httpQueryManager;
    }

    public void clearPumiceKnowledgeAndSaveToDao(){
        try {
            PumiceKnowledgeManager pumiceKnowledgeManager = new PumiceKnowledgeManager();
            pumiceKnowledgeDao.savePumiceKnowledge(pumiceKnowledgeManager);
            pumiceDialogState.setPumiceKnowledgeManager(pumiceKnowledgeManager);
        } catch (Exception e){
            throw new RuntimeException("failed to store the knowledge");
        }
    }

    public void savePumiceKnowledgeToDao(){
        try {
            pumiceKnowledgeDao.savePumiceKnowledge(getPumiceKnowledgeManager());
        } catch (Exception e){
            throw new RuntimeException("failed to store the knowledge");
        }
    }

    public SugiliteVoiceRecognitionListener getSugiliteVoiceRecognitionListener() {
        return sugiliteVoiceRecognitionListener;
    }

    public void stopTalking(){
        sugiliteVoiceRecognitionListener.stopTTS();
    }

    public void stopListening(){
        sugiliteVoiceRecognitionListener.stopListening();
    }


    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void runOnMainThread(Runnable r) {
        context.runOnUiThread(r);
    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public SugiliteData getSugiliteData() {
        return sugiliteData;
    }

    public ServiceStatusManager getServiceStatusManager() {
        return serviceStatusManager;
    }

    public PumiceInitInstructionParsingHandler getPumiceInitInstructionParsingHandler() {
        return pumiceInitInstructionParsingHandler;
    }

    public ScrollView getScrollView() {
        return (ScrollView) context.findViewById(R.id.pumice_dialog_scrollLayout);
    }

    public PumiceDialogState getPumiceDialogState() {
        return pumiceDialogState;
    }

//    public void loadSoviteConversationState(SoviteConversationState conversationState) {
//        List<PumiceUtterance> utterances = conversationState.getUtteranceHistory();
//        if (utterances.get(1) != null && utterances.get(1).getSender().equals(USER)) {
//            pumiceDialogView.addMessage(utterances.get(1));
//        }
//        SoviteSerializableRecoverableIntentHanlder intentHanlder = conversationState.getSoviteSerializableRecoverableIntentHanlder();
//        intentHanlder.inflateFromDeserializedInstance(context, this, sugiliteData, new PumiceDefaultUtteranceIntentHandler(this, context, sugiliteData));
//        this.updateUtteranceIntentHandlerInANewState(conversationState.getSoviteSerializableRecoverableIntentHanlder());
//        this.stateHistoryList.clear();
//
//        /*
//        for (PumiceUtterance utterance : utterances) {
//            //TODO: display those utterances
//            pumiceDialogView.addMessage(utterance);
//        }
//        */
//
//    }


}

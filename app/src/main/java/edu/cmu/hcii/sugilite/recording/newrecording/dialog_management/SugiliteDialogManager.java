package edu.cmu.hcii.sugilite.recording.newrecording.dialog_management;

import android.content.Context;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.speech.tts.TextToSpeech;
import android.widget.ImageButton;

import java.util.Calendar;
import java.util.List;

import edu.cmu.hcii.sugilite.Const;
import edu.cmu.hcii.sugilite.R;
import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.speech.SugiliteAndroidAPIVoiceRecognitionListener;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.speech.SugiliteGoogleCloudVoiceRecognitionListener;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.speech.SugiliteVoiceInterface;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.speech.SugiliteVoiceRecognitionListener;

import static edu.cmu.hcii.sugilite.Const.MUL_ZEROS;
import static edu.cmu.hcii.sugilite.Const.RECORDING_DARK_GRAY_COLOR;
import static edu.cmu.hcii.sugilite.Const.RECORDING_OFF_BUTTON_COLOR;
import static edu.cmu.hcii.sugilite.Const.RECORDING_ON_BUTTON_COLOR;
import static edu.cmu.hcii.sugilite.Const.RECORDING_SPEAKING_BUTTON_COLOR;
import static edu.cmu.hcii.sugilite.Const.RECORDING_WHITE_COLOR;

/**
 * @author toby
 * @date 2/20/18
 * @time 2:51 PM
 */
public abstract class SugiliteDialogManager implements SugiliteVoiceInterface {
    protected Context context;
    private SugiliteVoiceRecognitionListener sugiliteVoiceRecognitionListener;
    private boolean isListening = false;
    private boolean isSpeaking = false;
    private SugiliteDialogState currentState = null;
    protected AnimationDrawable speakingDrawable;
    protected AnimationDrawable listeningDrawable;
    protected Drawable notListeningDrawable;
    protected ImageButton speakButton = null;

    public SugiliteDialogManager(Context context, SugiliteData sugiliteData, SugiliteVoiceRecognitionListener sugiliteVoiceRecognitionListener) {
        this(context, sugiliteData, sugiliteVoiceRecognitionListener.getTTS());
    }
    public SugiliteDialogManager(Context context, SugiliteData sugiliteData, TextToSpeech tts) {
        this.context = context;
//        if (Const.SELECTED_SPEECH_RECOGNITION_TYPE == Const.SpeechRecognitionType.ANDROID) {
//            this.sugiliteVoiceRecognitionListener = new SugiliteAndroidAPIVoiceRecognitionListener(context, this, tts);
//        } else if (Const.SELECTED_SPEECH_RECOGNITION_TYPE == Const.SpeechRecognitionType.GOOGLE_CLOUD) {
//            this.sugiliteVoiceRecognitionListener = new SugiliteGoogleCloudVoiceRecognitionListener(context, sugiliteData, this, tts);
//        }
        initDrawables();
    }

    private void initDrawables() {
        //initiate the drawables for the icons on the speak button
        speakingDrawable = new AnimationDrawable();
        speakingDrawable.addFrame(context.getDrawable(R.mipmap.ic_speaker0), 500);
        speakingDrawable.addFrame(context.getDrawable(R.mipmap.ic_speaker1), 500);
        speakingDrawable.addFrame(context.getDrawable(R.mipmap.ic_speaker2), 500);
        speakingDrawable.addFrame(context.getDrawable(R.mipmap.ic_speaker3), 500);

        speakingDrawable.setOneShot(false);
        speakingDrawable.start();

        listeningDrawable = new AnimationDrawable();
        listeningDrawable.addFrame(context.getDrawable(R.mipmap.ic_tap_to_talk_0), 500);
        listeningDrawable.addFrame(context.getDrawable(R.mipmap.ic_tap_to_talk_1), 500);
        listeningDrawable.addFrame(context.getDrawable(R.mipmap.ic_tap_to_talk_2), 500);

        listeningDrawable.setOneShot(false);
        listeningDrawable.start();

        notListeningDrawable = context.getDrawable(R.mipmap.ic_tap_to_talk_0);
    }


    /**
     * play the prompt of currentState
     */
    public void initPrompt() {
        if (currentState.getPrompt() != null) {
            if(currentState.getOnInitiatedRunnable() != null) {
                currentState.getOnInitiatedRunnable().run();
            }
            speak(currentState.getPrompt(), currentState.getPromptOnPlayingDoneRunnable());
        }
    }

    /**
     * this class should initiate the dialog manager by creating the states, set the current state, and call initPrompt()
     */
    public abstract void initDialogManager();

    /**
     * stop both ASR listening and TTS speaking
     */
    public void stopASRandTTS() {
//        stopListening();
//        sugiliteVoiceRecognitionListener.stopTTS();
    }

    /**
     * refresh the color and icon for the speak button
     * @param speakButton
     */
    protected void refreshSpeakButtonStyle(ImageButton speakButton){
        if(speakButton != null) {
            if (isSpeaking()) {
                speakButton.setImageDrawable(speakingDrawable);
                speakButton.getBackground().setColorFilter(new LightingColorFilter(MUL_ZEROS, RECORDING_SPEAKING_BUTTON_COLOR));
                speakButton.getDrawable().setColorFilter(new LightingColorFilter(MUL_ZEROS, RECORDING_WHITE_COLOR));
                speakingDrawable.start();
                //dialog.getWindow().getDecorView().getBackground().setColorFilter(new LightingColorFilter(MUL_ZEROS, RECORDING_SPEAKING_ON_BACKGROUND_COLOR));
            } else {
                if (isListening()) {
                    speakButton.setImageDrawable(listeningDrawable);
                    speakButton.getBackground().setColorFilter(new LightingColorFilter(MUL_ZEROS, RECORDING_ON_BUTTON_COLOR));
                    speakButton.getDrawable().setColorFilter(new LightingColorFilter(MUL_ZEROS, RECORDING_WHITE_COLOR));
                } else {
                    speakButton.setImageDrawable(notListeningDrawable);
                    speakButton.getBackground().setColorFilter(new LightingColorFilter(MUL_ZEROS, RECORDING_OFF_BUTTON_COLOR));
                    speakButton.getDrawable().setColorFilter(new LightingColorFilter(MUL_ZEROS, RECORDING_DARK_GRAY_COLOR));
                }
            }
        }
    }

    /**
     * callback from ASR -> called when a set of ASR results is available
     * @param matches
     */
    @Override
    public void resultAvailableCallback(List<String> matches, boolean isFinal) {
        if (isFinal) {
            //fill in the ASR results for the current state
            currentState.setASRResult(matches);

            //invoke the on switched away runnable of the current state
            if (currentState.getOnSwitchedAwayRunnable() != null) {
                currentState.getOnSwitchedAwayRunnable().run();
            }

            //switch state
            currentState = currentState.getNextState(matches);

            if (currentState != null) {
                //invoke the on initiated runnable of the next state
                if (currentState.getOnInitiatedRunnable() != null) {
                    currentState.getOnInitiatedRunnable().run();
                }

                //play the prompt of the next state
                if (currentState.getPrompt() != null) {
                    speak(currentState.getPrompt(), currentState.getPromptOnPlayingDoneRunnable());
                }
            }
        }
    }

    @Override
    public void listeningStartedCallback() {
        isListening = true;
        refreshSpeakButtonStyle(speakButton);
    }

    @Override
    public void listeningEndedCallback() {
        isListening = false;
        refreshSpeakButtonStyle(speakButton);
    }

    @Override
    public void speakingStartedCallback() {
        isSpeaking = true;
        refreshSpeakButtonStyle(speakButton);
    }

    @Override
    public void speakingEndedCallback() {
        isSpeaking = false;
        refreshSpeakButtonStyle(speakButton);
    }

    public boolean isListening() {
        return isListening;
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    public void startListening() {
        sugiliteVoiceRecognitionListener.startListening();
    }

    public void stopListening() {
        sugiliteVoiceRecognitionListener.stopListening();
        listeningEndedCallback();
    }

    public void speak(String utterance, Runnable runnableOnDone) {
        if(sugiliteVoiceRecognitionListener != null) {
            sugiliteVoiceRecognitionListener.speak(utterance, String.valueOf(Calendar.getInstance().getTimeInMillis()), runnableOnDone);
        }
    }

    public SugiliteDialogState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(SugiliteDialogState currentState) {
        this.currentState = currentState;
    }

    public void setSpeakButton(ImageButton speakButton) {
        this.speakButton = speakButton;
    }

    public void onDestroy(){
        //sugiliteVoiceRecognitionListener.stopAllAndEndASRService();
    }

    public TextToSpeech getTTS(){
        return sugiliteVoiceRecognitionListener.getTTS();
    }
}

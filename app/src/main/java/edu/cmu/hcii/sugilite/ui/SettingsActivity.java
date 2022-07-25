package edu.cmu.hcii.sugilite.ui;


import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.support.v4.app.NavUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.stericson.RootTools.RootTools;

import java.util.List;

import edu.cmu.hcii.sugilite.Const;
import edu.cmu.hcii.sugilite.R;
import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.automation.ServiceStatusManager;
import edu.cmu.hcii.sugilite.communication.SugiliteBlockJSONProcessor;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptDao;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptFileDao;
import edu.cmu.hcii.sugilite.dao.SugiliteScriptSQLDao;
import edu.cmu.hcii.sugilite.dao.SugiliteTrackingDao;
import edu.cmu.hcii.sugilite.model.block.SugiliteStartingBlock;
import edu.cmu.hcii.sugilite.pumice.PumiceDemonstrationUtil;
import edu.cmu.hcii.sugilite.tracking.SugiliteTrackingHandler;

import static edu.cmu.hcii.sugilite.Const.SQL_SCRIPT_DAO;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {
    private static SharedPreferences prefs;
    private static ServiceStatusManager serviceStatusManager;
    private static SugiliteData sugiliteData;
    private static SugiliteScriptDao sugiliteScriptDao;
    private static SugiliteTrackingDao sugiliteTrackingDao;
    private static SugiliteTrackingHandler trackingHandler;
    private static SugiliteBlockJSONProcessor jsonProcessor;
    private static Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        serviceStatusManager = ServiceStatusManager.getInstance(this);
        sugiliteData = (SugiliteData)getApplication();
        if(Const.DAO_TO_USE == SQL_SCRIPT_DAO)
            sugiliteScriptDao = new SugiliteScriptSQLDao(this);
        else
            sugiliteScriptDao = new SugiliteScriptFileDao(this, sugiliteData);
        sugiliteTrackingDao = new SugiliteTrackingDao(this);
        trackingHandler = new SugiliteTrackingHandler(sugiliteData, this);
        jsonProcessor = new SugiliteBlockJSONProcessor(this);
        this.context = this;
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (!super.onMenuItemSelected(featureId, item)) {
                NavUtils.navigateUpFromSameTask(this);
            }
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }
    private static Preference.OnPreferenceChangeListener recordingInProgressPreferenceChangeListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object newValue) {
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(preference.getContext());
            switch (preference.getKey()) {
                case "recording_in_process":
                    //recording in progress status is changed
                    SwitchPreference recordingInProgressSwitch = (SwitchPreference) preference;
                    if (!recordingInProgressSwitch.isChecked()) {
                        sugiliteData.clearInstructionQueue();
                        AlertDialog.Builder builder = new AlertDialog.Builder(preference.getContext());
                        final EditText scriptName = new EditText(preference.getContext());
                        scriptName.setText(sugiliteScriptDao.getNextAvailableDefaultName());
                        scriptName.setSelectAllOnFocus(true);
                        builder.setMessage("Specify the name for your new script")
                                .setView(scriptName)
                                .setPositiveButton("Start Recording", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (!serviceStatusManager.isRunning()) {
                                            ((SwitchPreference) preference).setChecked(false);
                                            //prompt the user if the accessiblity service is not active
                                            AlertDialog.Builder builder1 = new AlertDialog.Builder(preference.getContext());
                                            builder1.setTitle("Service not running")
                                                    .setMessage("The Sugilite accessiblity service is not enabled. Please enable the service in the phone settings before recording.")
                                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            serviceStatusManager.promptEnabling();
                                                            //do nothing
                                                        }
                                                    }).show();
                                        } else if (scriptNamePreference != null && scriptName != null && scriptName.getText().toString().length() > 0) {
                                            scriptNamePreference.setText(scriptName.getText().toString());
                                            PumiceDemonstrationUtil.showSugiliteToast("Changed script name to " + sharedPreferences.getString("scriptName", "NULL"), Toast.LENGTH_SHORT);
                                            scriptNamePreference.setSummary(scriptName.getText().toString());
                                            //set the active script to the newly created script
                                            sugiliteData.initiateScriptRecording(PumiceDemonstrationUtil.addScriptExtension(scriptName.getText().toString()), null);
                                            sugiliteData.initiatedExternally = false;
                                            sugiliteData.setCurrentSystemState(SugiliteData.RECORDING_STATE);
                                            //save the newly created script to DB
                                            try {
                                                sugiliteScriptDao.save((SugiliteStartingBlock) sugiliteData.getScriptHead());
                                                sugiliteScriptDao.commitSave(null);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ((SwitchPreference) preference).setChecked(false);
                                    }
                                })
                                .setTitle("New Script");

                        final AlertDialog dialog = builder.create();
                        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                        dialog.show();

                        scriptName.setOnKeyListener(new View.OnKeyListener() {
                            public boolean onKey(View v, int keyCode, KeyEvent event) {
                                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                    switch (keyCode) {
                                        case KeyEvent.KEYCODE_DPAD_CENTER:
                                        case KeyEvent.KEYCODE_ENTER:
                                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                                            return true;
                                        default:
                                            break;
                                    }
                                }
                                return false;
                            }
                        });

                    }
                    else{
                        PumiceDemonstrationUtil.endRecording(context, sugiliteData, sharedPreferences, sugiliteScriptDao);
                    }
                    break;

                case "root_enabled":
                    final SwitchPreference rootEnabledSwitch = (SwitchPreference) preference;
                    if(!rootEnabledSwitch.isChecked()){
                        boolean rootEnabled = true;
                        AlertDialog.Builder builder = new AlertDialog.Builder(preference.getContext());
                        builder.setTitle("Root request failed").setMessage("Failed to get root access! Please check if your device has been rooted, or if the root access has been denied.");
                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                rootEnabledSwitch.setChecked(false);
                            }
                        });
                        //root access is enabled
                        try {
                            rootEnabled = RootTools.isAccessGiven();
                        }
                        catch (Exception e){
                            e.printStackTrace();
                            rootEnabled = false;
                            builder.show();
                            //do nothing
                        }
                        if(rootEnabled) {
                            PumiceDemonstrationUtil.showSugiliteToast("Root access is disabled", Toast.LENGTH_SHORT);
                        }
                        else {
                            builder.show();
                        }
                    }
                    else {
                        //root access is disabled
                        PumiceDemonstrationUtil.showSugiliteToast("Root access is disabled", Toast.LENGTH_SHORT);
                    }
                    break;

                case "tracking_in_process":
                    final SwitchPreference trackingEnabledSwitch = (SwitchPreference) preference;
                    if (!trackingEnabledSwitch.isChecked()) {
                        if (!serviceStatusManager.isRunning()) {
                            ((SwitchPreference) preference).setChecked(false);
                            //prompt the user if the accessiblity service is not active
                            AlertDialog.Builder builder1 = new AlertDialog.Builder(preference.getContext());
                            builder1.setTitle("Service not running")
                                    .setMessage("The Sugilite accessiblity service is not enabled. Please enable the service in the phone settings before recording.")
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            serviceStatusManager.promptEnabling();
                                            //do nothing
                                        }
                                    }).show();
                        }
                        else {
                            String name = trackingHandler.getDefaultTrackingName();
                            sugiliteData.initiateTracking(name);
                            try {
                                sugiliteTrackingDao.save(sugiliteData.getTrackingHead());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            AlertDialog.Builder builder2 = new AlertDialog.Builder(preference.getContext())
                                    .setMessage("Starting tracking " + name);
                            builder2.show();
                        }
                    }
            }
            return true;
        }

    };
    public void onRecordinginProgressSwitchChecked(){

    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || DataSyncPreferenceFragment.class.getName().equals(fragmentName)
                || NotificationPreferenceFragment.class.getName().equals(fragmentName);
    }
    private static EditTextPreference scriptNamePreference;

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);
            scriptNamePreference = (EditTextPreference)findPreference("scriptName");
            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("scriptName"));
            bindPreferenceSummaryToValue(findPreference("semantic_parsing_server_address"));
            bindPreferenceSummaryToValue(findPreference("bert_embedding_server_address"));

            findPreference("recording_in_process").setOnPreferenceChangeListener(recordingInProgressPreferenceChangeListener);
            findPreference("root_enabled").setOnPreferenceChangeListener(recordingInProgressPreferenceChangeListener);
            findPreference("tracking_in_process").setOnPreferenceChangeListener(recordingInProgressPreferenceChangeListener);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class NotificationPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_notification);
            setHasOptionsMenu(true);
            bindPreferenceSummaryToValue(findPreference("participantId"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows data and sync preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class DataSyncPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_data_sync);
            setHasOptionsMenu(true);
            bindPreferenceSummaryToValue(findPreference("remote_server_address"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}

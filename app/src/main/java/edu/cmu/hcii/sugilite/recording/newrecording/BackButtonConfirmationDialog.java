package edu.cmu.hcii.sugilite.recording.newrecording;

import static edu.cmu.hcii.sugilite.Const.OVERLAY_TYPE;

import android.accessibilityservice.AccessibilityService;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import edu.cmu.hcii.sugilite.R;
import edu.cmu.hcii.sugilite.accessibility_service.SugiliteAccessibilityService;
import edu.cmu.hcii.sugilite.recording.SugiliteScreenshotManager;

public class BackButtonConfirmationDialog {

    private Context context;
    private View dialogView;
    private TextView confirmationPromptTextView;
    private LayoutInflater layoutInflater;
    private Dialog dialog;
    private int operationType = -1;    // 0 Positive 1 Negative 2 Neutral

    public BackButtonConfirmationDialog(Context context, SugiliteScreenshotManager screenshotManager) {
        this.context = context;
        this.layoutInflater = LayoutInflater.from(context);
    }

    public void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        String prefix = "Press the back button";
        builder.setTitle("Save Operation Confirmation");

        dialogView = layoutInflater.inflate(R.layout.dialog_confirmation_popup_spoken, null);
        confirmationPromptTextView = (TextView) dialogView.findViewById(R.id.text_confirmation_prompt);
        if (confirmationPromptTextView != null) {
            SpannableStringBuilder text = new SpannableStringBuilder();
            text.append("Are you sure you want to record the operation: ");
            text.append(prefix);
            confirmationPromptTextView.setText(text);
        }
        builder.setView(dialogView);
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                operationType = 0;
                dialog.dismiss();
                SugiliteAccessibilityService sugiliteAccessibilityService = (SugiliteAccessibilityService) context;
                sugiliteAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                System.out.println("Performed the back button operation");
            }
        })
                .setNegativeButton("Skip", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        operationType = 1;
//                        SugiliteAccessibilityService sugiliteAccessibilityService = (SugiliteAccessibilityService) context;
//                        sugiliteAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                    }
                })
                .setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        operationType = 2;
                        dialog.dismiss();
                    }
                });
        dialog = builder.create();
        dialog.getWindow().setType(OVERLAY_TYPE);
        dialog.show();

    }

    public int getOperationType() {
        return operationType;
    }
}

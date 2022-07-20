package edu.cmu.hcii.sugilite.recording.newrecording.fullscreen_overlay;

import static android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cmu.hcii.sugilite.Const;
import edu.cmu.hcii.sugilite.model.Node;
import edu.cmu.hcii.sugilite.accessibility_service.SugiliteAccessibilityService;
import edu.cmu.hcii.sugilite.SugiliteData;
import edu.cmu.hcii.sugilite.model.block.util.SugiliteAvailableFeaturePack;
import edu.cmu.hcii.sugilite.model.block.SugiliteOperationBlock;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteClickOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteLongClickOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteUnaryOperation;
import edu.cmu.hcii.sugilite.ontology.*;
import edu.cmu.hcii.sugilite.pumice.PumiceDemonstrationUtil;
import edu.cmu.hcii.sugilite.recording.ReadableDescriptionGenerator;
import edu.cmu.hcii.sugilite.recording.SugiliteScreenshotManager;
import edu.cmu.hcii.sugilite.recording.TextChangedEventHandler;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.study.SugiliteStudyHandler;
import edu.cmu.hcii.sugilite.verbal_instruction_demo.util.NavigationBarUtil;

import static edu.cmu.hcii.sugilite.Const.OVERLAY_TYPE;

import org.apache.lucene.geo.Line;

/**
 * @author toby
 * @date 2/5/18
 * @time 3:46 PM
 */

//this class creates a full screen overlay over the screen
public class FullScreenRecordingOverlayManager {
    //map between overlays and node
    private Map<View, SugiliteEntity<Node>> overlayNodeMap;
    private Context context;
    private WindowManager windowManager;
    private NavigationBarUtil navigationBarUtil;
    private FullScreenRecordingOverlayManager recordingOverlayManager;
    private SugiliteData sugiliteData;
    private SharedPreferences sharedPreferences;
    private SugiliteFullScreenOverlayFactory overlayFactory;
    private View overlay;
    private DisplayMetrics displayMetrics;
    private ReadableDescriptionGenerator readableDescriptionGenerator;
    private SugiliteAccessibilityService sugiliteAccessibilityService;
    private SugiliteScreenshotManager sugiliteScreenshotManager;
    private TextToSpeech tts;
    private TextChangedEventHandler textChangedEventHandler;
    //whether overlays are currently shown
    private boolean showingOverlay = false;

    //latest UI snapshot
    private UISnapshot uiSnapshot = null;

    private int overlayCurrentHeight;
    private int overlayCurrentWidth;

    private int overlayCurrentFlag;

    public FullScreenRecordingOverlayManager(Context context, SugiliteData sugiliteData, SharedPreferences sharedPreferences, SugiliteAccessibilityService sugiliteAccessibilityService, TextToSpeech tts) {
        this.context = context;
        this.sugiliteAccessibilityService = sugiliteAccessibilityService;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.overlayNodeMap = new HashMap<>();
        this.navigationBarUtil = new NavigationBarUtil();
        this.sugiliteData = sugiliteData;
        this.sharedPreferences = sharedPreferences;
        this.overlayFactory = new SugiliteFullScreenOverlayFactory(context);
        this.recordingOverlayManager = this;
        this.readableDescriptionGenerator = new ReadableDescriptionGenerator(context);
        this.sugiliteScreenshotManager = SugiliteScreenshotManager.getInstance(sharedPreferences, sugiliteData);
        this.tts = tts;

//        displayMetrics = new DisplayMetrics();
//        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        displayMetrics = context.getResources().getDisplayMetrics();
        this.overlayCurrentHeight = displayMetrics.heightPixels;
        //hack -- leave 1px at the right end of the screen so the input method window becomes visible
        this.overlayCurrentWidth = displayMetrics.widthPixels - 1;
        this.overlayCurrentFlag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        initOverlay();
    }


    public boolean isShowingOverlay() {
        return showingOverlay;
    }


    public void updateRecordingOverlaySizeBasedOnInputMethod(boolean windowsContainsInputMethod, Rect inputMethodWindowBoundsInScreen, TextChangedEventHandler textChangedEventHandler) {
        this.textChangedEventHandler = textChangedEventHandler;
        SugiliteData.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                int newHeightPixels = displayMetrics.heightPixels - inputMethodWindowBoundsInScreen.height() - navigationBarUtil.getStatusBarHeight(context);
//                if (newHeightPixels > overlayCurrentHeight && windowsContainsInputMethod == false) {
//                    textChangedEventHandler.flush();
//                }
//                overlayCurrentHeight = newHeightPixels;
                if (overlay.isShown()) {
                    try {
                        windowManager.updateViewLayout(overlay, updateLayoutParams(overlayCurrentFlag, overlayCurrentWidth, overlayCurrentHeight));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }


    public void enableOverlay() {
        removeOverlays();
        //enable overlay
        overlayCurrentFlag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        WindowManager.LayoutParams layoutParams = updateLayoutParams(overlayCurrentFlag, overlayCurrentWidth, overlayCurrentHeight);

        //NEEDED TO BE CONFIGURED AT APPS->SETTINGS-DRAW OVER OTHER APPS on API>=23
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentApiVersion >= 23) {
            checkDrawOverlayPermission();
            System.out.println("ADDING OVERLAY TO WINDOW MANAGER");
            windowManager.addView(overlay, layoutParams);
        } else {
            windowManager.addView(overlay, layoutParams);
        }
        // set the listener
        setOverlayOnTouchListener(true);

        // set the flag
        showingOverlay = true;
    }

    public View getOverlay() {
        return overlay;
    }

    /**
     * remove all overlays from the window manager
     */
    public void removeOverlays() {
        try {
            if (overlay != null && overlay.getWindowToken() != null) {
                windowManager.removeView(overlay);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            showingOverlay = false;
        }
    }

    public void setUiSnapshot(UISnapshot uiSnapshot) {
        synchronized (this) {
            this.uiSnapshot = uiSnapshot;
        }
    }


    private void initOverlay() {
        overlay = overlayFactory.getFullScreenOverlay(displayMetrics);
    }

    private WindowManager.LayoutParams updateLayoutParams(int flag, int width, int height) {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                OVERLAY_TYPE,
                flag,
                PixelFormat.TRANSLUCENT);

        DisplayMetrics displaymetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displaymetrics);
        int real_y = 0;
        int statusBarHeight = navigationBarUtil.getStatusBarHeight(context);
        real_y -= statusBarHeight;

        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        layoutParams.x = 0;
        layoutParams.y = real_y;
        layoutParams.width = width;
        layoutParams.height = height;
        return layoutParams;
    }

    /**
     * set view to be not touchble (so it will pass through touch events)
     */
    private void setPassThroughOnTouchListener() {
        overlay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });
        overlay.setBackgroundColor(Const.RECORDING_OVERLAY_COLOR_STOP);
        overlay.invalidate();
        try {
            overlayCurrentFlag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            windowManager.updateViewLayout(overlay, updateLayoutParams(overlayCurrentFlag, overlayCurrentWidth, overlayCurrentHeight));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void setOverlayOnTouchListener(final boolean toConsumeEvent) {
        try {
            overlayCurrentFlag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(overlay, updateLayoutParams(overlayCurrentFlag, overlayCurrentWidth, overlayCurrentHeight));
            overlay.setBackgroundColor(Const.RECORDING_OVERLAY_COLOR);
            overlay.invalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }

        overlay.setOnTouchListener(new View.OnTouchListener() {
            GestureDetector myGestureDetector = new GestureDetector(context, new MyGestureDetector());


            @Override
            public boolean onTouch(final View v, MotionEvent event) {
                return myGestureDetector.onTouchEvent(event);
            }

            class MyGestureDetector extends GestureDetector.SimpleOnGestureListener {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent event) {
                    //single tap up detected
                    System.out.println("Single tap detected");
                    float rawX = event.getRawX();
                    float rawY = event.getRawY();
                    handleClick(rawX, rawY);
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent event) {
//                    System.out.println("Context click detected");
//                    float rawX = event.getRawX();
//                    float rawY = event.getRawY();
//                    handleContextClick(rawX, rawY, tts);
//                    return;
                }

                @Override
                public boolean onContextClick(MotionEvent e) {
                    System.out.println("Context click detected");
                    return super.onContextClick(e);
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (Const.ROOT_ENABLED) {
                        recordingOverlayManager.setPassThroughOnTouchListener();
                        try {
                            recordingOverlayManager.performFlingWithRootPermission(e1, e2, new Runnable() {
                                @Override
                                public void run() {
                                    //allow the overlay to get touch event after finishing the simulated gesture
                                    recordingOverlayManager.setOverlayOnTouchListener(true);
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return true;
                }
            }

            /*
            class Scroll extends GestureDetector.SimpleOnGestureListener {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    if (Const.ROOT_ENABLED) {
                        recordingOverlayManager.setPassThroughOnTouchListener(overlay);
                        try {
                            recordingOverlayManager.performFlingWithRootPermission(e1, e2, new Runnable() {
                                @Override
                                public void run() {
                                    //allow the overlay to get touch event after finishing the simulated gesture
                                    recordingOverlayManager.setOverlayOnTouchListener(overlay, true);
                                }
                            });
                        }
                        catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                    return true;
                }
            }
            */

        });
    }


    private UISnapshot getUiSnapshotAndAnnotateStringEntitiesIfNeeded() {
        synchronized (this) {
            //annotate string entities in the ui snapshot if needed
            if (uiSnapshot != null) {
                uiSnapshot.annotateStringEntitiesIfNeeded();
            }
            return uiSnapshot;
        }
    }


    private static List<SugiliteEntity<Node>> getMatchedNodesFromCoordinate(float x, float y, UISnapshot uiSnapshot, boolean getClickableNodeOnly, boolean getLongClickableNodeOnly) {
        List<SugiliteEntity<Node>> matchedNodeEntities = new ArrayList<>();
        if (uiSnapshot != null) {
            for (SugiliteEntity<Node> entity : uiSnapshot.getNodeSugiliteEntityMap().values()) {
                Node node = entity.getEntityValue();
                if (getClickableNodeOnly) {
                    if (!node.getClickable()) {
                        continue;
                    }
                }
                if (getLongClickableNodeOnly) {
                    if (!node.getLongClickable()) {
                        continue;
                    }
                }
                Rect boundingBox = Rect.unflattenFromString(node.getBoundsInScreen());
                if (boundingBox.contains((int) x, (int) y)) {
                    //contains
                    matchedNodeEntities.add(entity);
                }
            }
        }
        if (matchedNodeEntities.size() > 0) {
            matchedNodeEntities.sort(new Comparator<SugiliteEntity<Node>>() {
                //sort the list<node> based on Z-indexes, so the nodes displayed on top are in front
                @Override
                public int compare(SugiliteEntity<Node> e1, SugiliteEntity<Node> e2) {
                    Node o1 = e1.getEntityValue();
                    Node o2 = e2.getEntityValue();
                    if (o1.getWindowZIndex() != null && o2.getWindowZIndex() != null && (!o1.getWindowZIndex().equals(o2.getWindowZIndex()))) {
                        if (o1.getWindowZIndex() > o2.getWindowZIndex()) {
                            return -1;
                        } else {
                            return 1;
                        }
                    }
                    if (o1.getNodeZIndexSequence() != null && o2.getNodeZIndexSequence() != null) {
                        int length = o1.getNodeZIndexSequence().size() > o2.getNodeZIndexSequence().size() ? o1.getNodeZIndexSequence().size() : o2.getNodeZIndexSequence().size();
                        for (int i = 0; i < length; i++) {
                            if (i >= o1.getNodeZIndexSequence().size()) {
                                return 1;
                            }
                            if (i >= o2.getNodeZIndexSequence().size()) {
                                return -1;
                            }
                            if (o1.getNodeZIndexSequence().get(i) > o2.getNodeZIndexSequence().get(i)) {
                                return -1;
                            }
                            if (o1.getNodeZIndexSequence().get(i) < o2.getNodeZIndexSequence().get(i)) {
                                return 1;
                            }
                        }
                    }
                    return 0;
                }
            });
            //print matched nodes
            System.out.println("Matched " + matchedNodeEntities.size() + " objects!");

            //choose the top-layer matched node
            return matchedNodeEntities;
        } else {
            return null;
        }
    }


//    private File getLatestScreenshot() {
//        File latestScreenshot = sugiliteScreenshotManager.takeScreenshot(SugiliteScreenshotManager.DIRECTORY_PATH, sugiliteScreenshotManager.getFileNameFromDate());
//        return latestScreenshot;
//    }

    private void checkDrawOverlayPermission() {
        /* check if we already  have permission to draw over other apps */
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentApiVersion >= 23) {
            if (!Settings.canDrawOverlays(context)) {
                /* if not construct intent to request permission */
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                /* request permission via start activity for result */
                context.startActivity(intent);

            }
        }
    }


    /**
     * handle when the overlay detects a click at (x, y) -> should determine the UI object to match this click event to, and create an OverlayClickedDialog
     *
     * @param x
     * @param y
     */
    private void handleClick(float x, float y) {
        SugiliteEntity<Node> node = null;
        UISnapshot uiSnapshot = getUiSnapshotAndAnnotateStringEntitiesIfNeeded();
//        File screenshot = getLatestScreenshot();
        if (uiSnapshot != null) {
            List<SugiliteEntity<Node>> matchedNodeEntities = getMatchedNodesFromCoordinate(x, y, uiSnapshot, true, false);
            if (matchedNodeEntities != null) {
                node = matchedNodeEntities.get(0);
            }

        }
        if (node != null) {
            if (sugiliteAccessibilityService.getSugiliteStudyHandler().isToRecordNextOperation()) {
                //save a study packet
                Date time = Calendar.getInstance().getTime();
                String timeString = Const.dateFormat.format(time);
                String path = "/sdcard/Download/sugilite_study_packets";
                String fileName = "packet_" + timeString;
                try {
                    sugiliteScreenshotManager.takeScreenshotUsingShellCommand(true, path, fileName + ".png");
                } catch (Exception e) {
                    e.printStackTrace();
                }
//                SugiliteStudyHandler studyHandler = sugiliteAccessibilityService.getSugiliteStudyHandler();
//                studyHandler.handleEvent(new SugiliteAvailableFeaturePack(node, uiSnapshot, getLatestScreenshot()), uiSnapshot, path, fileName);
            } else {
                OverlayClickedDialog overlayClickedDialog = new OverlayClickedDialog(context, node, uiSnapshot, null, x, y, this, overlay, sugiliteData, sharedPreferences, tts, false);
                overlayClickedDialog.show();

                //flush the textChangedEventHandler
                if (textChangedEventHandler != null) {
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            //capture the screen
                            textChangedEventHandler.flush();
                        }
                    }, 500);
                }
            }
        } else {
            List<SugiliteEntity<Node>> matchedNodeEntities = getMatchedNodesFromCoordinate(x, y, uiSnapshot, false, false);
            node=matchedNodeEntities.get(0);
//            if (matchedNodeEntities.size() > 1) {
//                if (matchedNodeEntities.get(0).getEntityValue().getClickable()) {
//                    node = matchedNodeEntities.get(0);
//                } else if (matchedNodeEntities.get(0).getEntityValue().getClickable() == false && (null != matchedNodeEntities.get(0).getEntityValue().getText() || null != matchedNodeEntities.get(0).getEntityValue().getContentDescription())) {
//                    node = matchedNodeEntities.get(0);
//                } else {
//                    int i = 0;
//                    int k = 0;
//                    while (!matchedNodeEntities.get(i).getEntityValue().getClickable()) {
////                        System.out.println("The node info is: "+ matchedNodeEntities.get(i).getEntityValue().);
//                        if (i + 1 >= matchedNodeEntities.size()) {
//                            break;
//                        }
//                        i++;
//                    }
//
//                    if (i >= matchedNodeEntities.size()) {
//                        int innerIndex = 0;
//                        while (true) {
//                            if (innerIndex >= matchedNodeEntities.size()) {
//                                break;
//                            }
//                            if (null != matchedNodeEntities.get(innerIndex).getEntityValue().getText() || null != matchedNodeEntities.get(innerIndex).getEntityValue().getContentDescription()) {
//                                k = innerIndex;
//                                break;
//                            }
//                            innerIndex++;
//                        }
//                        node = matchedNodeEntities.get(k);
//                    } else {
//                        node = matchedNodeEntities.get(i);
//                    }
//
//
//                }
//            } else {
//                node = matchedNodeEntities.get(0);
//            }
            if (node != null) {
                if (sugiliteAccessibilityService.getSugiliteStudyHandler().isToRecordNextOperation()) {
                    //save a study packet
                    Date time = Calendar.getInstance().getTime();
                    String timeString = Const.dateFormat.format(time);
                    String path = "/sdcard/Download/sugilite_study_packets";
                    String fileName = "packet_" + timeString;
                    try {
                        sugiliteScreenshotManager.takeScreenshotUsingShellCommand(true, path, fileName + ".png");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
//                    SugiliteStudyHandler studyHandler = sugiliteAccessibilityService.getSugiliteStudyHandler();
//                    studyHandler.handleEvent(new SugiliteAvailableFeaturePack(node, uiSnapshot, getLatestScreenshot()), uiSnapshot, path, fileName);
                } else {
                    OverlayClickedDialog overlayClickedDialog = new OverlayClickedDialog(context, node, uiSnapshot, null, x, y, this, overlay, sugiliteData, sharedPreferences, tts, false);
                    overlayClickedDialog.show();

                    //flush the textChangedEventHandler
                    if (textChangedEventHandler != null) {
                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                //capture the screen
                                textChangedEventHandler.flush();
                            }
                        }, 500);
                    }
                }


//            PumiceDemonstrationUtil.showSugiliteToast("No node matched!", Toast.LENGTH_SHORT);
//            System.out.println("No node matched!");
            }
        }
    }



    private void handleContextClick(float x, float y, TextToSpeech tts) {
        UISnapshot uiSnapshot = getUiSnapshotAndAnnotateStringEntitiesIfNeeded();
//        File screenshot = getLatestScreenshot();
        if (uiSnapshot != null) {
            SugiliteEntity<Node> topLongClickableNode = null;
            SugiliteEntity<Node> topClickableNode = null;
            List<SugiliteEntity<Node>> matchedLongClickableNodeEntities = getMatchedNodesFromCoordinate(x, y, uiSnapshot, false, true);
            List<SugiliteEntity<Node>> matchedClickableNodeEntities = getMatchedNodesFromCoordinate(x, y, uiSnapshot, true, false);
            if (matchedLongClickableNodeEntities != null) {
                topLongClickableNode = matchedLongClickableNodeEntities.get(0);
            }
            if (matchedClickableNodeEntities != null) {
                topClickableNode = matchedClickableNodeEntities.get(0);
            }
            List<SugiliteEntity<Node>> matchedAllNodeEntities = getMatchedNodesFromCoordinate(x, y, uiSnapshot, false, false);
            if (matchedAllNodeEntities != null) {
                RecordingOverlayContextClickDialog recordingOverlayContextClickDialog = new RecordingOverlayContextClickDialog(context, this, topLongClickableNode, topClickableNode, matchedAllNodeEntities, uiSnapshot, null, sugiliteData, tts, x, y);
                recordingOverlayContextClickDialog.show();

                //flush the textChangedEventHandler
                if (textChangedEventHandler != null) {
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            //capture the screen
                            textChangedEventHandler.flush();
                        }
                    }, 500);
                }
            } else {
                PumiceDemonstrationUtil.showSugiliteToast("No node matched!", Toast.LENGTH_SHORT);
            }
        }
    }

    private void clickWithRootPermission(float x, float y, Runnable uiThreadRunnable, Node alternativeNode, boolean isLongClick) {
        Instrumentation m_Instrumentation = new Instrumentation();
        overlay.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            /**
             * the simulated touch event is send once the layout change has happend (when the view is set to not touchable)
             * @param v
             * @param left
             * @param top
             * @param right
             * @param bottom
             * @param oldLeft
             * @param oldTop
             * @param oldRight
             * @param oldBottom
             */
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                overlay.removeOnLayoutChangeListener(this);
                System.out.println("layout changed");
                Thread clickThread = new Thread(new Runnable() {
                    @Override
                    public synchronized void run() {
                        try {
                            m_Instrumentation.sendPointerSync(MotionEvent.obtain(
                                    SystemClock.uptimeMillis(),
                                    SystemClock.uptimeMillis(),
                                    MotionEvent.ACTION_DOWN, x, y, 0));
                            m_Instrumentation.sendPointerSync(MotionEvent.obtain(
                                    SystemClock.uptimeMillis(),
                                    SystemClock.uptimeMillis(),
                                    MotionEvent.ACTION_UP, x, y, 0));
                            //sugiliteAccessibilityService.runOnUiThread(uiThreadRunnable);
                        } catch (Exception e) {
                            e.printStackTrace();
                            recordingOverlayManager.addSugiliteOperationBlockBasedOnNode(alternativeNode, isLongClick);
                        }
                    }
                });
                clickThread.start();
                try {
                    clickThread.join();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //run the callback - this eliminates the latency between changing passthroughbility
                uiThreadRunnable.run();
            }
        });
    }

    private void performFlingWithRootPermission(MotionEvent event1, MotionEvent event2, Runnable uiThreadRunnable) {
        Instrumentation m_Instrumentation = new Instrumentation();
        overlay.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                overlay.removeOnLayoutChangeListener(this);
                System.out.println("layout changed");
                Thread flingThread = new Thread(new Runnable() {
                    @Override
                    public synchronized void run() {
                        //obtain event1
                        if (event1 != null && event2 != null) {
                            /*
                            MotionEvent.PointerProperties[] pointerProperties1 = new MotionEvent.PointerProperties[event1.getPointerCount()];
                            MotionEvent.PointerCoords[] pointerCoordses1 = new MotionEvent.PointerCoords[event1.getPointerCount()];
                            for (int i = 0; i < event1.getPointerCount(); i++) {
                                pointerProperties1[i] = new MotionEvent.PointerProperties();
                                pointerCoordses1[i] = new MotionEvent.PointerCoords();
                                event1.getPointerProperties(i, pointerProperties1[i]);
                                event1.getPointerCoords(i, pointerCoordses1[i]);
                            }
                            MotionEvent motionEvent1 = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                                    event1.getAction(), event1.getPointerCount(), pointerProperties1, pointerCoordses1, event1.getMetaState(),
                                    event1.getButtonState(), event1.getXPrecision(), event1.getYPrecision(), event1.getDeviceId(), event1.getEdgeFlags(), event1.getSource(), event1.getFlags());
                            System.out.println("EVENT1: " + motionEvent1.toString());

                            //obtain event2
                            MotionEvent.PointerProperties[] pointerProperties2 = new MotionEvent.PointerProperties[event2.getPointerCount()];
                            MotionEvent.PointerCoords[] pointerCoordses2 = new MotionEvent.PointerCoords[event2.getPointerCount()];
                            for (int i = 0; i < event2.getPointerCount(); i++) {
                                pointerProperties2[i] = new MotionEvent.PointerProperties();
                                pointerCoordses2[i] = new MotionEvent.PointerCoords();
                                event2.getPointerProperties(i, pointerProperties2[i]);
                                event2.getPointerCoords(i, pointerCoordses2[i]);
                            }
                            MotionEvent motionEvent2 = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                                    event2.getAction(), event2.getPointerCount(), pointerProperties2, pointerCoordses2, event2.getMetaState(),
                                    event2.getButtonState(), event2.getXPrecision(), event2.getYPrecision(), event2.getDeviceId(), event2.getEdgeFlags(), event2.getSource(), event2.getFlags());
                            System.out.println("EVENT2: " + motionEvent2.toString());
                            */

                            int x1 = (int) event1.getRawX();
                            int y1 = (int) event1.getRawY();
                            int x2 = (int) event2.getRawX();
                            int y2 = (int) event2.getRawY();
                            long duration = event2.getEventTime() - event1.getEventTime();
                            String command = "input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + duration;


                            try {
                                /*
                                m_Instrumentation.sendPointerSync(motionEvent1);
                                m_Instrumentation.sendPointerSync(motionEvent2);
                                */
                                Process sh = Runtime.getRuntime().exec("su", null, null);
                                OutputStream os = sh.getOutputStream();
                                os.write((command).getBytes("ASCII"));
                                os.flush();
                                os.close();
                                System.out.println("SWIPING: " + command);
                                Thread.sleep(400 + duration);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            //sugiliteAccessibilityService.runOnUiThread(uiThreadRunnable);

                        }
                    }
                });
                flingThread.start();
                try {
                    flingThread.join();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //run the callback
                uiThreadRunnable.run();
            }
        });
    }



    private void addSugiliteOperationBlockBasedOnNode(Node node, boolean isLongClick) {
        if (node.getBoundsInScreen() != null) {
            CombinedOntologyQuery parentQuery = new CombinedOntologyQuery(CombinedOntologyQuery.RelationType.AND);
            LeafOntologyQuery screenBoundsQuery = new LeafOntologyQuery();
            screenBoundsQuery.addObject(new SugiliteEntity<>(-1, String.class, node.getBoundsInScreen()));
            screenBoundsQuery.setQueryFunction(SugiliteRelation.HAS_SCREEN_LOCATION);
            parentQuery.addSubQuery(screenBoundsQuery);

            if (node.getClassName() != null) {
                LeafOntologyQuery classQuery = new LeafOntologyQuery();
                classQuery.addObject(new SugiliteEntity<>(-1, String.class, node.getClassName()));
                classQuery.setQueryFunction(SugiliteRelation.HAS_CLASS_NAME);
                parentQuery.addSubQuery(classQuery);
            }

            if (node.getPackageName() != null) {
                LeafOntologyQuery packageQuery = new LeafOntologyQuery();
                packageQuery.addObject(new SugiliteEntity<>(-1, String.class, node.getPackageName()));
                packageQuery.setQueryFunction(SugiliteRelation.HAS_PACKAGE_NAME);
                parentQuery.addSubQuery(packageQuery);
            }
            SugiliteOperationBlock operationBlock = generateBlock(parentQuery, parentQuery.toString(), isLongClick);


            //add the operation block to the instruction queue specified in sugiliteData
            sugiliteData.addInstruction(operationBlock);
        }


    }

    private SugiliteOperationBlock generateBlock(OntologyQuery query, String formula, boolean isLongClick) {
        //generate the sugilite operation
        SugiliteUnaryOperation sugiliteOperation = isLongClick ? new SugiliteLongClickOperation() : new SugiliteClickOperation();
        //assume it's click for now -- need to expand to more types of operations

        SugiliteOperationBlock operationBlock = new SugiliteOperationBlock();
        operationBlock.setOperation(sugiliteOperation);
        operationBlock.setFeaturePack(null);
        operationBlock.setElementMatchingFilter(null);
        operationBlock.setScreenshot(null);
        if (sugiliteOperation instanceof SugiliteClickOperation) {
            ((SugiliteClickOperation) sugiliteOperation).setQuery(query.clone());
        }
        if (sugiliteOperation instanceof SugiliteLongClickOperation) {
            ((SugiliteLongClickOperation) sugiliteOperation).setQuery(query.clone());
        }
        operationBlock.setDescription(readableDescriptionGenerator.generateDescriptionForVerbalBlock(operationBlock, formula, "UTTERANCE"));
        return operationBlock;
    }

    void clickNode(Node node, float x, float y, View overlay, boolean isLongClick) {
        if (Const.ROOT_ENABLED) {
            //on a rooted phone, should directly simulate the click itself
            recordingOverlayManager.setPassThroughOnTouchListener();
            try {
                recordingOverlayManager.clickWithRootPermission(x, y, new Runnable() {
                    @Override
                    public void run() {
                        //allow the overlay to get touch event after finishing the simulated click
                        recordingOverlayManager.setOverlayOnTouchListener(true);
                    }
                }, node, isLongClick);
            } catch (Exception e) {
                //do nothing
            }
        } else {
            recordingOverlayManager.addSugiliteOperationBlockBasedOnNode(node, isLongClick);
        }
    }




}

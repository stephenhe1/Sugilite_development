//package com.microsoft.userappaccessibilityactiontracer.model;
package edu.cmu.hcii.sugilite.model;

import android.graphics.Rect;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

//import com.microsoft.userappaccessibilityactiontracer.Const;
//import com.microsoft.userappaccessibilityactiontracer.handler.TextLabelManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Created by toby on 5/24/2017.
 */

public class Node implements Serializable {

    private String text;
    private String contentDescription;
    private String viewId;
    private String packageName;
    private String activityName;
    private String className;
    private String boundsInScreen;
    private String boundsInParent;
    private Integer rootNodeDescendantsCount = 0;
    private Boolean isClickable = false;
    private Boolean isLongClickable = false;
    private Boolean isEditable = false;
    private Boolean isChecked = false;
    private Boolean isCheckable = false;
    private Boolean isSelected = false;
    private Boolean isFocused = false;
    private Boolean isEnabled = false;
    private Boolean isScrollable = false;
    private Node parent = null;
    private String eventManagerId; //unique view identifier added to AccessibilityNodeInfo
    private String TAG = Node.class.getCanonicalName();
    private transient AccessibilityNodeInfo parentalNode=null;
    private transient AccessibilityNodeInfo thisNode=null;
    private Integer windowZIndex;
    private List<Integer> nodeZIndexSequence = new ArrayList<>();


    public Node(AccessibilityNodeInfo nodeInfo, Integer windowZIndex, List<Integer> parentNodeZIndexSequence, String activityName){

        this(nodeInfo, activityName);
        this.windowZIndex = windowZIndex;
        this.nodeZIndexSequence = new ArrayList<>(parentNodeZIndexSequence);
        Collections.copy(this.nodeZIndexSequence, parentNodeZIndexSequence);
        //NOTE: AccessibilityNodeInfo.getDrawingOrder requires API Level 24 (Android 7.0)
        this.nodeZIndexSequence.add(nodeInfo.getDrawingOrder());
    }



    public Node(AccessibilityNodeInfo nodeInfo, String activityName){
        if(nodeInfo == null){
            new Exception("null nodeinfo!").printStackTrace();
            return;
        }
        if(nodeInfo.getText() != null) {
            text = nodeInfo.getText().toString();
        }
        if(nodeInfo.getContentDescription() != null) {
            contentDescription = nodeInfo.getContentDescription().toString();
        }
        if(nodeInfo.getViewIdResourceName() != null) {
            viewId = nodeInfo.getViewIdResourceName();
        }
        if(nodeInfo.getPackageName() != null) {
            packageName = nodeInfo.getPackageName().toString();
        }
        this.activityName = activityName;
        if(nodeInfo.getClassName() != null) {
            className = nodeInfo.getClassName().toString();
        }


        Rect boundsInScreen = new Rect();
        Rect boundsInParent = new Rect();
        nodeInfo.getBoundsInScreen(boundsInScreen);
        nodeInfo.getBoundsInParent(boundsInParent);
        this.boundsInScreen = boundsInScreen.flattenToString();
        this.boundsInParent = boundsInParent.flattenToString();
        this.isClickable = nodeInfo.isClickable();
        this.isLongClickable = nodeInfo.isLongClickable();
        this.isEditable = nodeInfo.isEditable();
        this.isChecked = nodeInfo.isChecked();
        this.isSelected = nodeInfo.isSelected();
        this.isFocused = nodeInfo.isFocused();
        this.isChecked = nodeInfo.isChecked();
        this.isCheckable = nodeInfo.isCheckable();
        this.isScrollable = nodeInfo.isScrollable();
        if (null!=nodeInfo.getParent()){
            this.parentalNode=nodeInfo.getParent();
        }

        if(nodeInfo.getParent() != null) {
            parent = new Node(nodeInfo.getParent(), activityName);
        }
        this.thisNode=nodeInfo;

//        List<String> names = new ArrayList<>();
//        Node it = this;
//        names.add(0, String.valueOf(it.getClassName()));
//        while(it.getParentalNode()!= null){
//            int count = 0;
//            int length = 0;
//            String itClsName = String.valueOf(it.getClassName());
//            for(int i=0; i<it.getParentalNode().getChildCount(); i++) {
//                AccessibilityNodeInfo child = it.getParentalNode().getChild(i);
//                if (child == null)
//                    continue;
//                String childClsName = String.valueOf(child.getClassName());
//                if (!child.isVisibleToUser())
//                    continue;
//
//                if (itClsName.equals(childClsName)){
//                    length++;
//                    if (null != it && it.getViewIdResourceName() != null){
//                        if (it.getViewIdResourceName().equals("com.colpit.diamondcoming.isavemoney:id/amount")){
//                            System.out.println("child class name is: "+ child.getClassName());
//                            System.out.println(length);
//                            System.out.println(count);
//                        }
//                    }
//                }
//
//
//                if (it.getThisNode().equals(child)) {
//                    count = length;
////                    System.out.println("The count is: " + count);
//                }
////                if (it.isSameNode(child)){
////                    count = length;
////                }
//
//
//            }
//            if(length > 1)
//                names.set(0, String.format("%s[%d]", names.get(0), count));
//            it = it.getParent();
//            names.add(0, String.valueOf(it.getClassName()));
//        }
//        this.xpath = "/"+String.join("/", names);
//

    }

    public String getText(){
        return text;
    }

    public String getContentDescription(){
        return contentDescription;
    }

    public String getViewId(){
        return viewId;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getClassName() {
        return className;
    }

    public String getBoundsInScreen() {
        return boundsInScreen;
    }

    public String getBoundsInParent() {
        return boundsInParent;
    }

//    public List<Node> getChildNodes() {
//        return childNodes;
//    }


    public AccessibilityNodeInfo getParentalNode() {
        return parentalNode;
    }

    public Boolean getClickable() {
        return isClickable;
    }

    public Boolean getEditable() {
        return isEditable;
    }

    public Boolean getChecked() {
        return isChecked;
    }

    public Boolean getCheckable() {
        return isCheckable;
    }

    public Boolean getEnabled() {
        return isEnabled;
    }

    public Boolean getFocused() {
        return isFocused;
    }

    public Boolean getSelected() {
        return isSelected;
    }

    public Boolean getScrollable() {
        return isScrollable;
    }

    public String getViewIdResourceName() {
        return viewId;
    }

    public String getEventManagerId(){
        return eventManagerId;
    }

    public List<Integer> getNodeZIndexSequence() {
        return nodeZIndexSequence;
    }

    public Integer getWindowZIndex() {
        return windowZIndex;
    }

    public Boolean getLongClickable() {
        return isLongClickable;
    }

    public Node getParent() {
        return parent;
    }

    public AccessibilityNodeInfo getThisNode() {
        return thisNode;
    }



    @Deprecated
    public boolean sameNode(Node obj) {
        if(packageName.equals(((Node) obj).getPackageName()) &&
                className.equals(((Node) obj).getClassName())){
            if(viewId == null || (viewId.equals(obj.getViewId())))
                return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.packageName, this.className, this.boundsInScreen, this.viewId);
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Node){
            try {
                if (((this.packageName == null && ((Node) obj).packageName == null) || this.packageName.equals(((Node) obj).packageName)) &&
                        ((this.className == null && ((Node) obj).className == null) || this.className.equals(((Node) obj).className)) &&
                        ((this.boundsInScreen == null && ((Node) obj).boundsInScreen == null) || this.boundsInScreen.equals(((Node) obj).boundsInScreen)) &&
                        ((this.viewId == null && ((Node) obj).viewId == null) || this.viewId.equals(((Node) obj).viewId)))
                    return true;
            }
            catch (NullPointerException e){
                return false;
            }
        }
        return super.equals(obj);
    }

    public boolean isSameNode(AccessibilityNodeInfo accessibilityNodeInfo){
        try {
            Rect boundsInScreen = new Rect();
            accessibilityNodeInfo.getBoundsInScreen(boundsInScreen);
            String boundsInScreenOther = boundsInScreen.flattenToString();
            if (((this.packageName == null && (accessibilityNodeInfo).getPackageName() == null) || this.packageName.equals((accessibilityNodeInfo.getPackageName().toString()))) &&
                    ((this.className == null && (accessibilityNodeInfo).getClassName() == null) || this.className.equals((accessibilityNodeInfo).getClassName().toString())) &&
                    ((this.boundsInScreen == null && boundsInScreenOther == null) || this.boundsInScreen.equals(boundsInScreenOther)) &&
                    ((this.viewId == null && accessibilityNodeInfo.getViewIdResourceName() == null) || this.viewId.equals(accessibilityNodeInfo.getViewIdResourceName())))
                return true;
        }
        catch (NullPointerException e){
            return false;
        }
        return false;

    }





}

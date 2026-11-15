/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.graphics.Canvas
 *  de.robv.android.xposed.XC_MethodHook
 *  de.robv.android.xposed.XC_MethodHook$MethodHookParam
 *  de.robv.android.xposed.XC_MethodHook$Unhook
 *  de.robv.android.xposed.XposedBridge
 *  org.telegram.messenger.AndroidUtilities
 *  org.telegram.messenger.ChatObject
 *  org.telegram.messenger.MessageObject
 *  org.telegram.messenger.MessageObject$GroupedMessagePosition
 *  org.telegram.messenger.MessageObject$GroupedMessages
 *  org.telegram.messenger.MessagesController
 *  org.telegram.messenger.UserConfig
 *  org.telegram.tgnet.TLRPC$Chat
 *  org.telegram.tgnet.TLRPC$User
 *  org.telegram.ui.Cells.ChatMessageCell
 *  org.telegram.ui.Components.CheckBoxBase
 */
package com.extera.plugins.widemessages;

import android.graphics.Canvas;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.CheckBoxBase;

public final class Main {
    private static final int DIALOG_CACHE_MAX = 200;
    private static final int GROUP_LAYOUT_FULL_SPAN = 1000;
    private static final Map<MessageObject.GroupedMessages, GroupWidthSnapshot> GROUP_WIDTH_SNAPSHOTS;
    private static final List<XC_MethodHook.Unhook> HOOKS;
    private static final ThreadLocal<LayoutContext> LAYOUT_CONTEXT;
    private static final int MIN_CHAR_THRESHOLD_DEFAULT = 30;
    private static final ThreadLocal<Boolean> SUPPRESS_PARENT_WIDTH_HOOK;
    private static final Map<MessageObject.GroupedMessages, Integer> WIDE_GROUP_LAYOUT_WIDTHS;
    private static final Map<MessageObject, Integer> WIDE_TEXT_LAYOUT_WIDTHS;
    private static final Map<Long, Boolean> botCache;
    private static volatile boolean botsEnabled;
    private static final Map<Long, Boolean> channelCache;
    private static volatile boolean channelsEnabled;
    private static Field checkBoxAnimationInProgressField;
    private static Field checkBoxAnimationProgressField;
    private static Field checkBoxField;
    private static Field checkBoxTranslationField;
    private static Field checkBoxVisibleField;
    private static Field drawMediaCheckBoxField;
    private static Field drawSideButton2Field;
    private static Field drawSideButtonField;
    private static Field drawSummarizeButtonField;
    private static Method generateLayoutMethod;
    private static volatile boolean groupsEnabled;
    private static volatile boolean hideChannelAvatars;
    private static volatile boolean ignoreOwnMessages;
    private static Field layoutCreatedField;
    private static boolean loaded;
    private static Field mediaCheckBoxField;
    private static volatile int minChars;
    private static Field pressedSideButtonField;
    private static volatile boolean privateChatsEnabled;
    private static Field sideButtonPressedField;
    private static Field sideButtonVisibleField;
    private static Field summarizeButtonPressedField;

    static {
        privateChatsEnabled = false;
        botsEnabled = false;
        groupsEnabled = false;
        channelsEnabled = true;
        hideChannelAvatars = false;
        minChars = 30;
        ignoreOwnMessages = false;
        channelCache = new ConcurrentHashMap<Long, Boolean>();
        botCache = new ConcurrentHashMap<Long, Boolean>();
        HOOKS = new ArrayList<XC_MethodHook.Unhook>();
        LAYOUT_CONTEXT = new ThreadLocal();
        SUPPRESS_PARENT_WIDTH_HOOK = new ThreadLocal();
        WIDE_TEXT_LAYOUT_WIDTHS = new WeakHashMap<MessageObject, Integer>();
        WIDE_GROUP_LAYOUT_WIDTHS = new WeakHashMap<MessageObject.GroupedMessages, Integer>();
        GROUP_WIDTH_SNAPSHOTS = new WeakHashMap<MessageObject.GroupedMessages, GroupWidthSnapshot>();
    }

    private Main() {
    }

    private static void applyWideGroupLayout(MessageObject.GroupedMessages groupedMessages) {
        block4: {
            GroupWidthSnapshot groupWidthSnapshot;
            block6: {
                block5: {
                    if (groupedMessages == null || groupedMessages.posArray == null || groupedMessages.posArray.isEmpty()) break block4;
                    GroupWidthSnapshot groupWidthSnapshot2 = GROUP_WIDTH_SNAPSHOTS.get(groupedMessages);
                    if (groupWidthSnapshot2 == null) break block5;
                    groupWidthSnapshot = groupWidthSnapshot2;
                    if (groupWidthSnapshot2.matches(groupedMessages)) break block6;
                }
                groupWidthSnapshot = new GroupWidthSnapshot(groupedMessages);
                GROUP_WIDTH_SNAPSHOTS.put(groupedMessages, groupWidthSnapshot);
            }
            if (groupWidthSnapshot.apply()) {
                groupedMessages.cachedWidthForCaption = -1;
            }
            return;
        }
    }

    public static void clearCaches() {
        channelCache.clear();
        botCache.clear();
        WIDE_TEXT_LAYOUT_WIDTHS.clear();
    }

    private static void clearWideGroupCaptionWidthIfNeeded(MessageObject.GroupedMessages groupedMessages) {
        if (groupedMessages != null && WIDE_GROUP_LAYOUT_WIDTHS.remove(groupedMessages) != null) {
            groupedMessages.cachedWidthForCaption = -1;
            return;
        }
    }

    private static int dp(float f) {
        return AndroidUtilities.dp((float)f);
    }

    /*
     * WARNING - combined exceptions agressively - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static Method findGenerateLayoutMethod() {
        if (generateLayoutMethod != null) {
            return generateLayoutMethod;
        }
        try {
            for (Method method : MessageObject.class.getDeclaredMethods()) {
                if (!method.getName().equals("generateLayout") || method.getParameterTypes().length != 1) continue;
                generateLayoutMethod = method;
                return method;
            }
            return null;
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    private static int getAvatarInset(MessageObject messageObject) {
        if (messageObject != null) {
            try {
                if (messageObject.messageOwner != null && !messageObject.messageOwner.isThreadMessage && messageObject.needDrawAvatar() && !messageObject.isOutOwner()) {
                    int n = Main.dp(52.0f);
                    return n;
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return 0;
    }

    private static boolean getBooleanField(Field field, Object object, boolean bl) {
        if (field != null && object != null) {
            try {
                boolean bl2 = field.getBoolean(object);
                return bl2;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return bl;
    }

    private static int getCellParentWidth(ChatMessageCell chatMessageCell, MessageObject messageObject) {
        int n;
        block3: {
            n = Main.getMessageParentWidth(messageObject);
            int n2 = Main.getRawCellParentWidth(chatMessageCell);
            if (n2 <= 0) break block3;
            try {
                n2 = Math.min(n2, n);
                return n2;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return n;
    }

    private static Field getCheckBoxAnimationInProgressField() {
        if (checkBoxAnimationInProgressField == null) {
            checkBoxAnimationInProgressField = Main.getDeclaredField(ChatMessageCell.class, "checkBoxAnimationInProgress");
        }
        return checkBoxAnimationInProgressField;
    }

    private static Field getCheckBoxAnimationProgressField() {
        if (checkBoxAnimationProgressField == null) {
            checkBoxAnimationProgressField = Main.getDeclaredField(ChatMessageCell.class, "checkBoxAnimationProgress");
        }
        return checkBoxAnimationProgressField;
    }

    private static Field getCheckBoxField() {
        if (checkBoxField == null) {
            checkBoxField = Main.getDeclaredField(ChatMessageCell.class, "checkBox");
        }
        return checkBoxField;
    }

    private static Field getCheckBoxTranslationField() {
        if (checkBoxTranslationField == null) {
            checkBoxTranslationField = Main.getDeclaredField(ChatMessageCell.class, "checkBoxTranslation");
        }
        return checkBoxTranslationField;
    }

    private static Field getCheckBoxVisibleField() {
        if (checkBoxVisibleField == null) {
            checkBoxVisibleField = Main.getDeclaredField(ChatMessageCell.class, "checkBoxVisible");
        }
        return checkBoxVisibleField;
    }

    private static Field getDeclaredField(Class<?> clazz, String string) {
        try {
            Field field = clazz.getDeclaredField(string);
            field.setAccessible(true);
            return field;
        }
        catch (Throwable throwable) {
            throw new IllegalStateException("missing field " + clazz.getName() + "#" + string, throwable);
        }
    }

    private static MessageObject getDiscussionLinkMessage(MessageObject messageObject, MessageObject.GroupedMessages groupedMessages) {
        if (Main.isGroupedLayout(groupedMessages) && groupedMessages.messages != null && !groupedMessages.messages.isEmpty()) {
            return (MessageObject)groupedMessages.messages.get(0);
        }
        return messageObject;
    }

    private static Field getDrawMediaCheckBoxField() {
        if (drawMediaCheckBoxField == null) {
            drawMediaCheckBoxField = Main.getDeclaredField(ChatMessageCell.class, "drawMediaCheckBox");
        }
        return drawMediaCheckBoxField;
    }

    private static Field getDrawSideButton2Field() {
        if (drawSideButton2Field == null) {
            drawSideButton2Field = Main.getDeclaredField(ChatMessageCell.class, "drawSideButton2");
        }
        return drawSideButton2Field;
    }

    private static Field getDrawSideButtonField() {
        if (drawSideButtonField == null) {
            drawSideButtonField = Main.getDeclaredField(ChatMessageCell.class, "drawSideButton");
        }
        return drawSideButtonField;
    }

    private static Field getDrawSummarizeButtonField() {
        if (drawSummarizeButtonField == null) {
            drawSummarizeButtonField = Main.getDeclaredField(ChatMessageCell.class, "drawSummarizeButton");
        }
        return drawSummarizeButtonField;
    }

    private static int getFirstLineSpan(MessageObject.GroupedMessages object) {
        if (object != null && ((MessageObject.GroupedMessages)object).posArray != null) {
            object = ((MessageObject.GroupedMessages)object).posArray.iterator();
            int n = 0;
            while (object.hasNext()) {
                MessageObject.GroupedMessagePosition groupedMessagePosition = (MessageObject.GroupedMessagePosition)object.next();
                int n2 = n;
                if (groupedMessagePosition != null) {
                    if (groupedMessagePosition.minY != 0) break;
                    n2 = n + Math.max(0, groupedMessagePosition.pw + groupedMessagePosition.leftSpanOffset);
                }
                n = n2;
            }
            return n;
        }
        return 0;
    }

    private static float getFloatField(Field field, Object object, float f) {
        if (field != null && object != null) {
            try {
                float f2 = field.getFloat(object);
                return f2;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return f;
    }

    private static int getIntField(Field field, Object object, int n) {
        if (field != null && object != null) {
            try {
                int n2 = field.getInt(object);
                return n2;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return n;
    }

    private static Field getLayoutCreatedField() {
        if (layoutCreatedField == null) {
            layoutCreatedField = Main.getDeclaredField(MessageObject.class, "layoutCreated");
        }
        return layoutCreatedField;
    }

    private static Field getMediaCheckBoxField() {
        if (mediaCheckBoxField == null) {
            mediaCheckBoxField = Main.getDeclaredField(ChatMessageCell.class, "mediaCheckBox");
        }
        return mediaCheckBoxField;
    }

    private static int getMediaLeftInset(ChatMessageCell chatMessageCell, MessageObject messageObject) {
        if (Main.isSideMenuEnabled(chatMessageCell, messageObject)) {
            return Main.dp(73.0f);
        }
        float f = Main.getAvatarInset(messageObject) != 0 ? 57.0f : 9.0f;
        return Main.dp(f);
    }

    private static int getMessageParentWidth(MessageObject messageObject) {
        if (messageObject != null && messageObject.preview && messageObject.parentWidth > 0) {
            return messageObject.parentWidth;
        }
        if (AndroidUtilities.isTablet()) {
            return AndroidUtilities.getMinTabletSide();
        }
        if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            return AndroidUtilities.displaySize.x - Main.dp(50.0f);
        }
        return AndroidUtilities.displaySize.x;
    }

    private static Field getPressedSideButtonField() {
        if (pressedSideButtonField == null) {
            pressedSideButtonField = Main.getDeclaredField(ChatMessageCell.class, "pressedSideButton");
        }
        return pressedSideButtonField;
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static int getRawCellParentWidth(ChatMessageCell chatMessageCell) {
        if (chatMessageCell == null) {
            return 0;
        }
        Boolean bl = SUPPRESS_PARENT_WIDTH_HOOK.get();
        try {
            SUPPRESS_PARENT_WIDTH_HOOK.set(Boolean.TRUE);
            int n = chatMessageCell.getParentWidth();
            if (bl == null) {
                SUPPRESS_PARENT_WIDTH_HOOK.remove();
                return n;
            }
            SUPPRESS_PARENT_WIDTH_HOOK.set(bl);
            return n;
        }
        catch (Throwable throwable) {
            if (bl == null) {
                SUPPRESS_PARENT_WIDTH_HOOK.remove();
                return 0;
            }
            SUPPRESS_PARENT_WIDTH_HOOK.set(bl);
            return 0;
        }
    }

    private static Field getSideButtonPressedField() {
        if (sideButtonPressedField == null) {
            sideButtonPressedField = Main.getDeclaredField(ChatMessageCell.class, "sideButtonPressed");
        }
        return sideButtonPressedField;
    }

    private static Field getSideButtonVisibleField() {
        if (sideButtonVisibleField == null) {
            sideButtonVisibleField = Main.getDeclaredField(ChatMessageCell.class, "sideButtonVisible");
        }
        return sideButtonVisibleField;
    }

    private static Field getSummarizeButtonPressedField() {
        if (summarizeButtonPressedField == null) {
            summarizeButtonPressedField = Main.getDeclaredField(ChatMessageCell.class, "summarizeButtonPressed");
        }
        return summarizeButtonPressedField;
    }

    private static int getWideGroupPhotosWidth(MessageObject.GroupedMessages groupedMessages, int n) {
        if (n > 0 && groupedMessages != null && groupedMessages.posArray != null && !groupedMessages.posArray.isEmpty()) {
            int n2 = Math.round((float)n * Main.getWideGroupScale(groupedMessages));
            if (n2 <= n) {
                Main.clearWideGroupCaptionWidthIfNeeded(groupedMessages);
                return n;
            }
            Main.invalidateGroupedCaptionWidthIfNeeded(groupedMessages, n2);
            return n2;
        }
        return n;
    }

    private static float getWideGroupScale(MessageObject.GroupedMessages groupedMessages) {
        int n = Main.getFirstLineSpan(groupedMessages);
        if (n > 0 && n < 1000) {
            return Math.max(1.0f, 1000.0f / (float)n);
        }
        return 1.0f;
    }

    private static int getWideImageHeight(int n, int n2, int n3, int n4) {
        n2 = Math.round((float)n4 * ((float)n2 / (float)n));
        n4 = AndroidUtilities.getPhotoSize();
        n = n2;
        if (n4 > 0) {
            n = Math.min(n2, n4);
        }
        return Math.max(n3, n);
    }

    private static int getWideImageWidth(ChatMessageCell chatMessageCell, MessageObject messageObject) {
        return Math.max(Main.dp(120.0f), Main.getCellParentWidth(chatMessageCell, messageObject) - Main.getMediaLeftInset(chatMessageCell, messageObject) - Main.dp(17.0f));
    }

    private static int getWideTextWidth(ChatMessageCell chatMessageCell, MessageObject messageObject) {
        int n = Main.getCellParentWidth(chatMessageCell, messageObject);
        int n2 = Main.dp(40.0f);
        int n3 = Main.isSideMenuEnabled(chatMessageCell, messageObject) ? Main.dp(64.0f) : Main.getAvatarInset(messageObject);
        return Math.max(Main.dp(120.0f), n - n2 - n3);
    }

    private static boolean hasActiveGroupedLayout(ChatMessageCell chatMessageCell) {
        boolean bl = false;
        if (chatMessageCell != null) {
            if (Main.isGroupedLayout(chatMessageCell.getCurrentMessagesGroup()) || chatMessageCell.getCurrentPosition() != null) {
                bl = true;
            }
            return bl;
        }
        return false;
    }

    private static boolean hasSeparateCommentsButton(ChatMessageCell chatMessageCell, MessageObject messageObject, MessageObject.GroupedMessages groupedMessages) {
        block7: {
            boolean bl;
            block9: {
                block8: {
                    boolean bl2 = false;
                    if (chatMessageCell == null || messageObject == null) break block7;
                    if (!messageObject.isRoundVideo()) {
                        return false;
                    }
                    if (messageObject.shouldDrawWithoutBackground()) {
                        return false;
                    }
                    if (messageObject.isAnimatedEmoji()) {
                        return false;
                    }
                    if (messageObject.searchType != 0) {
                        return false;
                    }
                    groupedMessages = Main.getDiscussionLinkMessage(messageObject, groupedMessages);
                    if (chatMessageCell.hasDiscussion && groupedMessages != null && groupedMessages.isLinkedToChat(chatMessageCell.linkedChatId)) break block8;
                    bl = bl2;
                    if (!chatMessageCell.isRepliesChat) break block9;
                    bl = bl2;
                    if (messageObject.isOutOwner()) break block9;
                }
                bl = true;
            }
            return bl;
        }
        return false;
    }

    private static void hookGroupPhotosWidth() throws NoSuchMethodException {
        Method method = ChatMessageCell.class.getDeclaredMethod("getGroupPhotosWidth", new Class[0]);
        method.setAccessible(true);
        HOOKS.add(XposedBridge.hookMethod((Member)method, (XC_MethodHook)new XC_MethodHook(50){

            /*
             * Enabled aggressive block sorting
             * Enabled unnecessary exception pruning
             * Enabled aggressive exception aggregation
             */
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    ChatMessageCell chatMessageCell = (ChatMessageCell)methodHookParam.thisObject;
                    MessageObject messageObject = chatMessageCell.getMessageObject();
                    if (!Main.shouldWidenMessage(messageObject)) {
                        return;
                    }
                    MessageObject.GroupedMessages groupedMessages = chatMessageCell.getCurrentMessagesGroup();
                    if (Main.shouldRenderWideCell(chatMessageCell, messageObject) && Main.isWideGroupedMediaLayout(groupedMessages)) {
                        Object object = methodHookParam.getResult();
                        if (!(object instanceof Integer)) return;
                        int n = (Integer)object;
                        Main.applyWideGroupLayout(groupedMessages);
                        int n2 = Main.getWideGroupPhotosWidth(groupedMessages, n);
                        if (n2 <= n) return;
                        methodHookParam.setResult((Object)n2);
                        return;
                    }
                    Main.restoreWideGroupLayoutIfNeeded(groupedMessages);
                    return;
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
    }

    private static void hookMediaLayout() throws Exception {
        Method method = ChatMessageCell.class.getDeclaredMethod("setMessageContent", MessageObject.class, MessageObject.GroupedMessages.class, Boolean.TYPE, Boolean.TYPE, Boolean.TYPE, Boolean.TYPE);
        method.setAccessible(true);
        HOOKS.add(XposedBridge.hookMethod((Member)method, (XC_MethodHook)new XC_MethodHook(50){

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                ChatMessageCell chatMessageCell;
                block7: {
                    block8: {
                        if (!Main.isAnyEnabled()) {
                            return;
                        }
                        LayoutContext layoutContext = (LayoutContext)LAYOUT_CONTEXT.get();
                        chatMessageCell = (ChatMessageCell)methodHookParam.thisObject;
                        if (methodHookParam.args[0] instanceof MessageObject) {
                            methodHookParam = (MessageObject)methodHookParam.args[0];
                            break block7;
                        }
                        if (layoutContext == null) break block8;
                        methodHookParam = layoutContext.messageObject;
                        break block7;
                    }
                    methodHookParam = null;
                }
                try {
                    if (Main.shouldWidenMessage((MessageObject)methodHookParam) && Main.shouldSuppressWideActions(chatMessageCell, (MessageObject)methodHookParam)) {
                        Main.suppressWideActionState(chatMessageCell);
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                LAYOUT_CONTEXT.remove();
            }

            /*
             * Enabled aggressive block sorting
             * Enabled unnecessary exception pruning
             * Enabled aggressive exception aggregation
             */
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    MessageObject messageObject = (MessageObject)methodHookParam.args[0];
                    Object object = (MessageObject.GroupedMessages)methodHookParam.args[1];
                    methodHookParam = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(messageObject)) {
                        Main.restoreWideGroupLayoutIfNeeded((MessageObject.GroupedMessages)object);
                        LAYOUT_CONTEXT.remove();
                        return;
                    }
                    boolean bl = Main.isWideGroupedMediaLayout((MessageObject.GroupedMessages)object);
                    if (!Main.shouldRenderWideLayout((ChatMessageCell)methodHookParam, messageObject, (MessageObject.GroupedMessages)object)) {
                        Main.restoreWideGroupLayoutIfNeeded((MessageObject.GroupedMessages)object);
                        LAYOUT_CONTEXT.remove();
                        return;
                    }
                    if (bl) {
                        Main.applyWideGroupLayout((MessageObject.GroupedMessages)object);
                    } else if (Main.shouldRelayoutWideText((ChatMessageCell)methodHookParam, messageObject)) {
                        Main.setLayoutCreated(messageObject, false);
                    }
                    object = LAYOUT_CONTEXT;
                    LayoutContext layoutContext = new LayoutContext((ChatMessageCell)methodHookParam, messageObject, bl);
                    ((ThreadLocal)object).set(layoutContext);
                    return;
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
    }

    private static void hookMessageSize() throws NoSuchMethodException {
        Method method = ChatMessageCell.class.getDeclaredMethod("getMessageSize", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
        method.setAccessible(true);
        HOOKS.add(XposedBridge.hookMethod((Member)method, (XC_MethodHook)new XC_MethodHook(50){

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                block9: {
                    int n;
                    LayoutContext layoutContext;
                    block8: {
                        if (!Main.isAnyEnabled()) {
                            return;
                        }
                        layoutContext = (LayoutContext)LAYOUT_CONTEXT.get();
                        if (layoutContext != null) break block8;
                        return;
                    }
                    if (layoutContext.groupedMedia) {
                        return;
                    }
                    if (!Main.shouldWidenMessage(layoutContext.messageObject)) {
                        return;
                    }
                    if (!Main.isExpandableMediaMessage(layoutContext.messageObject)) {
                        return;
                    }
                    int n2 = (Integer)methodHookParam.args[0];
                    if (!Main.shouldExpandImage(n2, n = ((Integer)methodHookParam.args[1]).intValue())) break block9;
                    int n3 = (Integer)methodHookParam.args[2];
                    int n4 = (Integer)methodHookParam.args[3];
                    int n5 = Main.getWideImageWidth(layoutContext.cell, layoutContext.messageObject);
                    if (n5 <= n3) break block9;
                    try {
                        methodHookParam.args[2] = n5;
                        methodHookParam.args[3] = Main.getWideImageHeight(n2, n, n4, n5);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
            }
        }));
    }

    private static void hookNeedDrawAvatar() throws NoSuchMethodException {
        Method method = MessageObject.class.getDeclaredMethod("needDrawAvatar", new Class[0]);
        method.setAccessible(true);
        HOOKS.add(XposedBridge.hookMethod((Member)method, (XC_MethodHook)new XC_MethodHook(50){

            /*
             * Enabled force condition propagation
             * Lifted jumps to return sites
             */
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                long l;
                block7: {
                    MessageObject messageObject;
                    block6: {
                        if (!hideChannelAvatars) {
                            return;
                        }
                        messageObject = (MessageObject)methodHookParam.thisObject;
                        if (messageObject != null) break block6;
                        return;
                    }
                    Object object = methodHookParam.getResult();
                    if (!(object instanceof Boolean) || !((Boolean)object).booleanValue()) return;
                    l = messageObject.getDialogId();
                    if (l < 0L) break block7;
                    return;
                }
                try {
                    if (!Main.isChannelCached(l)) return;
                    methodHookParam.setResult((Object)Boolean.FALSE);
                    return;
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
    }

    private static void hookParentWidth() throws NoSuchMethodException {
        HOOKS.add(XposedBridge.hookMethod((Member)ChatMessageCell.class.getDeclaredMethod("getParentWidth", new Class[0]), (XC_MethodHook)new XC_MethodHook(50){

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                LayoutContext layoutContext;
                block9: {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    if (Boolean.TRUE.equals(SUPPRESS_PARENT_WIDTH_HOOK.get())) {
                        return;
                    }
                    layoutContext = (LayoutContext)LAYOUT_CONTEXT.get();
                    if (layoutContext != null) break block9;
                    return;
                }
                try {
                    if (layoutContext.cell != methodHookParam.thisObject) {
                        return;
                    }
                    if (layoutContext.groupedMedia) {
                        return;
                    }
                    if (!Main.shouldWidenMessage(layoutContext.messageObject)) {
                        return;
                    }
                    Object object = methodHookParam.getResult();
                    if (object instanceof Integer) {
                        methodHookParam.setResult((Object)(Math.min((Integer)object, Main.getMessageParentWidth(layoutContext.messageObject)) + Main.dp(40.0f)));
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
    }

    private static void hookSelectionCheckboxes() throws NoSuchMethodException {
        Method method = ChatMessageCell.class.getDeclaredMethod("setCheckBoxVisible", Boolean.TYPE, Boolean.TYPE);
        Method method2 = ChatMessageCell.class.getDeclaredMethod("setChecked", Boolean.TYPE, Boolean.TYPE, Boolean.TYPE);
        Method method3 = ChatMessageCell.class.getDeclaredMethod("updateTranslation", new Class[0]);
        Method method4 = ChatMessageCell.class.getDeclaredMethod("getCheckBoxTranslation", new Class[0]);
        Method method5 = ChatMessageCell.class.getDeclaredMethod("getNonAnimationTranslationX", Boolean.TYPE);
        Method method6 = ChatMessageCell.class.getDeclaredMethod("drawCheckBox", Canvas.class);
        method.setAccessible(true);
        method2.setAccessible(true);
        method3.setAccessible(true);
        method4.setAccessible(true);
        method5.setAccessible(true);
        method6.setAccessible(true);
        HOOKS.add(XposedBridge.hookMethod((Member)method, (XC_MethodHook)new XC_MethodHook(50){

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    methodHookParam = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(methodHookParam.getMessageObject())) {
                        return;
                    }
                    if (Main.shouldSuppressSelectionCheckboxes((ChatMessageCell)methodHookParam)) {
                        Main.suppressSelectionCheckboxes((ChatMessageCell)methodHookParam, true, Main.shouldPreserveAlbumMediaCheckbox((ChatMessageCell)methodHookParam));
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    ChatMessageCell chatMessageCell = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(chatMessageCell.getMessageObject())) {
                        return;
                    }
                    if (Boolean.TRUE.equals(methodHookParam.args[0]) && Main.shouldSuppressSelectionCheckboxes(chatMessageCell) && !Main.shouldPreserveAlbumMediaCheckbox(chatMessageCell)) {
                        methodHookParam.args[0] = false;
                        methodHookParam.args[1] = false;
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
        HOOKS.add(XposedBridge.hookMethod((Member)method2, (XC_MethodHook)new XC_MethodHook(50){

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    methodHookParam = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(methodHookParam.getMessageObject())) {
                        return;
                    }
                    if (Main.shouldSuppressSelectionCheckboxes((ChatMessageCell)methodHookParam)) {
                        Main.suppressSelectionCheckboxes((ChatMessageCell)methodHookParam, true, Main.shouldPreserveAlbumMediaCheckbox((ChatMessageCell)methodHookParam));
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
        HOOKS.add(XposedBridge.hookMethod((Member)method3, (XC_MethodHook)new XC_MethodHook(50){

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    methodHookParam = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(methodHookParam.getMessageObject())) {
                        return;
                    }
                    if (Main.shouldSuppressSelectionCheckboxes((ChatMessageCell)methodHookParam)) {
                        Main.suppressSelectionCheckboxes((ChatMessageCell)methodHookParam, false, Main.shouldPreserveAlbumMediaCheckbox((ChatMessageCell)methodHookParam));
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    methodHookParam = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(methodHookParam.getMessageObject())) {
                        return;
                    }
                    if (Main.shouldSuppressSelectionCheckboxes((ChatMessageCell)methodHookParam)) {
                        Main.suppressSelectionCheckboxes((ChatMessageCell)methodHookParam, false, Main.shouldPreserveAlbumMediaCheckbox((ChatMessageCell)methodHookParam));
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
        HOOKS.add(XposedBridge.hookMethod((Member)method4, (XC_MethodHook)new XC_MethodHook(50){

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    ChatMessageCell chatMessageCell = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(chatMessageCell.getMessageObject())) {
                        return;
                    }
                    if (Main.shouldSuppressSelectionCheckboxes(chatMessageCell)) {
                        methodHookParam.setResult((Object)Float.valueOf(0.0f));
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
        HOOKS.add(XposedBridge.hookMethod((Member)method5, (XC_MethodHook)new XC_MethodHook(50){

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    methodHookParam = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(methodHookParam.getMessageObject())) {
                        return;
                    }
                    if (Main.shouldSuppressSelectionCheckboxes((ChatMessageCell)methodHookParam)) {
                        Main.suppressSelectionCheckboxes((ChatMessageCell)methodHookParam, false, Main.shouldPreserveAlbumMediaCheckbox((ChatMessageCell)methodHookParam));
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    methodHookParam = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(methodHookParam.getMessageObject())) {
                        return;
                    }
                    if (Main.shouldSuppressSelectionCheckboxes((ChatMessageCell)methodHookParam)) {
                        Main.suppressSelectionCheckboxes((ChatMessageCell)methodHookParam, false, Main.shouldPreserveAlbumMediaCheckbox((ChatMessageCell)methodHookParam));
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
        HOOKS.add(XposedBridge.hookMethod((Member)method6, (XC_MethodHook)new XC_MethodHook(50){

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    ChatMessageCell chatMessageCell = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(chatMessageCell.getMessageObject())) {
                        return;
                    }
                    if (Main.shouldSuppressSelectionCheckboxes(chatMessageCell)) {
                        Main.suppressSelectionCheckboxes(chatMessageCell, false, Main.shouldPreserveAlbumMediaCheckbox(chatMessageCell));
                        methodHookParam.setResult(null);
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
    }

    private static void hookShareButton() throws NoSuchMethodException {
        Method method = ChatMessageCell.class.getDeclaredMethod("checkNeedDrawShareButton", MessageObject.class);
        method.setAccessible(true);
        HOOKS.add(XposedBridge.hookMethod((Member)method, (XC_MethodHook)new XC_MethodHook(50){

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    MessageObject messageObject = (MessageObject)methodHookParam.args[0];
                    ChatMessageCell chatMessageCell = (ChatMessageCell)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(messageObject)) {
                        return;
                    }
                    if (Main.shouldSuppressWideActions(chatMessageCell, messageObject)) {
                        methodHookParam.setResult((Object)false);
                        Main.suppressWideActionState(chatMessageCell);
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
    }

    private static void hookSideActions() throws NoSuchMethodException {
        Method method = ChatMessageCell.class.getDeclaredMethod("drawSideButton", Canvas.class, Boolean.TYPE);
        Method method2 = ChatMessageCell.class.getDeclaredMethod("drawSummarizeButton", Canvas.class);
        method.setAccessible(true);
        method2.setAccessible(true);
        HOOKS.add(XposedBridge.hookMethod((Member)method, (XC_MethodHook)new XC_MethodHook(50){

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    ChatMessageCell chatMessageCell = (ChatMessageCell)methodHookParam.thisObject;
                    if (Main.shouldWidenMessage(chatMessageCell.getMessageObject()) && Main.shouldSuppressWideActions(chatMessageCell)) {
                        Main.suppressWideActionState(chatMessageCell);
                        methodHookParam.setResult(null);
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
        HOOKS.add(XposedBridge.hookMethod((Member)method2, (XC_MethodHook)new XC_MethodHook(50){

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    ChatMessageCell chatMessageCell = (ChatMessageCell)methodHookParam.thisObject;
                    if (Main.shouldWidenMessage(chatMessageCell.getMessageObject()) && Main.shouldSuppressWideActions(chatMessageCell)) {
                        Main.suppressWideActionState(chatMessageCell);
                        methodHookParam.setResult(null);
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }));
    }

    private static void hookTextLayout() throws Exception {
        Method method = Main.findGenerateLayoutMethod();
        if (method == null) {
            return;
        }
        method.setAccessible(true);
        HOOKS.add(XposedBridge.hookMethod((Member)method, (XC_MethodHook)new XC_MethodHook(50){

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                block6: {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    methodHookParam = (MessageObject)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage((MessageObject)methodHookParam)) {
                        return;
                    }
                    LayoutContext layoutContext = (LayoutContext)LAYOUT_CONTEXT.get();
                    if (layoutContext == null) break block6;
                    try {
                        int n;
                        if (layoutContext.messageObject == methodHookParam && Main.isTextMessage((MessageObject)methodHookParam) && Main.shouldForceFillTextBubble((MessageObject)methodHookParam) && Main.shouldFillTextBubble(methodHookParam.textWidth, n = Main.getWideTextWidth(layoutContext.cell, (MessageObject)methodHookParam))) {
                            methodHookParam.textWidth = n;
                        }
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
            }
        }));
    }

    private static void hookTextWidth() throws NoSuchMethodException {
        HOOKS.add(XposedBridge.hookMethod((Member)MessageObject.class.getDeclaredMethod("getMaxMessageTextWidth", new Class[0]), (XC_MethodHook)new XC_MethodHook(50){

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                block7: {
                    if (!Main.isAnyEnabled()) {
                        return;
                    }
                    MessageObject messageObject = (MessageObject)methodHookParam.thisObject;
                    if (!Main.shouldWidenMessage(messageObject)) {
                        return;
                    }
                    LayoutContext layoutContext = (LayoutContext)LAYOUT_CONTEXT.get();
                    if (layoutContext == null) break block7;
                    if (layoutContext.messageObject != messageObject) break block7;
                    Object object = methodHookParam.getResult();
                    int n = object instanceof Integer ? (Integer)object : 0;
                    int n2 = Main.getWideTextWidth(layoutContext.cell, messageObject);
                    if (n2 <= n) break block7;
                    try {
                        methodHookParam.setResult((Object)n2);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
            }
        }));
    }

    private static void invalidateGroupedCaptionWidthIfNeeded(MessageObject.GroupedMessages groupedMessages, int n) {
        Integer n2 = WIDE_GROUP_LAYOUT_WIDTHS.get(groupedMessages);
        if (n2 == null || n2 != n) {
            WIDE_GROUP_LAYOUT_WIDTHS.put(groupedMessages, n);
            groupedMessages.cachedWidthForCaption = -1;
        }
    }

    private static boolean isAnyEnabled() {
        boolean bl = privateChatsEnabled || botsEnabled || groupsEnabled || channelsEnabled;
        return bl;
    }

    private static boolean isBotCached(long l) {
        if (l <= 0L) {
            return false;
        }
        Boolean bl = botCache.get(l);
        if (bl != null) {
            return bl;
        }
        boolean bl2 = Main.isBotUncached(l);
        if (botCache.size() > 200) {
            botCache.clear();
        }
        botCache.put(l, bl2);
        return bl2;
    }

    private static boolean isBotUncached(long l) {
        TLRPC.User user;
        block3: {
            try {
                user = MessagesController.getInstance((int)UserConfig.selectedAccount).getUser(Long.valueOf(l));
                if (user != null) break block3;
                return false;
            }
            catch (Throwable throwable) {
                return false;
            }
        }
        boolean bl = user.getClass().getField("bot").getBoolean(user);
        return bl;
    }

    private static boolean isChannelCached(long l) {
        if (l >= 0L) {
            return false;
        }
        Boolean bl = channelCache.get(l);
        if (bl != null) {
            return bl;
        }
        boolean bl2 = Main.isChannelUncached(l);
        if (channelCache.size() > 200) {
            channelCache.clear();
        }
        channelCache.put(l, bl2);
        return bl2;
    }

    private static boolean isChannelUncached(long l) {
        boolean bl;
        TLRPC.Chat chat;
        boolean bl2 = false;
        try {
            chat = MessagesController.getInstance((int)UserConfig.selectedAccount).getChat(Long.valueOf(-l));
            if (chat == null) {
                return false;
            }
            bl = bl2;
        }
        catch (Throwable throwable) {
            return false;
        }
        if (ChatObject.isChannel((TLRPC.Chat)chat)) {
            boolean bl3 = chat.megagroup;
            bl = bl2;
            if (!bl3) {
                bl = true;
            }
        }
        return bl;
    }

    private static boolean isExpandableMediaMessage(MessageObject messageObject) {
        boolean bl;
        int n = messageObject.type;
        boolean bl2 = bl = true;
        if (n != 1) {
            bl2 = bl;
            if (messageObject.type != 20) {
                bl2 = bl;
                if (messageObject.type != 3) {
                    bl2 = messageObject.type == 8 ? bl : false;
                }
            }
        }
        return bl2;
    }

    private static boolean isGroupedLayout(MessageObject.GroupedMessages groupedMessages) {
        block2: {
            boolean bl;
            block4: {
                block3: {
                    boolean bl2 = false;
                    if (groupedMessages == null) break block2;
                    if (groupedMessages.posArray != null && groupedMessages.posArray.size() > 1) break block3;
                    bl = bl2;
                    if (groupedMessages.messages == null) break block4;
                    bl = bl2;
                    if (groupedMessages.messages.size() <= 1) break block4;
                }
                bl = true;
            }
            return bl;
        }
        return false;
    }

    private static boolean isSideMenuEnabled(ChatMessageCell chatMessageCell, MessageObject messageObject) {
        boolean bl = true;
        if (chatMessageCell != null) {
            try {
                boolean bl2;
                if (chatMessageCell.isSideMenued || (bl2 = chatMessageCell.isSideMenuEnabled)) {
                    return true;
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        if (messageObject == null || !messageObject.sideMenuEnabled) {
            bl = false;
        }
        return bl;
    }

    private static boolean isTextMessage(MessageObject messageObject) {
        boolean bl = messageObject.type == 0 || messageObject.type == 36 || messageObject.type == 24;
        return bl;
    }

    private static boolean isTooShort(MessageObject object) {
        block10: {
            int n;
            boolean bl;
            int n2;
            block8: {
                block9: {
                    n2 = minChars;
                    bl = false;
                    boolean bl2 = false;
                    if (n2 <= 0) {
                        return false;
                    }
                    try {
                        if (((MessageObject)object).type != 0) break block8;
                        object = ((MessageObject)object).messageText;
                        if (object == null) break block9;
                        bl = bl2;
                    }
                    catch (Throwable throwable) {
                        return false;
                    }
                    if (object.length() < minChars) {
                        bl = true;
                    }
                    return bl;
                }
                return true;
            }
            object = ((MessageObject)object).caption;
            if (object == null) break block10;
            if (object.length() <= 0) break block10;
            n2 = object.length();
            if (n2 < (n = minChars)) {
                bl = true;
            }
            return bl;
        }
        return false;
    }

    private static boolean isWideGroupedMediaLayout(MessageObject.GroupedMessages groupedMessages) {
        boolean bl = Main.isGroupedLayout(groupedMessages) && !groupedMessages.isDocuments;
        return bl;
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public static void load() throws Exception {
        synchronized (Main.class) {
            boolean bl = loaded;
            if (bl) {
                return;
            }
            Main.resolveFields();
            Main.hookNeedDrawAvatar();
            Main.hookTextWidth();
            Main.hookTextLayout();
            Main.hookParentWidth();
            Main.hookShareButton();
            Main.hookMediaLayout();
            Main.hookMessageSize();
            Main.hookGroupPhotosWidth();
            Main.hookSelectionCheckboxes();
            Main.hookSideActions();
            loaded = true;
            return;
        }
    }

    private static boolean resetCheckBoxDrawable(Field object, Object object2) {
        block5: {
            if (object != null && object2 != null) {
                boolean bl;
                block6: {
                    object = ((Field)object).get(object2);
                    if (!(object instanceof CheckBoxBase)) break block5;
                    if (!(object = (CheckBoxBase)object).isChecked() && object.getProgress() == 0.0f) {
                        bl = false;
                        break block6;
                    }
                    bl = true;
                }
                try {
                    object.cancelCheckAnimator();
                    object.setChecked(false, false);
                    object.setProgress(0.0f);
                    return bl;
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }
        return false;
    }

    private static void resolveFields() {
        Main.getLayoutCreatedField();
        Main.getDrawSideButtonField();
        Main.getDrawSideButton2Field();
        Main.getDrawSummarizeButtonField();
        Main.getSideButtonVisibleField();
        Main.getSideButtonPressedField();
        Main.getSummarizeButtonPressedField();
        Main.getPressedSideButtonField();
        Main.getCheckBoxVisibleField();
        Main.getCheckBoxAnimationInProgressField();
        Main.getCheckBoxAnimationProgressField();
        Main.getCheckBoxTranslationField();
        Main.getDrawMediaCheckBoxField();
        Main.getCheckBoxField();
        Main.getMediaCheckBoxField();
    }

    private static void restoreWideGroupLayoutIfNeeded(MessageObject.GroupedMessages groupedMessages) {
        if (groupedMessages == null) {
            return;
        }
        Main.clearWideGroupCaptionWidthIfNeeded(groupedMessages);
        GroupWidthSnapshot groupWidthSnapshot = GROUP_WIDTH_SNAPSHOTS.remove(groupedMessages);
        if (groupWidthSnapshot != null && groupWidthSnapshot.restore()) {
            groupedMessages.cachedWidthForCaption = -1;
        }
    }

    private static void restoreWideGroupLayouts() {
        for (Map.Entry<MessageObject.GroupedMessages, GroupWidthSnapshot> entry : new ArrayList<Map.Entry<MessageObject.GroupedMessages, GroupWidthSnapshot>>(GROUP_WIDTH_SNAPSHOTS.entrySet())) {
            GroupWidthSnapshot groupWidthSnapshot;
            MessageObject.GroupedMessages groupedMessages;
            try {
                groupedMessages = entry.getKey();
                groupWidthSnapshot = entry.getValue();
                if (groupWidthSnapshot == null) continue;
            }
            catch (Throwable throwable) {}
            if (!groupWidthSnapshot.restore() || groupedMessages == null) continue;
            groupedMessages.cachedWidthForCaption = -1;
        }
        Iterator<Map.Entry<MessageObject.GroupedMessages, GroupWidthSnapshot>> iterator = new ArrayList<MessageObject.GroupedMessages>(WIDE_GROUP_LAYOUT_WIDTHS.keySet()).iterator();
        while (iterator.hasNext()) {
            Main.clearWideGroupCaptionWidthIfNeeded((MessageObject.GroupedMessages)iterator.next());
        }
        WIDE_GROUP_LAYOUT_WIDTHS.clear();
        GROUP_WIDTH_SNAPSHOTS.clear();
    }

    private static void setBooleanField(Field field, Object object, boolean bl) {
        if (field != null && object != null) {
            try {
                field.setBoolean(object, bl);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return;
        }
    }

    public static void setBotsEnabled(boolean bl) {
        botsEnabled = bl;
    }

    public static void setChannelsEnabled(boolean bl) {
        channelsEnabled = bl;
    }

    private static void setFloatField(Field field, Object object, float f) {
        if (field != null && object != null) {
            try {
                field.setFloat(object, f);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return;
        }
    }

    public static void setGroupsEnabled(boolean bl) {
        groupsEnabled = bl;
    }

    public static void setHideChannelAvatars(boolean bl) {
        hideChannelAvatars = bl;
    }

    public static void setIgnoreOwnMessages(boolean bl) {
        ignoreOwnMessages = bl;
    }

    private static void setIntField(Field field, Object object, int n) {
        if (field != null && object != null) {
            try {
                field.setInt(object, n);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return;
        }
    }

    private static void setLayoutCreated(MessageObject messageObject, boolean bl) {
        try {
            Main.getLayoutCreatedField().setBoolean(messageObject, bl);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static void setMinChars(int n) {
        minChars = n;
    }

    public static void setPrivateChatsEnabled(boolean bl) {
        privateChatsEnabled = bl;
    }

    private static boolean shouldExpandImage(int n, int n2) {
        boolean bl = n > 0 && n2 > 0 && n >= n2;
        return bl;
    }

    private static boolean shouldFillTextBubble(int n, int n2) {
        int n3 = minChars;
        boolean bl = true;
        boolean bl2 = true;
        if (n3 <= 0) {
            if (n2 <= 0) {
                bl2 = false;
            }
            return bl2;
        }
        bl2 = n2 > 0 && (float)n >= (float)n2 * 0.68f ? bl : false;
        return bl2;
    }

    private static boolean shouldForceFillTextBubble(MessageObject messageObject) {
        int n = minChars;
        boolean bl = true;
        if (n <= 0) {
            return true;
        }
        if (Main.getAvatarInset(messageObject) != 0) {
            bl = false;
        }
        return bl;
    }

    private static boolean shouldIgnoreMessage(MessageObject messageObject) {
        block5: {
            if (!ignoreOwnMessages) {
                return false;
            }
            if (messageObject == null) {
                return false;
            }
            try {
                if (messageObject.isOutOwner()) break block5;
                return false;
            }
            catch (Throwable throwable) {
                return false;
            }
        }
        boolean bl = Main.isChannelCached(messageObject.getDialogId());
        return !bl;
    }

    private static boolean shouldPreserveAlbumMediaCheckbox(ChatMessageCell chatMessageCell) {
        if (chatMessageCell != null && chatMessageCell.getCurrentPosition() != null) {
            return Main.isWideGroupedMediaLayout(chatMessageCell.getCurrentMessagesGroup());
        }
        return false;
    }

    private static boolean shouldRelayoutWideText(ChatMessageCell object, MessageObject messageObject) {
        if (!Main.isTextMessage(messageObject)) {
            return false;
        }
        int n = Main.getWideTextWidth((ChatMessageCell)object, messageObject);
        object = WIDE_TEXT_LAYOUT_WIDTHS.get(messageObject);
        if (object != null && (Integer)object == n) {
            return false;
        }
        WIDE_TEXT_LAYOUT_WIDTHS.put(messageObject, n);
        return true;
    }

    private static boolean shouldRenderWideBase(ChatMessageCell chatMessageCell, MessageObject messageObject, MessageObject.GroupedMessages groupedMessages) {
        if (messageObject == null) {
            return false;
        }
        if (messageObject.messageOwner == null) {
            return false;
        }
        if (messageObject.messageOwner.peer_id == null) {
            return false;
        }
        if (messageObject.preview) {
            return false;
        }
        if (messageObject.isSponsored()) {
            return false;
        }
        if (messageObject.type == 27) {
            return false;
        }
        return Main.hasSeparateCommentsButton(chatMessageCell, messageObject, groupedMessages) ^ true;
    }

    private static boolean shouldRenderWideCell(ChatMessageCell chatMessageCell, MessageObject messageObject) {
        MessageObject.GroupedMessages groupedMessages = chatMessageCell != null ? chatMessageCell.getCurrentMessagesGroup() : null;
        boolean bl = Main.shouldRenderWideBase(chatMessageCell, messageObject, groupedMessages);
        boolean bl2 = false;
        if (bl) {
            if (!Main.hasActiveGroupedLayout(chatMessageCell) || Main.isWideGroupedMediaLayout(chatMessageCell.getCurrentMessagesGroup())) {
                bl2 = true;
            }
            return bl2;
        }
        return false;
    }

    private static boolean shouldRenderWideLayout(ChatMessageCell chatMessageCell, MessageObject messageObject, MessageObject.GroupedMessages groupedMessages) {
        boolean bl = Main.shouldRenderWideBase(chatMessageCell, messageObject, groupedMessages);
        boolean bl2 = false;
        if (bl) {
            if (!Main.isGroupedLayout(groupedMessages) || Main.isWideGroupedMediaLayout(groupedMessages)) {
                bl2 = true;
            }
            return bl2;
        }
        return false;
    }

    private static boolean shouldSuppressSelectionCheckboxes(ChatMessageCell chatMessageCell) {
        boolean bl = chatMessageCell != null && chatMessageCell.getMessageObject() != null;
        return bl;
    }

    private static boolean shouldSuppressWideActions(ChatMessageCell chatMessageCell) {
        boolean bl = chatMessageCell != null && Main.shouldSuppressWideActions(chatMessageCell, chatMessageCell.getMessageObject());
        return bl;
    }

    private static boolean shouldSuppressWideActions(ChatMessageCell chatMessageCell, MessageObject messageObject) {
        MessageObject.GroupedMessages groupedMessages = chatMessageCell != null ? chatMessageCell.getCurrentMessagesGroup() : null;
        return Main.shouldRenderWideBase(chatMessageCell, messageObject, groupedMessages);
    }

    private static boolean shouldWidenDialog(long l) {
        if (l > 0L) {
            if (Main.isBotCached(l)) {
                return botsEnabled;
            }
            return privateChatsEnabled;
        }
        if (Main.isChannelCached(l)) {
            return channelsEnabled;
        }
        return groupsEnabled;
    }

    private static boolean shouldWidenMessage(MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        if (!Main.shouldWidenDialog(messageObject.getDialogId())) {
            return false;
        }
        return !Main.shouldIgnoreMessage(messageObject);
    }

    private static void suppressSelectionCheckboxes(ChatMessageCell chatMessageCell, boolean bl, boolean bl2) {
        block9: {
            boolean bl3;
            block10: {
                block11: {
                    Field field = Main.getCheckBoxVisibleField();
                    boolean bl4 = false;
                    boolean bl5 = Main.getBooleanField(field, chatMessageCell, false) || Main.getBooleanField(Main.getCheckBoxAnimationInProgressField(), chatMessageCell, false) || Main.getFloatField(Main.getCheckBoxAnimationProgressField(), chatMessageCell, 0.0f) != 0.0f || Main.getIntField(Main.getCheckBoxTranslationField(), chatMessageCell, 0) != 0 || !bl2 && Main.getBooleanField(Main.getDrawMediaCheckBoxField(), chatMessageCell, false);
                    Main.setBooleanField(Main.getCheckBoxVisibleField(), chatMessageCell, false);
                    Main.setBooleanField(Main.getCheckBoxAnimationInProgressField(), chatMessageCell, false);
                    Main.setFloatField(Main.getCheckBoxAnimationProgressField(), chatMessageCell, 0.0f);
                    Main.setIntField(Main.getCheckBoxTranslationField(), chatMessageCell, 0);
                    if (!bl2) {
                        Main.setBooleanField(Main.getDrawMediaCheckBoxField(), chatMessageCell, false);
                    }
                    bl5 = Main.resetCheckBoxDrawable(Main.getCheckBoxField(), chatMessageCell) || bl5;
                    bl3 = bl5;
                    if (bl2) break block10;
                    if (Main.resetCheckBoxDrawable(Main.getMediaCheckBoxField(), chatMessageCell)) break block11;
                    bl3 = bl4;
                    if (!bl5) break block10;
                }
                bl3 = true;
            }
            if (bl3) {
                chatMessageCell.invalidate();
                if (!bl) break block9;
                try {
                    chatMessageCell.updateTranslation();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }
    }

    private static void suppressWideActionState(ChatMessageCell chatMessageCell) {
        Main.setIntField(Main.getDrawSideButtonField(), chatMessageCell, 0);
        Main.setIntField(Main.getDrawSideButton2Field(), chatMessageCell, 0);
        Main.setBooleanField(Main.getDrawSummarizeButtonField(), chatMessageCell, false);
        Main.setBooleanField(Main.getSideButtonVisibleField(), chatMessageCell, false);
        Main.setBooleanField(Main.getSideButtonPressedField(), chatMessageCell, false);
        Main.setBooleanField(Main.getSummarizeButtonPressedField(), chatMessageCell, false);
        Main.setIntField(Main.getPressedSideButtonField(), chatMessageCell, 0);
    }

    /*
     * WARNING - Removed back jump from a try to a catch block - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public static void unload() {
        synchronized (Main.class) {
            Main.restoreWideGroupLayouts();
            Object object = new ArrayList(HOOKS);
            object = ((ArrayList)object).iterator();
            while (object.hasNext()) {
                ((XC_MethodHook.Unhook)object.next()).unhook();
            }
            HOOKS.clear();
            LAYOUT_CONTEXT.remove();
            SUPPRESS_PARENT_WIDTH_HOOK.remove();
            WIDE_TEXT_LAYOUT_WIDTHS.clear();
            WIDE_GROUP_LAYOUT_WIDTHS.clear();
            GROUP_WIDTH_SNAPSHOTS.clear();
            channelCache.clear();
            botCache.clear();
            generateLayoutMethod = null;
            loaded = false;
            return;
        }
    }

    private static final class GroupWidthSnapshot {
        private final int[] originalPw;
        private final List<MessageObject.GroupedMessagePosition> positions = new ArrayList<MessageObject.GroupedMessagePosition>();

        GroupWidthSnapshot(MessageObject.GroupedMessages groupedMessages) {
            int n = groupedMessages != null && groupedMessages.posArray != null ? groupedMessages.posArray.size() : 0;
            this.originalPw = new int[n];
            for (int i = 0; i < n; ++i) {
                MessageObject.GroupedMessagePosition groupedMessagePosition = (MessageObject.GroupedMessagePosition)groupedMessages.posArray.get(i);
                this.positions.add(groupedMessagePosition);
                int[] nArray = this.originalPw;
                int n2 = groupedMessagePosition != null ? groupedMessagePosition.pw : 0;
                nArray[i] = n2;
            }
        }

        private static int getWidePositionWidth(MessageObject.GroupedMessagePosition groupedMessagePosition, int n) {
            if (groupedMessagePosition.leftSpanOffset == 0 && groupedMessagePosition.siblingHeights == null && groupedMessagePosition.spanSize > n && groupedMessagePosition.spanSize <= 1000) {
                return groupedMessagePosition.spanSize;
            }
            return n;
        }

        boolean apply() {
            boolean bl = false;
            for (int i = 0; i < this.positions.size(); ++i) {
                MessageObject.GroupedMessagePosition groupedMessagePosition = this.positions.get(i);
                boolean bl2 = bl;
                if (groupedMessagePosition != null) {
                    int n = GroupWidthSnapshot.getWidePositionWidth(groupedMessagePosition, this.originalPw[i]);
                    bl2 = bl;
                    if (groupedMessagePosition.pw != n) {
                        groupedMessagePosition.pw = n;
                        bl2 = true;
                    }
                }
                bl = bl2;
            }
            return bl;
        }

        boolean matches(MessageObject.GroupedMessages groupedMessages) {
            if (groupedMessages != null && groupedMessages.posArray != null && groupedMessages.posArray.size() == this.positions.size()) {
                for (int i = 0; i < this.positions.size(); ++i) {
                    if (groupedMessages.posArray.get(i) == this.positions.get(i)) continue;
                    return false;
                }
                return true;
            }
            return false;
        }

        boolean restore() {
            boolean bl = false;
            for (int i = 0; i < this.positions.size(); ++i) {
                MessageObject.GroupedMessagePosition groupedMessagePosition = this.positions.get(i);
                boolean bl2 = bl;
                if (groupedMessagePosition != null) {
                    int n = this.originalPw[i];
                    bl2 = bl;
                    if (groupedMessagePosition.pw != n) {
                        groupedMessagePosition.pw = n;
                        bl2 = true;
                    }
                }
                bl = bl2;
            }
            return bl;
        }
    }

    private static final class LayoutContext {
        final ChatMessageCell cell;
        final boolean groupedMedia;
        final MessageObject messageObject;

        LayoutContext(ChatMessageCell chatMessageCell, MessageObject messageObject, boolean bl) {
            this.cell = chatMessageCell;
            this.messageObject = messageObject;
            this.groupedMedia = bl;
        }
    }
}


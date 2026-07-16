package com.crowmessenger.messages;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ComponentCaller;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.role.RoleManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.media.RingtoneManager;
import android.provider.Telephony;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.LruCache;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends Activity {
    static final String ACTION_MESSAGE_RECEIVED = BuildConfig.APPLICATION_ID + ".MESSAGE_RECEIVED";
    static final String EXTRA_OPEN_ADDRESS = BuildConfig.APPLICATION_ID + ".OPEN_ADDRESS";
    static final String EXTRA_MESSAGE_BODY = BuildConfig.APPLICATION_ID + ".MESSAGE_BODY";
    static final String EXTRA_MESSAGE_DATE = BuildConfig.APPLICATION_ID + ".MESSAGE_DATE";
    private static final int REQUEST_PICK_CONTACT = 30;
    private static final int REQUEST_PICK_SOUND = 31;
    private static final int REQUEST_VOICE_TEXT = 32;
    private static final int REQUEST_RUNTIME_PERMISSIONS = 33;
    private static final int REQUEST_VOICE_PERMISSION = 34;
    private static final int REQUEST_PICK_IMAGE = 35;
    private static final int REQUEST_TAKE_PHOTO = 36;
    private static final int REQUEST_EXPORT_SETTINGS = 37;
    private static final int REQUEST_IMPORT_SETTINGS = 38;
    private static final long MESSAGE_STORE_DIRECT_UPDATE_GRACE_MILLIS = 750L;
    private static final String STATE_CAMERA_URI = "state_camera_uri";
    private static final String STATE_CAMERA_ADDRESS = "state_camera_address";
    private static final String STATE_CAMERA_FOR_COMPOSE = "state_camera_for_compose";
    private static final String STATE_PICK_IMAGE_ADDRESS = "state_pick_image_address";
    private static final String STATE_PICK_IMAGE_FOR_COMPOSE = "state_pick_image_for_compose";
    private static final String STATE_PICKING_COMPOSE_CONTACT = "state_picking_compose_contact";
    private static final String STATE_PENDING_SOUND_ADDRESS = "state_pending_sound_address";
    private static final String STATE_COMPOSE_DRAFT = "state_compose_draft";
    private static final String STATE_COMPOSE_RECIPIENT_ADDRESSES = "state_compose_recipient_addresses";
    private static final String STATE_COMPOSE_RECIPIENT_NAMES = "state_compose_recipient_names";
    private static final int RECENT_THREAD_LIMIT = 140;
    private static final int COMPOSER_BOTTOM_PADDING_DP = 12;
    private static final int COMPOSER_KEYBOARD_BOTTOM_PADDING_DP = 0;
    private static final int HEADER_BAR_HEIGHT_DP = 56;
    private static final int INBOX_ACTION_HEIGHT_DP = 52;
    private static final int INBOX_ACTION_BOTTOM_MARGIN_DP = 72;
    private static final int KEYBOARD_CONTENT_BOTTOM_PADDING_DP = 6;
    private static final int KEYBOARD_SCROLL_GAP_DP = 4;
    private static final int SCREEN_CACHE_LIMIT = 12;
    private static final int THREAD_PREFETCH_COUNT = 6;
    private static final int INITIAL_THREAD_RENDER_LIMIT = 18;
    private static final long FULL_THREAD_RENDER_DELAY_MILLIS = 48L;
    private static final long DRAFT_SAVE_DELAY_MILLIS = 250L;
    private static final long SEARCH_DEBOUNCE_MILLIS = 180L;
    private static final long INBOX_REFRESH_THROTTLE_MILLIS = 15_000L;
    private static final String IMAGE_SEND_JOB_KEY = "crow-main-picture-send";
    private static volatile ImageSendSession retainedImageSendSession;
    private static final AtomicBoolean STARTUP_MAINTENANCE_RUNNING = new AtomicBoolean();
    private static final int[] BOTTOM_SETTLE_DELAYS_MS = new int[] { 0, 80, 180 };
    private static final int[] COMPOSER_SETTLE_DELAYS_MS = new int[] { 0, 50, 120, 240, 420 };
    private static final String[] MESSAGE_REFRESH_PERMISSIONS = new String[] {
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.RECEIVE_WAP_PUSH,
            Manifest.permission.READ_PHONE_STATE
    };
    private static final int MINT = Color.rgb(51, 250, 165);
    private static final int CYAN = Color.rgb(0, 255, 234);
    private static final int BLACK = Color.BLACK;
    private static final int SURFACE = Color.rgb(24, 24, 24);
    private static final int CHAT_HEADER = Color.rgb(37, 39, 41);
    private static final int DIVIDER = Color.rgb(62, 64, 66);
    private static final int TEXT = Color.rgb(245, 245, 245);
    private static final int MUTED = Color.rgb(165, 165, 170);
    private static final int MAX_ATTACHED_IMAGES = 10;

    private enum ScreenMode {
        INBOX,
        CHAT,
        COMPOSE,
        SPAM_RULES,
        PICTURE,
        CONVERSATION_INFO,
        TRASH,
        MEDIA
    }

    private enum ImageSendOrigin {
        COMPOSE,
        CHAT,
        RETRY
    }

    private LinearLayout root;
    private LinearLayout inboxList;
    private LinearLayout activeMessagesList;
    private ScrollView activeScrollView;
    private Button activeJumpToBottomButton;
    private LinearLayout cachedInboxRoot;
    private LinearLayout cachedInboxList;
    private boolean cachedInboxBlocked;
    private String cachedInboxQuery = "";
    private String renderedInboxCacheKey = "";
    private LinearLayout cachedChatRoot;
    private LinearLayout cachedChatMessagesList;
    private ScrollView cachedChatScrollView;
    private Button cachedChatJumpToBottomButton;
    private String cachedChatAddress = "";
    private boolean cachedChatBlockedOnly;
    private Conversation activeConversation;
    private ScreenMode screenMode = ScreenMode.INBOX;
    private String pendingSoundAddress = "";
    private boolean showingBlocked;
    private boolean activeThreadBlockedOnly;
    private final ArrayList<Uri> pendingImageUris = new ArrayList<>();
    private String pendingImageAddress = "";
    private final ArrayList<Uri> pendingComposeImageUris = new ArrayList<>();
    private final MmsImageSendCoordinator.Listener imageSendListener = this::onImageSendCompleted;
    private ImageSendUi activeImageSendUi;
    private Dialog activeImageSendDialog;
    private Uri pendingCameraImageUri;
    private String pendingCameraAddress = "";
    private boolean pendingCameraForCompose;
    private String pendingPickImageAddress = "";
    private boolean pendingPickImageForCompose;
    private boolean scrollThreadToBottomOnResume;
    private String searchQuery = "";
    private String threadSearchQuery = "";
    private ScreenMode pictureReturnScreen = ScreenMode.CHAT;
    private String composeDraft = "";
    private final ArrayList<ComposeRecipient> composeRecipients = new ArrayList<>();
    private boolean pickingComposeContact;
    private EditText activeVoiceInput;
    private EditText pendingVoiceInput;
    private int inboxLoadGeneration;
    private int threadLoadGeneration;
    private int threadRenderGeneration;
    private int activeThreadMessageLimit = RECENT_THREAD_LIMIT;
    private boolean activeThreadHasOlderMessages;
    private boolean activityResumed;
    private boolean pendingMessageRefresh;
    private String pendingIncomingAddress = "";
    private String pendingIncomingBody = "";
    private long pendingIncomingDateMillis;
    private long normalInboxLoadedAtMillis;
    private long blockedInboxLoadedAtMillis;
    private final ExecutorService inboxLoader = newSingleThreadLoader("crow-inbox-loader");
    private final ExecutorService threadLoader = newSingleThreadLoader("crow-thread-loader");
    private final ExecutorService threadPrefetchLoader = newSingleThreadLoader("crow-thread-prefetch");
    private final ExecutorService stateWriter = newSingleThreadLoader("crow-state-writer");
    private final Set<String> threadPrefetches = ConcurrentHashMap.newKeySet();
    private final Set<String> locallyReadAddresses = ConcurrentHashMap.newKeySet();
    private Future<?> inboxLoadTask;
    private Future<?> threadLoadTask;
    private final Handler draftSaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable draftSaveTask = this::persistPendingDraft;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Handler messageStoreHandler = new Handler(Looper.getMainLooper());
    private final Runnable messageStoreRefreshTask = this::refreshAfterMessageStoreChange;
    private final ContentObserver messageStoreObserver = new ContentObserver(messageStoreHandler) {
        @Override
        public void onChange(boolean selfChange) {
            scheduleMessageStoreRefresh();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            scheduleMessageStoreRefresh();
        }
    };
    private boolean messageStoreObserverRegistered;
    private long lastDirectMessageUpdateUptimeMillis;
    private final Runnable inboxSearchTask = () -> {
        if (screenMode == ScreenMode.INBOX && inboxList != null) {
            refreshInboxList(true);
        }
    };
    private String pendingDraftAddress = "";
    private String pendingDraftBody = "";
    private final LruCache<String, List<Conversation>> inboxRowsCache = new LruCache<>(SCREEN_CACHE_LIMIT);
    private final LruCache<String, List<ChatMessage>> threadRowsCache = new LruCache<>(SCREEN_CACHE_LIMIT);
    private final LruCache<String, Integer> imageHeightCache = new LruCache<>(64);
    private final BroadcastReceiver messageUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            lastDirectMessageUpdateUptimeMillis = SystemClock.uptimeMillis();
            messageStoreHandler.removeCallbacks(messageStoreRefreshTask);
            String address = intent == null ? "" : intent.getStringExtra(EXTRA_OPEN_ADDRESS);
            String body = intent == null ? "" : intent.getStringExtra(EXTRA_MESSAGE_BODY);
            long dateMillis = intent == null ? 0L : intent.getLongExtra(EXTRA_MESSAGE_DATE, 0L);
            if (MessageUpdateBroadcaster.isIncomingUpdate(intent)) {
                removeMatchingAddress(locallyReadAddresses, address);
            }
            if (!activityResumed) {
                rememberPendingIncoming(address, body, dateMillis);
                return;
            }
            if (screenMode == ScreenMode.CHAT
                    && activeConversation != null
                    && (TextUtils.isEmpty(address) || sameAddress(activeConversation.address, address))) {
                boolean wasNearBottom = isScrollNearBottom(activeScrollView, dp(120));
                showIncomingSmsImmediately(address, body, dateMillis, wasNearBottom);
                if (!wasNearBottom) {
                    showNewMessageButton();
                }
                markConversationReadAsync(activeConversation);
                refreshActiveThreadAsync(wasNearBottom);
            } else if (screenMode == ScreenMode.INBOX) {
                showIncomingConversationImmediately(address, body, dateMillis);
                refreshInboxList(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        styleSystemBars();
        requestRuntimePermissions();
        registerMessageUpdates();
        registerMessageStoreObserver();
        restoreTransientState(savedInstanceState);
        openFromIntent(getIntent());
        runStartupMaintenance();
    }

    private void runStartupMaintenance() {
        if (!beginStartupMaintenance()) {
            return;
        }
        Context appContext = getApplicationContext();
        new Thread(() -> {
            try {
                runMaintenanceStep(appContext, "MMS cleanup", () -> LocalMmsStore.cleanupAttachmentNameMessages(appContext));
                runMaintenanceStep(appContext, "MMS debug archive cleanup", () -> MmsDebugStore.trimArchivedPduFiles(appContext));
                runMaintenanceStep(appContext, "Scheduled messages", () -> ScheduledSmsReceiver.scheduleAll(appContext));
                runMaintenanceStep(appContext, "Pending MMS recovery", () -> MmsDownloadedReceiver.recoverPendingDownloads(appContext));
                runMaintenanceStep(appContext, "Unreadable MMS recovery", () -> MmsDownloadedReceiver.recoverUnreadableArchives(appContext));
                runMaintenanceStep(appContext, "MMS temporary file cleanup", () -> MmsFiles.cleanupStaleTemporaryFiles(appContext));
            } finally {
                finishStartupMaintenance();
            }
        }, "crow-startup-maintenance").start();
    }

    static boolean beginStartupMaintenance() {
        return STARTUP_MAINTENANCE_RUNNING.compareAndSet(false, true);
    }

    static void finishStartupMaintenance() {
        STARTUP_MAINTENANCE_RUNNING.set(false);
    }

    private static ExecutorService newSingleThreadLoader(String name) {
        return Executors.newSingleThreadExecutor(task -> new Thread(task, name));
    }

    private static void cancelTask(Future<?> task) {
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }

    private static void runMaintenanceStep(Context context, String label, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException ex) {
            MmsDebugStore.record(context, label + " maintenance failed: " + ex.getClass().getSimpleName());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String callerPackage = currentIntentCallerPackage();
        super.onNewIntent(intent);
        if (MmsImageSendCoordinator.isActive(IMAGE_SEND_JOB_KEY)) {
            Toast.makeText(this, "Finish the current picture message before opening another one.", Toast.LENGTH_SHORT).show();
            return;
        }
        setIntent(intent);
        if (attachKeyboardImageToOpenConversation(intent, callerPackage)) {
            setIntent(new Intent(Intent.ACTION_MAIN));
            return;
        }
        openFromIntent(intent);
    }

    private boolean attachKeyboardImageToOpenConversation(Intent intent, String callerPackage) {
        if (screenMode != ScreenMode.CHAT || activeConversation == null) {
            return false;
        }
        String inputMethod = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
        );
        if (!isImageShareFromInputMethod(intent, callerPackage, inputMethod)) {
            return false;
        }
        ArrayList<Uri> images = sharedImageUris(intent);
        if (images.isEmpty()) {
            return false;
        }
        for (Uri image : images) {
            persistSharedImagePermission(intent, image);
        }
        attachSelectedImages(images, false, activeConversation.address, false);
        return true;
    }

    private String currentIntentCallerPackage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                ComponentCaller caller = getCurrentCaller();
                if (caller != null && !TextUtils.isEmpty(caller.getPackage())) {
                    return caller.getPackage();
                }
            } catch (IllegalStateException ignored) {
            }
        }
        Uri referrer = getReferrer();
        return referrer != null && "android-app".equals(referrer.getScheme())
                ? emptyIfNull(referrer.getHost())
                : "";
    }

    static boolean isImageShareFromInputMethod(Intent intent, String callerPackage, String inputMethod) {
        if (intent == null
                || !Intent.ACTION_SEND.equals(intent.getAction())
                || TextUtils.isEmpty(intent.getType())
                || !intent.getType().startsWith("image/")
                || sharedImageUris(intent).isEmpty()) {
            return false;
        }
        String keyboardPackage = emptyIfNull(inputMethod).trim();
        int separator = keyboardPackage.indexOf('/');
        if (separator >= 0) {
            keyboardPackage = keyboardPackage.substring(0, separator);
        }
        return !TextUtils.isEmpty(keyboardPackage)
                && TextUtils.equals(keyboardPackage, emptyIfNull(callerPackage).trim());
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private void openFromIntent(Intent intent) {
        String address = intent == null ? "" : intent.getStringExtra(EXTRA_OPEN_ADDRESS);
        if (!TextUtils.isEmpty(address)) {
            showChat(cachedConversationForAddress(address), false);
            String body = intent.getStringExtra(EXTRA_MESSAGE_BODY);
            long dateMillis = intent.getLongExtra(EXTRA_MESSAGE_DATE, 0L);
            if (!TextUtils.isEmpty(body)) {
                showIncomingSmsImmediately(address, body, dateMillis, true);
            }
            return;
        }
        ComposeIntent composeIntent = composeIntent(intent);
        if (composeIntent != null) {
            composeDraft = composeIntent.body;
            composeRecipients.clear();
            deleteCameraImagesIfNeeded(pendingComposeImageUris);
            pendingComposeImageUris.clear();
            pendingComposeImageUris.addAll(composeIntent.imageUris);
            for (Uri imageUri : composeIntent.imageUris) {
                persistSharedImagePermission(intent, imageUri);
            }
            if (!TextUtils.isEmpty(composeIntent.address)) {
                composeRecipients.add(new ComposeRecipient(
                        SmsStore.displayNameForAddress(this, composeIntent.address),
                        composeIntent.address
                ));
            }
            showComposePage(false);
        } else {
            showInbox();
        }
    }

    static ComposeIntent composeIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        String action = intent.getAction();
        String address = "";
        String body = "";
        ArrayList<Uri> imageUris = new ArrayList<>();
        if (Intent.ACTION_SENDTO.equals(action)) {
            address = HeadlessSmsSendService.addressFromData(intent.getData());
            body = HeadlessSmsSendService.bodyFromIntent(intent);
            if (TextUtils.isEmpty(body) && intent.getData() != null) {
                body = bodyQueryFromData(intent.getData());
            }
        } else if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            body = HeadlessSmsSendService.bodyFromIntent(intent);
            if (!TextUtils.isEmpty(intent.getType()) && intent.getType().startsWith("image/")) {
                imageUris.addAll(sharedImageUris(intent));
            }
        } else {
            return null;
        }
        address = TextUtils.isEmpty(address) ? "" : address.trim();
        if (!TextUtils.isEmpty(address) && !AddressUtil.isSendableSmsRecipient(address)) {
            address = "";
        }
        body = TextUtils.isEmpty(body) ? "" : body.trim();
        if (TextUtils.isEmpty(address) && TextUtils.isEmpty(body) && imageUris.isEmpty()) {
            return null;
        }
        return new ComposeIntent(address, body, imageUris);
    }

    @SuppressWarnings("deprecation")
    static ArrayList<Uri> sharedImageUris(Intent intent) {
        ArrayList<Uri> images = new ArrayList<>();
        if (intent == null) {
            return images;
        }
        ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (streams != null) {
            addUniqueImageUris(images, streams);
        }
        Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream instanceof Uri) {
            addUniqueImageUri(images, (Uri) stream);
        }
        ClipData clipData = intent.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                addUniqueImageUri(images, clipData.getItemAt(index).getUri());
            }
        }
        return images;
    }

    private static void addUniqueImageUris(List<Uri> target, List<Uri> additions) {
        if (additions == null) {
            return;
        }
        for (Uri uri : additions) {
            addUniqueImageUri(target, uri);
        }
    }

    private static void addUniqueImageUri(List<Uri> target, Uri uri) {
        if (target == null
                || !isSupportedImageUri(uri)
                || target.contains(uri)
                || target.size() >= MAX_ATTACHED_IMAGES) {
            return;
        }
        target.add(uri);
    }

    static List<Uri> mergeRemainingImageUris(List<Uri> remaining, List<Uri> existing) {
        ArrayList<Uri> merged = new ArrayList<>();
        addUniqueImageUris(merged, remaining);
        addUniqueImageUris(merged, existing);
        return merged;
    }

    static List<Uri> reconcileCompletedImageUris(
            List<Uri> requested,
            List<Uri> remaining,
            List<Uri> existing
    ) {
        ArrayList<Uri> unrelated = existing == null
                ? new ArrayList<>()
                : new ArrayList<>(existing);
        if (requested != null && !requested.isEmpty()) {
            unrelated.removeAll(requested);
        }
        return mergeRemainingImageUris(remaining, unrelated);
    }

    static List<String> restoredComposeRecipientAddresses(
            String requestAddress,
            List<String> explicitRecipients,
            List<String> existingRecipients
    ) {
        ArrayList<String> restored = new ArrayList<>();
        if (existingRecipients != null && !existingRecipients.isEmpty()) {
            restored.addAll(existingRecipients);
        } else if (explicitRecipients != null && !explicitRecipients.isEmpty()) {
            restored.addAll(explicitRecipients);
        } else if (!TextUtils.isEmpty(requestAddress)) {
            restored.add(requestAddress);
        }
        return restored;
    }

    static boolean shouldHandleImageSendCompletion(
            boolean activityResumed,
            boolean activityDestroyed,
            boolean activityFinishing
    ) {
        return activityResumed && !activityDestroyed && !activityFinishing;
    }

    static boolean isSupportedImageUri(Uri uri) {
        return uri != null
                && "content".equalsIgnoreCase(uri.getScheme())
                && !TextUtils.isEmpty(uri.getAuthority());
    }

    private void persistSharedImagePermission(Intent intent, Uri uri) {
        if (intent == null || uri == null
                || (intent.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
    }

    private static String bodyQueryFromData(Uri uri) {
        if (uri == null) {
            return "";
        }
        String value = uri.getSchemeSpecificPart();
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        int queryStart = value.indexOf('?');
        if (queryStart < 0 || queryStart + 1 >= value.length()) {
            return "";
        }
        for (String part : value.substring(queryStart + 1).split("&")) {
            int equals = part.indexOf('=');
            if (equals > 0 && "body".equalsIgnoreCase(Uri.decode(part.substring(0, equals)))) {
                return Uri.decode(part.substring(equals + 1).replace("+", "%20"));
            }
        }
        return "";
    }

    private void showInbox() {
        ScreenMode previousScreen = screenMode;
        clearCurrentFocusBeforeNavigation();
        screenMode = ScreenMode.INBOX;
        styleSystemBars();
        activeConversation = null;
        activeMessagesList = null;
        activeScrollView = null;
        if (previousScreen != ScreenMode.INBOX && canRestoreInboxScreen()) {
            root = cachedInboxRoot;
            inboxList = cachedInboxList;
            setContentView(root);
            applySystemBarInsets(root);
            refreshInboxList(true);
            return;
        }
        root = installScreenRoot();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(headerBackground());
        header.setPadding(dp(16), dp(2), dp(16), dp(10));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text(showingBlocked ? "Spam & blocked" : "Crow Messenger", 27, TEXT, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout inboxMenu = threeDotMenuButton("More options");
        setFeedbackClickListener(inboxMenu, v -> showInboxMenu());
        titleRow.addView(inboxMenu, new LinearLayout.LayoutParams(dp(56), dp(40)));

        boolean defaultSmsApp = isDefaultSmsApp();
        if (!defaultSmsApp) {
            TextView status = text("Set as default to send messages", 14, MINT, Typeface.NORMAL);
            status.setPadding(0, dp(8), 0, 0);
            status.setOnClickListener(v -> requestDefaultSmsApp());
            header.addView(status);
        }

        EditText search = new EditText(this);
        search.setHint("Search");
        search.setText(searchQuery);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search_muted, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(8));
        search.setBackgroundResource(R.drawable.composer_background);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(42));
        searchParams.topMargin = dp(8);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                searchHandler.removeCallbacks(inboxSearchTask);
                searchHandler.postDelayed(inboxSearchTask, SEARCH_DEBOUNCE_MILLIS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        header.addView(search, searchParams);

        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(BLACK);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(BLACK);
        inboxList = new LinearLayout(this);
        inboxList.setOrientation(LinearLayout.VERTICAL);
        inboxList.setBackgroundColor(BLACK);
        inboxList.setPadding(dp(10), dp(12), dp(10), showingBlocked ? dp(20) : dp(132));
        scrollView.addView(inboxList);
        content.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));
        cachedInboxRoot = root;
        cachedInboxList = inboxList;
        cachedInboxBlocked = showingBlocked;
        cachedInboxQuery = searchQuery;

        if (!showingBlocked) {
            Button compose = new Button(this);
            compose.setText(R.string.compose_button);
            compose.setAllCaps(false);
            compose.setTextColor(BLACK);
            compose.setTextSize(15);
            compose.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            compose.setBackground(primaryGradientBackground(26));
            applyPressFeedback(compose);
            compose.setOnClickListener(v -> {
                performTapFeedback(v);
                showComposePage(true);
            });
            FrameLayout.LayoutParams composeParams = new FrameLayout.LayoutParams(
                    dp(130),
                    dp(INBOX_ACTION_HEIGHT_DP),
                    Gravity.END | Gravity.BOTTOM
            );
            composeParams.setMargins(0, 0, dp(18), dp(INBOX_ACTION_BOTTOM_MARGIN_DP));
            content.addView(compose, composeParams);
        }

        refreshInboxList(previousScreen != ScreenMode.INBOX);
    }

    private void showComposePage(boolean reset) {
        screenMode = ScreenMode.COMPOSE;
        styleSystemBars();
        activeConversation = null;
        activeMessagesList = null;
        activeScrollView = null;
        if (reset) {
            composeDraft = "";
            composeRecipients.clear();
            deleteCameraImagesIfNeeded(pendingComposeImageUris);
            deleteCameraImageIfNeeded(pendingCameraImageUri);
            pendingComposeImageUris.clear();
            pendingCameraImageUri = null;
        }

        root = installScreenRoot();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(headerBackground());
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(18), 0);
        bar.setBackgroundColor(Color.TRANSPARENT);
        header.addView(bar, new LinearLayout.LayoutParams(-1, dp(HEADER_BAR_HEIGHT_DP)));

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back_mint);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Back to messages");
        back.setScaleType(ImageView.ScaleType.CENTER);
        setFeedbackClickListener(back, v -> showInbox());
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView title = text("New message", 20, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView toLabel = text("Recipients", 13, MUTED, Typeface.BOLD);
        content.addView(toLabel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout recipients = new LinearLayout(this);
        recipients.setOrientation(LinearLayout.VERTICAL);
        recipients.setPadding(0, dp(6), 0, dp(4));
        content.addView(recipients, new LinearLayout.LayoutParams(-1, -2));
        renderComposeRecipients(recipients);

        LinearLayout manualRecipientRow = new LinearLayout(this);
        manualRecipientRow.setGravity(Gravity.CENTER_VERTICAL);
        manualRecipientRow.setPadding(0, dp(2), 0, dp(4));
        EditText numberInput = new EditText(this);
        numberInput.setHint("Phone number");
        numberInput.setSingleLine(true);
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        numberInput.setTextSize(15);
        numberInput.setTextColor(Color.rgb(25, 25, 25));
        numberInput.setHintTextColor(Color.rgb(130, 130, 130));
        numberInput.setBackgroundResource(R.drawable.composer_background);
        manualRecipientRow.addView(numberInput, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button addNumber = new Button(this);
        addNumber.setText(R.string.add_button);
        addNumber.setAllCaps(false);
        addNumber.setTextColor(BLACK);
        addNumber.setTextSize(14);
        addNumber.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addNumber.setBackground(primaryGradientBackground(22));
        addNumber.setOnClickListener(v -> {
            if (addTypedComposeRecipient(numberInput)) {
                renderComposeRecipients(recipients);
            }
        });
        LinearLayout.LayoutParams addNumberParams = new LinearLayout.LayoutParams(dp(68), dp(44));
        addNumberParams.leftMargin = dp(8);
        manualRecipientRow.addView(addNumber, addNumberParams);
        content.addView(manualRecipientRow, new LinearLayout.LayoutParams(-1, -2));

        Button addPerson = actionButton("+ Add from contacts", v -> {
            pickingComposeContact = true;
            pickContact();
        });
        addPerson.setBackground(primaryGradientBackground(22));
        LinearLayout.LayoutParams addPersonParams = new LinearLayout.LayoutParams(-1, dp(44));
        addPersonParams.setMargins(0, dp(4), 0, dp(4));
        addPerson.setLayoutParams(addPersonParams);
        content.addView(addPerson);

        EditText body = new EditText(this);
        body.setHint("Enter message");
        body.setSingleLine(false);
        body.setMinLines(1);
        body.setMaxLines(4);
        body.setTextSize(TextSizePrefs.composerSp(this));
        body.setTextColor(Color.rgb(25, 25, 25));
        body.setHintTextColor(Color.rgb(130, 130, 130));
        body.setBackgroundResource(R.drawable.composer_background);
        body.setText(composeDraft);
        body.setSelection(body.getText().length());
        body.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                composeDraft = s == null ? "" : s.toString();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setBackgroundColor(BLACK);
        composer.setPadding(dp(10), dp(10), dp(10), dp(COMPOSER_BOTTOM_PADDING_DP));
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));
        liftComposerAboveKeyboard(root, composer, scroll);

        if (!pendingComposeImageUris.isEmpty()) {
            composer.addView(composeImagePreviews());
        }

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBaselineAligned(false);
        composer.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

        inputRow.addView(attachButton(v -> showAttachmentMenu()), new LinearLayout.LayoutParams(dp(48), dp(48)));

        inputRow.addView(body, new LinearLayout.LayoutParams(0, -2, 1));

        addVoiceButton(inputRow, body);

        ImageButton send = sendButton(v -> sendComposeMessage(body, numberInput, recipients, v));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(58), dp(40));
        sendParams.leftMargin = dp(8);
        inputRow.addView(send, sendParams);
    }

    private void renderComposeRecipients(LinearLayout recipients) {
        recipients.removeAllViews();
        if (composeRecipients.isEmpty()) {
            return;
        }
        for (ComposeRecipient recipient : composeRecipients) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(8), dp(8));
            row.setBackground(roundedBackground(SURFACE, 18));

            LinearLayout identity = new LinearLayout(this);
            identity.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(recipient.name, 16, TEXT, Typeface.BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            identity.addView(name, new LinearLayout.LayoutParams(-1, -2));
            if (!TextUtils.equals(recipient.name, recipient.address)) {
                TextView address = text(recipient.address, 13, MUTED, Typeface.NORMAL);
                address.setSingleLine(true);
                address.setEllipsize(TextUtils.TruncateAt.END);
                address.setPadding(0, dp(2), 0, 0);
                identity.addView(address, new LinearLayout.LayoutParams(-1, -2));
            }
            row.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));

            ImageButton remove = new ImageButton(this);
            remove.setImageResource(R.drawable.ic_close_mint);
            remove.setBackgroundColor(Color.TRANSPARENT);
            remove.setPadding(dp(9), dp(9), dp(9), dp(9));
            remove.setContentDescription("Remove " + recipient.name);
            setFeedbackClickListener(remove, v -> {
                composeRecipients.remove(recipient);
                renderComposeRecipients(recipients);
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, 0, 0, dp(6));
            recipients.addView(row, rowParams);
        }
    }

    private void sendComposeMessage(EditText bodyInput, EditText numberInput, LinearLayout recipientsView, View sendControl) {
        if (blockSendWhilePictureJobActive()) {
            return;
        }
        String body = bodyInput == null ? "" : bodyInput.getText().toString().trim();
        boolean hasImage = !pendingComposeImageUris.isEmpty();
        if (hasTypedComposeNumber(numberInput)) {
            if (addTypedComposeRecipient(numberInput)) {
                renderComposeRecipients(recipientsView);
            } else {
                return;
            }
        }
        if (composeRecipients.isEmpty()) {
            Toast.makeText(this, "Add or type a phone number first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(body) && !hasImage) {
            Toast.makeText(this, "Type a message or attach a picture first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ensureDefaultSmsAppFor("sending")) {
            return;
        }
        ComposeRecipient recipient = composeRecipients.get(0);
        if (hasImage) {
            List<String> recipients = composeRecipients.size() > 1 ? composeRecipientAddresses() : null;
            String targetAddress = recipient.address;
            if (recipients != null) {
                targetAddress = LocalMmsStore.outgoingGroupAddress(this, recipients);
                recipient = new ComposeRecipient(LocalMmsStore.displayNameForAddress(this, targetAddress), targetAddress);
            }
            startImageSend(
                    ImageSendOrigin.COMPOSE,
                    targetAddress,
                    recipient.name,
                    recipients,
                    body,
                    pendingComposeImageUris,
                    bodyInput,
                    sendControl,
                    false,
                    ""
            );
            return;
        }
        try {
            long sentAt;
            if (composeRecipients.size() > 1) {
                List<String> recipients = composeRecipientAddresses();
                String groupAddress = LocalMmsStore.outgoingGroupAddress(this, recipients);
                sentAt = MmsTextSender.sendAndRecord(this, groupAddress, recipients, body);
                recipient = new ComposeRecipient(LocalMmsStore.displayNameForAddress(this, groupAddress), groupAddress);
            } else {
                sentAt = SmsSender.sendAndRecord(this, recipient.address, body);
            }
            rememberSentConversationImmediately(recipient.address, recipient.name, body, false, sentAt);
            Conversation targetConversation = SmsStore.conversationForAddress(this, recipient.address);
            composeDraft = "";
            composeRecipients.clear();
            hideKeyboard(bodyInput);
            bodyInput.clearFocus();
            sendSuccessFeedback(sendControl);
            afterKeyboardSettles(bodyInput, () -> showChat(targetConversation, false));
        } catch (SmsSender.SendException ex) {
            Toast.makeText(this, "Message could not be sent: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private List<String> composeRecipientAddresses() {
        ArrayList<String> addresses = new ArrayList<>();
        for (ComposeRecipient recipient : composeRecipients) {
            if (recipient != null && !TextUtils.isEmpty(recipient.address)) {
                addresses.add(recipient.address);
            }
        }
        return addresses;
    }

    private boolean startImageSend(
            ImageSendOrigin origin,
            String address,
            String name,
            List<String> recipients,
            String caption,
            List<Uri> ownedUiAttachments,
            EditText input,
            View sendControl,
            boolean blockedOnly,
            String failedId
    ) {
        return startImageSend(
                origin,
                address,
                name,
                recipients,
                caption,
                ownedUiAttachments,
                input,
                sendControl,
                blockedOnly,
                failedId,
                ownedUiAttachments
        );
    }

    private boolean startImageSend(
            ImageSendOrigin origin,
            String address,
            String name,
            List<String> recipients,
            String caption,
            List<Uri> ownedUiAttachments,
            EditText input,
            View sendControl,
            boolean blockedOnly,
            String failedId,
            List<Uri> requestedUris
    ) {
        if (activeImageSendUi != null || MmsImageSendCoordinator.isActive(IMAGE_SEND_JOB_KEY)) {
            Toast.makeText(this, "Crow is already preparing a picture message.", Toast.LENGTH_SHORT).show();
            return false;
        }
        ArrayList<Uri> imageUris = requestedUris == null
                ? new ArrayList<>()
                : new ArrayList<>(requestedUris);
        if (TextUtils.isEmpty(address) || imageUris.isEmpty()) {
            Toast.makeText(this, "No picture is ready to send.", Toast.LENGTH_SHORT).show();
            return false;
        }

        ImageSendSession session = new ImageSendSession(
                origin,
                address,
                name,
                blockedOnly,
                failedId,
                imageUris.size()
        );
        ImageSendUi ui = new ImageSendUi(session, input, sendControl);
        MmsImageSendCoordinator.Request request = new MmsImageSendCoordinator.Request(
                address,
                recipients,
                caption,
                imageUris,
                failedId
        );
        if (ownedUiAttachments != null) {
            ownedUiAttachments.clear();
        }
        if (sendControl != null) {
            sendControl.setEnabled(false);
        }
        retainedImageSendSession = session;
        activeImageSendUi = ui;
        boolean accepted;
        try {
            accepted = MmsImageSendCoordinator.submit(this, IMAGE_SEND_JOB_KEY, request, imageSendListener);
        } catch (RuntimeException ex) {
            accepted = false;
        }
        if (!accepted) {
            retainedImageSendSession = null;
            activeImageSendUi = null;
            if (sendControl != null) {
                sendControl.setEnabled(true);
            }
            restoreImageAttachments(ui.origin, ui.address, imageUris);
            Toast.makeText(this, "Picture sending could not be started.", Toast.LENGTH_LONG).show();
            return false;
        }
        showImageSendProgress(imageUris.size());
        return true;
    }

    private boolean blockSendWhilePictureJobActive() {
        if (!MmsImageSendCoordinator.isActive(IMAGE_SEND_JOB_KEY)) {
            return false;
        }
        Toast.makeText(this, "Crow is still finishing the current picture message.", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void reattachImageSendIfNeeded() {
        ImageSendSession session = retainedImageSendSession;
        if (session == null) {
            return;
        }
        if (!MmsImageSendCoordinator.attach(IMAGE_SEND_JOB_KEY, imageSendListener)) {
            retainedImageSendSession = null;
            return;
        }
        if (activeImageSendUi == null) {
            activeImageSendUi = new ImageSendUi(session, null, null);
        }
        if (activeImageSendDialog == null || !activeImageSendDialog.isShowing()) {
            showImageSendProgress(session.imageCount);
        }
    }

    private void showImageSendProgress(int count) {
        dismissImageSendProgress();
        Dialog dialog = new AlertDialog.Builder(this)
                .setTitle(count == 1 ? "Sending picture" : "Sending pictures")
                .setMessage("Preparing " + (count == 1 ? "your picture" : count + " pictures") + " for your carrier...")
                .setCancelable(false)
                .create();
        try {
            dialog.show();
            activeImageSendDialog = dialog;
        } catch (RuntimeException ignored) {
            activeImageSendDialog = null;
        }
    }

    private void dismissImageSendProgress() {
        if (activeImageSendDialog != null && activeImageSendDialog.isShowing()) {
            activeImageSendDialog.dismiss();
        }
        activeImageSendDialog = null;
    }

    private void onImageSendCompleted(
            String jobKey,
            MmsImageSendCoordinator.Request request,
            MmsImageSendCoordinator.Result result
    ) {
        ImageSendSession session = retainedImageSendSession;
        if (!IMAGE_SEND_JOB_KEY.equals(jobKey)
                || session == null
                || !TextUtils.equals(session.address, request.address)) {
            return;
        }
        if (!shouldHandleImageSendCompletion(activityResumed, isDestroyed(), isFinishing())) {
            return;
        }
        ImageSendUi ui = activeImageSendUi == null
                ? new ImageSendUi(session, null, null)
                : activeImageSendUi;
        dismissImageSendProgress();
        if (ui.sendControl != null) {
            ui.sendControl.setEnabled(true);
        }

        if (ui.origin != ImageSendOrigin.RETRY) {
            reconcileImageAttachments(
                    ui.origin,
                    ui.address,
                    request.imageUris,
                    result.remainingUris
            );
        }
        if (result.captionConsumed) {
            if (ui.origin == ImageSendOrigin.COMPOSE) {
                composeDraft = "";
            } else if (ui.origin == ImageSendOrigin.CHAT) {
                discardPendingDraft(ui.address);
                DraftStore.clear(this, ui.address);
            }
            if (ui.input != null) {
                ui.input.setText("");
            }
        }
        if (result.sentCount > 0) {
            rememberSentConversationImmediately(
                    request.address,
                    ui.name,
                    request.caption,
                    true,
                    result.lastSentAt
            );
        }
        if (!result.succeeded()) {
            restoreFailedImageSendState(ui, request, result.captionConsumed);
        }
        if (result.succeeded()) {
            finishSuccessfulImageSend(ui, request);
        } else {
            finishFailedImageSend(ui, result);
        }
        retainedImageSendSession = null;
        activeImageSendUi = null;
        MmsImageSendCoordinator.consume(jobKey);
    }

    private void finishSuccessfulImageSend(
            ImageSendUi ui,
        MmsImageSendCoordinator.Request request
    ) {
        if (ui.origin == ImageSendOrigin.RETRY) {
            try {
                LocalMmsStore.deleteFailedMessageById(this, ui.failedId);
            } catch (RuntimeException ignored) {
                // Worker cleanup is authoritative; never strand a completed send in the UI.
            }
            discardConversationCaches(request.address);
            invalidateInboxPresentationCache();
            Toast.makeText(this, "Sending again.", Toast.LENGTH_SHORT).show();
            refreshActiveThreadAsync(true);
            return;
        }

        Conversation sentConversation = SmsStore.conversationForAddress(this, request.address);
        if (ui.origin == ImageSendOrigin.COMPOSE) {
            composeDraft = "";
            composeRecipients.clear();
            if (ui.input != null) {
                hideKeyboard(ui.input);
                ui.input.clearFocus();
                sendSuccessFeedback(ui.sendControl);
                afterKeyboardSettles(ui.input, () -> showChat(sentConversation, false));
            } else {
                showChat(sentConversation, false);
            }
            return;
        }

        discardPendingDraft(request.address);
        DraftStore.clear(this, request.address);
        pendingImageAddress = "";
        if (!TextUtils.isEmpty(threadSearchQuery)) {
            threadSearchQuery = "";
        }
        if (ui.input != null) {
            hideKeyboard(ui.input);
            ui.input.clearFocus();
            sendSuccessFeedback(ui.sendControl);
            afterKeyboardSettles(ui.input, () -> showChat(sentConversation, ui.blockedOnly));
        } else {
            showChat(sentConversation, ui.blockedOnly);
        }
    }

    private void finishFailedImageSend(ImageSendUi ui, MmsImageSendCoordinator.Result result) {
        String message = result.sentCount > 0
                ? "Some pictures were sent. The remaining pictures are still attached. " + result.error
                : "Picture could not be sent: " + result.error;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (ui.origin == ImageSendOrigin.COMPOSE) {
            showComposePage(false);
        } else if (ui.origin == ImageSendOrigin.CHAT) {
            pendingImageAddress = ui.address;
            showChat(SmsStore.conversationForAddress(this, ui.address), ui.blockedOnly);
        } else {
            showChat(SmsStore.conversationForAddress(this, ui.address), ui.blockedOnly);
        }
    }

    private void restoreFailedImageSendState(
            ImageSendUi ui,
            MmsImageSendCoordinator.Request request,
            boolean captionConsumed
    ) {
        if (ui.origin == ImageSendOrigin.COMPOSE) {
            if (!captionConsumed) {
                composeDraft = request.caption;
            }
            if (composeRecipients.isEmpty()) {
                List<String> addresses = restoredComposeRecipientAddresses(
                        request.address,
                        request.explicitRecipients,
                        composeRecipientAddresses()
                );
                for (String address : addresses) {
                    if (!TextUtils.isEmpty(address)) {
                        composeRecipients.add(new ComposeRecipient(
                                SmsStore.displayNameForAddress(this, address),
                                address
                        ));
                    }
                }
            }
        } else if (ui.origin == ImageSendOrigin.CHAT && !captionConsumed) {
            DraftStore.save(this, request.address, request.caption);
        }
    }

    private void reconcileImageAttachments(
            ImageSendOrigin origin,
            String address,
            List<Uri> requestedUris,
            List<Uri> remainingUris
    ) {
        List<Uri> target = origin == ImageSendOrigin.COMPOSE ? pendingComposeImageUris : pendingImageUris;
        List<Uri> restored = reconcileCompletedImageUris(requestedUris, remainingUris, target);
        target.clear();
        target.addAll(restored);
        if (origin == ImageSendOrigin.CHAT) {
            pendingImageAddress = target.isEmpty() ? "" : address;
        }
    }

    private void restoreImageAttachments(ImageSendOrigin origin, String address, List<Uri> remainingUris) {
        if (remainingUris == null || remainingUris.isEmpty() || origin == ImageSendOrigin.RETRY) {
            return;
        }
        List<Uri> target = origin == ImageSendOrigin.COMPOSE ? pendingComposeImageUris : pendingImageUris;
        List<Uri> restored = mergeRemainingImageUris(remainingUris, target);
        target.clear();
        target.addAll(restored);
        if (origin == ImageSendOrigin.CHAT) {
            pendingImageAddress = address;
        }
    }

    private FrameLayout threeDotMenuButton(String contentDescription) {
        FrameLayout menu = new FrameLayout(this);
        menu.setBackground(primaryGradientBackground(20));
        menu.setContentDescription(contentDescription);
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            dot.setBackground(roundedBackground(BLACK, 3));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(5), dp(5));
            if (i > 0) {
                dotParams.leftMargin = dp(4);
            }
            dots.addView(dot, dotParams);
        }
        menu.addView(dots, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        applyPressFeedback(menu);
        return menu;
    }

    private ImageButton attachButton(View.OnClickListener listener) {
        ImageButton attach = new ImageButton(this);
        attach.setImageResource(android.R.drawable.ic_input_add);
        attach.setColorFilter(MINT);
        attach.setScaleType(ImageView.ScaleType.CENTER);
        attach.setPadding(dp(10), dp(10), dp(10), dp(10));
        attach.setContentDescription("Add a picture");
        attach.setBackgroundColor(Color.TRANSPARENT);
        applyPressFeedback(attach);
        attach.setOnClickListener(v -> {
            performTapFeedback(v);
            listener.onClick(v);
        });
        return attach;
    }

    private ImageButton sendButton(View.OnClickListener listener) {
        ImageButton send = new ImageButton(this);
        send.setImageResource(R.drawable.send_crow_art);
        send.clearColorFilter();
        send.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        send.setPadding(dp(8), dp(3), dp(8), dp(3));
        send.setContentDescription(getString(R.string.send_button));
        send.setBackground(primaryGradientBackground(20));
        applyPressFeedback(send);
        send.setOnClickListener(v -> {
            performTapFeedback(v);
            listener.onClick(v);
        });
        return send;
    }

    private void showInboxMenu() {
        ArrayList<String> options = new ArrayList<>();
        options.add(showingBlocked ? "Normal inbox" : "Spam & blocked");
        if (!showingBlocked) {
            options.add("Mark all messages read");
            options.add("Trash");
        }
        options.add("Appearance & typing");
        options.add("Spam protection");
        options.add("Messaging setup");
        options.add("Backup & restore");

        showCrowMenu("Messages", options, choice -> {
            if ("Spam & blocked".equals(choice) || "Normal inbox".equals(choice)) {
                showingBlocked = !showingBlocked;
                showInbox();
            } else if ("Mark all messages read".equals(choice)) {
                markAllMessagesRead();
            } else if ("Trash".equals(choice)) {
                showTrashPage();
            } else if ("Appearance & typing".equals(choice)) {
                showAppearanceSettingsMenu();
            } else if ("Spam protection".equals(choice)) {
                showSpamSettingsMenu();
            } else if ("Messaging setup".equals(choice)) {
                showMessagingSetupMenu();
            } else if ("Backup & restore".equals(choice)) {
                showBackupMenu();
            }
        });
    }

    private void showBackupMenu() {
        showCrowMenu(
                "Backup & restore",
                java.util.Arrays.asList("Export settings", "Restore settings"),
                choice -> {
                    if ("Export settings".equals(choice)) {
                        exportSettingsBackup();
                    } else {
                        importSettingsBackup();
                    }
                }
        );
    }

    private void exportSettingsBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "Crow-Messenger-Settings.json");
        try {
            startActivityForResult(intent, REQUEST_EXPORT_SETTINGS);
        } catch (Exception ignored) {
            Toast.makeText(this, "A save location could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private void importSettingsBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_SETTINGS);
        } catch (Exception ignored) {
            Toast.makeText(this, "A settings backup could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppearanceSettingsMenu() {
        ArrayList<String> options = new ArrayList<>();
        options.add("Text size");
        options.add(ComposerPrefs.voiceButtonVisible(this) ? "Hide microphone button" : "Show microphone button");
        showCrowMenu("Appearance & typing", options, choice -> {
            if ("Text size".equals(choice)) {
                showTextSizeMenu();
            } else {
                toggleVoiceButton();
            }
        });
    }

    private void showSpamSettingsMenu() {
        ArrayList<String> options = new ArrayList<>();
        options.add(showingBlocked ? "Return to normal inbox" : "View spam & blocked");
        options.add("Spam filter rules");
        showCrowMenu("Spam protection", options, choice -> {
            if ("Spam filter rules".equals(choice)) {
                showSpamRulesPage();
            } else {
                showingBlocked = !showingBlocked;
                showInbox();
            }
        });
    }

    private void showMessagingSetupMenu() {
        ArrayList<String> options = new ArrayList<>();
        options.add("MMS status");
        options.add("Default messaging app");
        options.add("Copy troubleshooting report");
        showCrowMenu("Messaging setup", options, choice -> {
            if ("MMS status".equals(choice)) {
                showMmsStatus();
            } else if ("Default messaging app".equals(choice)) {
                requestDefaultSmsApp();
            } else if ("Copy troubleshooting report".equals(choice)) {
                copyTroubleshootingReport();
            }
        });
    }

    private void copyTroubleshootingReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Troubleshooting report could not be copied.", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                "Crow Messenger troubleshooting report",
                TroubleshootingReport.create(this)
        ));
        Toast.makeText(this, "Private troubleshooting report copied.", Toast.LENGTH_SHORT).show();
    }

    private void toggleVoiceButton() {
        boolean visible = !ComposerPrefs.voiceButtonVisible(this);
        ComposerPrefs.setVoiceButtonVisible(this, visible);
        Toast.makeText(this, visible ? "Microphone button shown." : "Microphone button hidden.", Toast.LENGTH_SHORT).show();
        if (screenMode == ScreenMode.CHAT && activeConversation != null) {
            showChat(activeConversation, activeThreadBlockedOnly);
        } else if (screenMode == ScreenMode.COMPOSE) {
            showComposePage(false);
        } else {
            showInbox();
        }
    }

    private void showSpamRulesPage() {
        screenMode = ScreenMode.SPAM_RULES;
        styleSystemBars();
        activeConversation = null;
        activeMessagesList = null;
        activeScrollView = null;

        root = installScreenRoot();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(headerBackground());
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(18), 0);
        bar.setBackgroundColor(Color.TRANSPARENT);
        header.addView(bar, new LinearLayout.LayoutParams(-1, dp(HEADER_BAR_HEIGHT_DP)));

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back_mint);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Back to messages");
        back.setScaleType(ImageView.ScaleType.CENTER);
        setFeedbackClickListener(back, v -> showInbox());
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView title = text("Spam filter rules", 22, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(32));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView helper = text("Incoming texts from numbers outside your contacts will move to Spam & blocked when they contain a saved word or phrase. Messages from your contacts are always allowed. Separate multiple rules with commas. Example: free gift", 14, MUTED, Typeface.NORMAL);
        helper.setPadding(0, 0, 0, dp(14));
        content.addView(helper, new LinearLayout.LayoutParams(-1, -2));

        EditText input = new EditText(this);
        input.setHint("free gift");
        input.setSingleLine(true);
        input.setTextColor(BLACK);
        input.setHintTextColor(Color.rgb(130, 130, 130));
        input.setBackgroundResource(R.drawable.composer_background);
        content.addView(input, new LinearLayout.LayoutParams(-1, dp(48)));

        Button add = new Button(this);
        add.setText(R.string.add_spam_rules_button);
        add.setAllCaps(false);
        add.setTextColor(BLACK);
        add.setTextSize(15);
        add.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add.setBackground(roundedBackground(MINT, 22));
        add.setOnClickListener(v -> {
            String keywords = input.getText().toString().trim();
            if (TextUtils.isEmpty(keywords)) {
                Toast.makeText(this, "Type at least one word or phrase.", Toast.LENGTH_SHORT).show();
                return;
            }
            int added = SpamFilter.addCustomKeywords(this, keywords);
            Toast.makeText(this, added == 1 ? "Spam rule added." : added + " spam rules added.", Toast.LENGTH_SHORT).show();
            showSpamRulesPage();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, dp(48));
        addParams.topMargin = dp(12);
        content.addView(add, addParams);

        TextView current = text("Current rules", 18, TEXT, Typeface.BOLD);
        current.setPadding(0, dp(24), 0, dp(10));
        content.addView(current, new LinearLayout.LayoutParams(-1, -2));

        List<String> custom = SpamFilter.customKeywords(this);
        if (custom.isEmpty()) {
            TextView empty = text("No spam filter rules yet.", 15, MUTED, Typeface.NORMAL);
            empty.setPadding(0, dp(8), 0, 0);
            content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        ArrayList<CheckBox> boxes = new ArrayList<>();
        for (String keyword : custom) {
            CheckBox box = new CheckBox(this);
            box.setText(keyword);
            box.setTextSize(16);
            box.setTextColor(TEXT);
            box.setButtonTintList(new ColorStateList(
                    new int[][] {
                            new int[] { android.R.attr.state_checked },
                            new int[] {}
                    },
                    new int[] { MINT, Color.WHITE }
            ));
            box.setPadding(0, dp(8), 0, dp(8));
            boxes.add(box);
            content.addView(box, new LinearLayout.LayoutParams(-1, -2));
        }

        Button remove = new Button(this);
        remove.setText(R.string.remove_selected_button);
        remove.setAllCaps(false);
        remove.setTextColor(BLACK);
        remove.setTextSize(15);
        remove.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        remove.setBackground(roundedBackground(MINT, 22));
        remove.setOnClickListener(v -> {
            ArrayList<String> removals = new ArrayList<>();
            for (CheckBox box : boxes) {
                if (box.isChecked()) {
                    removals.add(box.getText().toString());
                }
            }
            if (removals.isEmpty()) {
                Toast.makeText(this, "Select at least one rule to remove.", Toast.LENGTH_SHORT).show();
                return;
            }
            int removed = SpamFilter.removeCustomKeywords(this, removals);
            Toast.makeText(this, removed == 1 ? "Spam rule removed." : removed + " spam rules removed.", Toast.LENGTH_SHORT).show();
            showSpamRulesPage();
        });
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(-1, dp(48));
        removeParams.topMargin = dp(16);
        content.addView(remove, removeParams);
    }

    private void showMmsStatus() {
        boolean rawArchiveEnabled = MmsDebugStore.shouldArchiveRawPdus(this);
        new AlertDialog.Builder(this)
                .setTitle("MMS status")
                .setMessage(MmsDebugStore.last(this)
                        + "\n\nRaw MMS debug files: "
                        + (rawArchiveEnabled ? "On" : "Off"))
                .setNeutralButton(rawArchiveEnabled ? "Turn raw files off" : "Turn raw files on", (dialog, which) -> {
                    MmsDebugStore.setArchiveRawPdus(this, !rawArchiveEnabled);
                    Toast.makeText(
                            this,
                            rawArchiveEnabled ? "Raw MMS debug files turned off." : "Raw MMS debug files turned on.",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .setPositiveButton("OK", null)
                .show();
    }

    private void refreshInboxList() {
        refreshInboxList(false);
    }

    private void refreshInboxList(boolean force) {
        if (inboxList == null) {
            return;
        }
        boolean blocked = showingBlocked;
        String query = searchQuery;
        String cacheKey = inboxCacheKey(blocked, query);
        if (TextUtils.isEmpty(query) && !TextUtils.equals(renderedInboxCacheKey, cacheKey)) {
            List<Conversation> cachedInbox = inboxRowsCache.get(cacheKey);
            if (cachedInbox == null && !blocked) {
                cachedInbox = InboxSnapshotStore.loadVisible(this);
                if (!cachedInbox.isEmpty()) {
                    inboxRowsCache.put(cacheKey, cachedInbox);
                }
            }
            if (cachedInbox != null) {
                renderInboxRows(cachedInbox, blocked, cacheKey);
            }
        }
        if (!TextUtils.isEmpty(query) && !TextUtils.equals(renderedInboxCacheKey, cacheKey)) {
            List<Conversation> cachedSearch = inboxRowsCache.get(cacheKey);
            if (cachedSearch == null) {
                renderInboxSearchLoading(cacheKey);
            } else {
                renderInboxRows(cachedSearch, blocked, cacheKey);
            }
        }
        if (inboxList.getChildCount() == 0) {
            List<Conversation> cachedRows = inboxRowsCache.get(cacheKey);
            if (cachedRows == null && !blocked && TextUtils.isEmpty(query)) {
                cachedRows = InboxSnapshotStore.loadVisible(this);
                if (!cachedRows.isEmpty()) {
                    inboxRowsCache.put(cacheKey, cachedRows);
                }
            }
            if (cachedRows != null) {
                renderInboxRows(cachedRows, blocked, cacheKey);
                prefetchVisibleThreads(cachedRows, blocked);
            } else if (blocked && TextUtils.isEmpty(query)) {
                renderInboxRows(new ArrayList<>(), true, cacheKey);
            }
        }
        long lastLoadedAt = blocked ? blockedInboxLoadedAtMillis : normalInboxLoadedAtMillis;
        if (!force
                && TextUtils.isEmpty(query)
                && inboxRowsCache.get(cacheKey) != null
                && System.currentTimeMillis() - lastLoadedAt < INBOX_REFRESH_THROTTLE_MILLIS) {
            return;
        }
        int generation = ++inboxLoadGeneration;
        Context appContext = getApplicationContext();
        cancelTask(inboxLoadTask);
        inboxLoadTask = inboxLoader.submit(() -> {
            List<Conversation> loadedConversations = SmsStore.loadConversations(appContext, blocked, query);
            List<Conversation> conversations = rowsWithConversationsRead(loadedConversations, locallyReadAddresses);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (!blocked && TextUtils.isEmpty(query)) {
                InboxSnapshotStore.save(appContext, conversations);
            }
            runOnUiThread(() -> {
                if (isDestroyed() || inboxList == null || generation != inboxLoadGeneration) {
                    return;
                }
                List<Conversation> snapshot = new ArrayList<>(conversations);
                boolean rowsChanged = !sameConversationRows(inboxRowsCache.get(cacheKey), snapshot);
                inboxRowsCache.put(cacheKey, snapshot);
                if (TextUtils.isEmpty(query)) {
                    if (blocked) {
                        blockedInboxLoadedAtMillis = System.currentTimeMillis();
                    } else {
                        normalInboxLoadedAtMillis = System.currentTimeMillis();
                    }
                }
                if (rowsChanged || !TextUtils.equals(renderedInboxCacheKey, cacheKey)) {
                    renderInboxRows(snapshot, blocked, cacheKey);
                }
                prefetchVisibleThreads(snapshot, blocked);
            });
        });
    }

    private void renderInboxSearchLoading(String cacheKey) {
        if (inboxList == null) {
            return;
        }
        renderedInboxCacheKey = cacheKey;
        inboxList.removeAllViews();
        TextView loading = text(getString(R.string.searching_messages), 15, MUTED, Typeface.NORMAL);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(42), 0, dp(20));
        inboxList.addView(loading, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showIncomingConversationImmediately(String address, String body, long dateMillis) {
        if (TextUtils.isEmpty(address)
                || TextUtils.isEmpty(body)
                || showingBlocked
                || !TextUtils.isEmpty(searchQuery)
                || MessageNotifier.shouldSuppressIncoming(this, address, "", body)) {
            return;
        }
        String cacheKey = inboxCacheKey(false, "");
        List<Conversation> updated = InboxSnapshotStore.upsertIncoming(this, address, body, dateMillis);
        inboxRowsCache.put(cacheKey, updated);
        renderInboxRows(updated, false, cacheKey);
    }

    private void showIncomingSmsImmediately(String address, String body, long dateMillis, boolean scrollToBottom) {
        if (activeConversation == null
                || TextUtils.isEmpty(body)
                || !TextUtils.isEmpty(threadSearchQuery)
                || !sameAddress(activeConversation.address, address)) {
            return;
        }
        String cacheKey = threadCacheKey(activeConversation.address, activeThreadBlockedOnly);
        List<ChatMessage> cached = threadRowsCache.get(cacheKey);
        if (cached == null) {
            cached = new ArrayList<>();
        }
        long safeDate = dateMillis > 0L ? dateMillis : System.currentTimeMillis();
        for (ChatMessage message : cached) {
            if (!message.outgoing
                    && message.dateMillis == safeDate
                    && TextUtils.equals(message.body, body)) {
                return;
            }
        }
        ArrayList<ChatMessage> updated = new ArrayList<>(cached);
        updated.add(new ChatMessage(body, safeDate, false));
        updated = new ArrayList<>(initialThreadRows(updated, activeThreadMessageLimit));
        threadRowsCache.put(cacheKey, updated);
        renderActiveThreadRowsStaged(updated, scrollToBottom);
    }

    private void rememberPendingIncoming(String address, String body, long dateMillis) {
        pendingMessageRefresh = true;
        if (!TextUtils.isEmpty(body)) {
            pendingIncomingAddress = address == null ? "" : address;
            pendingIncomingBody = body;
            pendingIncomingDateMillis = dateMillis;
        }
    }

    private Conversation cachedConversationForAddress(String address) {
        List<Conversation> cached = inboxRowsCache.get(inboxCacheKey(false, ""));
        if (cached == null) {
            cached = InboxSnapshotStore.loadVisible(this);
        }
        for (Conversation conversation : cached) {
            if (conversation != null && sameAddress(conversation.address, address)) {
                return conversation;
            }
        }
        return SmsStore.quickConversationForAddress(this, address);
    }

    private void applyPendingIncoming() {
        if (!pendingMessageRefresh) {
            return;
        }
        String address = pendingIncomingAddress;
        String body = pendingIncomingBody;
        long dateMillis = pendingIncomingDateMillis;
        pendingMessageRefresh = false;
        pendingIncomingAddress = "";
        pendingIncomingBody = "";
        pendingIncomingDateMillis = 0L;
        if (TextUtils.isEmpty(body)) {
            return;
        }
        if (screenMode == ScreenMode.CHAT
                && activeConversation != null
                && sameAddress(activeConversation.address, address)) {
            boolean wasNearBottom = isScrollNearBottom(activeScrollView, dp(120));
            showIncomingSmsImmediately(address, body, dateMillis, wasNearBottom);
            if (!wasNearBottom) {
                showNewMessageButton();
            }
        } else if (screenMode == ScreenMode.INBOX) {
            showIncomingConversationImmediately(address, body, dateMillis);
        }
    }

    private void rememberSentConversationImmediately(
            String address,
            String name,
            String body,
            boolean hasImage,
            long dateMillis
    ) {
        if (TextUtils.isEmpty(address)) {
            return;
        }
        String cacheKey = inboxCacheKey(false, "");
        List<Conversation> cached = inboxRowsCache.get(cacheKey);
        if (cached == null) {
            cached = InboxSnapshotStore.loadVisible(this);
        }
        ArrayList<Conversation> updated = new ArrayList<>(cached);
        Conversation previous = removeConversationRows(updated, address);
        String snippet = TextUtils.isEmpty(body) && hasImage ? LocalMmsStore.PICTURE_MESSAGE : body;
        updated.add(new Conversation(
                previous == null ? "" : previous.threadId,
                address,
                previous == null ? name : previous.name,
                previous == null ? "" : previous.photoUri,
                snippet,
                dateMillis > 0L ? dateMillis : System.currentTimeMillis(),
                previous == null ? 0 : previous.unreadCount
        ));
        sortInboxRows(updated);
        inboxRowsCache.put(cacheKey, updated);
        InboxSnapshotStore.save(this, updated);
    }

    static Conversation removeConversationRows(List<Conversation> conversations, String address) {
        Conversation newest = null;
        for (int index = conversations.size() - 1; index >= 0; index--) {
            Conversation row = conversations.get(index);
            if (row != null && AddressUtil.sameConversationAddress(row.address, address)) {
                if (newest == null || row.dateMillis > newest.dateMillis) {
                    newest = row;
                }
                conversations.remove(index);
            }
        }
        return newest;
    }

    private void sortInboxRows(List<Conversation> conversations) {
        PinnedStore.sortConversations(this, conversations);
    }

    private void prefetchVisibleThreads(List<Conversation> conversations, boolean blockedOnly) {
        if (conversations == null || conversations.isEmpty()) {
            return;
        }
        Context appContext = getApplicationContext();
        int submitted = 0;
        for (Conversation conversation : conversations) {
            if (conversation == null || TextUtils.isEmpty(conversation.address)) {
                continue;
            }
            String cacheKey = threadCacheKey(conversation.address, blockedOnly);
            if (threadRowsCache.get(cacheKey) != null || !threadPrefetches.add(cacheKey)) {
                continue;
            }
            String address = conversation.address;
            submitted++;
            threadPrefetchLoader.submit(() -> {
                try {
                    List<ChatMessage> rows = SmsStore.loadRecentMessagesForAddress(
                            appContext,
                            address,
                            RECENT_THREAD_LIMIT,
                            blockedOnly
                    );
                    if (threadPrefetches.remove(cacheKey)) {
                        threadRowsCache.put(cacheKey, new ArrayList<>(rows));
                    }
                } finally {
                    threadPrefetches.remove(cacheKey);
                }
            });
            if (submitted >= THREAD_PREFETCH_COUNT) {
                return;
            }
        }
    }

    private void renderInboxRows(List<Conversation> conversations, boolean blocked, String cacheKey) {
        if (inboxList == null) {
            return;
        }
        renderedInboxCacheKey = cacheKey;
        inboxList.removeAllViews();
        if (conversations == null || conversations.isEmpty()) {
            inboxList.addView(emptyStateView(
                    blocked ? "No spam here" : "No messages found",
                    blocked ? "Anything filtered out will land here." : "New conversations will show up here.",
                    blocked
            ), new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (int index = 0; index < conversations.size(); index++) {
            inboxList.addView(conversationRow(conversations.get(index)));
            if (index + 1 < conversations.size()) {
                View divider = new View(this);
                divider.setBackgroundColor(DIVIDER);
                divider.setAlpha(0.7f);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
                dividerParams.setMargins(dp(70), 0, dp(10), 0);
                inboxList.addView(divider, dividerParams);
            }
        }
    }

    static String inboxCacheKey(boolean blocked, String query) {
        String safeQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return (blocked ? "blocked" : "inbox") + "|" + safeQuery;
    }

    static boolean sameConversationRows(List<Conversation> first, List<Conversation> second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            Conversation left = first.get(index);
            Conversation right = second.get(index);
            if (left == right) {
                continue;
            }
            if (left == null || right == null
                    || !TextUtils.equals(left.threadId, right.threadId)
                    || !TextUtils.equals(left.address, right.address)
                    || !TextUtils.equals(left.name, right.name)
                    || !TextUtils.equals(left.photoUri, right.photoUri)
                    || !TextUtils.equals(left.snippet, right.snippet)
                    || left.dateMillis != right.dateMillis
                    || left.unreadCount != right.unreadCount) {
                return false;
            }
        }
        return true;
    }

    private boolean canRestoreInboxScreen() {
        return cachedInboxRoot != null
                && cachedInboxList != null
                && cachedInboxBlocked == showingBlocked
                && TextUtils.equals(cachedInboxQuery, searchQuery);
    }

    private void invalidateInboxPresentationCache() {
        inboxRowsCache.evictAll();
        renderedInboxCacheKey = "";
    }

    private void discardCachedInboxScreen() {
        invalidateInboxPresentationCache();
        cachedInboxRoot = null;
        cachedInboxList = null;
    }

    private void discardConversationCaches(String address) {
        String inboxKey = threadCacheKey(address, false);
        String blockedKey = threadCacheKey(address, true);
        threadRowsCache.remove(inboxKey);
        threadRowsCache.remove(blockedKey);
        threadPrefetches.remove(inboxKey);
        threadPrefetches.remove(blockedKey);
        if (sameAddress(cachedChatAddress, address)) {
            cachedChatRoot = null;
            cachedChatMessagesList = null;
            cachedChatScrollView = null;
            cachedChatJumpToBottomButton = null;
            cachedChatAddress = "";
        }
    }

    private void clearCurrentFocusBeforeNavigation() {
        flushPendingDraft();
        if (root == null) {
            return;
        }
        View focusedView = root.findFocus();
        if (focusedView != null) {
            hideKeyboard(focusedView);
            focusedView.clearFocus();
        }
    }

    private void scheduleDraftSave(String address, String body) {
        pendingDraftAddress = TextUtils.isEmpty(address) ? "" : address;
        pendingDraftBody = body == null ? "" : body;
        draftSaveHandler.removeCallbacks(draftSaveTask);
        draftSaveHandler.postDelayed(draftSaveTask, DRAFT_SAVE_DELAY_MILLIS);
    }

    private void flushPendingDraft() {
        draftSaveHandler.removeCallbacks(draftSaveTask);
        persistPendingDraft();
    }

    private void discardPendingDraft(String address) {
        if (!sameAddress(pendingDraftAddress, address)) {
            return;
        }
        draftSaveHandler.removeCallbacks(draftSaveTask);
        pendingDraftAddress = "";
        pendingDraftBody = "";
    }

    private void persistPendingDraft() {
        String address = pendingDraftAddress;
        String body = pendingDraftBody;
        pendingDraftAddress = "";
        pendingDraftBody = "";
        if (TextUtils.isEmpty(address)) {
            return;
        }
        DraftStore.save(this, address, body);
        invalidateInboxPresentationCache();
    }

    private View conversationRow(Conversation conversation) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(9), dp(8), dp(9));
        row.setMinimumHeight(dp(74));
        if (conversation.unreadCount > 0) {
            row.setBackground(roundedBackground(Color.rgb(13, 28, 23), 12));
        }
        applyPressFeedback(row);
        String draft = DraftStore.draft(this, conversation.address);
        String resultThreadSearch = threadSearchForInboxResult(conversation, searchQuery, draft);
        row.setOnClickListener(v -> {
            performTapFeedback(v);
            showChat(conversation, showingBlocked, resultThreadSearch);
        });

        row.addView(contactAvatar(conversation, 46, 18));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setPadding(dp(12), 0, dp(10), 0);
        row.addView(middle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView name = text(conversation.name, TextSizePrefs.inboxNameSp(this), TEXT, conversation.unreadCount > 0 ? Typeface.BOLD : Typeface.NORMAL);
        if (!TextUtils.isEmpty(searchQuery)) {
            name.setText(highlightSearchMatch(conversation.name, searchQuery.trim()));
        }
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        middle.addView(name);

        if (PinnedStore.isPinned(this, conversation.address)) {
            TextView pinned = text("Pinned", 12, MINT, Typeface.BOLD);
            pinned.setPadding(0, dp(2), 0, 0);
            middle.addView(pinned);
        }

        boolean hasDraft = !TextUtils.isEmpty(draft);
        boolean searchingInbox = !TextUtils.isEmpty(searchQuery);
        boolean draftIsSearchMatch = searchingInbox
                && hasDraft
                && TextUtils.equals(draft, conversation.snippet);
        boolean showDraft = hasDraft && (!searchingInbox || draftIsSearchMatch);
        String snippetText = showDraft
                ? "Draft: " + draft
                : searchExcerpt(conversation.snippet, searchQuery, 64);
        int snippetColor = showDraft ? MINT : (conversation.unreadCount > 0 ? TEXT : MUTED);
        TextView snippet = text(
                snippetText,
                TextSizePrefs.inboxPreviewSp(this),
                snippetColor,
                Typeface.NORMAL
        );
        if (!showDraft && searchingInbox) {
            snippet.setText(highlightSearchMatch(snippetText, searchQuery.trim()));
        }
        snippet.setSingleLine(true);
        snippet.setEllipsize(TextUtils.TruncateAt.END);
        snippet.setPadding(0, dp(5), 0, 0);
        middle.addView(snippet);

        LinearLayout trailing = new LinearLayout(this);
        trailing.setOrientation(LinearLayout.VERTICAL);
        trailing.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        trailing.setMinimumWidth(dp(68));
        row.addView(trailing);

        TextView time = text(
                SmsStore.formatTime(this, conversation.dateMillis),
                12,
                conversation.unreadCount > 0 ? MINT : MUTED,
                conversation.unreadCount > 0 ? Typeface.BOLD : Typeface.NORMAL
        );
        trailing.addView(time);

        if (conversation.unreadCount > 0) {
            View unreadDot = new View(this);
            unreadDot.setBackgroundResource(R.drawable.unread_badge);
            unreadDot.setContentDescription("Unread messages");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(10), dp(10));
            params.gravity = Gravity.END;
            params.topMargin = dp(10);
            trailing.addView(unreadDot, params);
        }
        row.setOnLongClickListener(v -> {
            showConversationMenu(conversation);
            return true;
        });
        return row;
    }

    private View emptyStateView(String title, String subtitle, boolean featureLargeLogo) {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setBackgroundColor(BLACK);
        empty.setPadding(dp(18), featureLargeLogo ? dp(20) : dp(44), dp(18), dp(20));

        ImageView logo = new ImageView(this);
        logo.setBackgroundColor(BLACK);
        logo.setImageResource(featureLargeLogo ? R.drawable.spam_blocked_empty_logo : R.mipmap.crow_launcher);
        logo.setScaleType(featureLargeLogo ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        logo.setAlpha(featureLargeLogo ? 0.82f : 0.72f);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int logoSize = featureLargeLogo
                ? Math.min(dp(280), screenWidth - dp(48))
                : dp(106);
        int logoHeight = featureLargeLogo ? dp(300) : logoSize;
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(logoSize, logoHeight);
        empty.addView(logo, logoParams);

        TextView titleView = text(title, 20, TEXT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, featureLargeLogo ? dp(12) : dp(16), 0, 0);
        empty.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitleView = text(subtitle, 14, MUTED, Typeface.NORMAL);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp(6), 0, 0);
        empty.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        return empty;
    }

    private FrameLayout contactAvatar(Conversation conversation, int sizeDp, int initialSp) {
        if (conversation != null && LocalMmsStore.isGroupAddress(conversation.address)) {
            return groupAvatar(conversation.address, sizeDp, initialSp);
        }
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackgroundResource(R.drawable.avatar_circle);
        avatar.setClipToOutline(true);
        if (!TextUtils.isEmpty(conversation.photoUri)) {
            ImageView photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setImageURI(Uri.parse(conversation.photoUri));
            avatar.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        } else {
            String initialText = TextUtils.isEmpty(conversation.name) ? "?" : conversation.name.substring(0, 1).toUpperCase(Locale.getDefault());
            TextView initial = text(initialText, initialSp, BLACK, Typeface.BOLD);
            initial.setGravity(Gravity.CENTER);
            avatar.addView(initial, new FrameLayout.LayoutParams(-1, -1));
        }
        avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return avatar;
    }

    private FrameLayout groupAvatar(String address, int sizeDp, int initialSp) {
        FrameLayout avatar = new FrameLayout(this);
        List<String> participants = GroupMmsRecipients.remoteRecipients(
                address,
                GroupMmsRecipients.knownOwnNumbers(this)
        );
        int circleSize = Math.max(22, Math.round(sizeDp * 0.68f));
        int offset = Math.max(8, sizeDp - circleSize);
        for (int index = 0; index < Math.min(2, participants.size()); index++) {
            String participant = participants.get(index);
            String name = LocalMmsStore.displayNameForParticipant(this, participant);
            String initial = TextUtils.isEmpty(name) ? "?" : name.substring(0, 1).toUpperCase(Locale.getDefault());
            TextView circle = text(initial, Math.max(12, initialSp / 2), BLACK, Typeface.BOLD);
            circle.setGravity(Gravity.CENTER);
            circle.setBackground(roundedBackground(index == 0 ? CYAN : MINT, circleSize / 2));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(circleSize), dp(circleSize));
            params.leftMargin = dp(index == 0 ? 0 : offset);
            params.topMargin = dp(index == 0 ? offset : 0);
            avatar.addView(circle, params);
        }
        avatar.setContentDescription(GroupMmsRecipients.totalPeopleCount(this, address) + " people in this group");
        avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return avatar;
    }

    private void showChat(Conversation conversation) {
        showChat(conversation, false);
    }

    private void showChat(Conversation conversation, boolean blockedOnly) {
        showChat(conversation, blockedOnly, "");
    }

    private void showChat(Conversation conversation, boolean blockedOnly, String initialSearchQuery) {
        ScreenMode previousScreen = screenMode;
        String previousAddress = activeConversation == null ? "" : activeConversation.address;
        inboxLoadGeneration++;
        cancelTask(inboxLoadTask);
        clearCurrentFocusBeforeNavigation();
        screenMode = ScreenMode.CHAT;
        styleSystemBars();
        threadSearchQuery = TextUtils.isEmpty(initialSearchQuery) ? "" : initialSearchQuery.trim();
        activeConversation = conversation;
        activeThreadBlockedOnly = blockedOnly;
        if (conversation == null || !sameAddress(previousAddress, conversation.address)) {
            activeThreadMessageLimit = RECENT_THREAD_LIMIT;
            activeThreadHasOlderMessages = false;
        }
        clearPendingImageIfDifferentConversation(conversation);
        markConversationReadAsync(conversation);
        if (previousScreen != ScreenMode.CHAT && canRestoreChatScreen(conversation, blockedOnly)) {
            root = cachedChatRoot;
            activeMessagesList = cachedChatMessagesList;
            activeScrollView = cachedChatScrollView;
            activeJumpToBottomButton = cachedChatJumpToBottomButton;
            setContentView(root);
            applySystemBarInsets(root);
            refreshActiveThreadAsync(true);
            return;
        }
        root = installScreenRoot();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(headerBackground());
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(12), 0);
        bar.setBackgroundColor(Color.TRANSPARENT);
        header.addView(bar, new LinearLayout.LayoutParams(-1, dp(HEADER_BAR_HEIGHT_DP)));

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back_mint);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Back to messages");
        back.setScaleType(ImageView.ScaleType.CENTER);
        setFeedbackClickListener(back, v -> showInbox());
        bar.addView(back, new LinearLayout.LayoutParams(dp(50), dp(48)));

        FrameLayout avatar = contactAvatar(conversation, 38, 15);
        setFeedbackClickListener(avatar, v -> showContactQuickActions(conversation));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        avatarParams.rightMargin = dp(10);
        bar.addView(avatar, avatarParams);

        boolean groupConversation = LocalMmsStore.isGroupAddress(conversation.address);
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(conversation.name, groupConversation ? 17 : 18, TEXT, Typeface.BOLD);
        title.setOnClickListener(v -> showContactQuickActions(conversation));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleBlock.addView(title, new LinearLayout.LayoutParams(-1, -2));
        if (groupConversation) {
            int participantCount = GroupMmsRecipients.totalPeopleCount(this, conversation.address);
            TextView subtitle = text(participantCount + " people", 12, MUTED, Typeface.NORMAL);
            subtitle.setSingleLine(true);
            titleBlock.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        }
        bar.addView(titleBlock, new LinearLayout.LayoutParams(0, -1, 1));

        if (!groupConversation) {
            ImageButton call = new ImageButton(this);
            call.setImageResource(android.R.drawable.ic_menu_call);
            call.setColorFilter(MINT);
            call.setBackgroundColor(Color.TRANSPARENT);
            call.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            call.setContentDescription("Call " + conversation.name);
            setFeedbackClickListener(call, v -> callContact(conversation.address));
            bar.addView(call, new LinearLayout.LayoutParams(dp(42), dp(40)));
        }

        FrameLayout menu = threeDotMenuButton("Conversation options");
        setFeedbackClickListener(menu, v -> showConversationMenu(conversation));
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(56), dp(40));
        bar.addView(menu, menuParams);

        FrameLayout threadArea = new FrameLayout(this);
        threadArea.setBackgroundColor(BLACK);
        root.addView(threadArea, new LinearLayout.LayoutParams(-1, 0, 1));

        ImageView watermark = new ImageView(this);
        watermark.setImageResource(R.drawable.crow_watermark_brand);
        watermark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        watermark.setAlpha(0.11f);
        watermark.setContentDescription("");
        FrameLayout.LayoutParams watermarkParams = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
        watermarkParams.setMargins(dp(26), dp(24), dp(26), dp(24));
        threadArea.addView(watermark, watermarkParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(12), dp(10), dp(12), dp(20));
        scroll.addView(messages);
        threadArea.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        Button jumpToBottom = new Button(this);
        jumpToBottom.setText(R.string.newest_messages);
        jumpToBottom.setAllCaps(false);
        jumpToBottom.setTextColor(BLACK);
        jumpToBottom.setTextSize(14);
        jumpToBottom.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        jumpToBottom.setMinWidth(0);
        jumpToBottom.setSingleLine(true);
        jumpToBottom.setPadding(dp(10), 0, dp(10), 0);
        jumpToBottom.setBackground(primaryGradientBackground(22));
        jumpToBottom.setVisibility(View.GONE);
        jumpToBottom.setContentDescription("Jump to newest message");
        applyPressFeedback(jumpToBottom);
        jumpToBottom.setOnClickListener(v -> {
            performTapFeedback(v);
            scroll.fullScroll(View.FOCUS_DOWN);
            jumpToBottom.setText(R.string.newest_messages);
            jumpToBottom.setVisibility(View.GONE);
        });
        FrameLayout.LayoutParams jumpParams = new FrameLayout.LayoutParams(dp(116), dp(44), Gravity.END | Gravity.BOTTOM);
        jumpParams.setMargins(0, 0, dp(14), dp(12));
        threadArea.addView(jumpToBottom, jumpParams);
        scroll.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (isScrollNearBottom(scroll, dp(120))) {
                jumpToBottom.setText(R.string.newest_messages);
                jumpToBottom.setVisibility(View.GONE);
            } else if (jumpToBottom.getVisibility() != View.VISIBLE) {
                jumpToBottom.setText(R.string.newest_messages);
                jumpToBottom.setVisibility(View.VISIBLE);
            }
        });
        activeMessagesList = messages;
        activeScrollView = scroll;
        activeJumpToBottomButton = jumpToBottom;
        cachedChatRoot = root;
        cachedChatMessagesList = messages;
        cachedChatScrollView = scroll;
        cachedChatJumpToBottomButton = jumpToBottom;
        cachedChatAddress = conversation.address;
        cachedChatBlockedOnly = blockedOnly;
        List<ChatMessage> cachedRows = threadRowsCache.get(threadCacheKey(conversation.address, blockedOnly));
        if (cachedRows != null) {
            activeThreadHasOlderMessages = cachedRows.size() >= activeThreadMessageLimit;
            scroll.setVisibility(View.INVISIBLE);
            renderActiveThreadRowsStaged(new ArrayList<>(cachedRows), true);
        }
        refreshActiveThreadAsync(true);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setBackgroundColor(BLACK);
        composer.setPadding(dp(10), dp(10), dp(10), dp(COMPOSER_BOTTOM_PADDING_DP));
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));
        liftComposerAboveKeyboard(root, composer, scroll);

        if (pendingImageForConversation(conversation)) {
            composer.addView(pendingImagePreviews());
        }

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBaselineAligned(false);
        composer.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

        inputRow.addView(attachButton(v -> showAttachmentMenu()), new LinearLayout.LayoutParams(dp(48), dp(48)));

        EditText input = new EditText(this);
        input.setHint("Enter message");
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setTextSize(TextSizePrefs.composerSp(this));
        input.setTextColor(Color.rgb(25, 25, 25));
        input.setHintTextColor(Color.rgb(130, 130, 130));
        input.setBackgroundResource(R.drawable.composer_background);
        String savedDraft = DraftStore.draft(this, conversation.address);
        if (!TextUtils.isEmpty(savedDraft)) {
            input.setText(savedDraft);
            input.setSelection(input.getText().length());
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleDraftSave(conversation.address, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                settleComposerAtBottom(scroll);
            }
        });
        inputRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        addVoiceButton(inputRow, input);

        ImageButton send = sendButton(v -> sendMessage(input, scroll, v));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(58), dp(40));
        sendParams.leftMargin = dp(8);
        inputRow.addView(send, sendParams);
    }

    private boolean pendingImageForConversation(Conversation conversation) {
        return conversation != null
                && !pendingImageUris.isEmpty()
                && sameAddress(conversation.address, pendingImageAddress);
    }

    private void clearPendingImageIfDifferentConversation(Conversation conversation) {
        if (!pendingImageUris.isEmpty() && (conversation == null || !sameAddress(conversation.address, pendingImageAddress))) {
            deleteCameraImagesIfNeeded(pendingImageUris);
            pendingImageUris.clear();
            pendingImageAddress = "";
        }
    }

    private View pendingImagePreviews() {
        return attachedImagePreviews(pendingImageUris, uri -> {
            deleteCameraImageIfNeeded(uri);
            pendingImageUris.remove(uri);
            if (pendingImageUris.isEmpty()) {
                pendingImageAddress = "";
            }
            refreshAttachmentScreen();
        });
    }

    private View composeImagePreviews() {
        return attachedImagePreviews(pendingComposeImageUris, uri -> {
            deleteCameraImageIfNeeded(uri);
            pendingComposeImageUris.remove(uri);
            showComposePage(false);
        });
    }

    private View attachedImagePreviews(List<Uri> imageUris, java.util.function.Consumer<Uri> removeAction) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout previews = new LinearLayout(this);
        previews.setOrientation(LinearLayout.HORIZONTAL);
        previews.setPadding(dp(4), 0, dp(4), dp(6));
        scroll.addView(previews, new HorizontalScrollView.LayoutParams(-2, dp(92)));
        if (imageUris == null) {
            return scroll;
        }
        int count = imageUris.size();
        for (int index = 0; index < count; index++) {
            previews.addView(attachedImagePreview(imageUris.get(index), index, count, removeAction));
        }
        return scroll;
    }

    private View attachedImagePreview(Uri imageUri, int index, int count, java.util.function.Consumer<Uri> removeAction) {
        FrameLayout tile = new FrameLayout(this);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        setMessageImage(preview, imageUri);
        preview.setBackgroundColor(SURFACE);
        preview.setContentDescription(count > 1
                ? "Attached picture " + (index + 1) + " of " + count
                : "Attached picture");
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(dp(78), dp(78), Gravity.BOTTOM | Gravity.START);
        tile.addView(preview, previewParams);

        ImageButton remove = new ImageButton(this);
        remove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        remove.setColorFilter(BLACK);
        remove.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        remove.setContentDescription("Remove attached picture " + (index + 1));
        remove.setPadding(dp(8), dp(8), dp(8), dp(8));
        remove.setMinimumWidth(0);
        remove.setMinimumHeight(0);
        remove.setBackground(roundedBackground(MINT, 16));
        remove.setOnClickListener(v -> {
            if (removeAction != null) {
                removeAction.accept(imageUri);
            }
        });
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.END);
        tile.addView(remove, removeParams);

        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(88), dp(86));
        tileParams.rightMargin = dp(6);
        tile.setLayoutParams(tileParams);
        return tile;
    }

    private void refreshAttachmentScreen() {
        if (activeConversation != null) {
            showChat(activeConversation, activeThreadBlockedOnly);
        }
    }

    private void markConversationReadAsync(Conversation conversation) {
        if (conversation == null) {
            return;
        }
        String threadId = conversation.threadId;
        String address = conversation.address;
        clearConversationUnreadImmediately(address);
        Context appContext = getApplicationContext();
        stateWriter.submit(() -> {
            boolean readVerified = SmsStore.markConversationReadVerified(appContext, threadId, address);
            if (readVerified) {
                List<Conversation> savedRows = InboxSnapshotStore.load(appContext);
                if (!savedRows.isEmpty()) {
                    InboxSnapshotStore.save(appContext, rowsWithConversationRead(savedRows, address));
                }
            }
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                reconcileReadOverride(locallyReadAddresses, address, readVerified);
                if (!readVerified) {
                    invalidateInboxPresentationCache();
                }
                if (screenMode == ScreenMode.INBOX && activityResumed) {
                    refreshInboxList(true);
                }
            });
        });
    }

    private void clearConversationUnreadImmediately(String address) {
        if (TextUtils.isEmpty(address)) {
            return;
        }
        locallyReadAddresses.add(address);
        for (java.util.Map.Entry<String, List<Conversation>> entry : inboxRowsCache.snapshot().entrySet()) {
            inboxRowsCache.put(entry.getKey(), rowsWithConversationRead(entry.getValue(), address));
        }
        // The cached inbox View still contains the old unread dot even though its row data is current.
        renderedInboxCacheKey = "";
        SmsStore.markSearchIndexRead(address);
        MessageNotifier.clearIncomingForAddress(this, address);
    }

    static List<Conversation> rowsWithConversationRead(List<Conversation> rows, String address) {
        if (TextUtils.isEmpty(address)) {
            return rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        }
        Set<String> addresses = new HashSet<>();
        addresses.add(address);
        return rowsWithConversationsRead(rows, addresses);
    }

    static List<Conversation> rowsWithConversationsRead(List<Conversation> rows, Set<String> addresses) {
        ArrayList<Conversation> updated = new ArrayList<>();
        if (rows == null) {
            return updated;
        }
        for (Conversation conversation : rows) {
            if (conversation == null
                    || conversation.unreadCount == 0
                    || !containsMatchingAddress(addresses, conversation.address)) {
                updated.add(conversation);
                continue;
            }
            updated.add(new Conversation(
                    conversation.threadId,
                    conversation.address,
                    conversation.name,
                    conversation.photoUri,
                    conversation.snippet,
                    conversation.dateMillis,
                    0
            ));
        }
        return updated;
    }

    static boolean removeMatchingAddress(Set<String> addresses, String address) {
        if (addresses == null || TextUtils.isEmpty(address)) {
            return false;
        }
        boolean removed = false;
        for (String stored : new HashSet<>(addresses)) {
            if (AddressUtil.sameConversationAddress(stored, address)) {
                removed |= addresses.remove(stored);
            }
        }
        return removed;
    }

    static boolean reconcileReadOverride(Set<String> addresses, String address, boolean readVerified) {
        return !readVerified && removeMatchingAddress(addresses, address);
    }

    private static boolean containsMatchingAddress(Set<String> addresses, String address) {
        if (addresses == null || addresses.isEmpty() || TextUtils.isEmpty(address)) {
            return false;
        }
        for (String stored : addresses) {
            if (AddressUtil.sameConversationAddress(stored, address)) {
                return true;
            }
        }
        return false;
    }

    private void markAllMessagesRead() {
        Context appContext = getApplicationContext();
        stateWriter.submit(() -> {
            SmsStore.markAllRead(appContext);
            LocalMmsStore.markAllRead(appContext);
            MessageNotifier.clearAllIncoming(appContext);
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                invalidateInboxPresentationCache();
                Toast.makeText(this, "All messages marked read.", Toast.LENGTH_SHORT).show();
                if (screenMode == ScreenMode.INBOX) {
                    refreshInboxList(true);
                }
            });
        });
    }

    private View messageBubble(ChatMessage message) {
        return messageBubble(message, false, false);
    }

    private View messageBubble(ChatMessage message, boolean groupedWithPrevious, boolean groupedWithNext) {
        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(message.outgoing ? Gravity.END : Gravity.START);
        holder.setPadding(0, dp(groupedWithPrevious ? 1 : 5), 0, dp(groupedWithNext ? 1 : 4));
        holder.setOnLongClickListener(v -> {
            showMessageActions(message);
            return true;
        });
        if (isRetryableFailedMessage(message)) {
            holder.setOnClickListener(v -> confirmRetryFailedMessage(message));
        }

        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(message.outgoing ? Gravity.END : Gravity.START);

        Uri imageUri = messageImageUri(message.imageUri);
        boolean hasImage = imageUri != null;
        String displayText = displayableText(message);
        String senderLabel = senderLabelForMessage(message);
        if (!groupedWithPrevious && !TextUtils.isEmpty(senderLabel)) {
            TextView sender = text(senderLabel, TextSizePrefs.senderSp(this), MUTED, Typeface.BOLD);
            sender.setPadding(dp(6), 0, dp(6), dp(2));
            stack.addView(sender);
        }
        if (hasImage) {
            FrameLayout imageFrame = new FrameLayout(this);
            imageFrame.setBackground(messageShapeBackground(SURFACE, message.outgoing, groupedWithPrevious, groupedWithNext));
            imageFrame.setClipToOutline(true);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            setMessageImage(image, imageUri);
            image.setOnClickListener(v -> showPicture(message.imageUri));
            image.setOnLongClickListener(v -> {
                showMessageActions(message);
                return true;
            });
            imageFrame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            imageFrame.setOnClickListener(v -> showPicture(message.imageUri));
            stack.addView(imageFrame, new LinearLayout.LayoutParams(dp(285), imageHeightForUri(message.imageUri)));
        }
        if (!TextUtils.isEmpty(displayText) || !hasImage) {
            TextView bubble = text(displayText, TextSizePrefs.messageSp(this), BLACK, Typeface.NORMAL);
            if (!TextUtils.isEmpty(threadSearchQuery)) {
                bubble.setText(highlightSearchMatch(displayText, threadSearchQuery));
            }
            bubble.setMaxWidth(dp(285));
            bubble.setPadding(dp(14), dp(9), dp(14), dp(9));
            bubble.setBackground(messageShapeBackground(
                    message.outgoing ? MINT : CYAN,
                    message.outgoing,
                    groupedWithPrevious,
                    groupedWithNext
            ));
            stack.addView(bubble);
        }

        String verificationCode = message.outgoing ? "" : VerificationCodeUtil.findCode(displayText);
        if (!TextUtils.isEmpty(verificationCode)) {
            Button copyCode = new Button(this);
            copyCode.setText(getString(R.string.copy_verification_code, verificationCode));
            copyCode.setAllCaps(false);
            copyCode.setTextColor(TEXT);
            copyCode.setTextSize(12);
            copyCode.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            copyCode.setMinWidth(0);
            copyCode.setPadding(dp(10), 0, dp(10), 0);
            copyCode.setBackground(roundedBackground(SURFACE, 16));
            copyCode.setContentDescription("Copy verification code " + verificationCode);
            setFeedbackClickListener(copyCode, v -> copyVerificationCode(verificationCode));
            LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(-2, dp(34));
            codeParams.topMargin = dp(4);
            stack.addView(copyCode, codeParams);
        }

        if (!groupedWithNext || !TextUtils.isEmpty(message.status)) {
            TextView timestamp = text(messageTimestampLabel(message), TextSizePrefs.timestampSp(this), MUTED, Typeface.NORMAL);
            timestamp.setPadding(dp(6), dp(3), dp(6), 0);
            stack.addView(timestamp);
        }
        holder.addView(stack);
        return holder;
    }

    private GradientDrawable messageShapeBackground(int color, boolean outgoing, boolean groupedWithPrevious, boolean groupedWithNext) {
        float outer = dp(20);
        float inner = dp(15);
        float topLeft = !outgoing && groupedWithPrevious ? inner : outer;
        float bottomLeft = !outgoing && groupedWithNext ? inner : outer;
        float topRight = outgoing && groupedWithPrevious ? inner : outer;
        float bottomRight = outgoing && groupedWithNext ? inner : outer;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(new float[] {
                topLeft, topLeft,
                topRight, topRight,
                bottomRight, bottomRight,
                bottomLeft, bottomLeft
        });
        return drawable;
    }

    private View dateSeparator(long dateMillis) {
        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(0, dp(15), 0, dp(8));
        TextView label = text(dayLabel(dateMillis), 12, MINT, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(11), dp(5), dp(11), dp(5));
        label.setBackground(roundedBackground(SURFACE, 12));
        holder.addView(label, new LinearLayout.LayoutParams(-2, -2));
        return holder;
    }

    private String dayLabel(long dateMillis) {
        long now = System.currentTimeMillis();
        if (MessageGrouping.sameDay(dateMillis, now)) {
            return "Today";
        }
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (MessageGrouping.sameDay(dateMillis, yesterday.getTimeInMillis())) {
            return "Yesterday";
        }
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(dateMillis));
    }

    private String messageTimestampLabel(ChatMessage message) {
        if (message == null) {
            return "";
        }
        String time = SmsStore.formatMessageTimestamp(this, message.dateMillis);
        String status = message.displayStatus();
        if (TextUtils.isEmpty(status)) {
            return time;
        }
        if (isRetryableFailedMessage(message)) {
            return "Failed - tap to retry | " + time;
        }
        return status + " | " + time;
    }

    private void showMessageActions(ChatMessage message) {
        if (message == null) {
            return;
        }
        ArrayList<String> options = new ArrayList<>();
        boolean hasImage = messageImageUri(message.imageUri) != null;
        if (hasDisplayableText(message)) {
            options.add("Copy text");
        }
        if (hasDisplayableText(message) || hasImage) {
            options.add("Forward");
        }
        if (hasImage) {
            options.add("View picture");
            options.add("Save picture");
        }
        if (isRetryableFailedMessage(message)) {
            options.add("Retry sending");
        }
        if (message.canDeleteStoredMessage() || message.hasLocalStatus()) {
            options.add("Delete message");
        }
        options.add("Message details");

        new AlertDialog.Builder(this)
                .setTitle("Message options")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String choice = options.get(which);
                    if ("Copy text".equals(choice)) {
                        copyMessageText(displayableText(message));
                    } else if ("Forward".equals(choice)) {
                        forwardMessage(message);
                    } else if ("View picture".equals(choice)) {
                        showPicture(message.imageUri);
                    } else if ("Save picture".equals(choice)) {
                        Uri uri = messageImageUri(message.imageUri);
                        if (uri != null) {
                            savePictureToGallery(uri);
                        }
                    } else if ("Retry sending".equals(choice)) {
                        confirmRetryFailedMessage(message);
                    } else if ("Delete message".equals(choice)) {
                        confirmDeleteMessage(message);
                    } else if ("Message details".equals(choice)) {
                        showMessageDetails(message);
                    }
                })
                .show();
    }

    private boolean isRetryableFailedMessage(ChatMessage message) {
        return message != null
                && message.outgoing
                && ChatMessage.STATUS_FAILED.equals(message.status)
                && !TextUtils.isEmpty(message.localStatusId)
                && (SmsSender.failedMessageForRetry(this, message.localStatusId) != null
                || LocalMmsStore.failedMessageForRetry(this, message.localStatusId) != null);
    }

    private void confirmRetryFailedMessage(ChatMessage message) {
        SmsSender.RetryMessage textRetry = message == null
                ? null
                : SmsSender.failedMessageForRetry(this, message.localStatusId);
        LocalMmsStore.RetryMessage mmsRetry = message == null
                ? null
                : LocalMmsStore.failedMessageForRetry(this, message.localStatusId);
        if (textRetry == null && mmsRetry == null) {
            Toast.makeText(this, "This failed message is no longer available to retry.", Toast.LENGTH_SHORT).show();
            refreshActiveThreadAsync(false);
            return;
        }
        String body = textRetry != null ? textRetry.body : mmsRetry.body;
        String prompt = TextUtils.isEmpty(body) || LocalMmsStore.PICTURE_MESSAGE.equals(body)
                ? "Send this picture again?"
                : body;
        new AlertDialog.Builder(this)
                .setTitle(mmsRetry != null && mmsRetry.hasImage() ? "Retry this picture?" : "Retry this message?")
                .setMessage(prompt)
                .setPositiveButton("Retry", (dialog, which) -> {
                    if (textRetry != null) {
                        retryFailedText(message.localStatusId, textRetry);
                    } else {
                        retryFailedMms(message.localStatusId, mmsRetry);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void retryFailedText(String failedId, SmsSender.RetryMessage retry) {
        if (blockSendWhilePictureJobActive()) {
            return;
        }
        try {
            SmsSender.sendAndRecord(this, retry.address, retry.body);
            SmsSender.deletePendingById(this, failedId);
            discardConversationCaches(retry.address);
            invalidateInboxPresentationCache();
            Toast.makeText(this, "Sending again.", Toast.LENGTH_SHORT).show();
            refreshActiveThreadAsync(true);
        } catch (SmsSender.SendException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void retryFailedMms(String failedId, LocalMmsStore.RetryMessage retry) {
        if (blockSendWhilePictureJobActive()) {
            return;
        }
        if (retry.hasImage()) {
            try {
                List<String> recipients = LocalMmsStore.isGroupAddress(retry.address)
                        ? MmsImageSender.recipientsForAddress(
                                retry.address,
                                GroupMmsRecipients.knownOwnNumbers(this)
                        )
                        : null;
                startImageSend(
                        ImageSendOrigin.RETRY,
                        retry.address,
                        LocalMmsStore.displayNameForAddress(this, retry.address),
                        recipients,
                        retry.body,
                        null,
                        null,
                        null,
                        activeThreadBlockedOnly,
                        failedId,
                        java.util.Collections.singletonList(Uri.parse(retry.imageUri))
                );
            } catch (SmsSender.SendException ex) {
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            long sentAt = MmsTextSender.sendAndRecord(this, retry.address, retry.body);
            LocalMmsStore.deleteFailedMessageById(this, failedId);
            rememberSentConversationImmediately(
                    retry.address,
                    LocalMmsStore.displayNameForAddress(this, retry.address),
                    retry.body,
                    false,
                    sentAt
            );
            discardConversationCaches(retry.address);
            invalidateInboxPresentationCache();
            Toast.makeText(this, "Sending again.", Toast.LENGTH_SHORT).show();
            refreshActiveThreadAsync(true);
        } catch (SmsSender.SendException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasDisplayableText(ChatMessage message) {
        return !TextUtils.isEmpty(displayableText(message));
    }

    private String displayableText(ChatMessage message) {
        if (message == null || TextUtils.isEmpty(message.body)) {
            return "";
        }
        if (messageImageUri(message.imageUri) != null && LocalMmsStore.PICTURE_MESSAGE.equals(message.body)) {
            return "";
        }
        return message.body;
    }

    private void copyMessageText(String body) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || TextUtils.isEmpty(body)) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Message", body));
        Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show();
    }

    private void copyVerificationCode(String code) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || TextUtils.isEmpty(code)) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Verification code", code));
        Toast.makeText(this, "Code copied.", Toast.LENGTH_SHORT).show();
    }

    private void showPicture(String imageUri) {
        Uri uri = messageImageUri(imageUri);
        if (uri == null) {
            return;
        }
        ScreenMode returnScreen = screenMode;
        pictureReturnScreen = returnScreen;
        screenMode = ScreenMode.PICTURE;
        Conversation returnConversation = activeConversation;
        activeMessagesList = null;
        activeScrollView = null;
        styleSystemBars();
        root = installScreenRoot();

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(18), 0);
        bar.setBackgroundColor(BLACK);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(HEADER_BAR_HEIGHT_DP)));

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back_mint);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Back to conversation");
        back.setScaleType(ImageView.ScaleType.CENTER);
        setFeedbackClickListener(back, v -> {
            if (returnScreen == ScreenMode.MEDIA && returnConversation != null) {
                showConversationMediaPage(returnConversation);
            } else if (returnConversation != null) {
                showChat(returnConversation, activeThreadBlockedOnly);
            } else {
                showInbox();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView title = text("Picture message", 20, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        ImageButton share = new ImageButton(this);
        share.setImageResource(android.R.drawable.ic_menu_share);
        share.setColorFilter(MINT);
        share.setBackgroundColor(Color.TRANSPARENT);
        share.setContentDescription("Share picture");
        setFeedbackClickListener(share, v -> sharePicture(uri));
        bar.addView(share, new LinearLayout.LayoutParams(dp(48), dp(48)));

        ImageButton save = new ImageButton(this);
        save.setImageResource(android.R.drawable.ic_menu_save);
        save.setColorFilter(MINT);
        save.setBackgroundColor(Color.TRANSPARENT);
        save.setContentDescription("Save picture to gallery");
        setFeedbackClickListener(save, v -> savePictureToGallery(uri));
        bar.addView(save, new LinearLayout.LayoutParams(dp(48), dp(48)));

        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        setMessageImage(image, uri);
        image.setBackgroundColor(BLACK);
        int padding = dp(12);
        image.setPadding(padding, padding, padding, padding);
        root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void sharePicture(Uri uri) {
        try {
            Uri sharedUri = uri;
            if ("file".equals(uri.getScheme())) {
                sharedUri = FileProvider.getUriForFile(
                        this,
                        MmsFiles.CAMERA_AUTHORITY,
                        new File(uri.getPath())
                );
            }
            Intent share = new Intent(Intent.ACTION_SEND)
                    .setType(pictureMimeType(uri))
                    .putExtra(Intent.EXTRA_STREAM, sharedUri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share picture"));
        } catch (Exception ex) {
            Toast.makeText(this, "Picture could not be shared.", Toast.LENGTH_SHORT).show();
        }
    }

    private void savePictureToGallery(Uri sourceUri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Saving to the gallery needs Android 10 or newer.", Toast.LENGTH_LONG).show();
            return;
        }
        String mimeType = pictureMimeType(sourceUri);
        String extension = mimeType.endsWith("png")
                ? ".png"
                : (mimeType.endsWith("gif") ? ".gif" : (mimeType.endsWith("webp") ? ".webp" : ".jpg"));
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "Crow_Message_" + System.currentTimeMillis() + extension);
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Crow Messenger");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri destination = null;
        try {
            destination = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (destination == null) {
                throw new IllegalStateException("Gallery destination unavailable");
            }
            try (java.io.InputStream input = getContentResolver().openInputStream(sourceUri);
                 OutputStream output = getContentResolver().openOutputStream(destination)) {
                if (input == null || output == null) {
                    throw new IllegalStateException("Picture stream unavailable");
                }
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count > 0) {
                        output.write(buffer, 0, count);
                    }
                }
            }
            ContentValues complete = new ContentValues();
            complete.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(destination, complete, null, null);
            Toast.makeText(this, "Saved to Pictures/Crow Messenger.", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            if (destination != null) {
                getContentResolver().delete(destination, null, null);
            }
            Toast.makeText(this, "Picture could not be saved.", Toast.LENGTH_SHORT).show();
        }
    }

    private String pictureMimeType(Uri uri) {
        String type = getContentResolver().getType(uri);
        if (!TextUtils.isEmpty(type) && type.startsWith("image/")) {
            return type;
        }
        String path = uri == null || TextUtils.isEmpty(uri.getPath())
                ? ""
                : uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".gif")) {
            return "image/gif";
        }
        if (path.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private Uri messageImageUri(String imageUri) {
        if (TextUtils.isEmpty(imageUri)) {
            return null;
        }
        try {
            Uri uri = Uri.parse(imageUri);
            if (TextUtils.isEmpty(uri.getPath())) {
                return null;
            }
            if ("file".equals(uri.getScheme()) && !new File(uri.getPath()).exists()) {
                return null;
            }
            return uri;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showMessageDetails(ChatMessage message) {
        if (message == null) {
            return;
        }
        StringBuilder details = new StringBuilder();
        details.append(TextUtils.isEmpty(message.status) ? (message.outgoing ? "Sent" : "Received") : message.status)
                .append("\n")
                .append(SmsStore.formatMessageTimestamp(this, message.dateMillis));
        if (!TextUtils.isEmpty(message.senderAddress)) {
            details.append("\nFrom: ")
                    .append(LocalMmsStore.displayNameForParticipant(this, message.senderAddress));
        }
        if (!TextUtils.isEmpty(message.imageUri)) {
            details.append("\nIncludes picture");
        }
        new AlertDialog.Builder(this)
                .setTitle("Message details")
                .setMessage(details.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private String senderLabelForMessage(ChatMessage message) {
        if (message == null || message.outgoing || activeConversation == null
                || !LocalMmsStore.isGroupAddress(activeConversation.address)
                || TextUtils.isEmpty(message.senderAddress)) {
            return "";
        }
        return LocalMmsStore.displayNameForParticipant(this, message.senderAddress);
    }

    private void refreshActiveThreadAsync(boolean scrollToBottom) {
        Conversation conversation = activeConversation;
        if (conversation == null || activeMessagesList == null) {
            return;
        }
        int generation = ++threadLoadGeneration;
        boolean blockedOnly = activeThreadBlockedOnly;
        String requestedQuery = threadSearchQuery;
        int requestedLimit = activeThreadMessageLimit;
        String cacheKey = threadCacheKey(conversation.address, blockedOnly);
        Context appContext = getApplicationContext();
        cancelTask(threadLoadTask);
        threadLoadTask = threadLoader.submit(() -> {
            ThreadLoadResult result = loadThreadRows(
                    appContext,
                    conversation.address,
                    blockedOnly,
                    requestedQuery,
                    requestedLimit
            );
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            runOnUiThread(() -> {
                if (isDestroyed()
                        || activeConversation == null
                        || !sameAddress(activeConversation.address, conversation.address)
                        || generation != threadLoadGeneration
                        || activeMessagesList == null) {
                    return;
                }
                List<ChatMessage> rows = result.rows;
                List<ChatMessage> snapshot = new ArrayList<>(rows);
                boolean rowsChanged = true;
                if (TextUtils.isEmpty(requestedQuery)) {
                    rowsChanged = !sameMessageRows(threadRowsCache.get(cacheKey), snapshot);
                    threadRowsCache.put(cacheKey, snapshot);
                }
                boolean olderStateChanged = activeThreadHasOlderMessages != result.hasOlder;
                activeThreadHasOlderMessages = result.hasOlder;
                if (rowsChanged || olderStateChanged) {
                    renderActiveThreadRowsStaged(snapshot, scrollToBottom);
                }
            });
        });
    }

    private ThreadLoadResult loadThreadRows(
            Context context,
            String address,
            boolean blockedOnly,
            String query,
            int limit
    ) {
        if (TextUtils.isEmpty(query)) {
            int safeLimit = Math.max(1, limit);
            List<ChatMessage> loaded = new ArrayList<>(SmsStore.loadRecentMessagesForAddress(
                    context,
                    address,
                    safeLimit + 1,
                    blockedOnly
            ));
            boolean hasOlder = loaded.size() > safeLimit;
            return new ThreadLoadResult(initialThreadRows(loaded, safeLimit), hasOlder);
        }
        return new ThreadLoadResult(
                new ArrayList<>(SmsStore.loadMessagesForAddress(context, address, blockedOnly)),
                false
        );
    }

    static String threadCacheKey(String address, boolean blockedOnly) {
        return (blockedOnly ? "blocked" : "inbox") + "|" + (address == null ? "" : address.trim());
    }

    static boolean sameMessageRows(List<ChatMessage> first, List<ChatMessage> second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            ChatMessage left = first.get(index);
            ChatMessage right = second.get(index);
            if (left == right) {
                continue;
            }
            if (left == null || right == null
                    || !TextUtils.equals(left.body, right.body)
                    || !TextUtils.equals(left.imageUri, right.imageUri)
                    || !TextUtils.equals(left.senderAddress, right.senderAddress)
                    || !TextUtils.equals(left.status, right.status)
                    || !TextUtils.equals(left.localStatusId, right.localStatusId)
                    || !TextUtils.equals(left.sourceType, right.sourceType)
                    || !TextUtils.equals(left.sourceId, right.sourceId)
                    || left.dateMillis != right.dateMillis
                    || left.outgoing != right.outgoing) {
                return false;
            }
        }
        return true;
    }

    private boolean canRestoreChatScreen(Conversation conversation, boolean blockedOnly) {
        return conversation != null
                && cachedChatRoot != null
                && cachedChatMessagesList != null
                && cachedChatScrollView != null
                && cachedChatBlockedOnly == blockedOnly
                && sameAddress(cachedChatAddress, conversation.address)
                && !pendingImageForConversation(conversation);
    }

    private void renderActiveThreadRowsStaged(List<ChatMessage> rows, boolean scrollToBottom) {
        int renderGeneration = ++threadRenderGeneration;
        if (!scrollToBottom
                || !TextUtils.isEmpty(threadSearchQuery)
                || rows.size() <= INITIAL_THREAD_RENDER_LIMIT) {
            renderActiveThreadRows(rows, scrollToBottom);
            return;
        }
        List<ChatMessage> initialRows = initialThreadRows(rows, INITIAL_THREAD_RENDER_LIMIT);
        LinearLayout target = activeMessagesList;
        String address = activeConversation == null ? "" : activeConversation.address;
        renderActiveThreadRows(initialRows, scrollToBottom);
        if (target == null) {
            return;
        }
        target.postDelayed(() -> {
            if (renderGeneration != threadRenderGeneration
                    || target != activeMessagesList
                    || activeConversation == null
                    || !sameAddress(address, activeConversation.address)
                    || !TextUtils.isEmpty(threadSearchQuery)) {
                return;
            }
            renderActiveThreadRows(rows, scrollToBottom);
        }, FULL_THREAD_RENDER_DELAY_MILLIS);
    }

    static List<ChatMessage> initialThreadRows(List<ChatMessage> rows, int limit) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        int count = Math.max(1, limit);
        int start = Math.max(0, rows.size() - count);
        return new ArrayList<>(rows.subList(start, rows.size()));
    }

    private void renderActiveThreadRows(List<ChatMessage> rows, boolean scrollToBottom) {
        if (activeMessagesList == null) {
            return;
        }
        activeMessagesList.removeAllViews();
        String query = threadSearchQuery.trim();
        boolean searching = !TextUtils.isEmpty(query);
        if (searching) {
            List<ChatMessage> matches = new ArrayList<>();
            for (ChatMessage message : rows) {
                if (matchesThreadSearch(message, query)) {
                    matches.add(message);
                }
            }
            activeMessagesList.addView(searchStatusRow(query, matches.size()));
            rows = matches;
        } else if (rows.isEmpty()) {
            activeMessagesList.addView(emptyThreadView());
        }
        if (!searching && activeThreadHasOlderMessages) {
            Button older = actionButton("Load older messages", v -> {
                v.setEnabled(false);
                v.setVisibility(View.GONE);
                loadOlderMessages();
            });
            older.setBackground(roundedBackground(SURFACE, 18));
            older.setTextColor(MINT);
            LinearLayout.LayoutParams olderParams = new LinearLayout.LayoutParams(-1, dp(44));
            olderParams.setMargins(dp(36), dp(8), dp(36), dp(12));
            activeMessagesList.addView(older, olderParams);
        }
        if (searching && rows.isEmpty()) {
            TextView empty = text("No matching messages.", 15, MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, 0);
            activeMessagesList.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
        for (int index = 0; index < rows.size(); index++) {
            ChatMessage message = rows.get(index);
            ChatMessage previous = index > 0 ? rows.get(index - 1) : null;
            ChatMessage next = index + 1 < rows.size() ? rows.get(index + 1) : null;
            if (previous == null || !MessageGrouping.sameDay(previous.dateMillis, message.dateMillis)) {
                activeMessagesList.addView(dateSeparator(message.dateMillis));
            }
            activeMessagesList.addView(messageBubble(
                    message,
                    MessageGrouping.canGroup(previous, message),
                    MessageGrouping.canGroup(message, next)
            ));
        }
        if (activeScrollView != null) {
            if (searching) {
                activeScrollView.post(() -> {
                    activeScrollView.scrollTo(0, 0);
                    activeScrollView.setVisibility(View.VISIBLE);
                });
            } else if (scrollToBottom) {
                showThreadAtBottomBeforeDraw();
            } else {
                activeScrollView.setVisibility(View.VISIBLE);
            }
        } else {
            activeMessagesList.setVisibility(View.VISIBLE);
        }
    }

    private View emptyThreadView() {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18), dp(52), dp(18), dp(24));

        ImageView crow = new ImageView(this);
        crow.setImageResource(R.drawable.crow_watermark_brand);
        crow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        crow.setAlpha(0.22f);
        crow.setContentDescription("");
        empty.addView(crow, new LinearLayout.LayoutParams(dp(116), dp(116)));

        TextView title = text("Start the conversation", 19, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, 0);
        empty.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text("Say hello when you're ready.", 14, MUTED, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, 0);
        empty.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        return empty;
    }

    private void showThreadAtBottomBeforeDraw() {
        if (activeScrollView == null || activeMessagesList == null) {
            return;
        }
        activeScrollView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (activeScrollView == null || activeMessagesList == null) {
                    return true;
                }
                activeScrollView.getViewTreeObserver().removeOnPreDrawListener(this);
                int bottom = Math.max(0, activeMessagesList.getHeight() - activeScrollView.getHeight());
                activeScrollView.scrollTo(0, bottom);
                activeScrollView.setVisibility(View.VISIBLE);
                settleThreadAtBottom();
                return true;
            }
        });
        activeScrollView.requestLayout();
    }

    private void settleThreadAtBottom() {
        if (activeScrollView == null || activeMessagesList == null) {
            return;
        }
        for (int delay : BOTTOM_SETTLE_DELAYS_MS) {
            activeScrollView.postDelayed(this::scrollActiveThreadToBottom, delay);
        }
    }

    private void scrollActiveThreadToBottom() {
        if (activeScrollView == null || activeMessagesList == null) {
            return;
        }
        int bottom = Math.max(0, activeMessagesList.getHeight() - activeScrollView.getHeight());
        activeScrollView.scrollTo(0, bottom);
        if (activeJumpToBottomButton != null) {
            activeJumpToBottomButton.setText(R.string.newest_messages);
            activeJumpToBottomButton.setVisibility(View.GONE);
        }
    }

    private void showNewMessageButton() {
        if (activeJumpToBottomButton == null) {
            return;
        }
        activeJumpToBottomButton.setText(R.string.new_message);
        activeJumpToBottomButton.setVisibility(View.VISIBLE);
    }

    private int imageHeightForUri(String imageUri) {
        int targetWidth = dp(285);
        if (TextUtils.isEmpty(imageUri)) {
            return targetWidth;
        }
        Integer cachedHeight = imageHeightCache.get(imageUri);
        if (cachedHeight != null) {
            return cachedHeight;
        }
        int height = targetWidth;
        try {
            Uri uri = messageImageUri(imageUri);
            if (uri == null) {
                return height;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(uri.getPath(), options);
            height = scaledImageHeight(targetWidth, dp(160), dp(360), options.outWidth, options.outHeight);
        } catch (Exception ignored) {
        }
        imageHeightCache.put(imageUri, height);
        return height;
    }

    private static void setMessageImage(ImageView image, Uri uri) {
        image.setImageURI(uri);
        if (image.getDrawable() instanceof Animatable) {
            ((Animatable) image.getDrawable()).start();
        }
    }

    static int scaledImageHeight(int targetWidth, int minimumHeight, int maximumHeight, int sourceWidth, int sourceHeight) {
        if (targetWidth <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return Math.max(0, targetWidth);
        }
        int lowerBound = Math.max(0, minimumHeight);
        int upperBound = Math.max(lowerBound, maximumHeight);
        int scaledHeight = Math.round(targetWidth * (sourceHeight / (float) sourceWidth));
        return Math.max(lowerBound, Math.min(upperBound, scaledHeight));
    }

    private SpannableString highlightSearchMatch(String body, String query) {
        SpannableString highlighted = new SpannableString(TextUtils.isEmpty(body) ? "" : body);
        if (TextUtils.isEmpty(body) || TextUtils.isEmpty(query)) {
            return highlighted;
        }
        String lowerBody = body.toLowerCase(Locale.getDefault());
        String lowerQuery = query.toLowerCase(Locale.getDefault());
        int start = lowerBody.indexOf(lowerQuery);
        while (start >= 0) {
            int end = start + query.length();
            highlighted.setSpan(new BackgroundColorSpan(Color.rgb(255, 239, 130)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            highlighted.setSpan(new ForegroundColorSpan(BLACK), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = lowerBody.indexOf(lowerQuery, end);
        }
        return highlighted;
    }

    static String searchExcerpt(String text, String query, int maximumCharacters) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(query) || maximumCharacters <= 0) {
            return TextUtils.isEmpty(text) ? "" : text;
        }
        String needle = query.trim();
        if (TextUtils.isEmpty(needle) || text.length() <= maximumCharacters) {
            return text;
        }
        int matchStart = text.toLowerCase(Locale.getDefault())
                .indexOf(needle.toLowerCase(Locale.getDefault()));
        if (matchStart < 0) {
            return text;
        }
        int before = Math.max(12, maximumCharacters / 3);
        int start = Math.max(0, matchStart - before);
        int end = Math.min(text.length(), start + maximumCharacters);
        int matchEnd = matchStart + needle.length();
        if (matchEnd > end) {
            end = Math.min(text.length(), matchEnd + before);
            start = Math.max(0, end - maximumCharacters);
        }
        String excerpt = text.substring(start, end).trim();
        return (start > 0 ? "..." : "") + excerpt + (end < text.length() ? "..." : "");
    }

    static String threadSearchForInboxResult(Conversation conversation, String query, String draft) {
        if (conversation == null || TextUtils.isEmpty(query)) {
            return "";
        }
        String needle = query.trim();
        if (TextUtils.isEmpty(needle)
                || TextUtils.equals(conversation.snippet, conversation.address)
                || (!TextUtils.isEmpty(draft) && TextUtils.equals(draft, conversation.snippet))
                || !containsSearchText(conversation.snippet, needle)) {
            return "";
        }
        return needle;
    }

    private static boolean containsSearchText(String text, String query) {
        return !TextUtils.isEmpty(text)
                && !TextUtils.isEmpty(query)
                && text.toLowerCase(Locale.getDefault()).contains(query.toLowerCase(Locale.getDefault()));
    }

    private View searchStatusRow(String query, int count) {
        TextView status = text("Search: " + query + "  |  " + count + " result" + (count == 1 ? "" : "s") + "  |  Clear", 13, MINT, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setBackground(roundedBackground(SURFACE, 18));
        status.setOnClickListener(v -> {
            threadSearchQuery = "";
            refreshActiveThreadAsync(false);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        status.setLayoutParams(params);
        return status;
    }

    private boolean matchesThreadSearch(ChatMessage message, String query) {
        String body = message == null ? "" : message.body;
        return containsSearchText(body, query);
    }

    private void showBlockDialog(Conversation conversation) {
        boolean blocked = Blocklist.isBlocked(this, conversation.address);
        new AlertDialog.Builder(this)
                .setTitle(blocked ? "Unblock this sender?" : "Block this sender?")
                .setMessage(blocked
                        ? "Their messages will return to your normal inbox."
                        : "Their messages will go to Blocked instead of your normal inbox.")
                .setPositiveButton(blocked ? "Unblock" : "Block", (dialog, which) -> {
                    if (blocked) {
                        Blocklist.unblock(this, conversation.address);
                    } else {
                        ConversationSuppression.block(this, conversation.address);
                    }
                    discardCachedInboxScreen();
                    showInbox();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markConversationSpam(Conversation conversation) {
        ConversationSuppression.markSpam(this, conversation.address, conversation.threadId);
        discardCachedInboxScreen();
        searchQuery = "";
        showingBlocked = true;
        inboxRowsCache.put(
                inboxCacheKey(true, ""),
                new ArrayList<>(java.util.Collections.singletonList(conversation))
        );
        blockedInboxLoadedAtMillis = 0L;
        Toast.makeText(this, "Moved to Spam & blocked.", Toast.LENGTH_SHORT).show();
        activeConversation = null;
        showInbox();
    }

    private void unmarkConversationSpam(Conversation conversation) {
        SpamFilter.unmarkSpam(this, conversation.address, conversation.threadId);
        discardCachedInboxScreen();
        Toast.makeText(this, "Moved back to normal inbox.", Toast.LENGTH_SHORT).show();
        activeConversation = null;
        showingBlocked = false;
        showInbox();
    }

    private void showConversationMenu(Conversation conversation) {
        ArrayList<String> options = new ArrayList<>();
        boolean group = LocalMmsStore.isGroupAddress(conversation.address);
        boolean pinned = PinnedStore.isPinned(this, conversation.address);

        options.add("Conversation info");
        options.add("Search conversation");
        options.add("Photos & media");
        options.add(pinned ? "Unpin conversation" : "Pin conversation");
        if (!group) {
            options.add("Scheduling");
        }
        options.add("Notifications & sound");
        options.add("Privacy & cleanup");

        showCrowMenu(conversation.name, options, choice -> {
            if ("Conversation info".equals(choice)) {
                showConversationInfoPage(conversation);
            } else if ("Search conversation".equals(choice)) {
                showConversationSearchDialog();
            } else if ("Photos & media".equals(choice)) {
                showConversationMediaPage(conversation);
            } else if ("Pin conversation".equals(choice) || "Unpin conversation".equals(choice)) {
                setConversationPinned(conversation, "Pin conversation".equals(choice));
            } else if ("Scheduling".equals(choice)) {
                showSchedulingMenu(conversation);
            } else if ("Notifications & sound".equals(choice)) {
                showConversationNotificationMenu(conversation);
            } else if ("Privacy & cleanup".equals(choice)) {
                showConversationPrivacyMenu(conversation);
            }
        });
    }

    private void setConversationPinned(Conversation conversation, boolean pinned) {
        if (pinned) {
            PinnedStore.pin(this, conversation.address);
        } else {
            PinnedStore.unpin(this, conversation.address);
        }
        discardCachedInboxScreen();
        Toast.makeText(this, pinned ? "Pinned." : "Unpinned.", Toast.LENGTH_SHORT).show();
        refreshAfterConversationSetting(conversation);
    }

    private void showSchedulingMenu(Conversation conversation) {
        ArrayList<String> options = new ArrayList<>();
        options.add("Schedule message");
        if (ScheduledMessageStore.hasForAddress(this, conversation.address)) {
            options.add("Scheduled messages");
        }
        showCrowMenu("Scheduling", options, choice -> {
            if ("Schedule message".equals(choice)) {
                showScheduleMessageDialog(conversation);
            } else {
                showScheduledMessagesPage(conversation);
            }
        });
    }

    private void showConversationNotificationMenu(Conversation conversation) {
        ArrayList<String> options = new ArrayList<>();
        boolean hasSoundSetting = ContactNotificationPrefs.hasCustomSetting(this, conversation.address);
        boolean muted = ContactNotificationPrefs.isSilent(this, conversation.address);
        options.add(muted ? "Unmute conversation" : "Mute conversation");
        options.add("Custom notification sound");
        if (hasSoundSetting) {
            options.add("Use default notification sound");
        }
        showCrowMenu("Notifications & sound", options, choice -> {
            if ("Custom notification sound".equals(choice)) {
                pickNotificationSound(conversation.address);
            } else if ("Mute conversation".equals(choice)) {
                muteConversation(conversation.address);
            } else {
                restoreDefaultNotificationSound(conversation.address);
            }
        });
    }

    private void showConversationPrivacyMenu(Conversation conversation) {
        ArrayList<String> options = new ArrayList<>();
        boolean group = LocalMmsStore.isGroupAddress(conversation.address);
        boolean spam = SpamFilter.isMarkedSpam(this, conversation.address);
        boolean blocked = Blocklist.isBlocked(this, conversation.address);
        options.add(spam ? "Not spam" : "Mark as spam");
        if (!group) {
            options.add(blocked ? "Unblock sender" : "Block sender");
        }
        options.add("Move to trash");
        showCrowMenu("Privacy & cleanup", options, choice -> {
            if ("Mark as spam".equals(choice)) {
                markConversationSpam(conversation);
            } else if ("Not spam".equals(choice)) {
                unmarkConversationSpam(conversation);
            } else if ("Block sender".equals(choice) || "Unblock sender".equals(choice)) {
                showBlockDialog(conversation);
            } else if ("Move to trash".equals(choice)) {
                showDeleteConversationDialog(conversation);
            }
        });
    }

    private void showTrashPage() {
        screenMode = ScreenMode.TRASH;
        activeConversation = null;
        activeMessagesList = null;
        activeScrollView = null;
        styleSystemBars();
        root = installScreenRoot();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), 0, dp(18), 0);
        header.setBackground(headerBackground());
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(HEADER_BAR_HEIGHT_DP)));

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back_mint);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Back to messages");
        back.setScaleType(ImageView.ScaleType.CENTER);
        setFeedbackClickListener(back, v -> showInbox());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView title = text("Trash", 22, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        List<TrashStore.Item> trashed = TrashStore.all(this);
        if (trashed.isEmpty()) {
            root.addView(emptyStateView("Trash is empty", "Conversations moved here can be restored.", true), new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(12), dp(10), dp(28));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        for (TrashStore.Item item : trashed) {
            list.addView(trashConversationRow(item));
        }
    }

    private View trashConversationRow(TrashStore.Item item) {
        Conversation conversation = item.conversation();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(10), dp(8), dp(10));
        row.setMinimumHeight(dp(76));
        applyPressFeedback(row);
        setFeedbackClickListener(row, v -> showTrashActions(item));
        row.addView(contactAvatar(conversation, 46, 18));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView name = text(conversation.name, TextSizePrefs.inboxNameSp(this), TEXT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);
        TextView snippet = text(conversation.snippet, TextSizePrefs.inboxPreviewSp(this), MUTED, Typeface.NORMAL);
        snippet.setSingleLine(true);
        snippet.setEllipsize(TextUtils.TruncateAt.END);
        snippet.setPadding(0, dp(4), 0, 0);
        labels.addView(snippet);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView date = text(SmsStore.formatTime(this, item.trashedAtMillis), 12, MUTED, Typeface.NORMAL);
        row.addView(date);
        return row;
    }

    private void showTrashActions(TrashStore.Item item) {
        showCrowMenu(item.name, java.util.Arrays.asList("Restore", "Delete forever"), choice -> {
            if ("Restore".equals(choice)) {
                TrashStore.restore(this, item.address);
                invalidateInboxPresentationCache();
                Toast.makeText(this, "Conversation restored.", Toast.LENGTH_SHORT).show();
                showTrashPage();
            } else {
                confirmDeleteForever(item);
            }
        });
    }

    private void confirmDeleteForever(TrashStore.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete forever?")
                .setMessage("This permanently removes this conversation from your phone and cannot be undone.")
                .setPositiveButton("Delete forever", (dialog, which) -> deleteConversationForever(item.conversation(), true))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void forwardMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        deleteCameraImagesIfNeeded(pendingComposeImageUris);
        pendingComposeImageUris.clear();
        composeRecipients.clear();
        composeDraft = displayableText(message);
        Uri image = messageImageUri(message.imageUri);
        if (image != null) {
            pendingComposeImageUris.add(image);
        }
        showComposePage(false);
    }

    private void confirmDeleteMessage(ChatMessage message) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this message?")
                .setMessage("This removes only this message from your phone and cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteMessage(message))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteMessage(ChatMessage message) {
        boolean deleted = SmsStore.deleteMessage(this, message);
        if (!deleted && message != null && message.hasLocalStatus()) {
            deleted = SmsSender.deletePendingById(this, message.localStatusId)
                    || LocalMmsStore.deleteFailedMessageById(this, message.localStatusId);
        }
        if (!deleted) {
            Toast.makeText(this, "This message could not be deleted.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (activeConversation != null) {
            discardConversationCaches(activeConversation.address);
        }
        invalidateInboxPresentationCache();
        Toast.makeText(this, "Message deleted.", Toast.LENGTH_SHORT).show();
        refreshActiveThreadAsync(false);
    }

    private void showCrowMenu(String title, List<String> options, MenuChoiceHandler handler) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(12));
        panel.setBackground(roundedBackground(CHAT_HEADER, 28));

        TextView heading = text(title, 21, TEXT, Typeface.BOLD);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, 0, 0, dp(10));
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        for (String option : options) {
            Button button = actionButton(option, v -> {
                dialog.dismiss();
                handler.onChoice(option);
            });
            panel.addView(button);
        }

        Button cancel = new Button(this);
        cancel.setText(R.string.cancel_button);
        cancel.setAllCaps(false);
        cancel.setTextColor(MINT);
        cancel.setTextSize(15);
        cancel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cancel.setBackground(roundedBackground(SURFACE, 22));
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(48));
        cancelParams.setMargins(0, dp(7), 0, dp(2));
        panel.addView(cancel, cancelParams);

        dialog.setView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void showTextSizeMenu() {
        ArrayList<String> options = new ArrayList<>();
        String current = TextSizePrefs.currentLabel(this);
        for (String label : TextSizePrefs.labels()) {
            options.add(label.equals(current) ? label + " (current)" : label);
        }
        ScreenMode previousMode = screenMode;
        Conversation previousConversation = activeConversation;
        showCrowMenu("Text size", options, choice -> {
            String selected = choice.replace(" (current)", "");
            TextSizePrefs.setLabel(this, selected);
            Toast.makeText(this, "Text size set to " + selected + ".", Toast.LENGTH_SHORT).show();
            refreshAfterTextSizeChange(previousMode, previousConversation);
        });
    }

    private void refreshAfterTextSizeChange(ScreenMode previousMode, Conversation previousConversation) {
        if (previousMode == ScreenMode.CHAT && previousConversation != null) {
            showChat(previousConversation, activeThreadBlockedOnly);
        } else {
            showInbox();
        }
    }

    private interface MenuChoiceHandler {
        void onChoice(String choice);
    }

    private static final class ComposeRecipient {
        final String name;
        final String address;

        ComposeRecipient(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    private void loadOlderMessages() {
        if (activeConversation == null || !activeThreadHasOlderMessages) {
            return;
        }
        activeThreadHasOlderMessages = false;
        activeThreadMessageLimit += RECENT_THREAD_LIMIT;
        refreshActiveThreadAsync(false);
    }

    private static final class ThreadLoadResult {
        final List<ChatMessage> rows;
        final boolean hasOlder;

        ThreadLoadResult(List<ChatMessage> rows, boolean hasOlder) {
            this.rows = rows;
            this.hasOlder = hasOlder;
        }
    }

    static final class ComposeIntent {
        final String address;
        final String body;
        final Uri imageUri;
        final ArrayList<Uri> imageUris;

        ComposeIntent(String address, String body, List<Uri> imageUris) {
            this.address = address;
            this.body = body;
            this.imageUris = new ArrayList<>();
            addUniqueImageUris(this.imageUris, imageUris);
            this.imageUri = this.imageUris.isEmpty() ? null : this.imageUris.get(0);
        }
    }

    private void refreshAfterConversationSetting(Conversation conversation) {
        if (activeConversation != null && sameAddress(activeConversation.address, conversation.address)) {
            showChat(conversation, activeThreadBlockedOnly);
        } else {
            showInbox();
        }
    }

    private static final class ImageSendSession {
        final ImageSendOrigin origin;
        final String address;
        final String name;
        final boolean blockedOnly;
        final String failedId;
        final int imageCount;

        ImageSendSession(
                ImageSendOrigin origin,
                String address,
                String name,
                boolean blockedOnly,
                String failedId,
                int imageCount
        ) {
            this.origin = origin;
            this.address = address;
            this.name = TextUtils.isEmpty(name) ? address : name;
            this.blockedOnly = blockedOnly;
            this.failedId = failedId == null ? "" : failedId;
            this.imageCount = Math.max(1, imageCount);
        }
    }

    private static final class ImageSendUi {
        final ImageSendOrigin origin;
        final String address;
        final String name;
        final EditText input;
        final View sendControl;
        final boolean blockedOnly;
        final String failedId;

        ImageSendUi(ImageSendSession session, EditText input, View sendControl) {
            this.origin = session.origin;
            this.address = session.address;
            this.name = session.name;
            this.input = input;
            this.sendControl = sendControl;
            this.blockedOnly = session.blockedOnly;
            this.failedId = session.failedId;
        }
    }

    private void showConversationMediaPage(Conversation conversation) {
        if (conversation == null) {
            showInbox();
            return;
        }
        screenMode = ScreenMode.MEDIA;
        activeConversation = conversation;
        activeMessagesList = null;
        activeScrollView = null;
        styleSystemBars();
        root = installScreenRoot();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), 0, dp(18), 0);
        header.setBackground(headerBackground());
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(HEADER_BAR_HEIGHT_DP)));

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back_mint);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Back to conversation");
        back.setScaleType(ImageView.ScaleType.CENTER);
        setFeedbackClickListener(back, v -> showChat(conversation, activeThreadBlockedOnly));
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView title = text("Photos & media", 22, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(10), dp(12), dp(10), dp(28));
        TextView loading = text("Loading pictures...", 15, MUTED, Typeface.NORMAL);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(40), 0, 0);
        content.addView(loading, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        int generation = ++threadLoadGeneration;
        cancelTask(threadLoadTask);
        Context appContext = getApplicationContext();
        threadLoadTask = threadLoader.submit(() -> {
            List<ChatMessage> messages = SmsStore.loadMessagesForAddress(appContext, conversation.address, activeThreadBlockedOnly);
            ArrayList<ChatMessage> pictures = new ArrayList<>();
            for (ChatMessage message : messages) {
                if (messageImageUri(message.imageUri) != null) {
                    pictures.add(message);
                }
            }
            runOnUiThread(() -> {
                if (isDestroyed()
                        || generation != threadLoadGeneration
                        || screenMode != ScreenMode.MEDIA
                        || activeConversation == null
                        || !sameAddress(activeConversation.address, conversation.address)) {
                    return;
                }
                renderConversationMedia(content, pictures);
            });
        });
    }

    private void renderConversationMedia(LinearLayout content, List<ChatMessage> pictures) {
        content.removeAllViews();
        if (pictures == null || pictures.isEmpty()) {
            content.addView(emptyStateView("No pictures yet", "Pictures from this conversation will appear here.", true), new LinearLayout.LayoutParams(-1, dp(430)));
            return;
        }
        TextView count = text(pictures.size() == 1 ? "1 picture" : pictures.size() + " pictures", 14, MUTED, Typeface.NORMAL);
        count.setPadding(dp(4), 0, dp(4), dp(10));
        content.addView(count, new LinearLayout.LayoutParams(-1, -2));
        for (int start = pictures.size() - 1; start >= 0; start -= 3) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.TOP);
            for (int column = 0; column < 3; column++) {
                int index = start - column;
                if (index < 0) {
                    View spacer = new View(this);
                    row.addView(spacer, new LinearLayout.LayoutParams(0, dp(112), 1));
                    continue;
                }
                ChatMessage message = pictures.get(index);
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                setMessageImage(image, messageImageUri(message.imageUri));
                image.setBackgroundColor(SURFACE);
                image.setContentDescription("Picture from " + SmsStore.formatMessageTimestamp(this, message.dateMillis));
                setFeedbackClickListener(image, v -> showPicture(message.imageUri));
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(0, dp(112), 1);
                imageParams.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(image, imageParams);
            }
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void showConversationInfoPage(Conversation conversation) {
        screenMode = ScreenMode.CONVERSATION_INFO;
        activeMessagesList = null;
        activeScrollView = null;
        styleSystemBars();
        root = installScreenRoot();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(headerBackground());
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(18), 0);
        bar.setBackgroundColor(Color.TRANSPARENT);
        header.addView(bar, new LinearLayout.LayoutParams(-1, dp(HEADER_BAR_HEIGHT_DP)));

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back_mint);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Back to conversation");
        back.setScaleType(ImageView.ScaleType.CENTER);
        setFeedbackClickListener(back, v -> showChat(conversation, activeThreadBlockedOnly));
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView title = text("Conversation info", 22, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(36));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content.addView(contactAvatar(conversation, 88, 32), new LinearLayout.LayoutParams(dp(88), dp(88)));

        TextView name = text(conversation.name, 26, TEXT, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(14), 0, 0);
        content.addView(name, new LinearLayout.LayoutParams(-1, -2));

        if (!LocalMmsStore.isGroupAddress(conversation.address)) {
            TextView address = text(conversation.address, 14, MUTED, Typeface.NORMAL);
            address.setGravity(Gravity.CENTER);
            address.setPadding(0, dp(4), 0, dp(10));
            content.addView(address, new LinearLayout.LayoutParams(-1, -2));
        }

        TextView status = text(conversationStatusText(conversation), 13, MUTED, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, dp(14));
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        boolean group = LocalMmsStore.isGroupAddress(conversation.address);
        if (group) {
            addGroupParticipants(content, conversation.address);
        }
        if (!group) {
            content.addView(actionButton("Call", v -> callContact(conversation.address)));
            content.addView(actionButton("View contact", v -> viewContact(conversation.address)));
        }
        content.addView(actionButton(PinnedStore.isPinned(this, conversation.address) ? "Unpin conversation" : "Pin conversation", v -> {
            if (PinnedStore.isPinned(this, conversation.address)) {
                PinnedStore.unpin(this, conversation.address);
                Toast.makeText(this, "Unpinned.", Toast.LENGTH_SHORT).show();
            } else {
                PinnedStore.pin(this, conversation.address);
                Toast.makeText(this, "Pinned.", Toast.LENGTH_SHORT).show();
            }
            discardCachedInboxScreen();
            showConversationInfoPage(conversation);
        }));
        if (!group) {
            content.addView(actionButton("Schedule message", v -> showScheduleMessageDialog(conversation)));
        }
        if (ScheduledMessageStore.hasForAddress(this, conversation.address)) {
            content.addView(actionButton("Scheduled messages", v -> showScheduledMessagesPage(conversation)));
        }
        content.addView(actionButton("Custom notification sound", v -> pickNotificationSound(conversation.address)));
        content.addView(actionButton(ContactNotificationPrefs.isSilent(this, conversation.address) ? "Unmute conversation" : "Mute conversation", v -> {
            if (ContactNotificationPrefs.isSilent(this, conversation.address)) {
                restoreDefaultNotificationSound(conversation.address);
            } else {
                muteConversation(conversation.address);
            }
            showConversationInfoPage(conversation);
        }));
        content.addView(actionButton(SpamFilter.isMarkedSpam(this, conversation.address) ? "Not spam" : "Mark as spam", v -> {
            if (SpamFilter.isMarkedSpam(this, conversation.address)) {
                unmarkConversationSpam(conversation);
            } else {
                markConversationSpam(conversation);
            }
        }));
        if (!group) {
            content.addView(actionButton(Blocklist.isBlocked(this, conversation.address) ? "Unblock sender" : "Block sender", v -> showBlockDialog(conversation)));
        }
        content.addView(actionButton("Search conversation", v -> {
            showChat(conversation, activeThreadBlockedOnly);
            showConversationSearchDialog();
        }));
        content.addView(actionButton("Photos & media", v -> showConversationMediaPage(conversation)));
        content.addView(actionButton("Move to trash", v -> showDeleteConversationDialog(conversation)));
    }

    private String conversationStatusText(Conversation conversation) {
        ArrayList<String> status = new ArrayList<>();
        if (LocalMmsStore.isGroupAddress(conversation.address)) {
            int participantCount = GroupMmsRecipients.totalPeopleCount(this, conversation.address);
            status.add(participantCount > 0 ? participantCount + " people" : "Group message");
        }
        if (PinnedStore.isPinned(this, conversation.address)) {
            status.add("Pinned");
        }
        if (ContactNotificationPrefs.isSilent(this, conversation.address)) {
            status.add("Muted");
        } else if (ContactNotificationPrefs.hasCustomSound(this, conversation.address)) {
            status.add("Custom sound");
        }
        if (SpamFilter.isMarkedSpam(this, conversation.address)) {
            status.add("Marked spam");
        }
        if (!LocalMmsStore.isGroupAddress(conversation.address) && Blocklist.isBlocked(this, conversation.address)) {
            status.add("Blocked");
        }
        return status.isEmpty() ? "No special settings" : TextUtils.join("  |  ", status);
    }

    private void addGroupParticipants(LinearLayout content, String address) {
        List<String> participants = GroupMmsRecipients.remoteRecipients(
                address,
                GroupMmsRecipients.knownOwnNumbers(this)
        );
        if (participants.isEmpty()) {
            return;
        }

        TextView heading = text("People in this group", 18, TEXT, Typeface.BOLD);
        heading.setPadding(0, dp(6), 0, dp(8));
        content.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout self = new LinearLayout(this);
        self.setPadding(dp(16), dp(12), dp(16), dp(12));
        self.setBackground(roundedBackground(SURFACE, 24));
        self.addView(text("You", 16, TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams selfParams = new LinearLayout.LayoutParams(-1, -2);
        selfParams.setMargins(0, 0, 0, dp(8));
        content.addView(self, selfParams);

        for (String participant : participants) {
            content.addView(groupParticipantRow(participant));
        }

        TextView note = text("Group texts, captions, and pictures stay together in this conversation.", 13, MUTED, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(8), dp(6), dp(8), dp(12));
        content.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }

    private View groupParticipantRow(String address) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(roundedBackground(SURFACE, 24));

        String name = SmsStore.displayNameForAddress(this, address);
        TextView nameView = text(LocalMmsStore.displayNameForParticipant(this, address), 16, TEXT, Typeface.BOLD);
        row.addView(nameView, new LinearLayout.LayoutParams(-1, -2));

        if (!TextUtils.isEmpty(name) && !name.equals(address)) {
            TextView addressView = text(address, 13, MUTED, Typeface.NORMAL);
            addressView.setPadding(0, dp(3), 0, 0);
            row.addView(addressView, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);
        return row;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(BLACK);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundedBackground(MINT, 22));
        applyPressFeedback(button);
        button.setOnClickListener(v -> {
            performTapFeedback(v);
            listener.onClick(v);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, dp(7), 0, dp(7));
        button.setLayoutParams(params);
        return button;
    }

    private void showConversationSearchDialog() {
        EditText input = new EditText(this);
        input.setHint("Search this conversation");
        input.setSingleLine(true);
        input.setText(threadSearchQuery);
        input.setSelectAllOnFocus(true);
        input.setTextColor(BLACK);
        input.setHintTextColor(Color.rgb(130, 130, 130));

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Search conversation")
                .setView(input)
                .setPositiveButton("Search", (dialog, which) -> {
                    threadSearchQuery = input.getText().toString().trim();
                    refreshActiveThreadAsync(false);
                })
                .setNegativeButton("Cancel", null);
        if (!TextUtils.isEmpty(threadSearchQuery)) {
            builder.setNeutralButton("Clear", (dialog, which) -> {
                threadSearchQuery = "";
                refreshActiveThreadAsync(false);
            });
        }
        builder.show();
    }

    private void muteConversation(String address) {
        ContactNotificationPrefs.setSound(this, address, null);
        MessageNotifier.resetContactChannel(this, address);
        Toast.makeText(this, "Conversation muted.", Toast.LENGTH_SHORT).show();
    }

    private void restoreDefaultNotificationSound(String address) {
        ContactNotificationPrefs.useDefault(this, address);
        MessageNotifier.resetContactChannel(this, address);
        Toast.makeText(this, "Default notification sound restored.", Toast.LENGTH_SHORT).show();
    }

    private void showScheduleMessageDialog(Conversation conversation) {
        if (conversation == null) {
            return;
        }
        if (LocalMmsStore.isGroupAddress(conversation.address)) {
            Toast.makeText(this, "Scheduled group texts are coming after group sending is finished.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!ensureDefaultSmsAppFor("scheduling")) {
            return;
        }

        flushPendingDraft();
        Calendar sendAt = Calendar.getInstance();
        sendAt.add(Calendar.HOUR_OF_DAY, 1);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(12));
        panel.setBackground(roundedBackground(CHAT_HEADER, 28));

        TextView title = text("Schedule message", 21, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView recipient = text("To: " + conversation.name, 14, MUTED, Typeface.NORMAL);
        recipient.setGravity(Gravity.CENTER);
        recipient.setPadding(0, dp(5), 0, dp(10));
        panel.addView(recipient, new LinearLayout.LayoutParams(-1, -2));

        EditText body = new EditText(this);
        body.setHint("Type the message");
        body.setMinLines(2);
        body.setMaxLines(5);
        body.setTextSize(TextSizePrefs.composerSp(this));
        body.setTextColor(BLACK);
        body.setHintTextColor(Color.rgb(130, 130, 130));
        body.setBackgroundResource(R.drawable.composer_background);
        String draft = DraftStore.draft(this, conversation.address);
        if (!TextUtils.isEmpty(draft)) {
            body.setText(draft);
            body.setSelection(body.getText().length());
        }
        panel.addView(body, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(actionButton("Voice to text", v -> startVoiceToText(body)));

        Button dateButton = actionButton("", v -> {
        });
        Button timeButton = actionButton("", v -> {
        });
        Runnable updateButtons = () -> {
            dateButton.setText(getString(
                    R.string.schedule_date_label,
                    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(sendAt.getTime())
            ));
            timeButton.setText(getString(
                    R.string.schedule_time_label,
                    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(sendAt.getTime())
            ));
        };
        dateButton.setOnClickListener(v -> new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    sendAt.set(Calendar.YEAR, year);
                    sendAt.set(Calendar.MONTH, month);
                    sendAt.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateButtons.run();
                },
                sendAt.get(Calendar.YEAR),
                sendAt.get(Calendar.MONTH),
                sendAt.get(Calendar.DAY_OF_MONTH)
        ).show());
        timeButton.setOnClickListener(v -> new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    sendAt.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    sendAt.set(Calendar.MINUTE, minute);
                    sendAt.set(Calendar.SECOND, 0);
                    sendAt.set(Calendar.MILLISECOND, 0);
                    updateButtons.run();
                },
                sendAt.get(Calendar.HOUR_OF_DAY),
                sendAt.get(Calendar.MINUTE),
                false
        ).show());
        updateButtons.run();
        panel.addView(dateButton);
        panel.addView(timeButton);

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        Button schedule = actionButton("Schedule", v -> {
            String messageBody = body.getText().toString().trim();
            long sendAtMillis = sendAt.getTimeInMillis();
            if (TextUtils.isEmpty(messageBody)) {
                Toast.makeText(this, "Type a message first.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (sendAtMillis < System.currentTimeMillis() + 60000L) {
                Toast.makeText(this, "Pick a time at least one minute from now.", Toast.LENGTH_LONG).show();
                return;
            }
            ScheduledMessageStore.ScheduledMessage scheduled = ScheduledMessageStore.save(
                    this,
                    conversation.address,
                    messageBody,
                    sendAtMillis
            );
            if (scheduled == null || !ScheduledSmsReceiver.schedule(this, scheduled)) {
                Toast.makeText(this, "Scheduled text could not be scheduled.", Toast.LENGTH_LONG).show();
                return;
            }
            DraftStore.clear(this, conversation.address);
            discardPendingDraft(conversation.address);
            discardConversationCaches(conversation.address);
            invalidateInboxPresentationCache();
            dialog.dismiss();
            Toast.makeText(this, "Message scheduled for " + formatScheduledTime(sendAtMillis) + ".", Toast.LENGTH_LONG).show();
            showScheduledMessagesPage(conversation);
        });
        panel.addView(schedule);

        Button cancel = new Button(this);
        cancel.setText(R.string.cancel_button);
        cancel.setAllCaps(false);
        cancel.setTextColor(MINT);
        cancel.setTextSize(15);
        cancel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cancel.setBackground(roundedBackground(SURFACE, 22));
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(48));
        cancelParams.setMargins(0, dp(7), 0, dp(2));
        panel.addView(cancel, cancelParams);

        dialog.setView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void showScheduledMessagesPage(Conversation conversation) {
        screenMode = ScreenMode.CONVERSATION_INFO;
        activeMessagesList = null;
        activeScrollView = null;
        styleSystemBars();
        root = installScreenRoot();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(headerBackground());
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(18), 0);
        bar.setBackgroundColor(Color.TRANSPARENT);
        header.addView(bar, new LinearLayout.LayoutParams(-1, dp(HEADER_BAR_HEIGHT_DP)));

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back_mint);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Back to conversation");
        back.setScaleType(ImageView.ScaleType.CENTER);
        setFeedbackClickListener(back, v -> showChat(conversation, activeThreadBlockedOnly));
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView title = text("Scheduled messages", 22, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(20), dp(18), dp(36));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        List<ScheduledMessageStore.ScheduledMessage> scheduled = ScheduledMessageStore.forAddress(this, conversation.address);
        if (scheduled.isEmpty()) {
            content.addView(emptyStateView(
                    "Nothing scheduled",
                    "Messages you schedule for this conversation will show up here.",
                    false
            ), new LinearLayout.LayoutParams(-1, -2));
            content.addView(actionButton("Schedule message", v -> showScheduleMessageDialog(conversation)));
            return;
        }

        for (ScheduledMessageStore.ScheduledMessage message : scheduled) {
            content.addView(scheduledMessageRow(conversation, message));
        }
        content.addView(actionButton("Schedule another message", v -> showScheduleMessageDialog(conversation)));
    }

    private View scheduledMessageRow(Conversation conversation, ScheduledMessageStore.ScheduledMessage message) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(roundedBackground(SURFACE, 24));

        TextView when = text(
                message.failed() ? "Failed: " + formatScheduledTime(message.failedAtMillis) : formatScheduledTime(message.sendAtMillis),
                14,
                message.failed() ? Color.rgb(255, 170, 170) : MINT,
                Typeface.BOLD
        );
        row.addView(when, new LinearLayout.LayoutParams(-1, -2));

        TextView body = text(message.body, TextSizePrefs.messageSp(this), TEXT, Typeface.NORMAL);
        body.setPadding(0, dp(8), 0, dp(2));
        row.addView(body, new LinearLayout.LayoutParams(-1, -2));

        if (message.failed()) {
            TextView reason = text(message.failureReason, 14, MUTED, Typeface.NORMAL);
            reason.setPadding(0, dp(4), 0, dp(4));
            row.addView(reason, new LinearLayout.LayoutParams(-1, -2));
        }

        Button cancel = actionButton(message.failed() ? "Remove failed text" : "Cancel scheduled text", v -> {
            ScheduledSmsReceiver.deleteAndCancel(this, message.id);
            Toast.makeText(this, message.failed() ? "Failed text removed." : "Scheduled text canceled.", Toast.LENGTH_SHORT).show();
            showScheduledMessagesPage(conversation);
        });
        row.addView(cancel);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(params);
        return row;
    }

    private String formatScheduledTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(millis);
    }

    private void showDeleteConversationDialog(Conversation conversation) {
        new AlertDialog.Builder(this)
                .setTitle("Move this conversation to Trash?")
                .setMessage("You can restore it later from Trash in the main menu.")
                .setPositiveButton("Move to Trash", (dialog, which) -> moveConversationToTrash(conversation))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void moveConversationToTrash(Conversation conversation) {
        flushPendingDraft();
        ConversationSuppression.moveToTrash(this, conversation);
        discardCachedInboxScreen();
        discardConversationCaches(conversation.address);
        Toast.makeText(this, "Conversation moved to Trash.", Toast.LENGTH_SHORT).show();
        activeConversation = null;
        showInbox();
    }

    private void deleteConversationForever(Conversation conversation, boolean returnToTrash) {
        discardPendingDraft(conversation.address);
        DraftStore.clear(this, conversation.address);
        int deleted = SmsStore.deleteConversation(this, conversation.threadId, conversation.address);
        int removedScheduled = deleteScheduledForConversation(conversation.address);
        TrashStore.restore(this, conversation.address);
        discardCachedInboxScreen();
        discardConversationCaches(conversation.address);
        Toast.makeText(this, deleted > 0 || removedScheduled > 0 ? "Conversation deleted forever." : "Conversation removed from Trash.", Toast.LENGTH_SHORT).show();
        activeConversation = null;
        if (returnToTrash) {
            showTrashPage();
        } else {
            showInbox();
        }
    }

    private int deleteScheduledForConversation(String address) {
        int deleted = 0;
        for (ScheduledMessageStore.ScheduledMessage message : ScheduledMessageStore.deleteForAddress(this, address)) {
            ScheduledSmsReceiver.cancel(this, message.id);
            deleted++;
        }
        return deleted;
    }

    private void showContactQuickActions(Conversation conversation) {
        if (!supportsPhoneActions(conversation.address)) {
            showConversationInfoPage(conversation);
            return;
        }
        ArrayList<String> options = new ArrayList<>();
        options.add("View contact");
        options.add("Call");

        new AlertDialog.Builder(this)
                .setTitle(conversation.name)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String choice = options.get(which);
                    if ("View contact".equals(choice)) {
                        viewContact(conversation.address);
                    } else if ("Call".equals(choice)) {
                        callContact(conversation.address);
                    }
                })
                .show();
    }

    private void viewContact(String address) {
        Uri contactUri = contactUriForAddress(address);
        if (contactUri == null) {
            Toast.makeText(this, "I couldn't find this person in Contacts.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            scrollThreadToBottomOnResume = true;
            startActivity(new Intent(Intent.ACTION_VIEW, contactUri));
        } catch (Exception ignored) {
            scrollThreadToBottomOnResume = false;
            Toast.makeText(this, "Contacts could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private Uri contactUriForAddress(String address) {
        if (!supportsPhoneActions(address)) {
            return null;
        }
        Uri lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address));
        try (Cursor cursor = getContentResolver().query(
                lookup,
                new String[] {
                        ContactsContract.PhoneLookup._ID,
                        ContactsContract.PhoneLookup.LOOKUP_KEY
                },
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                String lookupKey = cursor.getString(1);
                return ContactsContract.Contacts.getLookupUri(id, lookupKey);
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    private void callContact(String address) {
        if (!supportsPhoneActions(address)) {
            Toast.makeText(this, "No phone number for this conversation.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            scrollThreadToBottomOnResume = true;
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(address))));
        } catch (Exception ignored) {
            scrollThreadToBottomOnResume = false;
            Toast.makeText(this, "Phone app could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMessage(EditText input, ScrollView scroll, View sendControl) {
        if (blockSendWhilePictureJobActive()) {
            return;
        }
        String body = input.getText().toString().trim();
        boolean hasImage = pendingImageForConversation(activeConversation);
        if ((TextUtils.isEmpty(body) && !hasImage) || activeConversation == null) {
            return;
        }
        if (!ensureDefaultSmsAppFor("sending")) {
            return;
        }
        if (hasImage) {
            Conversation conversation = activeConversation;
            try {
                List<String> recipients = LocalMmsStore.isGroupAddress(conversation.address)
                        ? MmsImageSender.recipientsForAddress(
                                conversation.address,
                                GroupMmsRecipients.knownOwnNumbers(this)
                        )
                        : null;
                startImageSend(
                        ImageSendOrigin.CHAT,
                        conversation.address,
                        conversation.name,
                        recipients,
                        body,
                        pendingImageUris,
                        input,
                        sendControl,
                        activeThreadBlockedOnly,
                        ""
                );
            } catch (SmsSender.SendException ex) {
                Toast.makeText(this, "Picture could not be sent: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            long sentAt;
            if (LocalMmsStore.isGroupAddress(activeConversation.address)) {
                sentAt = MmsTextSender.sendAndRecord(this, activeConversation.address, body);
            } else {
                sentAt = SmsSender.sendAndRecord(this, activeConversation.address, body);
            }
            rememberSentConversationImmediately(
                    activeConversation.address,
                    activeConversation.name,
                    body,
                    false,
                    sentAt
            );
            DraftStore.clear(this, activeConversation.address);
            if (!TextUtils.isEmpty(threadSearchQuery)) {
                threadSearchQuery = "";
            }
            sendSuccessFeedback(sendControl);
            input.setText("");
            hideKeyboard(input);
            input.clearFocus();
            afterKeyboardSettles(input, () -> {
                refreshActiveThreadAsync(true);
                scroll.postDelayed(() -> scroll.fullScroll(View.FOCUS_DOWN), 80);
            });
        } catch (SmsSender.SendException ex) {
            Toast.makeText(this, "Message could not be sent: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showAttachmentMenu() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(12), dp(18), dp(24));
        panel.setBackground(roundedBackground(CHAT_HEADER, 26));

        View handle = new View(this);
        handle.setBackground(roundedBackground(MUTED, 2));
        handle.setAlpha(0.55f);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(42), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(14);
        panel.addView(handle, handleParams);

        TextView heading = text("Add a picture", 20, TEXT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, 0, 0, dp(12));
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        panel.addView(attachmentChoice(
                "Choose from gallery",
                android.R.drawable.ic_menu_gallery,
                dialog,
                this::pickImage
        ));
        panel.addView(attachmentChoice(
                "Take a picture",
                android.R.drawable.ic_menu_camera,
                dialog,
                this::takePhoto
        ));

        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.58f;
            window.setAttributes(attributes);
        }
        panel.setAlpha(0f);
        panel.setTranslationY(dp(28));
        panel.animate().alpha(1f).translationY(0f).setDuration(190).start();
    }

    private View attachmentChoice(String label, int iconResource, Dialog dialog, Runnable action) {
        LinearLayout choice = new LinearLayout(this);
        choice.setGravity(Gravity.CENTER_VERTICAL);
        choice.setPadding(dp(16), 0, dp(16), 0);
        choice.setBackground(roundedBackground(MINT, 18));
        choice.setContentDescription(label);
        applyPressFeedback(choice);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(BLACK);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        choice.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView text = text(label, 16, BLACK, Typeface.BOLD);
        text.setPadding(dp(14), 0, 0, 0);
        choice.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        choice.setOnClickListener(v -> {
            performTapFeedback(v);
            dialog.dismiss();
            action.run();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(56));
        params.setMargins(0, dp(5), 0, dp(5));
        choice.setLayoutParams(params);
        return choice;
    }

    private void pickImage() {
        pendingPickImageForCompose = screenMode == ScreenMode.COMPOSE;
        pendingPickImageAddress = activeConversation == null ? "" : activeConversation.address;
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
            intent.putExtra(
                    MediaStore.EXTRA_PICK_IMAGES_MAX,
                    Math.min(MAX_ATTACHED_IMAGES, MediaStore.getPickImagesMaxLimit())
            );
        } else {
            intent = new Intent(Intent.ACTION_PICK);
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (Exception ignored) {
            clearPendingImagePickTarget();
            Toast.makeText(this, "Photos could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearPendingImagePickTarget() {
        pendingPickImageAddress = "";
        pendingPickImageForCompose = false;
    }

    private void takePhoto() {
        if (screenMode != ScreenMode.CHAT && screenMode != ScreenMode.COMPOSE) {
            return;
        }
        try {
            deleteCameraImageIfNeeded(pendingCameraImageUri);
            pendingCameraForCompose = screenMode == ScreenMode.COMPOSE;
            pendingCameraAddress = activeConversation == null ? "" : activeConversation.address;
            pendingCameraImageUri = createCameraImageUri();
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraImageUri);
            intent.setClipData(ClipData.newRawUri("Crow Messenger picture", pendingCameraImageUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            grantCameraUriPermissions(intent, pendingCameraImageUri);
            startActivityForResult(intent, REQUEST_TAKE_PHOTO);
        } catch (Exception ignored) {
            deleteCameraImageIfNeeded(pendingCameraImageUri);
            pendingCameraImageUri = null;
            pendingCameraAddress = "";
            pendingCameraForCompose = false;
            Toast.makeText(this, "Camera could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private Uri createCameraImageUri() throws Exception {
        File directory = MmsFiles.appFileDir(this, MmsFiles.CAMERA_DIR);
        File file = new File(directory, "camera-" + System.currentTimeMillis() + "-" + System.nanoTime() + ".jpg");
        if (!file.createNewFile()) {
            throw new IllegalStateException("Camera image could not be created");
        }
        return FileProvider.getUriForFile(this, MmsFiles.CAMERA_AUTHORITY, file);
    }

    private void grantCameraUriPermissions(Intent intent, Uri uri) {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        for (ResolveInfo camera : getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (camera.activityInfo != null && !TextUtils.isEmpty(camera.activityInfo.packageName)) {
                grantUriPermission(camera.activityInfo.packageName, uri, flags);
            }
        }
    }

    private void attachSelectedImages(List<Uri> uris, boolean forCompose, String conversationAddress, boolean replaceExisting) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        if (forCompose) {
            if (replaceExisting) {
                deleteCameraImagesIfNeeded(pendingComposeImageUris);
                pendingComposeImageUris.clear();
            }
            addUniqueImageUris(pendingComposeImageUris, uris);
            showComposePage(false);
        } else if (!TextUtils.isEmpty(conversationAddress)) {
            if (replaceExisting) {
                deleteCameraImagesIfNeeded(pendingImageUris);
                pendingImageUris.clear();
            }
            Conversation conversation = SmsStore.conversationForAddress(this, conversationAddress);
            addUniqueImageUris(pendingImageUris, uris);
            pendingImageAddress = conversation.address;
            showChat(conversation, activeThreadBlockedOnly);
        }
    }

    private void attachCapturedImage(Uri uri, boolean forCompose, String conversationAddress) {
        attachSelectedImages(java.util.Collections.singletonList(uri), forCompose, conversationAddress, false);
    }

    private boolean cameraImageHasData(Uri uri) {
        File file = MmsFiles.cameraFileForUri(this, uri);
        return file != null && file.isFile() && file.length() > 0L;
    }

    private boolean saveCameraThumbnail(Intent data, Uri uri) {
        if (data == null || data.getExtras() == null || uri == null) {
            return false;
        }
        Object value = data.getExtras().get("data");
        if (!(value instanceof Bitmap)) {
            return false;
        }
        try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            return output != null && ((Bitmap) value).compress(Bitmap.CompressFormat.JPEG, 95, output);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void deleteCameraImageIfNeeded(Uri uri) {
        MmsFiles.deleteCameraFileUri(this, uri);
    }

    private void deleteCameraImagesIfNeeded(List<Uri> uris) {
        if (uris == null) {
            return;
        }
        for (Uri uri : new ArrayList<>(uris)) {
            deleteCameraImageIfNeeded(uri);
        }
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            startActivityForResult(intent, REQUEST_PICK_CONTACT);
        } catch (Exception ignored) {
            pickingComposeContact = false;
            Toast.makeText(this, "Contacts could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickNotificationSound(String address) {
        pendingSoundAddress = address;
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Notification sound");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ContactNotificationPrefs.soundUri(this, address));
        try {
            startActivityForResult(intent, REQUEST_PICK_SOUND);
        } catch (Exception ignored) {
            pendingSoundAddress = "";
            Toast.makeText(this, "Notification sound picker could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startVoiceToText(EditText target) {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            pendingVoiceInput = target;
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, REQUEST_VOICE_PERMISSION);
            return;
        }
        launchVoiceToText(target);
    }

    private void addVoiceButton(LinearLayout composer, EditText target) {
        if (!ComposerPrefs.voiceButtonVisible(this)) {
            return;
        }
        ImageButton voice = new ImageButton(this);
        voice.setImageResource(android.R.drawable.ic_btn_speak_now);
        voice.setColorFilter(BLACK);
        voice.setBackground(roundedBackground(MINT, 22));
        voice.setContentDescription("Voice to text");
        voice.setPadding(dp(8), dp(8), dp(8), dp(8));
        setFeedbackClickListener(voice, v -> startVoiceToText(target));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(48));
        params.leftMargin = dp(6);
        composer.addView(voice, params);
    }

    private void launchVoiceToText(EditText target) {
        activeVoiceInput = target;
        scrollThreadToBottomOnResume = true;
        Intent intent = voiceToTextIntent();
        try {
            startActivityForResult(intent, REQUEST_VOICE_TEXT);
        } catch (Exception ignored) {
            activeVoiceInput = null;
            scrollThreadToBottomOnResume = false;
            Toast.makeText(this, "Voice typing is not available on this phone.", Toast.LENGTH_LONG).show();
        }
    }

    private Intent voiceToTextIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message");
        return intent;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RUNTIME_PERMISSIONS) {
            handleRuntimePermissionResult(permissions, grantResults);
            return;
        }
        if (requestCode != REQUEST_VOICE_PERMISSION) {
            return;
        }
        handleVoicePermissionResult(grantResults);
    }

    private void handleRuntimePermissionResult(String[] permissions, int[] grantResults) {
        boolean contactsChanged = wasPermissionGranted(permissions, grantResults, Manifest.permission.READ_CONTACTS);
        boolean messageAccessChanged = wasAnyPermissionGranted(permissions, grantResults, MESSAGE_REFRESH_PERMISSIONS);
        if (contactsChanged) {
            SmsStore.clearContactCaches();
        }
        if (!contactsChanged && !messageAccessChanged) {
            return;
        }
        if (screenMode == ScreenMode.INBOX) {
            refreshInboxList(true);
        } else if (screenMode == ScreenMode.CHAT && activeConversation != null) {
            showChat(SmsStore.conversationForAddress(this, activeConversation.address), activeThreadBlockedOnly);
        }
    }

    static boolean wasPermissionGranted(String[] permissions, int[] grantResults, String permission) {
        if (permissions == null || grantResults == null) {
            return false;
        }
        for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
            if (permission.equals(permissions[i]) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    static boolean wasAnyPermissionGranted(String[] permissions, int[] grantResults, String[] requestedPermissions) {
        if (requestedPermissions == null) {
            return false;
        }
        for (String permission : requestedPermissions) {
            if (wasPermissionGranted(permissions, grantResults, permission)) {
                return true;
            }
        }
        return false;
    }

    private void handleVoicePermissionResult(int[] grantResults) {
        EditText target = pendingVoiceInput;
        pendingVoiceInput = null;
        if (target == null) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchVoiceToText(target);
        } else {
            Toast.makeText(this, "Microphone permission is needed for voice typing.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT_SETTINGS) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                writeSettingsBackup(data.getData());
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_SETTINGS) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                restoreSettingsBackup(data.getData());
            }
            return;
        }
        if (requestCode == REQUEST_VOICE_TEXT) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty() && activeVoiceInput != null) {
                    appendVoiceText(activeVoiceInput, matches.get(0));
                }
            }
            activeVoiceInput = null;
            return;
        }
        if (requestCode == REQUEST_PICK_SOUND) {
            if (resultCode == RESULT_OK && data != null) {
                Uri sound = pickedRingtoneUri(data);
                ContactNotificationPrefs.setSound(this, pendingSoundAddress, sound);
                MessageNotifier.resetContactChannel(this, pendingSoundAddress);
                Toast.makeText(this, sound == null ? "Notifications silenced for this contact." : "Notification sound saved.", Toast.LENGTH_SHORT).show();
            }
            pendingSoundAddress = "";
            return;
        }
        if (requestCode == REQUEST_PICK_IMAGE) {
            String conversationAddress = pendingPickImageAddress;
            boolean forCompose = pendingPickImageForCompose;
            clearPendingImagePickTarget();
            if (resultCode == RESULT_OK && data != null) {
                attachSelectedImages(selectedImageUris(data), forCompose, conversationAddress, true);
            }
            return;
        }
        if (requestCode == REQUEST_TAKE_PHOTO) {
            Uri uri = pendingCameraImageUri;
            String conversationAddress = pendingCameraAddress;
            boolean forCompose = pendingCameraForCompose;
            pendingCameraImageUri = null;
            pendingCameraAddress = "";
            pendingCameraForCompose = false;
            finishCameraCapture(uri, forCompose, conversationAddress, data, resultCode, 2);
            return;
        }
        if (requestCode != REQUEST_PICK_CONTACT) {
            return;
        }
        boolean addToCompose = pickingComposeContact || screenMode == ScreenMode.COMPOSE;
        pickingComposeContact = false;
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[] {
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null,
                null,
                null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }
            String name = cursor.getString(0);
            String number = cursor.getString(1);
            if (addToCompose) {
                addComposeRecipient(name, number);
                showComposePage(false);
            } else {
                showChat(new Conversation("", number, TextUtils.isEmpty(name) ? number : name, "", System.currentTimeMillis(), 0), false);
            }
        } catch (RuntimeException ex) {
            pickingComposeContact = false;
            Toast.makeText(this, "Contacts could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private void writeSettingsBackup(Uri destination) {
        Context appContext = getApplicationContext();
        stateWriter.submit(() -> {
            boolean saved = false;
            try (OutputStream output = appContext.getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) {
                    throw new IllegalStateException("No output stream");
                }
                output.write(SettingsBackup.create(appContext).getBytes(StandardCharsets.UTF_8));
                saved = true;
            } catch (Exception ignored) {
            }
            boolean completed = saved;
            runOnUiThread(() -> showBackupResult(
                    completed ? "Settings backup saved." : "Settings backup could not be saved."
            ));
        });
    }

    private void restoreSettingsBackup(Uri source) {
        Context appContext = getApplicationContext();
        stateWriter.submit(() -> {
            boolean restored = false;
            try (InputStream input = appContext.getContentResolver().openInputStream(source)) {
                if (input == null) {
                    throw new IllegalStateException("No input stream");
                }
                String rawBackup = readSmallText(input, SettingsBackup.MAX_BACKUP_CHARACTERS);
                SettingsBackup.restore(appContext, rawBackup);
                restored = true;
            } catch (Exception ignored) {
            }
            boolean completed = restored;
            runOnUiThread(() -> {
                if (!showBackupResult(completed
                        ? "Crow settings restored."
                        : "That Crow settings backup could not be restored.")) {
                    return;
                }
                if (completed) {
                    discardCachedInboxScreen();
                    showInbox();
                }
            });
        });
    }

    private boolean showBackupResult(String message) {
        if (isDestroyed() || isFinishing()) {
            return false;
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        return true;
    }

    static String readSmallText(InputStream input, int maxCharacters) throws Exception {
        if (input == null || maxCharacters <= 0) {
            throw new IllegalArgumentException("Invalid backup input");
        }
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (text.length() + read > maxCharacters) {
                    throw new IllegalArgumentException("Backup is too large");
                }
                text.append(buffer, 0, read);
            }
        }
        return text.toString();
    }

    static ArrayList<Uri> selectedImageUris(Intent data) {
        ArrayList<Uri> images = new ArrayList<>();
        if (data == null) {
            return images;
        }
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                addUniqueImageUri(images, clipData.getItemAt(index).getUri());
            }
        }
        addUniqueImageUri(images, data.getData());
        return images;
    }

    private void finishCameraCapture(
            Uri uri,
            boolean forCompose,
            String conversationAddress,
            Intent resultData,
            int resultCode,
            int retriesRemaining
    ) {
        if (!cameraImageHasData(uri)) {
            saveCameraThumbnail(resultData, uri);
        }
        if (!cameraImageHasData(uri) && retriesRemaining > 0 && root != null) {
            root.postDelayed(
                    () -> finishCameraCapture(uri, forCompose, conversationAddress, resultData, resultCode, retriesRemaining - 1),
                    retriesRemaining == 2 ? 200L : 500L
            );
            return;
        }
        try {
            revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        if (cameraImageHasData(uri)) {
            attachCapturedImage(uri, forCompose, conversationAddress);
        } else {
            deleteCameraImageIfNeeded(uri);
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "The camera did not save that picture. Please try again.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private Uri pickedRingtoneUri(Intent data) {
        if (data == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class);
        }
        return data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
    }

    private boolean addTypedComposeRecipient(EditText numberInput) {
        String number = numberInput == null ? "" : numberInput.getText().toString().trim();
        if (addComposeRecipient(number, number)) {
            numberInput.setText("");
            hideKeyboard(numberInput);
            return true;
        }
        return false;
    }

    private boolean hasTypedComposeNumber(EditText numberInput) {
        return numberInput != null && !TextUtils.isEmpty(numberInput.getText().toString().trim());
    }

    private boolean addComposeRecipient(String name, String number) {
        if (TextUtils.isEmpty(number)) {
            Toast.makeText(this, "That contact does not have a phone number.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!AddressUtil.isSendableSmsRecipient(number)) {
            Toast.makeText(this, "Type a valid phone number first.", Toast.LENGTH_SHORT).show();
            return false;
        }
        for (ComposeRecipient recipient : composeRecipients) {
            if (AddressUtil.sameDigits(recipient.address, number) || TextUtils.equals(recipient.address, number)) {
                Toast.makeText(this, "That person is already added.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        String displayName = TextUtils.isEmpty(name) ? number : name;
        composeRecipients.add(new ComposeRecipient(displayName, number));
        return true;
    }

    private void appendVoiceText(EditText input, String spokenText) {
        if (input == null || TextUtils.isEmpty(spokenText)) {
            return;
        }
        int start = Math.max(0, input.getSelectionStart());
        int end = Math.max(0, input.getSelectionEnd());
        String current = input.getText().toString();
        String insert = spokenText.trim();
        if (!TextUtils.isEmpty(current) && start > 0 && !Character.isWhitespace(current.charAt(start - 1))) {
            insert = " " + insert;
        }
        input.getText().replace(Math.min(start, end), Math.max(start, end), insert);
        input.requestFocus();
        input.setSelection(Math.min(input.getText().length(), Math.min(start, end) + insert.length()));
    }

    private boolean isDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null
                    && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)
                    && roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                return true;
            }
        }
        String packageName = Telephony.Sms.getDefaultSmsPackage(this);
        return getPackageName().equals(packageName);
    }

    private void requestDefaultSmsApp() {
        if (isDefaultSmsApp()) {
            Toast.makeText(this, "Crow Messenger is already your default texting app.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                try {
                    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS), 20);
                } catch (Exception ignored) {
                    Toast.makeText(this, "Default texting app settings could not be opened.", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            Toast.makeText(this, "Default texting app settings could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean ensureDefaultSmsAppFor(String action) {
        if (isDefaultSmsApp()) {
            return true;
        }
        requestDefaultSmsApp();
        Toast.makeText(this, "Set Crow Messenger as default before " + action + ".", Toast.LENGTH_LONG).show();
        return false;
    }

    private void requestRuntimePermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        addMissingPermission(permissions, Manifest.permission.READ_SMS);
        addMissingPermission(permissions, Manifest.permission.RECEIVE_SMS);
        addMissingPermission(permissions, Manifest.permission.RECEIVE_MMS);
        addMissingPermission(permissions, Manifest.permission.RECEIVE_WAP_PUSH);
        addMissingPermission(permissions, Manifest.permission.SEND_SMS);
        addMissingPermission(permissions, Manifest.permission.READ_PHONE_STATE);
        addMissingPermission(permissions, Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addMissingPermission(permissions, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_RUNTIME_PERMISSIONS);
        }
    }

    private void addMissingPermission(ArrayList<String> permissions, String permission) {
        if (!hasPermission(permission)) {
            permissions.add(permission);
        }
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable headerBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(CHAT_HEADER);
        drawable.setCornerRadius(dp(24));
        return drawable;
    }

    private GradientDrawable gradientBackground(int startColor, int endColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { startColor, endColor }
        );
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable primaryGradientBackground(int radiusDp) {
        return gradientBackground(MINT, CYAN, radiusDp);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void applyPressFeedback(View view) {
        view.setOnTouchListener((pressedView, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                pressedView.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                pressedView.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            }
            return false;
        });
    }

    private void setFeedbackClickListener(View view, View.OnClickListener listener) {
        applyPressFeedback(view);
        view.setOnClickListener(v -> {
            performTapFeedback(v);
            listener.onClick(v);
        });
    }

    private void performTapFeedback(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void sendSuccessFeedback(View view) {
        if (view == null) {
            return;
        }
        view.performHapticFeedback(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? HapticFeedbackConstants.CONFIRM
                        : HapticFeedbackConstants.KEYBOARD_TAP
        );
        view.animate().cancel();
        view.animate()
                .scaleX(1.14f)
                .scaleY(1.14f)
                .setDuration(90)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(130).start())
                .start();
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void liftComposerAboveKeyboard(View rootView, View composer, ScrollView scroll) {
        boolean[] keyboardWasVisible = new boolean[] { false };
        int[] previousComposerHeight = new int[] { 0 };
        View scrollContent = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
        int originalContentBottomPadding = scrollContent == null ? 0 : scrollContent.getPaddingBottom();
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            boolean wasNearBottom = isScrollNearBottom(scroll, dp(180));
            Rect visibleFrame = new Rect();
            rootView.getWindowVisibleDisplayFrame(visibleFrame);
            int coveredHeight = rootView.getRootView().getHeight() - visibleFrame.bottom;
            boolean keyboardVisible = coveredHeight > dp(180);
            boolean composerHeightChanged = previousComposerHeight[0] != 0
                    && previousComposerHeight[0] != composer.getHeight();
            int bottomPadding = dp(composerBottomPaddingDp(keyboardVisible));
            if (composer.getPaddingBottom() != bottomPadding) {
                composer.setPadding(composer.getPaddingLeft(), composer.getPaddingTop(), composer.getPaddingRight(), bottomPadding);
            }
            int composerLift = keyboardVisible
                    ? keyboardComposerLift(coveredHeight, rootView.getPaddingBottom())
                    : 0;
            composer.setTranslationY(-composerLift);
            if (scrollContent != null) {
                int contentBottomPadding = keyboardContentBottomPadding(
                        keyboardVisible,
                        originalContentBottomPadding,
                        dp(KEYBOARD_CONTENT_BOTTOM_PADDING_DP)
                );
                if (scrollContent.getPaddingBottom() != contentBottomPadding) {
                    scrollContent.setPadding(
                            scrollContent.getPaddingLeft(),
                            scrollContent.getPaddingTop(),
                            scrollContent.getPaddingRight(),
                            contentBottomPadding
                    );
                }
            }
            int scrollBottomPadding = keyboardScrollBottomPadding(
                    keyboardVisible,
                    composerLift,
                    dp(KEYBOARD_SCROLL_GAP_DP)
            );
            if (scroll.getPaddingBottom() != scrollBottomPadding) {
                scroll.setClipToPadding(true);
                scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(), scroll.getPaddingRight(), scrollBottomPadding);
            }
            if (shouldSettleComposer(
                    keyboardWasVisible[0],
                    keyboardVisible,
                    wasNearBottom,
                    composerHeightChanged
            )) {
                settleComposerAtBottom(scroll);
            }
            keyboardWasVisible[0] = keyboardVisible;
            previousComposerHeight[0] = composer.getHeight();
        });
    }

    static int keyboardScrollBottomPadding(boolean keyboardVisible, int composerLift, int gap) {
        return keyboardVisible ? Math.max(0, composerLift) + Math.max(0, gap) : 0;
    }

    static int keyboardComposerLift(int coveredHeight, int systemBottomInset) {
        return Math.max(0, coveredHeight - Math.max(0, systemBottomInset));
    }

    static int composerBottomPaddingDp(boolean keyboardVisible) {
        return keyboardVisible ? COMPOSER_KEYBOARD_BOTTOM_PADDING_DP : COMPOSER_BOTTOM_PADDING_DP;
    }

    static int keyboardContentBottomPadding(boolean keyboardVisible, int originalPadding, int compactPadding) {
        int safeOriginalPadding = Math.max(0, originalPadding);
        return keyboardVisible
                ? Math.min(safeOriginalPadding, Math.max(0, compactPadding))
                : safeOriginalPadding;
    }

    static boolean shouldSettleComposer(
            boolean keyboardWasVisible,
            boolean keyboardVisible,
            boolean wasNearBottom,
            boolean composerHeightChanged
    ) {
        return (keyboardVisible && !keyboardWasVisible)
                || (wasNearBottom && (keyboardVisible || keyboardWasVisible || composerHeightChanged));
    }

    private boolean isScrollNearBottom(ScrollView scroll, int thresholdPx) {
        if (scroll == null || scroll.getChildCount() == 0) {
            return true;
        }
        View content = scroll.getChildAt(0);
        int visibleBottom = scroll.getScrollY() + scroll.getHeight() - scroll.getPaddingBottom();
        return content.getBottom() - visibleBottom <= thresholdPx;
    }

    private void settleComposerAtBottom(ScrollView scroll) {
        if (scroll == null) {
            return;
        }
        for (int delay : COMPOSER_SETTLE_DELAYS_MS) {
            scroll.postDelayed(() -> {
                if (screenMode == ScreenMode.CHAT && activeScrollView == scroll) {
                    scroll.fullScroll(View.FOCUS_DOWN);
                }
            }, delay);
        }
    }

    private void registerMessageUpdates() {
        IntentFilter filter = new IntentFilter(ACTION_MESSAGE_RECEIVED);
        ContextCompat.registerReceiver(this, messageUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void registerMessageStoreObserver() {
        if (messageStoreObserverRegistered) {
            return;
        }
        try {
            getContentResolver().registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    messageStoreObserver
            );
            messageStoreObserverRegistered = true;
        } catch (SecurityException | IllegalArgumentException ignored) {
            messageStoreObserverRegistered = false;
        }
    }

    private void scheduleMessageStoreRefresh() {
        if (isDestroyed() || isFinishing()) {
            return;
        }
        if (!shouldScheduleMessageStoreRefresh(
                SystemClock.uptimeMillis(),
                lastDirectMessageUpdateUptimeMillis
        )) {
            return;
        }
        if (!activityResumed) {
            pendingMessageRefresh = true;
            return;
        }
        messageStoreHandler.removeCallbacks(messageStoreRefreshTask);
        messageStoreHandler.postDelayed(messageStoreRefreshTask, 180L);
    }

    static boolean shouldScheduleMessageStoreRefresh(long nowUptimeMillis, long lastDirectUpdateUptimeMillis) {
        return lastDirectUpdateUptimeMillis <= 0L
                || nowUptimeMillis < lastDirectUpdateUptimeMillis
                || nowUptimeMillis - lastDirectUpdateUptimeMillis > MESSAGE_STORE_DIRECT_UPDATE_GRACE_MILLIS;
    }

    private void refreshAfterMessageStoreChange() {
        if (!activityResumed || isDestroyed() || isFinishing()) {
            pendingMessageRefresh = true;
            return;
        }
        if (screenMode == ScreenMode.INBOX) {
            refreshInboxList(true);
        } else if (screenMode == ScreenMode.CHAT && activeConversation != null) {
            refreshActiveThreadAsync(isScrollNearBottom(activeScrollView, dp(120)));
        }
    }

    private boolean sameAddress(String first, String second) {
        return AddressUtil.sameConversationAddress(first, second);
    }

    private void styleSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(BLACK);
        window.setNavigationBarColor(BLACK);
    }

    private LinearLayout installScreenRoot() {
        LinearLayout screenRoot = new LinearLayout(this);
        screenRoot.setOrientation(LinearLayout.VERTICAL);
        screenRoot.setBackgroundColor(BLACK);
        setContentView(screenRoot);
        applySystemBarInsets(screenRoot);
        return screenRoot;
    }

    private void applySystemBarInsets(LinearLayout rootView) {
        rootView.setPadding(0, 0, 0, 0);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        registerMessageStoreObserver();
        reattachImageSendIfNeeded();
        SmsStore.clearContactCaches();
        if (root == null) {
            return;
        }
        applyPendingIncoming();
        if (screenMode == ScreenMode.INBOX) {
            refreshInboxList(true);
        } else if (screenMode == ScreenMode.CHAT) {
            markConversationReadAsync(activeConversation);
            boolean searchActive = !TextUtils.isEmpty(threadSearchQuery);
            boolean shouldOpenAtBottom = !searchActive || scrollThreadToBottomOnResume;
            scrollThreadToBottomOnResume = false;
            refreshActiveThreadAsync(shouldOpenAtBottom);
        }
    }

    static boolean supportsPhoneActions(String address) {
        return AddressUtil.hasSinglePhoneAddress(address);
    }

    private void afterKeyboardSettles(View anchor, Runnable action) {
        waitForKeyboardToClose(anchor, action, 10);
    }

    private void waitForKeyboardToClose(View anchor, Runnable action, int attemptsRemaining) {
        View screen = root == null ? anchor.getRootView() : root;
        Rect visibleFrame = new Rect();
        screen.getWindowVisibleDisplayFrame(visibleFrame);
        int coveredHeight = screen.getRootView().getHeight() - visibleFrame.bottom;
        if (coveredHeight <= dp(180) || attemptsRemaining <= 0) {
            action.run();
            return;
        }
        anchor.postDelayed(() -> waitForKeyboardToClose(anchor, action, attemptsRemaining - 1), 60);
    }

    private void restoreTransientState(Bundle state) {
        if (state == null) {
            return;
        }
        String cameraUri = state.getString(STATE_CAMERA_URI, "");
        pendingCameraImageUri = TextUtils.isEmpty(cameraUri) ? null : Uri.parse(cameraUri);
        pendingCameraAddress = state.getString(STATE_CAMERA_ADDRESS, "");
        pendingCameraForCompose = state.getBoolean(STATE_CAMERA_FOR_COMPOSE, false);
        pendingPickImageAddress = state.getString(STATE_PICK_IMAGE_ADDRESS, "");
        pendingPickImageForCompose = state.getBoolean(STATE_PICK_IMAGE_FOR_COMPOSE, false);
        pickingComposeContact = state.getBoolean(STATE_PICKING_COMPOSE_CONTACT, false);
        pendingSoundAddress = state.getString(STATE_PENDING_SOUND_ADDRESS, "");
        composeDraft = state.getString(STATE_COMPOSE_DRAFT, "");
        ArrayList<String> addresses = state.getStringArrayList(STATE_COMPOSE_RECIPIENT_ADDRESSES);
        ArrayList<String> names = state.getStringArrayList(STATE_COMPOSE_RECIPIENT_NAMES);
        composeRecipients.clear();
        if (addresses != null) {
            for (int index = 0; index < addresses.size(); index++) {
                String address = addresses.get(index);
                String name = names != null && index < names.size() ? names.get(index) : address;
                composeRecipients.add(new ComposeRecipient(name, address));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (pendingCameraImageUri != null) {
            outState.putString(STATE_CAMERA_URI, pendingCameraImageUri.toString());
        }
        outState.putString(STATE_CAMERA_ADDRESS, pendingCameraAddress);
        outState.putBoolean(STATE_CAMERA_FOR_COMPOSE, pendingCameraForCompose);
        outState.putString(STATE_PICK_IMAGE_ADDRESS, pendingPickImageAddress);
        outState.putBoolean(STATE_PICK_IMAGE_FOR_COMPOSE, pendingPickImageForCompose);
        outState.putBoolean(STATE_PICKING_COMPOSE_CONTACT, pickingComposeContact);
        outState.putString(STATE_PENDING_SOUND_ADDRESS, pendingSoundAddress);
        outState.putString(STATE_COMPOSE_DRAFT, composeDraft);
        ArrayList<String> addresses = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (ComposeRecipient recipient : composeRecipients) {
            addresses.add(recipient.address);
            names.add(recipient.name);
        }
        outState.putStringArrayList(STATE_COMPOSE_RECIPIENT_ADDRESSES, addresses);
        outState.putStringArrayList(STATE_COMPOSE_RECIPIENT_NAMES, names);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        MmsImageSendCoordinator.detach(IMAGE_SEND_JOB_KEY, imageSendListener);
        flushPendingDraft();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (screenMode == ScreenMode.CHAT && !TextUtils.isEmpty(threadSearchQuery)) {
            threadSearchQuery = "";
            refreshActiveThreadAsync(true);
            return;
        }
        if (screenMode == ScreenMode.CHAT) {
            showInbox();
            return;
        }
        if (screenMode == ScreenMode.PICTURE && pictureReturnScreen == ScreenMode.MEDIA && activeConversation != null) {
            showConversationMediaPage(activeConversation);
            return;
        }
        if ((screenMode == ScreenMode.PICTURE || screenMode == ScreenMode.CONVERSATION_INFO || screenMode == ScreenMode.MEDIA)
                && activeConversation != null) {
            showChat(activeConversation, activeThreadBlockedOnly);
            return;
        }
        if (screenMode == ScreenMode.COMPOSE || screenMode == ScreenMode.SPAM_RULES) {
            showInbox();
            return;
        }
        if (screenMode == ScreenMode.TRASH) {
            showInbox();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        activityResumed = false;
        dismissImageSendProgress();
        MmsImageSendCoordinator.detach(IMAGE_SEND_JOB_KEY, imageSendListener);
        activeImageSendUi = null;
        searchHandler.removeCallbacks(inboxSearchTask);
        messageStoreHandler.removeCallbacks(messageStoreRefreshTask);
        flushPendingDraft();
        inboxLoadGeneration++;
        threadLoadGeneration++;
        cancelTask(inboxLoadTask);
        cancelTask(threadLoadTask);
        inboxLoader.shutdownNow();
        threadLoader.shutdownNow();
        threadPrefetchLoader.shutdownNow();
        stateWriter.shutdownNow();
        try {
            unregisterReceiver(messageUpdateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (messageStoreObserverRegistered) {
            try {
                getContentResolver().unregisterContentObserver(messageStoreObserver);
            } catch (IllegalArgumentException ignored) {
            }
            messageStoreObserverRegistered = false;
        }
        super.onDestroy();
    }
}


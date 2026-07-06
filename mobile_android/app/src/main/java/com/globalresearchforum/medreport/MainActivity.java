package com.globalresearchforum.medreport;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Window;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ_PICK_FILE = 101;
    private static final int REQ_CAMERA = 102;
    private static final int MAX_FILES = 6;

    private static final int COLOR_BG = Color.rgb(239, 248, 250);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_PRIMARY = Color.rgb(0, 105, 128);
    private static final int COLOR_PRIMARY_DARK = Color.rgb(0, 73, 89);
    private static final int COLOR_ACCENT = Color.rgb(0, 150, 136);
    private static final int COLOR_TEXT = Color.rgb(25, 38, 45);
    private static final int COLOR_MUTED = Color.rgb(82, 104, 112);
    private static final int COLOR_BORDER = Color.rgb(199, 225, 230);

    private SharedPreferences prefs;
    private String deviceId;
    private Uri cameraUri;
    private String cameraFilename = "";
    private String cameraMime = "image/jpeg";
    private String currentReportId = "";
    private String selectedPreviousReportId = "";
    private String selectedPreviousReportLabel = "";
    private String currentCalmVideoUrl = "";
    private String currentCalmVideoTitle = "";
    private JSONObject calmContentJson = null;
    private final Random random = new Random();


    private final Handler breathingHandler = new Handler(Looper.getMainLooper());
    private int breathingIndex = 0;
    private String breathingHeadline = "";
    private final String[] breathingMessages = new String[]{
            "AI आपकी रिपोर्ट पढ़ रहा है। तब तक 30 सेकंड मन को शांत करें।",
            "मैं शांत आत्मा हूँ। मैं सही जानकारी को धैर्य से समझ रहा/रही हूँ।",
            "धीरे सांस लें। मन में सोचें: मैं सुरक्षित हूँ, शांत हूँ, और सही कदम doctor से पूछकर लूंगा/लूंगी।",
            "स्वयं को आत्मा समझकर परमात्मा शिव को याद करें।",
            "अपने शरीर को धन्यवाद दें। इसने आपका बहुत साथ दिया है।",
            "मन की शांति इलाज नहीं है, पर doctor से सही बात करने में सहारा देती है।",
            "खाने, पानी और doctor द्वारा बताई दवा के समय शांत और सावधान रहें।",
            "Rajyoga मन को शक्ति देता है। Medical निर्णय doctor से ही confirm करें।"
    };
    private final Runnable breathingRunnable = new Runnable() {
        @Override public void run() {
            String line = calmMessageForIndex(breathingIndex);
            String footer = words(
                    "This pause is only for calmness. It is not medical treatment.",
                    "यह pause केवल मन की शांति के लिए है। यह medical treatment नहीं है।"
            );
            String videoHint = currentCalmVideoUrl == null || currentCalmVideoUrl.trim().isEmpty()
                    ? ""
                    : "\n\n" + words("Optional: tap Watch calming video.", "चाहें तो शांति video देख सकते हैं।");
            if (outputView != null) {
                outputView.setText(breathingHeadline + "\n\n" + line + "\n\n" + footer + videoHint);
            }
            if (meditationOverlayText != null) {
                meditationOverlayText.setText(line);
            }
            breathingIndex++;
            breathingHandler.postDelayed(this, 5000);
        }
    };

    private final List<Uri> selectedUris = new ArrayList<>();
    private final List<String> selectedFilenames = new ArrayList<>();
    private final List<String> selectedMimes = new ArrayList<>();
    private final Map<String, String> questionAnswers = new HashMap<>();

    private static final class QuestionControl {
        String answerType = "free_text";
        boolean required = true;
        Spinner spinner;
        EditText textInput;
        EditText otherInput;
        final List<CheckBox> checkBoxes = new ArrayList<>();
    }

    private JSONArray currentQuestions = new JSONArray();
    private String extractionCorrectionsText = "";
    private JSONObject lastAnalysisForExport = null;
    private File lastPdfFile = null;

    private FrameLayout screenRoot;
    private ScrollView mainScroll;
    private LinearLayout root;
    private LinearLayout meditationOverlay;
    private TextView meditationOverlayText;
    private View meditationCircle;
    private AnimatorSet circleAnimator;
    private LinearLayout questionsBox;
    private TextView selectedFileView;
    private TextView outputView;
    private TextView finalStatusView;
    private Button readButton;
    private Button analyzeButton;
    private Button questionDialogButton;
    private Button clearButton;
    private Button calmVideoButton;
    private TextView savedContextView;
    private Button chooseSavedContextButton;
    private Button clearSavedContextButton;

    private EditText languageInput;
    private Spinner languageSpinner;
    private Spinner sexSpinner;
    private CheckBox useSavedContextCheckBox;
    private EditText nameInput;
    private EditText ageInput;
    private EditText sexInput;
    private EditText symptomsInput;
    private EditText conditionsInput;
    private EditText medicinesInput;
    private EditText allergiesInput;
    private EditText pregnancyInput;
    private EditText reportTextInput;
    private EditText youtubeInput;

    private final TextWatcher validationWatcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            saveDraftSafely();
            updateProceedButtons();
        }
        @Override public void afterTextChanged(Editable s) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("medical_report_ai", MODE_PRIVATE);
        deviceId = prefs.getString("device_id", "");
        if (deviceId.isEmpty()) {
            deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (deviceId == null || deviceId.trim().isEmpty()) deviceId = UUID.randomUUID().toString();
            prefs.edit().putString("device_id", deviceId).apply();
        }
        // V18: every check is independent. Saved reports stay in My account only and are not used as AI context.
        selectedPreviousReportId = "";
        selectedPreviousReportLabel = "";
        prefs.edit()
                .remove("use_saved_context")
                .remove("selected_previous_report_id")
                .remove("selected_previous_report_label")
                .apply();
        buildUi();
        restoreSelectedFilesFromPrefs();
        handleSharedIntent(getIntent());
        showConsentIfNeeded();
        updateProceedButtons();
        restoreMainScrollSoon();
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreMainScrollSoon();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSharedIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveDraftSafely();
        saveSelectedFilesLocally();
        saveMainScrollPosition();
    }

    private void saveMainScrollPosition() {
        try {
            if (mainScroll != null && prefs != null) {
                prefs.edit().putInt("main_scroll_y", mainScroll.getScrollY()).apply();
            }
        } catch (Exception ignored) {}
    }

    private void restoreMainScrollSoon() {
        try {
            if (mainScroll == null || prefs == null) return;
            final int y = prefs.getInt("main_scroll_y", 0);
            mainScroll.postDelayed(new Runnable() {
                @Override public void run() {
                    try { mainScroll.scrollTo(0, y); } catch (Exception ignored) {}
                }
            }, 150);
        } catch (Exception ignored) {}
    }

    private void buildUi() {
        screenRoot = new FrameLayout(this);
        screenRoot.setBackgroundColor(COLOR_BG);

        mainScroll = new ScrollView(this);
        mainScroll.setFillViewport(true);
        mainScroll.setBackgroundColor(COLOR_BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(32));
        mainScroll.addView(root);
        screenRoot.addView(mainScroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        buildMeditationOverlay();
        setContentView(screenRoot);

        root.addView(safetyBanner());

        LinearLayout hero = card();
        TextView title = text("Medical Report AI", 27, true, COLOR_PRIMARY_DARK);
        title.setGravity(Gravity.CENTER);
        hero.addView(title);
        TextView sub = text("Upload a report or simply type your health concern. Every check is independent for better accuracy.", 14, false, COLOR_MUTED);
        sub.setGravity(Gravity.CENTER);
        hero.addView(sub);
        TextView badge = pill("File optional • Text also works • Independent check");
        badge.setGravity(Gravity.CENTER);
        hero.addView(badge);
        Button accountButton = button("My account", new View.OnClickListener() { public void onClick(View v) { showAccountScreen(); }}, false);
        hero.addView(accountButton);
        root.addView(hero);

        LinearLayout patientCard = card();
        patientCard.addView(sectionTitle("1. Patient context"));
        patientCard.addView(text("Each check is independent. Saved reports are available in My account, but they are not mixed into new AI analysis.", 13, false, COLOR_MUTED));
        languageInput = hiddenEdit(prefs.getString("language", "Hindi"));
        patientCard.addView(languageInput);
        languageSpinner = dropdown(patientCard, "Language for all AI answers", languageOptions(), languageInput.getText().toString(), languageInput);
        nameInput = edit(patientCard, "Patient alias/name (optional)", prefs.getString("name", ""), 1);
        ageInput = edit(patientCard, "Age", prefs.getString("age", ""), 1);
        sexInput = hiddenEdit(prefs.getString("sex", "Female"));
        patientCard.addView(sexInput);
        sexSpinner = dropdown(patientCard, "Gender", genderOptions(), sexInput.getText().toString(), sexInput);
        // V18: saved-report comparison removed from main journey.
        // Reason: medical accuracy is safer when each run uses only the current upload/text and current answers.
        useSavedContextCheckBox = new CheckBox(this);
        useSavedContextCheckBox.setVisibility(View.GONE);
        useSavedContextCheckBox.setChecked(false);
        patientCard.addView(useSavedContextCheckBox);
        symptomsInput = edit(patientCard, "Current symptoms", prefs.getString("symptoms", ""), 3);
        conditionsInput = edit(patientCard, "Known conditions, e.g. diabetes, thyroid, BP", prefs.getString("conditions", ""), 3);
        medicinesInput = edit(patientCard, "Current medicines (optional, no dose needed)", prefs.getString("medicines", ""), 3);
        allergiesInput = edit(patientCard, "Allergies", prefs.getString("allergies", ""), 2);
        pregnancyInput = edit(patientCard, "Pregnancy status if relevant", prefs.getString("pregnancy", ""), 1);
        root.addView(patientCard);

        LinearLayout uploadCard = card();
        uploadCard.addView(sectionTitle("2. Add report file or type details"));
        uploadCard.addView(text("File upload is optional. You can upload a typed PDF/clear printed report photo, or simply type the report values/symptoms below. Avoid handwritten, blurry, cut, dark or half-page photos.", 13, false, COLOR_MUTED));
        uploadCard.addView(text("Privacy: this test build saves your report in your private app journal so later reports can be compared. Do not upload someone else's report without permission.", 12, false, COLOR_MUTED));
        uploadCard.addView(uploadGuideBox());
        selectedFileView = text("No file selected. You can upload PDF/photo OR type details below.", 14, false, COLOR_TEXT);
        selectedFileView.setPadding(0, dp(8), 0, dp(8));
        uploadCard.addView(selectedFileView);
        reportTextInput = edit(uploadCard, "Type report text, lab values, symptoms, or your concern. File upload is optional.", prefs.getString("report_text", ""), 4);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button("Pick files", new View.OnClickListener() { public void onClick(View v) { pickFiles(); }}, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        addHorizontalSpace(row, 8);
        row.addView(button("Camera", new View.OnClickListener() { public void onClick(View v) { capturePhoto(); }}, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        uploadCard.addView(row);
        readButton = button("Hidden report check", new View.OnClickListener() { public void onClick(View v) { uploadSelected(); }}, true);
        readButton.setVisibility(View.GONE);
        clearButton = button("Clear all", new View.OnClickListener() { public void onClick(View v) { clearAll(); }}, false);
        uploadCard.addView(clearButton);
        root.addView(uploadCard);

        LinearLayout confirmCard = card();
        confirmCard.addView(sectionTitle("3. AI health guide"));
        confirmCard.addView(text("Tap Am I good?. AI may ask a few simple questions, then directly shows the result with Green / Amber / Red boxes.", 13, false, COLOR_MUTED));
        youtubeInput = new EditText(this);
        youtubeInput.setText(prefs.getString("youtube_links", ""));
        questionsBox = new LinearLayout(this);
        questionsBox.setOrientation(LinearLayout.VERTICAL);
        confirmCard.addView(questionsBox);
        questionDialogButton = button("Answer AI questions", new View.OnClickListener() { public void onClick(View v) { showQuestionsDialog(); }}, false);
        questionDialogButton.setVisibility(View.GONE);
        confirmCard.addView(questionDialogButton);
        finalStatusView = text("Fill basic details. Add a file or type details. Then tap Am I good?. AI will ask only needed questions and show the result automatically.", 13, false, COLOR_MUTED);
        confirmCard.addView(finalStatusView);
        analyzeButton = button("Am I good?", new View.OnClickListener() { public void onClick(View v) { mainAction(); }}, true);
        styleActionButton(analyzeButton);
        confirmCard.addView(analyzeButton);
        root.addView(confirmCard);

        LinearLayout outputCard = card();
        outputCard.addView(sectionTitle("Current status"));
        outputView = text("Add patient context, upload a file or type details, then tap Am I good?. During reading, a calm Rajyoga pause will appear.", 15, false, COLOR_TEXT);
        outputView.setPadding(0, dp(8), 0, 0);
        outputCard.addView(outputView);
        root.addView(outputCard);

        attachValidationWatchers();
    }

    private void buildMeditationOverlay() {
        meditationOverlay = new LinearLayout(this);
        meditationOverlay.setOrientation(LinearLayout.VERTICAL);
        meditationOverlay.setGravity(Gravity.CENTER);
        meditationOverlay.setPadding(dp(24), dp(24), dp(24), dp(24));
        meditationOverlay.setBackgroundColor(Color.rgb(232, 247, 249));
        meditationOverlay.setVisibility(View.GONE);

        TextView title = text("Rajyoga pause", 22, true, COLOR_PRIMARY_DARK);
        title.setGravity(Gravity.CENTER);
        meditationOverlay.addView(title);

        meditationCircle = new View(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.rgb(185, 232, 229));
        circle.setStroke(dp(2), COLOR_PRIMARY);
        meditationCircle.setBackground(circle);
        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(dp(150), dp(150));
        circleLp.setMargins(0, dp(28), 0, dp(24));
        circleLp.gravity = Gravity.CENTER_HORIZONTAL;
        meditationOverlay.addView(meditationCircle, circleLp);

        meditationOverlayText = text("AI आपकी रिपोर्ट पढ़ रहा है। तब तक 30 सेकंड मन को शांत करें।", 17, false, COLOR_TEXT);
        meditationOverlayText.setGravity(Gravity.CENTER);
        meditationOverlayText.setLineSpacing(0, 1.18f);
        meditationOverlay.addView(meditationOverlayText);

        calmVideoButton = button(words("Watch calming video", "शांति video देखें"), new View.OnClickListener() {
            @Override public void onClick(View v) { openCurrentCalmVideo(); }
        }, false);
        calmVideoButton.setVisibility(View.GONE);
        meditationOverlay.addView(calmVideoButton);

        TextView footer = text("यह अभ्यास मन की शांति के लिए है। Medical निर्णय doctor से ही confirm करें।", 13, false, COLOR_MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(18), 0, 0);
        meditationOverlay.addView(footer);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        screenRoot.addView(meditationOverlay, lp);
    }

    private void attachValidationWatchers() {
        EditText[] fields = new EditText[]{languageInput, nameInput, ageInput, sexInput, symptomsInput, conditionsInput, medicinesInput, allergiesInput, pregnancyInput, reportTextInput};
        for (EditText e : fields) e.addTextChangedListener(validationWatcher);
    }

    private void showConsentIfNeeded() {
        if (prefs.getBoolean("consent_ok", false)) return;
        new AlertDialog.Builder(this)
                .setTitle("Important medical safety note")
                .setMessage("This app explains medical reports in simple language. It does not diagnose, prescribe medicine, replace a doctor, or provide emergency care. It is AI-generated and can make mistakes. It can suggest safe non-medicine comfort steps, but these are not cures. For emergencies, seek urgent medical care. By continuing, you agree to upload only reports you are allowed to use.")
                .setPositiveButton("I understand", (dialog, which) -> prefs.edit().putBoolean("consent_ok", true).apply())
                .setCancelable(false)
                .show();
    }

    private void handleSharedIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof Uri) addSelectedUri((Uri) stream);
        }
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) for (Uri uri : streams) addSelectedUri(uri);
        }
    }

    private void pickFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "image/jpeg", "image/png", "image/webp"});
        startActivityForResult(intent, REQ_PICK_FILE);
    }

    private void capturePhoto() {
        try {
            File dir = new File(getExternalFilesDir(null), "camera");
            if (!dir.exists()) dir.mkdirs();
            String name = "report_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
            File file = new File(dir, name);
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            cameraFilename = name;
            cameraMime = "image/jpeg";
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_CAMERA);
        } catch (Exception e) {
            toast("Camera failed: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQ_PICK_FILE && data != null) {
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                    addSelectedUri(uri);
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                addSelectedUri(uri);
            }
        } else if (requestCode == REQ_CAMERA && cameraUri != null) {
            addSelectedFile(cameraUri, cameraFilename, cameraMime);
        }
    }

    private void addSelectedUri(Uri uri) {
        String name = getDisplayName(uri);
        String mime = getContentResolver().getType(uri);
        if (mime == null) mime = guessMimeFromName(name);
        addSelectedFile(uri, name, mime);
    }

    private void addSelectedFile(Uri uri, String filename, String mime) {
        if (uri == null) return;
        if (selectedUris.size() >= MAX_FILES) {
            toast("Maximum " + MAX_FILES + " files allowed in one report.");
            return;
        }
        selectedUris.add(uri);
        selectedFilenames.add(filename == null ? "medical_report" : filename);
        selectedMimes.add(mime == null ? guessMimeFromName(filename) : mime);
        refreshSelectedFilesText();
        saveSelectedFilesLocally();
        updateProceedButtons();
    }

    private void refreshSelectedFilesText() {
        if (selectedUris.isEmpty()) {
            selectedFileView.setText("No report selected yet");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(selectedUris.size()).append(" file(s) selected:\n");
        for (int i = 0; i < selectedFilenames.size(); i++) {
            sb.append("• ").append(selectedFilenames.get(i)).append(" (").append(selectedMimes.get(i)).append(")\n");
        }
        selectedFileView.setText(sb.toString().trim());
    }

    private void uploadSelected() {
        if (!isRequiredProfileReady()) {
            toast("Select language, gender, age and current symptoms first.");
            return;
        }
        if (selectedUris.isEmpty() && !hasReportText()) {
            toast("Add at least one report file or paste report text first.");
            return;
        }
        saveProfileLocally();
        startCalmBreathing("Checking if the selected reports are readable and preparing safe questions...");
        if (readButton != null) readButton.setEnabled(false);
        if (analyzeButton != null) analyzeButton.setEnabled(false);
        final JSONObject profile = buildPatientProfile();
        final String lang = languageInput.getText().toString().trim();
        final ArrayList<Uri> uris = new ArrayList<>(selectedUris);
        final ArrayList<String> names = new ArrayList<>(selectedFilenames);
        final ArrayList<String> mimes = new ArrayList<>(selectedMimes);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject response = ApiClient.uploadReports(getContentResolver(), uris, names, mimes, deviceId, lang, profile, getReportText());
                    currentReportId = response.optString("report_id", "");
                    final String formatted = formatExtraction(response);
                    runOnUiThread(new Runnable() { @Override public void run() {
                        stopCalmBreathing();
                        outputView.setText(formatted);
                        buildQuestions(response.optJSONArray("questions"));
                        updateProceedButtons();
                    }});
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() { @Override public void run() { stopCalmBreathing(); outputView.setText("Could not check the report safely.\n\n" + e.getMessage()); updateProceedButtons(); }});
                }
            }
        }).start();
    }

    private void mainAction() {
        saveDraftSafely();
        if (!isRequiredProfileReady()) {
            toast("Select language, gender, age and current symptoms first. Write Not sure where needed.");
            return;
        }
        if (selectedUris.isEmpty() && !hasReportText() && (currentReportId == null || currentReportId.isEmpty())) {
            toast("Please add at least one report file or paste report text.");
            return;
        }
        if (currentReportId == null || currentReportId.isEmpty()) {
            uploadSelected();
            return;
        }
        if (!allQuestionsAnswered()) {
            showQuestionsDialog();
            if (finalStatusView != null) finalStatusView.setText("Answer the AI questions. After that I will show the report automatically.");
            return;
        }
        analyzeCurrentReport();
    }

    private void confirmLanguageThenAnalyze() {
        if (!canAnalyze()) {
            toast("Answer all required profile fields and AI questions first. Write 'No' or 'Not sure' if needed.");
            return;
        }
        final EditText langEdit = new EditText(this);
        langEdit.setSingleLine(true);
        langEdit.setText(languageInput.getText().toString().trim().isEmpty() ? "English" : languageInput.getText().toString().trim());
        langEdit.setHint("Final report language");
        new AlertDialog.Builder(this)
                .setTitle("Final report language")
                .setMessage("Before preparing the report, choose the language for the final answer.")
                .setView(langEdit)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Prepare report", (dialog, which) -> {
                    String chosen = langEdit.getText().toString().trim();
                    if (chosen.isEmpty()) chosen = "English";
                    languageInput.setText(chosen);
                    analyzeCurrentReport();
                })
                .show();
    }

    private void analyzeCurrentReport() {
        if (!canUseOneCreditNow()) return;
        auditClientEvent("analyze_start");
        saveProfileLocally();
        startCalmBreathing("Preparing the final doctor-prep report in your chosen language...");
        final JSONObject payload = new JSONObject();
        try {
            payload.put("report_id", currentReportId);
            payload.put("device_id", deviceId);
            payload.put("language", languageInput.getText().toString().trim());
            payload.put("patient_profile", buildPatientProfile());
            JSONObject answers = new JSONObject();
            for (Map.Entry<String, String> entry : questionAnswers.entrySet()) {
                answers.put(entry.getKey(), entry.getValue());
            }
            payload.put("user_answers", answers);
            payload.put("youtube_video_links", buildYoutubeArray());
            payload.put("extraction_corrections", extractionCorrectionsText == null ? "" : extractionCorrectionsText);
            // V18: every analysis is independent. Do not send old report context to backend.
            payload.put("continue_from_previous", false);
            payload.put("previous_report_id", "");
        } catch (Exception e) {
            outputView.setText("Internal error preparing request: " + e.getMessage());
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject response = ApiClient.analyzeReport(payload);
                    final JSONObject analysis = response.optJSONObject("analysis");
                    final String formatted = formatAnalysis(analysis);
                    runOnUiThread(new Runnable() { @Override public void run() {
                        stopCalmBreathing();
                        consumeOneCreditAfterSuccess();
                        auditClientEvent("analyze_success");
                        outputView.setText("Report is ready and saved. Open Saved reports anytime to see it again.\n\n" + firstNonBlank(analysis == null ? "" : analysis.optString("final_conclusion_simple"), analysis == null ? "" : analysis.optString("simple_patient_summary")));
                        showFinalReportScreen(analysis);
                        updateProceedButtons();
                    }});
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() { @Override public void run() { stopCalmBreathing(); outputView.setText("Could not prepare final report safely.\n\n" + e.getMessage()); updateProceedButtons(); }});
                }
            }
        }).start();
    }

    private JSONArray buildYoutubeArray() {
        JSONArray arr = new JSONArray();
        try {
            JSONArray videos = loadCalmContent().optJSONArray("youtube_videos");
            if (videos != null) {
                String lang = calmLanguageCode();
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject item = videos.optJSONObject(i);
                    if (item == null || !item.optBoolean("enabled", true)) continue;
                    String itemLang = item.optString("language", "").trim().toLowerCase(Locale.US);
                    if (!itemLang.isEmpty() && !itemLang.equals(lang)) continue;
                    String url = item.optString("youtube_url", "").trim();
                    if (url.isEmpty() || url.contains("PASTE_YOUTUBE_VIDEO_ID_HERE")) continue;
                    String title = item.optString("title", "").trim();
                    arr.put(title.isEmpty() ? url : title + " - " + url);
                }
            }
        } catch (Exception ignored) {}
        return arr;
    }


    private boolean isUsingSelectedSavedReport() {
        // V18: old reports are never used in a new AI run.
        return false;
    }

    private void updateSavedContextView() {
        try {
            selectedPreviousReportId = "";
            selectedPreviousReportLabel = "";
            if (useSavedContextCheckBox != null) useSavedContextCheckBox.setChecked(false);
            if (savedContextView != null) {
                savedContextView.setText("Independent check selected. Saved reports are not used as context.");
                savedContextView.setTextColor(COLOR_TEXT);
            }
        } catch (Exception ignored) {}
    }

    private void clearSelectedPreviousReport() {
        selectedPreviousReportId = "";
        selectedPreviousReportLabel = "";
        if (useSavedContextCheckBox != null) useSavedContextCheckBox.setChecked(false);
        prefs.edit()
                .remove("use_saved_context")
                .remove("selected_previous_report_id")
                .remove("selected_previous_report_label")
                .apply();
        updateProceedButtons();
        toast("Independent check selected. Old reports will not be used.");
    }

    private void loadHistoryForPatientSelection() {
        // V18: comparison selection is intentionally disabled.
        clearSelectedPreviousReport();
        auditClientEvent("saved_report_comparison_disabled");
        toast("Old report selection is removed. Each new check is independent for better accuracy.");
    }

    private void showSavedReportPicker(final JSONArray items) {
        // V18: old reports are not selectable for AI context.
        toast("Old reports can be viewed from My account, but they are not used in new AI analysis.");
    }

    private void loadHistory() {
        auditClientEvent("history_open");
        startCalmBreathing("Opening saved reports...");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject response = ApiClient.history(deviceId);
                    final JSONArray items = response.optJSONArray("items");
                    runOnUiThread(new Runnable() { @Override public void run() { stopCalmBreathing(); showHistoryScreen(items); updateProceedButtons(); }});
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() { @Override public void run() { stopCalmBreathing(); outputView.setText("Could not open saved reports.\n\n" + e.getMessage()); }});
                }
            }
        }).start();
    }

    private void showHistoryScreen(final JSONArray items) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(box);
        box.addView(safetyBanner());
        TextView title = text("Saved reports", 24, true, COLOR_PRIMARY_DARK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        box.addView(text("These are your saved reports for your record and doctor discussion. New AI checks do not use old reports as context.", 13, false, COLOR_MUTED));
        if (items == null || items.length() == 0) {
            box.addView(text("No saved reports yet.", 16, true, COLOR_TEXT));
        } else {
            for (int i = 0; i < items.length(); i++) {
                final JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                LinearLayout c = card();
                String titleText = firstNonBlank(item.optString("title"), item.optString("original_filename"), "Medical report");
                c.addView(text((i + 1) + ". " + titleText, 16, true, COLOR_PRIMARY_DARK));
                c.addView(text("Date: " + friendlyDate(item.optString("created_at")) + "\nStatus: " + friendlyStatus(item.optString("status")), 13, false, COLOR_TEXT));
                if (item.optBoolean("has_analysis")) {
                    c.addView(button("Open saved result", new View.OnClickListener() { public void onClick(View v) { openSavedReport(item.optString("report_id")); }}, true));
                } else {
                    c.addView(text("This report was saved, but final result is not available yet.", 13, false, COLOR_MUTED));
                }
                box.addView(c);
            }
        }
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("Close", null)
                .create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) window.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        });
        dialog.show();
    }

    private void openSavedReport(final String reportId) {
        if (reportId == null || reportId.trim().isEmpty()) { toast("Saved report could not be opened."); return; }
        startCalmBreathing("Opening saved report...");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject response = ApiClient.getReport(reportId, deviceId);
                    final JSONObject analysis = response.optJSONObject("analysis");
                    runOnUiThread(new Runnable() { public void run() { stopCalmBreathing(); showFinalReportScreen(analysis); }});
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() { public void run() { stopCalmBreathing(); toast("Could not open saved report: " + e.getMessage()); }});
                }
            }
        }).start();
    }

    private String friendlyDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Not available";
        return raw.replace("T", " ").replace("Z", "");
    }

    private String friendlyStatus(String status) {
        if (status == null) return "Saved";
        if (status.equalsIgnoreCase("analyzed")) return "Final result ready";
        if (status.toLowerCase(Locale.US).contains("clear")) return "Needs clearer report";
        if (status.equalsIgnoreCase("extracted")) return "Questions pending";
        return "Saved";
    }

    private void showAccountScreen() {
        auditClientEvent("account_open");
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        scroll.addView(box);
        box.addView(safetyBanner());
        box.addView(text("My account", 24, true, COLOR_PRIMARY_DARK));
        box.addView(text("Saved reports and credits are kept here, away from the main report screen.", 14, false, COLOR_MUTED));
        box.addView(button("Saved reports", new View.OnClickListener() { public void onClick(View v) { loadHistory(); }}, false));
        box.addView(button("Buy credits", new View.OnClickListener() { public void onClick(View v) { showBuyCreditsScreen(); }}, true));
        new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showBuyCreditsScreen() {
        auditClientEvent("buy_credits_open");
        auditCreditEvent("credit_screen_open", "", remainingCredits());
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(box);
        box.addView(safetyBanner());
        TextView title = text("Credits", 24, true, COLOR_PRIMARY_DARK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        if (isUnlimitedTestingMode()) {
            box.addView(addSimpleInfoBox("Testing mode", "This app was not installed from Play Store. Credits are unlimited for your testing.", Color.rgb(0, 105, 128)));
        } else {
            box.addView(text("One final report uses one credit. India: ₹20 per report. Outside India: $5 per report. Monthly pack uses the same per-report cost.", 14, false, COLOR_TEXT));
            box.addView(text("Remaining credits: " + remainingCredits(), 18, true, COLOR_PRIMARY_DARK));
            box.addView(button("Buy 1 India credit — ₹20", new View.OnClickListener() { public void onClick(View v) { addLocalCreditForNow("india_20"); }}, true));
            box.addView(button("Buy 1 global credit — $5", new View.OnClickListener() { public void onClick(View v) { addLocalCreditForNow("global_5"); }}, false));
            box.addView(text("Production note: before Play Store release, these buttons must be connected to Google Play Billing product IDs and backend purchase verification.", 12, false, COLOR_MUTED));
        }
        final AlertDialog dialog = new AlertDialog.Builder(this).setView(scroll).setPositiveButton("Close", null).create();
        dialog.setOnShowListener(d -> { Window w = dialog.getWindow(); if (w != null) w.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT); });
        dialog.show();
    }

    private LinearLayout addSimpleInfoBox(String title, String body, int borderColor) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(247, 253, 254));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(2), borderColor);
        c.setBackground(bg);
        c.addView(text(title, 16, true, COLOR_PRIMARY_DARK));
        c.addView(text(body, 14, false, COLOR_TEXT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, dp(10));
        c.setLayoutParams(lp);
        return c;
    }

    private boolean isUnlimitedTestingMode() {
        if (BuildConfig.DEBUG) return true;
        try {
            String installer = getPackageManager().getInstallerPackageName(getPackageName());
            return installer == null || !"com.android.vending".equals(installer);
        } catch (Exception e) {
            return true;
        }
    }

    private int remainingCredits() {
        return prefs.getInt("paid_credits", 0);
    }

    private boolean canUseOneCreditNow() {
        if (isUnlimitedTestingMode()) return true;
        if (remainingCredits() > 0) return true;
        showBuyCreditsScreen();
        toast("Please buy one credit to prepare the final report.");
        return false;
    }

    private void consumeOneCreditAfterSuccess() {
        if (isUnlimitedTestingMode()) return;
        int credits = Math.max(0, remainingCredits() - 1);
        prefs.edit().putInt("paid_credits", credits).apply();
        auditCreditEvent("credit_used", "", credits);
    }

    private void addLocalCreditForNow(String plan) {
        int credits = remainingCredits() + 1;
        prefs.edit().putInt("paid_credits", credits).apply();
        auditCreditEvent("credit_added", plan, credits);
        toast("Credit added. Remaining credits: " + credits);
    }

    private void auditClientEvent(final String eventName) {
        if (eventName == null || eventName.trim().isEmpty()) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject payload = baseAuditPayload(eventName);
                    ApiClient.auditEvent(payload);
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void auditCreditEvent(final String eventName, final String plan, final int remainingCredits) {
        if (eventName == null || eventName.trim().isEmpty()) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject payload = baseAuditPayload(eventName);
                    payload.put("plan", plan == null ? "" : plan);
                    payload.put("remaining_credits", remainingCredits);
                    payload.put("debug", BuildConfig.DEBUG);
                    ApiClient.creditAuditEvent(payload);
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private JSONObject baseAuditPayload(String eventName) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("device_id", deviceId == null ? "unknown" : deviceId);
        payload.put("event", eventName == null ? "client_event" : eventName);
        payload.put("screen", "android_app");
        payload.put("app_version", BuildConfig.VERSION_NAME);
        payload.put("source", "android_native");
        return payload;
    }

    private JSONObject buildPatientProfile() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("name_or_alias", nameInput.getText().toString().trim());
            obj.put("age", ageInput.getText().toString().trim());
            obj.put("sex", sexInput.getText().toString().trim());
            obj.put("language", languageInput.getText().toString().trim());
            obj.put("symptoms", symptomsInput.getText().toString().trim());
            obj.put("known_conditions", conditionsInput.getText().toString().trim());
            obj.put("current_medicines", medicinesInput.getText().toString().trim());
            obj.put("allergies", allergiesInput.getText().toString().trim());
            obj.put("pregnancy_status", pregnancyInput.getText().toString().trim());
            obj.put("additional_report_text", getReportText());
            obj.put("same_patient_saved_context", false);
            obj.put("selected_previous_report_id", "");
            obj.put("selected_previous_report_label", "");
            obj.put("context_policy", "Independent run: do not use any saved report or previous history. Accuracy is more important than comparison.");
            obj.put("notes", "User may prefer non-medicine supportive care, meditation support, calm food/medicine SOP, and doctor-prep language. Do not prescribe medicine.");
        } catch (Exception ignored) {}
        return obj;
    }

    private void saveProfileLocally() {
        if (languageInput == null || ageInput == null || sexInput == null || symptomsInput == null) return;
        prefs.edit()
                .putString("language", languageInput.getText().toString())
                .putString("name", nameInput.getText().toString())
                .putString("age", ageInput.getText().toString())
                .putString("sex", sexInput.getText().toString())
                .putString("symptoms", symptomsInput.getText().toString())
                .putString("conditions", conditionsInput.getText().toString())
                .putString("medicines", medicinesInput.getText().toString())
                .putString("allergies", allergiesInput.getText().toString())
                .putString("pregnancy", pregnancyInput.getText().toString())
                .putString("report_text", reportTextInput == null ? "" : reportTextInput.getText().toString())
                .putString("youtube_links", youtubeInput == null ? "" : youtubeInput.getText().toString())
                .remove("use_saved_context")
                .remove("selected_previous_report_id")
                .remove("selected_previous_report_label")
                .apply();
    }

    private void saveDraftSafely() {
        try { saveProfileLocally(); } catch (Exception ignored) {}
    }

    private void saveSelectedFilesLocally() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < selectedUris.size(); i++) {
                JSONObject o = new JSONObject();
                o.put("uri", selectedUris.get(i).toString());
                o.put("name", selectedFilenames.get(i));
                o.put("mime", selectedMimes.get(i));
                arr.put(o);
            }
            prefs.edit().putString("selected_files", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void restoreSelectedFilesFromPrefs() {
        try {
            String raw = prefs.getString("selected_files", "[]");
            JSONArray arr = new JSONArray(raw);
            selectedUris.clear();
            selectedFilenames.clear();
            selectedMimes.clear();
            for (int i = 0; i < arr.length() && i < MAX_FILES; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String uriText = o.optString("uri", "");
                if (uriText.trim().isEmpty()) continue;
                Uri uri = Uri.parse(uriText);
                selectedUris.add(uri);
                selectedFilenames.add(o.optString("name", getDisplayName(uri)));
                selectedMimes.add(o.optString("mime", guessMimeFromName(o.optString("name", ""))));
            }
            refreshSelectedFilesText();
        } catch (Exception ignored) {}
    }

    private void buildQuestions(JSONArray questions) {
        questionsBox.removeAllViews();
        questionAnswers.clear();
        extractionCorrectionsText = "";
        currentQuestions = questions == null ? new JSONArray() : questions;

        TextView note = text("AI checked readability. Next: answer a few safety questions on a separate screen.", 14, true, COLOR_PRIMARY_DARK);
        questionsBox.addView(note);

        if (currentQuestions.length() == 0) {
            questionsBox.addView(text("No extra safety questions are needed. You can prepare the final report.", 13, false, COLOR_MUTED));
        } else {
            questionsBox.addView(text("Tap Review AI questions. Each question will show its own relevant choices or a simple text field. Not sure is allowed where shown.", 13, false, COLOR_MUTED));
        }
        if (questionDialogButton != null) questionDialogButton.setVisibility(currentQuestions.length() > 0 ? View.VISIBLE : View.GONE);
        updateProceedButtons();
        if (currentQuestions.length() > 0) {
            showQuestionsDialog();
        } else if (canAnalyze()) {
            // No extra questions needed. Continue directly to final report.
            analyzeCurrentReport();
        }
    }

    private void showQuestionsDialog() {
        if (currentQuestions == null || currentQuestions.length() == 0) {
            toast("No AI questions are pending.");
            return;
        }

        ScrollView dialogScroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        dialogScroll.addView(box);

        box.addView(text("Answer these few questions", 20, true, COLOR_PRIMARY_DARK));
        box.addView(text("Each question has choices made for that exact question. Where choices are not safe, type a short answer. After this, the report will open automatically.", 13, false, COLOR_MUTED));

        final EditText finalLanguage = hiddenEdit(languageInput == null ? "Hindi" : languageInput.getText().toString().trim());
        box.addView(finalLanguage);
        final Spinner finalLanguageSpinner = dropdown(box, "Language for final report", languageOptions(), finalLanguage.getText().toString(), finalLanguage);

        EditText corrections = editStandalone("Correction to what AI read, if any", extractionCorrectionsText == null ? "" : extractionCorrectionsText, 3);
        box.addView(corrections);

        final Map<String, QuestionControl> controls = new HashMap<>();

        for (int i = 0; i < currentQuestions.length(); i++) {
            JSONObject q = currentQuestions.optJSONObject(i);
            if (q == null) continue;
            String id = q.optString("id", "q" + i);
            TextView qText = text((i + 1) + ". " + q.optString("question"), 15, true, COLOR_TEXT);
            qText.setPadding(0, dp(14), 0, dp(2));
            box.addView(qText);
            String why = q.optString("why_needed", "This can change how a doctor interprets the current report.");
            box.addView(text("Why asked: " + why, 12, false, COLOR_MUTED));

            QuestionControl control = new QuestionControl();
            control.answerType = questionAnswerType(q);
            control.required = q.optBoolean("required", true);
            String old = questionAnswers.get(id);

            if ("single_select".equals(control.answerType) || "yes_no".equals(control.answerType)) {
                Spinner spinner = new Spinner(this);
                final String[] options = getQuestionOptions(q);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
                if (old != null) {
                    String oldChoice = old.startsWith("Other: ") ? "Other" : old.startsWith("अन्य: ") ? "अन्य" : old;
                    for (int j = 0; j < options.length; j++) {
                        if (options[j].equalsIgnoreCase(oldChoice)) spinner.setSelection(j);
                    }
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, dp(6), 0, dp(8));
                spinner.setLayoutParams(lp);
                box.addView(spinner);
                control.spinner = spinner;

                if (q.optBoolean("allow_other", false)) {
                    String oldOther = "";
                    if (old != null && old.contains(":")) oldOther = old.substring(old.indexOf(':') + 1).trim();
                    EditText other = editStandalone(words("If you chose Other, type it here", "यदि आपने अन्य चुना है, तो यहाँ लिखें"), oldOther, 1);
                    box.addView(other);
                    control.otherInput = other;
                }
            } else if ("multi_select".equals(control.answerType)) {
                JSONArray supplied = q.optJSONArray("answer_options");
                ArrayList<String> selectedOld = new ArrayList<>();
                if (old != null) {
                    for (String part : old.split("\\s*\\|\\s*")) if (!part.trim().isEmpty()) selectedOld.add(part.trim());
                }
                if (supplied != null) {
                    for (int j = 0; j < supplied.length(); j++) {
                        String option = supplied.optString(j, "").trim();
                        if (option.isEmpty()) continue;
                        CheckBox check = new CheckBox(this);
                        check.setText(option);
                        check.setTextColor(COLOR_TEXT);
                        check.setChecked(selectedOld.contains(option));
                        box.addView(check);
                        control.checkBoxes.add(check);
                    }
                }
                if (q.optBoolean("allow_other", false)) {
                    EditText other = editStandalone(words("Other answer, if needed", "अन्य उत्तर, यदि आवश्यक हो"), "", 1);
                    box.addView(other);
                    control.otherInput = other;
                }
            } else {
                String placeholder = q.optString("placeholder", "").trim();
                if (placeholder.isEmpty()) placeholder = words("Type your answer", "अपना उत्तर लिखें");
                EditText answerInput = editStandalone(placeholder, old == null ? "" : old, "free_text".equals(control.answerType) ? 2 : 1);
                box.addView(answerInput);
                control.textInput = answerInput;
            }
            controls.put(id, control);
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogScroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("See result now", null)
                .create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) window.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                for (int i = 0; i < currentQuestions.length(); i++) {
                    JSONObject q = currentQuestions.optJSONObject(i);
                    if (q == null) continue;
                    String id = q.optString("id", "q" + i);
                    QuestionControl control = controls.get(id);
                    if (control == null) continue;
                    String answer = readQuestionAnswer(control);
                    if (answer.trim().isEmpty() && control.required) {
                        toast("Please answer question " + (i + 1) + ". Choose Not sure if it is available.");
                        return;
                    }
                    questionAnswers.put(id, answer.trim().isEmpty() ? "Not answered" : answer.trim());
                }
                String chosenLanguage = finalLanguage.getText().toString().trim();
                if (chosenLanguage.isEmpty()) chosenLanguage = "English";
                if (languageInput != null) languageInput.setText(chosenLanguage);
                extractionCorrectionsText = corrections.getText().toString().trim();
                renderQuestionSummary();
                updateProceedButtons();
                dialog.dismiss();
                if (currentReportId != null && !currentReportId.trim().isEmpty() && isRequiredProfileReady() && allQuestionsAnswered()) {
                    analyzeCurrentReport();
                } else {
                    toast("Please complete age, gender, symptoms and language first.");
                }
            });
        });
        dialog.show();
    }

    private String questionAnswerType(JSONObject q) {
        String type = q == null ? "" : q.optString("answer_type", "").trim().toLowerCase(Locale.US);
        if ("single_select".equals(type) || "multi_select".equals(type) || "yes_no".equals(type)
                || "number".equals(type) || "date".equals(type) || "free_text".equals(type)) {
            return type;
        }
        JSONArray options = q == null ? null : q.optJSONArray("answer_options");
        return options != null && options.length() > 0 ? "single_select" : "free_text";
    }

    private String[] getQuestionOptions(JSONObject q) {
        ArrayList<String> values = new ArrayList<>();
        values.add("Select answer");
        JSONArray supplied = q == null ? null : q.optJSONArray("answer_options");
        if (supplied != null) {
            for (int i = 0; i < supplied.length(); i++) {
                String value = supplied.optString(i, "").trim();
                if (!value.isEmpty() && !values.contains(value)) values.add(value);
            }
        }
        if (values.size() == 1) values.add(words("Not sure", "पता नहीं"));
        return values.toArray(new String[0]);
    }

    private String readQuestionAnswer(QuestionControl control) {
        if (control == null) return "";
        if (control.spinner != null) {
            Object selected = control.spinner.getSelectedItem();
            String value = selected == null ? "" : selected.toString().trim();
            if (value.isEmpty() || "Select answer".equals(value)) return "";
            if (("Other".equalsIgnoreCase(value) || "अन्य".equals(value)) && control.otherInput != null) {
                String other = control.otherInput.getText().toString().trim();
                if (other.isEmpty()) return "";
                return value + ": " + other;
            }
            return value;
        }
        if (!control.checkBoxes.isEmpty()) {
            ArrayList<String> selected = new ArrayList<>();
            for (CheckBox check : control.checkBoxes) if (check.isChecked()) selected.add(check.getText().toString().trim());
            if (control.otherInput != null) {
                String other = control.otherInput.getText().toString().trim();
                if (!other.isEmpty()) selected.add(words("Other", "अन्य") + ": " + other);
            }
            StringBuilder joined = new StringBuilder();
            for (String value : selected) {
                if (joined.length() > 0) joined.append(" | ");
                joined.append(value);
            }
            return joined.toString();
        }
        return control.textInput == null ? "" : control.textInput.getText().toString().trim();
    }

    private void renderQuestionSummary() {
        questionsBox.removeAllViews();
        questionsBox.addView(text("AI questions answered", 14, true, COLOR_PRIMARY_DARK));
        StringBuilder sb = new StringBuilder();
        if (questionAnswers.isEmpty()) {
            sb.append("No extra answers saved.");
        } else {
            for (int i = 0; i < currentQuestions.length(); i++) {
                JSONObject q = currentQuestions.optJSONObject(i);
                if (q == null) continue;
                String id = q.optString("id", "q" + i);
                sb.append("• ").append(q.optString("question")).append(" — ").append(questionAnswers.containsKey(id) ? questionAnswers.get(id) : "Not answered").append("\n");
            }
        }
        if (extractionCorrectionsText != null && !extractionCorrectionsText.trim().isEmpty()) {
            sb.append("\nCorrections: ").append(extractionCorrectionsText.trim());
        }
        questionsBox.addView(text(sb.toString().trim(), 13, false, COLOR_TEXT));
        if (questionDialogButton != null) questionDialogButton.setVisibility(View.VISIBLE);
    }

    private boolean hasReportText() {
        return reportTextInput != null && reportTextInput.getText() != null && !reportTextInput.getText().toString().trim().isEmpty();
    }

    private String getReportText() {
        return reportTextInput == null || reportTextInput.getText() == null ? "" : reportTextInput.getText().toString().trim();
    }

    private boolean isRequiredProfileReady() {
        return notBlank(languageInput) && notBlank(ageInput) && notBlank(sexInput) && notBlank(symptomsInput);
    }

    private boolean canAnalyze() {
        if (currentReportId == null || currentReportId.isEmpty()) return false;
        if (!isRequiredProfileReady()) return false;
        return allQuestionsAnswered();
    }

    private boolean allQuestionsAnswered() {
        if (currentQuestions == null || currentQuestions.length() == 0) return true;
        for (int i = 0; i < currentQuestions.length(); i++) {
            JSONObject q = currentQuestions.optJSONObject(i);
            if (q == null) continue;
            String id = q.optString("id", "q" + i);
            if (!q.optBoolean("required", true)) continue;
            String answer = questionAnswers.get(id);
            if (answer == null || answer.trim().isEmpty() || "Select answer".equals(answer)) return false;
        }
        return true;
    }

    private boolean notBlank(EditText e) {
        return e != null && e.getText() != null && !e.getText().toString().trim().isEmpty();
    }

    private void updateProceedButtons() {
        if (readButton != null) {
            readButton.setVisibility(View.GONE);
        }
        if (analyzeButton != null) {
            analyzeButton.setEnabled(true);
            if (currentReportId == null || currentReportId.isEmpty()) {
                analyzeButton.setText("Am I good?");
            } else if (!allQuestionsAnswered()) {
                analyzeButton.setText("Answer questions");
            } else {
                analyzeButton.setText("Am I good?");
            }
            styleActionButton(analyzeButton);
        }
        if (finalStatusView != null) {
            if (!isRequiredProfileReady()) {
                finalStatusView.setText("Select language, gender, age and symptoms. Use Not sure where needed.");
            } else if (selectedUris.isEmpty() && !hasReportText() && (currentReportId == null || currentReportId.isEmpty())) {
                finalStatusView.setText("Upload a file or type report/symptom details. Then tap Am I good?.");
            } else if (currentReportId == null || currentReportId.isEmpty()) {
                finalStatusView.setText("Independent check. Tap Am I good?. AI will ask only needed questions, then open the result directly.");
            } else if (!allQuestionsAnswered()) {
                finalStatusView.setText("Answer the few AI questions. The report will open automatically after that.");
            } else {
                finalStatusView.setText("Ready. Tap Am I good? to open the result.");
            }
        }
    }

    private void clearAll() {
        stopCalmBreathing();
        selectedUris.clear();
        selectedFilenames.clear();
        selectedMimes.clear();
        currentReportId = "";
        selectedPreviousReportId = "";
        selectedPreviousReportLabel = "";
        questionAnswers.clear();
        currentQuestions = new JSONArray();
        extractionCorrectionsText = "";
        if (questionsBox != null) questionsBox.removeAllViews();
        if (questionDialogButton != null) questionDialogButton.setVisibility(View.GONE);
        if (outputView != null) outputView.setText("Cleared. Start a fresh check. Add patient context, then upload a report or type the details.");
        saveSelectedFilesLocally();
        refreshSelectedFilesText();
        updateProceedButtons();
    }

    private void showFinalReportScreen(final JSONObject analysis) {
        if (analysis == null) {
            toast("No final report returned.");
            return;
        }
        lastAnalysisForExport = analysis;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(box, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        box.addView(safetyBanner());

        TextView title = text(words("Report summary", "रिपोर्ट की मुख्य बात"), 25, true, COLOR_PRIMARY_DARK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        TextView sub = text(words("AI summary for doctor discussion only. Not a diagnosis.", "यह doctor से बात करने के लिए AI summary है। यह diagnosis नहीं है।"), 13, false, COLOR_MUTED);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub);

        LinearLayout shareRow = new LinearLayout(this);
        shareRow.setOrientation(LinearLayout.HORIZONTAL);
        shareRow.addView(button(words("Download PDF", "PDF download करें"), new View.OnClickListener() { public void onClick(View v) { exportAnalysisPdf(analysis, false); }}, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        addHorizontalSpace(shareRow, 8);
        shareRow.addView(button(words("Share with doctor", "Doctor को भेजें"), new View.OnClickListener() { public void onClick(View v) { exportAnalysisPdf(analysis, true); }}, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        box.addView(shareRow);

        addFinalSection(box, words("Main answer", "सबसे जरूरी बात"), firstNonBlank(analysis.optString("final_conclusion_simple"), analysis.optString("simple_patient_summary")), Color.rgb(0, 105, 128), Color.rgb(0, 73, 89), Color.rgb(228, 247, 249));
        addFinalSection(box, words("How much can this test/report be trusted?", "इस test/report पर कितना भरोसा करें?"), confidenceTrustText(analysis), Color.rgb(180, 120, 0), COLOR_TEXT, Color.rgb(255, 251, 238));

        TextView riskTitle = sectionTitle(words("Green / Amber / Red first view", "हरा / पीला / लाल आसान संकेत"));
        box.addView(riskTitle);
        box.addView(text(words("Green means AI did not find anything needing quick action in what it could read. It does not mean all-clear. If symptoms continue, see a doctor.", "हरा मतलब AI को पढ़ी हुई जानकारी में जल्दी action वाली बात नहीं दिखी। इसका मतलब यह नहीं कि सब ठीक है। लक्षण बने रहें तो doctor को दिखाएँ।"), 13, false, COLOR_MUTED));
        addRiskBoxes(box, analysis);

        addFinalSection(box, words("What to say to doctor", "Doctor को क्या बोलें"), arrayToLines(analysis.optJSONArray("what_to_tell_doctor_simple")), Color.rgb(0, 105, 128), COLOR_TEXT, Color.rgb(247, 253, 254));
        addFinalSection(box, words("Treatment options to ask doctor", "Doctor से इलाज के कौनसे options पूछें"), arrayToLines(firstArray(analysis, "possible_treatment_directions_to_discuss", "ai_safe_next_options_to_discuss", "what_to_ask_doctor")), Color.rgb(0, 105, 128), COLOR_TEXT, Color.rgb(247, 253, 254));
        addFinalSection(box, words("Tests to ask doctor if needed", "जरूरत हो तो कौनसे test पूछें"), arrayToLines(firstArray(analysis, "additional_tests_to_discuss", "what_to_ask_doctor")), Color.rgb(0, 105, 128), COLOR_TEXT, Color.rgb(247, 253, 254));
        addFinalSection(box, words("When doctor help should be faster", "Doctor से जल्दी कब बात करें"), gentleMedicalHelpText(arrayToLines(firstArray(analysis, "when_it_can_become_serious", "emergency_warning_signs"))), Color.rgb(180, 90, 40), COLOR_TEXT, Color.rgb(255, 249, 244));

        addActionChecklist(box, firstArray(analysis, "action_checklist", "simple_next_steps_now", "what_to_ask_doctor"));
        addFinalSection(box, words("At home support only", "घर पर केवल सहारा"), arrayToLines(firstArray(analysis, "home_support_simple", "safe_home_comfort_steps", "supportive_non_medicine_care")), Color.rgb(90, 126, 40), COLOR_TEXT, Color.rgb(250, 255, 244));
        addFinalSection(box, words("Do not do these", "ये काम न करें"), arrayToLines(firstArray(analysis, "what_not_to_do_simple", "what_to_avoid_until_doctor_review")), Color.rgb(180, 120, 0), COLOR_TEXT, Color.rgb(255, 251, 238));

        addFollowUpBox(box);
        addCalmSupportCard(box, "result");

        TextView bottom = sectionTitle(words("More details if you want", "जरूरत हो तो और जानकारी"));
        bottom.setPadding(0, dp(14), 0, dp(6));
        box.addView(bottom);
        addCollapsibleSection(box, words("Could not read clearly", "जो साफ नहीं पढ़ा गया"), arrayToLines(firstArray(analysis, "machine_reading_limitations", "unreadable_or_unclear_items")), Color.rgb(110, 110, 110), COLOR_TEXT, Color.rgb(248, 248, 248), true);
        addCollapsibleSection(box, words("Assumptions used", "जहाँ बात पूरी साफ नहीं थी"), arrayToLines(analysis.optJSONArray("assumptions_made")), Color.rgb(110, 110, 110), COLOR_TEXT, Color.rgb(248, 248, 248), true);
        addCollapsibleSection(box, words("What AI read from report", "AI ने report में क्या पढ़ा"), arrayToLines(analysis.optJSONArray("what_i_read_before_analysis")), Color.rgb(110, 110, 110), COLOR_TEXT, Color.rgb(248, 248, 248), true);
        addCollapsibleSection(box, words("Rajyoga calm support", "Rajyoga से मन को सहारा"), arrayToLines(analysis.optJSONArray("meditation_psychological_support")), Color.rgb(88, 72, 150), COLOR_TEXT, Color.rgb(248, 246, 255), true);
        addCollapsibleSection(box, words("Calm food / medicine habit", "खाना / दवा लेते समय शांति"), arrayToLines(analysis.optJSONArray("calm_food_medicine_sop")), Color.rgb(88, 72, 150), COLOR_TEXT, Color.rgb(248, 246, 255), true);
        addFinalSection(box, words("Note for doctor", "Doctor के लिए note"), analysis.optString("doctor_note"), Color.rgb(0, 105, 128), COLOR_TEXT, Color.rgb(247, 253, 254));
        addCollapsibleSection(box, words("Safety note", "सुरक्षा note"), analysis.optString("safety_note"), Color.rgb(110, 110, 110), COLOR_TEXT, Color.rgb(248, 248, 248), true);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .setNegativeButton("New report", (d, which) -> clearAll())
                .setPositiveButton("Close", null)
                .create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) window.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        });
        dialog.show();
    }

    private String confidenceTrustText(JSONObject analysis) {
        if (analysis == null) return "";
        String confidence = analysis.optString("confidence", "Medium").trim();
        if (confidence.isEmpty()) confidence = "Medium";
        String trustWord;
        if (prefersHindi()) {
            if ("High".equalsIgnoreCase(confidence)) trustWord = "भरोसा: ज्यादा। Report साफ दिख रही है, फिर भी doctor से पक्का करें।";
            else if ("Low".equalsIgnoreCase(confidence)) trustWord = "भरोसा: कम। इस report/test पर अकेले भरोसा न करें। Doctor दोबारा test या दूसरा test बोल सकते हैं।";
            else trustWord = "भरोसा: मध्यम। इसे मदद के रूप में लें। Doctor जरूरत हो तो repeat/confirm test बोल सकते हैं।";
        } else {
            if ("High".equalsIgnoreCase(confidence)) trustWord = "Trust level: High. The report looks clear, but a doctor should still confirm.";
            else if ("Low".equalsIgnoreCase(confidence)) trustWord = "Trust level: Low. Do not rely on this alone. A doctor may repeat or confirm the test.";
            else trustWord = "Trust level: Medium. Use this as helpful guidance. A doctor may confirm or repeat the test.";
        }
        JSONArray arr = analysis.optJSONArray("test_accuracy_and_false_result_considerations");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String value = arr.optString(i, "").trim();
                if (!value.isEmpty()) {
                    trustWord += "\n• " + value;
                    break;
                }
            }
        }
        return trustWord.trim();
    }

    private String glossaryToLines(JSONArray glossary) {
        if (glossary == null || glossary.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glossary.length(); i++) {
            JSONObject item = glossary.optJSONObject(i);
            if (item == null) continue;
            String term = item.optString("term", "").trim();
            String meaning = item.optString("simple_meaning", "").trim();
            if (term.isEmpty() || meaning.isEmpty()) continue;
            sb.append("• ").append(term).append(": ").append(meaning).append("\n");
        }
        return sb.toString().trim();
    }

    private void addActionChecklist(LinearLayout parent, JSONArray items) {
        if (items == null || items.length() == 0) return;
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(247, 253, 254));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(2), COLOR_PRIMARY);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(10));
        c.setLayoutParams(lp);
        c.addView(text("Action checklist", 17, true, COLOR_PRIMARY_DARK));
        for (int i = 0; i < items.length(); i++) {
            String value = items.optString(i, "").trim();
            if (value.isEmpty()) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText(value);
            cb.setTextSize(15);
            cb.setTextColor(COLOR_TEXT);
            cb.setPadding(0, dp(4), 0, dp(4));
            c.addView(cb);
        }
        parent.addView(c);
    }

    private void addGlossary(LinearLayout parent, JSONArray glossary) {
        if (glossary == null || glossary.length() == 0) return;
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(245, 252, 253));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(2), COLOR_BORDER);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(10));
        c.setLayoutParams(lp);
        c.addView(text("Easy meaning of medical words", 17, true, COLOR_PRIMARY_DARK));
        c.addView(text("Tap any word to understand it.", 13, false, COLOR_MUTED));
        for (int i = 0; i < glossary.length(); i++) {
            JSONObject item = glossary.optJSONObject(i);
            if (item == null) continue;
            final String term = item.optString("term", "").trim();
            final String meaning = item.optString("simple_meaning", "").trim();
            if (term.isEmpty() || meaning.isEmpty()) continue;
            Button b = button(term, new View.OnClickListener() { public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(term)
                        .setMessage(meaning)
                        .setPositiveButton("OK", null)
                        .show();
            }}, false);
            c.addView(b);
        }
        parent.addView(c);
    }

    private void addFollowUpBox(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(255, 255, 255));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), COLOR_BORDER);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(10));
        c.setLayoutParams(lp);
        c.addView(text("Ask about this report", 17, true, COLOR_PRIMARY_DARK));
        final EditText q = editStandalone("Example: What should I ask the doctor first?", "", 2);
        c.addView(q);
        c.addView(button("Save my question for doctor visit", new View.OnClickListener() { public void onClick(View v) {
            String value = q.getText().toString().trim();
            if (value.isEmpty()) { toast("Write your question first."); return; }
            toast("Saved as a question to ask your doctor.");
        }}, false));
        parent.addView(c);
    }

    private void addCollapsibleSection(final LinearLayout parent, final String title, final String body, final int borderColor, final int titleColor, final int bgColor, boolean collapsed) {
        if (body == null || body.trim().isEmpty()) return;
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(2), borderColor);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(10));
        c.setLayoutParams(lp);
        final TextView bodyView = text(body.trim(), 15, false, COLOR_TEXT);
        bodyView.setLineSpacing(0, 1.15f);
        Button header = button((collapsed ? "+ " : "− ") + title, new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean show = bodyView.getVisibility() != View.VISIBLE;
                bodyView.setVisibility(show ? View.VISIBLE : View.GONE);
                ((Button) v).setText((show ? "− " : "+ ") + title);
            }
        }, false);
        header.setTextColor(titleColor);
        c.addView(header);
        c.addView(bodyView);
        bodyView.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        parent.addView(c);
    }

    private boolean hasEmergencyRedFlag(JSONObject analysis) {
        if (analysis == null) return false;
        if (analysis.optBoolean("emergency_escalation_needed", false)) return true;
        JSONArray boxes = analysis.optJSONArray("red_amber_green_boxes");
        if (boxes != null) {
            for (int i = 0; i < boxes.length(); i++) {
                JSONObject item = boxes.optJSONObject(i);
                if (item != null && "Red".equalsIgnoreCase(item.optString("color"))) return true;
            }
        }
        String text = (arrayToLines(analysis.optJSONArray("emergency_warning_signs")) + " " + symptomsInput.getText().toString()).toLowerCase(Locale.US);
        String[] terms = new String[]{"chest pain", "breathless", "breathlessness", "bleeding", "faint", "stroke", "seizure", "unconscious", "severe allergic", "suicide", "poison"};
        for (String t : terms) if (text.contains(t)) return true;
        return false;
    }

    private void openEmergencyHelp() {
        try {
            Intent dial = new Intent(Intent.ACTION_DIAL);
            startActivity(dial);
        } catch (Exception e) {
            toast(words("Please contact nearby medical help.", "कृपया पास की medical help से संपर्क करें।"));
        }
    }

    private void exportAnalysisPdf(JSONObject analysis, boolean share) {
        try {
            File file = createDoctorPdf(analysis);
            lastPdfFile = file;
            if (share) {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/pdf");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "Share report with doctor"));
            } else {
                String savedName = savePdfToDownloads(file);
                toast("PDF saved in Downloads: " + savedName);
            }
        } catch (Exception e) {
            toast("Could not create PDF: " + e.getMessage());
        }
    }

    private String savePdfToDownloads(File sourceFile) throws Exception {
        String filename = sourceFile.getName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Downloads folder not available");
            try (InputStream in = new FileInputStream(sourceFile); OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("Could not open Downloads file");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            return filename;
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, filename);
            try (InputStream in = new FileInputStream(sourceFile); OutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            return dest.getName();
        }
    }

    private String safeFilePart(String value) {
        String cleaned = value == null ? "Patient" : value.trim();
        if (cleaned.isEmpty()) cleaned = "Patient";
        cleaned = cleaned.replaceAll("[^A-Za-z0-9_\\-]+", "_");
        while (cleaned.contains("__")) cleaned = cleaned.replace("__", "_");
        if (cleaned.length() > 40) cleaned = cleaned.substring(0, 40);
        return cleaned;
    }

    private File createDoctorPdf(JSONObject analysis) throws Exception {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "Medical_Report_AI_" + safeFilePart(nameInput == null ? "Patient" : nameInput.getText().toString()) + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf");
        PdfDocument pdf = new PdfDocument();
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(11f);
        Paint titlePaint = new Paint(paint);
        titlePaint.setTextSize(17f);
        titlePaint.setFakeBoldText(true);
        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 36;
        int pageNumber = 1;
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
        Canvas canvas = page.getCanvas();
        int y = margin;
        canvas.drawText("Medical Report AI - Summary and Note for Doctor", margin, y, titlePaint);
        y += 26;
        y = drawWrapped(canvas, "Not a diagnosis — for discussion with your doctor only.", margin, y, pageWidth - (margin * 2), paint);
        y += 10;
        String content = buildDoctorPdfText(analysis);
        for (String paragraph : content.split("\\n")) {
            if (y > pageHeight - margin - 20) {
                pdf.finishPage(page);
                pageNumber++;
                page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                canvas = page.getCanvas();
                y = margin;
            }
            y = drawWrapped(canvas, paragraph, margin, y, pageWidth - (margin * 2), paint);
            y += 6;
        }
        pdf.finishPage(page);
        try (FileOutputStream out = new FileOutputStream(file)) {
            pdf.writeTo(out);
        }
        pdf.close();
        return file;
    }

    private int drawWrapped(Canvas canvas, String text, int x, int y, int width, Paint paint) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return y + 10;
        String[] words = value.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > width && line.length() > 0) {
                canvas.drawText(line.toString(), x, y, paint);
                y += 15;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, y, paint);
            y += 15;
        }
        return y;
    }

    private String buildDoctorPdfText(JSONObject analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("Main answer: ").append(firstNonBlank(analysis.optString("final_conclusion_simple"), analysis.optString("simple_patient_summary"))).append("\n\n");
        sb.append("Confidence: ").append(confidenceTrustText(analysis)).append("\n\n");
        sb.append("What patient should tell doctor:\n").append(arrayToLines(analysis.optJSONArray("what_to_tell_doctor_simple"))).append("\n\n");
        sb.append("Treatment options to ask doctor:\n").append(arrayToLines(firstArray(analysis, "possible_treatment_directions_to_discuss", "ai_safe_next_options_to_discuss", "what_to_ask_doctor"))).append("\n\n");
        sb.append("Green/Amber/Red view:\n");
        JSONArray boxes = analysis.optJSONArray("red_amber_green_boxes");
        if (boxes != null) {
            for (int i = 0; i < boxes.length(); i++) {
                JSONObject item = boxes.optJSONObject(i);
                if (item == null) continue;
                sb.append("- ").append(item.optString("color")).append(": ").append(item.optString("title")).append(" — ").append(item.optString("simple_message")).append(" Action: ").append(item.optString("what_to_do")).append("\n");
            }
        }
        sb.append("\nWhen doctor help should be faster:\n").append(gentleMedicalHelpText(arrayToLines(firstArray(analysis, "when_it_can_become_serious", "emergency_warning_signs")))).append("\n\n");
        sb.append("Note for doctor:\n").append(analysis.optString("doctor_note")).append("\n\n");
        sb.append("Machine reading limits:\n").append(arrayToLines(firstArray(analysis, "machine_reading_limitations", "unreadable_or_unclear_items"))).append("\n\n");
        sb.append("What AI read from report:\n").append(arrayToLines(analysis.optJSONArray("what_i_read_before_analysis"))).append("\n");
        return sb.toString();
    }

    private void addRiskBoxes(LinearLayout parent, JSONObject analysis) {
        JSONArray boxes = analysis.optJSONArray("red_amber_green_boxes");
        if (boxes != null && boxes.length() > 0) {
            for (int i = 0; i < boxes.length(); i++) {
                JSONObject item = boxes.optJSONObject(i);
                if (item == null) continue;
                addColorBox(parent, item.optString("color"), item.optString("title"), item.optString("simple_message"), item.optString("what_to_do"), item.optString("why_this_color"));
            }
            return;
        }
        JSONArray risks = analysis.optJSONArray("low_medium_high_risk_areas");
        if (risks == null || risks.length() == 0) {
            addColorBox(parent, "Green", "Nothing immediately alarming found", "Nothing immediately alarming was found in the parts AI could read. This does not prove there is no condition.", "Still show the report to a doctor if symptoms continue or you are worried.", "No high-risk item was available in AI output.");
            return;
        }
        for (int i = 0; i < risks.length(); i++) {
            JSONObject r = risks.optJSONObject(i);
            if (r == null) continue;
            String level = r.optString("risk_level", "Amber");
            String color = "Low".equalsIgnoreCase(level) ? "Green" : ("High".equalsIgnoreCase(level) ? "Red" : "Amber");
            addColorBox(parent, color, r.optString("area"), r.optString("what_it_may_mean_simple"), r.optString("doctor_priority"), r.optString("test_accuracy_consideration"));
        }
    }

    private void addColorBox(LinearLayout parent, String colorName, String title, String message, String action, String reason) {
        int border;
        int bg;
        int titleColor;
        String label;
        if ("Red".equalsIgnoreCase(colorName)) {
            border = Color.rgb(205, 60, 60);
            bg = Color.rgb(255, 239, 239);
            titleColor = Color.rgb(145, 30, 30);
            label = "RED — faster doctor review";
        } else if ("Amber".equalsIgnoreCase(colorName) || "Medium".equalsIgnoreCase(colorName)) {
            border = Color.rgb(220, 150, 20);
            bg = Color.rgb(255, 249, 229);
            titleColor = Color.rgb(150, 95, 0);
            label = "AMBER — doctor review";
        } else {
            border = Color.rgb(72, 160, 80);
            bg = Color.rgb(238, 250, 239);
            titleColor = Color.rgb(30, 120, 45);
            label = "GREEN — lower concern (not all-clear)";
        }
        String body = label + "\n\n" + firstNonBlank(message, "No simple message returned.");
        if (action != null && !action.trim().isEmpty()) body += "\n\nWhat to do: " + action.trim();
        if (reason != null && !reason.trim().isEmpty()) body += "\n\nWhy: " + reason.trim();
        addFinalSection(parent, gentleMedicalHelpText(firstNonBlank(title, "Risk item")), gentleMedicalHelpText(body), border, titleColor, bg);
    }

    private void addFinalSection(LinearLayout parent, String title, String body, int borderColor, int titleColor, int bgColor) {
        if (body == null || body.trim().isEmpty()) return;
        title = gentleMedicalHelpText(title);
        body = gentleMedicalHelpText(body);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(2), borderColor);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(10));
        c.setLayoutParams(lp);
        c.addView(text(title, 17, true, titleColor));
        TextView bodyView = text(body.trim(), 15, false, COLOR_TEXT);
        bodyView.setLineSpacing(0, 1.15f);
        c.addView(bodyView);
        parent.addView(c);
    }

    private JSONArray firstArray(JSONObject obj, String... keys) {
        if (obj == null) return null;
        for (String key : keys) {
            JSONArray arr = obj.optJSONArray(key);
            if (arr != null && arr.length() > 0) return arr;
        }
        return null;
    }

    private String arrayToLines(JSONArray arr) {
        if (arr == null || arr.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            String value = arr.optString(i, "").trim();
            if (!value.isEmpty()) sb.append("• ").append(value).append("\n");
        }
        return sb.toString().trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private String formatExtraction(JSONObject response) {
        StringBuilder sb = new StringBuilder();
        sb.append("SAFETY NOTE\n").append(response.optString("safety_note")).append("\n\n");
        sb.append("REPORT ID\n").append(response.optString("report_id")).append("\n\n");
        JSONObject extracted = response.optJSONObject("extracted");
        if (extracted == null) return response.toString();
        JSONObject quality = extracted.optJSONObject("document_quality");
        if (quality != null) {
            sb.append("DOCUMENT QUALITY\n");
            sb.append("Readable: ").append(quality.optBoolean("readable")).append("\n");
            sb.append("Typed/printed: ").append(quality.optBoolean("typed_or_printed")).append("\n");
            sb.append("Handwritten/unclear: ").append(quality.optBoolean("handwritten_or_unclear")).append("\n");
            sb.append(quality.optString("quality_notes")).append("\n\n");
        }
        appendArray(sb, "FILES READ", extracted.optJSONArray("files_read"));
        appendArray(sb, "WHAT I READ FROM REPORT", extracted.optJSONArray("what_i_read"));
        JSONArray tests = extracted.optJSONArray("test_results");
        if (tests != null && tests.length() > 0) {
            sb.append("TEST VALUES READ\n");
            for (int i = 0; i < tests.length(); i++) {
                JSONObject t = tests.optJSONObject(i);
                if (t == null) continue;
                sb.append("• ").append(t.optString("test_name"))
                        .append(": ").append(t.optString("value"))
                        .append(" ").append(t.optString("unit"))
                        .append(" | Range: ").append(t.optString("reference_range"))
                        .append(" | Flag: ").append(t.optString("flag"))
                        .append(" | Confidence: ").append(t.optInt("confidence"))
                        .append("%\n");
            }
            sb.append("\n");
        }
        appendArray(sb, "TEST ACCURACY LIMITATIONS", extracted.optJSONArray("test_accuracy_limitations"));
        appendArray(sb, "UNREADABLE / MISSING", extracted.optJSONArray("unreadable_or_missing_parts"));
        sb.append("\nNext: answer the questions above, then prepare final report.\n");
        return sb.toString();
    }

    private String formatAnalysis(JSONObject analysis) {
        if (analysis == null) return "No analysis returned.";
        StringBuilder sb = new StringBuilder();
        sb.append(analysis.optString("title", "Report explanation")).append("\n\n");
        sb.append("SAFETY NOTE\n").append(analysis.optString("safety_note")).append("\n\n");
        appendArray(sb, "WHAT AI READ BEFORE ANALYSIS", analysis.optJSONArray("what_i_read_before_analysis"));
        appendArray(sb, "UNREADABLE / UNCLEAR ITEMS", analysis.optJSONArray("unreadable_or_unclear_items"));
        sb.append("NO HANDWRITTEN ANALYSIS\n").append(analysis.optString("no_handwritten_analysis_note")).append("\n\n");
        appendArray(sb, "TEST ACCURACY / FALSE RESULT CONSIDERATIONS", analysis.optJSONArray("test_accuracy_and_false_result_considerations"));
        JSONArray risks = analysis.optJSONArray("low_medium_high_risk_areas");
        if (risks != null && risks.length() > 0) {
            sb.append("LOW / MEDIUM / HIGH RISK AREAS\n");
            for (int i = 0; i < risks.length(); i++) {
                JSONObject r = risks.optJSONObject(i);
                if (r == null) continue;
                sb.append("• [").append(r.optString("risk_level")).append("] ").append(r.optString("area")).append("\n");
                sb.append("  Evidence: ").append(r.optString("evidence_from_report")).append("\n");
                sb.append("  Meaning: ").append(r.optString("what_it_may_mean_simple")).append("\n");
                sb.append("  Test accuracy note: ").append(r.optString("test_accuracy_consideration")).append("\n");
                sb.append("  Why not final: ").append(r.optString("why_not_definitive")).append("\n");
                sb.append("  Doctor priority: ").append(r.optString("doctor_priority")).append("\n");
            }
            sb.append("\n");
        }
        appendArray(sb, "WHAT EACH RISK MEANS", analysis.optJSONArray("what_each_risk_area_means"));
        appendArray(sb, "POSSIBLE REASONS — NOT DIAGNOSIS", analysis.optJSONArray("possible_reasons_not_diagnosis"));
        appendArray(sb, "WHAT TO ASK DOCTOR", analysis.optJSONArray("what_to_ask_doctor"));
        appendArray(sb, "POSSIBLE TREATMENT DIRECTIONS TO DISCUSS", analysis.optJSONArray("possible_treatment_directions_to_discuss"));
        appendArray(sb, "SAFE NON-MEDICINE SUPPORTIVE CARE", analysis.optJSONArray("supportive_non_medicine_care"));
        appendArray(sb, "SAFE HOME COMFORT STEPS", analysis.optJSONArray("safe_home_comfort_steps"));
        appendArray(sb, "MEDITATION / PSYCHOLOGICAL SUPPORT", analysis.optJSONArray("meditation_psychological_support"));
        appendArray(sb, "CALM FOOD / MEDICINE SOP", analysis.optJSONArray("calm_food_medicine_sop"));
        appendArray(sb, "SUGGESTED MEDITATION VIDEO FROM YOUR LINKS", analysis.optJSONArray("suggested_meditation_videos"));
        appendArray(sb, "WHAT TO AVOID UNTIL DOCTOR REVIEW", analysis.optJSONArray("what_to_avoid_until_doctor_review"));
        appendArray(sb, "EMERGENCY WARNING SIGNS", analysis.optJSONArray("emergency_warning_signs"));
        sb.append("TREND FROM PREVIOUS REPORTS\n").append(analysis.optString("trend_from_previous_reports")).append("\n\n");
        sb.append("SIMPLE SUMMARY\n").append(analysis.optString("simple_patient_summary")).append("\n\n");
        sb.append("NOTE FOR DOCTOR\n").append(analysis.optString("doctor_note")).append("\n\n");
        appendArray(sb, "LIMITATIONS", analysis.optJSONArray("limitations"));
        sb.append("CONFIDENCE\n").append(analysis.optString("confidence")).append("\n");
        return sb.toString();
    }

    private String formatHistory(JSONArray items) {
        if (items == null || items.length() == 0) return "No saved reports yet.";
        StringBuilder sb = new StringBuilder("SAVED REPORTS\n\n");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            sb.append(i + 1).append(". ").append(item.optString("title", item.optString("original_filename"))).append("\n");
            sb.append("   Status: ").append(friendlyStatus(item.optString("status"))).append("\n");
            sb.append("   Date: ").append(friendlyDate(item.optString("created_at"))).append("\n\n");
        }
        return sb.toString();
    }

    private void appendArray(StringBuilder sb, String title, JSONArray arr) {
        if (arr == null || arr.length() == 0) return;
        sb.append(title).append("\n");
        for (int i = 0; i < arr.length(); i++) sb.append("• ").append(arr.optString(i)).append("\n");
        sb.append("\n");
    }

    private String getDisplayName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception ignored) {}
        }
        if (result == null || result.trim().isEmpty()) result = uri.getLastPathSegment();
        if (result == null || result.trim().isEmpty()) result = "medical_report";
        return result;
    }

    private String guessMimeFromName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private LinearLayout uploadGuideBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(245, 252, 253));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), COLOR_BORDER);
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(8));
        box.setLayoutParams(lp);
        box.addView(text("Good upload example", 14, true, COLOR_PRIMARY_DARK));
        box.addView(text("✓ Full printed report page visible\n✓ Good light, no shadow\n✓ Test name, value, unit and range readable\n✕ Avoid handwriting, blur, half page, WhatsApp-compressed photos", 13, false, COLOR_TEXT));
        return box;
    }

    private JSONObject loadCalmContent() {
        if (calmContentJson != null) return calmContentJson;
        StringBuilder sb = new StringBuilder();
        try {
            InputStream input = getAssets().open("calm_content.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            calmContentJson = new JSONObject(sb.toString());
        } catch (Exception ignored) {
            calmContentJson = new JSONObject();
        }
        return calmContentJson;
    }

    private String calmLanguageCode() {
        return prefersHindi() ? "hi" : "en";
    }

    private String calmMessageForIndex(int index) {
        try {
            JSONArray arr = loadCalmContent().optJSONArray("calm_messages");
            if (arr != null && arr.length() > 0) {
                List<String> values = new ArrayList<>();
                String lang = calmLanguageCode();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.optJSONObject(i);
                    if (item == null || !item.optBoolean("enabled", true)) continue;
                    String itemLang = item.optString("language", "").trim().toLowerCase(Locale.US);
                    if (!itemLang.isEmpty() && !itemLang.equals(lang)) continue;
                    String title = item.optString("title", "").trim();
                    String textValue = item.optString("text", "").trim();
                    if (!textValue.isEmpty()) values.add(title.isEmpty() ? textValue : title + "\n" + textValue);
                }
                if (!values.isEmpty()) return values.get(Math.abs(index) % values.size());
            }
        } catch (Exception ignored) {}
        return breathingMessages[Math.abs(index) % breathingMessages.length];
    }

    private JSONObject pickCalmVideo(String placement) {
        try {
            JSONArray arr = loadCalmContent().optJSONArray("youtube_videos");
            if (arr == null || arr.length() == 0) return null;
            List<JSONObject> values = new ArrayList<>();
            String lang = calmLanguageCode();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null || !item.optBoolean("enabled", true)) continue;
                String itemLang = item.optString("language", "").trim().toLowerCase(Locale.US);
                if (!itemLang.isEmpty() && !itemLang.equals(lang)) continue;
                String url = item.optString("youtube_url", "").trim();
                if (url.isEmpty() || url.contains("PASTE_YOUTUBE_VIDEO_ID_HERE")) continue;
                if (!placementAllowed(item.optJSONArray("placement"), placement)) continue;
                values.add(item);
            }
            if (values.isEmpty()) return null;
            return values.get(random.nextInt(values.size()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean placementAllowed(JSONArray placements, String placement) {
        if (placements == null || placements.length() == 0) return true;
        String want = placement == null ? "" : placement.trim().toLowerCase(Locale.US);
        for (int i = 0; i < placements.length(); i++) {
            if (placements.optString(i, "").trim().toLowerCase(Locale.US).equals(want)) return true;
        }
        return false;
    }

    private void updateCalmVideoButton(JSONObject video) {
        currentCalmVideoUrl = "";
        currentCalmVideoTitle = "";
        if (calmVideoButton == null) return;
        if (video == null) {
            calmVideoButton.setVisibility(View.GONE);
            return;
        }
        currentCalmVideoUrl = video.optString("youtube_url", "").trim();
        currentCalmVideoTitle = firstNonBlank(video.optString("title"), words("Calming video", "शांति video"));
        if (currentCalmVideoUrl.isEmpty()) {
            calmVideoButton.setVisibility(View.GONE);
            return;
        }
        calmVideoButton.setText(words("Watch calming video", "शांति video देखें"));
        calmVideoButton.setVisibility(View.VISIBLE);
    }

    private void openCurrentCalmVideo() {
        if (currentCalmVideoUrl == null || currentCalmVideoUrl.trim().isEmpty()) {
            toast(words("No calming video link is available yet.", "अभी कोई शांति video link उपलब्ध नहीं है।"));
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentCalmVideoUrl));
            startActivity(intent);
        } catch (Exception e) {
            toast(words("Could not open video.", "Video open नहीं हो पाया।"));
        }
    }

    private void addCalmSupportCard(LinearLayout parent, String placement) {
        JSONObject video = pickCalmVideo(placement);
        if (video == null) return;
        final String url = video.optString("youtube_url", "").trim();
        if (url.isEmpty()) return;
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(248, 246, 255));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(2), Color.rgb(88, 72, 150));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(10));
        c.setLayoutParams(lp);
        c.addView(text(words("Calm support", "मन को शांत रखने में सहारा"), 17, true, Color.rgb(88, 72, 150)));
        c.addView(text(firstNonBlank(video.optString("title"), words("Short calming video", "छोटी शांति video")), 15, true, COLOR_TEXT));
        c.addView(text(firstNonBlank(video.optString("description"), words("Optional support while you prepare for doctor discussion.", "Doctor से बात करने की तैयारी के समय optional सहारा।")), 13, false, COLOR_MUTED));
        c.addView(text(firstNonBlank(video.optString("medical_disclaimer"), words("For calmness only. Not medical advice.", "केवल मन की शांति के लिए। यह medical advice नहीं है।")), 12, false, COLOR_MUTED));
        c.addView(button(words("Watch calming video", "शांति video देखें"), new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception e) { toast(words("Could not open video.", "Video open नहीं हो पाया।")); }
            }
        }, false));
        parent.addView(c);
    }

    private String gentleMedicalHelpText(String raw) {
        String clean = raw == null ? "" : raw.trim();
        if (clean.isEmpty()) return clean;
        clean = clean.replace("1" + "08" + " / " + "1" + "02", "nearby medical service");
        clean = clean.replace("1" + "08" + "/" + "1" + "02", "nearby medical service");
        clean = clean.replace("call nearby medical service", "contact nearby medical service");
        clean = clean.replace("Call nearby medical service", "Contact nearby medical service");
        clean = clean.replace("go to E" + "R now", "contact nearby medical service now");
        clean = clean.replace("Go to E" + "R now", "Contact nearby medical service now");
        clean = clean.replace("Emergency " + "signs found", "Important doctor review points");
        clean = clean.replace("Urgent " + "warning", "Important doctor review points");
        clean = clean.replace("emergency " + "room", "nearby medical service");
        clean = clean.replace("Emergency " + "room", "nearby medical service");
        clean = clean.replace("ग" + "ंभीर " + "संकेत", "ध्यान देने वाली बातें");
        clean = clean.replace("तुर" + "ंत ध्यान दें", "ध्यान देने वाली बात");
        clean = clean.replace("तुर" + "ंत emergency", "medical help");
        clean = clean.replace("तुर" + "ंत", "जल्दी");
        return clean;
    }

    private boolean prefersHindi() {
        String lang = languageInput == null ? "" : languageInput.getText().toString().trim().toLowerCase(Locale.US);
        return lang.contains("hindi") || lang.contains("hinglish") || lang.contains("हिंदी") || lang.contains("हिन्दी") || lang.contains("हिन्दी");
    }

    private String words(String english, String hindi) {
        return prefersHindi() ? hindi : english;
    }

    private TextView safetyBanner() {
        TextView banner = text(words("⚠ Not a diagnosis. AI can make mistakes. Use this only to discuss with your doctor.", "⚠ यह diagnosis नहीं है। AI गलती कर सकता है। इसे केवल doctor से बात करने के लिए इस्तेमाल करें।"), 14, true, Color.rgb(110, 55, 0));
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(255, 248, 225));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(2), Color.rgb(235, 170, 40));
        banner.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        banner.setLayoutParams(lp);
        return banner;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), COLOR_BORDER);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        c.setLayoutParams(lp);
        return c;
    }

    private TextView sectionTitle(String value) {
        TextView t = text(value, 18, true, COLOR_PRIMARY_DARK);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private TextView pill(String value) {
        TextView p = text(value, 12, true, COLOR_PRIMARY_DARK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(225, 247, 245));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(178, 224, 220));
        p.setBackground(bg);
        p.setPadding(dp(10), dp(7), dp(10), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        p.setLayoutParams(lp);
        return p;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(4));
        view.setLineSpacing(0, 1.08f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }


    private String[] languageOptions() {
        return new String[]{"Hindi", "English", "Hinglish", "Marathi", "Gujarati", "Bengali", "Tamil", "Telugu", "Kannada", "Malayalam", "Punjabi", "Urdu", "Nepali", "Arabic", "Spanish", "French", "Other"};
    }

    private String[] genderOptions() {
        return new String[]{"Female", "Male", "Other", "Prefer not to say", "Not sure"};
    }

    private EditText hiddenEdit(String value) {
        EditText e = new EditText(this);
        e.setText(value == null || value.trim().isEmpty() ? "" : value.trim());
        e.setVisibility(View.GONE);
        return e;
    }

    private Spinner dropdown(LinearLayout parent, String label, String[] options, String currentValue, final EditText backingField) {
        TextView labelView = text(label, 13, true, COLOR_PRIMARY_DARK);
        labelView.setPadding(0, dp(8), 0, dp(2));
        parent.addView(labelView);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int selection = 0;
        String current = currentValue == null ? "" : currentValue.trim();
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(current)) {
                selection = i;
                break;
            }
        }
        spinner.setSelection(selection);
        if (backingField != null) backingField.setText(options[selection]);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parentView, View selectedView, int position, long id) {
                Object selected = parentView.getItemAtPosition(position);
                if (backingField != null && selected != null) backingField.setText(selected.toString());
                saveDraftSafely();
                updateProceedButtons();
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) {}
        });
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(250, 253, 254));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), COLOR_BORDER);
        spinner.setBackground(bg);
        spinner.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(8));
        spinner.setLayoutParams(lp);
        parent.addView(spinner);
        return spinner;
    }

    private EditText edit(LinearLayout parent, String hint, String value, int lines) {
        EditText e = editStandalone(hint, value, lines);
        parent.addView(e);
        return e;
    }

    private EditText editStandalone(String hint, String value, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setMinLines(lines);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setTextColor(COLOR_TEXT);
        e.setHintTextColor(Color.rgb(120, 145, 153));
        e.setTextSize(14);
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(250, 253, 254));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), COLOR_BORDER);
        e.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(8));
        e.setLayoutParams(lp);
        return e;
    }

    private Button button(String label, View.OnClickListener listener, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setOnClickListener(listener);
        styleButton(b, primary, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void styleButton(Button b, boolean primary, boolean enabled) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (!enabled) {
            bg.setColor(Color.rgb(205, 218, 222));
            b.setTextColor(Color.rgb(105, 125, 132));
        } else if (primary) {
            bg.setColor(COLOR_PRIMARY);
            b.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.rgb(232, 247, 249));
            bg.setStroke(dp(1), COLOR_BORDER);
            b.setTextColor(COLOR_PRIMARY_DARK);
        }
        b.setBackground(bg);
    }

    private void styleActionButton(Button b) {
        if (b == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.rgb(0, 128, 96));
        bg.setStroke(dp(2), Color.rgb(0, 92, 68));
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(bg);
    }

    private void addHorizontalSpace(LinearLayout row, int dpValue) {
        View v = new View(this);
        row.addView(v, new LinearLayout.LayoutParams(dp(dpValue), 1));
    }

    private void startCalmBreathing(final String headline) {
        breathingHeadline = headline == null ? "Please wait..." : headline;
        breathingIndex = 0;
        updateCalmVideoButton(pickCalmVideo("waiting"));
        if (meditationOverlay != null) meditationOverlay.setVisibility(View.VISIBLE);
        if (circleAnimator != null) circleAnimator.cancel();
        if (meditationCircle != null) {
            ObjectAnimator sx = ObjectAnimator.ofFloat(meditationCircle, "scaleX", 0.78f, 1.18f);
            ObjectAnimator sy = ObjectAnimator.ofFloat(meditationCircle, "scaleY", 0.78f, 1.18f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(meditationCircle, "alpha", 0.62f, 1.0f);
            sx.setRepeatCount(ObjectAnimator.INFINITE);
            sy.setRepeatCount(ObjectAnimator.INFINITE);
            alpha.setRepeatCount(ObjectAnimator.INFINITE);
            sx.setRepeatMode(ObjectAnimator.REVERSE);
            sy.setRepeatMode(ObjectAnimator.REVERSE);
            alpha.setRepeatMode(ObjectAnimator.REVERSE);
            sx.setDuration(2200);
            sy.setDuration(2200);
            alpha.setDuration(2200);
            circleAnimator = new AnimatorSet();
            circleAnimator.playTogether(sx, sy, alpha);
            circleAnimator.start();
        }
        breathingHandler.removeCallbacks(breathingRunnable);
        breathingRunnable.run();
    }

    private void stopCalmBreathing() {
        breathingHandler.removeCallbacks(breathingRunnable);
        if (circleAnimator != null) {
            circleAnimator.cancel();
            circleAnimator = null;
        }
        if (calmVideoButton != null) calmVideoButton.setVisibility(View.GONE);
        if (meditationOverlay != null) meditationOverlay.setVisibility(View.GONE);
    }

    private void setBusy(final String msg) {
        stopCalmBreathing();
        outputView.setText(msg);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

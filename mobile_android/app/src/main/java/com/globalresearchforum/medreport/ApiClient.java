package com.globalresearchforum.medreport;

import android.content.ContentResolver;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class ApiClient {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 240000;

    public static JSONObject uploadReports(ContentResolver resolver, List<Uri> uris, List<String> filenames, List<String> mimeTypes, String deviceId, String language, JSONObject patientProfile, String additionalReportText) throws Exception {
        boolean hasFiles = uris != null && !uris.isEmpty();
        boolean hasText = additionalReportText != null && !additionalReportText.trim().isEmpty();
        if (!hasFiles && !hasText) throw new RuntimeException("Please select at least one report file or paste report text.");
        String boundary = "Boundary-" + UUID.randomUUID();
        URL url = new URL(BuildConfig.API_BASE_URL + "/v1/report/upload");
        HttpURLConnection conn = open(url, "POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);

        try (OutputStream out = conn.getOutputStream()) {
            writeFormField(out, boundary, "device_id", deviceId);
            writeFormField(out, boundary, "language", language == null ? "auto" : language);
            writeFormField(out, boundary, "patient_profile_json", patientProfile == null ? "{}" : patientProfile.toString());
            writeFormField(out, boundary, "additional_report_text", additionalReportText == null ? "" : additionalReportText);
            for (int i = 0; hasFiles && i < uris.size(); i++) {
                String filename = i < filenames.size() ? filenames.get(i) : "medical_report_" + (i + 1);
                String mime = i < mimeTypes.size() ? mimeTypes.get(i) : null;
                writeFileField(out, boundary, "files", filename, mime, resolver, uris.get(i));
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        return readJson(conn);
    }

    public static JSONObject uploadReport(ContentResolver resolver, Uri uri, String filename, String mimeType, String deviceId, String language, JSONObject patientProfile) throws Exception {
        java.util.ArrayList<Uri> uris = new java.util.ArrayList<>();
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        java.util.ArrayList<String> mimes = new java.util.ArrayList<>();
        uris.add(uri);
        names.add(filename);
        mimes.add(mimeType);
        return uploadReports(resolver, uris, names, mimes, deviceId, language, patientProfile, "");
    }

    public static JSONObject analyzeReport(JSONObject payload) throws Exception {
        return postJson("/v1/report/analyze", payload);
    }

    public static JSONObject history(String deviceId) throws Exception {
        String url = BuildConfig.API_BASE_URL + "/v1/report/history?device_id=" + urlEncode(deviceId);
        HttpURLConnection conn = open(new URL(url), "GET");
        return readJson(conn);
    }

    public static JSONObject getReport(String reportId, String deviceId) throws Exception {
        String url = BuildConfig.API_BASE_URL + "/v1/report/" + urlEncode(reportId) + "?device_id=" + urlEncode(deviceId);
        HttpURLConnection conn = open(new URL(url), "GET");
        return readJson(conn);
    }

    public static JSONObject deleteReport(String reportId, String deviceId) throws Exception {
        String url = BuildConfig.API_BASE_URL + "/v1/report/" + urlEncode(reportId) + "?device_id=" + urlEncode(deviceId);
        HttpURLConnection conn = open(new URL(url), "DELETE");
        return readJson(conn);
    }

    public static JSONObject auditEvent(JSONObject payload) throws Exception {
        return postJson("/v1/audit/client", payload == null ? new JSONObject() : payload);
    }

    public static JSONObject creditAuditEvent(JSONObject payload) throws Exception {
        return postJson("/v1/audit/credit", payload == null ? new JSONObject() : payload);
    }

    private static JSONObject postJson(String path, JSONObject payload) throws Exception {
        URL url = new URL(BuildConfig.API_BASE_URL + path);
        HttpURLConnection conn = open(url, "POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readJson(conn);
    }

    private static HttpURLConnection open(URL url, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-App-Token", BuildConfig.APP_ACCESS_TOKEN);
        return conn;
    }

    private static void writeFormField(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFileField(OutputStream out, String boundary, String name, String filename, String mimeType, ContentResolver resolver, Uri uri) throws Exception {
        String safeName = filename == null || filename.trim().isEmpty() ? "medical_report" : filename.replace("\"", "_");
        String safeMime = mimeType == null || mimeType.trim().isEmpty() ? guessMime(safeName) : mimeType;
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + safeName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + safeMime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try (InputStream in = new BufferedInputStream(resolver.openInputStream(uri))) {
            if (in == null) throw new RuntimeException("Could not open selected file: " + safeName);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject readJson(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new RuntimeException("API error " + code + ": " + body);
        }
        return new JSONObject(body);
    }

    private static String readAll(InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private static String urlEncode(String value) throws Exception {
        return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private static String guessMime(String filename) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(filename);
        String mime = ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        return mime == null ? "application/octet-stream" : mime;
    }
}

package com.mysandbox.work;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/** Checks public release metadata only after an explicit user request. */
final class UpdateChecker {
    private static final String REPOSITORY = "hubg9527777-dotcom/WorkSandbox";
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private final Activity activity;
    private final Button button;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private volatile HttpsURLConnection connection;

    UpdateChecker(Activity activity, Button button) {
        this.activity = activity;
        this.button = button;
        button.setOnClickListener(view -> check());
    }

    private void check() {
        if (closed || !button.isEnabled()) return;
        button.setEnabled(false);
        button.setText(R.string.update_checking);
        executor.execute(() -> {
            try {
                Release release = fetchRelease();
                String installed = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0).versionName;
                boolean newer = UpdatePolicy.compareVersions(release.version, installed) > 0;
                activity.runOnUiThread(() -> {
                    if (!isActive()) return;
                    resetButton();
                    if (!newer) {
                        Toast.makeText(activity, R.string.update_current, Toast.LENGTH_LONG).show();
                        return;
                    }
                    String message = activity.getString(R.string.update_details,
                            installed, release.version, release.notes);
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.update_available)
                            .setMessage(message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.update_download, (dialog, which) -> {
                                try {
                                    activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                            Uri.parse(release.downloadUrl)).addCategory(Intent.CATEGORY_BROWSABLE));
                                    Toast.makeText(activity, R.string.update_install_hint, Toast.LENGTH_LONG).show();
                                } catch (ActivityNotFoundException | SecurityException e) {
                                    Toast.makeText(activity, R.string.update_browser_missing, Toast.LENGTH_LONG).show();
                                }
                            }).show();
                });
            } catch (Exception e) {
                // Do not log response bodies or device/account details.
                activity.runOnUiThread(() -> {
                    if (!isActive()) return;
                    resetButton();
                    Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private Release fetchRelease() throws Exception {
        HttpsURLConnection request = (HttpsURLConnection) new URL(
                "https://api.github.com/repos/" + REPOSITORY + "/releases/latest").openConnection();
        connection = request;
        try {
            if (closed) throw new IOException("Closed");
            request.setConnectTimeout(10000);
            request.setReadTimeout(10000);
            request.setInstanceFollowRedirects(false);
            request.setRequestProperty("Accept", "application/vnd.github+json");
            request.setRequestProperty("User-Agent", "WorkSandbox-update-checker");
            request.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            if (request.getResponseCode() != 200) throw new IOException("Release unavailable");
            if (request.getContentLengthLong() > MAX_RESPONSE_BYTES) throw new IOException("Response too large");
            JSONObject json;
            try (InputStream stream = request.getInputStream();
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096];
                int count;
                long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
                while ((count = stream.read(chunk)) != -1) {
                    if (android.os.SystemClock.elapsedRealtime() > deadline) throw new IOException("Read deadline exceeded");
                    if (closed || buffer.size() + count > MAX_RESPONSE_BYTES) throw new IOException("Response rejected");
                    buffer.write(chunk, 0, count);
                }
                json = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            }
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) throw new IOException("Not stable");
            String tag = json.getString("tag_name");
            if (!tag.matches("v[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")) throw new IOException("Invalid version");
            String name = "WorkSandbox-" + tag + ".apk";
            String expected = UpdatePolicy.downloadUrl(tag);
            JSONArray assets = json.getJSONArray("assets");
            boolean found = false;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (name.equals(asset.optString("name"))
                        && expected.equals(asset.optString("browser_download_url"))
                        && "uploaded".equals(asset.optString("state"))
                        && asset.optLong("size") > 0) {
                    found = true;
                    break;
                }
            }
            if (!found) throw new IOException("APK missing");
            String notes = json.optString("body", "");
            if (notes.length() > 4000) notes = notes.substring(0, 4000) + "…";
            // AlertDialog displays plain text, never remote HTML or executable content.
            return new Release(tag.substring(1), notes, expected);
        } finally {
            request.disconnect();
            connection = null;
        }
    }

    private boolean isActive() {
        return !closed && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void resetButton() {
        button.setEnabled(true);
        button.setText(R.string.update_check);
    }

    void close() {
        closed = true;
        HttpsURLConnection active = connection;
        if (active != null) active.disconnect();
        executor.shutdownNow();
    }

    private static final class Release {
        final String version;
        final String notes;
        final String downloadUrl;

        Release(String version, String notes, String downloadUrl) {
            this.version = version;
            this.notes = notes;
            this.downloadUrl = downloadUrl;
        }
    }
}

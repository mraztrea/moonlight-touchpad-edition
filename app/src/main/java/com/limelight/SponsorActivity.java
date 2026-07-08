package com.limelight;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.net.URL;

public class SponsorActivity extends Activity {
    private static final OkHttpClient httpClient = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sponsor);

        Button copyButton = findViewById(R.id.copySponsorNote);
        final EditText deviceModel = findViewById(R.id.sponsorDeviceModel);

        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = deviceModel.getText().toString().trim();
                String note = getString(R.string.sponsor_note_prefix);
                if (!text.isEmpty()) {
                    note += "\n" + text;
                }

                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        getString(R.string.sponsor_note_label), note));
                Toast.makeText(SponsorActivity.this,
                        getString(R.string.sponsor_note_copied), Toast.LENGTH_SHORT).show();
            }
        });

        loadRemoteSponsorCodes();
    }

    private void loadRemoteSponsorCodes() {
        final String configUrl = getString(R.string.sponsor_config_url).trim();
        if (configUrl.isEmpty()) {
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Request request = new Request.Builder().url(configUrl).build();
                    try (Response response = httpClient.newCall(request).execute()) {
                        if (!response.isSuccessful() || response.body() == null) {
                            return;
                        }

                        JSONObject config = new JSONObject(response.body().string());
                        final Bitmap wechat = loadBitmapFromUrl(configUrl,
                                config.optString("wechat_qr_url",
                                        config.optString("wechat", "")));
                        final Bitmap alipay = loadBitmapFromUrl(configUrl,
                                config.optString("alipay_qr_url",
                                        config.optString("alipay", "")));

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                boolean updated = false;
                                ImageView wechatQr = findViewById(R.id.sponsorWechatQr);
                                ImageView alipayQr = findViewById(R.id.sponsorAlipayQr);

                                if (wechat != null) {
                                    wechatQr.setImageBitmap(wechat);
                                    updated = true;
                                }
                                if (alipay != null) {
                                    alipayQr.setImageBitmap(alipay);
                                    updated = true;
                                }
                                if (updated) {
                                    Toast.makeText(SponsorActivity.this,
                                            getString(R.string.sponsor_qr_updated),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                } catch (Exception ignored) {
                    // Keep bundled QR codes if the remote config is unavailable.
                }
            }
        }, "Sponsor QR Loader").start();
    }

    private Bitmap loadBitmapFromUrl(String baseUrl, String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        try {
            String resolvedUrl = new URL(new URL(baseUrl), url.trim()).toString();
            Request request = new Request.Builder().url(resolvedUrl).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }

                return BitmapFactory.decodeStream(response.body().byteStream());
            }
        } catch (Exception e) {
            return null;
        }
    }
}

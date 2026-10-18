package net.kdt.pojavlaunch;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Displays NovaSkin's public community skin gallery inside OrynLauncher Plus.
 * Skins are hosted by NovaSkin; this screen does not claim that tapping a
 * gallery entry automatically applies it to the Minecraft account.
 */
public final class OrynNovaSkinGalleryActivity extends Activity {
    private static final int BG = Color.rgb(8, 13, 22);
    private static final int PANEL = Color.rgb(18, 27, 43);
    private static final int WHITE = Color.rgb(240, 245, 255);
    private WebView webView;
    private ProgressBar progress;
    private EditText searchInput;
    private boolean searchCapes = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(8), dp(14), dp(8));
        header.setBackgroundColor(PANEL);

        Button back = new Button(this);
        back.setText("‹ Back");
        back.setTextColor(WHITE);
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(82), dp(44)));

        ImageView logo = new ImageView(this);
        int logoId = getResources().getIdentifier("oryn_official_logo_v4", "drawable", getPackageName());
        if (logoId != 0) logo.setImageResource(logoId);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView title = new TextView(this);
        title.setText("OrynLauncherPlus");
        title.setTextColor(WHITE);
        title.setTextSize(17);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(10), 0, 0, 0);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(12), dp(7), dp(12), dp(7));
        searchRow.setBackgroundColor(BG);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextColor(WHITE);
        searchInput.setHintTextColor(Color.rgb(150, 165, 188));
        searchInput.setHint(searchCapes ? "Search capes..." : "Search skins...");
        searchInput.setTextSize(14);
        searchInput.setPadding(dp(12), 0, dp(12), 0);
        searchInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 125, 230)));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button typeButton = new Button(this);
        typeButton.setText(searchCapes ? "Capes" : "Skins");
        typeButton.setAllCaps(false);
        typeButton.setTextColor(WHITE);
        typeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35, 83, 155)));
        searchRow.addView(typeButton, new LinearLayout.LayoutParams(dp(76), dp(42)));

        Button searchButton = new Button(this);
        searchButton.setText("Search");
        searchButton.setAllCaps(false);
        searchButton.setTextColor(WHITE);
        searchButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35, 110, 225)));
        LinearLayout.LayoutParams searchButtonLp = new LinearLayout.LayoutParams(dp(90), dp(42));
        searchButtonLp.leftMargin = dp(6);
        searchRow.addView(searchButton, searchButtonLp);
        root.addView(searchRow, new LinearLayout.LayoutParams(-1, dp(56)));

        typeButton.setOnClickListener(v -> {
            searchCapes = !searchCapes;
            typeButton.setText(searchCapes ? "Capes" : "Skins");
            searchInput.setHint(searchCapes ? "Search capes..." : "Search skins...");
            if (searchInput.getText().toString().trim().length() > 0) performSearch();
            else if (webView != null) webView.loadUrl(searchCapes
                    ? "https://minecraft.novaskin.me/tag/cape?lang=en"
                    : "https://minecraft.novaskin.me/tag/gallery?lang=en");
        });
        searchButton.setOnClickListener(v -> performSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            performSearch();
            return true;
        });

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        webView = new WebView(this);
        webView.setBackgroundColor(BG);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !(url.startsWith("https://minecraft.novaskin.me/")
                        || url.startsWith("https://novaskin.me/"));
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        webView.loadUrl(searchCapes
                ? "https://minecraft.novaskin.me/tag/cape?lang=en"
                : "https://minecraft.novaskin.me/tag/gallery?lang=en");
    }

    private void performSearch() {
        if (webView == null || searchInput == null) return;
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            webView.loadUrl(searchCapes
                    ? "https://minecraft.novaskin.me/tag/cape?lang=en"
                    : "https://minecraft.novaskin.me/tag/gallery?lang=en");
            return;
        }
        String searchTerms = searchCapes ? query + " cape" : query;
        webView.loadUrl("https://minecraft.novaskin.me/search?q=" + Uri.encode(searchTerms));
        searchInput.clearFocus();
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

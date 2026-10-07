package com.evacleartradingenterprise.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Evaclear Android app: a fast, secure window onto https://www.evacleartradingenterprise.com.
 *
 * - Pages on the Evaclear website (and Paystack's checkout) stay inside the app.
 * - WhatsApp, phone numbers, email, maps and every other website open in the right app.
 * - Pull down to refresh, a slim loading bar, an offline screen and Android's back gesture.
 */
public class MainActivity extends AppCompatActivity {

    private static final long MAX_SPLASH_MS = 2500;

    private WebView webView;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private View offlineView;

    private String siteHost;
    private String failedUrl;
    private boolean firstPageLoaded = false;
    private long startedAt;

    private ValueCallback<Uri[]> fileCallback;
    private final ActivityResultLauncher<Intent> fileChooser = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (fileCallback == null) return;
                fileCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData()));
                fileCallback = null;
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        startedAt = SystemClock.uptimeMillis();
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        // Keep the splash screen up until the first page has drawn (max 2.5 s).
        splash.setKeepOnScreenCondition(() ->
                !firstPageLoaded && SystemClock.uptimeMillis() - startedAt < MAX_SPLASH_MS);

        // Edge-to-edge (required when targeting Android 15+): brand-coloured bars with light icons.
        EdgeToEdge.enable(this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        siteHost = Uri.parse(BuildConfig.START_URL).getHost();
        webView = findViewById(R.id.webview);
        swipe = findViewById(R.id.swipe);
        progress = findViewById(R.id.progress);
        offlineView = findViewById(R.id.offline);

        applyWindowInsets();
        setUpWebView();
        setUpSwipeToRefresh();
        setUpOfflineScreen();
        setUpBackNavigation();

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            firstPageLoaded = true;
        } else {
            webView.loadUrl(startUrl(getIntent()));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------

    /** Keeps the website clear of the status bar, navigation bar, display cut-outs and keyboard. */
    private void applyWindowInsets() {
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setUpWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);          // the store is a JavaScript app
        s.setDomStorageEnabled(true);          // cart, login session
        s.setSupportMultipleWindows(false);    // target="_blank" links come through shouldOverrideUrlLoading
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        // Lets the website know it is running inside the app.
        s.setUserAgentString(s.getUserAgentString() + " EvaclearApp/" + BuildConfig.VERSION_NAME);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true); // Paystack checkout runs in an iframe

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new EvaclearWebViewClient());
        webView.setWebChromeClient(new EvaclearChromeClient());
    }

    private void setUpSwipeToRefresh() {
        swipe.setColorSchemeResources(R.color.evc_primary);
        // Only refresh when the page is scrolled right to the top.
        swipe.setOnChildScrollUpCallback((parent, child) -> webView.getScrollY() > 0);
        swipe.setOnRefreshListener(() -> {
            if (offlineView.getVisibility() == View.VISIBLE) retry();
            else webView.reload();
        });
    }

    private void setUpOfflineScreen() {
        Button retry = findViewById(R.id.retry);
        retry.setOnClickListener(v -> retry());
        Button call = findViewById(R.id.call);
        call.setOnClickListener(v -> openExternally(Uri.parse("tel:" + getString(R.string.offline_phone))));
    }

    private void setUpBackNavigation() {
        // Works with the Android 13+ predictive back gesture and the classic back button.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (offlineView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
                    hideOffline();
                    webView.goBack();
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    // Leave the app as normal, but keep handling back next time it is opened.
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    // ---------------------------------------------------------------------------------------
    // Links
    // ---------------------------------------------------------------------------------------

    private String startUrl(@Nullable Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null && isInAppUrl(data)) return data.toString();
        }
        return BuildConfig.START_URL;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null && isInAppUrl(intent.getData())) {
            hideOffline();
            webView.loadUrl(intent.getData().toString());
        }
    }

    /** The Evaclear website and Paystack checkout stay inside the app. */
    private boolean isInAppUrl(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || !scheme.equalsIgnoreCase("https")) return false;
        host = host.toLowerCase(Locale.ROOT);
        String bare = siteHost.startsWith("www.") ? siteHost.substring(4) : siteHost;
        return host.equals(siteHost) || host.equals(bare) || host.endsWith("." + bare)
                || host.equals("paystack.com") || host.endsWith(".paystack.com")
                || host.equals("paystack.co") || host.endsWith(".paystack.co");
    }

    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("http") || scheme.equals("https")) {
            if (isInAppUrl(uri)) return false; // let the WebView load it
            openExternally(uri);               // wa.me, maps, social pages, etc.
            return true;
        }
        if (scheme.equals("intent")) {
            try {
                Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
                startActivity(intent);
            } catch (URISyntaxException | ActivityNotFoundException e) {
                String fallback = null;
                try {
                    fallback = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                            .getStringExtra("browser_fallback_url");
                } catch (URISyntaxException ignored) { /* no fallback */ }
                if (fallback != null && !handleUrl(Uri.parse(fallback))) webView.loadUrl(fallback);
                else toast(R.string.no_app_found);
            }
            return true;
        }
        if (scheme.equals("javascript") || scheme.equals("file") || scheme.equals("content") || scheme.equals("data")) {
            return true; // never navigate the app to these
        }
        // tel:, mailto:, whatsapp:, geo:, sms:, market: …
        openExternally(uri);
        return true;
    }

    private void openExternally(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("tel")) intent = new Intent(Intent.ACTION_DIAL, uri);
        else if (scheme.equals("mailto")) intent = new Intent(Intent.ACTION_SENDTO, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast(R.string.no_app_found);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Offline
    // ---------------------------------------------------------------------------------------

    private boolean isOnline() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) return true;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showOffline(String url) {
        failedUrl = url;
        firstPageLoaded = true;
        swipe.setRefreshing(false);
        progress.setVisibility(View.GONE);
        offlineView.setVisibility(View.VISIBLE);
    }

    private void hideOffline() {
        offlineView.setVisibility(View.GONE);
    }

    private void retry() {
        swipe.setRefreshing(false);
        if (!isOnline()) {
            showOffline(failedUrl);
            return;
        }
        hideOffline();
        String url = failedUrl != null ? failedUrl : BuildConfig.START_URL;
        failedUrl = null;
        webView.loadUrl(url);
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------------------------------
    // WebView clients
    // ---------------------------------------------------------------------------------------

    private class EvaclearWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl());
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            firstPageLoaded = true;
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) showOffline(request.getUrl().toString());
        }
    }

    private class EvaclearChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            if (newProgress >= 100) progress.setVisibility(View.GONE);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                fileChooser.launch(Intent.createChooser(params.createIntent(), getString(R.string.choose_file)));
                return true;
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                return false;
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}

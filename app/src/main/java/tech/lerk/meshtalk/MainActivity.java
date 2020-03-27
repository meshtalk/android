package tech.lerk.meshtalk;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.navigation.NavigationView;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;
import java.util.UUID;

import im.delight.android.identicons.Identicon;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.providers.IdentityProvider;
import tech.lerk.meshtalk.workers.DataKeys;
import tech.lerk.meshtalk.workers.GatewayMetaWorker;

import static tech.lerk.meshtalk.Stuff.waitOrDonT;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getCanonicalName();
    private AppBarConfiguration mAppBarConfiguration;
    private SharedPreferences preferences;
    private KeyHolder keyHolder;
    private NavController navController;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_item_chats, R.id.nav_item_contacts, R.id.nav_item_settings)
                .setDrawerLayout(drawer)
                .build();
        navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        keyHolder = KeyHolder.get(getApplicationContext());
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean firstStart = preferences.getBoolean(Preferences.FIRST_START.toString(), true);
        if (firstStart) {
            handleFirstStart();
        } else {
            handleNoIdentities();
        }
    }

    public void updateNavHeader() {
        updateNavHeaderIdentity();
        updateNavHeaderConnectionState();
    }

    private void updateNavHeaderConnectionState() {
        OneTimeWorkRequest fetchMetaWorkRequest = new OneTimeWorkRequest.Builder(GatewayMetaWorker.class).build();
        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueue(fetchMetaWorkRequest);

        TextView connectionText = navigationView.getHeaderView(0).findViewById(R.id.nav_header_connection_text);
        ImageView connectionIcon = navigationView.getHeaderView(0).findViewById(R.id.nav_header_connection_img);

        connectionText.setText(R.string.nav_header_connection_connecting);
        connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_refresh_black_16dp));
        setImageViewTint(connectionIcon, getColor(R.color.yellow));

        workManager.getWorkInfoByIdLiveData(fetchMetaWorkRequest.getId())
                .observe(this, info -> {
                    if (info != null && info.getState().isFinished()) {
                        Data data = info.getOutputData();
                        switch (data.getInt(DataKeys.ERROR_CODE.toString(), -1)) {
                            case GatewayMetaWorker.ERROR_INVALID_SETTINGS:
                                connectionText.setText(R.string.nav_header_connection_error_settings);
                                connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                setImageViewTint(connectionIcon, getColor(R.color.red));
                                break;
                            case GatewayMetaWorker.ERROR_NONE:
                                connectionText.setText(R.string.nav_header_connection_established);
                                connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_check_circle_black_16dp));
                                setImageViewTint(connectionIcon, getColor(R.color.yellow));
                                if (data.getInt(DataKeys.API_VERSION.toString(), 0) != Meta.API_VERSION) {
                                    connectionText.setText(R.string.nav_header_connection_error_api_version);
                                    connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                    setImageViewTint(connectionIcon, getColor(R.color.red));
                                } else if (!Meta.CORE_VERSION.equals(data.getString(DataKeys.CORE_VERSION.toString()))) {
                                    connectionText.setText(R.string.nav_header_connection_error_core_version);
                                    connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_check_circle_black_16dp));
                                    setImageViewTint(connectionIcon, getColor(R.color.yellow));
                                }
                                break;
                            case GatewayMetaWorker.ERROR_URI:
                                connectionText.setText(R.string.nav_header_connection_error_uri);
                                connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                setImageViewTint(connectionIcon, getColor(R.color.red));
                                break;
                            case GatewayMetaWorker.ERROR_CONNECTION:
                                connectionText.setText(R.string.nav_header_connection_error_connection);
                                connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                setImageViewTint(connectionIcon, getColor(R.color.red));
                                break;
                            case GatewayMetaWorker.ERROR_PARSING:
                                connectionText.setText(R.string.nav_header_connection_error_parsing);
                                connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                setImageViewTint(connectionIcon, getColor(R.color.red));
                                break;
                            default:
                                break;
                        }
                    }
                });
    }

    private void setImageViewTint(ImageView imageView, @ColorInt int color) {
        imageView.setImageTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_enabled},
                new int[]{android.R.attr.state_checked},
                new int[]{android.R.attr.state_pressed},
                new int[]{-android.R.attr.state_enabled},
                new int[]{-android.R.attr.state_checked},
                new int[]{-android.R.attr.state_pressed}
        }, new int[]{color, color, color, color, color, color}));
    }

    private void updateNavHeaderIdentity() {
        String defaultIdentityId = preferences.getString(Preferences.DEFAULT_IDENTITY.toString(), "");
        try {
            Identity defaultIdentity = null;
            if (!defaultIdentityId.equals("")) {
                defaultIdentity = IdentityProvider.get(this).getById(UUID.fromString(defaultIdentityId));
            }
            Identicon identicon = navigationView.getHeaderView(0).findViewById(R.id.nav_header_identicon);
            TextView name = navigationView.getHeaderView(0).findViewById(R.id.nav_header_title);
            TextView publicKey = navigationView.getHeaderView(0).findViewById(R.id.nav_header_subtitle);
            if (defaultIdentity != null) {
                identicon.show(defaultIdentity.getId());
                name.setText(defaultIdentity.getName());
                publicKey.setText(Stuff.getFingerprintForKey((RSAPublicKey) defaultIdentity.getPublicKey()));
            } else {
                identicon.show("");
                name.setText(R.string.nav_header_title);
                publicKey.setText(R.string.nav_header_subtitle);
            }
        } catch (DecryptionException e) {
            Log.w(TAG, "Error getting default identity!", e);
        }
    }

    private void handleNoIdentities() {
        if (IdentityProvider.get(getApplicationContext()).getAllIds().size() < 1) {
            navController.navigate(R.id.nav_item_identities);
        } else {
            updateNavHeader();
        }
    }

    private void handleFirstStart() {
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.first_start_dialog_title)
                .setCancelable(false)
                .setView(R.layout.dialog_loading)
                .create();
        loadingDialog.show();
        ProgressBar progressBar = Objects.requireNonNull(loadingDialog.findViewById(R.id.loading_spinner));
        TextView loadingTextView = Objects.requireNonNull(loadingDialog.findViewById(R.id.loading_text));
        AsyncTask.execute(() -> {
            MainActivity.this.runOnUiThread(() -> {
                progressBar.setProgress(1, true);
                loadingTextView.setText(R.string.progress_init_loading_keystore);
            });
            waitOrDonT(200);
            if (!keyHolder.keyExists(Stuff.APP_KEY_ALIAS)) {
                MainActivity.this.runOnUiThread(() -> {
                    progressBar.setProgress(1, true);
                    loadingTextView.setText(R.string.progress_init_generating_app_key);
                });
                waitOrDonT(200);
                keyHolder.generateAppKey();
            } else {
                progressBar.setProgress(25, true);
            }

            if (preferences.getString(Preferences.DEVICE_IV.toString(), Stuff.NONE).equals(Stuff.NONE)) {
                MainActivity.this.runOnUiThread(() -> {
                    progressBar.setProgress(1, true);
                    loadingTextView.setText(R.string.progress_init_generating_iv);
                });
                waitOrDonT(200);
                byte[] array = new byte[12];
                new SecureRandom().nextBytes(array);
                preferences.edit()
                        .putString(Preferences.DEVICE_IV.toString(), new String(array, StandardCharsets.UTF_8))
                        .apply();
            }
            MainActivity.this.runOnUiThread(() -> {
                progressBar.setProgress(1, true);
                loadingTextView.setText(R.string.progress_init_finishing);
            });
            waitOrDonT(200);
            preferences.edit().putBoolean(Preferences.FIRST_START.toString(), false).apply();
            MainActivity.this.runOnUiThread(() -> {
                loadingDialog.dismiss();
                handleNoIdentities();
            });
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    public NavController getNavController() {
        return navController;
    }
}

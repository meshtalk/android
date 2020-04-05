package tech.lerk.meshtalk;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
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
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.providers.impl.IdentityProvider;
import tech.lerk.meshtalk.ui.UpdatableFragment;
import tech.lerk.meshtalk.workers.DataKeys;
import tech.lerk.meshtalk.workers.GatewayMetaWorker;

import static tech.lerk.meshtalk.Stuff.waitOrDonT;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getCanonicalName();
    private static MainActivity instance;
    private AppBarConfiguration mAppBarConfiguration;
    private SharedPreferences preferences;
    private KeyHolder keyHolder;
    private NavController navController;
    private NavigationView navigationView;

    public static void maybeUpdateViews() {
        if (instance != null) {
            instance.runOnUiThread(() -> {
                Fragment fragmentById = instance.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                if (fragmentById != null) {
                    Fragment primaryNavigationFragment = fragmentById.getChildFragmentManager().getPrimaryNavigationFragment();
                    if (primaryNavigationFragment instanceof UpdatableFragment) {
                        ((UpdatableFragment) primaryNavigationFragment).updateViews();
                    }
                }
            });
        }
    }

    public static void maybeRunOnUiThread(Runnable r) {
        if (instance != null) {
            instance.runOnUiThread(r);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
    }

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

        instance = this;
        keyHolder = KeyHolder.get(getApplicationContext());
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        boolean firstStart = preferences.getBoolean(Preferences.FIRST_START.toString(), true);
        if (firstStart) {
            handleFirstStart();
        } else {
            noIdentitiesCheckpoint();
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
        RelativeLayout headerView = navigationView.getHeaderView(0).findViewById(R.id.nav_header_view);

        connectionText.setText(R.string.nav_header_connection_connecting);
        connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_refresh_black_16dp));
        setImageViewTint(connectionIcon, getColor(R.color.yellow));
        headerView.setBackground(getDrawable(R.drawable.side_nav_bar_warn));

        workManager.getWorkInfoByIdLiveData(fetchMetaWorkRequest.getId())
                .observe(this, info -> {
                    if (info != null && info.getState().isFinished()) {
                        Data data = info.getOutputData();
                        switch (data.getInt(DataKeys.ERROR_CODE.toString(), -1)) {
                            case GatewayMetaWorker.ERROR_INVALID_SETTINGS:
                                connectionText.setText(R.string.nav_header_connection_error_settings);
                                connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                setImageViewTint(connectionIcon, getColor(R.color.red));
                                headerView.setBackground(getDrawable(R.drawable.side_nav_bar_error));
                                break;
                            case GatewayMetaWorker.ERROR_NONE:
                                connectionText.setText(R.string.nav_header_connection_established);
                                connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_check_circle_black_16dp));
                                if (preferences.getString(Preferences.MESSAGE_GATEWAY_PROTOCOL.toString(), "http").equals("https")) {
                                    setImageViewTint(connectionIcon, getColor(R.color.green));
                                    headerView.setBackground(getDrawable(R.drawable.side_nav_bar_success));
                                } else {
                                    setImageViewTint(connectionIcon, getColor(R.color.yellow));
                                    headerView.setBackground(getDrawable(R.drawable.side_nav_bar_warn));
                                }
                                if (apiVersionMismatch(data)) {
                                    connectionText.setText(R.string.nav_header_connection_error_api_version);
                                    connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                    setImageViewTint(connectionIcon, getColor(R.color.red));
                                    headerView.setBackground(getDrawable(R.drawable.side_nav_bar_error));
                                } else if (coreVersionMismatch(data)) {
                                    connectionText.setText(R.string.nav_header_connection_error_core_version);
                                    connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_check_circle_black_16dp));
                                    setImageViewTint(connectionIcon, getColor(R.color.yellow));
                                    headerView.setBackground(getDrawable(R.drawable.side_nav_bar_warn));
                                }
                                break;
                            case GatewayMetaWorker.ERROR_URI:
                                connectionText.setText(R.string.nav_header_connection_error_uri);
                                connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                setImageViewTint(connectionIcon, getColor(R.color.red));
                                headerView.setBackground(getDrawable(R.drawable.side_nav_bar_error));
                                break;
                            case GatewayMetaWorker.ERROR_CONNECTION:
                                connectionText.setText(R.string.nav_header_connection_error_connection);
                                connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                setImageViewTint(connectionIcon, getColor(R.color.red));
                                headerView.setBackground(getDrawable(R.drawable.side_nav_bar_error));
                                break;
                            case GatewayMetaWorker.ERROR_PARSING:
                                connectionText.setText(R.string.nav_header_connection_error_parsing);
                                connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                setImageViewTint(connectionIcon, getColor(R.color.red));
                                headerView.setBackground(getDrawable(R.drawable.side_nav_bar_error));
                                break;
                            default:
                                break;
                        }
                    }
                });
    }

    private boolean apiVersionMismatch(Data data) {
        return data.getInt(DataKeys.API_VERSION.toString(), 0) != Meta.API_VERSION;
    }

    private boolean coreVersionMismatch(Data data) {
        String gatewayCoreVersion = data.getString(DataKeys.CORE_VERSION.toString());
        return !Meta.CORE_VERSION.equals(gatewayCoreVersion);
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
        if (!defaultIdentityId.equals("")) {
            AsyncTask.execute(() -> {
                try {
                    IdentityProvider.get(this).getById(UUID.fromString(defaultIdentityId), defaultIdentity ->
                            MainActivity.this.runOnUiThread(() -> {
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
                            }));
                } catch (DecryptionException e) {
                    Log.w(TAG, "Error getting default identity!", e);
                }
            });
        }
    }

    private void noIdentitiesCheckpoint() {
        AppExecutors.getInstance().diskIO().execute(() ->
                IdentityProvider.get(getApplicationContext()).getAll(ids ->
                        MainActivity.this.runOnUiThread(() -> {
                            if (ids.size() < 1) {
                                navController.navigate(R.id.nav_item_identities);
                            } else {
                                updateNavHeader();
                                startWorkers();
                            }
                        })));
    }

    public void startWorkers() {
        if (preferences.getString(Preferences.DEFAULT_IDENTITY.toString(), null) != null) {
            startService(new Intent(getApplicationContext(), MessagesService.class));
        } else {
            Toast.makeText(getApplicationContext(), R.string.info_fetching_messages_no_default_identity, Toast.LENGTH_LONG).show();
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
                noIdentitiesCheckpoint();
            });
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            navController.navigate(R.id.nav_item_settings);
            return true;
        } else if (item.getItemId() == R.id.action_refresh) {
            startWorkers();
        }
        return super.onOptionsItemSelected(item);
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

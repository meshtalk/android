package tech.lerk.meshtalk;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;

import tech.lerk.meshtalk.entities.Preferences;

import static tech.lerk.meshtalk.Stuff.waitOrDonT;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getCanonicalName();
    private AppBarConfiguration mAppBarConfiguration;
    private SharedPreferences preferences;
    private KeyHolder keyHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_item_chats, R.id.nav_item_contacts, R.id.nav_item_settings)
                .setDrawerLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        keyHolder = KeyHolder.get(getApplicationContext());
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean firstStart = preferences.getBoolean(Preferences.FIRST_START.toString(), true);
        if (firstStart) {
            handleFirstStart();
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
            MainActivity.this.runOnUiThread(loadingDialog::dismiss);
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
}

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
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
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
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.navigation.NavigationView;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.sql.Time;
import java.util.Base64;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import im.delight.android.identicons.Identicon;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.entities.Handshake;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.providers.ChatProvider;
import tech.lerk.meshtalk.providers.ContactProvider;
import tech.lerk.meshtalk.providers.IdentityProvider;
import tech.lerk.meshtalk.providers.MessageProvider;
import tech.lerk.meshtalk.workers.DataKeys;
import tech.lerk.meshtalk.workers.FetchHandshakesWorker;
import tech.lerk.meshtalk.workers.FetchMessagesWorker;
import tech.lerk.meshtalk.workers.GatewayMetaWorker;
import tech.lerk.meshtalk.workers.SubmitHandshakeWorker;

import static tech.lerk.meshtalk.Stuff.waitOrDonT;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getCanonicalName();
    private static final String FETCH_MESSAGES_TAG = "mt.fetch_messages";
    private static final String FETCH_HANDSHAKES_TAG = "mt.fetch_handshakes";
    private AppBarConfiguration mAppBarConfiguration;
    private SharedPreferences preferences;
    private KeyHolder keyHolder;
    private NavController navController;
    private NavigationView navigationView;
    private Gson gson;
    private ChatProvider chatProvider;
    private ContactProvider contactProvider;
    private IdentityProvider identityProvider;

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

        gson = Utils.getGson();
        keyHolder = KeyHolder.get(getApplicationContext());
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        chatProvider = ChatProvider.get(getApplicationContext());
        contactProvider = ContactProvider.get(getApplicationContext());
        identityProvider = IdentityProvider.get(getApplicationContext());

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
                                if (preferences.getString(Preferences.MESSAGE_GATEWAY_PROTOCOL.toString(), "http").equals("https")) {
                                    setImageViewTint(connectionIcon, getColor(R.color.green));
                                } else {
                                    setImageViewTint(connectionIcon, getColor(R.color.yellow));
                                }
                                if (apiVersionMismatch(data)) {
                                    connectionText.setText(R.string.nav_header_connection_error_api_version);
                                    connectionIcon.setImageDrawable(getDrawable(R.drawable.ic_error_black_16dp));
                                    setImageViewTint(connectionIcon, getColor(R.color.red));
                                } else if (coreVersionMismatch(data)) {
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

    private boolean apiVersionMismatch(Data data) {
        return data.getInt(DataKeys.API_VERSION.toString(), 0) != Meta.API_VERSION;
    }

    private boolean coreVersionMismatch(Data data) {
        String localCoreVersion = AndroidMeta.getCoreVersion();
        String gatewayCoreVersion = data.getString(DataKeys.CORE_VERSION.toString());
        return !localCoreVersion.equals(gatewayCoreVersion);
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

    private void noIdentitiesCheckpoint() {
        if (IdentityProvider.get(getApplicationContext()).getAllIds().size() < 1) {
            navController.navigate(R.id.nav_item_identities);
        } else {
            updateNavHeader();
            startWorkers();
        }
    }

    public void startWorkers() {
        String defaultIdentity = preferences.getString(Preferences.DEFAULT_IDENTITY.toString(), null);
        if (defaultIdentity != null) {
            startFetchMessageWorker(defaultIdentity);
            startFetchHandshakeWorker(defaultIdentity);
        } else {
            Toast.makeText(getApplicationContext(), R.string.info_fetching_messages_no_default_identity, Toast.LENGTH_LONG).show();
        }
    }

    private void startFetchHandshakeWorker(String defaultIdentity) {
        PeriodicWorkRequest fetchHandshakesRequest = new PeriodicWorkRequest
                .Builder(FetchHandshakesWorker.class, PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setInitialDelay(100, TimeUnit.MILLISECONDS)
                .build();
        WorkManager instance = WorkManager.getInstance(this);
        instance.enqueueUniquePeriodicWork(FETCH_HANDSHAKES_TAG, ExistingPeriodicWorkPolicy.REPLACE, fetchHandshakesRequest);
        instance.getWorkInfoByIdLiveData(fetchHandshakesRequest.getId()).observe(this, info -> {
            if (info != null && info.getState().isFinished()) {
                Data data = info.getOutputData();
                int errorCode = data.getInt(DataKeys.ERROR_CODE.toString(), -1);
                switch (errorCode) {
                    case FetchHandshakesWorker.ERROR_INVALID_SETTINGS:
                    case FetchHandshakesWorker.ERROR_URI:
                    case FetchHandshakesWorker.ERROR_CONNECTION:
                    case FetchHandshakesWorker.ERROR_PARSING:
                        String msg = "Got error " + errorCode + " while fetching handshakes!";
                        Log.e(TAG, msg);
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                        break;
                    case FetchHandshakesWorker.ERROR_NOT_FOUND:
                        Log.i(TAG, "No handshakes found on gateway.");
                        break;
                    case FetchHandshakesWorker.ERROR_NONE:
                        AsyncTask.execute(() -> {
                            for (int i = 0; i < data.getInt(DataKeys.HANDSHAKE_LIST_SIZE.toString(), 0); i++) {
                                Handshake handshake = gson.fromJson(data.getString(DataKeys.HANDSHAKE_LIST_ELEMENT_PREFIX + String.valueOf(i)), Handshake.class);
                                handleHandshake(defaultIdentity, handshake);
                            }
                        });
                        break;
                    default:
                        Log.wtf(TAG, "Invalid error code: " + errorCode + "!");
                        break;
                }
            }
        });
    }

    private void startFetchMessageWorker(String defaultIdentity) {
        PeriodicWorkRequest fetchMessagesRequest = new PeriodicWorkRequest
                .Builder(FetchMessagesWorker.class, PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setInitialDelay(100, TimeUnit.MILLISECONDS)
                .build();
        WorkManager instance = WorkManager.getInstance(this);
        instance.enqueueUniquePeriodicWork(FETCH_MESSAGES_TAG, ExistingPeriodicWorkPolicy.REPLACE, fetchMessagesRequest);
        instance.getWorkInfoByIdLiveData(fetchMessagesRequest.getId()).observe(this, info -> {
            if (info != null && info.getState().isFinished()) {
                Data data = info.getOutputData();
                int errorCode = data.getInt(DataKeys.ERROR_CODE.toString(), -1);
                switch (errorCode) {
                    case FetchMessagesWorker.ERROR_INVALID_SETTINGS:
                    case FetchMessagesWorker.ERROR_URI:
                    case FetchMessagesWorker.ERROR_CONNECTION:
                    case FetchMessagesWorker.ERROR_PARSING:
                        String msg = "Got error " + errorCode + " while fetching messages!";
                        Log.e(TAG, msg);
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                        break;
                    case FetchMessagesWorker.ERROR_NOT_FOUND:
                        Log.i(TAG, "No messages found on gateway.");
                        break;
                    case FetchMessagesWorker.ERROR_NONE:
                        AsyncTask.execute(() -> {
                            for (int i = 0; i < data.getInt(DataKeys.MESSAGE_LIST_SIZE.toString(), 0); i++) {
                                Message message = gson.fromJson(data.getString(DataKeys.MESSAGE_LIST_ELEMENT_PREFIX + String.valueOf(i)), Message.class);
                                MessageProvider.get(getApplicationContext()).save(message);
                            }
                        });
                        break;
                    default:
                        Log.wtf(TAG, "Invalid error code: " + errorCode + "!");
                        break;
                }
            }
        });
    }

    private void handleHandshake(String defaultIdentity, Handshake handshake) {
        Log.i(TAG, "Handshake received from: '" + handshake.getSender() + "'!");
        Chat chat = chatProvider.getById(handshake.getId());
        if (chat == null) {
            // new chat "request"
            //TODO add ability to decline a chat (?)
            chat = new Chat();
            chat.setId(handshake.getChat());
            chat.setRecipient(handshake.getSender());
            chat.setSender(handshake.getReceiver());
            chat.setTitle(getChatName(handshake.getSender()));
            chat.setHandshake(new HashMap<>());
            chat.setMessages(new TreeSet<>());
        }
        HashMap<UUID, Handshake> handshakes = chat.getHandshake();
        handshakes.put(handshake.getReceiver(), handshake);
        if (handshakes.get(chat.getSender()) == null) { // if we haven't sent a handshake yet...
            replyToHandshake(defaultIdentity, handshake, chat, handshakes);
        }
        chat.setHandshake(handshakes);
        chatProvider.save(chat);
    }

    private void replyToHandshake(String defaultIdentity, Handshake handshake, Chat chat, HashMap<UUID, Handshake> handshakes) {
        try {
            Identity handshakeIdentity = identityProvider.getById(UUID.fromString(defaultIdentity));
            if (handshakeIdentity != null) {
                SecretKey secretKey = getSecretKey(handshake, handshakeIdentity);
                if (secretKey != null) {
                    Cipher cipher = Cipher.getInstance("RSA");
                    Contact contactById = contactProvider.getById(handshake.getSender());
                    if (contactById != null) {
                        cipher.init(Cipher.ENCRYPT_MODE, contactById.getPublicKey());

                        Handshake handshakeReply = new Handshake();
                        handshakeReply.setId(UUID.randomUUID());
                        handshakeReply.setChat(chat.getId());
                        handshakeReply.setReceiver(chat.getRecipient());
                        handshakeReply.setSender(chat.getSender());
                        handshakeReply.setKey(Base64.getMimeEncoder().encodeToString(cipher.doFinal(secretKey.getEncoded())));
                        handshakeReply.setDate(new Time(System.currentTimeMillis()));

                        handshakes.put(chat.getRecipient(), handshakeReply);

                        Data handshakeData = new Data.Builder()
                                .putString(DataKeys.HANDSHAKE_ID.toString(), handshake.getId().toString())
                                .putString(DataKeys.HANDSHAKE_CHAT.toString(), handshake.getChat().toString())
                                .putString(DataKeys.HANDSHAKE_SENDER.toString(), handshake.getSender().toString())
                                .putString(DataKeys.HANDSHAKE_RECEIVER.toString(), handshake.getReceiver().toString())
                                .putString(DataKeys.HANDSHAKE_DATE.toString(), handshake.getDate().toString())
                                .putString(DataKeys.HANDSHAKE_KEY.toString(), handshake.getKey())
                                .build();

                        OneTimeWorkRequest sendHandshakeWorkRequest = new OneTimeWorkRequest.Builder(SubmitHandshakeWorker.class)
                                .setInputData(handshakeData).build();
                        WorkManager workManager = WorkManager.getInstance(MainActivity.this);
                        workManager.enqueue(sendHandshakeWorkRequest);

                        runOnUiThread(() ->
                                workManager.getWorkInfoByIdLiveData(sendHandshakeWorkRequest.getId()).observe(MainActivity.this, info -> {
                                    if (info != null && info.getState().isFinished()) {
                                        Data data = info.getOutputData();
                                        int errorCode = data.getInt(DataKeys.ERROR_CODE.toString(), -1);
                                        switch (errorCode) {
                                            case SubmitHandshakeWorker.ERROR_INVALID_SETTINGS:
                                            case SubmitHandshakeWorker.ERROR_URI:
                                            case SubmitHandshakeWorker.ERROR_CONNECTION:
                                            case SubmitHandshakeWorker.ERROR_PARSING:
                                                String msg = "Got error " + errorCode + " while sending handshake!";
                                                Log.e(TAG, msg);
                                                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                                                break;
                                            case SubmitHandshakeWorker.ERROR_NONE:
                                            default:
                                                Toast.makeText(getApplicationContext(), R.string.success_sending_handshake, Toast.LENGTH_LONG).show();
                                                break;
                                        }
                                    }
                                })
                        );
                    } else {
                        Log.e(TAG, "Contact is null!");
                        MainActivity.this.runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                                R.string.error_unknown_contact, Toast.LENGTH_LONG).show());
                    }
                } else {
                    String msg = "Secret key is null!";
                    Log.e(TAG, msg);
                    MainActivity.this.runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                            msg, Toast.LENGTH_LONG).show());
                }
            } else {
                String msg = "Identity is null!";
                Log.e(TAG, msg);
                MainActivity.this.runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                        msg, Toast.LENGTH_LONG).show());
            }
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "Unable to get KeyGenerator!", e);
        } catch (NoSuchPaddingException e) {
            Log.wtf(TAG, "Unable to get Cipher!", e);
        } catch (InvalidKeyException e) {
            Log.e(TAG, "Unable to init Cipher!", e);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "Unable to encrypt handshake!", e);
        } catch (DecryptionException e) {
            Log.e(TAG, "Unable to decrypt identity!", e);
        }
    }

    @Nullable
    private SecretKey getSecretKey(Handshake handshake, Identity identity) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, identity.getPrivateKey());
            byte[] decryptedKey = cipher.doFinal(Base64.getMimeDecoder().decode(handshake.getKey()));
            return new SecretKeySpec(decryptedKey, "AES");
        } catch (NoSuchAlgorithmException | InvalidKeyException |
                NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "Unable to decrypt handshake '" + handshake.getId() + "' with identity '" + identity.getId() + "'");
        }
        return null;
    }

    private String getChatName(UUID sender) {
        String title = "Chat with ";
        Contact contact = contactProvider.getById(sender);
        if (contact == null) {
            return title + sender;
        } else {
            return title + contact.getName();
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

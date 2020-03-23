package tech.lerk.meshtalk.ui.identities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import net.glxn.qrgen.android.QRCode;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import im.delight.android.identicons.Identicon;
import tech.lerk.meshtalk.MainActivity;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.exceptions.EncryptionException;
import tech.lerk.meshtalk.providers.ContactProvider;
import tech.lerk.meshtalk.providers.IdentityProvider;

public class IdentitiesFragment extends Fragment {

    private static final String TAG = IdentitiesFragment.class.getCanonicalName();
    private IdentitiesViewModel identitiesViewModel;
    private IdentityProvider identityProvider;
    private SharedPreferences preferences;
    private ContactProvider contactProvider;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        identitiesViewModel = ViewModelProviders.of(this).get(IdentitiesViewModel.class);
        identityProvider = IdentityProvider.get(requireContext());
        contactProvider = ContactProvider.get(requireContext());
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());

        View root = inflater.inflate(R.layout.fragment_identities, container, false);

        final ListView listView = root.findViewById(R.id.identity_list_view);
        identitiesViewModel.getIdentities().observe(getViewLifecycleOwner(), identities -> {
            if (identities.size() > 0) {
                root.findViewById(R.id.identity_list_empty).setVisibility(View.INVISIBLE);
                ((MainActivity) requireActivity()).updateNavHeader();
            } else {
                root.findViewById(R.id.identity_list_empty).setVisibility(View.VISIBLE);
            }
            ArrayAdapter<Identity> adapter = new ArrayAdapter<Identity>(requireContext(), R.layout.list_item_identity, new ArrayList<>(identities)) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = null;
                    if (convertView != null) {
                        v = convertView;
                    }
                    if (v == null) {
                        v = View.inflate(requireContext(), R.layout.list_item_identity, null);
                    }
                    Identicon identicon = v.findViewById(R.id.list_item_identity_identicon);
                    TextView name = v.findViewById(R.id.list_item_identity_name_label);
                    TextView publicKey = v.findViewById(R.id.list_item_identity_public_key_label);
                    ImageButton defaultIdentityButton = v.findViewById(R.id.list_item_identity_default);

                    Identity identity = this.getItem(position);
                    if (identity != null) {
                        v.setOnLongClickListener(v12 -> {
                            new AlertDialog.Builder(requireContext())
                                    .setMessage(R.string.dialog_identity_copy_message)
                                    .setPositiveButton(R.string.action_show_qr_code, (d, w) -> {
                                        d.dismiss();
                                        AlertDialog qrDialog = new AlertDialog.Builder(requireContext())
                                                .setPositiveButton(R.string.action_okay, (d1, w1) -> d1.dismiss())
                                                .setView(R.layout.dialog_qr_code)
                                                .setTitle(R.string.dialog_qr_code_title)
                                                .create();
                                        qrDialog.show();
                                        ImageView qrCodeView = qrDialog.findViewById(R.id.dialog_qr_code_image);
                                        String asShareableJSON = contactProvider.getAsShareableJSON(identity);
                                        qrCodeView.setImageBitmap(QRCode.from(asShareableJSON)
                                                .withSize(qrCodeView.getWidth(), qrCodeView.getHeight()).bitmap());
                                    })
                                    .setNeutralButton(R.string.action_copy_contact_info, (d, w) -> {
                                        ClipboardManager clipboard = Objects.requireNonNull((ClipboardManager)
                                                requireContext().getSystemService(Context.CLIPBOARD_SERVICE));
                                        String clipboardLabel = getString(R.string.action_copy_details_pre) + identity.getName();
                                        String encodedPK = Base64.encodeToString(identity.getPublicKey().getEncoded(), Base64.DEFAULT);
                                        String clipboardText = "ID: '" + identity.getId().toString() +
                                                "'. Public Key: '" + encodedPK;
                                        ClipData clip = ClipData.newPlainText(clipboardLabel, clipboardText);
                                        clipboard.setPrimaryClip(clip);
                                        d.dismiss();
                                        String toastMessage = getString(R.string.action_copy_details_pre) +
                                                identity.getName() + getString(R.string.action_copy_details_post);
                                        Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_LONG).show();
                                    })
                                    .setNegativeButton(R.string.action_cancel, (d, w) -> d.dismiss())
                                    .create().show();
                            return true;
                        });
                        String defaultIdentity = preferences.getString(Preferences.DEFAULT_IDENTITY.toString(), "");
                        if (defaultIdentity.equals(identity.getId().toString())) {
                            defaultIdentityButton.setImageDrawable(requireContext().getDrawable(R.drawable.ic_star_black_48dp));
                            defaultIdentityButton.setOnClickListener(v1 -> new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.dialog_unset_default_identity_title)
                                    .setMessage(R.string.dialog_unset_default_identity_message)
                                    .setPositiveButton(R.string.action_yes, (d, w) -> handleUnsetDefault(d))
                                    .setNegativeButton(R.string.action_no, (d, w) -> d.dismiss())
                                    .create().show());
                        } else {
                            defaultIdentityButton.setOnClickListener(v1 -> new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.dialog_set_default_identity_title)
                                    .setMessage(R.string.dialog_set_default_identity_message)
                                    .setPositiveButton(R.string.action_yes, (d, w) -> handleSetDefault(d, identity.getId()))
                                    .setNegativeButton(R.string.action_no, (d, w) -> d.dismiss())
                                    .create().show());
                        }
                        identicon.show(identity.getId().toString());
                        name.setText(identity.getName());
                        publicKey.setText(Stuff.getFingerprintForKey((RSAPublicKey) identity.getPublicKey()));
                    }
                    return v;
                }
            };
            listView.setAdapter(adapter);
        });
        updateIdentities();

        FloatingActionButton fab = root.findViewById(R.id.new_identity_button);
        fab.setOnClickListener(view -> handleActionButtonClick());

        if (IdentityProvider.get(requireContext()).getAllIds().size() < 1) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_no_identities_title)
                    .setMessage(R.string.dialog_no_identities_message)
                    .setNegativeButton(R.string.action_no, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_yes, (d, w) -> {
                        d.dismiss();
                        handleActionButtonClick();
                    }).create().show();
        }

        return root;
    }

    private void handleUnsetDefault(DialogInterface d) {
        d.dismiss();
        preferences.edit().putString(Preferences.DEFAULT_IDENTITY.toString(), "").apply();
        updateIdentities();
    }

    private void handleSetDefault(DialogInterface d, UUID id) {
        d.dismiss();
        preferences.edit().putString(Preferences.DEFAULT_IDENTITY.toString(), id.toString()).apply();
        updateIdentities();
    }

    private void updateIdentities() {
        Set<Identity> identities = new TreeSet<>();
        identityProvider.getAllIds().forEach(id -> {
            try {
                identities.add(identityProvider.getById(id));
            } catch (DecryptionException e) {
                String msg = "Unable to decrypt identity: '" + id + "'!";
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                Log.e(TAG, msg, e);
            }
        });
        identitiesViewModel.setIdentities(identities);
    }

    public void handleActionButtonClick() {
        UUID newUUID = UUID.randomUUID();
        AlertDialog newIdentityDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_new_identity_title)
                .setView(R.layout.dialog_new_identity)
                .setPositiveButton(R.string.action_save, (d, w) -> handleSave(d, newUUID))
                .setNegativeButton(R.string.action_cancel, (d, w) -> d.dismiss())
                .create();
        newIdentityDialog.show();
        Identicon identicon = newIdentityDialog.findViewById(R.id.dialog_new_identity_identicon);
        EditText identityName = newIdentityDialog.findViewById(R.id.dialog_new_identity_identity_name);
        identityName.setImeOptions(EditorInfo.IME_ACTION_DONE);
        identityName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                identicon.show(newUUID.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        identicon.show(newUUID.toString());
    }

    private void handleSave(DialogInterface d, UUID id) {
        String identityName = ((EditText) ((AlertDialog) d).findViewById(R.id.dialog_new_identity_identity_name)).getText().toString();
        d.dismiss();
        AsyncTask.execute(() -> {
            AtomicReference<AlertDialog> loadingDialog = new AtomicReference<>();
            requireActivity().runOnUiThread(() -> {
                loadingDialog.set(new AlertDialog.Builder(requireContext())
                        .setView(R.layout.dialog_loading)
                        .setTitle(R.string.dialog_saving_title)
                        .setCancelable(false).create());
                loadingDialog.get().show();
                TextView loadingText = loadingDialog.get().findViewById(R.id.loading_text);
                loadingText.setText(R.string.dialog_saving_creating_identity);
            });
            Stuff.waitOrDonT(200);
            try {
                Identity newIdentity = new Identity();
                newIdentity.setId(id);
                newIdentity.setName(identityName);
                newIdentity.setChats(new ArrayList<>());

                requireActivity().runOnUiThread(() -> {
                    TextView loadingText = loadingDialog.get().findViewById(R.id.loading_text);
                    loadingText.setText(R.string.dialog_saving_generating_keys);
                });
                Stuff.waitOrDonT(200);

                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(4096);
                KeyPair keyPair = keyGen.genKeyPair();

                requireActivity().runOnUiThread(() -> {
                    TextView loadingText = loadingDialog.get().findViewById(R.id.loading_text);
                    loadingText.setText(R.string.dialog_saving_saving_identity);
                });
                Stuff.waitOrDonT(200);

                newIdentity.setPrivateKey(keyPair.getPrivate());
                newIdentity.setPublicKey(keyPair.getPublic());
                IdentityProvider.get(requireContext()).save(newIdentity);

                requireActivity().runOnUiThread(() -> {
                    loadingDialog.get().dismiss();
                    updateIdentities();
                });
            } catch (NoSuchAlgorithmException | EncryptionException e) {
                requireActivity().runOnUiThread(() -> loadingDialog.get().dismiss());
                Log.e(TAG, "Unable to create new identity!", e);
            }
        });
    }
}

package tech.lerk.meshtalk.ui.identities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import net.glxn.qrgen.android.QRCode;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import im.delight.android.identicons.Identicon;
import tech.lerk.meshtalk.KeyHolder;
import tech.lerk.meshtalk.MainActivity;
import tech.lerk.meshtalk.Preferences;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.providers.impl.ContactProvider;
import tech.lerk.meshtalk.providers.impl.IdentityProvider;

public class IdentitiesFragment extends Fragment {

    private static final String TAG = IdentitiesFragment.class.getCanonicalName();
    private SharedPreferences preferences;
    private ContactProvider contactProvider;
    private ListView listView;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        contactProvider = ContactProvider.get(requireContext());
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());

        View root = inflater.inflate(R.layout.fragment_identities, container, false);

        listView = root.findViewById(R.id.identity_list_view);
        IdentityProvider.get(requireContext()).getAllLiveData(liveData -> {
            if (liveData != null) {
                requireActivity().runOnUiThread(() ->
                        liveData.observe(getViewLifecycleOwner(), identityDbos -> {
                            List<Identity> identities = identityDbos.stream().map(id -> {
                                try {
                                    return DatabaseEntityConverter.convert(id, KeyHolder.get(requireContext()));
                                } catch (DecryptionException e) {
                                    Log.e(TAG, "Unable to decrypt identity!", e);
                                    return null;
                                }
                            }).collect(Collectors.toList());
                            if (identities.size() > 0) {
                                root.findViewById(R.id.identity_list_empty).setVisibility(View.INVISIBLE);
                                updateNavHeader();
                            } else {
                                root.findViewById(R.id.identity_list_empty).setVisibility(View.VISIBLE);
                            }
                            ArrayAdapter<Identity> adapter = new ArrayAdapter<Identity>(requireContext(), R.layout.list_item_identity, new ArrayList<>(identities)) {
                                @NonNull
                                @Override
                                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                    return buildIdentityView(convertView, this.getItem(position));
                                }
                            };
                            listView.setAdapter(adapter);
                        }));
            }
        });

        FloatingActionButton fab = root.findViewById(R.id.new_identity_button);
        fab.setOnClickListener(view -> handleActionButtonClick());

        AsyncTask.execute(() -> IdentityProvider.get(requireContext()).getAll(ids -> {
            if (ids == null || ids.size() < 1) {
                Objects.requireNonNull(getActivity()).runOnUiThread(() ->
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle(R.string.dialog_no_identities_title)
                                .setMessage(R.string.dialog_no_identities_message)
                                .setNegativeButton(R.string.action_no, (d, w) -> d.dismiss())
                                .setPositiveButton(R.string.action_yes, (d, w) -> {
                                    d.dismiss();
                                    handleActionButtonClick();
                                }).create().show());
            }
        }));

        return root;
    }

    private void updateNavHeader() {
        ((MainActivity) requireActivity()).updateNavHeader();
        ((MainActivity) requireActivity()).startWorkers();
    }

    private View buildIdentityView(@Nullable View convertView, Identity identity) {
        View view = null;
        if (convertView != null) {
            view = convertView;
        }
        if (view == null) {
            view = View.inflate(requireContext(), R.layout.list_item_identity, null);
        }
        if (identity != null) {
            Identicon identicon = view.findViewById(R.id.list_item_identity_identicon);
            TextView name = view.findViewById(R.id.list_item_identity_name_label);
            TextView publicKey = view.findViewById(R.id.list_item_identity_public_key_label);
            ImageButton defaultIdentityButton = view.findViewById(R.id.list_item_identity_default);

            view.setOnLongClickListener(v12 -> showIdentityContextDialog(identity));
            String defaultIdentity = preferences.getString(Preferences.DEFAULT_IDENTITY.toString(), "");
            if (defaultIdentity.equals(identity.getId().toString())) {
                defaultIdentityButton.setImageDrawable(requireContext().getDrawable(R.drawable.ic_star_black_48dp));
                defaultIdentityButton.setOnClickListener(v1 -> {
                    if (preferences.getBoolean(Preferences.ASK_DEFAULT_IDENTITY.toString(), true)) {
                        new AlertDialog.Builder(requireContext())
                                .setTitle(R.string.dialog_unset_default_identity_title)
                                .setMessage(R.string.dialog_unset_default_identity_message)
                                .setPositiveButton(R.string.action_yes, (d, w) -> handleUnsetDefault(d))
                                .setNegativeButton(R.string.action_no, (d, w) -> d.dismiss())
                                .create().show();
                    } else {
                        handleUnsetDefault(null);
                    }
                });
            } else {
                defaultIdentityButton.setImageDrawable(requireContext().getDrawable(R.drawable.ic_star_border_black_48dp));
                defaultIdentityButton.setOnClickListener(v1 -> {
                    if (preferences.getBoolean(Preferences.ASK_DEFAULT_IDENTITY.toString(), true)) {
                        new AlertDialog.Builder(requireContext())
                                .setTitle(R.string.dialog_set_default_identity_title)
                                .setMessage(R.string.dialog_set_default_identity_message)
                                .setPositiveButton(R.string.action_yes, (d, w) -> handleSetDefault(d, identity.getId()))
                                .setNegativeButton(R.string.action_no, (d, w) -> d.dismiss())
                                .create().show();
                    } else {
                        handleSetDefault(null, identity.getId());
                    }
                });
            }
            identicon.show(identity.getId().toString());
            name.setText(identity.getName());
            publicKey.setText(Stuff.getFingerprintForKey((RSAPublicKey) identity.getPublicKey()));
        }
        return view;
    }

    private boolean showIdentityContextDialog(Identity identity) {
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
                    qrCodeView.setImageBitmap(QRCode.from(contactProvider.getAsShareableJSON(identity))
                            .withSize(qrCodeView.getWidth(), qrCodeView.getHeight()).bitmap());
                })
                .setNeutralButton(R.string.action_copy_contact_info, (d, w) -> {
                    ClipboardManager clipboard = Objects.requireNonNull((ClipboardManager)
                            requireContext().getSystemService(Context.CLIPBOARD_SERVICE));
                    String clipboardLabel = getString(R.string.action_copy_details_pre) + identity.getName();
                    String encodedPK = Base64.getMimeEncoder().encodeToString(identity.getPublicKey().getEncoded());
                    String clipboardText = "ID: '" + identity.getId().toString() + "'. Public Key: '" + encodedPK;
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
    }

    private void handleUnsetDefault(@Nullable DialogInterface d) {
        if (d != null) {
            d.dismiss();
        }
        preferences.edit().putString(Preferences.DEFAULT_IDENTITY.toString(), "").apply();
        listView.invalidateViews();
        updateNavHeader();
    }

    private void handleSetDefault(@Nullable DialogInterface d, UUID id) {
        if (d != null) {
            d.dismiss();
        }
        preferences.edit().putString(Preferences.DEFAULT_IDENTITY.toString(), id.toString()).apply();
        listView.invalidateViews();
        updateNavHeader();
    }

    private void handleActionButtonClick() {
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
                Identity newIdentity = buildIdentity(id, identityName, loadingDialog);
                IdentityProvider.get(requireContext()).save(newIdentity);
                requireActivity().runOnUiThread(() -> loadingDialog.get().dismiss());
            } catch (NoSuchAlgorithmException e) {
                requireActivity().runOnUiThread(() -> loadingDialog.get().dismiss());
                Log.e(TAG, "Unable to create new identity!", e);
            }
        });
    }

    private Identity buildIdentity(UUID id, String identityName, AtomicReference<AlertDialog> loadingDialog) throws NoSuchAlgorithmException {
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
        return newIdentity;
    }
}

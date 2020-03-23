package tech.lerk.meshtalk.ui.contacts;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
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
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.providers.ChatProvider;
import tech.lerk.meshtalk.providers.ContactProvider;

public class ContactsFragment extends Fragment {

    private static final String TAG = ContactsFragment.class.getCanonicalName();
    private ContactsViewModel contactsViewModel;
    private ContactProvider contactProvider;
    private SharedPreferences preferences;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        contactsViewModel = ViewModelProviders.of(this).get(ContactsViewModel.class);
        contactProvider = ContactProvider.get(requireContext());
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());

        View root = inflater.inflate(R.layout.fragment_contacts, container, false);

        final ListView listView = root.findViewById(R.id.contact_list_view);
        contactsViewModel.getContacts().observe(getViewLifecycleOwner(), contacts -> {
            if (contacts.size() > 0) {
                root.findViewById(R.id.contact_list_empty).setVisibility(View.INVISIBLE);
            } else {
                root.findViewById(R.id.contact_list_empty).setVisibility(View.VISIBLE);
            }
            ArrayAdapter<Contact> adapter = new ArrayAdapter<Contact>(requireContext(), R.layout.list_item_contact, new ArrayList<>(contacts)) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = null;
                    if (convertView != null) {
                        v = convertView;
                    }
                    if (v == null) {
                        v = View.inflate(requireContext(), R.layout.list_item_contact, null);
                    }
                    Identicon identicon = v.findViewById(R.id.list_item_contact_identicon);
                    TextView name = v.findViewById(R.id.list_item_contact_name_label);
                    TextView publicKey = v.findViewById(R.id.list_item_contact_public_key_label);
                    ImageButton startChatButton = v.findViewById(R.id.list_item_contact_start_chat);

                    Contact contact = this.getItem(position);
                    if (contact != null) {
                        startChatButton.setOnClickListener(v1 -> {
                            String dialogMessage = getString(R.string.dialog_start_chat_message_pre) +
                                    contact.getName() + getString(R.string.dialog_start_chat_message_post);
                            new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.dialog_start_chat_title)
                                    .setMessage(dialogMessage)
                                    .setPositiveButton(R.string.action_yes, (d, w) -> handleStartChat(d, contact))
                                    .setNegativeButton(R.string.action_no, (d, w) -> d.dismiss())
                                    .create().show();
                        });

                        identicon.show(contact.getId().toString());
                        name.setText(contact.getName());
                        publicKey.setText(Stuff.getFingerprintForKey((RSAPublicKey) contact.getPublicKey()));
                    }
                    return v;
                }
            };
            listView.setAdapter(adapter);
        });
        updateContacts();

        FloatingActionButton fab = root.findViewById(R.id.new_contact_button);
        fab.setOnClickListener(view -> handleActionButtonClick());

        return root;
    }

    private void handleStartChat(DialogInterface d, Contact recipient) {
        d.dismiss();
        Chat chat = new Chat();
        chat.setId(UUID.randomUUID());
        chat.setRecipient(recipient.getId());
        chat.setTitle("Chat with " + recipient.getName());
        ChatProvider.get(requireContext()).save(chat);
        ((MainActivity) Objects.requireNonNull(getActivity())).getNavController()
                .navigate(R.id.nav_item_chats);
    }

    private void handleActionButtonClick() {
        AlertDialog newContactDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_new_contact_title)
                .setMessage(R.string.dialog_new_contact_decision_message)
                .setPositiveButton(R.string.action_add_manually, (d, w) -> handleAddManually(d))
                .setNeutralButton(R.string.action_scan_qr_code, (d, w) -> handleScanQR(d))
                .setNegativeButton(R.string.action_cancel, (d, w) -> d.dismiss())
                .create();
        newContactDialog.show();
    }

    private void handleScanQR(DialogInterface d) {
        d.dismiss();
    }

    private void handleAddManually(DialogInterface d) {
        d.dismiss();
        AlertDialog newContactDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_new_contact_title)
                .setView(R.layout.dialog_new_contact)
                .setPositiveButton(R.string.action_save, (d1, w) -> handleSave(d1))
                .setNegativeButton(R.string.action_cancel, (d1, w) -> d1.dismiss())
                .create();
        newContactDialog.show();
        Identicon identicon = newContactDialog.findViewById(R.id.dialog_new_contact_identicon);
        EditText contactUUID = newContactDialog.findViewById(R.id.dialog_new_contact_contact_uuid);
        contactUUID.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                identicon.show(contactUUID.getText().toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        identicon.show(UUID.randomUUID());
    }

    private void handleSave(DialogInterface d) {
        String name = ((EditText) ((AlertDialog) d).findViewById(R.id.dialog_new_contact_contact_name)).getText().toString();
        String uuid = ((EditText) ((AlertDialog) d).findViewById(R.id.dialog_new_contact_contact_uuid)).getText().toString();
        String publicKey = ((EditText) ((AlertDialog) d).findViewById(R.id.dialog_new_contact_contact_public_key)).getText().toString();
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
                loadingText.setText(R.string.dialog_saving_creating_contact);
            });
            Stuff.waitOrDonT(200);

            byte[] decodedKey = Base64.decode(publicKey, Base64.DEFAULT);
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                Contact newContact = new Contact();
                newContact.setId(UUID.fromString(uuid));
                newContact.setName(name);
                newContact.setPublicKey(kf.generatePublic(new X509EncodedKeySpec(decodedKey)));
                requireActivity().runOnUiThread(() -> {
                    TextView loadingText = loadingDialog.get().findViewById(R.id.loading_text);
                    loadingText.setText(R.string.dialog_saving_saving_contact);
                });
                Stuff.waitOrDonT(200);

                contactProvider.save(newContact);

                requireActivity().runOnUiThread(() -> {
                    loadingDialog.get().dismiss();
                    updateContacts();
                });
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                Log.e(TAG, "Unable to decode key!", e);
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.get().dismiss();
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.dialog_error_title)
                            .setMessage(R.string.dialog_new_contact_error_message)
                            .setNeutralButton(R.string.action_okay, (d1, w) -> d1.dismiss())
                            .create().show();
                });
            }

        });
    }

    private void updateContacts() {
        Set<Contact> contacts = new TreeSet<>();
        contactProvider.getAllIds().forEach(id -> contacts.add(contactProvider.getById(id)));
        contactsViewModel.setContacts(contacts);
    }
}

package tech.lerk.meshtalk.ui.conversation;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.emoji.bundled.BundledEmojiCompatConfig;
import androidx.emoji.text.EmojiCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import tech.lerk.meshtalk.Callback;
import tech.lerk.meshtalk.MainActivity;
import tech.lerk.meshtalk.Preferences;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.providers.impl.ChatProvider;
import tech.lerk.meshtalk.providers.impl.ContactProvider;
import tech.lerk.meshtalk.providers.impl.HandshakeProvider;
import tech.lerk.meshtalk.providers.impl.IdentityProvider;
import tech.lerk.meshtalk.providers.impl.MessageProvider;
import tech.lerk.meshtalk.workers.DataKeys;
import tech.lerk.meshtalk.workers.SubmitHandshakeWorker;
import tech.lerk.meshtalk.workers.SubmitMessageWorker;

public class ConversationFragment extends Fragment {

    private static final String TAG = ConversationFragment.class.getCanonicalName();
    private MessageProvider messageProvider;
    private Chat currentChat;
    private IdentityProvider identityProvider;
    private ContactProvider contactProvider;
    private ChatProvider chatProvider;
    private HandshakeProvider handshakeProvider;
    private SharedPreferences preferences;
    private ArrayAdapter<Message> listViewAdapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        EmojiCompat.init(new BundledEmojiCompatConfig(requireContext()));
        View root = inflater.inflate(R.layout.fragment_conversation, container, false);

        String currentChatId = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext())
                .getString(Preferences.CURRENT_CHAT.toString(), Stuff.NONE);
        if (currentChatId.equals(Stuff.NONE)) {
            Toast.makeText(requireContext(), R.string.error_no_chat_selected, Toast.LENGTH_LONG).show();
            requireActivity().onBackPressed();
            ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
        }

        messageProvider = MessageProvider.get(requireContext());
        identityProvider = IdentityProvider.get(requireContext());
        contactProvider = ContactProvider.get(requireContext());
        chatProvider = ChatProvider.get(requireContext());
        handshakeProvider = HandshakeProvider.get(requireContext());
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());

        AlertDialog loadingDialog = Stuff.getLoadingDialog((MainActivity) requireActivity(), null);
        loadingDialog.show();
        AsyncTask.execute(() ->
                chatProvider.getById(UUID.fromString(currentChatId), c -> {
                    if (c != null) {
                        messageProvider.getByChat(c.getId(), messages ->
                                Objects.requireNonNull(getActivity()).runOnUiThread(() -> {
                                    currentChat = c;
                                    loadingDialog.dismiss();
                                    initUi(root, messages);
                                }));
                    } else {
                        Log.w(TAG, "Chat is null: '" + currentChatId + "'!");
                    }
                })
        );

        return root;
    }

    private void initUi(View root, List<Message> messages) {
        ListView listView = root.findViewById(R.id.message_list_view);

        if (messages != null) {
            if (messages.size() > 0) {
                root.findViewById(R.id.message_list_empty).setVisibility(View.INVISIBLE);
            } else {
                root.findViewById(R.id.message_list_empty).setVisibility(View.VISIBLE);
            }
            listViewAdapter = new ArrayAdapter<Message>(requireContext(), R.layout.list_item_message, messages) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = null;
                    if (convertView != null) {
                        v = convertView;
                    }
                    if (v == null) {
                        v = View.inflate(requireContext(), R.layout.list_item_message, null);
                    }
                    TextView contactNameView = v.findViewById(R.id.list_item_message_contact_name_label);
                    TextView messageView = v.findViewById(R.id.list_item_message_message);
                    TextView timeView = v.findViewById(R.id.list_item_message_message_time);

                    Message message = this.getItem(position);
                    if (message != null) {
                        AsyncTask.execute(() ->
                                messageProvider.decryptMessage(message.getContent(), currentChat, decryptedMessage ->
                                        contactProvider.getById(message.getSender(), contact ->
                                                identityProvider.getById(message.getSender(), identity ->
                                                        requireActivity().runOnUiThread(() -> {
                                                            if (contact != null) {
                                                                contactNameView.setText(contact.getName());
                                                            } else {
                                                                if (identity != null) {
                                                                    contactNameView.setText(identity.getName());
                                                                } else {
                                                                    contactNameView.setText(R.string.error_loading_contact);
                                                                    Log.e(TAG, "Unable to determine sender for message: '" + message.getId() + "'!");
                                                                }
                                                            }
                                                            if (decryptedMessage != null) {
                                                                messageView.setText(decryptedMessage);
                                                            } else {
                                                                messageView.setText(R.string.error_unable_to_decrypt_message);
                                                            }
                                                            timeView.setText(message.getDate().format(DateTimeFormatter.ISO_DATE_TIME));
                                                        })
                                                )

                                        )));
                    }
                    return v;
                }
            };
        } else {
            Log.w(TAG, "Messages are null for: '" + currentChat.getId() + "'!");
        }
        listView.setAdapter(listViewAdapter);

        EditText messageET = root.findViewById(R.id.message_edit_text);
        messageET.setImeOptions(EditorInfo.IME_ACTION_SEND);
        messageET.setInputType(EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        messageET.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(messageET.getText().toString(), currentChat, success -> {
                    if (success != null && success) {
                        messageET.setText("");
                    }
                });
            }
            return false;
        });
        checkForHandshake();
    }

    private void sendMessage(String message, Chat chat, Callback<Boolean> callback) {
        AsyncTask.execute(() ->
                Stuff.determineSelfId(chat.getSender(), chat.getRecipient(), identityProvider, selfId ->
                        Stuff.determineOtherId(chat.getSender(), chat.getRecipient(), identityProvider, otherId -> {
                            if (selfId != null && otherId != null) {
                                messageProvider.encryptMessage(message, chat, encryptedMessage -> {
                                    Data messageData = new Data.Builder()
                                            .putString(DataKeys.MESSAGE_ID.toString(), UUID.randomUUID().toString())
                                            .putString(DataKeys.MESSAGE_CHAT.toString(), chat.getId().toString())
                                            .putString(DataKeys.MESSAGE_SENDER.toString(), selfId.toString())
                                            .putString(DataKeys.MESSAGE_RECEIVER.toString(), otherId.toString())
                                            .putLong(DataKeys.MESSAGE_DATE.toString(), LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                                            .putString(DataKeys.MESSAGE_CONTENT.toString(), encryptedMessage)
                                            .build();

                                    OneTimeWorkRequest sendMessageWorkRequest = new OneTimeWorkRequest.Builder(SubmitMessageWorker.class)
                                            .setInputData(messageData).build();
                                    WorkManager workManager = WorkManager.getInstance(requireContext());
                                    workManager.enqueue(sendMessageWorkRequest);

                                    requireActivity().runOnUiThread(() ->
                                            workManager.getWorkInfoByIdLiveData(sendMessageWorkRequest.getId()).observe(requireActivity(), info -> {
                                                if (info != null && info.getState().isFinished()) {
                                                    Data data = info.getOutputData();
                                                    int errorCode = data.getInt(DataKeys.ERROR_CODE.toString(), -1);
                                                    switch (errorCode) {
                                                        case SubmitHandshakeWorker.ERROR_INVALID_SETTINGS:
                                                        case SubmitHandshakeWorker.ERROR_URI:
                                                        case SubmitHandshakeWorker.ERROR_CONNECTION:
                                                        case SubmitHandshakeWorker.ERROR_PARSING:
                                                            String msg = "Got error " + errorCode + " while sending message!";
                                                            Log.e(TAG, msg);
                                                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                                                            callback.call(false);
                                                            break;
                                                        case SubmitHandshakeWorker.ERROR_NONE:
                                                            if (preferences.getBoolean(Preferences.TOAST_MESSAGE_SENT.toString(), true)) {
                                                                Toast.makeText(requireContext(), R.string.success_sending_message, Toast.LENGTH_LONG).show();
                                                            }
                                                            callback.call(true);
                                                            break;
                                                        default:
                                                            Log.wtf(TAG, "Invalid errorCode: " + errorCode + "!");
                                                            break;
                                                    }
                                                }
                                            })
                                    );
                                });
                            } else {
                                throw new IllegalStateException("Unable to determine ids!");
                            }
                        })));
    }

    private void checkForHandshake() {
        Stuff.determineSelfId(currentChat.getSender(), currentChat.getRecipient(), identityProvider, selfId ->
                Stuff.determineOtherId(currentChat.getSender(), currentChat.getRecipient(), identityProvider, otherId -> {
                    if (selfId != null && otherId != null) {
                        handshakeProvider.getLatestByReceiver(otherId, otherHandshake ->
                                handshakeProvider.getLatestByReceiver(selfId, selfHandshake ->
                                        contactProvider.getById(otherId, contact -> {
                                            if (selfHandshake == null) {
                                                requireActivity().runOnUiThread(() -> {
                                                    AlertDialog alertDialog = Stuff.getLoadingDialog((MainActivity) requireActivity(), (d, w) -> {
                                                        d.dismiss();
                                                        requireActivity().onBackPressed();
                                                        ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
                                                    }, R.string.dialog_waiting_for_handshake_title);
                                                    alertDialog.show();
                                                    ProgressBar progressBar = Objects.requireNonNull(alertDialog.findViewById(R.id.loading_spinner));
                                                    TextView loadingTextView = Objects.requireNonNull(alertDialog.findViewById(R.id.loading_text));
                                                    if (otherHandshake == null) {
                                                        progressBar.setProgress(1, true);
                                                        loadingTextView.setText(R.string.dialog_waiting_for_handshake_message_sending_handshake);
                                                        if (contact != null) {
                                                            try {
                                                                submitHandshake(contact, selfId, KeyGenerator.getInstance("AES").generateKey());
                                                            } catch (NoSuchAlgorithmException e) {
                                                                Log.wtf(TAG, "Unable to get KeyGenerator!", e);
                                                            }
                                                        } else {
                                                            Log.w(TAG, "Contact '" + otherId + "' is null!");
                                                        }
                                                    }
                                                    progressBar.setProgress(80, true);
                                                    loadingTextView.setText(R.string.dialog_waiting_for_handshake_message_waiting_for_handshake);
                                                });
                                            }
                                        })
                                )
                        );
                    } else {
                        Toast.makeText(requireContext(), R.string.error_unable_to_determine_participant, Toast.LENGTH_LONG).show();
                        requireActivity().onBackPressed();
                        ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
                    }
                }));
    }

    private void submitHandshake(@NonNull Contact receiver, @NonNull UUID senderId, @NonNull SecretKey secretKey) {
        AsyncTask.execute(() -> {
            try {
                Cipher cipher = Cipher.getInstance("RSA");
                cipher.init(Cipher.ENCRYPT_MODE, receiver.getPublicKey());
                Data handshakeData = new Data.Builder()
                        .putString(DataKeys.HANDSHAKE_ID.toString(), UUID.randomUUID().toString())
                        .putString(DataKeys.HANDSHAKE_CHAT.toString(), currentChat.getId().toString())
                        .putString(DataKeys.HANDSHAKE_SENDER.toString(), senderId.toString())
                        .putString(DataKeys.HANDSHAKE_RECEIVER.toString(), receiver.getId().toString())
                        .putLong(DataKeys.HANDSHAKE_DATE.toString(), LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                        .putString(DataKeys.HANDSHAKE_KEY.toString(), Base64.getMimeEncoder().encodeToString(cipher.doFinal(secretKey.getEncoded())))
                        .putString(DataKeys.HANDSHAKE_IV.toString(), generateChatIv())
                        .build();

                OneTimeWorkRequest sendHandshakeWorkRequest = new OneTimeWorkRequest.Builder(SubmitHandshakeWorker.class)
                        .setInputData(handshakeData).build();
                WorkManager workManager = WorkManager.getInstance(requireContext());
                workManager.enqueue(sendHandshakeWorkRequest);

                requireActivity().runOnUiThread(() ->
                        workManager.getWorkInfoByIdLiveData(sendHandshakeWorkRequest.getId()).observe(requireActivity(), info -> {
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
                                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                                        break;
                                    case SubmitHandshakeWorker.ERROR_NONE:
                                        Toast.makeText(requireContext(), R.string.success_sending_handshake, Toast.LENGTH_LONG).show();
                                        break;
                                    default:
                                        Log.wtf(TAG, "Invalid errorCode: " + errorCode + "!");
                                        break;
                                }
                            }
                        })
                );
            } catch (NoSuchAlgorithmException e) {
                Log.wtf(TAG, "Unable to get KeyGenerator!", e);
            } catch (NoSuchPaddingException e) {
                Log.wtf(TAG, "Unable to get Cipher!", e);
            } catch (InvalidKeyException e) {
                Log.e(TAG, "Unable to init Cipher!", e);
            } catch (BadPaddingException | IllegalBlockSizeException e) {
                Log.e(TAG, "Unable to encrypt handshake!", e);
            }
        });
    }

    private String generateChatIv() {
        byte[] array = new byte[12];
        new SecureRandom().nextBytes(array);
        return new String(array, StandardCharsets.UTF_8);
    }
}

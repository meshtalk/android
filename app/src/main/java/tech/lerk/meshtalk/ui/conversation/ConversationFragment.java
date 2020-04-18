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
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.emoji.bundled.BundledEmojiCompatConfig;
import androidx.emoji.text.EmojiCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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
import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.entities.Handshake;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.Sendable;
import tech.lerk.meshtalk.providers.impl.ChatProvider;
import tech.lerk.meshtalk.providers.impl.ContactProvider;
import tech.lerk.meshtalk.providers.impl.HandshakeProvider;
import tech.lerk.meshtalk.providers.impl.IdentityProvider;
import tech.lerk.meshtalk.providers.impl.MessageProvider;
import tech.lerk.meshtalk.workers.DataKeys;
import tech.lerk.meshtalk.workers.SubmitHandshakeWorker;
import tech.lerk.meshtalk.workers.SubmitMessageWorker;

import static tech.lerk.meshtalk.MessageGatewayClientService.getIvFromHandshake;
import static tech.lerk.meshtalk.MessageGatewayClientService.getSecretKeyFromHandshake;

public class ConversationFragment extends Fragment {

    private static final String TAG = ConversationFragment.class.getCanonicalName();
    private MessageProvider messageProvider;
    private Chat currentChat;
    private IdentityProvider identityProvider;
    private ContactProvider contactProvider;
    private ChatProvider chatProvider;
    private HandshakeProvider handshakeProvider;
    private SharedPreferences preferences;
    private ArrayAdapter<UIMessage> listViewAdapter;
    private View emptyList;
    private ListView listView;
    private FloatingActionButton scrollButton;

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

        emptyList = root.findViewById(R.id.message_list_empty);
        listView = root.findViewById(R.id.message_list_view);
        scrollButton = root.findViewById(R.id.message_list_scroll_button);

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (view.getId() == R.id.message_list_view) {
                    int lastindex = view.getLastVisiblePosition() + 1;
                    if (lastindex == totalItemCount) {
                        scrollButton.setVisibility(View.INVISIBLE);
                    } else if (firstVisibleItem + visibleItemCount == lastindex) {
                        scrollButton.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        AlertDialog loadingDialog = Stuff.getLoadingDialog((MainActivity) requireActivity(), null);
        loadingDialog.show();
        UUID id = UUID.fromString(currentChatId);
        chatProvider.getById(id, chat -> {
            currentChat = chat;
            if (currentChat != null) {
                messageProvider.getByChat(currentChat.getId(), messages ->
                        handshakeProvider.getById(currentChat.getHandshake(), handshake -> {
                            if (handshake != null) {
                                if (handshake.getReceiver().equals(chat.getSender())) {
                                    getKeysAndInit(root, loadingDialog, messages, handshake);
                                } else {
                                    handshakeProvider.getById(handshake.getReply(), reply -> {
                                        if (reply != null) {
                                            if (reply.getReceiver().equals(chat.getSender())) {
                                                getKeysAndInit(root, loadingDialog, messages, reply);
                                            } else {
                                                Log.wtf(TAG, "Not participating in chat!");
                                            }
                                        } else {
                                            Log.wtf(TAG, "Handshake reply is null!");
                                        }
                                    });
                                }
                            }
                            loadingDialog.dismiss();
                            requireActivity().runOnUiThread(this::checkForHandshake);
                        }));
            } else {
                Log.wtf(TAG, "Chat is null: '" + currentChatId + "'!");
            }
        });

        return root;
    }

    private void getKeysAndInit(View root, AlertDialog loadingDialog, List<Message> messages, Handshake handshake) {
        identityProvider.getById(currentChat.getSender(), identity -> {
            if (identity != null) {
                SecretKey chatKey = getSecretKeyFromHandshake(handshake, identity);
                byte[] chatIv = getIvFromHandshake(handshake, identity);
                if (chatKey != null && chatIv != null && messages != null) {
                    getUIMessages(messages, chatKey, chatIv, uiMessages -> {
                        if (uiMessages != null) {
                            requireActivity().runOnUiThread(() -> {
                                loadingDialog.dismiss();
                                initUi(root, uiMessages);
                                addMessageObserver(chatKey, chatIv);
                            });
                        } else {
                            Log.wtf(TAG, "Converted messages list is null!");
                        }
                    });
                } else {
                    Log.e(TAG, "Chat key, iv or messages invalid!");
                }
            } else {
                Log.e(TAG, "Identity is null!");
            }
        });
    }

    private void addMessageObserver(SecretKey chatKey, byte[] chatIv) {
        messageProvider.getLiveMessagesByChat(currentChat.getId(), liveMessages -> {
            if (liveMessages != null) {
                requireActivity().runOnUiThread(() ->
                        liveMessages.observe(getViewLifecycleOwner(), messageDbos ->
                                getUIMessages(messageDbos.stream().map(DatabaseEntityConverter::convert).collect(Collectors.toList()),
                                        chatKey, chatIv, updatedUiMessages -> requireActivity().runOnUiThread(() -> updateUi(updatedUiMessages)))));
            } else {
                Log.e(TAG, "Unable to get messages LiveData!");
            }
        });
    }

    private void getUIMessages(@NonNull List<Message> messages,
                               @NonNull SecretKey chatKey,
                               @NonNull byte[] chatIv,
                               Callback<ArrayList<UIMessage>> callback) {
        AsyncTask.execute(() -> {
            ArrayList<UIMessage> uiMessages = new ArrayList<>();
            messages.forEach(m ->
                    Stuff.getContactOrIdentityForId(m.getSender(), contactProvider, identityProvider, sender -> {
                        if (sender != null) {
                            uiMessages.add(UIMessage.of(m, chatKey, chatIv, sender, messageProvider));
                        }
                    })
            );
            callback.call(uiMessages);
        });
    }

    private void initUi(View root, @NonNull List<UIMessage> messages) {
        ((Toolbar) requireActivity().findViewById(R.id.toolbar)).setTitle(currentChat.getTitle());
        listViewAdapter = new ArrayAdapter<UIMessage>(requireContext(), R.layout.list_item_message, messages) {
            @Override
            public void notifyDataSetChanged() {
                super.notifyDataSetChanged();
                if (listViewAdapter.getCount() > 0) {
                    emptyList.setVisibility(View.INVISIBLE);
                } else {
                    emptyList.setVisibility(View.VISIBLE);
                }
            }

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

                UIMessage message = this.getItem(position);
                if (message != null) {
                    contactNameView.setText(message.getSenderName());
                    messageView.setText(message.getDecryptedText());
                    timeView.setText(message.getDate().format(DateTimeFormatter.ofPattern("d MMM uuuu hh:mm")));
                }
                return v;
            }
        };
        listViewAdapter.setNotifyOnChange(true);
        listViewAdapter.sort(Sendable::compareTo);
        listView.setAdapter(listViewAdapter);
        scrollButton.setOnClickListener(v -> scrollToBottom());

        EditText messageET = root.findViewById(R.id.message_edit_text);
        messageET.setImeOptions(EditorInfo.IME_ACTION_NONE);
        messageET.setInputType(EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);

        ProgressBar sendLoader = root.findViewById(R.id.send_loader);
        sendLoader.setIndeterminate(true);

        ImageButton sendButton = root.findViewById(R.id.send_button);
        sendButton.setOnClickListener((v) -> {
            sendButton.setVisibility(View.INVISIBLE);
            sendLoader.setVisibility(View.VISIBLE);
            messageET.setEnabled(false);
            sendMessage(messageET.getText().toString(), currentChat, success -> {
                if (success != null && success) {
                    messageET.setText("");
                }
                sendButton.setVisibility(View.VISIBLE);
                sendLoader.setVisibility(View.INVISIBLE);
                messageET.setEnabled(true);
            });
        });

        checkForHandshake();
    }

    private void scrollToBottom() {
        listView.smoothScrollToPositionFromTop(listViewAdapter.getCount() + 1, 0);
    }

    private void updateUi(@Nullable final ArrayList<UIMessage> updatedMessages) {
        if (updatedMessages != null && !updatedMessages.isEmpty()) {
            listViewAdapter.clear();
            listViewAdapter.addAll(updatedMessages);
            listViewAdapter.sort(Sendable::compareTo);
            if (preferences.getBoolean(Preferences.CHAT_SCROLL_TO_BOTTOM_ON_NEW_MESSAGES.toString(), true)) {
                scrollToBottom();
            }
        } else {
            Log.d(TAG, "Messages are null or empty!");
        }
    }

    private void sendMessage(String message, Chat chat, Callback<Boolean> callback) {
        UUID selfId = chat.getSender();
        UUID otherId = chat.getRecipient();
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

                requireActivity().runOnUiThread(() -> {
                    OneTimeWorkRequest sendMessageWorkRequest = new OneTimeWorkRequest.Builder(SubmitMessageWorker.class)
                            .setInputData(messageData).build();
                    WorkManager workManager = WorkManager.getInstance(requireActivity());
                    workManager.enqueue(sendMessageWorkRequest);
                    workManager.getWorkInfoByIdLiveData(sendMessageWorkRequest.getId()).observe(requireActivity(), info -> {
                        if (info != null && info.getState().isFinished()) {
                            Data data = info.getOutputData();
                            int errorCode = data.getInt(DataKeys.ERROR_CODE.toString(), -1);
                            switch (errorCode) {
                                case SubmitMessageWorker.ERROR_INVALID_SETTINGS:
                                case SubmitMessageWorker.ERROR_URI:
                                case SubmitMessageWorker.ERROR_CONNECTION:
                                case SubmitMessageWorker.ERROR_PARSING:
                                    String msg = "Got error " + errorCode + " while sending message!";
                                    Log.e(TAG, msg);
                                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                                    callback.call(false);
                                    return;
                                case SubmitMessageWorker.ERROR_NONE:
                                    if (preferences.getBoolean(Preferences.TOAST_MESSAGE_SENT.toString(), true)) {
                                        Toast.makeText(requireContext(), R.string.success_sending_message, Toast.LENGTH_LONG).show();
                                    }
                                    callback.call(true);
                                    return;
                                default:
                                    Log.wtf(TAG, "Invalid errorCode: " + errorCode + "!");
                                    callback.call(false);
                            }
                        }
                    });
                });
            });
        } else {
            callback.call(false);
            throw new IllegalStateException("Unable to determine ids!");
        }

    }

    private void checkForHandshake() {
        UUID selfId = currentChat.getSender();
        UUID otherId = currentChat.getRecipient();
        if (selfId != null && otherId != null) {
            requireActivity().runOnUiThread(() -> {
                AlertDialog alertDialog = Stuff.getLoadingDialog((MainActivity) requireActivity(), (d, w) -> {
                    d.dismiss();
                    requireActivity().onBackPressed();
                    ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
                }, R.string.dialog_waiting_for_handshake_checking_title);
                alertDialog.show();
                contactProvider.getById(otherId, contact ->
                        requireActivity().runOnUiThread(() -> {
                            ProgressBar progressBar = Objects.requireNonNull(alertDialog.findViewById(R.id.loading_spinner));
                            TextView loadingTextView = Objects.requireNonNull(alertDialog.findViewById(R.id.loading_text));
                            if (currentChat.getHandshake() == null) {
                                progressBar.setProgress(1, true);
                                loadingTextView.setText(R.string.dialog_waiting_for_handshake_message_sending_handshake);
                                if (contact != null) {
                                    AsyncTask.execute(() -> {
                                        try {
                                            submitInitialHandshake(contact, selfId, KeyGenerator.getInstance("AES").generateKey(), alertDialog);
                                        } catch (NoSuchAlgorithmException e) {
                                            Log.wtf(TAG, "Unable to get KeyGenerator!", e);
                                        }
                                    });
                                } else {
                                    Log.w(TAG, "Contact '" + otherId + "' is null!");
                                }
                            } else {
                                handshakeProvider.getById(currentChat.getHandshake(), handshake -> {
                                    if (handshake != null && handshake.getReceiver().equals(currentChat.getSender())) {
                                        replyToHandshake(handshake);
                                    }
                                    requireActivity().runOnUiThread(() -> {
                                        progressBar.setProgress(80, true);
                                        loadingTextView.setText(R.string.dialog_waiting_for_handshake_message_waiting_for_handshake);
                                        startWaitingForHandshake(alertDialog);
                                    });
                                });
                            }
                        })
                );
            });
        } else {
            Toast.makeText(requireContext(), R.string.error_unable_to_determine_participants, Toast.LENGTH_LONG).show();
            requireActivity().onBackPressed();
            ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
        }

    }

    private void replyToHandshake(@NonNull Handshake handshake) {
        identityProvider.getById(currentChat.getSender(), handshakeIdentity -> {
            if (handshakeIdentity != null) {
                SecretKey secretKey = getSecretKeyFromHandshake(handshake, handshakeIdentity);
                if (secretKey != null) {
                    try {
                        Cipher cipher = Cipher.getInstance("RSA");
                        contactProvider.getById(currentChat.getRecipient(), contactById -> {
                            try {
                                if (contactById != null) {
                                    initiateHandshakeReply(handshake, handshakeIdentity, secretKey, cipher, contactById);
                                } else {
                                    Log.e(TAG, "Contact is null!");
                                    MainActivity.maybeRunOnUiThread(() -> Toast.makeText(requireContext(),
                                            R.string.error_unknown_contact, Toast.LENGTH_LONG).show());
                                }
                            } catch (InvalidKeyException e) {
                                Log.e(TAG, "Unable to init Cipher!", e);
                            } catch (BadPaddingException | IllegalBlockSizeException e) {
                                Log.e(TAG, "Unable to encrypt handshake!", e);
                            }
                        });
                    } catch (NoSuchAlgorithmException e) {
                        Log.wtf(TAG, "Unable to get KeyGenerator!", e);
                    } catch (NoSuchPaddingException e) {
                        Log.wtf(TAG, "Unable to get Cipher!", e);
                    }
                } else {
                    String msg = "Secret key is null!";
                    Log.e(TAG, msg);
                    MainActivity.maybeRunOnUiThread(() -> Toast.makeText(requireContext(),
                            msg, Toast.LENGTH_LONG).show());
                }
            } else {
                String msg = "Identity is null!";
                Log.e(TAG, msg);
                MainActivity.maybeRunOnUiThread(() -> Toast.makeText(requireContext(),
                        msg, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void initiateHandshakeReply(@NonNull Handshake handshake,
                                        @NonNull Identity handshakeIdentity,
                                        @NonNull SecretKey secretKey,
                                        @NonNull Cipher cipher,
                                        @NonNull Contact contactById) throws InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        cipher.init(Cipher.ENCRYPT_MODE, contactById.getPublicKey());

        Data handshakeData = new Data.Builder()
                .putString(DataKeys.HANDSHAKE_ID.toString(), handshake.getReply().toString())
                .putString(DataKeys.HANDSHAKE_CHAT.toString(), currentChat.getId().toString())
                .putString(DataKeys.HANDSHAKE_SENDER.toString(), currentChat.getSender().toString())
                .putString(DataKeys.HANDSHAKE_RECEIVER.toString(), currentChat.getRecipient().toString())
                .putString(DataKeys.HANDSHAKE_REPLY.toString(), handshake.getId().toString())
                .putLong(DataKeys.HANDSHAKE_DATE.toString(), LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                .putString(DataKeys.HANDSHAKE_KEY.toString(), Base64.getMimeEncoder().encodeToString(cipher.doFinal(secretKey.getEncoded())))
                .putString(DataKeys.HANDSHAKE_IV.toString(), Base64.getMimeEncoder().encodeToString(cipher.doFinal(getIvFromHandshake(handshake, handshakeIdentity))))
                .build();

        OneTimeWorkRequest sendHandshakeWorkRequest = new OneTimeWorkRequest.Builder(SubmitHandshakeWorker.class)
                .setInputData(handshakeData).build();
        WorkManager workManager = WorkManager.getInstance(requireActivity());
        workManager.enqueue(sendHandshakeWorkRequest);

        MainActivity.maybeRunOnUiThread(() ->
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
                            default:
                                if (preferences.getBoolean(Preferences.TOAST_MESSAGE_SENT.toString(), true)) {
                                    Toast.makeText(requireContext(), R.string.success_sending_handshake, Toast.LENGTH_LONG).show();
                                }
                                break;
                        }
                    }
                })
        );
    }

    private void startWaitingForHandshake(AlertDialog alertDialog) {
        handshakeProvider.getById(currentChat.getHandshake(), handshake -> {
            if (handshake != null) {
                handshakeProvider.getLiveDataById(handshake.getReply(), liveData -> {
                    if (liveData != null) {
                        requireActivity().runOnUiThread(() ->
                                liveData.observe(getViewLifecycleOwner(), hdbo -> {
                                    Handshake reply = DatabaseEntityConverter.convert(hdbo);
                                    if (reply != null) {
                                        Log.i(TAG, "Handshake reply received!");
                                        requireActivity().runOnUiThread(alertDialog::dismiss);
                                    } else {
                                        Log.i(TAG, "Still waiting for handshake reply...");
                                    }
                                }));
                    } else {
                        Log.wtf(TAG, "Unable to get handshake LiveData!");
                    }
                });
            } else {
                Log.wtf(TAG, "Handshake is null!");
            }
        });
    }

    private void submitInitialHandshake(@NonNull Contact receiver, @NonNull UUID senderId, @NonNull SecretKey secretKey, AlertDialog alertDialog) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, receiver.getPublicKey());
            UUID handshakeId = UUID.randomUUID();
            Data handshakeData = new Data.Builder()
                    .putString(DataKeys.HANDSHAKE_ID.toString(), handshakeId.toString())
                    .putString(DataKeys.HANDSHAKE_CHAT.toString(), currentChat.getId().toString())
                    .putString(DataKeys.HANDSHAKE_SENDER.toString(), senderId.toString())
                    .putString(DataKeys.HANDSHAKE_RECEIVER.toString(), receiver.getId().toString())
                    .putString(DataKeys.HANDSHAKE_REPLY.toString(), UUID.randomUUID().toString())
                    .putLong(DataKeys.HANDSHAKE_DATE.toString(), LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                    .putString(DataKeys.HANDSHAKE_KEY.toString(), Base64.getMimeEncoder().encodeToString(cipher.doFinal(secretKey.getEncoded())))
                    .putString(DataKeys.HANDSHAKE_IV.toString(), Base64.getMimeEncoder().encodeToString(generateNewChatIv().getBytes(StandardCharsets.UTF_8)))
                    .build();

            requireActivity().runOnUiThread(() -> {
                        OneTimeWorkRequest sendHandshakeWorkRequest = new OneTimeWorkRequest.Builder(SubmitHandshakeWorker.class)
                                .setInputData(handshakeData).build();
                        WorkManager workManager = WorkManager.getInstance(requireContext());
                        workManager.enqueue(sendHandshakeWorkRequest);
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
                                        alertDialog.dismiss();
                                        requireActivity().onBackPressed();
                                        ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
                                        break;
                                    case SubmitHandshakeWorker.ERROR_NONE:
                                        Toast.makeText(requireContext(), R.string.success_sending_handshake, Toast.LENGTH_LONG).show();
                                        currentChat.setHandshake(handshakeId);
                                        chatProvider.save(currentChat);
                                        ProgressBar progressBar = Objects.requireNonNull(alertDialog.findViewById(R.id.loading_spinner));
                                        TextView loadingTextView = Objects.requireNonNull(alertDialog.findViewById(R.id.loading_text));
                                        progressBar.setProgress(80, true);
                                        loadingTextView.setText(R.string.dialog_waiting_for_handshake_message_waiting_for_handshake);
                                        startWaitingForHandshake(alertDialog);
                                        break;
                                    default:
                                        Log.wtf(TAG, "Invalid errorCode: " + errorCode + "!");
                                        break;
                                }
                            }
                        });
                    }
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
    }

    private String generateNewChatIv() {
        byte[] array = new byte[12];
        new SecureRandom().nextBytes(array);
        return new String(array, StandardCharsets.UTF_8);
    }
}

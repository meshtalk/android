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
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
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
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Handshake;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.entities.ui.MessageDO;
import tech.lerk.meshtalk.entities.ui.UserDO;
import tech.lerk.meshtalk.providers.impl.ChatProvider;
import tech.lerk.meshtalk.providers.impl.ContactProvider;
import tech.lerk.meshtalk.providers.impl.IdentityProvider;
import tech.lerk.meshtalk.providers.impl.MessageProvider;
import tech.lerk.meshtalk.ui.UpdatableFragment;
import tech.lerk.meshtalk.workers.DataKeys;
import tech.lerk.meshtalk.workers.SubmitHandshakeWorker;
import tech.lerk.meshtalk.workers.SubmitMessageWorker;

public class ConversationFragment extends UpdatableFragment {

    private static final String TAG = ConversationFragment.class.getCanonicalName();
    private ConversationViewModel conversationViewModel;
    private MessageProvider messageProvider;
    private Chat currentChat;
    private IdentityProvider identityProvider;
    private ContactProvider contactProvider;
    private ChatProvider chatProvider;
    private SharedPreferences preferences;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        conversationViewModel =
                ViewModelProviders.of(this).get(ConversationViewModel.class);
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
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());

        AlertDialog loadingDialog = Stuff.getLoadingDialog((MainActivity) requireActivity(), null);
        loadingDialog.show();
        AsyncTask.execute(() ->
                chatProvider.getById(UUID.fromString(currentChatId), c -> {
                    currentChat = c;
                    Objects.requireNonNull(getActivity()).runOnUiThread(() -> {
                        conversationViewModel.getMessages().observe(getViewLifecycleOwner(), messageDOs -> {
                            MessagesListAdapter<MessageDO> adapter = new MessagesListAdapter<>(
                                    currentChat.getSender().toString(),
                                    new UserDO.IdenticonImageLoader(requireContext()));
                            ((MessagesList) root.findViewById(R.id.message_list_view)).setAdapter(adapter);
                        });

                        EditText messageET = root.findViewById(R.id.message_edit_text);
                        messageET.setImeOptions(EditorInfo.IME_ACTION_SEND);
                        messageET.setInputType(EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
                        messageET.setOnEditorActionListener((v, actionId, event) -> {
                            if (actionId == EditorInfo.IME_ACTION_SEND) {
                                sendMessage(messageET.getText().toString(), c, success -> {
                                    if (success != null && success) {
                                        messageET.setText("");
                                    }
                                });
                            }
                            return false;
                        });
                        loadingDialog.dismiss();
                        updateViews();
                    });
                }));
        return root;
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
                    if (otherId != null) {
                        HashMap<UUID, Handshake> handshakes = currentChat.getHandshakes();
                        if (handshakes == null) {
                            handshakes = new HashMap<>();
                            currentChat.setHandshakes(handshakes);
                            chatProvider.save(currentChat);
                        }
                        final Handshake otherIdHandshake = handshakes.get(otherId);
                        final Handshake selfIdHandshake = handshakes.get(selfId);
                        if (selfIdHandshake == null) {
                            requireActivity().runOnUiThread(() -> {
                                AlertDialog alertDialog = Stuff.getLoadingDialog((MainActivity) requireActivity(), (d, w) -> {
                                    d.dismiss();
                                    requireActivity().onBackPressed();
                                    ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
                                }, R.string.dialog_waiting_for_handshake_title);
                                alertDialog.show();
                                ProgressBar progressBar = Objects.requireNonNull(alertDialog.findViewById(R.id.loading_spinner));
                                TextView loadingTextView = Objects.requireNonNull(alertDialog.findViewById(R.id.loading_text));
                                if (otherIdHandshake == null) {
                                    progressBar.setProgress(1, true);
                                    loadingTextView.setText(R.string.dialog_waiting_for_handshake_message_sending_handshake);

                                    AsyncTask.execute(() -> {
                                        try {
                                            SecretKey secretKey = KeyGenerator.getInstance("AES").generateKey();

                                            Cipher cipher = Cipher.getInstance("RSA");
                                            contactProvider.getById(otherId, contactById -> {
                                                if (contactById != null) {
                                                    try {
                                                        cipher.init(Cipher.ENCRYPT_MODE, contactById.getPublicKey());

                                                        Handshake handshake = new Handshake();
                                                        handshake.setId(UUID.randomUUID());
                                                        handshake.setChat(currentChat.getId());
                                                        handshake.setReceiver(otherId);
                                                        handshake.setSender(selfId);
                                                        handshake.setKey(Base64.getMimeEncoder().encodeToString(cipher.doFinal(secretKey.getEncoded())));
                                                        handshake.setDate(LocalDateTime.now());
                                                        handshake.setIv(generateChatIv());

                                                        HashMap<UUID, Handshake> handshakes2 = currentChat.getHandshakes();
                                                        handshakes2.put(otherId, handshake);
                                                        currentChat.setHandshakes(handshakes2);
                                                        chatProvider.save(currentChat);

                                                        Data handshakeData = new Data.Builder()
                                                                .putString(DataKeys.HANDSHAKE_ID.toString(), handshake.getId().toString())
                                                                .putString(DataKeys.HANDSHAKE_CHAT.toString(), handshake.getChat().toString())
                                                                .putString(DataKeys.HANDSHAKE_SENDER.toString(), handshake.getSender().toString())
                                                                .putString(DataKeys.HANDSHAKE_RECEIVER.toString(), handshake.getReceiver().toString())
                                                                .putLong(DataKeys.HANDSHAKE_DATE.toString(), handshake.getDate().toEpochSecond(ZoneOffset.UTC))
                                                                .putString(DataKeys.HANDSHAKE_KEY.toString(), handshake.getKey())
                                                                .putString(DataKeys.HANDSHAKE_IV.toString(), handshake.getIv())
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
                                                    } catch (InvalidKeyException e) {
                                                        Log.e(TAG, "Unable to init Cipher!", e);
                                                    } catch (BadPaddingException | IllegalBlockSizeException e) {
                                                        Log.e(TAG, "Unable to encrypt handshake!", e);
                                                    }
                                                } else {
                                                    Log.e(TAG, "Contact is null!");
                                                }
                                            });
                                        } catch (NoSuchAlgorithmException e) {
                                            Log.wtf(TAG, "Unable to get KeyGenerator!", e);
                                        } catch (NoSuchPaddingException e) {
                                            Log.wtf(TAG, "Unable to get Cipher!", e);
                                        }
                                    });
                                }
                                progressBar.setProgress(80, true);
                                loadingTextView.setText(R.string.dialog_waiting_for_handshake_message_waiting_for_handshake);
                            });
                        }
                    } else {
                        Toast.makeText(requireContext(), R.string.error_unable_to_determine_participant, Toast.LENGTH_LONG).show();
                        requireActivity().onBackPressed();
                        ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
                    }
                }));
    }

    private String generateChatIv() {
        byte[] array = new byte[12];
        new SecureRandom().nextBytes(array);
        return new String(array, StandardCharsets.UTF_8);
    }

    @Override
    public void updateViews() {
        if (currentChat != null) {
            checkForHandshake();
            AsyncTask.execute(() ->
                    messageProvider.getAll(ms -> {
                        if (ms != null) {
                            requireActivity().runOnUiThread(() ->
                                    conversationViewModel.setMessages(ms, currentChat, requireActivity())
                            );
                        }
                    })
            );
        }
    }
}

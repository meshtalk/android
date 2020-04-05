package tech.lerk.meshtalk.ui.conversation;

import android.app.AlertDialog;
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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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

public class ConversationFragment extends UpdatableFragment {

    private static final String TAG = ConversationFragment.class.getCanonicalName();
    private ConversationViewModel conversationViewModel;
    private MessageProvider messageProvider;
    private Chat currentChat;
    private IdentityProvider identityProvider;
    private ContactProvider contactProvider;
    private ChatProvider chatProvider;

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

        chatProvider.getById(UUID.fromString(currentChatId), c -> {
            currentChat = c;
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
                    //TODO: implement handshaking before implementing sending...
                    Toast.makeText(requireContext(), "TODO: implement sending...", Toast.LENGTH_LONG).show();
                    messageET.setText("");
                }
                return false;
            });

            updateViews();
        });
        return root;
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
                        if (handshakes.get(selfId) == null) {
                            AlertDialog alertDialog = new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.dialog_waiting_for_handshake_title)
                                    .setView(R.layout.dialog_loading)
                                    .setCancelable(false)
                                    .setNegativeButton(R.string.action_back, (d, w) -> {
                                        d.dismiss();
                                        requireActivity().onBackPressed();
                                        ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
                                    }).create();
                            alertDialog.show();
                            ProgressBar progressBar = Objects.requireNonNull(alertDialog.findViewById(R.id.loading_spinner));
                            TextView loadingTextView = Objects.requireNonNull(alertDialog.findViewById(R.id.loading_text));
                            if (handshakes.get(otherId) == null) {
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
                        }
                    } else {
                        Toast.makeText(requireContext(), R.string.error_unable_to_determine_participant, Toast.LENGTH_LONG).show();
                        requireActivity().onBackPressed();
                        ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
                    }
                }));
    }

    @Override
    public void updateViews() {
        checkForHandshake();
        messageProvider.getAll(ms ->
                conversationViewModel.setMessages(ms, currentChat, requireContext()));
    }
}

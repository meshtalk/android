package tech.lerk.meshtalk.ui.conversation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;

import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.MainActivity;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.entities.ui.MessageDO;
import tech.lerk.meshtalk.entities.ui.UserDO;
import tech.lerk.meshtalk.providers.ChatProvider;
import tech.lerk.meshtalk.providers.MessageProvider;

public class ConversationFragment extends Fragment {

    private ConversationViewModel conversationViewModel;
    private MessageProvider messageProvider;
    private Chat currentChat;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        conversationViewModel =
                ViewModelProviders.of(this).get(ConversationViewModel.class);
        View root = inflater.inflate(R.layout.fragment_conversation, container, false);

        String currentChatId = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext())
                .getString(Preferences.CURRENT_CHAT.toString(), Stuff.NONE);
        if (currentChatId.equals(Stuff.NONE)) {
            Toast.makeText(requireContext(), R.string.error_no_chat_selected, Toast.LENGTH_LONG).show();
            ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
        }

        messageProvider = MessageProvider.get(requireContext());
        currentChat = ChatProvider.get(requireContext()).getById(UUID.fromString(currentChatId));

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
                //TODO: implement sending...
                Toast.makeText(requireContext(), "TODO: implement sending...", Toast.LENGTH_LONG).show();
                messageET.setText("");
            }
            return false;
        });

        updateMessages();
        return root;
    }

    private void updateMessages() {
        TreeSet<Message> messages = messageProvider.getAllIds().stream()
                .map(i -> messageProvider.getById(i))
                .collect(Collectors.toCollection(TreeSet::new));
        conversationViewModel.setMessages(messages, currentChat, requireContext());
    }
}

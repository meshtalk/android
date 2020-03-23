package tech.lerk.meshtalk.ui.chats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import im.delight.android.identicons.Identicon;
import tech.lerk.meshtalk.MainActivity;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.providers.ChatProvider;
import tech.lerk.meshtalk.providers.ContactProvider;
import tech.lerk.meshtalk.providers.MessageProvider;

public class ChatsFragment extends Fragment {

    private ChatsViewModel chatsViewModel;
    private ChatProvider chatProvider;
    private ContactProvider contactProvider;
    private MessageProvider messageProvider;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        chatsViewModel = ViewModelProviders.of(this).get(ChatsViewModel.class);
        chatProvider = ChatProvider.get(requireContext());
        contactProvider = ContactProvider.get(requireContext());
        messageProvider = MessageProvider.get(requireContext());

        View root = inflater.inflate(R.layout.fragment_chats, container, false);

        final ListView listView = root.findViewById(R.id.chat_list_view);
        chatsViewModel.getChats().observe(getViewLifecycleOwner(), chats -> {
            if (chats.size() > 0) {
                root.findViewById(R.id.chat_list_empty).setVisibility(View.INVISIBLE);
            } else {
                root.findViewById(R.id.chat_list_empty).setVisibility(View.VISIBLE);
            }
            ArrayAdapter<Chat> adapter = new ArrayAdapter<Chat>(requireContext(), R.layout.list_item_chat, new ArrayList<>(chats)) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = null;
                    if (convertView != null) {
                        v = convertView;
                    }
                    if (v == null) {
                        v = View.inflate(requireContext(), R.layout.list_item_chat, null);
                    }
                    Identicon identicon = v.findViewById(R.id.list_item_chat_identicon);
                    TextView title = v.findViewById(R.id.list_item_chat_title_label);
                    TextView latestMessage = v.findViewById(R.id.list_item_chat_latest_message_label);

                    Chat chat = this.getItem(position);
                    if (chat != null) {
                        v.setOnClickListener(v1 -> {
                            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                                    .putString(Preferences.CURRENT_CHAT.toString(), chat.getId().toString())
                                    .apply();
                            ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_conversations);
                        });
                        identicon.show(chat.getRecipient().toString());
                        title.setText(chat.getTitle());
                        String latestMessageText = messageProvider.decryptMessage(messageProvider.getLatestMessage(chat), chat);
                        if (latestMessageText != null) {
                            latestMessage.setText(latestMessageText);
                        } else {
                            latestMessage.setText(R.string.no_messages);
                        }
                    }
                    return v;
                }
            };
            listView.setAdapter(adapter);
        });
        updateChats();

        return root;
    }

    private void updateChats() {
        Set<Chat> chats = new TreeSet<>();
        chatProvider.getAllIds().forEach(id -> chats.add(chatProvider.getById(id)));
        chatsViewModel.setChats(chats);
    }
}

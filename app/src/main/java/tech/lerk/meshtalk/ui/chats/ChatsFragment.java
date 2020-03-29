package tech.lerk.meshtalk.ui.chats;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

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
                    ImageButton menuToggle = v.findViewById(R.id.list_item_chat_menu_toggle);
                    final PopupMenu dropDownMenu = new PopupMenu(getContext(), menuToggle);
                    final Menu menu = dropDownMenu.getMenu();
                    dropDownMenu.getMenuInflater().inflate(R.menu.list_item_chat, menu);

                    Chat chat = this.getItem(position);
                    if (chat != null) {
                        dropDownMenu.setOnMenuItemClickListener(item -> {
                            switch (item.getItemId()) {
                                case R.id.action_delete:
                                    new AlertDialog.Builder(getContext())
                                            .setTitle(R.string.dialog_delete_chat_title)
                                            .setMessage(R.string.dialog_delete_chat_message)
                                            .setNegativeButton(R.string.action_cancel, (d, w) -> d.dismiss())
                                            .setPositiveButton(R.string.action_yes, (d, w) -> {
                                                d.dismiss();
                                                Set<UUID> messages = chat.getMessages();
                                                if (messages != null) {
                                                    messages.forEach(id -> messageProvider.deleteById(id));
                                                }
                                                chatProvider.deleteById(chat.getId());
                                                updateChats();
                                            }).create().show();
                                    return true;
                                case R.id.action_redo_handshake:
                                    //TODO: implement
                                    Toast.makeText(getContext(), R.string.error_not_implemented, Toast.LENGTH_LONG).show();
                                    break;
                            }
                            return false;
                        });
                        menuToggle.setOnClickListener(v1 -> dropDownMenu.show());
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

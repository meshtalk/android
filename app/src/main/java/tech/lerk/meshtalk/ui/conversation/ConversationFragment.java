package tech.lerk.meshtalk.ui.conversation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;

import tech.lerk.meshtalk.MainActivity;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.entities.Preferences;

public class ConversationFragment extends Fragment {

    private ConversationViewModel conversationViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        conversationViewModel =
                ViewModelProviders.of(this).get(ConversationViewModel.class);
        View root = inflater.inflate(R.layout.fragment_conversation, container, false);
        final TextView textView = root.findViewById(R.id.text_conversation);
        conversationViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        if (PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext())
                .getString(Preferences.CURRENT_CHAT.toString(), Stuff.NONE).equals(Stuff.NONE)) {
            Toast.makeText(requireContext(), R.string.error_no_chat_selected, Toast.LENGTH_LONG).show();
            ((MainActivity) requireActivity()).getNavController().navigate(R.id.nav_item_chats);
        }
        return root;
    }
}

package tech.lerk.meshtalk.ui.settings;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import tech.lerk.meshtalk.KeyHolder;
import tech.lerk.meshtalk.MainActivity;
import tech.lerk.meshtalk.MessageGatewayClientService;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Preferences;
import tech.lerk.meshtalk.providers.impl.ChatProvider;
import tech.lerk.meshtalk.providers.impl.ContactProvider;
import tech.lerk.meshtalk.providers.impl.IdentityProvider;
import tech.lerk.meshtalk.providers.impl.MessageProvider;

import static tech.lerk.meshtalk.Stuff.waitOrDonT;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String TAG = SettingsFragment.class.getCanonicalName();
    private int selfDestructClickCount = 0;
    private KeyHolder keyHolder;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        Preference selfDestructPreference = Objects.requireNonNull(findPreference(Preferences.SELF_DESTRUCT.toString()));
        selfDestructPreference.setOnPreferenceClickListener(preference -> {
            if (selfDestructClickCount == 2) {
                selfDestructClickCount = 0;
                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.pref_title_self_destruct)
                        .setMessage(R.string.pref_dialog_self_destruct)
                        .setPositiveButton(R.string.action_yes, (d, w) -> selfDestruct(d))
                        .setNegativeButton(R.string.action_no, (d, w) -> d.dismiss())
                        .create();
                dialog.setOnDismissListener(d -> selfDestructClickCount = 0);
                dialog.show();
            } else {
                Toast.makeText(getContext(), R.string.toast_self_destruct, Toast.LENGTH_SHORT).show();
                selfDestructClickCount++;
            }
            return true;
        });

        keyHolder = KeyHolder.get(requireContext());
    }

    private void selfDestruct(DialogInterface d) {
        d.dismiss();
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_title_self_destruct)
                .setCancelable(false)
                .setView(R.layout.dialog_loading)
                .create();
        loadingDialog.show();
        ProgressBar progressBar = Objects.requireNonNull(loadingDialog.findViewById(R.id.loading_spinner));
        TextView loadingTextView = Objects.requireNonNull(loadingDialog.findViewById(R.id.loading_text));
        AsyncTask.execute(() -> {
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress(0, true);
                loadingTextView.setText(R.string.progress_self_destruct_stopping_services);
            });
            ((MainActivity) requireActivity()).setRunService(false);
            requireActivity().stopService(new Intent(requireActivity().getApplicationContext(), MessageGatewayClientService.class));
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress(1, false);
                loadingTextView.setText(R.string.progress_self_destruct_deleting_contacts);
            });
            ContactProvider contactProvider = ContactProvider.get(requireContext());
            contactProvider.deleteAll();
            waitOrDonT(200);
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress(10, false);
                loadingTextView.setText(R.string.progress_self_destruct_deleting_messages);
            });
            MessageProvider messageProvider = MessageProvider.get(requireContext());
            messageProvider.deleteAll();
            waitOrDonT(200);
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress(20, false);
                loadingTextView.setText(R.string.progress_self_destruct_deleting_chats);
            });
            ChatProvider chatProvider = ChatProvider.get(requireContext());
            chatProvider.deleteAll();
            waitOrDonT(200);
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress(30, false);
                loadingTextView.setText(R.string.progress_self_destruct_deleting_identities);
            });
            IdentityProvider identityProvider = IdentityProvider.get(requireContext());
            identityProvider.deleteAll();
            waitOrDonT(200);
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress(40, false);
                loadingTextView.setText(R.string.progress_self_destruct_deleting_app_key);
            });
            keyHolder.deleteAppKey();
            PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext()).edit()
                    .remove(Preferences.DEVICE_IV.toString())
                    .remove(Preferences.DEFAULT_IDENTITY.toString())
                    .remove(Preferences.CURRENT_CHAT.toString())
                    .remove(Preferences.ASK_CREATING_CHAT.toString())
                    .putBoolean(Preferences.FIRST_START.toString(), true)
                    .apply();
            waitOrDonT(250);
            requireActivity().runOnUiThread(() -> {
                loadingDialog.dismiss();
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.progress_self_destruct_finished)
                        .setMessage(R.string.progress_self_destruct_finished_message)
                        .setNeutralButton(R.string.action_okay, (d1, w) -> {
                            d.dismiss();
                            requireActivity().finishAndRemoveTask();
                        })
                        .create().show();
            });
        });
    }
}
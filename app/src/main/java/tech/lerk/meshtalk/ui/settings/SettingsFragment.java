package tech.lerk.meshtalk.ui.settings;

import android.content.DialogInterface;
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

import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.providers.KeyProvider;

import static tech.lerk.meshtalk.Stuff.waitOrDonT;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String TAG = SettingsFragment.class.getCanonicalName();
    private int selfDestructClickCount = 0;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        Preference selfDestructPreference = Objects.requireNonNull(findPreference(Preferences.SELF_DESTRUCT.toString()));
        selfDestructPreference.setOnPreferenceClickListener(preference -> {
            if (selfDestructClickCount == 2) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.pref_title_self_destruct)
                        .setMessage(R.string.pref_dialog_self_destruct)
                        .setPositiveButton(R.string.action_yes, (d, w) -> selfDestruct(d))
                        .setNegativeButton(R.string.action_no, (d, w) -> d.dismiss())
                        .create().show();
            } else {
                Toast.makeText(getContext(), R.string.toast_self_destruct, Toast.LENGTH_SHORT).show();
                selfDestructClickCount++;
            }
            return true;
        });
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
                progressBar.setProgress(1, true);
                loadingTextView.setText(R.string.progress_self_destruct_deleting_contacts);
            });
            //TODO: delete contacts...
            waitOrDonT(200);
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress(10, true);
                loadingTextView.setText(R.string.progress_self_destruct_deleting_messages);
            });
            //TODO: delete messages...
            waitOrDonT(200);
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress(20, true);
                loadingTextView.setText(R.string.progress_self_destruct_deleting_chats);
            });
            //TODO: delete chats...
            waitOrDonT(200);
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress(30, true);
                loadingTextView.setText(R.string.progress_self_destruct_deleting_identities);
            });
            //TODO: delete identities...
            waitOrDonT(200);
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress(40, true);
                loadingTextView.setText(R.string.progress_self_destruct_deleting_app_key);
            });
            KeyProvider.get().deleteAppKey();
            PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext())
                    .edit().putBoolean(Preferences.FIRST_START.toString(), true).apply();
            waitOrDonT(250);
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
    }
}
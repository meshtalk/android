package tech.lerk.meshtalk.ui.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;

import tech.lerk.meshtalk.R;

public class HostnameEditTextPreference extends EditTextPreference {
    public HostnameEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setOnBindEditTextListener(new URLBindEditTextHandler(getContext()));
    }

    public HostnameEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HostnameEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HostnameEditTextPreference(Context context) {
        super(context);
    }

    private static class URLBindEditTextHandler implements OnBindEditTextListener {

        private final Context context;

        private URLBindEditTextHandler(Context context) {
            this.context = context;
        }

        @Override
        public void onBindEditText(@NonNull EditText editText) {
            editText.setInputType(EditorInfo.TYPE_TEXT_VARIATION_URI);
            editText.setHint(R.string.hint_uri);
            editText.setImeOptions(EditorInfo.IME_ACTION_GO);
            editText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    Toast.makeText(context, "TODO: implement URL validation...", Toast.LENGTH_LONG).show();
                    return true;
                }
                return false;
            });
        }
    }
}

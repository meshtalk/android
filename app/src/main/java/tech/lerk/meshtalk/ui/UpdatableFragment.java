package tech.lerk.meshtalk.ui;

import androidx.fragment.app.Fragment;

public abstract class UpdatableFragment extends Fragment {
    public abstract void updateViews();

    @Override
    public void onResume() {
        super.onResume();
        updateViews();
    }
}

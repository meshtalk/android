package tech.lerk.meshtalk.ui.identities;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class IdentitiesViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public IdentitiesViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("TODO: Add identity list");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
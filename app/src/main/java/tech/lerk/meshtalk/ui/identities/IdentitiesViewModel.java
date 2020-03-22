package tech.lerk.meshtalk.ui.identities;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Set;

import tech.lerk.meshtalk.entities.Identity;

public class IdentitiesViewModel extends ViewModel {

    private MutableLiveData<Set<Identity>> identities;

    public IdentitiesViewModel() {
        identities = new MutableLiveData<>();
    }

    public LiveData<Set<Identity>> getIdentities() {
        return identities;
    }

    public void setIdentities(Set<Identity> identities) {
        this.identities.setValue(identities);
    }
}
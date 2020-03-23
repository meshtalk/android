package tech.lerk.meshtalk.entities.ui;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.models.IUser;

import tech.lerk.meshtalk.R;

public class UserDO implements IUser {
    private final String id;
    private final String name;

    public UserDO(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getAvatar() {
        return id;
    }

    public static class IdenticonImageLoader implements ImageLoader {
        private final Context context;

        public IdenticonImageLoader(Context context) {
            this.context = context;
        }

        @Override
        public void loadImage(ImageView imageView, @Nullable String url, @Nullable Object payload) {
            imageView.setImageDrawable(context.getDrawable(R.drawable.ic_person_black_48dp));
        }
    }
}

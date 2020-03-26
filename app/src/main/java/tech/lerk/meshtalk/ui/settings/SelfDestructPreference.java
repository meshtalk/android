package tech.lerk.meshtalk.ui.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import tech.lerk.meshtalk.R;

public class SelfDestructPreference extends Preference {
    public SelfDestructPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SelfDestructPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SelfDestructPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SelfDestructPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        int color = getContext().getColor(R.color.red);
        if (summaryView != null) {
            summaryView.setTextColor(color);
        }
        final TextView titleView = (TextView) holder.findViewById(android.R.id.title);
        if (titleView != null) {
            titleView.setTextColor(color);
        }
        final ImageView imageView = (ImageView) holder.findViewById(android.R.id.icon);
        if (imageView != null) {
            imageView.setImageTintList(new ColorStateList(new int[][]{
                    new int[]{android.R.attr.state_enabled},
                    new int[]{android.R.attr.state_checked},
                    new int[]{android.R.attr.state_pressed},
                    new int[]{-android.R.attr.state_enabled},
                    new int[]{-android.R.attr.state_checked},
                    new int[]{-android.R.attr.state_pressed}
            }, new int[]{color, color, color, color, color, color}));
        }
    }
}

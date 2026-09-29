package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import git.artdeell.mojo.R;

/**
 * Modern in-game drawer rows matching the OrynLauncher control-menu design.
 *
 * Colors intentionally come from theme attributes in item_game_menu.xml so the
 * menu follows the active launcher theme/accent instead of using fixed colors.
 */
public class GameMenuAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final CharSequence[] labels;
    private final int[] icons;

    public GameMenuAdapter(Context context, CharSequence[] labels, int[] icons) {
        this.inflater = LayoutInflater.from(context);
        this.labels = labels;
        this.icons = icons;
    }

    public void setText(int position, CharSequence text) {
        if (position < 0 || position >= labels.length) return;
        labels[position] = text;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return labels.length;
    }

    @Override
    public Object getItem(int position) {
        return labels[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.item_game_menu, parent, false);
        }

        ((TextView) view.findViewById(R.id.game_menu_text)).setText(labels[position]);

        ImageView icon = view.findViewById(R.id.game_menu_icon);
        icon.setImageResource(icons[position]);

        return view;
    }
}

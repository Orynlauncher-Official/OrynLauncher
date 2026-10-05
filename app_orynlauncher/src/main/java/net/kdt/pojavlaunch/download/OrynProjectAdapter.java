package net.kdt.pojavlaunch.download;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import git.artdeell.mojo.R;

public final class OrynProjectAdapter extends RecyclerView.Adapter<OrynProjectAdapter.ProjectHolder> {
    public interface Listener {
        void onProjectClicked(ModrinthProject project);
    }

    private final OrynContentRepository repository;
    private final Listener listener;
    private final List<ModrinthProject> items = new ArrayList<>();
    private int selectedPosition = RecyclerView.NO_POSITION;
    private int placeholderResId = R.drawable.oryn_download_mod;

    public OrynProjectAdapter(OrynContentRepository repository, Listener listener) {
        this.repository = repository;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setPlaceholder(int resourceId) {
        placeholderResId = resourceId;
        notifyDataSetChanged();
    }

    public void submitList(List<ModrinthProject> projects) {
        items.clear();
        if (projects != null) items.addAll(projects);
        android.util.Log.d("OrynDownload", "ProjectCard views requested = " + items.size());
        if (selectedPosition >= items.size()) selectedPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public void setSelectedId(String projectId) {
        int old = selectedPosition;
        selectedPosition = RecyclerView.NO_POSITION;
        for (int i = 0; i < items.size(); i++) {
            if (projectId != null && projectId.equals(items.get(i).id)) {
                selectedPosition = i;
                break;
            }
        }
        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition);
    }

    @Override public long getItemId(int position) {
        return items.get(position).id == null ? position : items.get(position).id.hashCode();
    }

    @NonNull
    @Override public ProjectHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ProjectHolder(createCard(parent));
    }

    @Override public void onBindViewHolder(@NonNull ProjectHolder holder, int position) {
        holder.bind(items.get(position), position == selectedPosition);
    }

    @Override public int getItemCount() {
        return items.size();
    }

    private View createCard(ViewGroup parent) {
        int dp = (int) (parent.getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout card = new LinearLayout(parent.getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(12 * dp, 10 * dp, 12 * dp, 10 * dp);
        card.setBackground(round(0xFF17191F, 14 * dp));
        card.setClickable(true);
        card.setFocusable(true);

        ImageView icon = new ImageView(parent.getContext());
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageResource(placeholderResId);
        card.addView(icon, new LinearLayout.LayoutParams(58 * dp, 58 * dp));

        LinearLayout body = new LinearLayout(parent.getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(12 * dp, 0, 4 * dp, 0);

        TextView title = label(parent, "", 15, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(title, new LinearLayout.LayoutParams(-1, 23 * dp));

        TextView author = label(parent, "", 10, 0xFF8E939E);
        body.addView(author, new LinearLayout.LayoutParams(-1, 19 * dp));

        TextView description = label(parent, "", 11, 0xFFC0C3CA);
        description.setMaxLines(2);
        body.addView(description, new LinearLayout.LayoutParams(-1, 36 * dp));

        TextView meta = label(parent, "", 9, 0xFF777C87);
        body.addView(meta, new LinearLayout.LayoutParams(-1, 19 * dp));

        card.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        return card;
    }

    private TextView label(ViewGroup parent, String value, float size, int color) {
        TextView view = new TextView(parent.getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    final class ProjectHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView author;
        final TextView description;
        final TextView meta;

        ProjectHolder(View itemView) {
            super(itemView);
            LinearLayout row = (LinearLayout) itemView;
            icon = (ImageView) row.getChildAt(0);
            LinearLayout body = (LinearLayout) row.getChildAt(1);
            title = (TextView) body.getChildAt(0);
            author = (TextView) body.getChildAt(1);
            description = (TextView) body.getChildAt(2);
            meta = (TextView) body.getChildAt(3);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                selectedPosition = position;
                notifyDataSetChanged();
                listener.onProjectClicked(items.get(position));
            });
        }

        void bind(ModrinthProject project, boolean selected) {
            title.setText(project.title == null || project.title.isEmpty()
                    ? "Unknown project" : project.title);
            author.setText("by " + safe(project.author));
            description.setText(project.description == null || project.description.isEmpty()
                    ? "No description available." : project.description);

            String version = project.gameVersions == null || project.gameVersions.isEmpty()
                    ? "Minecraft: —"
                    : "Minecraft: " + project.gameVersions.get(0);
            String loader = project.loaders == null || project.loaders.isEmpty()
                    ? ""
                    : " • " + project.loaders.get(0);
            meta.setText(version + loader + "  •  " + compact(project.downloads) + " downloads");

            itemView.setBackground(round(selected ? 0xFF252A34 : 0xFF17191F, 14));

            icon.setTag(project.iconUrl);
            icon.setImageResource(R.drawable.oryn_download_mod);
            if (project.iconUrl != null && !project.iconUrl.isEmpty()) {
                repository.loadIcon(project.iconUrl, new OrynContentRepository.Listener<android.graphics.Bitmap>() {
                    @Override public void onSuccess(android.graphics.Bitmap bitmap) {
                        if (project.iconUrl.equals(icon.getTag())) icon.setImageBitmap(bitmap);
                    }
                    @Override public void onError(Exception error) { }
                });
            }

            itemView.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(80).start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        break;
                }
                return false;
            });
        }

        private String safe(String value) {
            return value == null ? "Unknown author" : value;
        }

        private String compact(long value) {
            if (value >= 1000000000L) return String.format(Locale.ROOT, "%.1fB", value / 1000000000.0);
            if (value >= 1000000L) return String.format(Locale.ROOT, "%.1fM", value / 1000000.0);
            if (value >= 1000L) return String.format(Locale.ROOT, "%.1fK", value / 1000.0);
            return String.valueOf(value);
        }
    }
}

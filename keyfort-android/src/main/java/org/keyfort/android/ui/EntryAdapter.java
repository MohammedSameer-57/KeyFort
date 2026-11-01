package org.keyfort.android.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.keyfort.android.R;
import org.keyfort.core.Entry;

import java.util.ArrayList;
import java.util.List;

public class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.EntryViewHolder> {

    public interface OnEntryClickListener {
        void onEntryClick(Entry entry);
    }

    private final List<Entry> entries = new ArrayList<>();
    private OnEntryClickListener listener;

    public void setOnEntryClickListener(OnEntryClickListener listener) {
        this.listener = listener;
    }

    public void setEntries(List<Entry> newEntries) {
        entries.clear();
        if (newEntries != null) entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    public Entry getEntryAt(int position) {
        return entries.get(position);
    }

    public void removeAt(int position) {
        entries.remove(position);
        notifyItemRemoved(position);
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_entry, parent, false);
        return new EntryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position) {
        Entry entry = entries.get(position);
        holder.tvSite.setText(entry.site());
        holder.tvUsername.setText(entry.username());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEntryClick(entry);
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class EntryViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSite;
        final TextView tvUsername;

        EntryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSite = itemView.findViewById(R.id.tv_site);
            tvUsername = itemView.findViewById(R.id.tv_username);
        }
    }
}

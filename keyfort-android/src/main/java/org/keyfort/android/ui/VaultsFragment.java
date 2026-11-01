package org.keyfort.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.keyfort.android.AppSession;
import org.keyfort.android.KeyFortApp;
import org.keyfort.android.R;
import org.keyfort.android.repository.VaultRepository;
import org.keyfort.core.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VaultsFragment extends Fragment {

    private EntryAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vaults, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_entries);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        FloatingActionButton fab = view.findViewById(R.id.fab_add_entry);

        adapter = new EntryAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnEntryClickListener(entry -> {
            EntryDetailDialog dialog = EntryDetailDialog.newInstance(entry);
            dialog.setOnEntryChangedListener(() -> loadEntries());
            dialog.show(getChildFragmentManager(), "entry_detail");
        });

        // Swipe-to-delete
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                Entry entry = adapter.getEntryAt(pos);
                deleteEntry(entry, pos);
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView);

        fab.setOnClickListener(v -> {
            AddEntryDialog dialog = AddEntryDialog.newInstance();
            dialog.setOnEntrySavedListener(() -> loadEntries());
            dialog.show(getChildFragmentManager(), "add_entry");
        });

        swipeRefreshLayout.setOnRefreshListener(() -> syncVault());

        // Sync toolbar button
        view.findViewById(R.id.btn_sync).setOnClickListener(v -> syncVault());

        loadEntries();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadEntries();
    }

    private void loadEntries() {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        if (!session.isUnlocked()) return;

        Map<String, Entry> entryMap = session.getSession().entries();
        List<Entry> entries = new ArrayList<>(entryMap.values());
        adapter.setEntries(entries);
    }

    private void deleteEntry(Entry entry, int pos) {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        if (!session.isUnlocked()) {
            adapter.notifyItemChanged(pos);
            return;
        }
        executor.execute(() -> {
            try {
                session.getSession().remove(entry.id());
                VaultRepository repo = new VaultRepository(requireContext());
                repo.syncToServer(session);
                requireActivity().runOnUiThread(() -> {
                    adapter.removeAt(pos);
                    Toast.makeText(getContext(), R.string.entry_deleted, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    adapter.notifyItemChanged(pos);
                    Toast.makeText(getContext(),
                            getString(R.string.error_prefix) + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void syncVault() {
        AppSession session = KeyFortApp.getInstance().getAppSession();
        if (!session.isUnlocked()) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        swipeRefreshLayout.setRefreshing(true);
        executor.execute(() -> {
            try {
                // Push local state first, then pull latest
                VaultRepository repo = new VaultRepository(requireContext());
                repo.syncToServer(session);
                requireActivity().runOnUiThread(() -> {
                    loadEntries();
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), R.string.synced, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(),
                            getString(R.string.error_prefix) + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}

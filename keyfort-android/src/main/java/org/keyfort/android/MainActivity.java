package org.keyfort.android;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.keyfort.android.ui.GeneratorFragment;
import org.keyfort.android.ui.SendFragment;
import org.keyfort.android.ui.SettingsFragment;
import org.keyfort.android.ui.UnlockActivity;
import org.keyfort.android.ui.VaultsFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private final FragmentManager fragmentManager = getSupportFragmentManager();

    private final Fragment vaultsFragment = new VaultsFragment();
    private final Fragment sendFragment = new SendFragment();
    private final Fragment generatorFragment = new GeneratorFragment();
    private final Fragment settingsFragment = new SettingsFragment();

    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Security: prevent screenshots and screen recording in release builds
        boolean isDebuggable = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!isDebuggable) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
            );
        }
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            fragmentManager.beginTransaction()
                    .add(R.id.fragment_container, vaultsFragment, "vaults")
                    .add(R.id.fragment_container, sendFragment, "send").hide(sendFragment)
                    .add(R.id.fragment_container, generatorFragment, "generator").hide(generatorFragment)
                    .add(R.id.fragment_container, settingsFragment, "settings").hide(settingsFragment)
                    .commit();
            activeFragment = vaultsFragment;
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_vaults) {
                switchFragment(vaultsFragment);
            } else if (id == R.id.nav_send) {
                switchFragment(sendFragment);
            } else if (id == R.id.nav_generator) {
                switchFragment(generatorFragment);
            } else if (id == R.id.nav_settings) {
                switchFragment(settingsFragment);
            }
            return true;
        });
    }

    private void switchFragment(Fragment target) {
        if (activeFragment == target) return;
        fragmentManager.beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Auto-lock: wipe in-memory session when app goes to background
        AppSession session = KeyFortApp.getInstance().getAppSession();
        session.lock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppSession session = KeyFortApp.getInstance().getAppSession();
        if (!session.isUnlocked()) {
            // Vault is locked — redirect to unlock screen
            Intent intent = new Intent(this, UnlockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
    }
}

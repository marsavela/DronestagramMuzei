package la.marsave.dronestagrammuzei;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import java.util.Calendar;

/**
 * Created by sergiu on 26/06/15.
 */
public class SettingsActivity extends AppCompatActivity {

    static Boolean update = false;
    static Boolean schedule = false;

    public static void setUpdate(Boolean update) {
        SettingsActivity.update = update;
    }

    public static void setSchedule(Boolean schedule) {
        SettingsActivity.schedule = schedule;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pref_activity);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().
                    replace(android.R.id.content, new PrefsFragment())
                    .commit();
        }
    }

    public static class PrefsFragment extends PreferenceFragment {

        private SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            findPreference(getString(R.string.pref_about_key)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.pref_about_webpage))));
                    return true;
                }
            });

            findPreference(getString(R.string.pref_dronestagram_key)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.pref_dronestagram_webpage))));
                    return true;
                }
            });

            /* show correct version name & copyright year */
            try {
                findPreference(getString(R.string.pref_about_key))
                        .setSummary(getString(R.string.pref_about_summary,
                                getString(R.string.app_name),
                                getActivity().getPackageManager()
                                        .getPackageInfo(getActivity().getPackageName(), 0).versionName,
                                Calendar.getInstance().get(Calendar.YEAR)));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            sharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener(){
                @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key){
                    if (key.equals(getResources().getString(R.string.pref_cyclemode_key))) {
                        SettingsActivity.setUpdate(true);
                    } else if (key.equals(getResources().getString(R.string.pref_intervalpicker_key)) || key.equals(getResources().getString(R.string.pref_wifiswitch_key))) {
                        SettingsActivity.setSchedule(true);
                    }
                }
            };
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            view.setFitsSystemWindows(true);
        }

        @Override
         public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        }

        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
            super.onPause();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        //This method is called when the up button is pressed. Just the pop back stack.
        getSupportFragmentManager().popBackStack();
        return true;
    }

    @Override
    public void onBackPressed() {
        refresh();
        super.onBackPressed();
    }

    private void refresh() {
        if (update) {
            startService(new Intent(this, DroneMuzeiSource.class)
                    .putExtra(DroneMuzeiSource.EXTRA_UPDATE, true)
                    .setAction(DroneMuzeiSource.ACTION_UPDATE));
        } else if (schedule) {
            startService(new Intent(this, DroneMuzeiSource.class)
                    .putExtra(DroneMuzeiSource.EXTRA_SCHEDULE, true)
                    .setAction(DroneMuzeiSource.ACTION_UPDATE));
        }
    }
}

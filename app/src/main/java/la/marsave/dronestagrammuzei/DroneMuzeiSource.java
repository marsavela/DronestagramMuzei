package la.marsave.dronestagrammuzei;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;
import com.google.android.apps.muzei.api.UserCommand;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import retrofit.ErrorHandler;
import retrofit.RestAdapter;
import retrofit.RetrofitError;

/**
 * Created by sergiu on 24/06/15.
 */
public class DroneMuzeiSource  extends RemoteMuzeiArtSource {

    /* preferences */
    private SharedPreferences prefs;
    private boolean isRandom;
    private boolean isRefreshOnWifiOnly;

    private String currentToken;

    public static final String ACTION_UPDATE = "la.marsave.donestagrammuzei.action.REFRESH";
    public static final String EXTRA_SCHEDULE = "la.marsave.donestagrammuzei.extra.SCHEDULE_NEXT";
    public static final String EXTRA_UPDATE = "la.marsave.donestagrammuzei.extra.SHOULD_REFRESH";

    public DroneMuzeiSource() {
        super(Config.SOURCE_NAME);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        isRandom = prefs.getBoolean(getString(R.string.pref_cyclemode_key), true);
        isRefreshOnWifiOnly = prefs.getBoolean(getString(R.string.pref_wifiswitch_key), false);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            super.onHandleIntent(null);
            return;
        }

        String action = intent.getAction();
        if (ACTION_UPDATE.equals(action)) {
            if (intent.hasExtra(EXTRA_UPDATE)) {
                onUpdate(UPDATE_REASON_USER_NEXT);
            } else if (intent.hasExtra(EXTRA_SCHEDULE)) {
                scheduleNext();
            }

        }

        super.onHandleIntent(intent);
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {
        currentToken = (getCurrentArtwork() != null) ? getCurrentArtwork().getToken() : null;

        List<UserCommand> commands = new ArrayList<>();
        commands.add(new UserCommand(BUILTIN_COMMAND_ID_NEXT_ARTWORK));
        commands.add(new UserCommand(Config.COMMAND_ID_SHARE, getString(R.string.action_share_artwork)));
        if (BuildConfig.DEBUG) {
            commands.add(new UserCommand(Config.COMMAND_ID_DEBUG_INFO, "Debug info"));
        }

        setUserCommands(commands);

        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint("http://www.dronestagr.am")
                .setErrorHandler(new ErrorHandler() {
                    @Override
                    public Throwable handleError(RetrofitError retrofitError) {
                        int statusCode = retrofitError.getResponse().getStatus();
                        if (retrofitError.getKind() == RetrofitError.Kind.NETWORK
                                || (500 <= statusCode && statusCode < 600)) {
                            return new RetryException();
                        }
                        scheduleUpdate(System.currentTimeMillis() + Config.ROTATE_TIME_MILLIS);
                        return retrofitError;
                    }
                })
                .build();

        DroneMuzeiService service = restAdapter.create(DroneMuzeiService.class);
        DroneMuzeiService.DataResponse dataResponse;

        if (isRandom) {
            dataResponse = service.getData(new Random().nextInt(Config.NUMBER_OF_PAGES));
        } else {
            dataResponse = service.getData();
        }

        if (dataResponse == null || dataResponse.posts == null || (isRefreshOnWifiOnly && !isConnectedWifi())) {
            throw new RetryException();
        }

        if (dataResponse.posts.size() == 0) {
            Log.w(Config.TAG, "No posts returned from API.");
            scheduleUpdate(System.currentTimeMillis() + Config.ROTATE_TIME_MILLIS);
            return;
        }

        DroneMuzeiService.Post post;
        String token;
        while (true) {
            post = dataResponse.posts.get(new Random().nextInt(dataResponse.posts.size()));
            token = Integer.toString(post.getId());
            if (dataResponse.posts.size() <= 1 || !TextUtils.equals(token, currentToken)) {
                break;
            }
        }

        publishArtwork(new Artwork.Builder()
                .title(post.getTitlePlain())
                .byline("By " + post.getAuthor().getName())
                .imageUri(Uri.parse(post.getThumbnailImages().getFull().getUrl()))
                .token(token)
                .viewIntent(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(post.getUrl())))
                .build());

        this.scheduleNext();
    }

    private void publishNextArtwork() throws RetryException {


    }

    @Override
    protected void onCustomCommand(int id) {
        super.onCustomCommand(id);
        if (Config.COMMAND_ID_SHARE == id) {
            Artwork currentArtwork = getCurrentArtwork();
            if (currentArtwork == null) {
                Log.w(Config.TAG, "No current artwork, can't share.");
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(DroneMuzeiSource.this,
                                R.string.dronestagram_source_error_no_artwork_to_share,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }

            String detailUrl = currentArtwork.getViewIntent().getDataString();
            String artist = currentArtwork.getByline()
                    .replaceFirst("\\.\\s*($|\\n).*", "").trim();

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "My Android wallpaper today is '"
                    + currentArtwork.getTitle().trim()
                    + artist
                    + ". #Dronestagram\n\n"
                    + detailUrl);
            shareIntent = Intent.createChooser(shareIntent, "Share picture");
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(shareIntent);

        } else  if (Config.COMMAND_ID_DEBUG_INFO == id) {
            long nextUpdateTimeMillis = getSharedPreferences()
                    .getLong("scheduled_update_time_millis", 0);
            final String nextUpdateTime;
            if (nextUpdateTimeMillis > 0) {
                Date d = new Date(nextUpdateTimeMillis);
                nextUpdateTime = SimpleDateFormat.getDateTimeInstance().format(d);
            } else {
                nextUpdateTime = "None";
            }

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DroneMuzeiSource.this,
                            "Next update time: " + nextUpdateTime,
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void scheduleNext() {

        int intervalTimeMilis;
        if(isRandom) {
            intervalTimeMilis = Integer.parseInt(
                    prefs.getString(getString(R.string.pref_intervalpicker_key), getString(R.string.pref_intervalpicker_defaultvalue))
            ) * 60 * 1000;
        } else {
            intervalTimeMilis = Config.ROTATE_TIME_MILLIS;
        }

        scheduleUpdate(System.currentTimeMillis() + intervalTimeMilis);
    }

    /**
     * is connected to wifi
     *
     * @return boolean
     */
    private boolean isConnectedWifi() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI);
    }
}
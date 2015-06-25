package la.marsave.dronestagrammuzei;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
    private static final String TAG = "Dronestagram";
    private static final String SOURCE_NAME = "DroneMuzeiSource";

    private static final int NUMBER_OF_PAGES = 1700;

    private static final int COMMAND_ID_SHARE = 1;
    private static final int COMMAND_ID_DEBUG_INFO = 51;

    private static final int ROTATE_TIME_MILLIS = 3 * 60 * 60 * 1000; // rotate every 3 hours

    public DroneMuzeiSource() {
        super(SOURCE_NAME);
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {
        String currentToken = (getCurrentArtwork() != null) ? getCurrentArtwork().getToken() : null;

        List<UserCommand> commands = new ArrayList<>();
        commands.add(new UserCommand(BUILTIN_COMMAND_ID_NEXT_ARTWORK));
        commands.add(new UserCommand(COMMAND_ID_SHARE, getString(R.string.action_share_artwork)));
        if (BuildConfig.DEBUG) {
            commands.add(new UserCommand(COMMAND_ID_DEBUG_INFO, "Debug info"));
        }

        setUserCommands(commands);

        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint("http://www.dronestagr.am")
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setErrorHandler(new ErrorHandler() {
                    @Override
                    public Throwable handleError(RetrofitError retrofitError) {
                        int statusCode = retrofitError.getResponse().getStatus();
                        if (retrofitError.getKind() == RetrofitError.Kind.NETWORK
                                || (500 <= statusCode && statusCode < 600)) {
                            return new RetryException();
                        }
                        scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
                        return retrofitError;
                    }
                })
                .build();

        Random random = new Random();

        DroneMuzeiService service = restAdapter.create(DroneMuzeiService.class);
        int ran = random.nextInt(NUMBER_OF_PAGES);
        Log.d("Random",Integer.toString(ran));
        DroneMuzeiService.DataResponse dataResponse = service.getData(ran);

        if (dataResponse == null || dataResponse.posts == null) {
            throw new RetryException();
        }

        if (dataResponse.posts.size() == 0) {
            Log.w(TAG, "No posts returned from API.");
            scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
            return;
        }

        DroneMuzeiService.Post post;
        String token;
        while (true) {
            post = dataResponse.posts.get(random.nextInt(dataResponse.posts.size()));
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

        scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
    }

    @Override
    protected void onCustomCommand(int id) {
        super.onCustomCommand(id);
        if (COMMAND_ID_SHARE == id) {
            Artwork currentArtwork = getCurrentArtwork();
            if (currentArtwork == null) {
                Log.w(TAG, "No current artwork, can't share.");
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

        } else  if (COMMAND_ID_DEBUG_INFO == id) {
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
}
/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.LongSparseArray;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.TeeAudioProcessor;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.SurfaceNotValidException;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FourierTransform;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.castserver.webserver.SimpleWebServer;
import org.telegram.messenger.castserver.webserver.WebServerUtil;
import org.telegram.messenger.secretmedia.ExtendedDefaultDataSourceFactory;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@SuppressLint("NewApi")
public class VideoPlayer implements Player.Listener, VideoListener, AnalyticsListener, NotificationCenter.NotificationCenterDelegate {

    private DispatchQueue workerQueue;
    private boolean isStory;

    public boolean createdWithAudioTrack() {
        return !audioDisabled;
    }

    public interface VideoPlayerDelegate {
        void onStateChanged(boolean playWhenReady, int playbackState);
        void onError(VideoPlayer player, Exception e);
        void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio);
        void onRenderedFirstFrame();
        void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture);
        boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture);
        default void onRenderedFirstFrame(EventTime eventTime) {

        }
        default void onSeekStarted(EventTime eventTime) {

        }
        default void onSeekFinished(AnalyticsListener.EventTime eventTime) {

        }

        default void onCastStateChanged(int newState) {
            // TODO: Implement
        }
    }

    public interface AudioVisualizerDelegate {
        void onVisualizerUpdate(boolean playing, boolean animate, float[] values);
        boolean needUpdate();
    }

    private final CastStateListener castStateListener = newState -> updateCastState(newState);

    private final SessionManagerListener<Session> sessionManagerListener = new SessionManagerListener<Session>() {
        @Override
        public void onSessionStarted(Session session, String sessionId) {
            setCurrentPlayer(castPlayer);
            if (waitingToPlay) {
                waitingToPlay = false;
                // Wait a moment for RemoteMediaClient to be fully initialized
                new Handler(Looper.getMainLooper()).postDelayed(loadingRequestRunnable, 500);
            }
        }

        @Override
        public void onSessionResumed(Session session, boolean wasSuspended) {
            setCurrentPlayer(castPlayer);
            if (waitingToPlay) {
                waitingToPlay = false;
                // Wait a moment for RemoteMediaClient to be fully initialized
                new Handler(Looper.getMainLooper()).postDelayed(loadingRequestRunnable, 500);
            }
        }

        @Override
        public void onSessionEnded(Session session, int error) {
            setCurrentPlayer(player);
            // Clear any pending requests
            pendingLoadRequest = null;
            waitingToPlay = false;
        }

        // Implement other methods if necessary
        @Override public void onSessionStarting(Session session) {}
        @Override public void onSessionStartFailed(Session session, int error) {}
        @Override public void onSessionEnding(Session session) {}
        @Override public void onSessionResumeFailed(Session session, int error) {}
        @Override public void onSessionSuspended(Session session, int reason) {}
        @Override public void onSessionResuming(Session session, String sessionId) {}
    };

    private Player currentPlayer;
    public ExoPlayer player;
    private ExoPlayer audioPlayer;
    private CastPlayer castPlayer;
    private CastContext castContext;
    private CastSession currentCastSession;
    private boolean waitingToPlay = false;
    private MediaLoadRequestData pendingLoadRequest = null;
    private Runnable loadingRequestRunnable = this::tryLoadMedia;
    private DefaultBandwidthMeter bandwidthMeter;
    private MappingTrackSelector trackSelector;
    private ExtendedDefaultDataSourceFactory mediaDataSourceFactory;
    private TextureView textureView;
    private SurfaceView surfaceView;
    private Surface surface;
    private boolean isStreaming;
    private boolean autoplay;
    private boolean mixedAudio;
    public boolean allowMultipleInstances;

    private boolean triedReinit;

    private Uri currentUri;

    private boolean videoPlayerReady;
    private boolean audioPlayerReady;
    private boolean mixedPlayWhenReady;

    private VideoPlayerDelegate delegate;
    private AudioVisualizerDelegate audioVisualizerDelegate;
    private int lastReportedPlaybackState;
    private boolean lastReportedPlayWhenReady;

    private ArrayList<Quality> videoQualities;
    private Quality videoQualityToSelect;
    private ArrayList<VideoUri> manifestUris;
    private Uri videoUri, audioUri;
    private String videoType, audioType;
    private boolean loopingMediaSource;
    private boolean looping;
    private int repeatCount;

    private boolean shouldPauseOther;
    MediaSource.Factory dashMediaSourceFactory;
    HlsMediaSource.Factory hlsMediaSourceFactory;
    SsMediaSource.Factory ssMediaSourceFactory;
    ProgressiveMediaSource.Factory progressiveMediaSourceFactory;

    Handler audioUpdateHandler = new Handler(Looper.getMainLooper());

    boolean audioDisabled;

    public VideoPlayer() {
        this(true, false);
    }

    static int playerCounter = 0;
    public VideoPlayer(boolean pauseOther, boolean audioDisabled) {
        this.audioDisabled = audioDisabled;
        mediaDataSourceFactory = new ExtendedDefaultDataSourceFactory(ApplicationLoader.applicationContext, "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)");
        trackSelector = new DefaultTrackSelector(ApplicationLoader.applicationContext, new AdaptiveTrackSelection.Factory());
        if (audioDisabled) {
            trackSelector.setParameters(trackSelector.getParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
        }
        lastReportedPlaybackState = ExoPlayer.STATE_IDLE;
        shouldPauseOther = pauseOther;
        if (pauseOther) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.playerDidStartPlaying);
        }

        //Crash on stories
        if (!isStory) {
            try {
                initCastPlayer();
            } catch (Exception e) {
                FileLog.e("Cast initialization failed", e);
            }
        }
    }

    private void initCastPlayer() {
        castContext = CastContext.getSharedInstance();
        currentCastSession = castContext.getSessionManager().getCurrentCastSession();

        castPlayer = new CastPlayer(castContext);
        currentPlayer = player;  // Keep local player as initial player

        castPlayer.setRepeatMode(looping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
        castPlayer.setPlayWhenReady(autoplay);

        castPlayer.addListener(this);

        castContext.addCastStateListener(castStateListener);
        castContext.getSessionManager().addSessionManagerListener(sessionManagerListener, Session.class);
    }


    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.playerDidStartPlaying) {
            VideoPlayer p = (VideoPlayer) args[0];
            if (p != this && isPlaying() && !allowMultipleInstances) {
                pause();
            }
        }
    }

    private void ensurePlayerCreated() {
        DefaultLoadControl loadControl;
        if (isStory) {
            loadControl = new DefaultLoadControl(
                    new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    1000,
                    1000,
                    DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES,
                    DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
                    DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS,
                    DefaultLoadControl.DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
        } else {
            loadControl = new DefaultLoadControl(
                    new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    100,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                    DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES,
                    DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
                    DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS,
                    DefaultLoadControl.DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
        }
        if (player == null) {
            DefaultRenderersFactory factory;
            if (audioVisualizerDelegate != null) {
                factory = new AudioVisualizerRenderersFactory(ApplicationLoader.applicationContext);
            } else {
                factory = new DefaultRenderersFactory(ApplicationLoader.applicationContext);
            }
            factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
            player = new ExoPlayer.Builder(ApplicationLoader.applicationContext).setRenderersFactory(factory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl).build();

            player.addAnalyticsListener(this);
            player.addListener(this);
            player.addVideoListener(this);
            if (textureView != null) {
                player.setVideoTextureView(textureView);
            } else if (surface != null) {
                player.setVideoSurface(surface);
            } else if (surfaceView != null) {
                player.setVideoSurfaceView(surfaceView);
            }
            player.setPlayWhenReady(autoplay);
            player.setRepeatMode(looping ? ExoPlayer.REPEAT_MODE_ALL : ExoPlayer.REPEAT_MODE_OFF);
        }
        if (mixedAudio) {
            if (audioPlayer == null) {
                audioPlayer = new ExoPlayer.Builder(ApplicationLoader.applicationContext)
                        .setTrackSelector(trackSelector)
                        .setLoadControl(loadControl).buildSimpleExoPlayer();
                audioPlayer.addListener(new Player.Listener() {

                    @Override
                    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                        if (!audioPlayerReady && playbackState == Player.STATE_READY) {
                            audioPlayerReady = true;
                            checkPlayersReady();
                        }
                    }
                });
                audioPlayer.setPlayWhenReady(autoplay);
            }
        }
    }

    public void preparePlayerLoop(Uri videoUri, String videoType, Uri audioUri, String audioType) {
        this.videoQualities = null;
        this.videoQualityToSelect = null;
        this.videoUri = videoUri;
        this.audioUri = audioUri;
        this.videoType = videoType;
        this.audioType = audioType;
        this.loopingMediaSource = true;
        currentStreamIsHls = false;

        mixedAudio = true;
        audioPlayerReady = false;
        videoPlayerReady = false;
        ensurePlayerCreated();
        MediaSource mediaSource1 = null, mediaSource2 = null;
        for (int a = 0; a < 2; a++) {
            MediaSource mediaSource;
            String type;
            Uri uri;
            if (a == 0) {
                type = videoType;
                uri = videoUri;
            } else {
                type = audioType;
                uri = audioUri;
            }
            mediaSource = mediaSourceFromUri(uri, type);
            mediaSource = new LoopingMediaSource(mediaSource);
            if (a == 0) {
                mediaSource1 = mediaSource;
            } else {
                mediaSource2 = mediaSource;
            }
        }
        player.setMediaSource(mediaSource1, true);
        player.prepare();
        audioPlayer.setMediaSource(mediaSource2, true);
        audioPlayer.prepare();
    }

    private MediaSource mediaSourceFromUri(Uri uri, String type) {
        MediaItem mediaItem = new MediaItem.Builder().setUri(uri).build();
        switch (type) {
            case "dash":
                if (dashMediaSourceFactory == null) {
                    dashMediaSourceFactory = new DashMediaSource.Factory(mediaDataSourceFactory);
                }
                return dashMediaSourceFactory.createMediaSource(mediaItem);
            case "hls":
                if (hlsMediaSourceFactory == null) {
                    hlsMediaSourceFactory = new HlsMediaSource.Factory(mediaDataSourceFactory);
                }
                return hlsMediaSourceFactory.createMediaSource(mediaItem);
            case "ss":
                if (ssMediaSourceFactory == null) {
                    ssMediaSourceFactory = new SsMediaSource.Factory(mediaDataSourceFactory);
                }
                return ssMediaSourceFactory.createMediaSource(mediaItem);
            default:
                if (progressiveMediaSourceFactory == null) {
                    progressiveMediaSourceFactory = new ProgressiveMediaSource.Factory(mediaDataSourceFactory);
                }
                return progressiveMediaSourceFactory.createMediaSource(mediaItem);
        }
    }

    public void preparePlayer(Uri uri, String type) {
        preparePlayer(uri, type, FileLoader.PRIORITY_HIGH);
    }

    public void preparePlayer(Uri uri, String type, int priority) {
        this.videoQualities = null;
        this.videoQualityToSelect = null;
        this.videoUri = uri;
        this.videoType = type;
        this.audioUri = null;
        this.audioType = null;
        this.loopingMediaSource = false;
        currentStreamIsHls = false;

        videoPlayerReady = false;
        mixedAudio = false;
        currentUri = uri;
        String scheme = uri != null ? uri.getScheme() : null;
        isStreaming = scheme != null && !scheme.startsWith("file");
        ensurePlayerCreated();
//        MediaSource mediaSource = mediaSourceFromUri(uri, type);
//        player.setMediaSource(mediaSource, true);
//        player.prepare();

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(uri)
                .setMimeType(getMimeType(type))
                .setMediaMetadata(new com.google.android.exoplayer2.MediaMetadata.Builder()
                        .setTitle(getMediaTitle(uri))
                        .build())
                .build();
        // If casting, use CastPlayer
        if (isCasting() && castPlayer != null) {
            // Stop local player if playing
            if (player != null && player.isPlaying()) {
                player.stop();
            }

            castPlayer.setMediaItem(mediaItem);
            castPlayer.prepare();
            currentPlayer = castPlayer;
        } else if (player != null) {
            // For local player, use MediaSource
            MediaSource mediaSource = mediaSourceFromUri(uri, type);
            player.setMediaSource(mediaSource, true);
            player.prepare();
            currentPlayer = player;
        }
    }

    private String getMediaTitle(Uri uri) {
        if (uri == null) return "";
        String lastSegment = uri.getLastPathSegment();
        if (lastSegment != null) {
            // Clean up filename for display
            return lastSegment.replaceAll("\\d+_", "")
                    .replaceAll("\\.\\w+$", "");
        }
        return "";
    }

    private String getMimeType(String type) {
        switch (type) {
            case "dash":
                return "application/dash+xml";
            case "hls":
                return "application/x-mpegURL";
            case "ss":
                return "application/vnd.ms-sstr+xml";
            default:
                return "video/mp4";
        }
    }

    private String getDirectUrl(Uri uri) {
        try {
            if (uri.getScheme() != null && uri.getScheme().equals("tg")) {
                // Parse the parameters from the tg:// URI
                String query = uri.getEncodedQuery();
                if (query == null) return null;

                // Extract necessary parameters
                int dc = -1;
                long id = -1;
                String hash = null;

                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2) {
                        if (pair[0].equals("id")) {
                            id = Long.parseLong(pair[1]);
                        } else if (pair[0].equals("dc")) {
                            dc = Integer.parseInt(pair[1]);
                        } else if (pair[0].equals("hash")) {
                            hash = pair[1];
                        }
                    }
                }

                if (dc != -1 && id != -1) {
                    // Construct CDN URL
                    return String.format("https://cdn%d.telegram-cdn.org/file/%d?hash=%s", dc, id, hash);
                }
            }
        } catch (Exception e) {
            FileLog.e("Error getting direct URL", e);
        }
        return null;
    }


    public void preparePlayer(ArrayList<Quality> uris, Quality select) {
        this.videoQualities = uris;
        this.videoQualityToSelect = select;
        this.videoUri = null;
        this.videoType = "hls";
        this.audioUri = null;
        this.audioType = null;
        this.loopingMediaSource = false;

        videoPlayerReady = false;
        mixedAudio = false;
        currentUri = null;
        isStreaming = true;
        ensurePlayerCreated();

        currentStreamIsHls = false;
        setSelectedQuality(true, select);
    }

    public static Quality getSavedQuality(ArrayList<Quality> qualities, MessageObject messageObject) {
        if (messageObject == null) return null;
        return getSavedQuality(qualities, messageObject.getDialogId(), messageObject.getId());
    }

    public static Quality getSavedQuality(ArrayList<Quality> qualities, long did, int mid) {
        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("media_saved_pos", Activity.MODE_PRIVATE);
        final String setting = preferences.getString(did + "_" + mid + "q2", "");
        if (TextUtils.isEmpty(setting)) return null;
        for (Quality q : qualities) {
            final String idx = q.width + "x" + q.height + (q.original ? "s" : "");
            if (TextUtils.equals(setting, idx)) return q;
        }
        return null;
    }

    public static void saveQuality(Quality q, MessageObject messageObject) {
        if (messageObject == null) return;
        saveQuality(q, messageObject.getDialogId(), messageObject.getId());
    }

    public static void saveQuality(Quality q, long did, int mid) {
        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("media_saved_pos", Activity.MODE_PRIVATE);
        final SharedPreferences.Editor editor = preferences.edit();
        if (q == null) {
            editor.remove(did + "_" + mid + "q2");
        } else {
            editor.putString(did + "_" + mid + "q2", q.width + "x" + q.height + (q.original ? "s" : ""));
        }
        editor.apply();
    }

    public static final int QUALITY_AUTO = -1; // HLS
    private int selectedQualityIndex = QUALITY_AUTO;
    private boolean currentStreamIsHls;

    public Quality getQuality(int index) {
        if (videoQualities == null) return getHighestQuality(false);
        if (index < 0 || index >= videoQualities.size()) return getHighestQuality(false);
        return videoQualities.get(index);
    }

    public Quality getHighestQuality(Boolean original) {
        Quality max = null;
        for (int i = 0; i < getQualitiesCount(); ++i) {
            final Quality q = getQuality(i);
            if (original != null && q.original != original) continue;
            if (max == null || max.width * max.height < q.width * q.height) {
                max = q;
            }
        }
        return max;
    }

    public int getHighestQualityIndex(Boolean original) {
        int maxIndex = -1;
        Quality max = null;
        for (int i = 0; i < getQualitiesCount(); ++i) {
            final Quality q = getQuality(i);
            if (original != null && q.original != original) continue;
            if (max == null || max.width * max.height < q.width * q.height) {
                max = q;
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    public Quality getLowestQuality() {
        Quality min = null;
        for (int i = 0; i < getQualitiesCount(); ++i) {
            final Quality q = getQuality(i);
            if (min == null || min.width * min.height > q.width * q.height) {
                min = q;
            }
        }
        return min;
    }

    public int getQualitiesCount() {
        if (videoQualities == null) return 0;
        return videoQualities.size();
    }

    public int getSelectedQuality() {
        return selectedQualityIndex;
    }

    public int getCurrentQualityIndex() {
        if (selectedQualityIndex == QUALITY_AUTO) {
            try {
                final MappingTrackSelector.MappedTrackInfo mapTrackInfo = trackSelector.getCurrentMappedTrackInfo();
                for (int renderIndex = 0; renderIndex < mapTrackInfo.getRendererCount(); ++renderIndex) {
                    final TrackGroupArray trackGroups = mapTrackInfo.getTrackGroups(renderIndex);
                    for (int groupIndex = 0; groupIndex < trackGroups.length; ++groupIndex) {
                        final TrackGroup trackGroup = trackGroups.get(groupIndex);
                        for (int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
                            final Format format = trackGroup.getFormat(trackIndex);
                            int formatIndex;
                            try {
                                formatIndex = Integer.parseInt(format.id);
                            } catch (Exception e) {
                                formatIndex = -1;
                            }
                            if (formatIndex >= 0) {
                                int formatOrder = 0;
                                for (int j = 0; j < getQualitiesCount(); ++j) {
                                    final Quality q = getQuality(j);
                                    for (int i = 0; i < q.uris.size(); ++i){
                                        if (q.uris.get(i).m3u8uri != null) {
                                            if (formatOrder == formatIndex) {
                                                return j;
                                            }
                                            formatOrder++;
                                        }
                                    }
                                }
                            }
                            for (int j = 0; j < getQualitiesCount(); ++j) {
                                final Quality q = getQuality(j);
                                if (format.width == q.width && format.height == q.height) {
                                    return j;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                return -1;
            }
        }
        return selectedQualityIndex;
    }

    private TrackSelectionOverride getQualityTrackSelection(VideoUri videoUri) {
        try {
            int qualityOrder = manifestUris.indexOf(videoUri);
            final MappingTrackSelector.MappedTrackInfo mapTrackInfo = trackSelector.getCurrentMappedTrackInfo();
            for (int renderIndex = 0; renderIndex < mapTrackInfo.getRendererCount(); ++renderIndex) {
                final TrackGroupArray trackGroups = mapTrackInfo.getTrackGroups(renderIndex);
                for (int groupIndex = 0; groupIndex < trackGroups.length; ++groupIndex) {
                    final TrackGroup trackGroup = trackGroups.get(groupIndex);
                    for (int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
                        final Format format = trackGroup.getFormat(trackIndex);

                        int formatIndex;
                        try {
                            formatIndex = Integer.parseInt(format.id);
                        } catch (Exception e) {
                            formatIndex = -1;
                        }
                        if (formatIndex >= 0) {
                            if (qualityOrder == formatIndex) {
                                return new TrackSelectionOverride(trackGroup, trackIndex);
                            }
                        }
                        if (format.width == videoUri.width && format.height == videoUri.height) {
                            return new TrackSelectionOverride(trackGroup, trackIndex);
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    @Override
    public void onTrackSelectionParametersChanged(TrackSelectionParameters parameters) {
        Player.Listener.super.onTrackSelectionParametersChanged(parameters);
        if (onQualityChangeListener != null) {
            AndroidUtilities.runOnUIThread(onQualityChangeListener);
        }
    }

    private long fallbackDuration = C.TIME_UNSET;
    private long fallbackPosition = C.TIME_UNSET;

    public void setSelectedQuality(int index) {
        if (player == null) return;
        if (index != selectedQualityIndex) {
            selectedQualityIndex = index;
            Quality q = null;
            if (videoQualities != null && index >= 0 && index < videoQualities.size())  q = videoQualities.get(index);
            setSelectedQuality(false, q);
        }
    }

    private void setSelectedQuality(boolean start, Quality quality) {
        if (player == null) return;

        final boolean lastPlaying = player.isPlaying();
        final long lastPosition = player.getCurrentPosition();
        if (!start) {
            fallbackPosition = lastPosition;
            fallbackDuration = player.getDuration();
        }

        boolean reset = false;

        videoQualityToSelect = quality;
        if (quality == null) { // AUTO
            final Uri hlsManifest = makeManifest(videoQualities);
            if (hlsManifest != null) {
                trackSelector.setParameters(trackSelector.getParameters().buildUpon().clearOverrides().build());
                if (!currentStreamIsHls) {
                    currentStreamIsHls = true;
                    player.setMediaSource(mediaSourceFromUri(hlsManifest, "hls"), false);
                    reset = true;
                }
            } else {
                quality = getHighestQuality(true);
                if (quality == null) quality = getHighestQuality(false);
                if (quality == null || quality.uris.isEmpty()) return;
                currentStreamIsHls = false;
                videoQualityToSelect = quality;
                player.setMediaSource(mediaSourceFromUri(quality.uris.get(0).uri, "other"), false);
                reset = true;
            }
        } else {
            if (quality.uris.isEmpty()) return;
            Uri hlsManifest = null;
            if (quality.uris.size() > 1) {
                hlsManifest = makeManifest(videoQualities);
            }
            if (hlsManifest == null || quality.uris.size() == 1) {
                currentStreamIsHls = false;
                player.setMediaSource(mediaSourceFromUri(quality.uris.get(0).uri, "other"), false);
                reset = true;
            } else {
                if (!currentStreamIsHls) {
                    currentStreamIsHls = true;
                    player.setMediaSource(mediaSourceFromUri(hlsManifest, "hls"), false);
                    reset = true;
                }
                TrackSelectionParameters.Builder selector = trackSelector.getParameters().buildUpon().clearOverrides();
                for (VideoUri uri : quality.uris) {
                    TrackSelectionOverride override = getQualityTrackSelection(uri);
                    if (override == null) continue;
                    selector.addOverride(override);
                }
                trackSelector.setParameters(selector.build());
            }
        }

        if (reset) {
            player.prepare();
            if (!start) {
                player.seekTo(lastPosition);
                if (lastPlaying) {
                    player.play();
                }
            }
        }
    }

    public Quality getCurrentQuality() {
        final int index = getCurrentQualityIndex();
        if (index < 0 || index >= getQualitiesCount()) return null;
        return getQuality(index);
    }

    private Runnable onQualityChangeListener;
    public void setOnQualityChangeListener(Runnable listener) {
        this.onQualityChangeListener = listener;
    }

    public static ArrayList<Quality> getQualities(int currentAccount, TLRPC.Document original, ArrayList<TLRPC.Document> alt_documents, int reference, boolean forThumb) {
        ArrayList<TLRPC.Document> documents = new ArrayList<>();
        if (original != null) {
            documents.add(original);
        }
        if (!MessagesController.getInstance(currentAccount).videoIgnoreAltDocuments && alt_documents != null) {
            documents.addAll(alt_documents);
        }

        final LongSparseArray<TLRPC.Document> manifests = new LongSparseArray<>();
        for (int i = 0; i < documents.size(); ++i) {
            final TLRPC.Document document = documents.get(i);
            if ("application/x-mpegurl".equalsIgnoreCase(document.mime_type)) {
                if (document.file_name_fixed == null || !document.file_name_fixed.startsWith("mtproto")) continue;
                try {
                    long videoDocumentId = Long.parseLong(document.file_name_fixed.substring(7));
                    manifests.put(videoDocumentId, document);
                    documents.remove(i);
                    i--;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        ArrayList<VideoUri> result = new ArrayList<>();
        for (int i = 0; i < documents.size(); ++i) {
            try {
                final TLRPC.Document document = documents.get(i);
                if ("application/x-mpegurl".equalsIgnoreCase(document.mime_type)) {
                    continue;
                }
                VideoUri q = VideoUri.of(currentAccount, document, manifests.get(document.id), reference);
                if (q.width <= 0 || q.height <= 0) {
                    continue;
                }
                if (document == original) {
                    q.original = true;
                }
                result.add(q);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        ArrayList<VideoUri> filtered = new ArrayList<>();
        for (int i = 0; i < result.size(); ++i) {
            final VideoUri q = result.get(i);
            if (q.codec != null) {
                if (forThumb) {
                    if (!("avc".equals(q.codec) || "h264".equals(q.codec) || "h265".equals(q.codec) || "hevc".equals(q.codec) || "vp9".equals(q.codec) || "vp8".equals(q.codec))) {
                        continue;
                    }
                } else {
                    if (("av1".equals(q.codec) || "hevc".equals(q.codec) || "vp9".equals(q.codec)) && !supportsHardwareDecoder(q.codec)) {
                        continue;
                    }
                }
            }
            filtered.add(q);
        }

        ArrayList<VideoUri> qualities = new ArrayList<>();
        if (filtered.isEmpty())
            qualities.addAll(result);
        else
            qualities.addAll(filtered);

        return Quality.groupBy(qualities);
    }

    public static boolean hasQualities(int currentAccount, TLRPC.MessageMedia media) {
        if (!(media instanceof TLRPC.TL_messageMediaDocument))
            return false;
        ArrayList<Quality> qualities = getQualities(currentAccount, media.document, media.alt_documents, 0, false);
        return qualities != null && qualities.size() > 1;
    }

    public static TLRPC.Document getDocumentForThumb(int currentAccount, TLRPC.MessageMedia media) {
        if (!(media instanceof TLRPC.TL_messageMediaDocument))
            return null;
        ArrayList<Quality> qualities = getQualities(currentAccount, media.document, media.alt_documents, 0, true);
        final int MAX_SIZE = 860;
        VideoUri uri = null;
        for (final Quality q : qualities) {
            for (final VideoUri v : q.uris) {
                if ((uri == null || uri.width * uri.height < v.width * v.height) && v.width <= MAX_SIZE && v.height <= MAX_SIZE) {
                    uri = v;
                }
            }
        }
        if (uri == null) {
            for (final Quality q : qualities) {
                for (final VideoUri v : q.uris) {
                    if ((uri == null || uri.width * uri.height > v.width * v.height)){
                        uri = v;
                    }
                }
            }
        }
        return uri == null ? null : uri.document;
    }

    public static boolean supportsHardwareDecoder(String codec) {
        try {
            switch (codec) {
                case "h264":
                case "avc": codec = "video/avc"; break;
                case "vp8": codec = "video/x-vnd.on2.vp8"; break;
                case "vp9": codec = "video/x-vnd.on2.vp9"; break;
                case "h265":
                case "hevc": codec = "video/hevc"; break;
                case "av1": case "av01": codec = "video/av01"; break;
                default: codec = "video/" + codec; break;
            }
            final int count = MediaCodecList.getCodecCount();
            for (int i = 0; i < count; i++) {
                final MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder()) continue;
                if (!MediaCodecUtil.isHardwareAccelerated(info, codec)) continue;
                final String[] supportedTypes = info.getSupportedTypes();
                for (int j = 0; j < supportedTypes.length; ++j) {
                    if (supportedTypes[j].equalsIgnoreCase(codec))
                        return true;
                }
            }
            return false;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public Uri makeManifest(ArrayList<Quality> qualities) {
        final StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:6\n");
        sb.append("#EXT-X-INDEPENDENT-SEGMENTS\n\n");
        manifestUris = new ArrayList<>();
        boolean hasManifests = false;
        for (Quality q : qualities) {
            for (VideoUri v : q.uris) {
                mediaDataSourceFactory.putDocumentUri(v.docId, v.uri);
                mediaDataSourceFactory.putDocumentUri(v.manifestDocId, v.m3u8uri);
                if (v.m3u8uri != null) {
                    manifestUris.add(v);
                    sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append((int) Math.floor(v.bitrate * 8)).append(",RESOLUTION=").append(v.width).append("x").append(v.height);
                    sb.append("\n");
                    sb.append("mtproto:").append(v.manifestDocId).append("\n\n");
                    hasManifests = true;
                }
            }
        }
        if (!hasManifests) return null;
        final String base64 = Base64.encodeToString(sb.toString().getBytes(), Base64.NO_WRAP);
        return Uri.parse("data:application/x-mpegurl;base64," + base64);
    }

    public static class Quality {

        public boolean original;
        public int width, height;
        public final ArrayList<VideoUri> uris = new ArrayList<>();

        public Quality(VideoUri uri) {
            original = uri.original;
            width = uri.width;
            height = uri.height;
            uris.add(uri);
        }

        public static ArrayList<Quality> groupBy(ArrayList<VideoUri> uris) {
            final ArrayList<Quality> qualities = new ArrayList<>();

            for (VideoUri uri : uris) {
                if (uri.original) {
                    qualities.add(new Quality(uri));
                    continue;
                }

                Quality q = null;
                for (Quality _q : qualities) {
                    if (!_q.original && _q.width == uri.width && _q.height == uri.height) {
                        q = _q;
                        break;
                    }
                }

                if (q != null && !SharedConfig.debugVideoQualities) {
                    q.uris.add(uri);
                } else {
                    qualities.add(new Quality(uri));
                }
            }

            return qualities;
        }

        @NonNull
        @Override
        public String toString() {
            if (SharedConfig.debugVideoQualities) {
                return width + "x" + height +
                    (original ? " (" + getString(R.string.QualitySource) + ")" : "") + "\n" +
                    AndroidUtilities.formatFileSize((long) uris.get(0).bitrate).replace(" ", "") + "/s" +
                    (uris.get(0).codec != null ? ", " + uris.get(0).codec : "");
            } else {
                int p = Math.min(width, height);
                if (Math.abs(p - 1080) < 30) p = 1080;
                else if (Math.abs(p - 720) < 30) p = 720;
                else if (Math.abs(p - 360) < 30) p = 360;
                else if (Math.abs(p - 240) < 30) p = 240;
                else if (Math.abs(p - 144) < 30) p = 144;
                return p + "p" + (original ? " (" + getString(R.string.QualitySource) + ")" : "");
            }
        }

        private static final List<String> preferableCodecs_1 = Arrays.asList("h264", "avc");
        private static final ArrayList<String> preferableCodecs_2 = new ArrayList(Arrays.asList("h265", "hevc"));

        public TLRPC.Document getDownloadDocument() {
            if (uris.isEmpty()) return null;
            for (VideoUri v : uris) {
                if (v.codec != null && preferableCodecs_1.contains(v.codec)) {
                    return v.document;
                }
            }
            for (VideoUri v : uris) {
                if (v.codec != null && preferableCodecs_2.contains(v.codec)) {
                    return v.document;
                }
            }
            return uris.get(0).document;
        }
    }

    public static class VideoUri {

        public boolean original;
        public long docId;
        public Uri uri;
        public long manifestDocId;
        public Uri m3u8uri;

        public TLRPC.Document document;

        public int width, height;
        public double duration;
        public long size;
        public double bitrate;

        public String codec;
        public MediaItem mediaItem;

        public static Uri getUri(int currentAccount, TLRPC.Document document, int reference) throws UnsupportedEncodingException {
            final String params =
                "?account=" + currentAccount +
                "&id=" + document.id +
                "&hash=" + document.access_hash +
                "&dc=" + document.dc_id +
                "&size=" + document.size +
                "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") +
                "&rid=" + reference +
                "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8") +
                "&reference=" + Utilities.bytesToHex(document.file_reference != null ? document.file_reference : new byte[0]);
            return Uri.parse("tg://" + MessageObject.getFileName(document) + params);
        }

        public static VideoUri of(int currentAccount, TLRPC.Document document, TLRPC.Document manifest, int reference) throws UnsupportedEncodingException {
            final VideoUri videoUri = new VideoUri();
            TLRPC.TL_documentAttributeVideo attributeVideo = null;
            for (int i = 0; i < document.attributes.size(); ++i) {
                final TLRPC.DocumentAttribute attribute = document.attributes.get(i);
                if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    attributeVideo = (TLRPC.TL_documentAttributeVideo) attribute;
                    break;
                }
            }
            final String codec = attributeVideo == null ? null : attributeVideo.video_codec;

            videoUri.document = document;
            videoUri.docId = document.id;
            videoUri.uri = getUri(currentAccount, document, reference);
            if (manifest != null) {
                videoUri.manifestDocId = manifest.id;
                videoUri.m3u8uri = getUri(currentAccount, manifest, reference);
                File file = FileLoader.getInstance(currentAccount).getPathToAttach(manifest, null, false, true);
                if (file != null && file.exists()) {
//                    qualityUri.m3u8uri = Uri.fromFile(file);
                }
            }

            videoUri.codec = codec;
            videoUri.size = document.size;
            if (attributeVideo != null) {
                videoUri.duration = attributeVideo.duration;
                videoUri.width = attributeVideo.w;
                videoUri.height = attributeVideo.h;

                videoUri.bitrate = videoUri.size / videoUri.duration;
            }

            File file = FileLoader.getInstance(currentAccount).getPathToAttach(document, null, false, true);
            if (file != null && file.exists()) {
//                qualityUri.uri = Uri.fromFile(file);
            }

            return videoUri;
        }

        public MediaItem getMediaItem() {
            if (mediaItem == null) {
                mediaItem = new MediaItem.Builder().setUri(uri).build();
            }
            return mediaItem;
        }

    }

    public boolean isPlayerPrepared() {
        return currentPlayer != null;
    }

    public void releasePlayer(boolean async) {
        if (player != null) {
            player.release();
            player = null;
        }
        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }
        if (shouldPauseOther) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.playerDidStartPlaying);
        }
        if (castPlayer != null) {
            castPlayer.release();
            castPlayer = null;
        }
        if (castContext != null) {
            castContext.getSessionManager().removeSessionManagerListener(sessionManagerListener, Session.class);
            castContext.removeCastStateListener(castStateListener);
        }
        playerCounter--;
    }

    @Override
    public void onSeekStarted(EventTime eventTime) {
        if (delegate != null) {
            delegate.onSeekStarted(eventTime);
        }
    }

    @Override
    public void onSeekProcessed(EventTime eventTime) {
        if (delegate != null) {
            delegate.onSeekFinished(eventTime);
        }
    }

    @Override
    public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
        fallbackPosition = C.TIME_UNSET;
        fallbackDuration = C.TIME_UNSET;
        if (delegate != null) {
            delegate.onRenderedFirstFrame(eventTime);
        }
    }

    private int cstate = -1;
    private void updateCastState(int newState) {
        cstate = newState;
        if (newState != CastState.NO_DEVICES_AVAILABLE) {
            setCurrentPlayer(castPlayer);
        } else {
            setCurrentPlayer(player);
        }
        if (delegate != null) {
            delegate.onCastStateChanged(newState);
        }
    }

    public void setTextureView(TextureView texture) {
        if (textureView == texture) {
            return;
        }
        textureView = texture;
        if (player == null) {
            return;
        }
        player.setVideoTextureView(textureView);
    }

    public void setSurfaceView(SurfaceView surfaceView) {
        if (this.surfaceView == surfaceView) {
            return;
        }
        this.surfaceView = surfaceView;
        if (player == null) {
            return;
        }
        player.setVideoSurfaceView(surfaceView);
    }

    public void setSurface(Surface s) {
        if (surface == s) {
            return;
        }
        surface = s;
        if (player == null) {
            return;
        }
        player.setVideoSurface(surface);
    }

    public boolean getPlayWhenReady() {
        return player.getPlayWhenReady();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public Uri getCurrentUri() {
        return currentUri;
    }


    public void play() {
        mixedPlayWhenReady = true;
        if (mixedAudio) {
            if (!audioPlayerReady || !videoPlayerReady) {
                if (player != null) {
                    player.setPlayWhenReady(false);
                }
                if (audioPlayer != null) {
                    audioPlayer.setPlayWhenReady(false);
                }
                return;
            }
        }

        if (currentPlayer != null) {
            currentPlayer.setPlayWhenReady(true);
            if (currentPlayer == castPlayer) {
                currentPlayer.prepare();
            }
        }

        if (audioPlayer != null) {
            audioPlayer.setPlayWhenReady(true);
        }
    }

    public void pause() {
        mixedPlayWhenReady = false;
        if (currentPlayer != null) {
            currentPlayer.setPlayWhenReady(false);
        }
        if (audioPlayer != null) {
            audioPlayer.setPlayWhenReady(false);
        }

        if (audioVisualizerDelegate != null) {
            audioUpdateHandler.removeCallbacksAndMessages(null);
            audioVisualizerDelegate.onVisualizerUpdate(false, true, null);
        }
    }

    public void setPlaybackSpeed(float speed) {
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(speed, speed > 1.0f ? 0.98f : 1.0f));
        }
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        mixedPlayWhenReady = playWhenReady;
        if (playWhenReady && mixedAudio) {
            if (!audioPlayerReady || !videoPlayerReady) {
                if (player != null) {
                    player.setPlayWhenReady(false);
                }
                if (audioPlayer != null) {
                    audioPlayer.setPlayWhenReady(false);
                }
                return;
            }
        }
        autoplay = playWhenReady;
        if (player != null) {
            player.setPlayWhenReady(playWhenReady);
        }
        if (audioPlayer != null) {
            audioPlayer.setPlayWhenReady(playWhenReady);
        }
    }

    public long getDuration() {
        if (fallbackDuration != C.TIME_UNSET) {
            return fallbackDuration;
        }
        return currentPlayer != null ? currentPlayer.getDuration() : 0;
    }

    public long getCurrentPosition() {
        if (fallbackPosition != C.TIME_UNSET) {
            return fallbackPosition;
        }
        return currentPlayer != null ? currentPlayer.getCurrentPosition() : 0;
    }

    public boolean isMuted() {
        return currentPlayer != null && currentPlayer.getVolume() == 0.0f;
    }

    public void setMute(boolean value) {
        if (currentPlayer != null) {
            currentPlayer.setVolume(value ? 0.0f : 1.0f);
        }
        if (audioPlayer != null) {
            audioPlayer.setVolume(value ? 0.0f : 1.0f);
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onSurfaceSizeChanged(int width, int height) {

    }

    public void setVolume(float volume) {
        if (player != null) {
            player.setVolume(volume);
        }
        if (audioPlayer != null) {
            audioPlayer.setVolume(volume);
        }
    }

    public void seekTo(long positionMs) {
        seekTo(positionMs, false);
    }

    public void seekTo(long positionMs, boolean fast) {
        if (player != null) {
            player.setSeekParameters(fast ? SeekParameters.CLOSEST_SYNC : SeekParameters.EXACT);
            player.seekTo(positionMs);
        }
    }

    public void setDelegate(VideoPlayerDelegate videoPlayerDelegate) {
        delegate = videoPlayerDelegate;
    }

    public void setAudioVisualizerDelegate(AudioVisualizerDelegate audioVisualizerDelegate) {
        this.audioVisualizerDelegate = audioVisualizerDelegate;
    }

    public int getBufferedPercentage() {
        return isStreaming ? (player != null ? currentPlayer.getBufferedPercentage() : 0) : 100;
    }

    public long getBufferedPosition() {
        return player != null ? (isStreaming ? currentPlayer.getBufferedPosition() : currentPlayer.getDuration()) : 0;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public boolean isPlaying() {
        return mixedAudio && mixedPlayWhenReady || currentPlayer != null && currentPlayer.getPlayWhenReady();
    }

    public boolean isBuffering() {
        return player != null && lastReportedPlaybackState == ExoPlayer.STATE_BUFFERING;
    }


    private boolean handleAudioFocus = false;
    public void handleAudioFocus(boolean handleAudioFocus) {
        this.handleAudioFocus = handleAudioFocus;
        if (player != null) {
            player.setAudioAttributes(player.getAudioAttributes(), handleAudioFocus);
        }
    }

    public void setStreamType(int type) {
        if (player != null) {
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(type == AudioManager.STREAM_VOICE_CALL ? C.USAGE_VOICE_COMMUNICATION : C.USAGE_MEDIA)
                .build(), handleAudioFocus);
        }
        if (audioPlayer != null) {
            audioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(type == AudioManager.STREAM_VOICE_CALL ? C.USAGE_VOICE_COMMUNICATION : C.USAGE_MEDIA)
                .build(), true);
        }
    }

    public void setLooping(boolean looping) {
        if (this.looping != looping) {
            this.looping = looping;
            if (player != null) {
                player.setRepeatMode(looping ? ExoPlayer.REPEAT_MODE_ALL : ExoPlayer.REPEAT_MODE_OFF);
            }
        }
    }

    public boolean isLooping() {
        return looping;
    }

    private void checkPlayersReady() {
        if (audioPlayerReady && videoPlayerReady && mixedPlayWhenReady) {
            play();
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        maybeReportPlayerState();
        if (playWhenReady && playbackState == Player.STATE_READY && !isMuted() && shouldPauseOther) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.playerDidStartPlaying, this);
        }
        if (!videoPlayerReady && playbackState == Player.STATE_READY) {
            videoPlayerReady = true;
            checkPlayersReady();
        }
        if (playbackState != Player.STATE_READY) {
            audioUpdateHandler.removeCallbacksAndMessages(null);
            if (audioVisualizerDelegate != null) {
                audioVisualizerDelegate.onVisualizerUpdate(false, true, null);
            }
        }
    }

    public boolean isCasting() {
        return currentPlayer == castPlayer;
    }

    private void setCurrentPlayer(Player newPlayer) {
        if (currentPlayer == newPlayer || newPlayer == null) {
            return;
        }

        // Store state from old player
        long position = currentPlayer != null ? currentPlayer.getCurrentPosition() : 0;
        boolean playWhenReady = currentPlayer != null && currentPlayer.getPlayWhenReady();

        // Create MediaItem once for both cases
        MediaItem.Builder builder = new MediaItem.Builder();
        String uri = "";
        String title = "";
        if (currentStreamIsHls) {
            int idx = getCurrentQualityIndex();
            Uri g = videoQualities.get(idx).uris.get(0).uri;
            uri = getDirectUrl(g);
            title = "hls";
        }
        else if (currentUri != null) {
            uri = getLocalUrlForVideo(currentUri);
            title = currentUri.getLastPathSegment();
        }
//        uri = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_1MB.mp4";
        MediaItem mediaItem = builder.setUri(uri)
            .setMimeType("video/mp4")
            .setMediaId(uri)
            .setMediaMetadata(new com.google.android.exoplayer2.MediaMetadata.Builder()
                    .setTitle(title)
                    .build())
            .build();

        // Stop current playback
        if (currentPlayer != null) {
            currentPlayer.stop();
            castPlayer.setMediaItem(mediaItem);
        }

        // Create JSON custom data to map back to MediaItem
        JSONObject customData = new JSONObject();
        try {
            customData.put("uri", uri);
            customData.put("mimeType", "video/mp4");
            // Add any other necessary MediaItem properties
            customData.put("mediaItem", true);  // Flag to identify our media items
        } catch (JSONException e) {
            FileLog.e("Error creating custom data", e);
        }

        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        movieMetadata.putString(MediaMetadata.KEY_TITLE, "Video");

        MediaInfo mediaInfo = new MediaInfo.Builder(uri)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("video/mp4")
                .setMetadata(movieMetadata)
                .setCustomData(customData)
                .build();

        pendingLoadRequest = new MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(playWhenReady)
                .setCurrentTime(position / 1000L)
                .build();

        if (newPlayer == castPlayer) {
            MediaLoadRequestData requestData = new MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(playWhenReady)
                    .setCurrentTime(position / 1000L)  // Convert to seconds
                    .build();

            // Load into cast player
                tryLoadMedia();
//            castPlayer.setMediaItem(mediaItem);
//            castPlayer.prepare();
//            castPlayer.seekTo(position);
//            castPlayer.setPlayWhenReady(playWhenReady);
        } else if (newPlayer == player) {
            MediaSource mediaSource = mediaSourceFromUri(currentUri, videoType);
            player.setMediaSource(mediaSource, true);
            player.prepare();
            player.seekTo(position);
            player.setPlayWhenReady(playWhenReady);
        }
        currentPlayer = player;
    }

    private void tryLoadMedia() {
        CastSession session = castContext.getSessionManager().getCurrentCastSession();
        if (session == null || !session.isConnected()) {
            waitingToPlay = true;
            return;
        }

        if (castPlayer != null) {
            waitingToPlay = false;

            RemoteMediaClient remoteMediaClient = session.getRemoteMediaClient();
            if (remoteMediaClient != null) {
                castPlayer.setRemoteMediaClient(remoteMediaClient);

                remoteMediaClient.load(pendingLoadRequest)
                        .addStatusListener(status -> {
                           if(!status.isSuccess()) {
                               new Handler(Looper.getMainLooper()).postDelayed(loadingRequestRunnable, 1000);
                           } else {
                               pendingLoadRequest = null;
                           }
                        });
            } else {
                // RemoteMediaClient not ready yet, wait for session callback
                waitingToPlay = true;
            }
        }
    }

    private String getLocalUrlForVideo(Uri uri) {
        try {
            String host = PhotoViewer.getInstance().deviceIp;
            if (uri == null || uri.getScheme() == null || host == null) {
                return null;
            }

            // Start local server if not already running
//            SimpleWebServer.init(parentActivity, true);

            // Get source file path
            String sourcePath = uri.getPath();
            if (sourcePath == null) {
                return null;
            }
            File sourceFile = new File(sourcePath);
            String fileName = sourceFile.getName();

            // Get or create video file using MediaStore
            ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Telegram");

            Uri targetUri = null;
            // First try to find existing file
            String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " +
                    MediaStore.MediaColumns.DISPLAY_NAME + "=?";
            String[] selectionArgs = new String[]{"Movies/Telegram", fileName};
            try (Cursor cursor = resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    null,
                    selection,
                    selectionArgs,
                    null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID);
                    if (idColumn >= 0) {
                        long id = cursor.getLong(idColumn);
                        targetUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    }
                }
            }

            // If file doesn't exist, create it
            if (targetUri == null) {
                targetUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            }

            if (targetUri == null) {
                return null;
            }

            // Copy the file
            try (InputStream is = new FileInputStream(sourceFile);
                 OutputStream os = resolver.openOutputStream(targetUri, "wt")) {
                if (os == null) {
                    return null;
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }

            // Get the actual file path
            try (Cursor cursor = resolver.query(targetUri, new String[]{MediaStore.MediaColumns.DATA}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA));
                    if (filePath != null) {
                        return "http://" + host + ":8080/Movies/Telegram/" + Uri.encode(fileName);
                    }
                }
            }

            return null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    @Override
    public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, @Player.DiscontinuityReason int reason) {
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            repeatCount++;
        }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        AndroidUtilities.runOnUIThread(() -> {
            Throwable cause = error.getCause();
            if (textureView != null && (!triedReinit && cause instanceof MediaCodecRenderer.DecoderInitializationException || cause instanceof SurfaceNotValidException)) {
                triedReinit = true;
                if (player != null) {
                    ViewGroup parent = (ViewGroup) textureView.getParent();
                    if (parent != null) {
                        int i = parent.indexOfChild(textureView);
                        parent.removeView(textureView);
                        parent.addView(textureView, i);
                    }
                    if (workerQueue != null) {
                        workerQueue.postRunnable(() -> {
                            if (player != null) {
                                player.clearVideoTextureView(textureView);
                                player.setVideoTextureView(textureView);
                                if (videoQualities != null) {
                                    preparePlayer(videoQualities, videoQualityToSelect);
                                } else if (loopingMediaSource) {
                                    preparePlayerLoop(videoUri, videoType, audioUri, audioType);
                                } else {
                                    preparePlayer(videoUri, videoType);
                                }
                                play();
                            }
                        });
                    } else {
                        player.clearVideoTextureView(textureView);
                        player.setVideoTextureView(textureView);
                        if (videoQualities != null) {
                            preparePlayer(videoQualities, videoQualityToSelect);
                        } else if (loopingMediaSource) {
                            preparePlayerLoop(videoUri, videoType, audioUri, audioType);
                        } else {
                            preparePlayer(videoUri, videoType);
                        }
                        play();
                    }
                }
            } else {
                delegate.onError(this, error);
            }
        });
    }

    public VideoSize getVideoSize() {
        return player != null ? player.getVideoSize() : null;
    }

    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
        delegate.onVideoSizeChanged(videoSize.width, videoSize.height, videoSize.unappliedRotationDegrees, videoSize.pixelWidthHeightRatio);
        Player.Listener.super.onVideoSizeChanged(videoSize);
    }

    @Override
    public void onRenderedFirstFrame() {
        delegate.onRenderedFirstFrame();
    }

    @Override
    public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
        return delegate.onSurfaceDestroyed(surfaceTexture);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        delegate.onSurfaceTextureUpdated(surfaceTexture);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    private void maybeReportPlayerState() {
        if (player == null) {
            return;
        }
        boolean playWhenReady = player.getPlayWhenReady();
        int playbackState = player.getPlaybackState();
        if (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState) {
            delegate.onStateChanged(playWhenReady, playbackState);
            lastReportedPlayWhenReady = playWhenReady;
            lastReportedPlaybackState = playbackState;
        }
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    private class AudioVisualizerRenderersFactory extends DefaultRenderersFactory {

        public AudioVisualizerRenderersFactory(Context context) {
            super(context);
        }

        @Nullable
        @Override
        protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams, boolean enableOffload) {
            return new DefaultAudioSink.Builder()
                    .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(new AudioProcessor[] {new TeeAudioProcessor(new VisualizerBufferSink())})
                    .setOffloadMode(
                            enableOffload
                                    ? DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
                                    : DefaultAudioSink.OFFLOAD_MODE_DISABLED)
                    .build();
        }
    }

    private class VisualizerBufferSink implements TeeAudioProcessor.AudioBufferSink {

        private final int BUFFER_SIZE = 1024;
        private final int MAX_BUFFER_SIZE = BUFFER_SIZE * 8;
        FourierTransform.FFT fft = new FourierTransform.FFT(BUFFER_SIZE, 48000);
        float[] real = new float[BUFFER_SIZE];
        ByteBuffer byteBuffer;
        int position = 0;

        public VisualizerBufferSink() {
            byteBuffer = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
            byteBuffer.position(0);
        }

        @Override
        public void flush(int sampleRateHz, int channelCount, int encoding) {
            
        }


        long lastUpdateTime;

        @Override
        public void handleBuffer(ByteBuffer buffer) {
            if (audioVisualizerDelegate == null) {
                return;
            }
            if (buffer == AudioProcessor.EMPTY_BUFFER || !mixedPlayWhenReady) {
                audioUpdateHandler.postDelayed(() -> {
                    audioUpdateHandler.removeCallbacksAndMessages(null);
                    audioVisualizerDelegate.onVisualizerUpdate(false, true, null);
                }, 80);
                return;
            }

            if (!audioVisualizerDelegate.needUpdate()) {
                return;
            }

            int len = buffer.limit();
            if (len > MAX_BUFFER_SIZE) {
                audioUpdateHandler.removeCallbacksAndMessages(null);
                audioVisualizerDelegate.onVisualizerUpdate(false, true, null);
                return;
//                len = MAX_BUFFER_SIZE;
//                byte[] bytes = new byte[BUFFER_SIZE];
//                buffer.get(bytes);
//                byteBuffer.put(bytes, 0, BUFFER_SIZE);
            } else {
                byteBuffer.put(buffer);
            }
            position += len;

            if (position >= BUFFER_SIZE) {
                len = BUFFER_SIZE;
                byteBuffer.position(0);
                for (int i = 0; i < len; i++) {
                    real[i] = (byteBuffer.getShort()) / 32768.0F;
                }
                byteBuffer.rewind();
                position = 0;

                fft.forward(real);
                float sum = 0;
                for (int i = 0; i < len; i++) {
                    float r = fft.getSpectrumReal()[i];
                    float img = fft.getSpectrumImaginary()[i];
                    float peak = (float) Math.sqrt(r * r + img * img) / 30f;
                    if (peak > 1f) {
                        peak = 1f;
                    } else if (peak < 0) {
                        peak = 0;
                    }
                    sum += peak * peak;
                }
                float amplitude = (float) (Math.sqrt(sum / len));

                float[] partsAmplitude = new float[7];
                partsAmplitude[6] = amplitude;
                if (amplitude < 0.4f) {
                    for (int k = 0; k < 7; k++) {
                        partsAmplitude[k] = 0;
                    }
                } else {
                    int part = len / 6;

                    for (int k = 0; k < 6; k++) {
                        int start = part * k;
                        float r = fft.getSpectrumReal()[start];
                        float img = fft.getSpectrumImaginary()[start];
                        partsAmplitude[k] = (float) (Math.sqrt(r * r + img * img) / 30f);

                        if (partsAmplitude[k] > 1f) {
                            partsAmplitude[k] = 1f;
                        } else if (partsAmplitude[k] < 0) {
                            partsAmplitude[k] = 0;
                        }
                    }
                }

                int updateInterval = 64;

                if (System.currentTimeMillis() - lastUpdateTime < updateInterval) {
                    return;
                }
                lastUpdateTime = System.currentTimeMillis();

                audioUpdateHandler.postDelayed(() -> audioVisualizerDelegate.onVisualizerUpdate(true, true, partsAmplitude), 130);
            }
        }
    }

    public boolean isHDR() {
        if (player == null) {
            return false;
        }
        try {
            Format format = player.getVideoFormat();
            if (format == null || format.colorInfo == null) {
                return false;
            }
            return (
                format.colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
                format.colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
            );
        } catch (Exception ignore) {}
        return false;
    }


    public StoryEntry.HDRInfo getHDRStaticInfo(StoryEntry.HDRInfo hdrInfo) {
        if (hdrInfo == null) {
            hdrInfo = new StoryEntry.HDRInfo();
        }
        try {
            MediaFormat mediaFormat = ((MediaCodecRenderer) player.getRenderer(0)).codecOutputMediaFormat;
            ByteBuffer byteBuffer = mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            if (byteBuffer.get() == 0) {
                hdrInfo.maxlum = byteBuffer.getShort(17);
                hdrInfo.minlum = byteBuffer.getShort(19) * 0.0001f;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                    hdrInfo.colorTransfer = mediaFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                }
                if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                    hdrInfo.colorStandard = mediaFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD);
                }
                if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
                    hdrInfo.colorRange = mediaFormat.getInteger(MediaFormat.KEY_COLOR_RANGE);
                }
            }
        } catch (Exception ignore) {
            hdrInfo.maxlum = hdrInfo.minlum = 0;
        }
        return hdrInfo;
    }

    public void setWorkerQueue(DispatchQueue dispatchQueue) {
        workerQueue = dispatchQueue;
        player.setWorkerQueue(dispatchQueue);
    }

    public void setIsStory() {
        isStory = true;
    }
}

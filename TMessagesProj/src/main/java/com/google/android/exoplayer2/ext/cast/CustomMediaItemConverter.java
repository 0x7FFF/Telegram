package com.google.android.exoplayer2.ext.cast;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.Log;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Default {@link MediaItemConverter} implementation.
 */

public class CustomMediaItemConverter extends DefaultMediaItemConverter {
    @Override
    public MediaItem toMediaItem(MediaQueueItem mediaQueueItem) {
        MediaInfo mediaInfo = mediaQueueItem.getMedia();
        if (mediaInfo == null) {
            throw new IllegalArgumentException("MediaInfo is null");
        }

        JSONObject customData = mediaInfo.getCustomData();
        if (customData == null || !customData.optBoolean(KEY_MEDIA_ITEM, false)) {
            // Fallback for unknown media
            return new MediaItem.Builder()
                    .setUri(mediaInfo.getContentId())
                    .setMimeType(mediaInfo.getContentType())
                    .setMediaMetadata(
                            new com.google.android.exoplayer2.MediaMetadata.Builder()
                                    .setTitle(mediaInfo.getMetadata().getString(MediaMetadata.KEY_TITLE))
                                    .build()
                    )
                    .build();
        }

        // Convert back to MediaItem using custom data
        return new MediaItem.Builder()
                .setUri(customData.optString("uri"))
                .setMimeType(customData.optString("mimeType", "video/mp4"))
                .setMediaMetadata(
                        new com.google.android.exoplayer2.MediaMetadata.Builder()
                                .setTitle(mediaInfo.getMetadata().getString(MediaMetadata.KEY_TITLE))
                                .build()
                )
                .build();
    }

    @Override
    public MediaQueueItem toMediaQueueItem(MediaItem mediaItem) {
        // Create custom data
        JSONObject customData = new JSONObject();
        try {
            customData.put("uri", mediaItem.localConfiguration.uri.toString());
            customData.put("mimeType", mediaItem.localConfiguration.mimeType);
            customData.put(KEY_MEDIA_ITEM, true);
        } catch (JSONException e) {
            Log.e("TAG", "Error creating custom data", e);
        }

        // Create media metadata
        MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        if (mediaItem.mediaMetadata.title != null) {
            metadata.putString(MediaMetadata.KEY_TITLE, mediaItem.mediaMetadata.title.toString());
        }

        // Create media info
        MediaInfo mediaInfo = new MediaInfo.Builder(mediaItem.localConfiguration.uri.toString())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mediaItem.localConfiguration.mimeType)
                .setMetadata(metadata)
                .setCustomData(customData)
                .build();

        return new MediaQueueItem.Builder(mediaInfo).build();
    }
}

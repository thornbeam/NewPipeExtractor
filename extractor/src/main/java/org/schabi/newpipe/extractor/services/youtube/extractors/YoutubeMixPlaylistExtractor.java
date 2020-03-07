package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeParsingHelper.getJsonResponse;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import java.io.IOException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

/**
 * A YoutubePlaylistExtractor for a mix (auto-generated playlist). It handles urls in the format of
 * "youtube.com/watch?v=videoId&list=playlistId"
 */
public class YoutubeMixPlaylistExtractor extends PlaylistExtractor {

    private final static String CONTENTS = "contents";
    private final static String RESPONSE = "response";
    private final static String PLAYLIST = "playlist";
    private final static String TWO_COLUMN_WATCH_NEXT_RESULTS = "twoColumnWatchNextResults";
    private final static String PLAYLIST_PANEL_VIDEO_RENDERER = "playlistPanelVideoRenderer";

    private JsonObject playlistData;

    public YoutubeMixPlaylistExtractor(StreamingService service, ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader)
        throws IOException, ExtractionException {
        final String url = getUrl() + "&pbj=1";
        final JsonArray ajaxJson = getJsonResponse(url, getExtractorLocalization());
        JsonObject initialData = ajaxJson.getObject(3).getObject(RESPONSE);
        try {
            playlistData = initialData.getObject(CONTENTS).getObject(TWO_COLUMNS_WATCH_NEXT_RESULTS)
                .getObject(PLAYLIST).getObject(PLAYLIST);
        } catch (NullPointerException e) {
            throw new ExtractionException(e);
        }

    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        try {
            final String name = playlistData.getString("title");
            if (name != null) {
                return name;
            } else {
                return "";
            }
        } catch (Exception e) {
            throw new ParsingException("Could not get playlist name", e);
        }
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        try {
            final String playlistId = playlistData.getString("playlistId");
            final String videoId;
            if (playlistId.startsWith("RDMM")) {
                videoId = playlistId.substring(4);
            } else {
                videoId = playlistId.substring(2);
            }
            if (videoId.isEmpty()) {
                throw new ParsingException("");
            }
            return getThumbnailUrlFromId(videoId);
        } catch (Exception e) {
            throw new ParsingException("Could not get playlist thumbnail", e);
        }
    }

    @Override
    public String getBannerUrl() {
        return "";
    }

    @Override
    public String getUploaderUrl() {
        //Youtube mix are auto-generated
        return "";
    }

    @Override
    public String getUploaderName() {
        //Youtube mix are auto-generated
        return "";
    }

    @Override
    public String getUploaderAvatarUrl() {
        //Youtube mix are auto-generated
        return "";
    }

    @Override
    public long getStreamCount() {
        // Auto-generated playlist always start with 25 videos and are endless
        return 25;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws ExtractionException {
        StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        collectStreamsFrom(collector, playlistData.getArray(CONTENTS));
        return new InfoItemsPage<>(collector, getNextPageUrl());
    }

    @Override
    public String getNextPageUrl() throws ExtractionException {
        final JsonObject lastStream = ((JsonObject) playlistData.getArray(CONTENTS)
            .get(playlistData.getArray(CONTENTS).size() - 1));
        if (lastStream == null || lastStream.getObject(PLAYLIST_PANEL_VIDEO_RENDERER) == null) {
            throw new ExtractionException("Could not extract next page url");
        }
        return "https://youtube.com" + lastStream.getObject(PLAYLIST_PANEL_VIDEO_RENDERER)
            .getObject("navigationEndpoint").getObject("commandMetadata")
            .getObject("webCommandMetadata").getString("url") + "&pbj=1";
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final String pageUrl)
        throws ExtractionException, IOException {
        if (pageUrl == null || pageUrl.isEmpty()) {
            throw new ExtractionException(
                new IllegalArgumentException("Page url is empty or null"));
        }

        StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final JsonArray ajaxJson = getJsonResponse(pageUrl, getExtractorLocalization());
        playlistData =
            ajaxJson.getObject(3).getObject(RESPONSE).getObject(CONTENTS)
                .getObject(TWO_COLUMNS_WATCH_NEXT_RESULTS).getObject(PLAYLIST)
                .getObject(PLAYLIST);
        final JsonArray streams = playlistData.getArray(CONTENTS);
        //Because continuation requests are created with the last video of previous request as start
        streams.remove(0);
        collectStreamsFrom(collector, streams);
        return new InfoItemsPage<>(collector, getNextPageUrl());
    }

    private void collectStreamsFrom(
        @Nonnull StreamInfoItemsCollector collector,
        @Nullable JsonArray streams) {
        collector.reset();

        if (streams == null) {
            return;
        }

        final TimeAgoParser timeAgoParser = getTimeAgoParser();

        for (Object stream : streams) {
            if (stream instanceof JsonObject) {
                JsonObject streamInfo = ((JsonObject) stream)
                    .getObject(PLAYLIST_PANEL_VIDEO_RENDERER);
                if (streamInfo != null) {
                    collector.commit(new YoutubeStreamInfoItemExtractor(streamInfo, timeAgoParser));
                }
            }
        }
    }

    private String getThumbnailUrlFromId(String videoId) {
        return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
    }
}

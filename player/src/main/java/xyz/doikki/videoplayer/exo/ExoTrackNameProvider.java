package xyz.doikki.videoplayer.exo;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.text.TextUtils;


import androidx.annotation.Nullable;

import com.github.tvbox.osc.player.R;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;


import java.util.Locale;

public class ExoTrackNameProvider {

    private final Resources resources;

    /**
     * @param resources Resources from which to obtain strings.
     */
    public ExoTrackNameProvider(Resources resources) {
        this.resources = Assertions.checkNotNull(resources);
    }

    public String getTrackName(Format format) {
        String trackName;
        int trackType = inferPrimaryTrackType(format);
        if (trackType == C.TRACK_TYPE_VIDEO) {
            trackName =
                    joinWithSeparator(
                            buildRoleString(format), buildResolutionString(format), buildBitrateString(format));
        } else if (trackType == C.TRACK_TYPE_AUDIO) {
            trackName =
                    joinWithSeparator(
                            buildLanguageOrLabelString(format),
                            buildAudioChannelString(format),
                            buildBitrateString(format));
        } else {
            trackName = buildLanguageOrLabelString(format);
        }
        return trackName.length() == 0 ? resources.getString(R.string.exo_track_unknown) : trackName;
    }

    private String buildResolutionString(Format format) {
        int width = format.width;
        int height = format.height;
        return width == Format.NO_VALUE || height == Format.NO_VALUE
                ? ""
                : resources.getString(R.string.exo_track_resolution, width, height);
    }

    private String buildBitrateString(Format format) {
        int bitrate = format.bitrate;
        return bitrate == Format.NO_VALUE
                ? ""
                : resources.getString(R.string.exo_track_bitrate, bitrate / 1000000f);
    }

    private String buildAudioChannelString(Format format) {
        int channelCount = format.channelCount;
        if (channelCount == Format.NO_VALUE || channelCount < 1) {
            return "";
        }
        switch (channelCount) {
            case 1:
                return resources.getString(R.string.exo_track_mono);
            case 2:
                return resources.getString(R.string.exo_track_stereo);
            case 6:
            case 7:
                return resources.getString(R.string.exo_track_surround_5_point_1);
            case 8:
                return resources.getString(R.string.exo_track_surround_7_point_1);
            default:
                return resources.getString(R.string.exo_track_surround);
        }
    }

    private String buildLanguageOrLabelString(Format format) {
        String languageAndRole =
                joinWithSeparator(buildLanguageString(format), buildRoleString(format));
        return TextUtils.isEmpty(languageAndRole) ? buildLabelString(format) : languageAndRole;
    }

    private String buildLabelString(Format format) {
        return TextUtils.isEmpty(format.label) ? "" : format.label;
    }

    private String buildLanguageString(Format format) {
        @Nullable String language = format.language;
        if (TextUtils.isEmpty(language) || C.LANGUAGE_UNDETERMINED.equals(language)) {
            return "";
        }
        Locale languageLocale =
                Util.SDK_INT >= 21 ? Locale.forLanguageTag(language) : new Locale(language);
        Locale displayLocale =
                Util.SDK_INT >= 24 ? Locale.getDefault(Locale.Category.DISPLAY) : Locale.getDefault();
        String languageName = languageLocale.getDisplayName(displayLocale);
        if (TextUtils.isEmpty(languageName)) {
            return "";
        }
        try {
            // Capitalize the first letter. See: https://github.com/google/ExoPlayer/issues/9452.
            int firstCodePointLength = languageName.offsetByCodePoints(0, 1);
            return languageName.substring(0, firstCodePointLength).toUpperCase(displayLocale)
                    + languageName.substring(firstCodePointLength);
        } catch (IndexOutOfBoundsException e) {
            // Should never happen, but return the unmodified language name if it does.
            return languageName;
        }
    }

    private String buildRoleString(Format format) {
        String roles = "";
        if ((format.roleFlags & C.ROLE_FLAG_ALTERNATE) != 0) {
            roles = resources.getString(R.string.exo_track_role_alternate);
        }
        if ((format.roleFlags & C.ROLE_FLAG_SUPPLEMENTARY) != 0) {
            roles = joinWithSeparator(roles, resources.getString(R.string.exo_track_role_supplementary));
        }
        if ((format.roleFlags & C.ROLE_FLAG_COMMENTARY) != 0) {
            roles = joinWithSeparator(roles, resources.getString(R.string.exo_track_role_commentary));
        }
        if ((format.roleFlags & (C.ROLE_FLAG_CAPTION | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND)) != 0) {
            roles =
                    joinWithSeparator(roles, resources.getString(R.string.exo_track_role_closed_captions));
        }
        return roles;
    }

    private String joinWithSeparator(String... items) {
        String itemList = "";
        for (String item : items) {
            if (item.length() > 0) {
                if (TextUtils.isEmpty(itemList)) {
                    itemList = item;
                } else {
                    itemList = resources.getString(R.string.exo_item_list, itemList, item);
                }
            }
        }
        return itemList;
    }

    private static int inferPrimaryTrackType(Format format) {
        int trackType = MimeTypes.getTrackType(format.sampleMimeType);
        if (trackType != C.TRACK_TYPE_UNKNOWN) {
            return trackType;
        }
        if (MimeTypes.getVideoMediaMimeType(format.codecs) != null) {
            return C.TRACK_TYPE_VIDEO;
        }
        if (MimeTypes.getAudioMediaMimeType(format.codecs) != null) {
            return C.TRACK_TYPE_AUDIO;
        }
        if (format.width != Format.NO_VALUE || format.height != Format.NO_VALUE) {
            return C.TRACK_TYPE_VIDEO;
        }
        if (format.channelCount != Format.NO_VALUE || format.sampleRate != Format.NO_VALUE) {
            return C.TRACK_TYPE_AUDIO;
        }
        return C.TRACK_TYPE_UNKNOWN;
    }
}

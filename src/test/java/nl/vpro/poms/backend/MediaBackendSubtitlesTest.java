package nl.vpro.poms.backend;

import java.time.Duration;
import java.util.Locale;

import org.junit.*;
import org.junit.runners.MethodSorters;

import nl.vpro.domain.subtitles.Cue;
import nl.vpro.domain.subtitles.StandaloneCue;
import nl.vpro.domain.subtitles.Subtitles;
import nl.vpro.domain.subtitles.SubtitlesType;
import nl.vpro.poms.AbstractApiMediaBackendTest;
import nl.vpro.poms.DoAfterException;
import nl.vpro.util.CountedIterator;

import static nl.vpro.poms.Utils.waitUntilNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeNoException;

/**
 * @author Michiel Meeuwissen
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MediaBackendSubtitlesTest extends AbstractApiMediaBackendTest {

    private static final String MID = "WO_VPRO_025057";
    private static final Duration ACCEPTABLE_DURATION = Duration.ofMinutes(3);

    @Rule
    public DoAfterException doAfterException = new DoAfterException((t) -> {
        if (! (t instanceof AssumptionViolatedException)) {
            MediaBackendSubtitlesTest.exception = t;
        }
    });

    private static Throwable exception = null;

    @Before
    public void setup() {
        assumeNoException(exception);
    }

    @Test
    public void test01addSubtitles() {
        //assumeThat(backendVersionNumber, greaterThanOrEqualTo(5.1f));


        Subtitles subtitles = Subtitles.webvtt(MID, Duration.ZERO, Locale.CHINESE,
            "WEBVTT\n" +
                "\n" +
                "1\n" +
                "00:00:02.200 --> 00:00:04.150\n" +
                "" + title + "\n" +
                "\n" +
                "2\n" +
                "00:00:04.200 --> 00:00:08.060\n" +
                "*'k Heb een paar puntjes die ik met je wil bespreken\n" +
                "\n" +
                "3\n" +
                "00:00:08.110 --> 00:00:11.060\n" +
                "*Dat wil ik doen in jouw mobiele bakkerij\n" +
                "\n" +
                ""
        );
        backend.setSubtitles(subtitles);
    }

    @Test
    public void checkArrived() throws Exception {
        if (exception == null) {
            Cue cue = waitUntilNotNull(ACCEPTABLE_DURATION, () -> {
                CountedIterator<StandaloneCue> update = backend.getBackendRestService().getSubtitles(MID, Locale.CHINESE, SubtitlesType.TRANSLATION, true);
                StandaloneCue found = update.next();
                if (found.getContent().equals(title)) {
                    return found;
                }
                return null;

            });

            assertThat(cue.getContent()).isEqualTo(title);
        }
    }
}
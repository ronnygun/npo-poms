package nl.vpro.poms.backend;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

import javax.ws.rs.core.Response;
import javax.xml.bind.JAXB;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;

import nl.vpro.api.client.media.ResponseError;
import nl.vpro.domain.media.*;
import nl.vpro.domain.media.update.*;
import nl.vpro.domain.media.update.collections.XmlCollection;
import nl.vpro.poms.AbstractApiMediaBackendTest;
import nl.vpro.testutils.Utils;
import nl.vpro.util.Version;

import static nl.vpro.testutils.Utils.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;


/*
 * 2018-08-17:
 * 5.9-SNAPSHOT @ dev : allemaal ok
 */
/***
 * @author Michiel Meeuwissen
 */
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@Log4j2
class MediaBackendTest extends AbstractApiMediaBackendTest {

    private static final Duration ACCEPTABLE_DURATION = Duration.ofMinutes(3);

    @BeforeEach
    public void setup() {
        log.info("Mailing errors to {}", backend.getErrors());
        assumeThat(backend.getErrors()).isNotEmpty();

    }


    private static String newMid;

    @Test
    public void test01CreateObjectWithMembers() {
        ProgramUpdate clip = ProgramUpdate.create(
            MediaTestDataBuilder.clip()
                .ageRating(AgeRating.ALL)
                .title(title)
                .broadcasters("VPRO")
                .languages("ZH")
                .predictions(Prediction.builder().platform(Platform.INTERNETVOD).encryption(Encryption.NONE).plannedAvailability(true).build())
                .constrainedNew()
                .build()
        );
        clip.setVersion(Version.of(5, 9));

        JAXB.marshal(clip, System.out);
        newMid = backend.set(clip);
        assertThat(newMid).isNotEmpty();

        log.info("Created {}", newMid);

        ProgramUpdate member = ProgramUpdate
            .create(
                MediaTestDataBuilder.clip()
                    .title(title + "_members")
                    .broadcasters("VPRO")
                    .constrainedNew()
                    .build());

        // TODO: this will happen via queue in ImportRoute
        String memberMid = backend.set(member);
        log.info("Created {} too", memberMid);


        waitUntil(Duration.ofMinutes(5),
            "Waiting until " + memberMid + " see also MSE-3836",
            () -> backend.get(memberMid) != null);

        // This won't so it may be executed earlier and hence fail (MSE-3836)
        backend.createMember(newMid, memberMid, 1);

    }

    @Test
    public void test02CheckArrived() {
        assumeThat(newMid).isNotNull();

        ProgramUpdate u = waitUntil(
            ACCEPTABLE_DURATION,
            newMid + " exists ",
            () -> backend.get(newMid),
            Objects::nonNull);
        //assertThat(u.getSegments()).hasSize(1);
        assertThat(u.getLanguages()).containsExactly(new Locale("ZH"));
        assertThat(u.getPredictions()).hasSize(1);
        assertThat(u.getPredictions().first().getPlatform()).isEqualTo(Platform.INTERNETVOD);

        MediaUpdateList<MemberUpdate> memberUpdates = waitUntil(ACCEPTABLE_DURATION,
            newMid + " exists and has one member",
            () -> backend.getGroupMembers(newMid),
            (groupMembers) -> groupMembers.size() == 1
        );
        assertThat(memberUpdates).hasSize(1);

    }

   /* @Test
    public void test03UpdateClip() {
        ProgramUpdate clip = ProgramUpdate.create(
            MediaTestDataBuilder.clip()
                .title(title)
                .crids("crid://backendtests/clip/" + NOW)
                .broadcasters("VPRO")
                .constrainedNew()
                .segments(MediaTestDataBuilder
                    .segment()
                    .title("segment of " + title)
                    .broadcasters("VPRO")
                    .constrainedNew()
                    .build()
                )
        );
        String foundMid = backend.set(clip);


    }
*/


    /**
     * It should simple provisionlly accept.
     */
    @Test
    public void test03CreateObjectWithoutBroadcaster() {
        backend.setValidateInput(false);
        ProgramUpdate clip = ProgramUpdate.create(
            MediaBuilder.clip()
                .ageRating(AgeRating.ALL)
                .mainTitle(title)
                .languages("ZH")
                .build()
        );
        clip.setVersion(Version.of(5, 5));
        try {
            String mid = backend.set(clip);
            log.info("Found mid {}", mid);
            //fail("Should give error on creating object without any broadcasters. But created  " + mid);
        } catch (ResponseError re) {
            log.info("Response: {}", re.getMessage(), re);
            assertThat(re.getStatus()).isEqualTo(401);
        }

    }

    private static final String CRID = "crid://test.poms/1";

    @Test
    public void test04DeleteForCridIfExists() {
        log.info("{}", backend.deleteIfExists(CRID));
        Optional<ProgramUpdate> pu = waitUntil(
            ACCEPTABLE_DURATION,
            CRID + " does not exists (or is deleted)",
            () -> backend.optional(CRID),
            o -> !o.isPresent() || o.get().isDeleted());
        pu.ifPresent(
            programUpdate -> log.info("Found {}", programUpdate)
        );


    }

    private static String midWithCrid;
    private static String againMidWithCrid;
    private static String againMidWithStolenCrid;


    @Test
    public void test05CreateObjectWithCrids() {
        backend.setLookupCrids(false);
        ProgramUpdate clip = ProgramUpdate.create(
            MediaBuilder.clip()
                .ageRating(AgeRating.ALL)
                .broadcasters("VPRO")
                .mainTitle(title)
                .crids("crid://test.poms/1")
                .build()
        );
        midWithCrid = backend.set(clip);
        log.info("Found mid {}", midWithCrid);
        ProgramUpdate created = waitUntil(ACCEPTABLE_DURATION,
            midWithCrid + " exists",
            () -> backend.get(midWithCrid),
            Objects::nonNull);
        assertThat(created.getCrids()).contains(CRID);
    }

    @Test
    public void test06CreateObjectWithCrids() {
        backend.setLookupCrids(false);
        ProgramUpdate clip = ProgramUpdate.create(
            MediaBuilder.clip()
                .ageRating(AgeRating.ALL)
                .broadcasters("VPRO")
                .mainTitle(title)
                .crids("crid://test.poms/1")
                .build()
        );
        againMidWithCrid = backend.set(clip);
        log.info("Found another mid {}. This clip may not actually appear!", againMidWithCrid);
    }

    @Test
    public void test07CreateObjectWithStolenCrids() {
        backend.setLookupCrids(false);
        backend.setStealCrids(AssemblageConfig.Steal.YES);
        ProgramUpdate clip = ProgramUpdate.create(
            MediaBuilder.clip()
                .ageRating(AgeRating.ALL)
                .broadcasters("VPRO")
                .mainTitle(title)
                .ageRatingAllIfUnset()

                .crids("crid://test.poms/1")
                .build()
        );
        againMidWithStolenCrid = backend.set(clip);
        log.info("Found another mid {}", againMidWithStolenCrid);
        waitUntil(ACCEPTABLE_DURATION,
            CRID + " exists ",
            () -> backend.get(againMidWithStolenCrid),
            (Predicate<MediaUpdate<?>>) u -> u != null && u.getCrids().contains(CRID));
    }


    @Test
    public void test08checkObjectsWithCrids() {
        assumeThat(midWithCrid).isNotNull();
        assumeThat(againMidWithCrid).isNotNull();
        assumeThat(againMidWithStolenCrid).isNotNull();
        waitUntil(ACCEPTABLE_DURATION,
            CRID + " exists ",
            () -> backend.get(midWithCrid),
            (Predicate<MediaUpdate<?>>) u -> u != null && !u.getCrids().contains(CRID));

        Assertions.assertThat((Object) backend.get(againMidWithCrid)).isNull();
    }

    @Test
    @Disabled
    public void tryToPinDownDamnServerErrorsOnDev() throws InterruptedException {
        for (int i = 0 ; i < 100; i++) {
            ProgramUpdate update = backend_authority.get(MID);
            assertThat(update).isNotNull();
            log.info("{}: {}", i, update);
            Thread.sleep(1000);
        }
    }
    @Test
    @Tag("prediction")
    public void test10setPrediction() throws IOException {
        try (Response response =
                 backend.getBackendRestService().setPrediction(
                     null,
                     MID,
                     Platform.INTERNETVOD,
                     null,
                     null,
                     PredictionUpdate.builder()
                         .encryption(Encryption.NONE)
                         .publishStart(NOW.toInstant())
                         .build())
        ) {

            log.info("{}", response.getEntity());
        }
    }

    @Test
    @Tag("prediction")
    public void test11checkPrediction() {
        waitUntil(ACCEPTABLE_DURATION,
            () -> {
                try {
                    return backend.getBackendRestService().getPredictions(null, MID, null);
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                    return null;
                }
            },
            Utils.Check.<XmlCollection<PredictionUpdate>>builder()
                .description("prediction has publishStart " + NOW)
                .predicate((l) ->
                        l.stream()
                            .map(e -> e.getPlatform() == Platform.INTERNETVOD && e.getPublishStart().equals(NOW.toInstant()))
                            .findFirst().isPresent())
                .build(),
            Utils.Check.<XmlCollection<PredictionUpdate>>builder()
                .description("prediction has encryption NONE")
                .predicate((l) ->
                    l.stream()
                        .map(e -> e.getPlatform() == Platform.INTERNETVOD && Objects.equals(e.getEncryption(), Encryption.NONE))
                        .findFirst().isPresent())
                .build()
        );
    }



     @Test
    @Tag("prediction")
    public void test12setPredictions() throws IOException {
        try (Response response =
                 backend.getBackendRestService().setPredictions(
                     null,
                     MID,
                     null,
                     null,
                     new XmlCollection<>(
                         PredictionUpdate.builder()
                             .encryption(Encryption.DRM)
                             .platform(Platform.INTERNETVOD)
                             .publishStart(NOW.toInstant().minus(Duration.ofMinutes(5)))
                             .build()))
        ) {

            log.info("{}", response.getEntity());
        }
    }

    @Test
    @Tag("prediction")
    public void test13checkPredictions() {
        waitUntil(ACCEPTABLE_DURATION,
            () -> backend.getFull(MID),
            Utils.Check.<MediaObject>builder()
                .description("prediction has publishStart " + NOW)
                .predicate((m) -> m.findOrCreatePrediction(Platform.INTERNETVOD).getPublishStartInstant().equals(NOW.toInstant().minus(Duration.ofMinutes(5))))
                .build(),
            Utils.Check.<MediaObject>builder()
                .description("prediction is with DRM")
                .predicate((m) -> Objects.equals(m.findOrCreatePrediction(Platform.INTERNETVOD).getEncryption(), Encryption.DRM))
                .build()
        );
    }
}

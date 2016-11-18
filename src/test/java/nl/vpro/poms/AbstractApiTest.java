package nl.vpro.poms;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;

import javax.ws.rs.core.MediaType;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import nl.vpro.api.client.resteasy.NpoApiClients;
import nl.vpro.api.client.utils.NpoApiMediaUtil;
import nl.vpro.rs.media.MediaRestClient;

/**
 * @author Michiel Meeuwissen
 * @since 1.0
 */
@Slf4j
public abstract class AbstractApiTest {


    @Rule
    public AllowUnavailable unavailable = new AllowUnavailable();

    private static final String TITLE = Instant.now().toString();
    @Rule
    public TestName name = new TestName();
    protected String title;


    @Before
    public void setupTitle() {
        title = TITLE + " " + name.getMethodName();
    }

    protected static final Duration ACCEPTABLE_DURATION_FRONTEND = Duration.ofMinutes(10);
    protected static final NpoApiClients clients =
        NpoApiClients.configured(Config.env(), Config.getProperties(Config.Prefix.npoapi))
            .mediaType(MediaType.APPLICATION_XML_TYPE)
            .trustAll(true)
            .build();
    protected static final MediaRestClient backend =
        MediaRestClient.configured(Config.env(), Config.getProperties(Config.Prefix.backendapi))
            .trustAll(true)
            .build();
    protected static final NpoApiMediaUtil mediaUtil = new NpoApiMediaUtil(clients);

    static {
        log.info("Using {}, {}", clients, backend);
    }


}

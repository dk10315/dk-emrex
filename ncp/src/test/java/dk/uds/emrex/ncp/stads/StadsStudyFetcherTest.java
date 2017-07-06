package dk.uds.emrex.ncp.stads;

import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import javax.xml.bind.Marshaller;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

import https.github_com.emrex_eu.elmo_schemas.tree.v1.Elmo;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by sj on 30-08-16.
 */
@Slf4j
public class StadsStudyFetcherTest {

	private static String STADS_URL = "https://ref.stads.dk:443/ws_STDBEMRX/services/GetStudentsResult/version_1_0?wsdl";
	private static String[] SSN_ARRAY = { "091280-1128", "091280-1136" };
	
	private StadsStudyFetcher studyFetcher;

	@Before
	public void setUp() throws Exception {
		Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setMarshallerProperties(properties);
		marshaller.setContextPath("dk.uds.emrex.stads.wsdl");

		this.studyFetcher = new StadsStudyFetcher();

		this.studyFetcher.setMarshaller(marshaller);
		this.studyFetcher.setUnmarshaller(marshaller);
	}

	@After
	public void tearDown() throws Exception {
		this.studyFetcher = null;
	}

	@Test
	public void testFetchStudies() throws Exception {
		try {
			for (final String ssn : SSN_ARRAY) {
				final Iterator<String> urlIterator = Collections.singleton(STADS_URL).iterator();
				final Optional<Elmo> elmo = this.studyFetcher.fetchElmo(urlIterator, ssn);
				if (!elmo.isPresent()) {
					fail("ELMO response was empty");
				}
			}
		} catch (Exception ex) {
			log.warn("Error in test", ex);
			throw ex;
		}
	}

	@Test
	public void testFetchElmo() {

	}
}
package dk.uds.emrex.ncp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

import junit.framework.TestCase;

/**
 * Created by marko.hollanti on 04/09/15.
 */
public class DkNcpApplicationTest extends TestCase {

	private static final String ELMO_XML_KAISA = "src/test/resources/kaisa.xml";

	public static String getElmo() throws Exception {
		return new String(Files.readAllBytes(Paths.get(new File(ELMO_XML_KAISA).getAbsolutePath())));
	}
	
	@Test
    public void testGetElmo() throws Exception {

        final String elmoXml = getElmo();
        assertNotNull(elmoXml);
        assertTrue(elmoXml.startsWith("<elmo xmlns=\"https://github.com/emrex-eu/elmo-schemas/tree/v1\">"));
    }
}
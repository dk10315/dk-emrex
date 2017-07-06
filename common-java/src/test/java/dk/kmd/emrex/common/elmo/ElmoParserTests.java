package dk.kmd.emrex.common.elmo;

import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import dk.kmd.emrex.common.util.TestUtil;
import junit.framework.TestCase;


/**
 * Created by jpentika on 19/10/15.
 */
public class ElmoParserTests extends TestCase {

    //private String testXML = "elmo-1.0-example.xml";
    private String testXML = "swedish_1_elmo1.0.xml";
    @Test
    public void testRemoveCourses() throws Exception {
        String elmo = TestUtil.getFileContent(testXML);
        ElmoParser parser = ElmoParser.elmoParser(elmo);
        String[] courses = {"0","1","2"}; 
        String readyElmo = parser.asXml(courses);
        checkEmptyHasPartNodes(readyElmo);
    }

    @Test
    public void testCoursesCount() throws Exception {
        String elmo = TestUtil.getFileContent(testXML);
        ElmoParser parser =  ElmoParser.elmoParser(elmo);
        ElmoParser parser2 =  ElmoParser.elmoParser(elmo);
//        assertEquals(17, parser.getCoursesCount());
        assertEquals(parser2.getCoursesCount(), parser.getCoursesCount());
    }


    @Test
    public void testGetHostInstitution() throws Exception {
        String elmo = TestUtil.getFileContent(testXML);
        ElmoParser parser = ElmoParser.elmoParser(elmo);
        String host = parser.getHostInstitution();
        assertEquals("umu.se", host);
    }
        @Test
    public void testGetHostCountry() throws Exception {
        String elmo = TestUtil.getFileContent(testXML);
        ElmoParser parser = ElmoParser.elmoParser(elmo);
        String host = parser.getHostCountry();
        assertEquals("SE", host);
    }

    @Test
    public void testCountECTS() throws Exception {
        runETCSTest(testXML, 360);
       // runETCSTest("Example-elmo-Norway.xml", 512); // some crazy learner here
    }

    private void runETCSTest(String elmoName, int value) throws Exception {
        String elmo = TestUtil.getFileContent(elmoName);
        ElmoParser parser = ElmoParser.elmoParser(elmo);
        int count = parser.getETCSCount();
        assertEquals(value, count);
        ElmoParser parser2 = ElmoParser.elmoParser(elmo);
        int count2 = parser2.getETCSCount();
        assertEquals(count2, count);
    }

    @Test
    public void testAddAndReadPDF() throws Exception {
        String elmo = TestUtil.getFileContent(testXML);
        File pdfFile = TestUtil.getFile("elmo-finland.pdf");
        byte[] pdf = IOUtils.toByteArray(new FileInputStream(pdfFile));
        ElmoParser parser = ElmoParser.elmoParser(elmo);
        parser.addPDFAttachment(pdf);
        byte[] readPdf = parser.getAttachedPDF();
        assertArrayEquals(pdf, readPdf);
    }

    @Test
    public void testAddPDFTwice() throws Exception
    {
        String elmo = TestUtil.getFileContent(testXML);
        File pdfFile = TestUtil.getFile("elmo-finland.pdf");
        byte[] pdf = IOUtils.toByteArray(new FileInputStream(pdfFile));
        ElmoParser parser = ElmoParser.elmoParser(elmo);
        parser.addPDFAttachment(pdf);
        parser.addPDFAttachment(pdf);
        // next line throws an exception if there are several pdfs
        byte[] readPdf = parser.getAttachedPDF();

        assertArrayEquals(pdf, readPdf);
    }

    private void checkEmptyHasPartNodes(String readyElmo) throws ParserConfigurationException, SAXException, IOException {
        Document document = getDocument(readyElmo);
        NodeList hasParts = document.getElementsByTagName("hasPart");
        for (int i = 0; i < hasParts.getLength(); i++) {
            Element hasPart = (Element) hasParts.item(i);
            String textContent = hasPart.getTextContent();
            String withoutWhiteSpaces = textContent.trim();
            assertEquals(false, withoutWhiteSpaces.isEmpty());
            assertEquals(true, hasParts.item(i).hasChildNodes());
        }
    }

    private Document getDocument(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        //Get the DOM Builder
        DocumentBuilder builder;

        builder = factory.newDocumentBuilder();
        StringReader sr = new StringReader(xml);
        InputSource s = new InputSource(sr);

        return builder.parse(s);
    }




}







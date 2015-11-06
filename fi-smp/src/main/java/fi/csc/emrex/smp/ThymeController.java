/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fi.csc.emrex.smp;

import fi.csc.emrex.common.elmo.ElmoParser;
import fi.csc.emrex.common.model.Person;
import fi.csc.emrex.common.util.ShibbolethHeaderHandler;
import fi.csc.emrex.smp.model.VerificationReply;
import fi.csc.emrex.smp.model.VerifiedReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author salum
 */
@Controller
@Slf4j
public class ThymeController {

    @Value("${smp.verification.threshold}")
    private double verificationThreshold;

    @Autowired
    private HttpServletRequest context;

    @Autowired
    private SignatureVerifier signatureVerifier;

    @Value("${emreg.url}")
    private String emregUrl;

    @Value("${smp.return.url}")
    private String returnUrl;

    @Value("${smp.university.base.directory}")
    private String universityBaseDirectory;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String smp(HttpServletRequest request, Model model) throws Exception {
        return smpsmp(request, model);
    }

    @RequestMapping(value = "/smp/", method = RequestMethod.GET)
    public String smpsmp(HttpServletRequest request, Model model) throws Exception {
        String firstName = request.getHeader("shib-givenName");
        model.addAttribute("name", firstName);
        context.getSession().setAttribute("sessionStartTime", LocalDateTime.now());
        return "smp";
    }

    @RequestMapping(value = "/abort", method = RequestMethod.GET)
    public String abort(Model model) throws Exception {
        model.addAttribute("url", getLinkToPolishQuestionare());
        return "onReturnAbort";
    }

    @RequestMapping(value = "/smp/abort", method = RequestMethod.GET)
    public String smpAbort(Model model) throws Exception {
        return abort(model);
    }

    @RequestMapping(value = "/smp/onReturn", method = RequestMethod.POST)
    public String smponReturnelmo(@ModelAttribute ElmoData request, Model model, @CookieValue(value = "elmoSessionId") String sessionIdCookie, @CookieValue(value = "chosenNCP") String chosenNCP, HttpServletRequest httpRequest) throws Exception {
        return this.onReturnelmo(request, model, sessionIdCookie, chosenNCP, httpRequest);
    }

    @RequestMapping(value = "/onReturn", method = RequestMethod.POST)
    public String onReturnelmo(@ModelAttribute ElmoData request, Model model, @CookieValue(value = "elmoSessionId") String sessionIdCookie, @CookieValue(value = "chosenNCP") String chosenNCP, HttpServletRequest httpRequest) throws Exception {
        String sessionId = request.getSessionId();
        context.getSession().setAttribute("sessionID", sessionId); // for making the url
        String elmo = request.getElmo();

        if (elmo == null) {
            return "onReturnAbort";
        }

        Person person = (Person) context.getSession().getAttribute("shibPerson");

        if (person == null) {
            ShibbolethHeaderHandler headerHandler = new ShibbolethHeaderHandler(httpRequest);
            log.info(headerHandler.stringifyHeader());
            person = headerHandler.generatePerson();
            context.getSession().setAttribute("shibPerson", person);
        }

        final byte[] bytes = DatatypeConverter.parseBase64Binary(elmo);
        final String decodedXml = GzipUtil.gzipDecompress(bytes);

        // TODO charset problems UTF-8 vs UTF-16
        final boolean verifySignatureResult = signatureVerifier.verifySignatureWithDecodedData(getCertificate(), decodedXml, StandardCharsets.UTF_8);
        log.info("Verify signature result: {}", verifySignatureResult);

        log.info("providedSessionId: " + sessionId);

        String ncpPubKey = chosenNCP;

        try {
            FiSmpApplication.verifySessionId(sessionId, sessionIdCookie);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            model.addAttribute("error", "<p>Session verification failed</p>");
            return "error";
        }
        try {
            if (!FiSmpApplication.verifyElmoSignature(decodedXml, ncpPubKey)) {
                model.addAttribute("error", "<p>NCP signature check failed</p>");
                return "error";
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            model.addAttribute("error", "<p>NCP verification failed</p>");
            return "error";
        }
        log.info("Returned elmo XML " + decodedXml);
        context.getSession().setAttribute("elmoxmlstring", decodedXml);
        ElmoParser parser = new ElmoParser(decodedXml);
        byte[] pdf = parser.getAttachedPDF();
        context.getSession().setAttribute("pdf", pdf);

        model.addAttribute("elmoXml", decodedXml);

        Document document;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        //Get the DOM Builder
        DocumentBuilder builder;
        if (person != null) {
            List<VerifiedReport> results = new ArrayList<>();
            try {

                builder = factory.newDocumentBuilder();
                StringReader sr = new StringReader(decodedXml);
                final InputSource inputSource = new InputSource();
                inputSource.setEncoding(StandardCharsets.UTF_8.name());
                inputSource.setCharacterStream(sr);

                //Load and Parse the XML document
                //document contains the complete XML as a Tree.
                document = builder.parse(inputSource);
                NodeList reports = document.getElementsByTagName("report");
                for (int i = 0; i < reports.getLength(); i++) {
                    VerifiedReport vr = new VerifiedReport();
                    Element report = (Element) reports.item(i);
                    vr.setReport(nodeToString(report));
                    Person elmoPerson = getUserFromElmoReport(report);

                    if (elmoPerson != null) {
                        VerificationReply verification = VerificationReply.verify(person, elmoPerson, verificationThreshold);
                        log.info("Verification messages: " + verification.getMessages());
                        log.info("VerScore: " + verification.getScore());

                        vr.setVerification(verification);

                    } else {
                        vr.addMessage("Elmo learner missing");
                        //todo fix this
                    }
                    results.add(vr);
                }
                context.getSession().setAttribute("reports", results);
                model.addAttribute("reports", results);

            } catch (ParserConfigurationException | IOException | SAXException ex) {
                log.error("Error in report verification", ex);
                Logger.getLogger(ThymeController.class.getName()).log(Level.SEVERE, null, ex);
                model.addAttribute("error", ex.getMessage());
                return "error";
            }
        } else {

            model.addAttribute("error", "<p>HAKA login missing</p>");
            return "error";
        }
        return "review";
    }

    // FIXME serti jostain muualta
    private String getCertificate() {
        return "-----BEGIN CERTIFICATE-----\n"
                + "MIIB+TCCAWICCQDiZILVgSkjojANBgkqhkiG9w0BAQUFADBBMQswCQYDVQQGEwJG\n"
                + "STERMA8GA1UECAwISGVsc2lua2kxETAPBgNVBAcMCEhlbHNpbmtpMQwwCgYDVQQK\n"
                + "DANDU0MwHhcNMTUwMjA1MTEwNTI5WhcNMTgwNTIwMTEwNTI5WjBBMQswCQYDVQQG\n"
                + "EwJGSTERMA8GA1UECAwISGVsc2lua2kxETAPBgNVBAcMCEhlbHNpbmtpMQwwCgYD\n"
                + "VQQKDANDU0MwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMyVVTyGT1Cp8z1f\n"
                + "jYEO93HEtIpFKnb/tvPb6Ee5b8m8lnuv6YWsF8DBWPVfsOq0KCWD8zE1yD+w+xxM\n"
                + "mp6+zATp089PUrEUYawG/tGu9OG+EX+nhOAj0SBvGHEkXh6lGJgeGxbdFVwZePAN\n"
                + "135ra5L3gYcwYBVOuEyYFZJp7diHAgMBAAEwDQYJKoZIhvcNAQEFBQADgYEAP2E9\n"
                + "YD7djCum5UYn1Od9Z1w55j+SuKRWMnTR3yzy1PXJjb2dGqNcV9tEhdbqWbwTnNfl\n"
                + "6sidCnd1U0p4XdLjg28me8ZmfftH+QU4LkwSFSyF4ajoTFC3QHD0xTtGpQIT/rAD\n"
                + "x/59fhfX5icydMzzNulwXJWImtXq2/AX43/yR+M=\n"
                + "-----END CERTIFICATE-----";
    }

    private Person getUserFromElmoReport(Element report) {

        Element learner = getOneNode(report, "learner");
        if (learner != null) {
            System.out.println("learner found");
            Person elmoPerson = new Person();
            elmoPerson.setFirstName(getOneNode(learner, "givenNames").getTextContent());
            elmoPerson.setLastName(getOneNode(learner, "familyName").getTextContent());
            Element bday = getOneNode(learner, "bday");
            if (bday != null) {
                elmoPerson.setBirthDate(bday.getTextContent(), bday.getAttribute("dtf"));
            }

            return elmoPerson;

        } else {
            System.out.println("no learner found");
            return null;
        }

    }

    private Element getOneNode(Element node, String name) {
        NodeList list = node.getElementsByTagName(name);
        if (list.getLength() == 1) {
            System.out.println("found " + name);
            return (Element) list.item(0);
        } else {
            System.out.println("no " + name + "found");
            return null;
        }
    }


    private String nodeToString(Node node) {
        StringWriter sw = new StringWriter();
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            t.transform(new DOMSource(node), new StreamResult(sw));
        } catch (TransformerException te) {
            System.out.println("nodeToString Transformer Exception");
        }
        return sw.toString();
    }


    private String getLinkToPolishQuestionare() throws Exception{
        Person shibPerson = (Person)context.getSession().getAttribute("shibPerson");
        String decodedXml = (String) context.getSession().getAttribute("elmoxmlstring");
        LocalDateTime startTime = (LocalDateTime)context.getSession().getAttribute("sessionStartTime");
        ElmoParser parser = new ElmoParser(decodedXml);

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern ("yyyy-MM-dd");

        Duration duration = Duration.between(LocalDateTime.now(), startTime);

        String link = "https://ankieter.mimuw.edu.pl/surveys/79/?session_id=" + context.getSession().getAttribute("sessionId");
        link += "&home_institution=" + shibPerson.getHomeOrganization();
        link += "&home_country=" + "fi";
        link += "&host_institution=" + parser.getHostInstitution();
        link += "&host_country=" + "X"; //not found in elmo
        link += "&date_of_import=" + LocalDateTime.now().format(dateFormatter);
        link += "&time_spent=" + Double.toString(duration.getSeconds());
        link += "&grades_imported=" + "X";
        link += "&ects_imported=" + Integer.toString(parser.getETCSCount());
        link += "&grades_imported_percent=" + "X";
        link += "&ects_imported_percent=" + "X";

        return link;
    }


}

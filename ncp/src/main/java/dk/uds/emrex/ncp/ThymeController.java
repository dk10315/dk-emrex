/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.uds.emrex.ncp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import dk.kmd.emrex.common.PdfGen;
import dk.kmd.emrex.common.PersonalLogger;
import dk.kmd.emrex.common.StatisticalLogger;
import dk.kmd.emrex.common.elmo.ElmoParser;
import dk.kmd.emrex.common.util.Security;
import dk.kmd.emrex.common.util.ShibbolethHeaderHandler;
import dk.uds.emrex.ncp.saml2.WayfUser;
import https.github_com.emrex_eu.elmo_schemas.tree.v1.Elmo;
import lombok.extern.slf4j.Slf4j;

/**
 * @author salum
 */
@EnableAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration.class
})
@Controller
@Slf4j
public class ThymeController {

    @Autowired
    private HttpServletRequest context;

    @Autowired
    private StudyFetcher studyFetcher;

    @Autowired
    private DataSign dataSign;

    @Value("${ncp.environment}")
    private String env;

    // function for local testing
    @RequestMapping(value = "/ncp/review", method = RequestMethod.GET)
    public String ncpReview(@RequestParam(value = "courses", required = false) String[] courses,
            Model model) throws Exception {
    	log.info("(ncp/review");
        return this.review(courses, model);
    }

    @RequestMapping(value = "/review", method = RequestMethod.GET)
    public String review(@RequestParam(value = "courses", required = false) String[] courses,
            Model model) throws Exception {
    	log.info("/review");
        final WayfUser wayfUser = this.getCurrentUser();

        model.addAttribute("sessionId", context.getSession().getAttribute("sessionId"));
        model.addAttribute("returnUrl", context.getSession().getAttribute("returnUrl"));

        ElmoParser parser;
        try {
            final Optional<Elmo> elmo = this.studyFetcher.fetchElmo(wayfUser.getOrganizationId(), wayfUser.getCpr());
            parser = ElmoParser.elmoParser(elmo.get());
        } catch (IOException e) {
            log.error("Failed to fetch study data.", e);
            throw e;
        }

        //ElmoParser parser = (ElmoParser) context.getSession().getAttribute("elmo");

        String xmlString = getElmoXml(courses, parser);

        PdfGen pdfGenerator = new PdfGen();

        byte[] pdf = pdfGenerator.generatePdf(xmlString);

        parser.addPDFAttachment(pdf);

        xmlString = getElmoXml(courses, parser);

        ElmoParser finalParser = ElmoParser.elmoParser(xmlString);

        //String source = "NCP";
        //String statisticalLogLine = generateStatisticalLogLine(finalParser, source);
        //StatisticalLogger.log(statisticalLogLine);

        xmlString = dataSign.sign(xmlString.trim(), StandardCharsets.UTF_8);
        //if (courses != null) {
            model.addAttribute("returnCode", context.getSession().getAttribute("returnCode"));
            model.addAttribute("elmo", xmlString);
        //} else {
        //    model.addAttribute("returnCode", "NCP_NO_RESULTS");
        //    model.addAttribute("elmo", null);
        //}
        model.addAttribute("buttonText", "Confirm selected results");

        model.addAttribute("buttonClass", "btn btn-success");

        String logLine = getLogLineStr(parser, finalParser, true);
        StatisticalLogger.log(logLine);
        
        return "review";

    }

    private String getElmoXml(@RequestParam(value = "courses", required = false) String[] courses, ElmoParser parser) throws ParserConfigurationException {
        String xmlString = courses==null ? parser.asXml(new String[] {}) : parser.asXml(courses);
        return xmlString;
    }

    @RequestMapping(value = "/ncp/abort", method = RequestMethod.GET)
    public String smpabort(Model model) {
    	log.info("/ncp/abort");
        return abort(model);
    }

    @RequestMapping(value = "/abort", method = RequestMethod.GET)
    public String abort(Model model) {
    	log.info("/abort");
        // same submit button with ame url and color is used, but without Elmo
        model.addAttribute("sessionId", context.getSession().getAttribute("sessionId"));
        model.addAttribute("returnUrl", context.getSession().getAttribute("returnUrl"));
        model.addAttribute("buttonText", "Cancel");
        model.addAttribute("returnCode", "NCP_CANCEL");
        model.addAttribute("buttonClass", "btn btn-link");
        return "review";
    }

    @RequestMapping(value = "/")
    public String ncp1(@ModelAttribute CustomRequest customRequest, HttpServletRequest request, Model model) {
    	log.info("/");
        return this.greeting(customRequest, request, model);
    }

    @RequestMapping(value = "/ncp/")
    public String greeting(@ModelAttribute CustomRequest customRequest, HttpServletRequest request, Model model) {
    	log.info("/ncp");
        final WayfUser wayfUser = this.getCurrentUser();

        log.info("/ncp/");
        if (customRequest != null) {
            try {
                if (customRequest.getSessionId() != null) {
                    if (customRequest.getSessionId().equals(Security.stripXSS(customRequest.getSessionId()))) {
                        context.getSession().setAttribute("sessionId", customRequest.getSessionId());
                    } else {
                        throw new Exception("Suspected XSS-injection");
                    }
                }
                if (customRequest.getReturnUrl() != null) {
                    String returnUrl = customRequest.getReturnUrl();
                    log.info("unprocessed returnURL: " + returnUrl);
                    String temp = Security.stripXSS(returnUrl);
                    log.info("processed returnURL: " + temp);
                    if (!returnUrl.equals(temp)) {
                        throw new Exception("Suspected XSS-injection");
                    }
                    if (!returnUrl.startsWith("https")) {
                        log.debug("env: "+env);
                        if (!"dev".equals(env)) {
                            throw new Exception("Only HTTPS allowed");
                        }
                    }
                    context.getSession().setAttribute("returnUrl", returnUrl);

                }
            } catch (Exception e) {
                model.addAttribute("error", e.getMessage());
                return "error";
            }
            log.info("Return URL: {}", context.getSession().getAttribute("returnUrl"));
            log.info("Session ID: {}", context.getSession().getAttribute("sessionId"));

            try {
                if (context.getSession().getAttribute("elmo") == null) {
                    String elmoXML;
                    ShibbolethHeaderHandler headerHandler = new ShibbolethHeaderHandler(request);
                    log.debug(headerHandler.stringifyHeader());
                    String OID = wayfUser.getOrganizationId();
//                    String OID = headerHandler.getOID();
                    String personalId = wayfUser.getCpr();
//                    String personalId = headerHandler.getPersonalID();

//                    if (OID == null && personalId == null) {
//                        if ("dev".equals(env)) {
//                            elmoXML = studyFetcher.fetchStudies("17488477125", personalId);
//                        } else {
//                            elmoXML = "";
//                        }
//                    } else {
                    		Optional<Elmo> elmo = studyFetcher.fetchElmo(OID, personalId);
                    		if (elmo.isPresent()) {
                          elmoXML = new ElmoParser(elmo.get()).asXml();
                    		} else {
                    			elmoXML = null;
                    		}
//                    }
                    log.debug(elmoXML);
                    ElmoParser parser = null;
                    if (elmoXML == null) {
                        log.debug("elmoXML null");
                        context.getSession().setAttribute("returnCode", "NCP_NO_RESULTS");

                    } else {
                        context.getSession().setAttribute("returnCode", "NCP_OK");
                        parser = new ElmoParser(elmoXML);
                        context.getSession().setAttribute("elmo", parser);

                    }
                    String logLine = getLogLineStr(parser, null, false);
                    //String personalLogLine = generatePersonalLogLine(customRequest, headerHandler, parser, wayfUser);

                    //String statisticalLogLine = generateStatisticalLogLine(parser, "NCP");
                    StatisticalLogger.log(logLine);
                    PersonalLogger.log(logLine);
                }
                return "norex";

            } catch (Exception e) {
                log.error("Elmo was null and fetching elmo failed somehow.", e);
                model.addAttribute("error", "Fetching study data failed");
                return "error";
            }

        }
        return "norex";
    }

    /**
     * @deprecated Use getLogLineStr(...)
     */
    private String generatePersonalLogLine(@ModelAttribute CustomRequest customRequest, ShibbolethHeaderHandler headerHandler, ElmoParser parser, WayfUser wayfUser) {
        String personalLogLine = "NCP\t" + customRequest.getSessionId();
        personalLogLine += "\t" + customRequest.getReturnUrl();
        personalLogLine += "\t" + wayfUser.getGivenName() + " " + wayfUser.getSurName();
        if (parser == null) {
            personalLogLine += "\t" + "not-available";
        } else {
            personalLogLine += "\t" + parser.getHostInstitutionForLoging();
        }
        return personalLogLine;
    }

    /**
     * @deprecated Use getLogLineStr(...)
     */
    private String generateStatisticalLogLine(ElmoParser parser, String source) throws Exception {
        String statisticalLogLine = source + "\t" + context.getSession().getAttribute("sessionId");
        statisticalLogLine += "\t" + context.getSession().getAttribute("returnUrl");
        if (parser != null) {
            statisticalLogLine += "\t" + parser.getCoursesCount();
            statisticalLogLine += "\t" + parser.getETCSCount();
            statisticalLogLine += "\t" + parser.getHostInstitutionForLoging();
        } else {
            statisticalLogLine += "\t0\t0\tfi"; // zero courses, zero ects, from finland
        }
        return statisticalLogLine;
    }

    private WayfUser getCurrentUser() {
        return (WayfUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    
	/**
	 * Make log entry like descriped in
	 * https://confluence.csc.fi/display/EMREX/Implementation+details:+NCP
	 * 
	 * @since EMREX-17
	 * @author z6cxh
	 */
	private String getLogLineStr(ElmoParser parser, ElmoParser finalParser, boolean success) throws Exception {
		final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

		HttpSession session = context.getSession();
		String sessionId = session.getId();
		long sessionTime = session.getCreationTime();
		long ncpTime = session.getLastAccessedTime() - sessionTime;
		Object returnUrl = session.getAttribute("returnUrl");
		String countryCode = "DK";
		Object heiId = this.getCurrentUser().getOrganizationId();
		int coursesImported = parser != null ? parser.getCoursesCount() : 0;
		float creditsImported = parser != null ? parser.getETCSCountAsFloat() : 0.0f;
		int coursesExported = finalParser != null ? finalParser.getCoursesCount() : 0;
		float creditsExporte = finalParser != null ? finalParser.getETCSCountAsFloat() : 0.0f;

		Date sessionDate = new Date(sessionTime);
		return String.format("%s\t%s\t%.1f\t%s\t%s\t%s\t%s\t%d\t%.1f\t%d\t%.1f ", sessionId, sdf.format(sessionDate),
				(ncpTime / 60000.0), returnUrl, countryCode, (success ? "yes" : "no"), heiId, coursesImported,
				creditsImported, coursesExported, creditsExporte);
	}
	
}

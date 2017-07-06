package dk.uds.emrex.ncp;

import dk.uds.emrex.ncp.saml2.WayfUser;
import dk.kmd.emrex.common.elmo.ElmoParser;
import https.github_com.emrex_eu.elmo_schemas.tree.v1.Elmo;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
public class JsonController {

    final static Logger log = LoggerFactory.getLogger(JsonController.class);

    @Autowired
    private HttpServletRequest context;

    @Autowired
    private StudyFetcher studyFetcher;

    @RequestMapping(value = "/elmo", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> fetchElmoXml(HttpServletRequest request) throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("returnUrl", context.getSession().getAttribute("returnUrl"));
        model.put("sessionId", context.getSession().getAttribute("sessionId"));

        final WayfUser user = getCurrentUser();
        Optional<Elmo> elmo = studyFetcher.fetchElmo(user.getOrganizationId(), user.getCpr());
        final ElmoParser parser = elmo.isPresent() ? ElmoParser.elmoParser(elmo.get()) : null;

        if (parser != null) {
        	String elmoXml = parser.asXml();
          model.put("elmoXml", elmoXml);
        } else {
          model.put("elmoXml", "");
        }
        return model;
    }

    @RequestMapping(value = "/ncp/api/fullelmo", method = RequestMethod.GET)
    @ResponseBody
    public String npcGetFullElmoJSON() throws Exception {
        return this.getFullElmoJSON();
    }

    @RequestMapping(value = "/api/fullelmo", method = RequestMethod.GET)
    @ResponseBody
    public String getFullElmoJSON() throws IOException {
        log.info("getting FullELmo");

        try {
            final WayfUser user = getCurrentUser();
            Optional<Elmo> elmo = studyFetcher.fetchElmo(user.getOrganizationId(), user.getCpr());
            final ElmoParser parser = elmo.isPresent() ? ElmoParser.elmoParser(elmo.get()) : null;

            if (parser != null) {
              String jsonString = parser.asJson();
              log.debug(jsonString);
              return jsonString;
            } else {
            	return "";
            }
        } catch (Exception e) {
            log.error("Error getting FullELmo", e);
            StackTraceElement elements[] = e.getStackTrace();
            Map<String, Object> error = new HashMap<String, Object>();
            Map<String, Object> log = new HashMap<String, Object>();
            error.put("message", e.getMessage());
            for (int i = 0, n = elements.length; i < n; i++) {
                log.put(elements[i].getFileName() + " " + elements[i].getLineNumber(),
                        elements[i].getMethodName());
            }
            error.put("stack", log);
            return new JSONObject(error).toString();
        }
    }

    @RequestMapping(value = "/ncp/api/elmo", method = RequestMethod.GET)
    @ResponseBody
    public String npcGetElmoJSON(@RequestParam(value = "courses", required = false) String[] courses) throws Exception {
        return this.getElmoJSON(courses);
    }

    @RequestMapping(value = "/api/elmo", method = RequestMethod.GET)
    @ResponseBody
    public String getElmoJSON(
            @RequestParam(value = "courses", required = false) String[] courses) throws Exception {
        	log.info("Courses: [" + (courses==null? "null" : Arrays.stream(courses).reduce((a,b)-> a+", "+b).orElse(""))+']');
        try {
            final WayfUser user = getCurrentUser();
            Optional<Elmo> elmo = studyFetcher.fetchElmo(user.getOrganizationId(), user.getCpr());
            final ElmoParser parser = ElmoParser.elmoParser(elmo.get());

            String jsonString = courses==null? parser.asJson(new String[]{}) : parser.asJson(courses);
            log.debug(courses==null?"Courses count: null":("Courses count: "+ courses.length));
            log.debug(jsonString);
            return jsonString;
        } catch (Exception e) {
            log.error("Failed to get Elmo JSON", e);
            StackTraceElement elements[] = e.getStackTrace();
            Map<String, Object> error = new HashMap<String, Object>();
            Map<String, Object> log = new HashMap<String, Object>();
            error.put("message", e.getMessage());
            for (int i = 0, n = elements.length; i < n; i++) {
                log.put(elements[i].getFileName() + " " + elements[i].getLineNumber(),
                        elements[i].getMethodName());
            }
            error.put("stack", log);
            return new JSONObject(error).toString();
        }
    }

    private WayfUser getCurrentUser() {
        return (WayfUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

}

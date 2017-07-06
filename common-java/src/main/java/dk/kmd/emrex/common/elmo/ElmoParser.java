package dk.kmd.emrex.common.elmo;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ooxi.jdatauri.DataUri;
import com.sun.xml.internal.bind.marshaller.NamespacePrefixMapper;

import https.github_com.emrex_eu.elmo_schemas.tree.v1.Attachment;
import https.github_com.emrex_eu.elmo_schemas.tree.v1.Elmo;
import https.github_com.emrex_eu.elmo_schemas.tree.v1.Elmo.Report;
import https.github_com.emrex_eu.elmo_schemas.tree.v1.Elmo.Report.Issuer.Identifier;
import https.github_com.emrex_eu.elmo_schemas.tree.v1.LearningOpportunitySpecification;
import https.github_com.emrex_eu.elmo_schemas.tree.v1.LearningOpportunitySpecification.Specifies;
import https.github_com.emrex_eu.elmo_schemas.tree.v1.LearningOpportunitySpecification.Specifies.LearningOpportunityInstance.Credit;
import https.github_com.emrex_eu.elmo_schemas.tree.v1.TokenWithOptionalLang;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Slf4j
public class ElmoParser {

	private final Elmo elmo;

	
	public static ElmoParser elmoParser(@NonNull Elmo elmo) {
		return new ElmoParser(elmo);
	}
	
	public static ElmoParser elmoParser(@NonNull String elmoString) {
		return new ElmoParser(elmoString);
	}

	public ElmoParser(@NonNull String elmoString) {
		this.elmo = asElmo(elmoString).orElse(null);
	}
	
	public ElmoParser(@NonNull Elmo elmo) {
		this.elmo = elmo;
	}

	public byte[] getAttachedPDF() throws Exception {
		List<Attachment> attachments = this.elmo.getAttachment();
		for (Attachment attachment : attachments) {
			if (attachment.getType().equals("EMREX transcript")) {
				Object content = attachment.getContent();
				DataUri parse = DataUri.parse((String) content, Charset.forName("UTF-8"));
				if ("application/pdf".equals(parse.getMime())) {
					return parse.getData();
				}
			}
		}
		throw new Exception("No attached PDFs in Elmo");
	}

	public void addPDFAttachment(byte[] pdf) {

		Attachment attachment = new Attachment();

		TokenWithOptionalLang title = new TokenWithOptionalLang();
		title.setValue("EMREX transcript");
		title.setValue("en");
		attachment.getTitle().add(title);

		attachment.setType("EMREX transcript");

		attachment.setContent("data:application/pdf;base64," + DatatypeConverter.printBase64Binary(pdf));

		this.elmo.getAttachment().clear(); // Remove existing attachments to avoid
																				// duplicates
		this.elmo.getAttachment().add(attachment);
	}

	/**
	 * Elmo with a learning instance selection removes all learning opportunities
	 * not selected even if a learning opprtunity has a child that is among the
	 * selected courses.
	 *
	 * @param courses
	 * @return String representation of Elmo-xml with selected courses
	 * @throws ParserConfigurationException
	 */
	public String asXml(String... courses) throws ParserConfigurationException {
		String copyElmoString = getStringFromDoc(elmo);
		Elmo copyElmo = asElmo(copyElmoString).get();
		selectCourses(copyElmo, Arrays.asList(courses));
		String xml = getStringFromDoc(copyElmo);
		return xml;
	}

	/*
	 * Get all learning opputinitys.
	 * @since EMREX-23
	 * @author z6cxh
	 */
	public String asXml() throws ParserConfigurationException {
		String copyElmoString = getStringFromDoc(elmo);
		Elmo copyElmo = asElmo(copyElmoString).get();
		String xml = getStringFromDoc(copyElmo);
		return xml;
	}

	public String asJson(String... courses) {
		String jsonString = null;
		try {
			Elmo elmoCopy = asElmo(this.asXml()).get();
			selectCourses(elmoCopy, Arrays.asList(courses));
			jsonString = "{ \"elmo\" : " + asJson(elmoCopy) + " }";
			log.debug(jsonString);
		} catch (ParserConfigurationException e) {
			log.error("Error marshalling Elmo", e);
		}
		return jsonString;
	}
	
	/*
	 * Get all learning opputinitys.
	 * @since EMREX-23
	 * @author z6cxh
	 */
	public String asJson() {
		String jsonString = null;
		try {
			Elmo elmoCopy = asElmo(this.asXml()).get();
		//	selectCourses(elmoCopy, Arrays.asList(courses));
			jsonString = "{ \"elmo\" : " + asJson(elmoCopy) + " }";
			log.debug(jsonString);
		} catch (ParserConfigurationException e) {
			log.error("Error marshalling Elmo", e);
		}
		return jsonString;
	}

	/**
	 * Remove all courses from Elmo that are not in list of courses.
	 * @param elmo
	 * @param courses
	 */
	private void selectCourses(Elmo elmo, List<String> courses) {
		if (courses != null) { 
			List<Elmo.Report> reports = elmo.getReport();
			log.debug("reports: " + reports.size());
			for (Elmo.Report report : reports) {
				ArrayList<LearningOpportunitySpecification> losList = new ArrayList<LearningOpportunitySpecification>();
				List<LearningOpportunitySpecification> tempList = report.getLearningOpportunitySpecification();
				for (LearningOpportunitySpecification los : tempList) {
					getAllLearningOpportunities(los, losList);
				}

				// log.debug("templist size: " + tempList.size() + "; losList
				// size: " + losList.size());
				tempList.clear();
				// log.debug("templist cleared: " + tempList.size());
				for (LearningOpportunitySpecification spec : losList) {
					List<LearningOpportunitySpecification.Identifier> identifiers = spec.getIdentifier();
					for (LearningOpportunitySpecification.Identifier id : identifiers) {
						if (courses.contains(id.getValue())) {
							tempList.add(spec);
						}
					}
				}
			}
		}
	}
	
  public static List<LearningOpportunitySpecification> getAllLearningOpportunities(LearningOpportunitySpecification los, List<LearningOpportunitySpecification> losList) {
    if (los != null) {
        losList.add(los);
        List<LearningOpportunitySpecification.HasPart> hasParts = los.getHasPart();
        for (LearningOpportunitySpecification.HasPart hasPart : hasParts) {
            getAllLearningOpportunities(hasPart.getLearningOpportunitySpecification(), losList);
        }
        if (hasParts != null) {
            //log.debug("deleting parts: " + hasParts.size());
            hasParts.clear();
        }
    }
    return losList;
  }

	public static String asJson(Elmo elmo) {
		String jsonString = null;
		try {
			ObjectMapper om = new ObjectMapper();
			jsonString = om.writerWithDefaultPrettyPrinter().writeValueAsString(elmo);
		} catch (JsonProcessingException e) {
			log.error("Error marshalling Elmo", e);
		}
		return jsonString;
	}
	
	/**
	 * Count ECTS points for Elmo report.
	 * 
	 * @return
	 */
	public int getETCSCount() {
		BigDecimal etcsCount = BigDecimal.ZERO;
		List<Report> reports = this.elmo.getReport();
		for (Report report : reports) {
			List<LearningOpportunitySpecification> learningOpportunitySpecifications = report
					.getLearningOpportunitySpecification();
			for (LearningOpportunitySpecification learningOpportunitySpecification : learningOpportunitySpecifications) {
				Specifies specifies = learningOpportunitySpecification.getSpecifies();
				List<Credit> credits = specifies.getLearningOpportunityInstance().getCredit();
				for (Credit credit : credits) {
					if ("ECTS".equalsIgnoreCase(credit.getScheme())) {
						etcsCount = etcsCount.add(credit.getValue());
					}
				}
			}
		}
		return etcsCount.intValue();
	}

	/**
	 * Count ECTS points for Elmo report.
	 * 
	 * Wunder why getETCSCount() build to return integer whe ETCS is fraction walues?
	 * 
	 * @since EMREX-17
	 * @author z6cxh
	 */
	public float getETCSCountAsFloat() {
		BigDecimal etcsCount = BigDecimal.ZERO;
		List<Report> reports = this.elmo.getReport();
		for (Report report : reports) {
			List<LearningOpportunitySpecification> learningOpportunitySpecifications = report
					.getLearningOpportunitySpecification();
			for (LearningOpportunitySpecification learningOpportunitySpecification : learningOpportunitySpecifications) {
				Specifies specifies = learningOpportunitySpecification.getSpecifies();
				List<Credit> credits = specifies.getLearningOpportunityInstance().getCredit();
				for (Credit credit : credits) {
					if ("ECTS".equalsIgnoreCase(credit.getScheme()) && credit!=null) {
						etcsCount = etcsCount.add(credit.getValue());
					}
				}
			}
		}
		return etcsCount.floatValue();
	}
	/**
	 * Get number of courses from Elmo report
	 * 
	 * @return number of courses from Elmo report
	 * @throws Exception
	 */
	public int getCoursesCount() throws Exception {
		int coursesCount = 0;
		List<Report> reports = this.elmo.getReport();
		for (Report report : reports) {
			List<LearningOpportunitySpecification> learningOpportunitySpecifications = report
					.getLearningOpportunitySpecification();
			for (LearningOpportunitySpecification learningOpportunitySpecification : learningOpportunitySpecifications) {
				if ("Course".equals(learningOpportunitySpecification.getType())) {
					coursesCount += 1;
				}
			}
		}
		return coursesCount;
	}

	public String getHostInstitution() {
		String hostInstitution = "Unknown host institution";

		List<Report> reports = this.elmo.getReport();
		for (Report report : reports) {
			if (report.getIssuer() != null) {
				List<Identifier> identifiers = report.getIssuer().getIdentifier();
				for (Identifier identifier : identifiers) {
					if ("schac".equals(identifier.getType())) {
						hostInstitution = identifier.getValue();
					}
				}
			}
		}

		return hostInstitution;
	}

	public String getHostCountry() {
		String hostCountry = "Unknown country";

		List<Report> reports = this.elmo.getReport();
		for (Report report : reports) {
			if (report.getIssuer() != null) {
				hostCountry = report.getIssuer().getCountry().value();
			}
		}

		return hostCountry;
	}
	
	private static Optional<Elmo> asElmo(@NonNull String xml) {
		Elmo elmo = null;

		try {
			JAXBContext jc = JAXBContext.newInstance("https.github_com.emrex_eu.elmo_schemas.tree.v1");
			Unmarshaller um = jc.createUnmarshaller();
			elmo = (Elmo)um.unmarshal(new StringReader(xml));
		} catch (JAXBException e) {
			log.error("Error unmarshalling Elmo", e);
		}
		
		return Optional.of(elmo);
	}

	private static String getStringFromDoc(Elmo elmo) {
		String xml = null;
		try {
			JAXBContext jc = JAXBContext.newInstance("https.github_com.emrex_eu.elmo_schemas.tree.v1");
			StringWriter out = new StringWriter();

			Marshaller m = jc.createMarshaller();
			
			// Forcing WebLogic to use Elmo namespace as default namespace
			// Receiving EMREG system requires Elmo namespace to be default.
			try {
	            m.setProperty("com.sun.xml.internal.bind.namespacePrefixMapper", new ElmoAsDefaultNamespaceMapper());
	        } catch(PropertyException e) {
	            // Ignore in case another JAXB implementation is used
	        }
			
			m.marshal(elmo, out);
			xml = out.toString();
			
			xml = ElmoParser.giveEctsValueTwoDecimal(xml);
		} catch (JAXBException e) {
			log.error("Error marshalling Elmo", e);
		}
		return xml;
	}
	
	
	public static class ElmoAsDefaultNamespaceMapper extends NamespacePrefixMapper {

		private static final String FOO_PREFIX = ""; // DEFAULT NAMESPACE
		private static final String FOO_URI = "https://github.com/emrex-eu/elmo-schemas/tree/v1";

		@Override
		public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
			if (FOO_URI.equals(namespaceUri)) {
				return FOO_PREFIX;
			}
			return suggestion;
		}

		@Override
		public String[] getPreDeclaredNamespaceUris() {
			return new String[] { FOO_URI };
		}

	}
	
	/** 
     * For activity loging.
     */
	public String getHostInstitutionForLoging() {
		String hostInstitution = "Unknown host institution";

		List<Report> reports = this.elmo.getReport();
		for (Report report : reports) {
			if (report.getIssuer() != null) {
				List<Identifier> identifiers = report.getIssuer().getIdentifier();
				for (Identifier identifier : identifiers) {
					if ("local".equals(identifier.getType())) {
						hostInstitution = identifier.getValue();
					}
				}
			}
		}

		return hostInstitution;
	}
	
	/**
	 * Make ECTS value have excatly 2 decimal (I know this misarable - but with my lack of jaxb knowlage - this the only way I could fix this)
	 * @since EMREX-25
	 * @author z6cxh
	 */
	public static String giveEctsValueTwoDecimal(String s) {
		List<String[]> toChange = new ArrayList<String[]>();

		Pattern pattern = Pattern.compile("<value>\\d*.\\d*</value>");
		Matcher matcher = pattern.matcher(s);
		while (matcher.find()) {
			String[] n = new String[] { s.substring(matcher.start(), matcher.end()),
					s.substring(matcher.start() + 7, matcher.end() - 8) };
			toChange.add(n);
		}
		for (String[] ns : toChange) {
			double d = Double.parseDouble(ns[1]);
			s = s.replaceAll(ns[0], String.format(Locale.ROOT, "<value>%.2f</value>", d));
		}

		return s;
	}
	
}

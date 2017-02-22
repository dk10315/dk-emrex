package dk.uds.emrex.ncp.stads;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.oxm.XmlMappingException;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceException;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.client.core.SoapActionCallback;

import dk.kmd.emrex.common.idp.IdpConfig;
import dk.kmd.emrex.common.idp.IdpConfig.IdpConfigUrl;
import dk.kmd.emrex.common.idp.IdpConfigListService;
import dk.uds.emrex.ncp.StudyFetcher;
import dk.uds.emrex.stads.wsdl.GetStudentsResultInput;
import dk.uds.emrex.stads.wsdl.GetStudentsResults;
import dk.uds.emrex.stads.wsdl.GetStudentsResultsOutput;
import dk.uds.emrex.stads.wsdl.GetStudentsResultsResponse;
import https.github_com.emrex_eu.elmo_schemas.tree.v1.Elmo;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by sj on 30-03-16.
 */
@Slf4j
public class StadsStudyFetcher extends WebServiceGatewaySupport implements StudyFetcher {

	@Autowired
	private IdpConfigListService idpConfigListService;

	@Override
	public Optional<Elmo> fetchElmo(@NotNull String institutionId, @NotNull String ssn) throws IOException {
		for (IdpConfig idpConfig : idpConfigListService.getIdpConfigs()) {
			if (idpConfig.getId().equalsIgnoreCase(institutionId)) {
				for (IdpConfigUrl idpConfigUrl : idpConfig.getGetStudentsResultWebserviceEndpoints()) {
					Elmo elmo = null;
					GetStudentsResultsResponse studentResult = getStudentResult(idpConfigUrl.getUrl(), ssn);
					if ((studentResult != null) && (studentResult.getReturn() != null) && (studentResult.getReturn().getElmoDocument()) != null) {
						elmo = StadsToElmoConverter.toElmo(studentResult.getReturn().getElmoDocument());
					}
					return Optional.ofNullable(elmo);
				}
			}
		}

		throw new IOException(String.format("No STADS servers known for IDP {}", institutionId));
	}

	public Optional<Elmo> fetchElmo(@NotNull Iterator<String> urls, @NotNull String ssn) throws IOException {
		Elmo elmo = null;
		while (urls.hasNext()) {
			String url = urls.next();
			GetStudentsResultsResponse studentResult = getStudentResult(url, ssn);
			if (studentResult != null) {
				elmo = StadsToElmoConverter.toElmo(studentResult.getReturn().getElmoDocument());
			}
		}
		return Optional.of(elmo);
	}
	
	private static BigInteger uuidToBigInteger(@NotNull UUID uuid) {
		final ByteBuffer buffer = ByteBuffer.allocate(16);

		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());

		return new BigInteger(buffer.array());
	}

	private GetStudentsResultsResponse getStudentResult(@NotNull String url, @NotNull String cpr) {
		final BigInteger requestId = uuidToBigInteger(UUID.randomUUID());

		final GetStudentsResults request = new GetStudentsResults();

		final GetStudentsResultInput input = new GetStudentsResultInput();
		input.setCPR(formatCprToStads(cpr));
		input.setRequestId(requestId);

		request.setInputStruct(input);

		final GetStudentsResultsResponse response = (GetStudentsResultsResponse) this.getWebServiceTemplate()
				.marshalSendAndReceive(url, request, new SoapActionCallback(url));
		return response;
	}

	/***
	 *
	 * @param url
	 * @param cpr
	 * @return ELMO XML as String
	 * @throws IOException
	 * @throws SoapFaultClientException
	 */
	private String getStudentsResults(@NotNull String url, @NotNull String cpr) throws IOException, WebServiceException {
		GetStudentsResultsResponse response = getStudentResult(url, cpr);

		final GetStudentsResultsOutput.ReceiptStructure receipt = response.getReturn().getReceiptStructure();

		switch (receipt.getReceiptCode()) {
		case 0:
			// Get ELMO document as XML string
			dk.uds.emrex.stads.wsdl.Elmo stadsElmo = response.getReturn().getElmoDocument();

			// String elmoString = marshall(stadsElmo);
			String elmoString = marshall(StadsToElmoConverter.toElmo(stadsElmo));
			log.debug("Returning ELMO string:\n{}", elmoString);
			return elmoString;
		default:
			throw new IOException(
					String.format("STADS error: %s - %s", receipt.getReceiptCode(), receipt.getReceiptText()));
		}
	}

	private String marshall(@NotNull https.github_com.emrex_eu.elmo_schemas.tree.v1.Elmo elmo) {
		final StringWriter xmlWriter = new StringWriter();
		final StreamResult marshalResult = new StreamResult(xmlWriter);
		try {
			Jaxb2Marshaller elmoMarshaller = new Jaxb2Marshaller();

			elmoMarshaller.setContextPath("https.github_com.emrex_eu.elmo_schemas.tree.v1");

			elmoMarshaller.marshal(elmo, marshalResult);
		} catch (XmlMappingException e) {
			log.error("Error marshalling : " + elmo, e);
		}
		String xml = xmlWriter.toString();
		log.debug("Marshalled to : ", xml);
		return xml;
	}

	private String marshall(@NotNull GetStudentsResultsResponse response) {
		final StringWriter xmlWriter = new StringWriter();
		final StreamResult marshalResult = new StreamResult(xmlWriter);
		try {
			this.getMarshaller().marshal(response, marshalResult);
		} catch (XmlMappingException e) {
			log.error("Error marshalling : " + response, e);
		} catch (IOException e) {
			log.error("Error marshalling : " + response, e);
		}
		String xml = xmlWriter.toString();
		log.debug("Marshalled to : ", xml);
		return xml;
	}

	private String marshall(@NotNull dk.uds.emrex.stads.wsdl.Elmo elmo) {
		final StringWriter xmlWriter = new StringWriter();
		final StreamResult marshalResult = new StreamResult(xmlWriter);
		final JAXBElement<dk.uds.emrex.stads.wsdl.Elmo> elmoJAXBElement = new JAXBElement<dk.uds.emrex.stads.wsdl.Elmo>(
				new QName("elmo"), dk.uds.emrex.stads.wsdl.Elmo.class, elmo);
		try {
			this.getMarshaller().marshal(elmoJAXBElement, marshalResult);
		} catch (XmlMappingException e) {
			log.error("Error marshalling : " + elmo, e);
		} catch (IOException e) {
			log.error("Error marshalling : " + elmo, e);
		}
		String xml = xmlWriter.toString();
		log.debug("Marshalled to : ", xml);
		return xml;
	}

	private static String formatCprToStads(@NotNull String cpr) {
		if (cpr.length() == 10) {
			return cpr.substring(0, 6) + "-" + cpr.substring(6);
		} else if (cpr.length() == 11) {
			return cpr;
		} else {
			throw new IllegalArgumentException("cpr must be either length 10 or 11");
		}
	}
}

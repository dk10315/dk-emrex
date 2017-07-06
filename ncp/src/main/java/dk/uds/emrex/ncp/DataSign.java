package dk.uds.emrex.ncp;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import javax.xml.bind.DatatypeConverter;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by marko.hollanti on 06/10/15.
 */
@Setter
@Slf4j
@Component
public class DataSign {

	private String certificate;
	private String encryptionKey;

	@Value("${ncp.path.certificate}")
	private String certificatePath;

	@Value("${ncp.path.encryption.key}")
	private String encryptionKeyPath;

	@Value("${ncp.environment}")
	private String environment;

	public String sign(String data, Charset charset) throws Exception {

		assertCertificateAndEncryptionKeyAvailable();

		// Create a DOM XMLSignatureFactory that will be used to generate the
		// enveloped signature.
		XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

		// Create a Reference to the enveloped document (in this case, you are
		// signing the whole
		// document, so a URI of "" signifies that, and also specify the SHA1
		// digest algorithm
		// and the ENVELOPED Transform.
		Reference ref = fac.newReference("", fac.newDigestMethod(DigestMethod.SHA256, null),
				Collections.singletonList(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null,
				null);
	//	Reference ref = fac.newReference("", fac.newDigestMethod(DigestMethod.SHA1, null),
	//			Collections.singletonList(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null,
	//			null);

		// Create the SignedInfo.
		SignedInfo si = fac.newSignedInfo(
				fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
				fac.newSignatureMethod(SignatureMethod.RSA_SHA1, null), Collections.singletonList(ref));

		// Instantiate the document to be signed.
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		InputStream is = new ByteArrayInputStream(data.getBytes(charset)); // StandardCharsets.ISO_8859_1
		Document doc = dbf.newDocumentBuilder().parse(is);

		// Extract the private key from string
		encryptionKey = encryptionKey.replaceAll("(-----.*?-----)", "");

		byte[] decoded = DatatypeConverter.parseBase64Binary(encryptionKey);

		PKCS8EncodedKeySpec rsaPrivKeySpec = new PKCS8EncodedKeySpec(decoded);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		RSAPrivateKey pk = (RSAPrivateKey) kf.generatePrivate(rsaPrivKeySpec);

		// Create a DOMSignContext and specify the RSA PrivateKey and
		// location of the resulting XMLSignature's parent element.
		DOMSignContext dsc = new DOMSignContext(pk, doc.getDocumentElement());

		// Create the XMLSignature, but don't sign it yet.
		KeyInfoFactory kif = fac.getKeyInfoFactory();
		X509Certificate cert = getCertificate(certificate);
		List<Object> x509Content = new ArrayList<Object>();
		x509Content.add(cert.getSubjectX500Principal().getName());
		x509Content.add(cert);
		X509Data xd = kif.newX509Data(x509Content);
		KeyInfo ki = kif.newKeyInfo(Collections.singletonList(xd));
		XMLSignature signature = fac.newXMLSignature(si, ki);

		// Marshal, generate, and sign the enveloped signature.
		signature.sign(dsc);

		String fileEncoding = System.getProperty("file.encoding");
		log.debug("file encoding : " + fileEncoding);

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer trans = tf.newTransformer();
		trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		trans.transform(new DOMSource(doc), new StreamResult(os));

		final String signedXml = os.toString();

		String baseEncodedCompressedXml = packBase64CompressedXml(signedXml);

		boolean isValid = isValidSignature(baseEncodedCompressedXml, charset);

		if (!isValid) {
			throw new RuntimeException("Elmo is not valid");
		}

		return baseEncodedCompressedXml;
	}
	
	public String unpackBase64CompressedXml(String base64EncodedCompressedXml) throws IOException {
		byte[] compressedXml = DatatypeConverter.parseBase64Binary(base64EncodedCompressedXml);
		
		String xml = GzipUtil.gzipDecompress(compressedXml);
		return xml;
	}

	public String packBase64CompressedXml(String xml) throws IOException {
		final byte[] compressedXml = GzipUtil.compress(xml);

		String baseEncodedCompressedXml = DatatypeConverter.printBase64Binary(compressedXml);
		return baseEncodedCompressedXml;
	}

	boolean isValidSignature(String base64EncodedCompressedXml, Charset charset) {
		boolean valid = false;

		try {
			
			byte[] compressedXml = DatatypeConverter.parseBase64Binary(base64EncodedCompressedXml);
			
			String xml = GzipUtil.gzipDecompress(compressedXml);
			
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
			dbf.setNamespaceAware(true);

			org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(charset)));

			// Find Signature element.
			org.w3c.dom.NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
			if (nl.getLength() == 0) {
				throw new Exception("Cannot find Signature element");
			}

			// Create a DOMValidateContext and specify a KeySelector
			// and document context.
			DOMValidateContext valContext = new DOMValidateContext(new X509KeySelector(), nl.item(0));

			// Unmarshal the XMLSignature.
			XMLSignature signature = fac.unmarshalXMLSignature(valContext);

			// Validate the XMLSignature.
			valid = signature.validate(valContext);

			log.debug(docToFormattedXml(doc));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return valid;
	}

	private static String docToFormattedXml(org.w3c.dom.Document doc)
			throws IOException, TransformerException {
		StringWriter sw = new StringWriter();
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

		transformer.transform(new DOMSource(doc), new StreamResult(sw));
		
		String xml = sw.toString();
		return xml;
	}

	private void assertCertificateAndEncryptionKeyAvailable() throws Exception {
		if (certificate == null) {
			certificate = readFileContent(certificatePath);
		}
		if (encryptionKey == null) {
			encryptionKey = readFileContent(encryptionKeyPath);
		}
	}

	private String readFileContent(String path) throws Exception {
		String fileContent = null;

		try {
			Path certPath = Paths.get(path);
			fileContent = new String(Files.readAllBytes(certPath));
		} catch (Exception e) {
			log.info("Certifikat kunne ikke læses fra præcis path " + path);
		}

		if (fileContent == null) {
			if (path.startsWith("/")) {
				try {
					Path certPath = Paths.get(path.substring(1));
					fileContent = new String(Files.readAllBytes(certPath));
				} catch (Exception e) {
					log.info("Certifikat kunne ikke læses fra relativ path " + path.substring(1));
				}
			}
		}

		if (fileContent == null) {
			try {
				String cleanPath = path;
				if (cleanPath.lastIndexOf("/") >= 0) {
					cleanPath = path.substring(path.lastIndexOf("/") + 1);
				}
				fileContent = FileReader.getFileContent(cleanPath);
			} catch (Exception e) {
				log.debug("Certifikat " + path + " kunne ikke læses fra classpath", e);
			}
		}

		if (fileContent == null) {
			throw new IOException("Certifikat kunne ikke findes med path " + path);
		}

		return fileContent;
	}

	private static X509Certificate getCertificate(String certString) throws IOException, GeneralSecurityException {
		InputStream is = new ByteArrayInputStream(certString.getBytes());
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
		is.close();
		return cert;
	}

	/** @see http://www.oracle.com/technetwork/articles/javase/dig-signature-api-140772.html */
	private static final class X509KeySelector extends KeySelector {
		public KeySelectorResult select(KeyInfo keyInfo, KeySelector.Purpose purpose, AlgorithmMethod method,
				XMLCryptoContext context) throws KeySelectorException {
			Iterator<XMLStructure> ki = keyInfo.getContent().iterator();
			while (ki.hasNext()) {
				XMLStructure info = (XMLStructure) ki.next();
				if (!(info instanceof X509Data))
					continue;
				X509Data x509Data = (X509Data) info;
				Iterator xi = x509Data.getContent().iterator();
				while (xi.hasNext()) {
					Object o = xi.next();
					if (!(o instanceof X509Certificate))
						continue;
					final PublicKey key = ((X509Certificate) o).getPublicKey();
					// Make sure the algorithm is compatible
					// with the method.
					if (algEquals(method.getAlgorithm(), key.getAlgorithm())) {
						return new KeySelectorResult() {
							public Key getKey() {
								return key;
							}
						};
					}
				}
			}
			throw new KeySelectorException("No key found!");
		}

		boolean algEquals(String algURI, String algName) {
			if ((algName.equalsIgnoreCase("DSA") && algURI.equalsIgnoreCase(SignatureMethod.DSA_SHA1))
					|| (algName.equalsIgnoreCase("RSA") && algURI.equalsIgnoreCase(SignatureMethod.RSA_SHA1))) {
				return true;
			} else {
				return false;
			}
		}
	}

}

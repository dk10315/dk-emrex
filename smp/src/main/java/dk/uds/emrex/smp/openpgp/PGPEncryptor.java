package dk.uds.emrex.smp.openpgp;

/**
 * PGP Encryptor for encryption of files source
 * https://katariakapil.wordpress.com/2009/08/22/pgp-encryption-bouncycastle-openpgp/
 * 1) Download the dependent JARS
 * http://www.bouncycastle.org/download/bcprov-jdk14-122.jar
 * http://www.bouncycastle.org/download/bcpg-jdk14-122.jar 2) Should have a
 * key/pair
 */
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the Bouncy Castle (BC) PGP Encryption/Decryption algorithm.
 * Used to encryptFile files
 */
@Slf4j
public class PGPEncryptor {

    private BouncyCastleProvider bcp = null;
// Provider Name
    private static final String PROVIDER = "BC";

    public PGPEncryptor() {
        /**
         * Initialize the Bouncy Castle provider.
         */
       if (bcp == null) {
            bcp = new BouncyCastleProvider();
            Security.addProvider(bcp);
        }
         //Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public void encryptFile(File inFile, File keyFile, File outFile, boolean isArmoredOutput) throws IOException,
            NoSuchProviderException, NoSuchAlgorithmException, PGPException {

        OutputStream out = null;
        OutputStream cOut = null;
        PGPCompressedDataGenerator comData = null;

        try {
// get public key
            PGPPublicKey encKey = readPublicKey(keyFile);
            log.info(
                    "Key Strength = " + encKey.getBitStrength()
            );
            log.info(
                    "Algorithm = " + encKey.getAlgorithm()
            );

            int count = 0;
            for (java.util.Iterator iterator = encKey.getUserIDs(); iterator.hasNext();) {
                count++;
                log.info((String) iterator.next());
            }
            log.info(
                    "Key Count = " + count
            );

// init output stream
            out = new FileOutputStream(outFile);
            if (isArmoredOutput) {
                out = new ArmoredOutputStream(out);
            }

// encryptFile and compress input file content
            PGPEncryptedDataGenerator cPk = new PGPEncryptedDataGenerator(PGPEncryptedData.CAST5, new SecureRandom(), PROVIDER);
            cPk.addMethod(encKey);

            cOut = cPk.open(out, new byte[1 << 16]);
            comData = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);

// write encrypted content to a file
            PGPUtil.writeFileToLiteralData(comData.open(cOut), PGPLiteralData.BINARY, inFile, new byte[1 << 16]);

        } finally {
            if (comData != null) {
                comData.close();
            }
            if (cOut != null) {
                cOut.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    public OutputStream encryptFileToStream(File inFile, File keyFile, OutputStream out, boolean isArmoredOutput) throws IOException,
            NoSuchProviderException, NoSuchAlgorithmException, PGPException {

        //OutputStream out = null;
        OutputStream cOut = null;
        PGPCompressedDataGenerator comData = null;

        try {
// get public key
            PGPPublicKey encKey = readPublicKey(keyFile);
            log.info(
                    "Key Strength = " + encKey.getBitStrength()
            );
            log.info(
                    "Algorithm = " + encKey.getAlgorithm()
            );

            int count = 0;
            for (java.util.Iterator iterator = encKey.getUserIDs(); iterator.hasNext();) {
                count++;
                log.info((String) iterator.next());
            }
            log.info(
                    "Key Count = " + count
            );

// init output stream
            //out = new FileOutputStream(outFile);
            if (isArmoredOutput) {
                out = new ArmoredOutputStream(out);
            }

// encryptFile and compress input file content
            PGPEncryptedDataGenerator cPk = new PGPEncryptedDataGenerator(PGPEncryptedData.CAST5, new SecureRandom(), PROVIDER);
            cPk.addMethod(encKey);

            cOut = cPk.open(out, new byte[1 << 16]);
            comData = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);

// write encrypted content to a file
            PGPUtil.writeFileToLiteralData(comData.open(cOut), PGPLiteralData.BINARY, inFile, new byte[1 << 16]);

        } finally {
            if (comData != null) {
                comData.close();
            }
            if (cOut != null) {
                cOut.close();
            }
            if (out != null) {
                out.flush();
                out.close();
                return out;

            }
            return null;
                    
        }
    }

    public boolean validateEncryptionKey(File keyFile) throws IOException, PGPException {

        PGPPublicKey encKey = readPublicKey(keyFile);

        return encKey.isEncryptionKey();
    }

    private static PGPPublicKey readPublicKey(File keyFile)
            throws IOException, PGPException {

        PGPPublicKey key = null;
        InputStream in = null;
        try {
            in = PGPUtil.getDecoderStream(new FileInputStream(keyFile));
            PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(in);

// iterate through the key rings.
            Iterator rIt = pgpPub.getKeyRings();
            while (key == null && rIt.hasNext()) {
                PGPPublicKeyRing kRing = (PGPPublicKeyRing) rIt.next();
                Iterator kIt = kRing.getPublicKeys();
                while (key == null && kIt.hasNext()) {
                    PGPPublicKey k = (PGPPublicKey) kIt.next();
                    if (k.isEncryptionKey()) {
                        key = k;
                    }
                }
            }
            if (key == null) {
                throw new IllegalArgumentException(
                        "Can’t find encryption key in key ring: " + keyFile.getCanonicalPath()
                );
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
        return key;
    }

}

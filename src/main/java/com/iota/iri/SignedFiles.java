package com.iota.iri;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.utils.Converter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;

/**
 * Created by alon on 26/01/18.
 */
public class SignedFiles {
    private static final Logger log = LoggerFactory.getLogger(SignedFiles.class);

    public static boolean isFileSignatureValid(String filename, String signatureFilename, String publicKey, int depth, int index) {
        byte[] trits = new byte[Curl.HASH_LENGTH * 3];
        Sponge curl = SpongeFactory.create(SpongeFactory.Mode.KERL);
        BufferedReader reader = null;
        //digest file
        try {
            InputStream inputStream = SignedFiles.class.getResourceAsStream(filename);
            //if resource doesn't exist, read from file system
            if (inputStream == null) {
                inputStream = new FileInputStream(filename);
            }
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            reader = new BufferedReader(new InputStreamReader(bufferedInputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                Converter.trits(Converter.asciiToTrytes(line), trits, 0);
                curl.absorb(trits, 0, trits.length);
                Arrays.fill(trits, (byte) 0);
            }
        } catch (IOException e) {
            log.error("Can't read file " + filename, e);
            return false;
        } finally {
            IOUtils.closeQuietly(reader);
        }
        //validate signature
        trits = new byte[Curl.HASH_LENGTH];
        curl.squeeze(trits, 0, Curl.HASH_LENGTH);
        SpongeFactory.Mode mode = SpongeFactory.Mode.CURLP81;
        byte[] digests = new byte[0];
        byte[] bundle = ISS.normalizedBundle(trits);
        byte[] root;
        int i;
        try {
            InputStream inputStream = SignedFiles.class.getResourceAsStream(signatureFilename);
            if (inputStream == null) {
                inputStream = new FileInputStream(signatureFilename);
            }
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            reader = new BufferedReader(new InputStreamReader(bufferedInputStream));
            String line;
            for (i = 0; i < 3 && (line = reader.readLine()) != null; i++) {
                byte[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                Converter.trits(line, lineTrits, 0);
                digests = ArrayUtils.addAll(
                        digests,
                        ISS.digest(mode
                                , Arrays.copyOfRange(bundle, i * ISS.NORMALIZED_FRAGMENT_LENGTH, (i + 1) * ISS.NORMALIZED_FRAGMENT_LENGTH)
                                , lineTrits));
            }
            if ((line = reader.readLine()) != null) {
                byte[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                Converter.trits(line, lineTrits, 0);
                root = ISS.getMerkleRoot(mode, ISS.address(mode, digests), lineTrits, 0, index, depth);
            }
            else {
                root = ISS.address(mode, digests);
            }

            byte[] pubkeyTrits = Converter.allocateTritsForTrytes(publicKey.length());
            Converter.trits(publicKey, pubkeyTrits, 0);
            if (Arrays.equals(pubkeyTrits, root)) {
                //valid
                return true;
            }
        } catch (IOException e) {
            log.error("Can't read signature file " + filename, e);
            return false;
        } finally {
            IOUtils.closeQuietly(reader);
        }
        return false;
    }
}

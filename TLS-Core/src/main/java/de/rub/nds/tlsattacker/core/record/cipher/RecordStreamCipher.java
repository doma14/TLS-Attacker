/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2020 Ruhr University Bochum, Paderborn University,
 * and Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.record.cipher;

import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.crypto.cipher.CipherWrapper;
import de.rub.nds.tlsattacker.core.crypto.mac.MacWrapper;
import de.rub.nds.tlsattacker.core.crypto.mac.WrappedMac;
import de.rub.nds.tlsattacker.core.exceptions.CryptoException;
import de.rub.nds.tlsattacker.core.protocol.parser.Parser;
import de.rub.nds.tlsattacker.core.record.BlobRecord;
import de.rub.nds.tlsattacker.core.record.Record;
import de.rub.nds.tlsattacker.core.record.RecordCryptoComputations;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.KeySet;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import de.rub.nds.tlsattacker.transport.ConnectionEndType;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecordStreamCipher extends RecordCipher {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * mac for verification of incoming messages
     */
    private WrappedMac readMac;
    /**
     * mac object for macing outgoing messages
     */
    private WrappedMac writeMac;

    public RecordStreamCipher(TlsContext context, KeySet keySet) {
        super(context, keySet);
        initCipherAndMac();
    }

    private void initCipherAndMac() throws UnsupportedOperationException {
        try {
            ConnectionEndType localConEndType = context.getConnection().getLocalConnectionEndType();
            encryptCipher = CipherWrapper.getEncryptionCipher(cipherSuite, localConEndType, getKeySet());
            decryptCipher = CipherWrapper.getDecryptionCipher(cipherSuite, localConEndType, getKeySet());
            readMac = MacWrapper.getMac(version, cipherSuite, getKeySet().getReadMacSecret(localConEndType));
            writeMac = MacWrapper.getMac(version, cipherSuite, getKeySet().getWriteMacSecret(localConEndType));
        } catch (NoSuchAlgorithmException ex) {
            throw new UnsupportedOperationException("Cipher not supported: " + cipherSuite.name(), ex);
        }
    }

    public byte[] calculateMac(byte[] data, ConnectionEndType connectionEndType) {
        LOGGER.debug("The MAC was calculated over the following data: {}", ArrayConverter.bytesToHexString(data));
        byte[] result;
        if (connectionEndType == context.getChooser().getConnectionEndType()) {
            result = writeMac.calculateMac(data);
        } else {
            result = readMac.calculateMac(data);
        }
        LOGGER.debug("MAC: {}", ArrayConverter.bytesToHexString(result));
        return result;
    }

    @Override
    public void encrypt(Record record) throws CryptoException {
        if (record.getComputations() == null) {
            LOGGER.warn("Record computations are not prepared.");
            record.prepareComputations();
        }
        LOGGER.debug("Encrypting Record:");
        RecordCryptoComputations computations = record.getComputations();
        computations.setMacKey(getKeySet().getWriteMacSecret(context.getChooser().getConnectionEndType()));
        computations.setCipherKey(getKeySet().getWriteKey(context.getChooser().getConnectionEndType()));

        byte[] cleanBytes = record.getCleanProtocolMessageBytes().getValue();

        computations.setAuthenticatedNonMetaData(cleanBytes);

        // For unusual handshakes we need the length here if TLS 1.3 is
        // negotiated as a version.
        record.setLength(cleanBytes.length + AlgorithmResolver.getMacAlgorithm(version, cipherSuite).getSize());

        computations.setAuthenticatedMetaData(collectAdditionalAuthenticatedData(record, version));
        computations.setMac(calculateMac(ArrayConverter.concatenate(computations.getAuthenticatedMetaData().getValue(),
                computations.getAuthenticatedNonMetaData().getValue()), context.getConnection()
                .getLocalConnectionEndType()));

        computations.setPlainRecordBytes(ArrayConverter.concatenate(record.getCleanProtocolMessageBytes().getValue(),
                computations.getMac().getValue()));

        computations.setCiphertext(encryptCipher.encrypt(record.getComputations().getPlainRecordBytes().getValue()));

        record.setProtocolMessageBytes(computations.getCiphertext().getValue());
        // TODO our macs are always valid
        computations.setMacValid(true);
    }

    @Override
    public void decrypt(Record record) throws CryptoException {
        if (record.getComputations() == null) {
            LOGGER.warn("Record computations are not preapred.");
            record.prepareComputations();
        }
        LOGGER.debug("Decrypting Record");
        RecordCryptoComputations computations = record.getComputations();

        computations.setMacKey(getKeySet().getReadMacSecret(context.getChooser().getConnectionEndType()));
        computations.setCipherKey(getKeySet().getReadKey(context.getChooser().getConnectionEndType()));

        byte[] cipherText = record.getProtocolMessageBytes().getValue();

        computations.setCiphertext(cipherText);
        byte[] plainData = decryptCipher.decrypt(cipherText);
        computations.setPlainRecordBytes(plainData);
        plainData = computations.getPlainRecordBytes().getValue();
        DecryptionParser parser = new DecryptionParser(0, plainData);
        byte[] cleanBytes = parser.parseByteArrayField(plainData.length - readMac.getMacLength());
        record.setCleanProtocolMessageBytes(cleanBytes);
        record.getComputations().setAuthenticatedNonMetaData(cleanBytes);
        record.getComputations().setAuthenticatedMetaData(collectAdditionalAuthenticatedData(record, version));
        byte[] hmac = parser.parseByteArrayField(readMac.getMacLength());
        record.getComputations().setMac(hmac);
        byte[] calculatedHmac = calculateMac(
                ArrayConverter.concatenate(record.getComputations().getAuthenticatedMetaData().getValue(), record
                        .getComputations().getAuthenticatedNonMetaData().getValue()),
                context.getTalkingConnectionEndType());
        record.getComputations().setMacValid(Arrays.equals(hmac, calculatedHmac));
    }

    @Override
    public void encrypt(BlobRecord br) throws CryptoException {
        LOGGER.debug("Encrypting BlobRecord");
        br.setProtocolMessageBytes(encryptCipher.encrypt(br.getCleanProtocolMessageBytes().getValue()));
    }

    @Override
    public void decrypt(BlobRecord br) throws CryptoException {
        LOGGER.debug("Derypting BlobRecord");
        br.setProtocolMessageBytes(decryptCipher.decrypt(br.getCleanProtocolMessageBytes().getValue()));
    }

    class DecryptionParser extends Parser<Object> {

        public DecryptionParser(int startposition, byte[] array) {
            super(startposition, array);
        }

        @Override
        public Object parse() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public byte[] parseByteArrayField(int length) {
            return super.parseByteArrayField(length);
        }

        @Override
        public int getBytesLeft() {
            return super.getBytesLeft();
        }

        @Override
        public int getPointer() {
            return super.getPointer();
        }

    }
}

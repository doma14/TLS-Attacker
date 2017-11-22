/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.handler.extension;

import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.DigestAlgorithm;
import de.rub.nds.tlsattacker.core.constants.ExtensionType;
import de.rub.nds.tlsattacker.core.constants.HKDFAlgorithm;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.Tls13KeySetType;
import de.rub.nds.tlsattacker.core.crypto.HKDFunction;
import de.rub.nds.tlsattacker.core.protocol.message.extension.PSK.PSKIdentity;
import de.rub.nds.tlsattacker.core.protocol.message.extension.PSK.PskSet;
import de.rub.nds.tlsattacker.core.protocol.message.extension.PreSharedKeyExtensionMessage;
import de.rub.nds.tlsattacker.core.protocol.parser.extension.ExtensionParser;
import de.rub.nds.tlsattacker.core.protocol.parser.extension.PreSharedKeyExtensionParser;
import de.rub.nds.tlsattacker.core.protocol.preparator.extension.ExtensionPreparator;
import de.rub.nds.tlsattacker.core.protocol.preparator.extension.PreSharedKeyExtensionPreparator;
import de.rub.nds.tlsattacker.core.protocol.serializer.extension.ExtensionSerializer;
import de.rub.nds.tlsattacker.core.protocol.serializer.extension.PreSharedKeyExtensionSerializer;
import de.rub.nds.tlsattacker.core.record.cipher.RecordCipher;
import de.rub.nds.tlsattacker.core.record.cipher.RecordCipherFactory;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.KeySet;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.KeySetGenerator;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import de.rub.nds.tlsattacker.transport.ConnectionEndType;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;

/**
 * RFC draft-ietf-tls-tls13-21
 */
public class PreSharedKeyExtensionHandler extends ExtensionHandler<PreSharedKeyExtensionMessage> {

    public PreSharedKeyExtensionHandler(TlsContext context) {
        super(context);
    }

    @Override
    public ExtensionParser getParser(byte[] message, int pointer) {
        return new PreSharedKeyExtensionParser(pointer, message);
    }

    @Override
    public ExtensionPreparator getPreparator(PreSharedKeyExtensionMessage message) {
        return new PreSharedKeyExtensionPreparator(context.getChooser(), message, getSerializer(message));
    }

    @Override
    public ExtensionSerializer getSerializer(PreSharedKeyExtensionMessage message) {
        return new PreSharedKeyExtensionSerializer(message, context.getConnectionEnd().getConnectionEndType());
    }

    @Override
    public void adjustTLSExtensionContext(PreSharedKeyExtensionMessage message) {
        LOGGER.debug("Adjusting TLS Context for PSK Key Extension Message");
        if (context.getConnectionEnd().getConnectionEndType() == ConnectionEndType.CLIENT) {
            if (message.getSelectedIdentity() != null) {
                adjustPsk(message);
            } else {
                context.setEarlyDataPSKIdentity(context.getChooser().getPskSets().get(0).getPreSharedKeyIdentity());
                context.setEarlyDataCipherSuite(context.getChooser().getPskSets().get(0).getCipherSuite());
            }
        }
        if (context.getConnectionEnd().getConnectionEndType() == ConnectionEndType.SERVER
                && message.getIdentities() != null) {
            selectPsk(message);
            if (context.isExtensionNegotiated(ExtensionType.EARLY_DATA)) {
                adjustEarlyTrafficSecret(message);
                adjustRecordLayer0RTT();
            }
        }
    }

    private void adjustPsk(PreSharedKeyExtensionMessage message) {
        if (message.getSelectedIdentity().getValue() < context.getChooser().getPskSets().size()) {
            LOGGER.debug("Setting PSK as chosen by server");
            context.setPsk(context.getChooser().getPskSets().get(message.getSelectedIdentity().getValue())
                    .getPreSharedKey());
            context.setSelectedIdentityIndex(message.getSelectedIdentity().getValue());
        } else {
            LOGGER.warn("The server's chosen PSK identity is unknown - no psk set");
        }
    }

    private void selectPsk(PreSharedKeyExtensionMessage message) {
        int pskIdentityIndex = 0;
        List<PskSet> pskSets = context.getChooser().getPskSets();
        for (PSKIdentity pskIdentity : message.getIdentities()) {
            for (int x = 0; x < pskSets.size(); x++) {
                if (Arrays.equals(pskSets.get(x).getPreSharedKeyIdentity(), pskIdentity.getIdentity().getValue())) {
                    LOGGER.debug("Selected PSK identity: "
                            + ArrayConverter.bytesToHexString(pskSets.get(x).getPreSharedKeyIdentity()));
                    context.setPsk(pskSets.get(x).getPreSharedKey());
                    context.setEarlyDataCipherSuite(pskSets.get(x).getCipherSuite());
                    context.setSelectedIdentityIndex(pskIdentityIndex);
                    return;
                }
            }
            pskIdentityIndex++;
        }
        LOGGER.warn("No matching PSK identity provided by client - no PSK was set");
    }

    private void adjustEarlyTrafficSecret(PreSharedKeyExtensionMessage message) {

        LOGGER.debug("Calculating early traffic secret using transcript: "
                + ArrayConverter.bytesToHexString(context.getDigest().getRawBytes()));

        List<PskSet> pskSets = context.getChooser().getPskSets();
        byte[] earlyDataPsk = null;
        for (int x = 0; x < pskSets.size(); x++) {
            if (Arrays.equals(pskSets.get(x).getPreSharedKeyIdentity(), message.getIdentities().get(0).getIdentity()
                    .getValue())) {
                earlyDataPsk = pskSets.get(x).getPreSharedKey();
                context.setEarlyDataCipherSuite(pskSets.get(x).getCipherSuite());
                LOGGER.debug("EarlyData PSK: " + ArrayConverter.bytesToHexString(earlyDataPsk));
                break;
            }
        }
        if (earlyDataPsk == null) {
            LOGGER.warn("Server is missing the EarlyData PSK - decryption will fail");
        } else {
            HKDFAlgorithm hkdfAlgortihm = AlgorithmResolver.getHKDFAlgorithm(context.getEarlyDataCipherSuite());
            DigestAlgorithm digestAlgo = AlgorithmResolver.getDigestAlgorithm(ProtocolVersion.TLS13,
                    context.getEarlyDataCipherSuite());

            byte[] psk = earlyDataPsk;
            byte[] earlySecret = HKDFunction.extract(hkdfAlgortihm, new byte[0], psk);
            byte[] earlyTrafficSecret = HKDFunction.deriveSecret(hkdfAlgortihm, digestAlgo.getJavaName(), earlySecret,
                    HKDFunction.CLIENT_EARLY_TRAFFIC_SECRET, context.getDigest().getRawBytes());

            context.setEarlySecret(earlySecret);
            context.setClientEarlyTrafficSecret(earlyTrafficSecret);
        }

    }

    private void adjustRecordLayer0RTT() {
        try {
            LOGGER.debug("Setting up RecordLayer to allow for EarlyData decryption");

            context.setActiveClientKeySetType(Tls13KeySetType.EARLY_TRAFFIC_SECRETS);
            KeySet keySet = KeySetGenerator.generateKeySet(context, ProtocolVersion.TLS13,
                    context.getActiveClientKeySetType());
            RecordCipher recordCipher = RecordCipherFactory.getRecordCipher(context, keySet,
                    context.getEarlyDataCipherSuite());
            context.getRecordLayer().setRecordCipher(recordCipher);
            context.getRecordLayer().updateDecryptionCipher();
            context.setReadSequenceNumber(0);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(PreSharedKeyExtensionHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}

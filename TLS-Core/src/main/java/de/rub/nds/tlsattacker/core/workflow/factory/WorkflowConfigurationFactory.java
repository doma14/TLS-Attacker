/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.workflow.factory;

import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.exceptions.ConfigurationException;
import de.rub.nds.tlsattacker.core.protocol.message.ApplicationMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateRequestMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateVerifyMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ChangeCipherSpecMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ClientHelloMessage;
import de.rub.nds.tlsattacker.core.protocol.message.DHClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.DHEServerKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ECDHClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ECDHEServerKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.FinishedMessage;
import de.rub.nds.tlsattacker.core.protocol.message.HeartbeatMessage;
import de.rub.nds.tlsattacker.core.protocol.message.HelloVerifyRequestMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ProtocolMessage;
import de.rub.nds.tlsattacker.core.protocol.message.RSAClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ServerHelloDoneMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ServerHelloMessage;
import de.rub.nds.tlsattacker.core.workflow.TlsConfig;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.MessageActionFactory;
import de.rub.nds.tlsattacker.transport.ConnectionEnd;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Juraj Somorovsky <juraj.somorovsky@rub.de>
 * @author Philip Riese <philip.riese@rub.de>
 */
public class WorkflowConfigurationFactory {

    static final Logger LOGGER = LogManager.getLogger(WorkflowConfigurationFactory.class);

    protected final TlsConfig config;

    public WorkflowConfigurationFactory(TlsConfig config) {
        this.config = config;
    }

    public WorkflowTrace createWorkflowTrace(WorkflowTraceType type) {
        switch (type) {
            case HELLO:
                return createHelloWorkflow();
            case FULL:
                return createFullWorkflow();
            case HANDSHAKE:
                return createHandshakeWorkflow();
        }
        throw new ConfigurationException("Unknown WorkflowTraceType " + type.name());
    }

    public WorkflowTrace createHelloWorkflow() {
        WorkflowTrace workflowTrace = new WorkflowTrace();
        List<ProtocolMessage> messages = new LinkedList<>();
        ClientHelloMessage clientHello = null;
        if (config.getHighestProtocolVersion() == ProtocolVersion.DTLS10
                || config.getHighestProtocolVersion() == ProtocolVersion.DTLS12) {
            clientHello = new ClientHelloMessage(config);
            clientHello.setIncludeInDigest(false);
        } else {
            clientHello = new ClientHelloMessage(config);
        }
        messages.add(clientHello);

        workflowTrace.add(MessageActionFactory.createAction(config.getConnectionEnd(), ConnectionEnd.CLIENT, messages));
        if (config.getHighestProtocolVersion() == ProtocolVersion.DTLS10
                || config.getHighestProtocolVersion() == ProtocolVersion.DTLS12) {

            HelloVerifyRequestMessage helloVerifyRequestMessage = new HelloVerifyRequestMessage(config);
            helloVerifyRequestMessage.setIncludeInDigest(false);
            messages = new LinkedList<>();

            messages.add(helloVerifyRequestMessage);
            workflowTrace.add(MessageActionFactory.createAction(config.getConnectionEnd(), ConnectionEnd.SERVER,
                    messages));
            clientHello = new ClientHelloMessage(config);
            messages = new LinkedList<>();
            messages.add(clientHello);
            workflowTrace.add(MessageActionFactory.createAction(config.getConnectionEnd(), ConnectionEnd.CLIENT,
                    messages));
        }
        messages = new LinkedList<>();
        messages.add(new ServerHelloMessage(config));
        messages.add(new CertificateMessage(config));
        if (config.getSupportedCiphersuites().get(0).isEphemeral()) {
            addServerKeyExchangeMessage(messages);
        }
        if (config.isClientAuthentication()) {
            CertificateRequestMessage certRequest = new CertificateRequestMessage(config);
            messages.add(certRequest);
        }

        messages.add(new ServerHelloDoneMessage(config));
        workflowTrace.add(MessageActionFactory.createAction(config.getConnectionEnd(), ConnectionEnd.SERVER, messages));
        return workflowTrace;
    }

    public WorkflowTrace createHandshakeWorkflow() {
        WorkflowTrace workflowTrace = this.createHelloWorkflow();
        List<ProtocolMessage> messages = new LinkedList<>();
        if (config.isClientAuthentication()) {
            messages.add(new CertificateMessage(config));
            addClientKeyExchangeMessage(messages);
            messages.add(new CertificateVerifyMessage(config));
        } else {
            addClientKeyExchangeMessage(messages);
        }
        messages.add(new ChangeCipherSpecMessage(config));
        messages.add(new FinishedMessage(config));
        workflowTrace.add(MessageActionFactory.createAction(config.getConnectionEnd(), ConnectionEnd.CLIENT, messages));
        messages = new LinkedList<>();
        messages.add(new ChangeCipherSpecMessage(config));
        messages.add(new FinishedMessage(config));
        workflowTrace.add(MessageActionFactory.createAction(config.getConnectionEnd(), ConnectionEnd.SERVER, messages));
        return workflowTrace;

    }

    private void addClientKeyExchangeMessage(List<ProtocolMessage> messages) {
        CipherSuite cs = config.getSupportedCiphersuites().get(0);
        switch (AlgorithmResolver.getKeyExchangeAlgorithm(cs)) {
            case RSA:
                messages.add(new RSAClientKeyExchangeMessage(config));
                break;
            case ECDHE_ECDSA:
            case ECDH_ECDSA:
            case ECDH_RSA:
            case ECDHE_RSA:
                messages.add(new ECDHClientKeyExchangeMessage(config));
                break;
            case DHE_DSS:
            case DHE_RSA:
            case DH_ANON:
            case DH_DSS:
            case DH_RSA:
                messages.add(new DHClientKeyExchangeMessage(config));
                break;
            default:
                LOGGER.warn("Unsupported key exchange algorithm: " + AlgorithmResolver.getKeyExchangeAlgorithm(cs)
                        + ", not adding ClientKeyExchange Message");
                break;
        }
    }

    private void addServerKeyExchangeMessage(List<ProtocolMessage> messages) {
        CipherSuite cs = config.getSupportedCiphersuites().get(0);
        switch (AlgorithmResolver.getKeyExchangeAlgorithm(cs)) {
            case RSA:
                messages.add(new ECDHEServerKeyExchangeMessage(config));
                break;
            case ECDHE_ECDSA:
            case ECDH_ECDSA:
            case ECDH_RSA:
            case ECDHE_RSA:
                messages.add(new ECDHEServerKeyExchangeMessage(config));
                break;
            case DHE_DSS:
            case DHE_RSA:
            case DH_ANON:
            case DH_DSS:
            case DH_RSA:
                messages.add(new DHEServerKeyExchangeMessage(config));
                break;
            default:
                LOGGER.warn("Unsupported key exchange algorithm: " + AlgorithmResolver.getKeyExchangeAlgorithm(cs)
                        + ", not adding ServerKeyExchange Message");
                break;
        }
    }

    /**
     * Creates an extended TLS workflow including an application data and
     * heartbeat messages
     *
     * @return
     */
    public WorkflowTrace createFullWorkflow() {
        WorkflowTrace workflowTrace = this.createHandshakeWorkflow();
        List<ProtocolMessage> messages = new LinkedList<>();
        if (config.isServerSendsApplicationData()) {
            messages.add(new ApplicationMessage(config));
            workflowTrace.add(MessageActionFactory.createAction(config.getConnectionEnd(), ConnectionEnd.SERVER,
                    messages));
            messages = new LinkedList<>();
        }
        messages.add(new ApplicationMessage(config));

        if (config.isAddHeartbeatExtension()) {
            messages.add(new HeartbeatMessage(config));
            workflowTrace.add(MessageActionFactory.createAction(config.getConnectionEnd(), ConnectionEnd.CLIENT,
                    messages));
            messages = new LinkedList<>();
            messages.add(new HeartbeatMessage(config));
            workflowTrace.add(MessageActionFactory.createAction(config.getConnectionEnd(), ConnectionEnd.SERVER,
                    messages));
        } else {
            workflowTrace.add(MessageActionFactory.createAction(config.getConnectionEnd(), ConnectionEnd.CLIENT,
                    messages));
        }
        return workflowTrace;
    }
}
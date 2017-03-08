/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.tls.protocol.message;

import de.rub.nds.tlsattacker.modifiablevariable.ModifiableVariableFactory;
import de.rub.nds.tlsattacker.modifiablevariable.ModifiableVariableProperty;
import de.rub.nds.tlsattacker.modifiablevariable.singlebyte.ModifiableByte;
import de.rub.nds.tlsattacker.tls.constants.ProtocolMessageType;
import de.rub.nds.tlsattacker.tls.protocol.handler.ProtocolMessageHandler;
import de.rub.nds.tlsattacker.tls.protocol.serializer.ChangeCipherSpecSerializer;
import de.rub.nds.tlsattacker.tls.protocol.serializer.Serializer;
import de.rub.nds.tlsattacker.tls.workflow.TlsConfig;
import de.rub.nds.tlsattacker.tls.workflow.TlsContext;

/**
 * @author Juraj Somorovsky <juraj.somorovsky@rub.de>
 */
public class ChangeCipherSpecMessage extends ProtocolMessage {

    @ModifiableVariableProperty
    ModifiableByte ccsProtocolType;

    public ChangeCipherSpecMessage(TlsConfig tlsConfig) {
        super();
        this.protocolMessageType = ProtocolMessageType.CHANGE_CIPHER_SPEC;
    }

    public ChangeCipherSpecMessage() {
        super();
        this.protocolMessageType = ProtocolMessageType.CHANGE_CIPHER_SPEC;
    }

    public ModifiableByte getCcsProtocolType() {
        return ccsProtocolType;
    }

    public void setCcsProtocolType(ModifiableByte ccsProtocolType) {
        this.ccsProtocolType = ccsProtocolType;
    }

    public void setCcsProtocolType(byte value) {
        this.ccsProtocolType = ModifiableVariableFactory.safelySetValue(ccsProtocolType, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nChangeCipherSpec message:").append("\n  CCS Protocol Message: ")
                .append(String.format("%02X ", ccsProtocolType.getValue()));
        return sb.toString();
    }

    @Override
    public String toCompactString() {
        return "ChangeCipherSpec";
    }

    @Override
    public Serializer getSerializer() {
        return new ChangeCipherSpecSerializer(this);
    }
}
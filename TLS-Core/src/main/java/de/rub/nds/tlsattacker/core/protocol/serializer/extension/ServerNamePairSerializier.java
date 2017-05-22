/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.serializer.extension;

import de.rub.nds.tlsattacker.core.constants.ExtensionByteLength;
import de.rub.nds.tlsattacker.core.protocol.message.extension.SNI.ServerNamePair;
import de.rub.nds.tlsattacker.core.protocol.serializer.Serializer;

/**
 *
 * @author Robert Merget - robert.merget@rub.de
 */
public class ServerNamePairSerializier extends Serializer<ServerNamePair> {

    private final ServerNamePair pair;

    public ServerNamePairSerializier(ServerNamePair pair) {
        this.pair = pair;
    }

    @Override
    protected byte[] serializeBytes() {
        appendByte(pair.getServerNameType().getValue());
        appendInt(pair.getServerNameLength().getValue(), ExtensionByteLength.SERVER_NAME_LENGTH);
        appendBytes(pair.getServerName().getValue());
        return getAlreadySerialized();
    }

}

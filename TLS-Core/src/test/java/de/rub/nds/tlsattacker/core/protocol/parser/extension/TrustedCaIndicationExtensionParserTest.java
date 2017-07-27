/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.parser.extension;

import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.tlsattacker.core.constants.ExtensionType;
import de.rub.nds.tlsattacker.core.protocol.message.extension.TrustedCaIndicationExtensionMessage;
import de.rub.nds.tlsattacker.core.protocol.message.extension.trustedauthority.TrustedAuthority;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *
 * @author Matthias Terlinde <matthias.terlinde@rub.de>
 */
@RunWith(Parameterized.class)
public class TrustedCaIndicationExtensionParserTest {

    private final ExtensionType type;
    private final byte[] extensionBytes;
    private final int startposition;
    private final int extensionLength;
    private final List<TrustedAuthority> trustedAuthoritiesList;
    private final int trustedAuthoritiesLength;

    public TrustedCaIndicationExtensionParserTest(ExtensionType type, byte[] extensionBytes, int startposition,
            int extensionLength, List<TrustedAuthority> trustedAuthoritiesList, int trustedAuthoritiesLength) {
        this.type = type;
        this.extensionBytes = extensionBytes;
        this.startposition = startposition;
        this.extensionLength = extensionLength;
        this.trustedAuthoritiesList = trustedAuthoritiesList;
        this.trustedAuthoritiesLength = trustedAuthoritiesLength;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> generateData() {
        return Arrays.asList(new Object[][] { {
                ExtensionType.TRUSTED_CA_KEYS,
                ArrayConverter.hexStringToByteArray("0003000B0009000200050102030405"),
                0,
                11,
                Arrays.asList(new TrustedAuthority((byte) 0, null, null, null), new TrustedAuthority((byte) 2, null, 5,
                        new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 })), 9 } });
    }

    @Test
    public void testParse() {
        TrustedCaIndicationExtensionParser parser = new TrustedCaIndicationExtensionParser(startposition,
                extensionBytes);
        TrustedCaIndicationExtensionMessage msg = parser.parse();

        assertArrayEquals(type.getValue(), msg.getExtensionType().getValue());
        assertEquals(extensionLength, (int) msg.getExtensionLength().getValue());

        assertEquals(trustedAuthoritiesLength, (int) msg.getTrustedAuthoritiesLength().getValue());
        assertCachedObjectList(trustedAuthoritiesList, msg.getTrustedAuthorities());
    }

    public void assertCachedObjectList(List<TrustedAuthority> expected, List<TrustedAuthority> actual) {
        for (int i = 0; i < expected.size(); i++) {
            TrustedAuthority expectedObject = expected.get(i);
            TrustedAuthority actualObject = actual.get(i);

            assertEquals(expectedObject.getIdentifierType().getValue(), actualObject.getIdentifierType().getValue());
            if (expectedObject.getDistinguishedNameLength().getValue() != null) {
                assertEquals(expectedObject.getDistinguishedNameLength().getValue(), actualObject
                        .getDistinguishedNameLength().getValue());
            } else {
                assertNull(actualObject.getDistinguishedNameLength());
            }
            if (expectedObject.getSha1Hash().getValue() != null) {
                assertArrayEquals(expectedObject.getSha1Hash().getValue(), actualObject.getSha1Hash().getValue());
            } else {
                assertNull(actualObject.getSha1Hash());
            }
            if (expectedObject.getDistinguishedName().getValue() != null) {
                assertArrayEquals(expectedObject.getDistinguishedName().getValue(), actualObject.getDistinguishedName()
                        .getValue());
            } else {
                assertNull(actualObject.getDistinguishedName());
            }
        }
    }
}

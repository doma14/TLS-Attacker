/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0 http://www.apache.org/licenses/LICENSE-2.0
 */
package Analyzer;

import Config.Analyzer.AnalyzeModificationRuleConfig;
import Config.EvolutionaryFuzzerConfig;
import Graphs.BranchTrace;
import Modification.AddMessageModification;
import Modification.AddRecordModification;
import Modification.ChangeServerCertificateModification;
import Modification.DuplicateMessageModification;
import Modification.ModificationType;
import Modification.ModifyFieldModification;
import Result.Result;
import TestVector.TestVector;
import de.rub.nds.tlsattacker.tls.protocol.alert.AlertMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ClientHelloMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ServerHelloDoneMessage;
import de.rub.nds.tlsattacker.util.FileHelper;
import de.rub.nds.tlsattacker.wrapper.MutableInt;
import java.io.File;
import java.util.HashMap;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 
 * @author ic0ns
 */
public class AnalyzeModificationRuleTest {

    private AnalyzeModificationRule rule;
    private TestVector vector;

    public AnalyzeModificationRuleTest() {
    }

    @Before
    public void setUp() {
	EvolutionaryFuzzerConfig config = new EvolutionaryFuzzerConfig();
	config.setOutputFolder("unit_test_output/");
	config.setConfigFolder("unit_test_config/");
	rule = new AnalyzeModificationRule(config);
	vector = new TestVector(null, null, null, null);
	vector.addModification(new AddMessageModification(new ClientHelloMessage()));
	vector.addModification(new AddRecordModification(new ClientHelloMessage()));
	vector.addModification(new ChangeServerCertificateModification(null));
	vector.addModification(new DuplicateMessageModification(new ClientHelloMessage(), 0));
	vector.addModification(new ModifyFieldModification("test", new AlertMessage()));

    }

    @After
    public void tearDown() {
	FileHelper.deleteFolder(new File("unit_test_output"));
	FileHelper.deleteFolder(new File("unit_test_config"));

    }

    /**
     * Test of applys method, of class AnalyzeModificationRule.
     */
    @Test
    public void testApplys() {
	Result result = new Result(false, false, 1000, 2000, new BranchTrace(), new TestVector(), new TestVector(),
		"unittest.id");
	assertTrue(rule.applys(result));
    }

    /**
     * Test of onApply method, of class AnalyzeModificationRule.
     */
    @Test
    public void testOnApply() {
	Result result = new Result(false, false, 1000, 2000, new BranchTrace(), vector, vector, "unittest.id");
	rule.onApply(result);
    }

    /**
     * Test of onDecline method, of class AnalyzeModificationRule.
     */
    @Test
    public void testOnDecline() {
	rule.onDecline(null);
    }

    /**
     * Test of report method, of class AnalyzeModificationRule.
     */
    @Test
    public void testReport() {
	Result result = new Result(false, false, 1000, 2000, new BranchTrace(), vector, vector, "unittest.id");
	assertNull(rule.report());
	rule.onApply(result);
	assertNotNull(rule.report());
    }

    /**
     * Test of getConfig method, of class AnalyzeModificationRule.
     */
    @Test
    public void testGetConfig() {
	assertNotNull(rule.getConfig());
    }

    /**
     * Test of getExecutedTraces method, of class AnalyzeModificationRule.
     */
    @Test
    public void testGetExecutedTraces() {
	Result result = new Result(false, false, 1000, 2000, new BranchTrace(), vector, vector, "unittest.id");
	rule.onApply(result);
	assertTrue(rule.getExecutedTraces() == 1);
	rule.onApply(result);
	assertTrue(rule.getExecutedTraces() == 2);

    }

    /**
     * Test of getTypeMap method, of class AnalyzeModificationRule.
     */
    @Test
    public void testGetTypeMap() {
	Result result = new Result(false, false, 1000, 2000, new BranchTrace(), vector, vector, "unittest.id");
	rule.onApply(result);
	vector.addModification(new AddMessageModification(new ServerHelloDoneMessage()));
	rule.onApply(result);
	HashMap<ModificationType, MutableInt> typeMap = rule.getTypeMap();
	MutableInt val = typeMap.get(ModificationType.ADD_RECORD);
	assertTrue(val.getValue() == 2);
	val = typeMap.get(ModificationType.ADD_MESSAGE);
	assertTrue(val.getValue() == 3);
    }
}
